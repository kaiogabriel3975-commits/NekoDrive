package com.nekodrive.app.data.drive

import com.nekodrive.app.data.auth.AuthManager
import com.nekodrive.app.data.auth.GoogleAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.OutputStream
import java.io.IOException

data class DriveFile(
    val id: String,
    val name: String,
    val mimeType: String,
    val size: Long?,
    val modifiedTime: String?,
    val thumbnailLink: String?,
    /** Qualquer pessoa com o link consegue abrir (mídias privadas ficam sem indicador). */
    val publico: Boolean = false,
) {
    val isFolder get() = mimeType == "application/vnd.google-apps.folder"
    val isImage get() = mimeType.startsWith("image/")
    val isVideo get() = mimeType.startsWith("video/")
}

data class DrivePage(val files: List<DriveFile>, val nextPageToken: String?)

class UnauthorizedException : IOException("Sessão expirada")

class DriveHttpException(val code: Int, mensagem: String) : IOException(mensagem)

/** Arquivo achado dentro de uma pasta, com o caminho relativo ("Fotos/2024") a partir da pasta escolhida. */
class ArquivoDePasta(val arquivo: DriveFile, val caminho: String)

class DriveRepository(private val auth: AuthManager) {
    private val http = OkHttpClient()
    private val api = "https://www.googleapis.com/drive/v3/"

    /** Executa com o token da conta; se o Drive responder 401, renova o token e tenta de novo. */
    private suspend fun <T> comToken(email: String, bloco: (String) -> T): T =
        withContext(Dispatchers.IO) {
            try {
                bloco(auth.token(email))
            } catch (e: UnauthorizedException) {
                bloco(auth.token(email, force = true))
            }
        }

    /** [consulta] é o filtro `q` do Drive; [ordem] é o `orderBy` (ex.: "folder,name_natural"). */
    suspend fun list(email: String, consulta: String, ordem: String, pageToken: String?): DrivePage =
        comToken(email) { token ->
            val url = (api + "files").toHttpUrl().newBuilder()
                .addQueryParameter("q", consulta)
                .addQueryParameter("fields", "nextPageToken,files(id,name,mimeType,size,modifiedTime,thumbnailLink,permissionIds)")
                .addQueryParameter("orderBy", ordem)
                .addQueryParameter("pageSize", "100")
                .apply { if (pageToken != null) addQueryParameter("pageToken", pageToken) }
                .build()
            val json = JSONObject(get(token, url))
            val arr = json.optJSONArray("files")
            val files = (0 until (arr?.length() ?: 0)).map { i -> arr!!.getJSONObject(i).paraArquivo() }
            DrivePage(files, json.optString("nextPageToken").ifEmpty { null })
        }

    private fun JSONObject.paraArquivo(): DriveFile {
        val ids = optJSONArray("permissionIds")
        val publico = (0 until (ids?.length() ?: 0)).any {
            val id = ids!!.optString(it)
            id == "anyoneWithLink" || id == "anyone"
        }
        return DriveFile(
            id = getString("id"),
            name = optString("name"),
            mimeType = optString("mimeType"),
            size = optString("size").toLongOrNull(),
            modifiedTime = optString("modifiedTime").ifEmpty { null },
            thumbnailLink = optString("thumbnailLink").ifEmpty { null },
            publico = publico,
        )
    }

    /** Pastas que estão diretamente dentro de [idPai] (para escolher o destino de mover/copiar). */
    suspend fun pastasDe(email: String, idPai: String, pagina: String?): DrivePage =
        list(email, "'$idPai' in parents and trashed = false and mimeType = 'application/vnd.google-apps.folder'", "name_natural", pagina)

    /** Cria uma pasta dentro de [idPai]. */
    suspend fun criarPasta(email: String, nome: String, idPai: String): DriveFile = comToken(email) { token ->
        val corpo = JSONObject().put("name", nome).put("mimeType", "application/vnd.google-apps.folder")
            .put("parents", JSONArray().put(idPai)).toString().toRequestBody("application/json".toMediaType())
        val url = (api + "files").toHttpUrl().newBuilder().addQueryParameter("fields", "id,name,mimeType,modifiedTime").build()
        JSONObject(executar(Request.Builder().url(url).post(corpo).header("Authorization", "Bearer $token").build())).paraArquivo()
    }

