package com.nekodrive.app.data.drive

import com.nekodrive.app.data.auth.AuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
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

    /** Lista o conteúdo de uma pasta ("root" = Meu Drive), pastas primeiro, depois por nome. */
    suspend fun list(email: String, folderId: String, pageToken: String?): DrivePage =
        withContext(Dispatchers.IO) {
            try {
                request(auth.token(email), folderId, pageToken)
            } catch (e: UnauthorizedException) {
                request(auth.token(email, force = true), folderId, pageToken)
            }
        }

    private fun request(token: String, folderId: String, pageToken: String?): DrivePage {
        val url = "https://www.googleapis.com/drive/v3/files".toHttpUrl().newBuilder()
            .addQueryParameter("q", "'$folderId' in parents and trashed = false")
            .addQueryParameter("fields", "nextPageToken,files(id,name,mimeType,size,modifiedTime,thumbnailLink)")
            .addQueryParameter("orderBy", "folder,name")
            .addQueryParameter("pageSize", "100")
            .apply { if (pageToken != null) addQueryParameter("pageToken", pageToken) }
            .build()
        val req = Request.Builder().url(url).header("Authorization", "Bearer $token").build()
        http.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (resp.code == 401) throw UnauthorizedException()
            if (!resp.isSuccessful) throw IOException("Erro ${resp.code} do Drive")
            val json = JSONObject(body)
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
            return DrivePage(files, json.optString("nextPageToken").ifEmpty { null })
        }
    }
}
