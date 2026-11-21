package com.nekodrive.app.ui

import android.app.ActivityManager
import android.content.Context

/** Nível do aparelho, pela memória RAM. Decide quanto de qualidade cabe sem apertar a memória. */
enum class NivelAparelho(val ladoFoto: Int) {
    /** Aparelhos de entrada (cerca de 4 GB ou menos). */
    ENTRADA(1600),

    /** Intermediários (cerca de 6 a 8 GB): é o valor que o app sempre usou. */
    INTERMEDIARIO(2400),

    /** Topo de linha (cerca de 12 GB ou mais). */
    TOPO(3200);

    companion object {
        @Volatile private var detectado: NivelAparelho? = null

        /** Calculado uma vez por execução; a RAM do aparelho não muda. */
        fun atual(context: Context): NivelAparelho =
            detectado ?: calcular(context.applicationContext).also { detectado = it }

        private fun calcular(context: Context): NivelAparelho {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                ?: return INTERMEDIARIO
            if (am.isLowRamDevice) return ENTRADA
            val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
            // totalMem vem menor que o número da caixa (o sistema reserva parte): 4 GB ≈ 3,7; 8 GB ≈ 7,4; 12 GB ≈ 11
            val gb = info.totalMem / (1024.0 * 1024.0 * 1024.0)
            return when {
                gb < 4.5 -> ENTRADA
                gb < 9.5 -> INTERMEDIARIO
                else -> TOPO
            }
        }
    }
}
