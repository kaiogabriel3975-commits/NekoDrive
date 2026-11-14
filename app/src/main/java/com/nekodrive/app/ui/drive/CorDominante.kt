package com.nekodrive.app.ui.drive

import android.graphics.Bitmap
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.Color
import coil.request.ImageRequest
import coil.request.repeatCount

/** Cor mais frequente de cada miniatura, guardada para não recalcular ao rolar a lista. */
object CoresMidia {
    private val mapa = HashMap<String, Color>()
    fun obter(id: String): Color? = mapa[id]
    fun guardar(id: String, cor: Color) { mapa[id] = cor }
}

/** Cor que mais aparece na imagem (amostra reduzida, cores agrupadas em faixas). */
fun corDominante(bitmap: Bitmap): Color {
    val pequeno = Bitmap.createScaledBitmap(bitmap, 24, 24, true)
    val grupos = HashMap<Int, IntArray>() // faixa -> [quantidade, soma R, soma G, soma B]
    for (y in 0 until pequeno.height) {
        for (x in 0 until pequeno.width) {
            val c = pequeno.getPixel(x, y)
            if ((c ushr 24) < 128) continue
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val faixa = ((r shr 4) shl 8) or ((g shr 4) shl 4) or (b shr 4)
            val g4 = grupos.getOrPut(faixa) { IntArray(4) }
            g4[0]++; g4[1] += r; g4[2] += g; g4[3] += b
        }
    }
    val melhor = grupos.values.maxByOrNull { it[0] } ?: return Color.Unspecified
    val n = melhor[0].toFloat()
    return Color(melhor[1] / n / 255f, melhor[2] / n / 255f, melhor[3] / n / 255f)
}

/** Cor dominante de qualquer imagem carregada (fotos, vídeos e o primeiro quadro de GIFs). */
fun corDominante(drawable: Drawable): Color = try {
    corDominante(drawable.toBitmap(24, 24))
} catch (e: Exception) {
    Color.Unspecified
}

/** GIFs e imagens animadas repetem para sempre enquanto estiverem na tela. */
fun ImageRequest.Builder.emLoop(): ImageRequest.Builder = this.repeatCount(AnimatedImageDrawable.REPEAT_INFINITE)
