package com.nekodrive.app.ui.galeria

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.nekodrive.app.ui.Animacoes
import com.nekodrive.app.ui.Rebote
import androidx.compose.foundation.border
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
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
        animationSpec = Animacoes.espec(tween(if (visivel) entradaMs else saidaMs), tween(if (visivel) 120 else 150)),
        label = "dissolver",
    )
    if (p < 1f) {
        Box(
            modifier.graphicsLayer {
                alpha = 1f - p
                val raio = p * 14.dp.toPx()
                renderEffect = if (raio > 0.5f && Animacoes.normais) BlurEffect(raio, raio, TileMode.Decal) else null
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

/**
 * Vários botões (ou um texto) agrupados numa única pílula de vidro com blur. Com [luz], o contorno é
 * iluminado pela mídia que está por perto (veja [reflexoLuz]).
 */
@Composable
fun PilulaVidro(modifier: Modifier = Modifier, luz: AmbienteLuz? = null, conteudo: @Composable RowScope.() -> Unit) {
    val forma = RoundedCornerShape(50)
    Row(
        modifier.clip(forma).vidro()
            .then(if (luz != null) Modifier.reflexoLuz(luz) else Modifier)
            .padding(horizontal = 8.dp, vertical = 4.dp),
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
 * Surge em 0,5 s com a mesma dissolução dos botões do player, tem largura do maior item, rola quando não cabe
 * (com bounce nas pontas) e fecha ao tocar fora. [chave] muda quando o conteúdo troca, para voltar ao topo.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MenuVidro(
    aberto: Boolean,
    aoFechar: () -> Unit,
    modifier: Modifier = Modifier,
    chave: Any? = null,
    conteudo: @Composable ColumnScope.() -> Unit,
) {
    Dissolver(aberto, Modifier.fillMaxSize(), entradaMs = 500, saidaMs = 200) {
        Box(
            Modifier.fillMaxSize().clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = aoFechar,
            ),
        )
        val escopo = rememberCoroutineScope()
        val rolagem = rememberScrollState()
        val rebote = remember { Rebote(escopo, Float.MAX_VALUE) { } } // sem "puxar para atualizar": só o elástico
        LaunchedEffect(chave) { rolagem.scrollTo(0) }
        Box(
            modifier.clip(RoundedCornerShape(22.dp)).vidro()
                .widthIn(min = 150.dp, max = 260.dp).width(IntrinsicSize.Max),
        ) {
            CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
                Column(
                    Modifier
                        .then(if (rolagem.maxValue > 0) Modifier.nestedScroll(rebote) else Modifier)
                        .graphicsLayer { translationY = rebote.deslocamento }
                        .verticalScroll(rolagem)
                        .padding(vertical = 8.dp),
                    content = conteudo,
                )
            }
        }
    }
}

@Composable
fun ItemMenuVidro(
    texto: String,
    aoClicar: () -> Unit,
    marcado: Boolean = false,
    cor: Color = Color.White,
    icone: ImageVector? = null,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = aoClicar).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icone != null) {
            Icon(icone, null, tint = cor, modifier = Modifier.padding(end = 10.dp).size(20.dp))
        }
        Text(texto, color = cor, modifier = Modifier.weight(1f, fill = false))
        if (marcado) Text("  ✓", color = cor)
    }
}
