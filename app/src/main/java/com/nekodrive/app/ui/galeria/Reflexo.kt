package com.nekodrive.app.ui.galeria

import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nekodrive.app.data.drive.DriveFile
import com.nekodrive.app.ui.Animacoes
import com.nekodrive.app.ui.drive.CoresImagem
import com.nekodrive.app.ui.drive.CoresMidia
import com.nekodrive.app.ui.drive.ProporcoesMidia
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.SweepGradientShader
import androidx.compose.ui.graphics.lerp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.hypot
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow

/** Luz do ambiente disponível para as peças de vidro desta tela (null: sem reflexos). */
val LocalLuz = staticCompositionLocalOf<AmbienteLuz?> { null }

/** Luz da tela principal: a lista de arquivos informa nela as miniaturas visíveis (veja [AmbienteLuz.fontes]). */
val LocalLuzPrincipal = staticCompositionLocalOf<AmbienteLuz?> { null }

/** Ligado: só as toolbars (pílulas marcadas como toolbar e a busca) recebem o reflexo. Desligado: tudo que é vidro. */
val LocalLuzSoToolbars = staticCompositionLocalOf { false }

/**
 * O que a tela sabe sobre a luz do ambiente: onde estão as mídias que iluminam e de que cor elas são.
 * Na galeria as fontes são as páginas visíveis do pager; na tela principal, as miniaturas visíveis da lista.
 * Tudo vem de números já prontos (cores e proporções das miniaturas), sem ler imagem nenhuma.
 */
@Stable
class AmbienteLuz {
    // galeria
    var pager: PagerState? = null
    var fotos: List<DriveFile> = emptyList()
    var zoomado: () -> Boolean = { false }
    var videoTocando = false
    val vivas = HashMap<String, CoresImagem>() // cores do quadro atual do vídeo, enquanto toca

    // tela principal (ou qualquer outra): quem usa informa as fontes e quando há movimento
    var fontes: ((ColetorLuz) -> Unit)? = null
    var movendo: () -> Boolean = { false }

    var largura = 0f
    var altura = 0f
    var espaco = 0f // espaço entre páginas do pager, em px
    var origemX = 0f // canto da área na janela
    var origemY = 0f
    var raioDp = 520f // até onde a luz chega

    internal fun emMovimento() = pager?.isScrollInProgress == true || videoTocando || movendo()

    internal fun atualizarAlvo(col: ColetorLuz, raioPx: Float) {
        col.zerar(raioPx)
        if (largura > 0f && altura > 0f && col.w > 0f) {
            val p = pager
            if (p != null) coletarGaleria(p, col) else fontes?.invoke(col)
        }
        col.fechar()
    }

    private fun coletarGaleria(pg: PagerState, col: ColetorLuz) {
        val l = largura
        val a = altura
        val atual = pg.currentPage
        val fracao = pg.currentPageOffsetFraction
        val outra = if (fracao > 0f) atual + 1 else if (fracao < 0f) atual - 1 else atual
        val passo = l + espaco
        val zoom = zoomado()
        val telaRazao = l / a
        for (k in 0..1) {
            val i = if (k == 0) atual else outra
            if (k == 1 && i == atual) break
            val f = fotos.getOrNull(i) ?: continue
            // retângulo da mídia na tela: página deslocada pelo deslize + formato da miniatura (ou página inteira)
            val xPag = (i - atual - fracao) * passo
            var ml: Float
            var mr: Float
            var mt: Float
            var mb: Float
            if (zoom && i == atual) {
                ml = xPag - l; mr = xPag + 2 * l; mt = -a; mb = 2 * a
            } else {
                val r = ProporcoesMidia.obter(f.id) ?: telaRazao
                val mw = if (r > telaRazao) l else a * r
                val mh = if (r > telaRazao) l / r else a
                ml = xPag + (l - mw) / 2f; mr = ml + mw
                mt = (a - mh) / 2f; mb = mt + mh
                // mídia encostada na borda da tela: a luz continua para fora dela
                if (ml <= 0.5f) ml = -1e6f
                if (mr >= l - 0.5f) mr = 1e6f
                if (mt <= 0.5f) mt = -1e6f
                if (mb >= a - 0.5f) mb = 1e6f
            }
            val v = vivas[f.id]
            val dom = v?.dominante?.takeIf { it != Color.Unspecified } ?: CoresMidia.obter(f.id) ?: Color.White
            val forte = v?.forte?.takeIf { it != Color.Unspecified } ?: CoresMidia.obterForte(f.id) ?: dom
            col.fonte(ml, mt, mr, mb, dom, forte)
        }
    }
}

/**
 * Luz que chega a uma peça de vidro, por lado (0 cima, 1 direita, 2 baixo, 3 esquerda): cada fonte entra por
 * [fonte], a conta fecha em [fechar] e [suavizar] aproxima o que é desenhado do resultado, aos poucos.
 */
