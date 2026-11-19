package com.nekodrive.app.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * Modo de animações do app (ajuste "Animações"):
 *  0 = normais (tudo como foi desenhado);
 *  1 = baratas para a GPU: durações curtas, sem blur de dissolução, sem inclinação, sem camadas extras por item e
 *      sem molas que oscilam (só fades e movimentos simples);
 *  2 = sem animações: todas as transições acontecem na hora.
 * Fica em estado do Compose, então quem lê [modo] na composição se atualiza quando o ajuste muda.
 */
object Animacoes {
    var modo by mutableIntStateOf(0)

    val normais: Boolean get() = modo == 0
    val baratas: Boolean get() = modo == 1
    val sem: Boolean get() = modo == 2

    /** Escolhe a animação conforme o modo: [normal], uma [barata] (curta e sem oscilação) ou nenhuma (snap). */
    fun <T> espec(normal: FiniteAnimationSpec<T>, barata: FiniteAnimationSpec<T> = tween(150)): FiniteAnimationSpec<T> =
        when (modo) {
            0 -> normal
            1 -> barata
            else -> snap()
        }

    /** Duração em milissegundos conforme o modo (0 sem animações). */
    fun ms(normal: Int, barato: Int = (normal * 0.4f).toInt().coerceAtMost(200)): Int =
        when (modo) {
            0 -> normal
            1 -> barato
            else -> 0
        }

    fun entrada(normal: EnterTransition, barata: EnterTransition = fadeIn(tween(120))): EnterTransition =
        when (modo) {
            0 -> normal
            1 -> barata
            else -> EnterTransition.None
        }

    fun saida(normal: ExitTransition, barata: ExitTransition = fadeOut(tween(100))): ExitTransition =
        when (modo) {
            0 -> normal
            1 -> barata
            else -> ExitTransition.None
        }
}

/** Vibração (feedback tátil) ao segurar um item ou começar a arrastar uma foto. */
val LocalVibrar = staticCompositionLocalOf { true }

/** Nomes de fotos e vídeos nos cards da grade (pastas e outros arquivos sempre mostram o nome). */
val LocalMostrarNomes = staticCompositionLocalOf { true }

/** GIFs animam nas miniaturas; desligado, mostram só o primeiro quadro. */
val LocalAnimarGifs = staticCompositionLocalOf { true }

/** Verdadeiro quando as miniaturas só podem vir do cache (ajuste "só no Wi-Fi" ligado e sem Wi-Fi no momento). */
val LocalMiniaturasSoCache = staticCompositionLocalOf { false }

/** Reflexos no contorno das pílulas da galeria. */
val LocalReflexoPilula = staticCompositionLocalOf { true }

/** Pede um novo link de miniatura quando a capa em vídeo de uma pasta não carrega (o link do Drive expira). */
val LocalRenovarCapa = staticCompositionLocalOf<(String) -> Unit> { {} }

/** Fundo dos cards de mídia: 0 cor da mídia, 1 neutro, 2 miniatura invertida, desfocada e escurecida. */
val LocalFundoCard = staticCompositionLocalOf { 0 }

/** Wi-Fi ou cabo que não seja marcado como tarifado (hotspot de outro celular conta como dados móveis). */
private fun redeSemTarifa(caps: NetworkCapabilities?): Boolean =
    caps != null &&
        (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) &&
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)

/** Verdadeiro enquanto a rede ativa for Wi-Fi ou cabo sem tarifa (atualiza sozinho quando a rede muda). */
@Composable
fun rememberRedeWifi(): Boolean {
    val ctx = LocalContext.current
    // valor inicial lido da rede atual: antes ele assumia Wi-Fi até o primeiro aviso do sistema
    var wifi by remember {
        val gerente = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        mutableStateOf(runCatching { redeSemTarifa(gerente.getNetworkCapabilities(gerente.activeNetwork)) }.getOrDefault(true))
    }
    DisposableEffect(ctx) {
        val gerente = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val ouvinte = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                wifi = redeSemTarifa(caps)
            }

            override fun onLost(network: Network) {
                wifi = false
            }
        }
        runCatching { gerente.registerDefaultNetworkCallback(ouvinte) }
        onDispose { runCatching { gerente.unregisterNetworkCallback(ouvinte) } }
    }
    return wifi
}