    /** Move um arquivo ou pasta para [idDestino] (sai de todas as pastas em que estava). */
    suspend fun mover(email: String, id: String, idDestino: String) {
        comToken(email) { token ->
            val urlPais = (api + "files/" + id).toHttpUrl().newBuilder().addQueryParameter("fields", "parents").build()
            val pais = JSONObject(get(token, urlPais)).optJSONArray("parents")
            val atuais = (0 until (pais?.length() ?: 0)).map { pais!!.getString(it) }.filter { it != idDestino }
            val url = (api + "files/" + id).toHttpUrl().newBuilder()
                .addQueryParameter("addParents", idDestino)
                .apply { if (atuais.isNotEmpty()) addQueryParameter("removeParents", atuais.joinToString(",")) }
                .addQueryParameter("fields", "id")
                .build()
            executar(
                Request.Builder().url(url).patch("{}".toRequestBody("application/json".toMediaType()))
                    .header("Authorization", "Bearer $token").build(),
            )
        }
    }

    /**
     * Copia um arquivo para [idDestino]. O Drive não copia pastas: uma pasta vira uma pasta nova com cópias
     * de tudo que está dentro (chamando [aoCopiar] a cada arquivo copiado).
     */
    suspend fun copiar(email: String, f: DriveFile, idDestino: String, aoCopiar: () -> Unit = {}) {
        if (!f.isFolder) {
            comToken(email) { token ->
                val corpo = JSONObject().put("parents", JSONArray().put(idDestino)).toString()
                    .toRequestBody("application/json".toMediaType())
                val url = (api + "files/" + f.id + "/copy").toHttpUrl().newBuilder().addQueryParameter("fields", "id").build()
                executar(Request.Builder().url(url).post(corpo).header("Authorization", "Bearer $token").build())
            }
            aoCopiar()
            return
        }
        val nova = criarPasta(email, f.name, idDestino)
        var pagina: String? = null
        do {
            val p = list(email, "'${f.id}' in parents and trashed = false", "folder,name_natural", pagina)
            p.files.forEach { copiar(email, it, nova.id, aoCopiar) }
            pagina = p.nextPageToken
        } while (pagina != null)
    }

    /** Envia um arquivo para [idPai] (envio retomável, em fluxo: não carrega o arquivo inteiro na memória). */
    suspend fun enviar(
        email: String,
        nome: String,
        mime: String,
        tamanho: Long,
        idPai: String,
        abrir: () -> java.io.InputStream,
        aoProgresso: (Float) -> Unit,
    ) {
        // a escrita do corpo é bloqueante e não enxerga o cancelamento sozinha: confere a cada bloco
        val tarefa = currentCoroutineContext()[Job]
        comToken(email) { token ->
            val cliente = http.newBuilder()
                .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            val meta = JSONObject().put("name", nome).put("parents", JSONArray().put(idPai)).toString()
            val abrirSessao = Request.Builder()
                .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable")
                .header("Authorization", "Bearer $token")
                .header("X-Upload-Content-Type", mime)
                .apply { if (tamanho > 0) header("X-Upload-Content-Length", tamanho.toString()) }
                .post(meta.toRequestBody("application/json; charset=UTF-8".toMediaType()))
                .build()
            val sessao = cliente.newCall(abrirSessao).execute().use { r ->
                if (r.code == 401) throw UnauthorizedException()
                if (!r.isSuccessful) throw IOException(mensagemDeErro(r.code, r.body?.string().orEmpty()))
                r.header("Location") ?: throw IOException("O Drive não abriu a sessão de envio")
            }
            val corpo = object : okhttp3.RequestBody() {
                override fun contentType() = mime.toMediaType()
                override fun contentLength() = if (tamanho > 0) tamanho else -1L
                override fun writeTo(sink: okio.BufferedSink) {
                    abrir().use { entrada ->
                        val buffer = ByteArray(64 * 1024)
                        var enviados = 0L
                        while (true) {
                            tarefa?.ensureActive()
                            val n = entrada.read(buffer)
                            if (n < 0) break
                            sink.write(buffer, 0, n)
                            enviados += n
                            if (tamanho > 0) aoProgresso((enviados.toFloat() / tamanho).coerceIn(0f, 1f))
                        }
                    }
                }
            }
            cliente.newCall(Request.Builder().url(sessao).put(corpo).build()).execute().use { r ->
                if (r.code == 401) throw UnauthorizedException()
                if (!r.isSuccessful) throw IOException(mensagemDeErro(r.code, r.body?.string().orEmpty()))
            }
        }
    }