class ColetorLuz {
    internal var x = 0f
    internal var y = 0f
    internal var w = 0f
    internal var h = 0f
    private var raio = 1f

    private val soma = FloatArray(4)
    private val aForca = FloatArray(4)
    private val aN = FloatArray(12) // alvo: cor mais forte, rgb por lado
    private val aC = FloatArray(12) // alvo: cor mais presente, rgb por lado
    private val bruta = FloatArray(4)
    private val rN = FloatArray(12)
    private val rC = FloatArray(12)

    // o que é desenhado
    internal val forca = FloatArray(4)
    internal val nucleo = FloatArray(12)
    internal val corpo = FloatArray(12)

    internal fun zerar(raioPx: Float) {
        raio = raioPx
        soma.fill(0f); aN.fill(0f); aC.fill(0f)
    }

    /** Uma mídia que ilumina, com seu retângulo (na mesma área da peça) e suas cores. */
    fun fonte(ml: Float, mt: Float, mr: Float, mb: Float, dom: Color, forte: Color) {
        val pl = x
        val pr = x + w
        val pt = y
        val pb = y + h
        val horiz = sobreposicao(pl, pr, ml, mr) // quanto da largura da peça a mídia cobre
        val vert = sobreposicao(pt, pb, mt, mb)
        val wCima = realce(cobertura(pt - mb, pt - mt, raio)) * horiz
        val wBaixo = realce(cobertura(mt - pb, mb - pb, raio)) * horiz
        val wEsq = realce(cobertura(pl - mr, pl - ml, raio)) * vert
        val wDir = realce(cobertura(ml - pr, mr - pr, raio)) * vert
        for (s in 0..3) {
            val p = when (s) { 0 -> wCima; 1 -> wDir; 2 -> wBaixo; else -> wEsq }
            if (p <= 0f) continue
            soma[s] += p
            val i = s * 3
            aN[i] += p * forte.red; aN[i + 1] += p * forte.green; aN[i + 2] += p * forte.blue
            aC[i] += p * dom.red; aC[i + 1] += p * dom.green; aC[i + 2] += p * dom.blue
        }
    }

    internal fun fechar() {
        for (s in 0..3) {
            val t = soma[s]
            if (t > 0f) {
                for (c in 0..2) { aN[s * 3 + c] /= t; aC[s * 3 + c] /= t }
                aForca[s] = if (t > 1f) 1f else t
            } else {
                aForca[s] = 0f
            }
        }
        // refração: o vidro devolve parte da luz no lado oposto (por baixo, um pouco mais)
        System.arraycopy(aForca, 0, bruta, 0, 4)
        System.arraycopy(aN, 0, rN, 0, 12)
        System.arraycopy(aC, 0, rC, 0, 12)
        for (s in 0..3) {
            val o = (s + 2) % 4
            val r = (if (s == 0) 0.32f else 0.2f) * bruta[s]
            if (r > aForca[o]) {
                aForca[o] = r
                for (c in 0..2) { aN[o * 3 + c] = rN[s * 3 + c]; aC[o * 3 + c] = rC[s * 3 + c] }
            }
        }
    }

    /** Aproxima o desenhado do alvo; devolve true enquanto ainda há o que mudar. */
    internal fun suavizar(dt: Float, instantaneo: Boolean): Boolean {
        val k = if (instantaneo) 1f else 1f - exp(-dt / 0.10f)
        var mexe = false
        for (s in 0..3) {
            val alvo = aForca[s]
            if (alvo > 0.02f) {
                if (forca[s] < 0.03f) { // lado que está acendendo: já nasce na cor certa
                    for (c in 0..2) { nucleo[s * 3 + c] = aN[s * 3 + c]; corpo[s * 3 + c] = aC[s * 3 + c] }
                } else {
                    for (c in 0..2) {
                        val i = s * 3 + c
                        if (abs(aN[i] - nucleo[i]) > 0.002f) { nucleo[i] += (aN[i] - nucleo[i]) * k; mexe = true } else nucleo[i] = aN[i]
                        if (abs(aC[i] - corpo[i]) > 0.002f) { corpo[i] += (aC[i] - corpo[i]) * k; mexe = true } else corpo[i] = aC[i]
                    }
                }
            }
            val d = alvo - forca[s]
            if (abs(d) > 0.002f) { forca[s] += d * k; mexe = true } else forca[s] = alvo
        }
        return mexe
    }
}

/** Quanto do trecho de 0 a [raio], contado a partir da peça, a mídia cobre: o brilho cai ao cubo com a distância. */
private fun cobertura(de: Float, ate: Float, raio: Float): Float {
    val a = de.coerceAtLeast(0f)
    val b = ate.coerceAtMost(raio)
    if (b <= a) return 0f
    val ia = 1f - a / raio
    val ib = 1f - b / raio
    return ia * ia * ia - ib * ib * ib
}

