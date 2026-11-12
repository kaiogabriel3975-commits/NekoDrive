package com.nekodrive.app.ui.galeria

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.google.android.gms.cast.framework.CastState
import com.nekodrive.app.data.drive.DriveFile
import com.nekodrive.app.ui.drive.DriveViewModel
import com.nekodrive.app.ui.player.CastControle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private const val API = "https://www.googleapis.com/drive/v3/files/"

class Faixa(val rotulo: String, val grupo: Tracks.Group)

/** Uma resolução disponível para o vídeo: a original ou uma versão convertida pelo Drive. */
class Qualidade(val rotulo: String, val altura: Int, val url: String?, val original: Boolean)

private fun rotuloAltura(h: Int): String = when {
    h >= 2000 -> "4K"
    h >= 1400 -> "2K"
    else -> "${h}p"
}

/** Player de um vídeo da galeria: estado observável, gestos, legendas, versão convertida e transmissão. */
@OptIn(UnstableApi::class)
class ControleVideo(
    ctx: Context,
    private val vm: DriveViewModel,
    val arquivo: DriveFile,
    private val legendasLaterais: List<DriveFile>,
    private val escopo: CoroutineScope,
) {
    val player: ExoPlayer = ExoPlayer.Builder(ctx)
        .setMediaSourceFactory(DefaultMediaSourceFactory(vm.midia.fabrica()))
        .build()
    val cast = CastControle(ctx, vm.midia.servidor)

    var tocando by mutableStateOf(false); private set
    var carregando by mutableStateOf(true); private set
    /** Verdadeiro depois do primeiro quadro pronto: antes disso é a tela de carregamento. */
    var pronto by mutableStateOf(false); private set
    var duracao by mutableLongStateOf(0L); private set
    var posicao by mutableLongStateOf(0L); private set
    var proporcao by mutableFloatStateOf(16f / 9f); private set
    var legendaTexto by mutableStateOf(""); private set
    var faixas by mutableStateOf(listOf<Faixa>()); private set
    var erro by mutableStateOf<String?>(null); private set
    var velocidade by mutableFloatStateOf(1f); private set
    var turbo by mutableStateOf(false); private set
    var convertida by mutableStateOf(false); private set
    var mensagem by mutableStateOf<String?>(null); private set
    var idMensagem by mutableIntStateOf(0); private set

    /** Resoluções realmente disponíveis para este vídeo (original e versões convertidas menores). */
    var qualidades by mutableStateOf(listOf<Qualidade>()); private set
    var qualidadeAtual by mutableStateOf<Qualidade?>(null); private set
    /** Verdadeiro enquanto o usuário não escolheu uma resolução: aí o app ajusta sozinho conforme a rede. */
    var qualidadeAutomatica by mutableStateOf(true); private set
    private var qualidadesCarregadas = false
    private var alturaOriginal = 0
    private var redeWifi = true

    private var jaTransmitiu = false
    private var ultimoSalvo = 0L
    private val email: String get() = vm.contaAtual?.email ?: ""

    private val ouvinte = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) { tocando = isPlaying }

        override fun onPlaybackStateChanged(state: Int) {
            carregando = state == Player.STATE_BUFFERING || state == Player.STATE_IDLE
            if (state == Player.STATE_READY) {
                pronto = true
                duracao = player.duration.coerceAtLeast(0L)
                if (!qualidadesCarregadas && !convertida) {
                    qualidadesCarregadas = true
                    alturaOriginal = player.videoSize.height
                    if (alturaOriginal > 0) escopo.launch { carregarQualidades() }
                }
            }
            if (state == Player.STATE_ENDED) salvarAgora()
        }

        override fun onVideoSizeChanged(size: VideoSize) {
            if (size.width > 0 && size.height > 0) proporcao = size.width * size.pixelWidthHeightRatio / size.height
        }

        override fun onCues(cueGroup: CueGroup) {
            legendaTexto = cueGroup.cues.mapNotNull { it.text?.toString() }.joinToString("\n")
        }

        override fun onTracksChanged(tracks: Tracks) {
            faixas = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }.mapIndexed { i, g ->
                val f = g.getTrackFormat(0)
                Faixa(f.label ?: f.language ?: "Legenda ${i + 1}", g)
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val formatoNaoSuportado = error.errorCode in listOf(
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
                PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
                PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
                PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
                PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            )
            if (formatoNaoSuportado && !convertida) {
                tentarConvertida()
            } else {
                erro = error.errorCodeName + (error.cause?.message?.let { ": $it" } ?: "")
            }
        }
    }

    private val gerenteRede = ctx.getSystemService(ConnectivityManager::class.java)
    private val ouvinteRede = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            val wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            escopo.launch {
                if (wifi != redeWifi) {
                    redeWifi = wifi
                    aplicarPolitica()
                }
            }
        }
    }

    init {
        redeWifi = gerenteRede?.getNetworkCapabilities(gerenteRede.activeNetwork)?.let {
            it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || it.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        } ?: true
        try {
            gerenteRede?.registerDefaultNetworkCallback(ouvinteRede)
        } catch (e: Exception) {
            // sem acesso ao estado da rede: mantém a resolução como está
        }
        player.addListener(ouvinte)
        // As legendas começam desligadas; o usuário escolhe no menu
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
    }

    private fun montarItem(uri: String, chaveCache: String): MediaItem {
        val legendas = legendasLaterais.map { l ->
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(API + l.id + "?alt=media"))
                .setMimeType(if (l.name.endsWith(".vtt", true)) MimeTypes.TEXT_VTT else MimeTypes.APPLICATION_SUBRIP)
                .setLabel(l.name)
                .setSelectionFlags(0)
                .build()
        }
        return MediaItem.Builder()
            .setUri(uri)
            .setCustomCacheKey(chaveCache)
            .setSubtitleConfigurations(legendas)
            .build()
    }

    /** Prepara o vídeo original (com as legendas laterais) e retoma de onde parou. */
    fun iniciar() {
        val item = montarItem(API + arquivo.id + "?alt=media", vm.chaveCache(arquivo))
        val antes = vm.progresso(arquivo)
        player.setMediaItem(item)
        if (antes != null && antes.posicaoMs > 5_000 && antes.posicaoMs < antes.duracaoMs * 0.9) {
            player.seekTo(antes.posicaoMs)
        }
        player.prepare()
        player.playWhenReady = true
    }

    /** Chamado a cada ~300 ms: atualiza a posição e grava o progresso a cada 5 s. */
    fun passo(atualizarPosicao: Boolean) {
        if (atualizarPosicao) posicao = player.currentPosition
        val d = player.duration
        if (d > 0) duracao = d
        if (player.isPlaying && System.currentTimeMillis() - ultimoSalvo > 5_000) {
            salvarAgora()
            ultimoSalvo = System.currentTimeMillis()
        }
    }

    fun salvarAgora() {
        val d = if (cast.transmitindo) cast.duracao else player.duration
        val p = if (cast.transmitindo) cast.posicao else player.currentPosition
        if (d > 0) vm.salvarProgresso(arquivo, p, d)
    }

    // ---------- controles ----------

    fun alternar() { if (player.isPlaying) player.pause() else player.play() }

    fun buscar(ms: Long) {
        player.seekTo(ms.coerceIn(0L, maxOf(duracao, 0L)))
        posicao = player.currentPosition
    }

    /** Toque duplo: avança ou volta 10 s e mostra a mensagem. */
    fun pular(deltaMs: Long) {
        buscar(player.currentPosition + deltaMs)
        mostrarMensagem(if (deltaMs < 0) "- 10 segundos" else "+ 10 segundos")
    }

    fun definirVelocidade(v: Float) {
        velocidade = v
        if (!turbo) player.setPlaybackSpeed(v)
    }

    fun ligarTurbo() {
        turbo = true
        player.setPlaybackSpeed(2f)
        mostrarMensagem("Velocidade 2x")
    }

    fun desligarTurbo() {
        if (turbo) {
            turbo = false
            player.setPlaybackSpeed(velocidade)
        }
    }

    fun escolherLegenda(f: Faixa?) {
        val b = player.trackSelectionParameters.buildUpon()
        if (f == null) {
            b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            legendaTexto = ""
        } else {
            b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setOverrideForType(TrackSelectionOverride(f.grupo.mediaTrackGroup, 0))
        }
        player.trackSelectionParameters = b.build()
    }

    fun mostrarMensagem(texto: String) {
        mensagem = texto
        idMensagem++
    }

    fun limparMensagem() { mensagem = null }

    fun tentarNovamente() {
        erro = null
        player.prepare()
    }

    /** Alternativa para formatos que o player não toca: a versão mp4 que o Drive gera (endereço não oficial). */
    fun tentarConvertida() {
        convertida = true
        erro = null
        carregando = true
        escopo.launch {
            try {
                val url = vm.midia.urlConvertida(email, arquivo.id)
                val item = MediaItem.Builder().setUri(url).setCustomCacheKey(vm.chaveCache(arquivo) + "|conv").build()
                val pos = player.currentPosition
                player.setMediaItem(item, pos)
                player.prepare()
                player.play()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                erro = "A versão convertida não está disponível: ${e.message}"
            }
        }
    }

    /** Informações que só o player conhece (o Drive não informa FPS). */
    fun extrasInfo(): List<Pair<String, String>> {
        val fps = player.videoFormat?.frameRate ?: -1f
        if (fps <= 0f) return emptyList()
        val texto = if (abs(fps - fps.roundToInt()) < 0.05f) fps.roundToInt().toString()
        else String.format(Locale.getDefault(), "%.2f", fps)
        return listOf("FPS" to texto)
    }

    // ---------- resolução ----------

    /** Descobre as resoluções disponíveis: a original e as versões convertidas menores que ela. */
    private suspend fun carregarQualidades() {
        val original = Qualidade("${rotuloAltura(alturaOriginal)} (original)", alturaOriginal, null, true)
        val convertidas = try {
            vm.midia.formatosConvertidos(email, arquivo.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
        val menores = convertidas.filter { it.altura < alturaOriginal }.map { Qualidade(rotuloAltura(it.altura), it.altura, it.url, false) }
        qualidades = listOf(original) + menores
        qualidadeAtual = original
        aplicarPolitica()
    }

    /** Padrão: até 1080p no Wi-Fi e até 540p (ou a mais próxima abaixo) nos dados móveis. Só vale sem escolha manual. */
    private fun aplicarPolitica() {
        if (!qualidadeAutomatica || qualidades.isEmpty()) return
        val alvo = if (redeWifi) 1080 else 540
        val escolha = qualidades.filter { it.altura <= alvo }.maxByOrNull { it.altura }
            ?: qualidades.minByOrNull { it.altura } ?: return
        if (escolha !== qualidadeAtual) trocarPara(escolha)
    }

    private fun trocarPara(q: Qualidade) {
        val pos = player.currentPosition
        val tocava = player.playWhenReady
        val item = if (q.original) {
            montarItem(API + arquivo.id + "?alt=media", vm.chaveCache(arquivo))
        } else {
            montarItem(q.url ?: return, vm.chaveCache(arquivo) + "|conv|" + q.altura)
        }
        qualidadeAtual = q
        player.setMediaItem(item, pos)
        player.prepare()
        player.playWhenReady = tocava
    }

    /** Escolha manual de resolução (null volta ao automático). Com escolha manual, a rede não muda mais nada. */
    fun escolherQualidade(q: Qualidade?) {
        if (q == null) {
            qualidadeAutomatica = true
            aplicarPolitica()
        } else {
            qualidadeAutomatica = false
            if (q !== qualidadeAtual) trocarPara(q)
        }
    }

    // ---------- Chromecast ----------

    /** Reage à conexão com a TV: pausa aqui e manda o vídeo para a TV a partir da posição atual. */
    suspend fun aoMudarEstadoCast() {
        if (cast.estado == CastState.CONNECTED && !cast.transmitindo) {
            try {
                val pos = player.currentPosition
                player.pause()
                // O receptor padrão toca mp4/webm; para outros formatos, tenta a versão convertida do Drive.
                val simples = arquivo.mimeType == "video/mp4" || arquivo.mimeType == "video/webm"
                val convertidaUrl = if (simples) null else try {
                    vm.midia.urlConvertida(email, arquivo.id)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
                val url = convertidaUrl ?: vm.midia.urlOriginal(arquivo.id)
                val mime = if (convertidaUrl != null) "video/mp4" else arquivo.mimeType
                cast.iniciar(arquivo.name, url, mime, pos)
                jaTransmitiu = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                erro = "Não foi possível transmitir: ${e.message}"
                player.play()
            }
        } else if (cast.estado != CastState.CONNECTED && cast.transmitindo) {
            cast.terminouSessao()
        }
    }

    /** Ao parar a transmissão, o vídeo continua no celular de onde estava na TV. */
    fun retomarSeParou() {
        if (!cast.transmitindo && jaTransmitiu) {
            player.seekTo(cast.posicao)
            player.play()
        }
    }

    fun liberar() {
        salvarAgora()
        try { gerenteRede?.unregisterNetworkCallback(ouvinteRede) } catch (e: Exception) { }
        player.removeListener(ouvinte)
        cast.soltar()
        player.release()
    }
}