    /** Move um arquivo ou pasta para a lixeira do Drive. */
    suspend fun trash(email: String, id: String) {
        comToken(email) { token ->
            val corpo = """{"trashed":true}""".toRequestBody("application/json".toMediaType())
            executar(
                Request.Builder().url(api + "files/" + id).patch(corpo)
                    .header("Authorization", "Bearer $token").build(),
            )
        }
    }

    /** Muda o nome de um arquivo ou pasta. */
    suspend fun renomear(email: String, id: String, novoNome: String) {
        comToken(email) { token ->
            val corpo = JSONObject().put("name", novoNome).toString().toRequestBody("application/json".toMediaType())
            executar(
                Request.Builder().url(api + "files/" + id).patch(corpo)
                    .header("Authorization", "Bearer $token").build(),
            )
        }
    }

    /** Fotos e vídeos que estão diretamente dentro de uma pasta (para escolher a capa). */
    suspend fun imagensDaPasta(email: String, idPasta: String, pagina: String?): DrivePage =
        list(
            email,
            "'$idPasta' in parents and trashed = false and (mimeType contains 'image/' or mimeType contains 'video/')",
            "name_natural", pagina,
        )

    /** Todos os arquivos de uma pasta e das subpastas, com o caminho relativo de cada um. */
    suspend fun arquivosDaPasta(
        email: String,
        idPasta: String,
        nomePasta: String,
        aoContar: (Int) -> Unit = {},
    ): List<ArquivoDePasta> {
        fun limpo(n: String) = n.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifEmpty { "_" }
        val resultado = mutableListOf<ArquivoDePasta>()
        val fila = ArrayDeque<Pair<String, String>>()
        fila.add(idPasta to limpo(nomePasta))
        while (fila.isNotEmpty()) {
            val (pasta, caminho) = fila.removeFirst()
            var pagina: String? = null
            do {
                val p = list(email, "'$pasta' in parents and trashed = false", "folder,name_natural", pagina)
                p.files.forEach { f ->
                    if (f.isFolder) fila.add(f.id to caminho + "/" + limpo(f.name)) else resultado.add(ArquivoDePasta(f, caminho))
                }
                pagina = p.nextPageToken
                aoContar(resultado.size)
            } while (pagina != null)
        }
        return resultado
    }

    /** Metadados de um arquivo para o menu de informações (somente campos que a API confirma). */
    suspend fun metadados(email: String, id: String): JSONObject = comToken(email) { token ->
        val campos = "id,name,mimeType,size,createdTime,modifiedTime,fileExtension,webViewLink," +
            "owners(emailAddress,displayName),parents,imageMediaMetadata,videoMediaMetadata"
        val url = (api + "files/" + id).toHttpUrl().newBuilder().addQueryParameter("fields", campos).build()
        JSONObject(get(token, url))
    }

    /** Caminho legível ("Meu Drive / Fotos / 2024"); vazio se o Drive não permitir confirmar. */
    suspend fun caminho(email: String, idPai: String?): String = comToken(email) { token ->
        val nomes = mutableListOf<String>()
        var atual = idPai
        while (atual != null && nomes.size < 25) {
            val url = (api + "files/" + atual).toHttpUrl().newBuilder()
                .addQueryParameter("fields", "name,parents").build()
            val pasta = try {
                JSONObject(get(token, url))
            } catch (e: DriveHttpException) {
                break
            }
            nomes.add(0, pasta.optString("name"))
            atual = pasta.optJSONArray("parents")?.optString(0)?.takeIf { it.isNotEmpty() }
        }
        nomes.joinToString(" / ")
    }

    /** Baixa o conteúdo original do arquivo para [destino]. */
    suspend fun baixar(email: String, id: String, destino: File) {
        comToken(email) { token ->
            val url = (api + "files/" + id).toHttpUrl().newBuilder().addQueryParameter("alt", "media").build()
            val req = Request.Builder().url(url).header("Authorization", "Bearer $token").build()
            http.newCall(req).execute().use { resp ->
                if (resp.code == 401) throw UnauthorizedException()
                if (!resp.isSuccessful) throw IOException(mensagemDeErro(resp.code, resp.body?.string().orEmpty()))
                destino.parentFile?.mkdirs()
                resp.body!!.byteStream().use { entrada ->
                    destino.outputStream().use { saida -> entrada.copyTo(saida) }
                }
            }
        }
    }