/** Curva que levanta a luz fraca (mídia longe ainda ilumina um pouco) sem mexer na máxima. */
private fun realce(c: Float): Float = if (c <= 0f) 0f else c.pow(0.6f)

private fun sobreposicao(a0: Float, a1: Float, b0: Float, b1: Float): Float {
    val s = minOf(a1, b1) - maxOf(a0, b0)
    return if (s <= 0f) 0f else (s / (a1 - a0)).coerceAtMost(1f)
}

private fun clarear(v: Float, quanto: Float) = v + (1f - v) * quanto

private fun pincel(s: Int, w: Float, h: Float, a: Color, b: Color, fim: Color): Brush {
    val fundo = minOf(w * 0.6f, h * 1.6f) // profundidade nas laterais: só a ponta da pílula
    return when (s) {
        0 -> Brush.verticalGradient(0f to a, 0.5f to b, 1f to fim, startY = 0f, endY = h * 0.9f)
        2 -> Brush.verticalGradient(0f to fim, 0.5f to b, 1f to a, startY = h * 0.1f, endY = h)
        3 -> Brush.horizontalGradient(0f to a, 0.5f to b, 1f to fim, startX = 0f, endX = fundo)
        else -> Brush.horizontalGradient(0f to fim, 0.5f to b, 1f to a, startX = w - fundo, endX = w)
    }
}

/** Gradiente em varredura (ao redor do centro) girado em [graus]: a posição 0 do gradiente aponta para [graus]. */
private fun varredura(centro: Offset, graus: Float, cores: List<Color>, paradas: List<Float>): Brush =
    object : ShaderBrush() {
        override fun createShader(size: Size): Shader {
            val sh = SweepGradientShader(centro, cores, paradas)
            sh.setLocalMatrix(android.graphics.Matrix().apply { postRotate(graus, centro.x, centro.y) })
            return sh
        }
    }

/**
 * Vidro no estilo "liquid glass" da Apple, iluminado pela mídia. Camadas, de baixo para cima:
 *  1. película: um brilho difuso e translúcido que vem da direção da luz;
 *  2. luz colorida do ambiente: cada lado que a mídia ilumina acende com a cor dela (como antes);
 *  3. aro especular: dois lóbulos de brilho opostos ao longo da borda, o principal virado para a luz e o outro
 *     (mais fraco) no lado contrário, que se concentram nas pontas arredondadas e somem pelas retas, em três
 *     espessuras (halo, corpo e fio), que é o que dá o ar de vidro grosso e polido.
 * A direção dos lóbulos acompanha de onde vem a luz (padrão: canto superior esquerdo) e a força cresce com a
 * proximidade da mídia. A refração real do fundo (lente nas bordas) não é feita: exigiria um shader AGSL
 * (Android 13+) aplicado ao fundo desfocado. [canto] é o raio dos cantos (null = pílula/círculo).
 */
