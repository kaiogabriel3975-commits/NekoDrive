package com.nekodrive.app.data

import android.content.Context

/** Preferências de visualização (grade/lista e ordenação). */
class ViewPrefs(context: Context) {
    private val p = context.getSharedPreferences("view", Context.MODE_PRIVATE)

    var grade: Boolean
        get() = p.getBoolean("grade", true)
        set(v) { p.edit().putBoolean("grade", v).apply() }

    var campo: String
        get() = p.getString("campo", "Nome") ?: "Nome"
        set(v) { p.edit().putString("campo", v).apply() }

    var crescente: Boolean
        get() = p.getBoolean("crescente", true)
        set(v) { p.edit().putBoolean("crescente", v).apply() }
}
