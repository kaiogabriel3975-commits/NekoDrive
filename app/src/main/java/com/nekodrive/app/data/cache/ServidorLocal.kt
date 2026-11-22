package com.nekodrive.app.data.cache

import android.os.SystemClock
import java.io.FilterInputStream
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import fi.iki.elonen.NanoHTTPD
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Servidor HTTP no celular para o Chromecast: a TV pede o vídeo aqui e o servidor busca no Drive com o token
 * da conta, repassando o cabeçalho Range (necessário para avançar/voltar na TV).
 */
class ServidorLocal(private val cliente: OkHttpClient) : NanoHTTPD(0) {
    private class Destino(val url: String, val mime: String, val email: String?)

    private val destinos = ConcurrentHashMap<String, Destino>()

    val porta: Int get() = listeningPort

    // Conexões em andamento (a resposta é lida pela TV/player depois que serve() retorna) e último uso.
    private val ativos = AtomicInteger(0)
    @Volatile private var ultimoUso = 0L
    @Volatile private var vigia: Thread? = null

    /** Entrega a resposta e avisa quando o outro lado terminou de ler (ou desistiu). */
    private class FluxoContado(entrada: InputStream, private val aoFechar: () -> Unit) : FilterInputStream(entrada) {
        private val fechado = AtomicBoolean(false)
        override fun close() {
            try { super.close() } finally { if (fechado.compareAndSet(false, true)) aoFechar() }
        }
    }

    private fun terminouConexao() {
        ultimoUso = SystemClock.elapsedRealtime()
        ativos.decrementAndGet()
    }

    fun iniciarSeNecessario() {
        ultimoUso = SystemClock.elapsedRealtime()
        if (!isAlive) start(SOCKET_READ_TIMEOUT, false)
        // O servidor escuta na rede local: não fica aberto para sempre. Se ninguém usa por um bom tempo, fecha
        // (a próxima transmissão ou player externo abre de novo, com outra porta).
        if (vigia?.isAlive != true) {
            vigia = Thread({
                while (isAlive) {
                    try { Thread.sleep(60_000L) } catch (e: InterruptedException) { return@Thread }
                    val ocioso = SystemClock.elapsedRealtime() - ultimoUso
                    if (ativos.get() == 0 && ocioso > OCIOSO_MS) {
                        parar()
                        return@Thread
                    }
                }
            }, "NekoDrive-ServidorLocal").apply { isDaemon = true; start() }
        }
    }

    private fun parar() {
        stop()
        destinos.clear()
    }

    /** Fecha o servidor assim que a transmissão acaba, se mais nada o estiver usando. */
    fun encerrarSeOcioso() {
        if (!isAlive || ativos.get() > 0) return
        Thread({ if (ativos.get() == 0) parar() }, "NekoDrive-ServidorLocal-parar").start()
    }

    /** Registra um endereço do Drive e devolve a chave que vai no link local (/v/chave). */
    fun registrar(url: String, mime: String, email: String? = null): String {
        val chave = UUID.randomUUID().toString().replace("-", "").take(16)
        // [email]: a conta dona do arquivo; sem ele vale a conta atual do app (como antes)
        destinos[chave] = Destino(url, mime, email)
        return chave
    }

    override fun serve(sessao: IHTTPSession): Response {
        if (sessao.method == Method.OPTIONS) {
            return cors(newFixedLengthResponse(Response.Status.OK, "text/plain", ""))
        }
        val chave = sessao.uri.removePrefix("/v/")
        val destino = destinos[chave]
            ?: return cors(newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Não encontrado"))
        ativos.incrementAndGet()
        var entregue = false
        return try {
            val req = Request.Builder().url(destino.url)
                .apply { destino.email?.let { header(CABECALHO_CONTA, it) } }
                .apply { sessao.headers["range"]?.let { header("Range", it) } }
                .build()
            val resp = cliente.newCall(req).execute()
            val corpo = resp.body
            if (!resp.isSuccessful || corpo == null) {
                val codigo = resp.code
                resp.close()
                cors(newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Erro $codigo do Drive"))
            } else {
                val status = if (resp.code == 206) Response.Status.PARTIAL_CONTENT else Response.Status.OK
                val tamanho = corpo.contentLength()
                val fluxo = FluxoContado(corpo.byteStream()) { terminouConexao() }
                entregue = true
                val saida = if (tamanho >= 0) {
                    newFixedLengthResponse(status, destino.mime, fluxo, tamanho)
                } else {
                    newChunkedResponse(status, destino.mime, fluxo)
                }
                resp.header("Content-Range")?.let { saida.addHeader("Content-Range", it) }
                saida.addHeader("Accept-Ranges", "bytes")
                cors(saida)
            }
        } catch (e: Exception) {
            cors(newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", e.message ?: "Erro"))
        } finally {
            // se a resposta não foi entregue com o fluxo, a conexão acabou aqui
            if (!entregue) terminouConexao()
        }
    }

    private fun cors(r: Response): Response {
        r.addHeader("Access-Control-Allow-Origin", "*")
        r.addHeader("Access-Control-Allow-Headers", "Range, Content-Type")
        r.addHeader("Access-Control-Expose-Headers", "Content-Range, Content-Length, Accept-Ranges")
        return r
    }
}

/** Tempo sem nenhuma conexão para o servidor local se fechar sozinho. */
private const val OCIOSO_MS = 30 * 60 * 1000L
