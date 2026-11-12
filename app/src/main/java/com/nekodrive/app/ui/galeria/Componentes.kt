package com.nekodrive.app.ui.galeria

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.nekodrive.app.ui.vidro

/**
 * Some e aparece com dissolução sutil: fade combinado com blur.
 * Saída: 90 ms (padrão anterior) + 300 ms = 390 ms. Entrada: 220 ms (padrão anterior).
 */
@Composable
fun Dissolver(
    visivel: Boolean,
    modifier: Modifier = Modifier,
    entradaMs: Int = 220,
    saidaMs: Int = 390,
    conteudo: @Composable BoxScope.() -> Unit,
) {
    val p by animateFloatAsState(
        targetValue = if (visivel) 0f else 1f,
        animationSpec = tween(if (visivel) entradaMs else saidaMs),
        label = "dissolver",
    )
    if (p < 1f) {
        Box(
            modifier.graphicsLayer {
                alpha = 1f - p
                val raio = p * 14.dp.toPx()
                renderEffect = if (raio > 0.5f) BlurEffect(raio, raio, TileMode.Decal) else null
            },
            content = conteudo,
        )
    }
}

/** Botão dentro de um círculo de vidro com blur. */
@Composable
fun CirculoVidro(aoClicar: () -> Unit, modifier: Modifier = Modifier, conteudo: @Composable () -> Unit) {
    Box(
        modifier.size(46.dp).clip(CircleShape).vidro().clickable(onClick = aoClicar),
        contentAlignment = Alignment.Center,
    ) { conteudo() }
}

/** Vários botões (ou um texto) agrupados numa única pílula de vidro com blur. */
@Composable
fun PilulaVidro(modifier: Modifier = Modifier, conteudo: @Composable RowScope.() -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(50)).vidro().padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = conteudo,
    )
}

/** Ícone para usar sobre o vidro. */
@Composable
fun BotaoVidro(icone: ImageVector, descricao: String, aoClicar: () -> Unit, tint: Color = Color.White) {
    IconButton(onClick = aoClicar) { Icon(icone, descricao, tint = tint) }
}

/**
 * Menu com blur (no lugar do menu opaco do sistema): fica na mesma composição, então o vidro desfoca o que está atrás.
 * Tocar fora fecha.
 */
@Composable
fun MenuVidro(
    aberto: Boolean,
    aoFechar: () -> Unit,
    modifier: Modifier = Modifier,
    conteudo: @Composable ColumnScope.() -> Unit,
) {
    Dissolver(aberto, Modifier.fillMaxSize(), entradaMs = 160, saidaMs = 200) {
        Box(
            Modifier.fillMaxSize().clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = aoFechar,
            ),
        )
        Column(
            modifier.clip(RoundedCornerShape(22.dp)).vidro().widthIn(min = 220.dp).padding(vertical = 8.dp),
            content = conteudo,
        )
    }
}

@Composable
fun ItemMenuVidro(texto: String, aoClicar: () -> Unit, marcado: Boolean = false, cor: Color = Color.White) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = aoClicar).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(texto, color = cor, modifier = Modifier.weight(1f))
        if (marcado) Text("✓", color = cor)
    }
}
