package com.nekodrive.app.data.cache

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.nekodrive.app.data.Rede
import com.nekodrive.app.data.auth.AuthManager
import com.nekodrive.app.data.drive.DriveFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.net.URLDecoder
import java.util.concurrent.CopyOnWriteArrayList
import java.util.TreeSet
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.coroutineContext

private const val GB = 1024L * 1024L * 1024L

/** Uma versão convertida do vídeo: [itag] do Drive, [altura] em pixels e o endereço do fluxo. */
class FormatoConvertido(val itag: String, val altura: Int, val url: String, val largura: Int = 0)

private val ALTURA_PADRAO = mapOf("37" to 1080, "22" to 720, "59" to 480, "35" to 480, "18" to 360, "34" to 360)

/**
 * Cabeçalho interno que diz de qual conta é a requisição. O interceptor usa e remove: nunca vai para a rede.
 * Sem ele, vale a conta atual do app. Assim um download da conta A não depende da conta que está na tela.
 */
internal const val CABECALHO_CONTA = "X-Neko-Conta"

/**
 * Parâmetro interno na URI que diz de qual conta é o pedido (usado nas legendas laterais, que não têm chave de cache).
 * O ResolvingDataSource troca por [CABECALHO_CONTA] e o tira da URI: nunca vai para a rede.
 */
internal const val PARAM_CONTA = "neko_conta"

/** O token do Drive só é enviado para estes endereços do Google. */
private val HOSTS_COM_TOKEN = setOf("www.googleapis.com", "drive.google.com", "docs.google.com")

/** Guarda os cookies em memória (a versão convertida do Drive exige o cookie DRIVE_STREAM). */
class JarMemoria : CookieJar {
    private val todos = CopyOnWriteArrayList<Cookie>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach { novo ->
            todos.removeIf { it.name == novo.name && it.domain == novo.domain && it.path == novo.path }
            todos.add(novo)
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val agora = System.currentTimeMillis()
        return todos.filter { it.expiresAt > agora && it.matches(url) }
    }

    fun limpar() = todos.clear()
}

/**
 * Remove primeiro o que foi usado há mais tempo quando o total passa de [maxBytes].
 * Igual ao evictor LRU do Media3, mas com limite que pode mudar em tempo de execução.
 */
@OptIn(UnstableApi::class)
class EvictorAjustavel(inicial: Long) : CacheEvictor {
    @Volatile
    var maxBytes: Long = inicial

    /** Avisado (com a chave do recurso) toda vez que um trecho entra ou sai do cache. */
    @Volatile
    var aoMudar: ((String) -> Unit)? = null

    private val porUso = TreeSet<CacheSpan> { a, b ->
        val d = a.lastTouchTimestamp - b.lastTouchTimestamp
        if (d == 0L) a.compareTo(b) else if (d < 0) -1 else 1
    }
    private var tamanhoAtual = 0L

    override fun requiresCacheSpanTouches() = true
    override fun onCacheInitialized() {}

    @Synchronized
    override fun onStartFile(cache: Cache, key: String, position: Long, length: Long) {
        if (length != C.LENGTH_UNSET.toLong()) liberar(cache, length)
    }

    @Synchronized
    override fun onSpanAdded(cache: Cache, span: CacheSpan) {
        porUso.add(span)
        tamanhoAtual += span.length
        aoMudar?.invoke(span.key)
        liberar(cache, 0)
    }

    @Synchronized
    override fun onSpanRemoved(cache: Cache, span: CacheSpan) {
        porUso.remove(span)
        tamanhoAtual -= span.length
        aoMudar?.invoke(span.key)
    }

    override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) {
        onSpanRemoved(cache, oldSpan)
        onSpanAdded(cache, newSpan)
    }

    @Synchronized
    fun aplicar(cache: Cache) = liberar(cache, 0)

    private fun liberar(cache: Cache, extra: Long) {
        while (tamanhoAtual + extra > maxBytes && porUso.isNotEmpty()) cache.removeSpan(porUso.first())
    }
}

/**
 * Cache único de vídeos e fotos, em cacheDir/midia. Tudo que é assistido (streaming) ou baixado entra aqui,
 * e a limpeza automática respeita o limite de GB.
 */
