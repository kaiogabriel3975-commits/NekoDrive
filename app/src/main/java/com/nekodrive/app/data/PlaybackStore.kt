package com.nekodrive.app.data

import android.content.Context

/** [assistidoEm] é 0 enquanto o vídeo não passou de 90%; depois guarda o momento em que isso aconteceu. */
data class Progresso(val posicaoMs: Long, val duracaoMs: Long, val assistidoEm: Long) {
    val assistido get() = assistidoEm > 0
}

/** Posição de reprodução e marca de "assistido" por conta e arquivo. */
class PlaybackStore(context: Context) {
    private val p = context.getSharedPreferences("playback", Context.MODE_PRIVATE)

    private fun chave(email: String, id: String) = "$email|$id"

    fun obter(email: String, id: String): Progresso? {
        val partes = p.getString(chave(email, id), null)?.split(',') ?: return null
        if (partes.size < 3) return null
        return Progresso(
            partes[0].toLongOrNull() ?: return null,
            partes[1].toLongOrNull() ?: return null,
            partes[2].toLongOrNull() ?: 0L,
        )
    }

    fun salvar(email: String, id: String, posicaoMs: Long, duracaoMs: Long) {
        if (duracaoMs <= 0) return
        val antes = obter(email, id)
        val assistidoEm = when {
            antes != null && antes.assistido -> antes.assistidoEm
            posicaoMs >= duracaoMs * 0.9 -> System.currentTimeMillis()
            else -> 0L
        }
        p.edit().putString(chave(email, id), "$posicaoMs,$duracaoMs,$assistidoEm").apply()
    }
}
