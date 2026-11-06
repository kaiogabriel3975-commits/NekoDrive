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
import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException
import java.util.TreeSet
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.coroutineContext

private const val GB = 1024L * 1024L * 1024L

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

    /** Anexa o token da conta atual a cada requisição ao Drive (renovado quando preciso). */
    private val cliente = OkHttpClient.Builder().addInterceptor { chain ->
        val email = conta.get()
        val req = chain.request()
        if (email == null) {
            chain.proceed(req)
        } else {
            val token = try {
                runBlocking { auth.token(email) }
            } catch (e: Exception) {
                throw IOException("Sem autorização do Drive: ${e.message}", e)
            }
            chain.proceed(req.newBuilder().header("Authorization", "Bearer $token").build())
        }
    }.build()

    fun definirConta(email: String?) { conta.set(email) }

    fun chave(email: String, id: String) = "$email|$id"

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
