package com.nekodrive.app.ui.galeria

import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import com.nekodrive.app.data.drive.DriveFile
import com.nekodrive.app.ui.drive.CoresMidia
import com.nekodrive.app.ui.drive.ProporcoesMidia

/**
 * O que a galeria sabe sobre a luz do ambiente: onde está cada mídia visível (página do pager, formato e zoom)
 * e de que cor ela é. As pílulas de vidro usam isso para acender o contorno do lado de onde vem a luz.
 * Tudo aqui é leitura de números já prontos (cores e proporções das miniaturas), sem imagem nem alocação por quadro.
 */
@Stable
class AmbienteLuz {
    var pager: PagerState? = null
    var fotos: List<DriveFile> = emptyList()
    var largura = 0f
    var altura = 0f
    var espaco = 0f // espaço entre páginas do pager, em px
    var origemX = 0f // canto da área da galeria na janela
    var origemY = 0f
    var zoomado: () -> Boolean = { false }
}

/** Resultado por pílula: força e cores da luz que chega de cada lado (0 cima, 1 direita, 2 baixo, 3 esquerda). */
private class LuzPilula {
    var x = 0f; var y = 0f; var w = 0f; var h = 0f
    val forca = FloatArray(4)
    val nucleo = FloatArray(12) // rgb da cor mais forte, por lado
    val corpo = FloatArray(12) // rgb da cor mais presente, por lado
    val soma = FloatArray(4)
}

private const val INF = 1e6f

/** Quanto do trecho de 0 a [raio], contado a partir da pílula, a mídia cobre: o brilho cai ao cubo com a distância. */
private fun cobertura(de: Float, ate: Float, raio: Float): Float {
    val a = de.coerceAtLeast(0f)
    val b = ate.coerceAtMost(raio)
    if (b <= a) return 0f
    val ia = 1f - a / raio
    val ib = 1f - b / raio
    return ia * ia * ia - ib * ib * ib
}

private fun sobreposicao(a0: Float, a1: Float, b0: Float, b1: Float): Float {
    val s = minOf(a1, b1) - maxOf(a0, b0)
    return if (s <= 0f) 0f else s / (a1 - a0)
}

private fun LuzPilula.calcular(amb: AmbienteLuz, raio: Float) {
    forca.fill(0f); soma.fill(0f); nucleo.fill(0f); corpo.fill(0f)
    val pg = amb.pager ?: return
    if (amb.largura <= 0f || amb.altura <= 0f || w <= 0f) return
    val l = amb.largura
    val a = amb.altura
    val atual = pg.currentPage
    val fracao = pg.currentPageOffsetFraction
    val outra = if (fracao > 0f) atual + 1 else if (fracao < 0f) atual - 1 else atual
    val passo = l + amb.espaco
    val zoom = amb.zoomado()
    val telaRazao = l / a

    for (k in 0..1) {
        val i = if (k == 0) atual else outra
        if (k == 1 && i == atual) break
        val f = amb.fotos.getOrNull(i) ?: continue

        // retângulo da mídia na tela: página deslocada pelo deslize + formato da miniatura (ou página inteira)
        val xPag = (i - atual - fracao) * passo
        var ml: Float; var mr: Float; var mt: Float; var mb: Float
        if (zoom && i == atual) {
            ml = xPag - l; mr = xPag + 2 * l; mt = -a; mb = 2 * a
        } else {
            val r = ProporcoesMidia.obter(f.id) ?: telaRazao
            val mw = if (r > telaRazao) l else a * r
            val mh = if (r > telaRazao) l / r else a
            ml = xPag + (l - mw) / 2f; mr = ml + mw
            mt = (a - mh) / 2f; mb = mt + mh
            // mídia encostada na borda da tela: a luz continua para fora dela
            if (ml <= 0.5f) ml = -INF
            if (mr >= l - 0.5f) mr = INF
            if (mt <= 0.5f) mt = -INF
            if (mb >= a - 0.5f) mb = INF
        }

        val pl = x; val pr = x + w; val pt = y; val pb = y + h
        val horiz = sobreposicao(pl, pr, ml, mr) // quanto da largura da pílula a mídia cobre
        val vert = sobreposicao(pt, pb, mt, mb)
        val wCima = cobertura(pt - mb, pt - mt, raio) * horiz
        val wBaixo = cobertura(mt - pb, mb - pb, raio) * horiz
        val wEsq = cobertura(pl - mr, pl - ml, raio) * vert
        val wDir = cobertura(ml - pr, mr - pr, raio) * vert

        val dom = CoresMidia.obter(f.id) ?: Color.White
        val forte = CoresMidia.obterForte(f.id) ?: dom
        for (s in 0..3) {
            val p = when (s) { 0 -> wCima; 1 -> wDir; 2 -> wBaixo; else -> wEsq }
            if (p <= 0f) continue
            soma[s] += p
            nucleo[s * 3] += p * forte.red; nucleo[s * 3 + 1] += p * forte.green; nucleo[s * 3 + 2] += p * forte.blue
            corpo[s * 3] += p * dom.red; corpo[s * 3 + 1] += p * dom.green; corpo[s * 3 + 2] += p * dom.blue
        }
    }
    for (s in 0..3) {
        val t = soma[s]
        if (t > 0f) {
            for (c in 0..2) { nucleo[s * 3 + c] /= t; corpo[s * 3 + c] /= t }
            forca[s] = if (t > 1f) 1f else t
        }
    }
    // o vidro deixa vazar um pouco de luz por baixo quando a mídia não o ilumina de baixo
    var fonte = 0
    if (forca[1] > forca[fonte]) fonte = 1
    if (forca[3] > forca[fonte]) fonte = 3
    val vaza = 0.3f * forca[fonte]
    if (vaza > forca[2]) {
        forca[2] = vaza
        for (c in 0..2) { nucleo[6 + c] = nucleo[fonte * 3 + c]; corpo[6 + c] = corpo[fonte * 3 + c] }
    }
}

