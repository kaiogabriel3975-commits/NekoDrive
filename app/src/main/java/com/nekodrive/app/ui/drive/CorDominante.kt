package com.nekodrive.app.ui.drive

import android.graphics.Bitmap
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.Color
import coil.request.ImageRequest
import coil.request.repeatCount

/** Mapa com limite: ao passar de [maximo] entradas descarta a usada há mais tempo. Seguro entre threads. */
private class MapaLimitado<K, V>(private val maximo: Int) {
    private val mapa = object : LinkedHashMap<K, V>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean = size > maximo
    }

    @Synchronized fun obter(chave: K): V? = mapa[chave]
    @Synchronized fun guardar(chave: K, valor: V) { mapa[chave] = valor }
}

private const val LIMITE_MAPAS = 4000

/** Cor mais frequente de cada miniatura, guardada para não recalcular ao rolar a lista. */
object CoresMidia {
    private val mapa = MapaLimitado<String, Color>(LIMITE_MAPAS)
    private val fortes = MapaLimitado<String, Color>(LIMITE_MAPAS)
    fun obter(id: String): Color? = mapa.obter(id)
    fun guardar(id: String, cor: Color) { mapa.guardar(id, cor) }

    /** Cor mais forte (viva e clara) da miniatura, usada no núcleo dos reflexos de vidro. */
    fun obterForte(id: String): Color? = fortes.obter(id)
    fun guardarForte(id: String, cor: Color) { fortes.guardar(id, cor) }
}

/** As duas cores que definem o clima de uma imagem: a que mais aparece e a mais forte. */
class CoresImagem(val dominante: Color, val forte: Color)

/** Proporção (largura/altura) de cada miniatura já carregada: a galeria usa para abrir direto no formato certo. */
object ProporcoesMidia {
    private val mapa = MapaLimitado<String, Float>(LIMITE_MAPAS)
    fun obter(id: String): Float? = mapa.obter(id)
    fun guardar(id: String, drawable: Drawable) {
        val w = drawable.intrinsicWidth
        val h = drawable.intrinsicHeight
        if (w > 0 && h > 0) mapa.guardar(id, w.toFloat() / h.toFloat())
    }
}

/** Cor que mais aparece na imagem (amostra reduzida, cores agrupadas em faixas). */
fun corDominante(bitmap: Bitmap): Color = coresDaImagem(bitmap).dominante

/** Cor mais presente e cor mais forte (quantidade x saturação x brilho) numa única passada pela amostra reduzida. */
fun coresDaImagem(bitmap: Bitmap): CoresImagem {
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
    var melhor: IntArray? = null
    var maisForte: IntArray? = null
    var pontosForte = -1f
    for (g in grupos.values) {
        if (melhor == null || g[0] > melhor[0]) melhor = g
        val n = g[0].toFloat()
        val r = g[1] / n / 255f; val gg = g[2] / n / 255f; val b = g[3] / n / 255f
        val alto = maxOf(r, gg, b)
        val sat = if (alto > 0f) (alto - minOf(r, gg, b)) / alto else 0f
        val pontos = n * (0.15f + sat) * (0.35f + alto)
        if (pontos > pontosForte) { pontosForte = pontos; maisForte = g }
    }
    val m = melhor ?: return CoresImagem(Color.Unspecified, Color.Unspecified)
    val f = maisForte ?: m
    return CoresImagem(media(m), media(f))
}

private fun media(g: IntArray): Color {
    val n = g[0].toFloat()
    return Color(g[1] / n / 255f, g[2] / n / 255f, g[3] / n / 255f)
}

/** Cor dominante de qualquer imagem carregada (fotos, vídeos e o primeiro quadro de GIFs). */
fun corDominante(drawable: Drawable): Color = coresDaImagem(drawable).dominante

/** Cores (dominante e forte) de qualquer imagem carregada. */
fun coresDaImagem(drawable: Drawable): CoresImagem = try {
    coresDaImagem(drawable.toBitmap(24, 24))
} catch (e: Exception) {
    CoresImagem(Color.Unspecified, Color.Unspecified)
}

/** GIFs e imagens animadas repetem para sempre enquanto estiverem na tela. */
fun ImageRequest.Builder.emLoop(): ImageRequest.Builder = this.repeatCount(AnimatedImageDrawable.REPEAT_INFINITE)
