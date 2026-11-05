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
import org.json.JSONObject
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
            if (!resp.isSuccessful) throw IOException(mensagemDeErro(resp.code, body))
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
