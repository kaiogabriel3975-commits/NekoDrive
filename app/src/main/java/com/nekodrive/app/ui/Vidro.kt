package com.nekodrive.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

/** Estado e estilo do efeito de vidro (desfoque do que passa por baixo das barras). */
class Vidro(val estado: HazeState, val estilo: HazeStyle, val estiloCabecalho: HazeStyle = estilo)

val LocalVidro = staticCompositionLocalOf<Vidro?> { null }

private val FILTRO_SATURACAO = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.35f) })

/** Aumenta em 35% a saturação de tudo que é desenhado por dentro (usado em todos os blurs). */
fun Modifier.saturar(): Modifier = this.drawWithContent {
    val tinta = Paint().apply { colorFilter = FILTRO_SATURACAO }
    drawIntoCanvas { canvas ->
        canvas.saveLayer(Rect(0f, 0f, size.width, size.height), tinta)
        drawContent()
        canvas.restore()
    }
}

/** Desfoca o que está por baixo deste componente (barras, círculos, pílulas), com +35% de saturação. */
@Composable
fun Modifier.vidro(): Modifier {
    val v = LocalVidro.current ?: return this
    return this.saturar().hazeEffect(state = v.estado, style = v.estilo)
}

/** Desfoque em gradiente: intenso no topo e sumindo para baixo (cabeçalho), com +35% de saturação. */
@Composable
fun Modifier.vidroGradiente(): Modifier {
    val v = LocalVidro.current ?: return this
    return this.saturar().hazeEffect(state = v.estado, style = v.estiloCabecalho) {
        progressive = HazeProgressive.verticalGradient(startIntensity = 1f, endIntensity = 0f)
    }
}

/** Marca o conteúdo que rola por baixo do vidro. Não pode conter componentes com [vidro]. */
@Composable
fun Modifier.fonteVidro(): Modifier {
    val v = LocalVidro.current ?: return this
    return this.hazeSource(state = v.estado)
}

/**
 * Área no topo da tela onde ficam botões flutuantes (cada um com o próprio vidro). Não tem fundo:
 * informa a própria altura para a lista começar abaixo dela e rolar por baixo.
 */
@Composable
fun FaixaVidro(
    topo: Dp,
    aoMedir: (Dp) -> Unit,
    modifier: Modifier = Modifier,
    conteudo: @Composable ColumnScope.() -> Unit,
) {
    val densidade = LocalDensity.current
    Column(
        modifier.padding(top = topo).fillMaxWidth()
            .onSizeChanged { aoMedir(with(densidade) { it.height.toDp() }) },
        content = conteudo,
    )
}