@Composable
fun Modifier.reflexoLuz(amb: AmbienteLuz, canto: Dp? = null): Modifier {
    val luz = remember { ColetorLuz() }
    var tique by remember { mutableIntStateOf(0) }
    val densidade = LocalDensity.current.density
    LaunchedEffect(luz, amb) {
        var dt = 0.016f
        var antes = 0L
        while (true) {
            amb.atualizarAlvo(luz, amb.raioDp * densidade)
            val mexeu = luz.suavizar(dt, Animacoes.sem)
            if (mexeu) tique++
            if (mexeu || amb.emMovimento()) {
                val t = androidx.compose.runtime.withFrameNanos { it }
                dt = if (antes == 0L) 0.016f else ((t - antes) / 1e9f).coerceIn(0.001f, 0.1f)
                antes = t
            } else {
                delay(100)
                antes = 0L
                dt = 0.1f
            }
        }
    }
    return this
        .onPlaced { c ->
            val p = c.positionInRoot()
            luz.x = p.x - amb.origemX; luz.y = p.y - amb.origemY
            luz.w = c.size.width.toFloat(); luz.h = c.size.height.toFloat()
        }
        .drawBehind {
            if (tique < 0) return@drawBehind // só para esta camada reler o desenho a cada passo
            val w = size.width
            val h = size.height
            val raio = canto?.toPx() ?: (minOf(w, h) / 2f)
            val centro = Offset(w / 2f, h / 2f)
            fun forma(inset: Float) = CornerRadius((raio - inset).coerceAtLeast(0f))

            // de onde vem a luz: soma dos lados acesos, com um empurrãozinho para o canto superior esquerdo
            var forte = 0
            for (s in 1..3) if (luz.forca[s] > luz.forca[forte]) forte = s
            val intensidade = luz.forca[forte].coerceIn(0f, 1f)
            val lx = (luz.forca[1] - luz.forca[3]) - 0.12f
            val ly = (luz.forca[2] - luz.forca[0]) - 0.12f
            val rad = atan2(ly, lx)
            val graus = Math.toDegrees(rad.toDouble()).toFloat()
            val ti = forte * 3
            val tom = Color(clarear(luz.nucleo[ti], 0.5f), clarear(luz.nucleo[ti + 1], 0.5f), clarear(luz.nucleo[ti + 2], 0.5f))
            val branco = lerp(Color.White, tom, 0.35f * intensidade) // o brilho especular herda um pouco da cor do ambiente

            // 1) película: brilho difuso vindo da direção da luz
            val dx = cos(rad)
            val dy = sin(rad)
            val meia = hypot(w, h) / 2f
            drawRoundRect(
                Brush.linearGradient(
                    0f to branco.copy(alpha = 0.16f + 0.10f * intensidade),
                    0.55f to branco.copy(alpha = 0f),
                    start = Offset(centro.x + dx * meia, centro.y + dy * meia),
                    end = Offset(centro.x - dx * meia, centro.y - dy * meia),
                ),
                cornerRadius = forma(0f),
            )

            // 2) luz colorida: cada lado que a mídia ilumina acende na cor dela
            for (s in 0..3) {
                val f = luz.forca[s].coerceAtMost(1f)
                if (f < 0.02f) continue
                val i = s * 3
                val n = Color(clarear(luz.nucleo[i], 0.55f), clarear(luz.nucleo[i + 1], 0.55f), clarear(luz.nucleo[i + 2], 0.55f), f * 0.85f)
                val c = Color(clarear(luz.corpo[i], 0.3f), clarear(luz.corpo[i + 1], 0.3f), clarear(luz.corpo[i + 2], 0.3f), f * 0.55f)
                val pinc = pincel(s, w, h, n, c, c.copy(alpha = 0f))
                val halo = 5.dp.toPx()
                val aro = 1.dp.toPx()
                drawRoundRect(pinc, cornerRadius = forma(0f), alpha = 0.09f)
                drawRoundRect(
                    pinc, topLeft = Offset(halo / 2f, halo / 2f), size = Size(w - halo, h - halo),
                    cornerRadius = forma(halo / 2f), style = Stroke(halo), alpha = 0.22f,
                )
                drawRoundRect(
                    pinc, topLeft = Offset(aro / 2f, aro / 2f), size = Size(w - aro, h - aro),
                    cornerRadius = forma(aro / 2f), style = Stroke(aro), alpha = 0.8f,
                )
            }

            // 3) aro especular: lóbulo principal virado para a luz e outro, mais fraco, no lado oposto
            val a1 = 0.55f + 0.40f * intensidade
            val a2 = 0.25f + 0.30f * intensidade
            fun b(a: Float) = branco.copy(alpha = a.coerceIn(0f, 1f))
            val pincelEspecular = varredura(
                centro, graus,
                listOf(
                    b(a1), b(a1 * 0.55f), b(a1 * 0.12f), b(0f), b(0f), b(a2 * 0.12f), b(a2 * 0.55f), b(a2),
                    b(a2 * 0.55f), b(a2 * 0.12f), b(0f), b(0f), b(a1 * 0.12f), b(a1 * 0.55f), b(a1),
                ),
                listOf(0f, 0.05f, 0.12f, 0.20f, 0.30f, 0.38f, 0.45f, 0.50f, 0.55f, 0.62f, 0.70f, 0.80f, 0.88f, 0.95f, 1f),
            )
            val fio = 1.1.dp.toPx()
            val corpo = 2.2.dp.toPx()
            val halo = 4.5.dp.toPx()
            drawRoundRect(
                pincelEspecular, topLeft = Offset(halo / 2f, halo / 2f), size = Size(w - halo, h - halo),
                cornerRadius = forma(halo / 2f), style = Stroke(halo), alpha = 0.20f,
            )
            drawRoundRect(
                pincelEspecular, topLeft = Offset(corpo / 2f, corpo / 2f), size = Size(w - corpo, h - corpo),
                cornerRadius = forma(corpo / 2f), style = Stroke(corpo), alpha = 0.45f,
            )
            drawRoundRect(
                pincelEspecular, topLeft = Offset(fio / 2f, fio / 2f), size = Size(w - fio, h - fio),
                cornerRadius = forma(fio / 2f), style = Stroke(fio),
            )
            // fio de vidro bem discreto em todo o contorno, mesmo longe dos lóbulos
            drawRoundRect(
                Color.White.copy(alpha = 0.09f), topLeft = Offset(fio / 2f, fio / 2f), size = Size(w - fio, h - fio),
                cornerRadius = forma(fio / 2f), style = Stroke(fio),
            )
        }
}
