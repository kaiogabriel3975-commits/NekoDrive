package com.nekodrive.app.data.drive

import com.nekodrive.app.data.auth.AuthManager
import com.nekodrive.app.data.auth.GoogleAccount
import kotlinx.coroutines.Dispatchers
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
import java.io.IOException

data class DriveFile(
    val id: String,
    val name: String,
    val mimeType: String,
    val size: Long?,
    val modifiedTime: String?,
    val thumbnailLink: String?,
) {
    val isFolder get() = mimeType == "application/vnd.google-apps.folder"
    val isImage get() = mimeType.startsWith("image/")
    val isVideo get() = mimeType.startsWith("video/")
}

data class DrivePage(val files: List<DriveFile>, val nextPageToken: String?)

class UnauthorizedException : IOException("Sessão expirada")

class DriveHttpException(val code: Int, mensagem: String) : IOException(mensagem)

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
                .addQueryParameter("fields", "nextPageToken,files(id,name,mimeType,size,modifiedTime,thumbnailLink)")
                .addQueryParameter("orderBy", ordem)
                .addQueryParameter("pageSize", "100")
                .apply { if (pageToken != null) addQueryParameter("pageToken", pageToken) }
                .build()
            val json = JSONObject(get(token, url))
            val arr = json.optJSONArray("files")
            val files = (0 until (arr?.length() ?: 0)).map { i ->
                val o = arr!!.getJSONObject(i)
                DriveFile(
                    id = o.getString("id"),
                    name = o.optString("name"),
                    mimeType = o.optString("mimeType"),
                    size = o.optString("size").toLongOrNull(),
                    modifiedTime = o.optString("modifiedTime").ifEmpty { null },
                    thumbnailLink = o.optString("thumbnailLink").ifEmpty { null },
                )
            }
            DrivePage(files, json.optString("nextPageToken").ifEmpty { null })
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

    /** Todos os campos que a API devolve para o arquivo (`fields=*`), em lista chave/valor. */
    suspend fun detalhes(email: String, id: String): List<Pair<String, String>> =
        comToken(email) { token ->
            val url = (api + "files/" + id).toHttpUrl().newBuilder().addQueryParameter("fields", "*").build()
            val saida = mutableListOf<Pair<String, String>>()
            achatar("", JSONObject(get(token, url)), saida)
            saida.sortedBy { it.first }
        }

    private fun achatar(prefixo: String, valor: Any?, saida: MutableList<Pair<String, String>>) {
        when (valor) {
            is JSONObject -> valor.keys().forEach { k ->
                achatar(if (prefixo.isEmpty()) k else "$prefixo.$k", valor.get(k), saida)
            }
            is JSONArray -> for (i in 0 until valor.length()) achatar("$prefixo[$i]", valor.get(i), saida)
            else -> saida += prefixo to valor.toString()
        }
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
            .addQueryParameter("fields", "id,name,mimeType,size,modifiedTime,thumbnailLink,trashed")
            .build()
        try {
            val o = JSONObject(get(token, url))
            if (o.optBoolean("trashed")) {
                null
            } else {
                DriveFile(
                    id = o.getString("id"),
                    name = o.optString("name"),
                    mimeType = o.optString("mimeType"),
                    size = o.optString("size").toLongOrNull(),
                    modifiedTime = o.optString("modifiedTime").ifEmpty { null },
                    thumbnailLink = o.optString("thumbnailLink").ifEmpty { null },
                )
            }
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
