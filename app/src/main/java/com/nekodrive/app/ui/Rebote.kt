package com.nekodrive.app.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Overscroll com bounce (elástico com mola) no começo e no fim da lista.
 * No topo, puxar além do [limiar] dispara a atualização (indicador em [IndicadorAtualizar]).
 */
@Stable
class Rebote(
    private val escopo: CoroutineScope,
    val limiar: Float,
    private val aoAtualizar: () -> Unit,
) : NestedScrollConnection {
    var deslocamento by mutableFloatStateOf(0f)
        private set
    var atualizando by mutableStateOf(false)
        private set
    private var animacao: Job? = null

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput) return Offset.Zero
        val d = deslocamento
        // Esticada e o dedo vai no sentido contrário: primeiro desfaz o esticamento
        if (d != 0f && available.y != 0f && (d > 0f) != (available.y > 0f)) {
            animacao?.cancel()
            val novo = d + available.y * FATOR
            val limitado = if (d > 0f) novo.coerceAtLeast(0f) else novo.coerceAtMost(0f)
            deslocamento = limitado
            return Offset(0f, (limitado - d) / FATOR)
        }
        return Offset.Zero
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput || available.y == 0f) return Offset.Zero
        animacao?.cancel()
        deslocamento += available.y * FATOR
        return Offset(0f, available.y)
    }

    override suspend fun onPreFling(available: Velocity): Velocity =
        if (deslocamento != 0f) available else Velocity.Zero

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        soltar()
        return Velocity.Zero
    }

    private fun soltar() {
        animacao?.cancel()
        animacao = escopo.launch {
            if (deslocamento >= limiar && !atualizando) {
                atualizando = true
                aoAtualizar()
            }
            val alvo = if (atualizando && deslocamento > 0f) limiar * 0.7f else 0f
            animate(deslocamento, alvo, animationSpec = spring(dampingRatio = 0.55f, stiffness = 220f)) { v, _ ->
                deslocamento = v
            }
        }
    }

    /** O carregamento terminou: o indicador some encolhendo e a lista volta ao lugar. */
    fun terminou() {
        if (atualizando) {
            atualizando = false
            soltar()
        }
    }

    private companion object {
        const val FATOR = 0.42f
    }
}

@Composable
fun rememberRebote(carregando: Boolean, aoAtualizar: () -> Unit): Rebote {
    val escopo = rememberCoroutineScope()
    val limiar = with(LocalDensity.current) { 88.dp.toPx() }
    val atualizar by rememberUpdatedState(aoAtualizar)
    val rebote = remember(escopo, limiar) { Rebote(escopo, limiar) { atualizar() } }
    val carregandoAtual by rememberUpdatedState(carregando)
    LaunchedEffect(carregando, rebote.atualizando) {
        if (rebote.atualizando && !carregando) {
            delay(500) // dá tempo de o carregamento começar; depois garante que o indicador some
            if (!carregandoAtual) rebote.terminou()
        }
    }
    return rebote
}

/** Quadrado com blur que cresce com física de mola ao puxar e encolhe do mesmo jeito ao terminar. */
@Composable
fun IndicadorAtualizar(rebote: Rebote, modifier: Modifier = Modifier) {
    val progresso = (rebote.deslocamento / rebote.limiar).coerceIn(0f, 1f)
    val alvo = when {
        rebote.atualizando -> 1f
        rebote.deslocamento > 6f -> 0.35f + 0.65f * progresso
        else -> 0f
    }
    val escala by animateFloatAsState(alvo, spring(dampingRatio = 0.55f, stiffness = 260f), label = "indicador")
    if (escala > 0.01f) {
        Box(
            modifier
                .graphicsLayer {
                    scaleX = escala
                    scaleY = escala
                    translationY = rebote.deslocamento.coerceAtMost(rebote.limiar) * 0.55f
                }
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .vidro(),
            contentAlignment = Alignment.Center,
        ) {
            if (rebote.atualizando) {
                CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 3.dp)
            } else {
                CircularProgressIndicator(progress = { progresso }, modifier = Modifier.size(26.dp), strokeWidth = 3.dp)
            }
        }
    }
}
