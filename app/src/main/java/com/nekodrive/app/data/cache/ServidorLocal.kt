package com.nekodrive.app.data.cache

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
    private class Destino(val url: String, val mime: String)

    private val destinos = ConcurrentHashMap<String, Destino>()

    val porta: Int get() = listeningPort

    fun iniciarSeNecessario() {
        if (!isAlive) start(SOCKET_READ_TIMEOUT, false)
    }

    /** Registra um endereço do Drive e devolve a chave que vai no link local (/v/chave). */
    fun registrar(url: String, mime: String): String {
        val chave = UUID.randomUUID().toString().replace("-", "").take(16)
        destinos[chave] = Destino(url, mime)
        return chave
    }

    override fun serve(sessao: IHTTPSession): Response {
        if (sessao.method == Method.OPTIONS) {
            return cors(newFixedLengthResponse(Response.Status.OK, "text/plain", ""))
        }
        val chave = sessao.uri.removePrefix("/v/")
        val destino = destinos[chave]
            ?: return cors(newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Não encontrado"))
        return try {
            val req = Request.Builder().url(destino.url)
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
                val saida = if (tamanho >= 0) {
                    newFixedLengthResponse(status, destino.mime, corpo.byteStream(), tamanho)
                } else {
                    newChunkedResponse(status, destino.mime, corpo.byteStream())
                }
                resp.header("Content-Range")?.let { saida.addHeader("Content-Range", it) }
                saida.addHeader("Accept-Ranges", "bytes")
                cors(saida)
            }
        } catch (e: Exception) {
            cors(newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", e.message ?: "Erro"))
        }
    }

    private fun cors(r: Response): Response {
        r.addHeader("Access-Control-Allow-Origin", "*")
        r.addHeader("Access-Control-Allow-Headers", "Range, Content-Type")
        r.addHeader("Access-Control-Expose-Headers", "Content-Range, Content-Length, Accept-Ranges")
        return r
    }
}