@OptIn(UnstableApi::class)
class MidiaCache(private val context: Context, private val auth: AuthManager, limiteGb: Int) {
    private val conta = AtomicReference<String?>(null)
    private val evictor = EvictorAjustavel(limiteGb * GB)
    private val cache = SimpleCache(File(context.cacheDir, "midia"), evictor, StandaloneDatabaseProvider(context))

    // Resultado de completo() por arquivo ("chave|tamanho"). Qualquer trecho que entra ou sai do cache apaga
    // as entradas daquele recurso; [mudancas] impede guardar um resultado calculado enquanto o cache mudava.
    private val memoCompleto = ConcurrentHashMap<String, Boolean>()
    private val mudancas = AtomicLong(0)

    // Versão (md5) com que cada recurso foi guardado, para notar quando o arquivo mudou no Drive.
    private val versoesPrefs = context.getSharedPreferences("versoes_cache", Context.MODE_PRIVATE)
    private val versoes = ConcurrentHashMap<String, String>().also { mapa ->
        versoesPrefs.all.forEach { (k, v) -> if (v is String) mapa[k] = v }
    }

    init {
        evictor.aoMudar = { chave ->
            val prefixo = "$chave|"
            memoCompleto.keys.removeIf { it.startsWith(prefixo) }
            mudancas.incrementAndGet()
        }
    }

    private val cookies = JarMemoria()

    /** Anexa o token da conta atual às requisições ao Google (renovado quando preciso). */
    private val cliente = Rede.base.newBuilder().addInterceptor { chain ->
        val original = chain.request()
        // a conta da própria requisição (cabeçalho interno, sempre removido) vale mais que a conta atual da tela
        val daRequisicao = original.header(CABECALHO_CONTA)
        val req = if (daRequisicao != null) original.newBuilder().removeHeader(CABECALHO_CONTA).build() else original
        val email = daRequisicao ?: conta.get()
        if (email == null || req.url.host !in HOSTS_COM_TOKEN) {
            chain.proceed(req)
        } else {
            val token = try {
                runBlocking { auth.token(email) }
            } catch (e: Exception) {
                throw IOException("Sem autorização do Drive: ${e.message}", e)
            }
            chain.proceed(req.newBuilder().header("Authorization", "Bearer $token").build())
        }
    }.cookieJar(cookies).build()

    /** Servidor local usado para transmitir ao Chromecast. */
    val servidor: ServidorLocal by lazy { ServidorLocal(cliente) }

    fun definirConta(email: String?) {
        // os cookies da versão convertida pertencem à conta que os pediu: não passam para a outra conta
        if (conta.getAndSet(email) != email) cookies.limpar()
    }

    fun urlOriginal(id: String): String = url(id)

    /** Chave estável e isolada por conta para todos os recursos do cache. */
    fun chave(email: String, id: String): String = "$email|$id"

    /**
     * Versões convertidas (mp4) que o Drive gera para streaming, da maior para a menor altura.
     * Usa um endereço NÃO oficial do Drive, que pode mudar ou deixar de funcionar.
     */
    suspend fun formatosConvertidos(email: String, id: String): List<FormatoConvertido> = withContext(Dispatchers.IO) {
        val endereco = "https://drive.google.com/get_video_info".toHttpUrl().newBuilder()
            .addQueryParameter("docid", id)
            .addQueryParameter("drive_originator_app", "303")
            .build()
        val corpo = cliente.newCall(Request.Builder().url(endereco).header(CABECALHO_CONTA, email).build()).execute()
            .use { it.body?.string().orEmpty() }
        val campos = corpo.split('&').mapNotNull { par ->
            val i = par.indexOf('=')
            if (i < 0) null else URLDecoder.decode(par.substring(0, i), "UTF-8") to URLDecoder.decode(par.substring(i + 1), "UTF-8")
        }.toMap()
        if (campos["status"] != "ok") {
            throw IOException(campos["reason"] ?: "O Drive não ofereceu uma versão convertida deste vídeo")
        }
        val dimensoes = (campos["fmt_list"] ?: "").split(',').mapNotNull { entrada ->
            val partes = entrada.split('/')
            val wh = partes.getOrNull(1)?.split('x')
            val largura = wh?.getOrNull(0)?.toIntOrNull() ?: 0
            val altura = wh?.getOrNull(1)?.toIntOrNull()
            if (altura != null) partes[0] to (largura to altura) else null
        }.toMap()
        val urls = (campos["fmt_stream_map"] ?: throw IOException("O Drive não devolveu os formatos convertidos"))
            .split(',')
            .mapNotNull { entrada ->
                val i = entrada.indexOf('|')
                if (i < 0) null else entrada.substring(0, i) to entrada.substring(i + 1).replace("\\u0026", "&")
            }.toMap()
        val formatos = urls.mapNotNull { (itag, url) ->
            val dim = dimensoes[itag]
            val altura = dim?.second ?: ALTURA_PADRAO[itag] ?: return@mapNotNull null
            FormatoConvertido(itag, altura, url, dim?.first ?: 0)
        }.sortedByDescending { it.altura }
        if (formatos.isEmpty()) throw IOException("Nenhum formato convertido disponível")
        formatos
    }