    /** Metadados atuais de um arquivo; null se não existir mais ou se estiver na lixeira. */
    suspend fun arquivo(email: String, id: String): DriveFile? = comToken(email) { token ->
        val url = (api + "files/" + id).toHttpUrl().newBuilder()
            .addQueryParameter("fields", "id,name,mimeType,size,modifiedTime,thumbnailLink,trashed,permissionIds")
            .build()
        try {
            val o = JSONObject(get(token, url))
            if (o.optBoolean("trashed")) null else o.paraArquivo()
        } catch (e: DriveHttpException) {
            if (e.code == 404) null else throw e
        }
    }

    /** Tira um item da lixeira. */
    suspend fun restaurar(email: String, id: String) {
        comToken(email) { token ->
            val corpo = """{"trashed":false}""".toRequestBody("application/json".toMediaType())
            executar(
                Request.Builder().url(api + "files/" + id).patch(corpo)
                    .header("Authorization", "Bearer $token").build(),
            )
        }
    }

    /** Exclui para sempre um item (só deve ser usado na lixeira). */
    suspend fun excluirDefinitivo(email: String, id: String) {
        comToken(email) { token ->
            executar(Request.Builder().url(api + "files/" + id).delete().header("Authorization", "Bearer $token").build())
        }
    }

    /** Apaga para sempre tudo que está na lixeira da conta. */
    suspend fun esvaziarLixeira(email: String) {
        comToken(email) { token ->
            executar(Request.Builder().url(api + "files/trash").delete().header("Authorization", "Bearer $token").build())
        }
    }

    /** Baixa o conteúdo original para um fluxo de saída (usado para salvar em Downloads), com progresso de 0 a 1. */
    suspend fun baixarParaStream(email: String, id: String, saida: OutputStream, aoProgresso: (Float) -> Unit) {
        // A leitura é bloqueante e não enxerga o cancelamento sozinha: confere a cada bloco se ainda vale a pena.
        val tarefa = currentCoroutineContext()[Job]
        comToken(email) { token ->
            val url = (api + "files/" + id).toHttpUrl().newBuilder().addQueryParameter("alt", "media").build()
            val req = Request.Builder().url(url).header("Authorization", "Bearer $token").build()
            http.newCall(req).execute().use { resp ->
                if (resp.code == 401) throw UnauthorizedException()
                if (!resp.isSuccessful) throw IOException(mensagemDeErro(resp.code, resp.body?.string().orEmpty()))
                val corpo = resp.body ?: throw IOException("Resposta vazia do Drive")
                val total = corpo.contentLength()
                var lidos = 0L
                val buffer = ByteArray(64 * 1024)
                corpo.byteStream().use { entrada ->
                    while (true) {
                        tarefa?.ensureActive()
                        val n = entrada.read(buffer)
                        if (n < 0) break
                        saida.write(buffer, 0, n)
                        lidos += n
                        if (total > 0) aoProgresso((lidos.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        }
    }

    /** Identifica a conta dona do token (e-mail, nome e foto) pela própria API do Drive. */
    suspend fun about(token: String): GoogleAccount = withContext(Dispatchers.IO) {
        val url = (api + "about").toHttpUrl().newBuilder()
            .addQueryParameter("fields", "user(displayName,emailAddress,photoLink)")
            .build()
        val u = JSONObject(get(token, url)).getJSONObject("user")
        GoogleAccount(
            email = u.getString("emailAddress"),
            name = u.optString("displayName").ifEmpty { null },
            photoUrl = u.optString("photoLink").ifEmpty { null },
        )
    }

    private fun get(token: String, url: HttpUrl): String =
        executar(Request.Builder().url(url).header("Authorization", "Bearer $token").build())

    private fun executar(req: Request): String =
        http.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (resp.code == 401) throw UnauthorizedException()
            if (!resp.isSuccessful) throw DriveHttpException(resp.code, mensagemDeErro(resp.code, body))
            body
        }

    private fun mensagemDeErro(code: Int, body: String): String {
        val detalhe = try {
            JSONObject(body).optJSONObject("error")?.optString("message").orEmpty()
        } catch (e: Exception) {
            ""
        }
        return if (detalhe.isNotEmpty()) "Drive ($code): $detalhe" else "Erro $code do Drive"
    }
}