private fun clarear(v: Float, quanto: Float) = v + (1f - v) * quanto

/**
 * Contorno de vidro iluminado pela mídia: cada lado da pílula acende com a cor da mídia que está desse lado,
 * com força que cai com a distância. Ao deslizar entre mídias, a luz da que sai apaga de um lado e a da que
 * chega acende do outro. Cuida só de desenho: o cálculo roda na fase de desenho e não recompõe nada.
 */
@Composable
fun Modifier.reflexoLuz(amb: AmbienteLuz): Modifier {
    val luz = remember { LuzPilula() }
    return this
        .onPlaced { c ->
            val p = c.positionInRoot()
            luz.x = p.x - amb.origemX; luz.y = p.y - amb.origemY
            luz.w = c.size.width.toFloat(); luz.h = c.size.height.toFloat()
        }
        .drawBehind {
            luz.calcular(amb, 220.dp.toPx())
            val w = size.width; val h = size.height
            val traco = 1.5.dp.toPx()
            val canto = CornerRadius(h / 2f)
            for (s in 0..3) {
                val f = luz.forca[s]
                if (f < 0.02f) continue
                val n = Color(clarear(luz.nucleo[s * 3], 0.45f), clarear(luz.nucleo[s * 3 + 1], 0.45f), clarear(luz.nucleo[s * 3 + 2], 0.45f), f * 0.95f)
                val c = Color(clarear(luz.corpo[s * 3], 0.25f), clarear(luz.corpo[s * 3 + 1], 0.25f), clarear(luz.corpo[s * 3 + 2], 0.25f), f * 0.5f)
                val fim = c.copy(alpha = 0f)
                val pincel = when (s) {
                    0 -> Brush.verticalGradient(0f to n, 0.5f to c, 1f to fim, startY = 0f, endY = h * 0.9f)
                    2 -> Brush.verticalGradient(0f to fim, 0.5f to c, 1f to n, startY = h * 0.1f, endY = h)
                    3 -> Brush.horizontalGradient(0f to n, 0.5f to c, 1f to fim, startX = 0f, endX = w * 0.3f)
                    else -> Brush.horizontalGradient(0f to fim, 0.5f to c, 1f to n, startX = w * 0.7f, endX = w)
                }
                drawRoundRect(pincel, cornerRadius = canto, alpha = 0.12f) // brilho suave dentro do vidro
                drawRoundRect(
                    pincel,
                    topLeft = Offset(traco / 2f, traco / 2f),
                    size = Size(w - traco, h - traco),
                    cornerRadius = canto,
                    style = Stroke(traco),
                )
            }
        }
}