    suspend fun urlConvertida(email: String, id: String): String = formatosConvertidos(email, id).first().url

    private fun url(id: String) = "https://www.googleapis.com/drive/v3/files/$id?alt=media"

    /** Fonte de dados que lê do cache e, se faltar, busca no Drive (gravando no cache). */
    fun fabrica(): CacheDataSource.Factory =
        CacheDataSource.Factory()
            .setCache(cache)
            .setCacheWriteDataSinkFactory(
                CacheDataSink.Factory().setCache(cache).setFragmentSize(C.LENGTH_UNSET.toLong()),
            )
            // a chave do cache começa com o e-mail da conta ("email|id"): vira o cabeçalho que o interceptor usa
            .setUpstreamDataSourceFactory(
                ResolvingDataSource.Factory(OkHttpDataSource.Factory(cliente)) { spec ->
                    val daUri = spec.uri.getQueryParameter(PARAM_CONTA)
                    val dona = spec.key?.substringBefore('|', "")?.takeIf { '@' in it }
                    if (daUri != null) {
                        val limpa = spec.uri.buildUpon().clearQuery().apply {
                            spec.uri.queryParameterNames.filter { it != PARAM_CONTA }.forEach { nome ->
                                spec.uri.getQueryParameters(nome).forEach { valor -> appendQueryParameter(nome, valor) }
                            }
                        }.build()
                        spec.withUri(limpa).withAdditionalHeaders(mapOf(CABECALHO_CONTA to daUri))
                    } else if (dona != null) {
                        spec.withAdditionalHeaders(mapOf(CABECALHO_CONTA to dona))
                    } else {
                        spec
                    }
                },
            )
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    /** O que está guardado ainda é a versão atual do arquivo? Sem md5 (ex.: Google Docs) ou sem registro, vale. */
    private fun versaoConfere(chave: String, f: DriveFile): Boolean {
        val md5 = f.md5 ?: return true
        val guardada = versoes[chave] ?: return true
        return guardada == md5
    }

    /**
     * Chamar antes de usar o arquivo (tocar, baixar). Se o conteúdo mudou no Drive desde que foi guardado,
     * apaga a cópia antiga (e as versões convertidas dela) e passa a valer a versão nova. Rápido quando nada mudou.
     */
    fun validar(email: String, f: DriveFile) {
        val md5 = f.md5 ?: return
        val chave = chave(email, f.id)
        val guardada = versoes[chave]
        if (guardada == md5) return
        if (guardada != null) {
            // mudou: o que está no cache é de outra versão
            cache.keys.toList().filter { it == chave || it.startsWith("$chave|") }.forEach { cache.removeResource(it) }
        }
        versoes[chave] = md5
        versoesPrefs.edit().putString(chave, md5).apply()
    }

    fun completo(email: String, f: DriveFile): Boolean {
        val tam = f.size ?: return false
        if (tam <= 0) return false
        val chave = chave(email, f.id)
        if (!versaoConfere(chave, f)) return false
        val k = "$chave|$tam"
        memoCompleto[k]?.let { return it }
        val antes = mudancas.get()
        val resultado = cache.isCached(chave, 0, tam)
        if (mudancas.get() == antes) {
            if (memoCompleto.size > LIMITE_MEMO) memoCompleto.clear()
            memoCompleto[k] = resultado
        }
        return resultado
    }

