package com.nekodrive.app.data

import android.content.Context
import org.json.JSONObject

/** Capa escolhida para cada pasta (id da pasta -> id da foto), por conta. Só existe no aparelho. */
class CapasStore(context: Context) {
    private val p = context.getSharedPreferences("capas", Context.MODE_PRIVATE)

    fun todas(email: String): Map<String, String> = try {
        val o = JSONObject(p.getString(email, "{}") ?: "{}")
        o.keys().asSequence().associateWith { o.getString(it) }
    } catch (e: Exception) {
        emptyMap()
    }

    fun salvar(email: String, mapa: Map<String, String>) {
        p.edit().putString(email, JSONObject(mapa as Map<*, *>).toString()).apply()
    }
}
