package com.nekodrive.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
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

/** Desfoca o que está por baixo deste componente (barras, faixas). */
@Composable
fun Modifier.vidro(): Modifier {
    val v = LocalVidro.current ?: return this
    return this.hazeEffect(state = v.estado, style = v.estilo)
}

/** Desfoque em gradiente: intenso no topo e sumindo para baixo (cabeçalho). */
@Composable
fun Modifier.vidroGradiente(): Modifier {
    val v = LocalVidro.current ?: return this
    return this.hazeEffect(state = v.estado, style = v.estiloCabecalho) {
        progressive = HazeProgressive.verticalGradient(startIntensity = 1f, endIntensity = 0f)
    }
}

/** Marca o conteúdo que rola por baixo do vidro. Não pode conter componentes com [vidro]. */
@Composable
fun Modifier.fonteVidro(): Modifier {
    val v = LocalVidro.current ?: return this
    return this.hazeSource(state = v.estado)
}

/** Faixa de vidro no topo da tela; informa a própria altura para a lista começar abaixo dela e rolar por baixo. */
@Composable
fun FaixaVidro(
    topo: Dp,
    aoMedir: (Dp) -> Unit,
    modifier: Modifier = Modifier,
    conteudo: @Composable ColumnScope.() -> Unit,
) {
    val densidade = LocalDensity.current
    Column(
        modifier.padding(top = topo).fillMaxWidth().vidro()
            .onSizeChanged { aoMedir(with(densidade) { it.height.toDp() }) },
        content = conteudo,
    )
}
