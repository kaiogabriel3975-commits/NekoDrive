package com.nekodrive.app.data.auth

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class GoogleAccount(val email: String, val name: String?, val photoUrl: String?)

/** Guarda a lista de contas e a conta atual. Não guarda senhas nem tokens. */
class AccountStore(context: Context) {
    private val prefs = context.getSharedPreferences("accounts", Context.MODE_PRIVATE)

    fun load(): List<GoogleAccount> {
        val arr = JSONArray(prefs.getString("list", "[]"))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            GoogleAccount(
                email = o.getString("email"),
                name = o.optString("name").ifEmpty { null },
                photoUrl = o.optString("photo").ifEmpty { null },
            )
        }
    }

    fun save(list: List<GoogleAccount>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("email", it.email).put("name", it.name ?: "").put("photo", it.photoUrl ?: ""))
        }
        prefs.edit().putString("list", arr.toString()).apply()
    }

    var currentEmail: String?
        get() = prefs.getString("current", null)
        set(value) { prefs.edit().putString("current", value).apply() }
}