    /** Arquivo de uma foto já guardada por inteiro (para abrir offline), ou null. */
    fun arquivoLocal(email: String, f: DriveFile): File? {
        if (!completo(email, f)) return null
        val tam = f.size ?: return null
        val spans = cache.getCachedSpans(chave(email, f.id)).sortedBy { it.position }
        val span = spans.singleOrNull()
        if (span != null) return span.file?.takeIf { span.position == 0L && span.length >= tam }
        // Um download cancelado e retomado deixa o arquivo em vários trechos no cache: junta num arquivo só
        return juntarTrechos(f, spans, tam)
    }

    /**
     * Junta os trechos (contíguos, começando em 0) num arquivo único em cache/juntar. Só para arquivos pequenos,
     * como fotos, porque pode ser chamado a partir da tela; o resultado é reaproveitado nas próximas chamadas.
     */
    private fun juntarTrechos(f: DriveFile, spans: List<CacheSpan>, tam: Long): File? {
        if (tam > LIMITE_JUNTAR_BYTES) return null
        var esperado = 0L
        for (s in spans) {
            if (s.position != esperado || s.file == null) return null
            esperado += s.length
        }
        if (esperado != tam) return null
        val dir = File(context.cacheDir, "juntar").apply { mkdirs() }
        val agora = System.currentTimeMillis()
        dir.listFiles()?.forEach { if (it.lastModified() < agora - IDADE_JUNTAR_MS) it.delete() }
        val destino = File(dir, "${f.id}_$tam")
        if (destino.exists() && destino.length() == tam) {
            destino.setLastModified(agora)
            return destino
        }
        val temporario = File(dir, destino.name + ".tmp")
        return try {
            temporario.outputStream().use { saida ->
                for (s in spans) s.file!!.inputStream().use { it.copyTo(saida) }
            }
            if (temporario.length() == tam && temporario.renameTo(destino)) destino else { temporario.delete(); null }
        } catch (e: Exception) {
            temporario.delete()
            null
        }
    }

    /** Apaga do cache tudo o que pertence a esta conta (as chaves começam com "e-mail|"). */
    fun limparConta(email: String) {
        val prefixo = "$email|"
        cache.keys.toList().filter { it.startsWith(prefixo) }.forEach { cache.removeResource(it) }
        val antigas = versoes.keys.filter { it.startsWith(prefixo) }
        antigas.forEach { versoes.remove(it) }
        if (antigas.isNotEmpty()) versoesPrefs.edit().apply { antigas.forEach { remove(it) } }.apply()
    }

    /** Baixa o arquivo inteiro para o cache; baixar de novo continua de onde parou. */
    suspend fun baixar(email: String, f: DriveFile, aoProgresso: (Float) -> Unit) {
        withContext(Dispatchers.IO) {
            validar(email, f)
            val fonte = fabrica().createDataSourceForDownloading()
            val spec = DataSpec.Builder().setUri(url(f.id)).setKey(chave(email, f.id)).build()
            val escritor = CacheWriter(fonte, spec, null) { tamanho, baixado, _ ->
                if (tamanho > 0) aoProgresso((baixado.toFloat() / tamanho).coerceIn(0f, 1f))
            }
            val job: Job? = coroutineContext[Job]
            val registro = job?.invokeOnCompletion { causa -> if (causa != null) escritor.cancel() }
            try {
                escritor.cache()
            } finally {
                registro?.dispose()
            }
        }
    }

    fun tamanhoTotal(): Long = cache.cacheSpace

    fun definirLimite(gb: Int) {
        evictor.maxBytes = gb * GB
        evictor.aplicar(cache)
    }

    fun limpar() {
        cache.keys.toList().forEach { cache.removeResource(it) }
        versoes.clear()
        versoesPrefs.edit().clear().apply()
    }
}

/** Arquivos em vários trechos só são juntados até este tamanho (ex.: fotos). */
private const val LIMITE_MEMO = 5000
private const val LIMITE_JUNTAR_BYTES = 32L * 1024 * 1024
private const val IDADE_JUNTAR_MS = 10 * 60 * 1000L
