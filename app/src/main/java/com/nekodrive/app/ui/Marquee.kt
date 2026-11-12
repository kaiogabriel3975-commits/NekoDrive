package com.nekodrive.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Quando verdadeiro, os nomes ficam parados (durante as animações de abrir e fechar a galeria). */
val LocalMarqueePausado = compositionLocalOf { false }

private const val QUADROS_POR_SEGUNDO = 24

/**
 * Nome em uma linha. Se não couber, rola continuamente na horizontal (marquee) a 24 quadros por segundo.
 */
@Composable
fun NomeUmaLinha(
    texto: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    centralizar: Boolean = false,
) {
    val pausado = LocalMarqueePausado.current
    val densidade = LocalDensity.current
    var larguraTexto by remember(texto) { mutableIntStateOf(0) }
    var larguraCaixa by remember { mutableIntStateOf(0) }
    var deslocamento by remember(texto) { mutableFloatStateOf(0f) }

    val excede = larguraCaixa > 0 && larguraTexto > larguraCaixa
    // O texto surge com um fade rápido (por exemplo, ao trocar entre grade e lista)
    val opacidade = remember { Animatable(0f) }
    LaunchedEffect(Unit) { opacidade.animateTo(1f, tween(140)) }
    val intervalo = with(densidade) { 32.dp.toPx() }
    val ciclo = larguraTexto + intervalo

    LaunchedEffect(excede, pausado, texto, ciclo) {
        if (excede && !pausado) {
            val passo = with(densidade) { 38.dp.toPx() } / QUADROS_POR_SEGUNDO
            if (deslocamento == 0f) delay(1200)
            while (true) {
                delay(1000L / QUADROS_POR_SEGUNDO)
                deslocamento = (deslocamento + passo) % ciclo
            }
        }
    }

    Box(
        modifier.clipToBounds().graphicsLayer { alpha = opacidade.value }.onSizeChanged { larguraCaixa = it.width },
        contentAlignment = if (centralizar) Alignment.Center else Alignment.CenterStart,
    ) {
        Text(
            texto,
            style = style,
            color = color,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            onTextLayout = { larguraTexto = it.size.width },
            modifier = Modifier.wrapContentWidth(align = Alignment.Start, unbounded = true)
                .graphicsLayer { translationX = -deslocamento },
        )
        if (excede) {
            Text(
                texto,
                style = style,
                color = color,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                modifier = Modifier.wrapContentWidth(align = Alignment.Start, unbounded = true)
                    .graphicsLayer { translationX = -deslocamento + ciclo },
            )
        }
    }
}
