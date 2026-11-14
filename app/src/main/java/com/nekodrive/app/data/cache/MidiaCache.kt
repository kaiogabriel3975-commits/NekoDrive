package com.nekodrive.app.data.cache

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
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
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.net.URLDecoder
import java.util.concurrent.CopyOnWriteArrayList
import java.util.TreeSet
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.coroutineContext

private const val GB = 1024L * 1024L * 1024L

/** Uma versão convertida do vídeo: [itag] do Drive, [altura] em pixels e o endereço do fluxo. */
class FormatoConvertido(val itag: String, val altura: Int, val url: String)

private val ALTURA_PADRAO = mapOf("37" to 1080, "22" to 720, "59" to 480, "35" to 480, "18" to 360, "34" to 360)

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
}

/**
 * Remove primeiro o que foi usado há mais tempo quando o total passa de [maxBytes].
 * Igual ao evictor LRU do Media3, mas com limite que pode mudar em tempo de execução.
 */
@OptIn(UnstableApi::class)
class EvictorAjustavel(inicial: Long) : CacheEvictor {
    @Volatile
    var maxBytes: Long = inicial

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
        liberar(cache, 0)
    }

    @Synchronized
    override fun onSpanRemoved(cache: Cache, span: CacheSpan) {
        porUso.remove(span)
        tamanhoAtual -= span.length
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
class MidiaCache(context: Context, private val auth: AuthManager, limiteGb: Int) {
    private val conta = AtomicReference<String?>(null)
    private val evictor = EvictorAjustavel(limiteGb * GB)
    private val cache = SimpleCache(File(context.cacheDir, "midia"), evictor, StandaloneDatabaseProvider(context))

    private val cookies = JarMemoria()

    /** Anexa o token da conta atual às requisições ao Google (renovado quando preciso). */
    private val cliente = OkHttpClient.Builder().addInterceptor { chain ->
        val email = conta.get()
        val req = chain.request()
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

    fun definirConta(email: String?) { conta.set(email) }

    fun urlOriginal(id: String): String = url(id)

    /**
     * Versões convertidas (mp4) que o Drive gera para streaming, da maior para a menor altura.
     * Usa um endereço NÃO oficial do Drive, que pode mudar ou deixar de funcionar.
     */
    suspend fun formatosConvertidos(email: String, id: String): List<FormatoConvertido> = withContext(Dispatchers.IO) {
        conta.set(email)
        val endereco = "https://drive.google.com/get_video_info".toHttpUrl().newBuilder()
            .addQueryParameter("docid", id)
            .addQueryParameter("drive_originator_app", "303")
            .build()
        val corpo = cliente.newCall(Request.Builder().url(endereco).build()).execute()
            .use { it.body?.string().orEmpty() }
        val campos = corpo.split('&').mapNotNull { par ->
            val i = par.indexOf('=')
            if (i < 0) null else URLDecoder.decode(par.substring(0, i), "UTF-8") to URLDecoder.decode(par.substring(i + 1), "UTF-8")
        }.toMap()
        if (campos["status"] != "ok") {
            throw IOException(campos["reason"] ?: "O Drive não ofereceu uma versão convertida deste vídeo")
        }
        val alturas = (campos["fmt_list"] ?: "").split(',').mapNotNull { entrada ->
            val partes = entrada.split('/')
            val altura = partes.getOrNull(1)?.split('x')?.getOrNull(1)?.toIntOrNull()
            if (altura != null) partes[0] to altura else null
        }.toMap()
        val urls = (campos["fmt_stream_map"] ?: throw IOException("O Drive não devolveu os formatos convertidos"))
            .split(',')
            .mapNotNull { entrada ->
                val i = entrada.indexOf('|')
                if (i < 0) null else entrada.substring(0, i) to entrada.substring(i + 1).replace("\\u0026", "&")
            }.toMap()
        val formatos = urls.mapNotNull { (itag, url) ->
            val altura = alturas[itag] ?: ALTURA_PADRAO[itag] ?: return@mapNotNull null
            FormatoConvertido(itag, altura, url)
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
            .setUpstreamDataSourceFactory(OkHttpDataSource.Factory(cliente))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    fun completo(email: String, f: DriveFile): Boolean {
        val tam = f.size ?: return false
        return tam > 0 && cache.isCached(chave(email, f.id), 0, tam)
    }

    /** Arquivo de uma foto já guardada por inteiro (para abrir offline), ou null. */
    fun arquivoLocal(email: String, f: DriveFile): File? {
        if (!completo(email, f)) return null
        val span = cache.getCachedSpans(chave(email, f.id)).singleOrNull() ?: return null
        return span.file?.takeIf { span.position == 0L && span.length >= (f.size ?: Long.MAX_VALUE) }
    }

    /** Baixa o arquivo inteiro para o cache; baixar de novo continua de onde parou. */
    suspend fun baixar(email: String, f: DriveFile, aoProgresso: (Float) -> Unit) {
        conta.set(email)
        withContext(Dispatchers.IO) {
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
    }
}
