package com.nekodrive.app.data

import android.content.Context
import com.nekodrive.app.data.drive.DriveFile
import org.json.JSONArray
import org.json.JSONObject

/** Favoritos locais por conta (não altera a estrela do Drive). Guarda os metadados da última vez que o arquivo foi visto. */
class FavoritosStore(context: Context) {
    private val p = context.getSharedPreferences("favoritos", Context.MODE_PRIVATE)

    fun ler(email: String): List<DriveFile> = try {
        val arr = JSONArray(p.getString(email, "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            DriveFile(
                id = o.getString("id"),
                name = o.optString("name"),
                mimeType = o.optString("mimeType"),
                size = o.optString("size").toLongOrNull(),
                modifiedTime = o.optString("modifiedTime").ifEmpty { null },
                thumbnailLink = o.optString("thumbnailLink").ifEmpty { null },
            )
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun salvar(email: String, lista: List<DriveFile>) {
        val arr = JSONArray()
        lista.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id).put("name", it.name).put("mimeType", it.mimeType)
                    .put("size", it.size?.toString() ?: "")
                    .put("modifiedTime", it.modifiedTime ?: "")
                    .put("thumbnailLink", it.thumbnailLink ?: ""),
            )
        }
        p.edit().putString(email, arr.toString()).apply()
    }
}
