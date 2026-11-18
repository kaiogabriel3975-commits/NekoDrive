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
import androidx.media3.common.AudioAttributes
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
import androidx.media3.effect.Presentation
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

/**
 * Uma resolução do player. [altura] é o lado menor da imagem (1080p vale para 1080x1920 também). [url] é a fonte
 * (null = arquivo original); quando a fonte é maior que a resolução pedida, [alturaEfeito] é a altura em pixels
 * para a qual o player reduz a imagem; [fonte] é o lado menor da fonte (usado no cache).
 */
class Qualidade(
    val rotulo: String,
    val altura: Int,
    val url: String?,
    val original: Boolean,
    val alturaEfeito: Int = 0,
    val fonte: Int = altura,
)

/** Resoluções oferecidas, da maior para a menor (lado menor da imagem). */
private val ESCADA = listOf(2160, 1440, 1080, 720, 540, 480, 360, 240, 144)

private fun ladoMenor(largura: Int, altura: Int): Int =
    if (largura > 0 && altura > 0) minOf(largura, altura) else altura

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
        // foco de áudio (para o som ceder a ligações e outros apps) e pausa ao desconectar o fone
        .setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
            true,
        )
        .setHandleAudioBecomingNoisy(true)
        .build()
    val cast = CastControle(ctx, vm.midia.servidor)

    var tocando by mutableStateOf(false); private set

    /** Superfície em que o vídeo é desenhado; os reflexos tiram dela uma amostra minúscula do quadro atual. */
    var textura: android.view.TextureView? = null
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
    private var larguraOriginal = 0
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
                    larguraOriginal = player.videoSize.width
                    if (alturaOriginal > 0) escopo.launch { carregarQualidades() }
                }
            }
            if (state == Player.STATE_ENDED) salvarAgora()
        }

        /** Com "repetir" ligado, o fim do vídeo vira uma volta ao início: grava como assistido antes de recomeçar. */
        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
                val d = player.duration
                if (d > 0) vm.salvarProgresso(arquivo, d, d)
            }
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
        player.repeatMode = if (vm.repetirVideo) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
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
        if (vm.retomarVideo && antes != null && antes.posicaoMs > 5_000 && antes.posicaoMs < antes.duracaoMs * 0.9) {
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

    fun alternar() {
        if (player.isPlaying) {
            player.pause()
        } else {
            // depois do fim (sem repetir), tocar de novo começa do início
            if (player.playbackState == Player.STATE_ENDED) player.seekToDefaultPosition()
            player.play()
        }
    }

    fun buscar(ms: Long) {
        player.seekTo(ms.coerceIn(0L, maxOf(duracao, 0L)))
        posicao = player.currentPosition
    }

    /** Toque duplo: avança ([sentido] = 1) ou volta (-1) o tempo definido nos ajustes e mostra a mensagem. */
    fun pular(sentido: Int) {
        val s = vm.saltoSeg
        buscar(player.currentPosition + sentido * s * 1000L)
        mostrarMensagem(if (sentido < 0) "- $s segundos" else "+ $s segundos")
    }

    fun definirVelocidade(v: Float) {
        velocidade = v
        if (!turbo) player.setPlaybackSpeed(v)
    }

    fun ligarTurbo() {
        turbo = true
        val v = vm.velocidadeTurbo
        player.setPlaybackSpeed(v)
        mostrarMensagem("Velocidade " + (if (v % 1f == 0f) v.roundToInt().toString() else String.format(Locale.getDefault(), "%.1f", v)) + "x")
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
        player.setVideoEffects(emptyList())
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

    /**
     * Monta a lista de resoluções: a original e, abaixo dela, cada degrau da escada (4K a 144p) que o vídeo comporta.
     * Cada degrau usa a menor fonte que ainda é igual ou maior que ele (a original ou uma versão convertida pelo
     * Drive) e, se a fonte for maior, o player reduz a imagem para o degrau pedido.
     */
    private suspend fun carregarQualidades() {
        val lado = ladoMenor(larguraOriginal, alturaOriginal)
        val original = Qualidade("${rotuloAltura(lado)} (original)", lado, null, true)
        val convertidas = try {
            vm.midia.formatosConvertidos(email, arquivo.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
        // fontes de onde o vídeo pode vir: (lado menor, endereço); endereço nulo = arquivo original
        val fontes = listOf<Pair<Int, String?>>(lado to null) +
            convertidas.map { ladoMenor(it.largura, it.altura) to (it.url as String?) }
                .filter { it.first < lado * 0.97f }
        val degraus = ESCADA.filter { it < lado * 0.97f }.mapNotNull { n ->
            val fonte = fontes.filter { it.first >= n * 0.97f }.minByOrNull { it.first } ?: return@mapNotNull null
            val exata = abs(fonte.first - n) <= n * 0.03f
            val altPx = (alturaOriginal * n.toFloat() / lado).roundToInt().let { it - it % 2 }
            Qualidade(rotuloAltura(n), n, fonte.second, false, if (exata) 0 else altPx, fonte.first)
        }
        qualidades = listOf(original) + degraus
        qualidadeAtual = original
        aplicarPolitica()
    }

    /** Padrão: até a resolução dos ajustes no Wi-Fi (1080p) e nos dados móveis (540p), ou a mais próxima abaixo. Só vale sem escolha manual. */
    private fun aplicarPolitica() {
        if (!qualidadeAutomatica || qualidades.isEmpty()) return
        val alvo = if (redeWifi) vm.resWifi else vm.resMovel
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
            montarItem(q.url ?: return, vm.chaveCache(arquivo) + "|conv|" + q.fonte)
        }
        // fonte maior que a resolução pedida: o player reduz a imagem para a altura do degrau
        player.setVideoEffects(if (q.alturaEfeito > 0) listOf(Presentation.createForHeight(q.alturaEfeito)) else emptyList())
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
