package com.nekodrive.app.ui.player

import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.net.Uri
import android.util.Rational
import android.view.TextureView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.Forward10
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.Replay10
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
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
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.nekodrive.app.ui.drive.DriveViewModel
import com.nekodrive.app.ui.drive.VideoAberto
import com.nekodrive.app.ui.drive.pastaBounds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

private const val API = "https://www.googleapis.com/drive/v3/files/"
private val VELOCIDADES = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

private class Faixa(val rotulo: String, val grupo: Tracks.Group)

private fun Context.encontrarActivity(): ComponentActivity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is ComponentActivity) return c
        c = c.baseContext
    }
    return null
}

private fun tempo(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val ss = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, ss) else "%02d:%02d".format(m, ss)
}

@OptIn(UnstableApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun Reprodutor(vm: DriveViewModel, dados: VideoAberto, st: SharedTransitionScope, av: AnimatedVisibilityScope) {
    val arquivo = dados.arquivo
    val ctx = LocalContext.current
    val activity = remember(ctx) { ctx.encontrarActivity() }
    val scope = rememberCoroutineScope()

    // ---------- token do Drive (renovado de tempos em tempos) ----------
    val tokenRef = remember { AtomicReference<String?>(vm.token) }
    LaunchedEffect(vm.token) { tokenRef.set(vm.token) }
    LaunchedEffect(Unit) {
        vm.renovarToken()
        while (true) {
            delay(40 * 60_000L)
            vm.renovarToken()
        }
    }

    // ---------- player ----------
    val player = remember {
        val cliente = OkHttpClient.Builder().addInterceptor { chain ->
            val t = tokenRef.get()
            val req = if (t != null) chain.request().newBuilder().header("Authorization", "Bearer $t").build() else chain.request()
            chain.proceed(req)
        }.build()
        ExoPlayer.Builder(ctx)
            .setMediaSourceFactory(DefaultMediaSourceFactory(OkHttpDataSource.Factory(cliente)))
            .build()
    }

    var tocando by remember { mutableStateOf(false) }
    var carregando by remember { mutableStateOf(true) }
    var duracao by remember { mutableLongStateOf(0L) }
    var posicao by remember { mutableLongStateOf(0L) }
    var proporcao by remember { mutableFloatStateOf(16f / 9f) }
    var legendaTexto by remember { mutableStateOf("") }
    var faixas by remember { mutableStateOf(listOf<Faixa>()) }
    var erro by remember { mutableStateOf<String?>(null) }
    var velocidade by remember { mutableFloatStateOf(1f) }
    var turbo by remember { mutableStateOf(false) }
    var controles by remember { mutableStateOf(true) }
    var emPip by remember { mutableStateOf(false) }
    var paisagem by remember { mutableStateOf(false) }
    var arrastando by remember { mutableStateOf(false) }
    var posSlider by remember { mutableFloatStateOf(0f) }
    var indicador by remember { mutableStateOf<String?>(null) }
    var menuVelocidade by remember { mutableStateOf(false) }
    var menuLegendas by remember { mutableStateOf(false) }
    var escala by remember { mutableFloatStateOf(1f) }
    var deslocamento by remember { mutableStateOf(Offset.Zero) }
    val arrasto = remember { Animatable(0f) }

    fun salvarAgora() {
        val d = player.duration
        if (d > 0) vm.salvarProgresso(arquivo, player.currentPosition, d)
    }

    DisposableEffect(player) {
        val ouvinte = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { tocando = isPlaying }
            override fun onPlaybackStateChanged(state: Int) {
                carregando = state == Player.STATE_BUFFERING || state == Player.STATE_IDLE
                if (state == Player.STATE_READY) duracao = player.duration.coerceAtLeast(0L)
                if (state == Player.STATE_ENDED) { salvarAgora(); controles = true }
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
                erro = error.errorCodeName + (error.cause?.message?.let { ": $it" } ?: "")
            }
        }
        player.addListener(ouvinte)
        onDispose {
            salvarAgora()
            player.removeListener(ouvinte)
            player.release()
        }
    }

    // Prepara o vídeo (com as legendas laterais) e retoma de onde parou
    LaunchedEffect(player) {
        val legendas = dados.legendas.map { l ->
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(API + l.id + "?alt=media"))
                .setMimeType(if (l.name.endsWith(".vtt", true)) MimeTypes.TEXT_VTT else MimeTypes.APPLICATION_SUBRIP)
                .setLabel(l.name)
                .setSelectionFlags(0)
                .build()
        }
        val item = MediaItem.Builder()
            .setUri(API + arquivo.id + "?alt=media")
            .setSubtitleConfigurations(legendas)
            .build()
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
        val antes = vm.progresso(arquivo)
        player.setMediaItem(item)
        if (antes != null && antes.posicaoMs > 5_000 && antes.posicaoMs < antes.duracaoMs * 0.9) {
            player.seekTo(antes.posicaoMs)
        }
        player.prepare()
        player.playWhenReady = true
    }

    // Posição na barra e gravação do progresso a cada 5 s
    LaunchedEffect(player) {
        var ultimoSalvo = 0L
        while (true) {
            if (!arrastando) posicao = player.currentPosition
            val d = player.duration
            if (d > 0) duracao = d
            if (player.isPlaying && System.currentTimeMillis() - ultimoSalvo > 5_000) {
                salvarAgora()
                ultimoSalvo = System.currentTimeMillis()
            }
            delay(300)
        }
    }

    // Esconde os controles sozinhos durante a reprodução
    LaunchedEffect(controles, tocando, arrastando, menuVelocidade, menuLegendas) {
        if (controles && tocando && !arrastando && !menuVelocidade && !menuLegendas) {
            delay(3500)
            controles = false
        }
    }
    LaunchedEffect(indicador) {
        if (indicador != null) {
            delay(700)
            indicador = null
        }
    }

    // ---------- tela cheia, orientação e Picture-in-Picture ----------
    DisposableEffect(activity) {
        val janela = activity?.window
        val controle = janela?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controle?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controle?.hide(WindowInsetsCompat.Type.systemBars())
        val ouvintePip = Consumer<PictureInPictureModeChangedInfo> { emPip = it.isInPictureInPictureMode }
        activity?.addOnPictureInPictureModeChangedListener(ouvintePip)
        onDispose {
            controle?.show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity?.removeOnPictureInPictureModeChangedListener(ouvintePip)
            activity?.setPictureInPictureParams(PictureInPictureParams.Builder().setAutoEnterEnabled(false).build())
        }
    }
    val razaoPip = Rational((proporcao * 1000).roundToInt().coerceIn(420, 2380), 1000)
    LaunchedEffect(razaoPip, tocando) {
        activity?.setPictureInPictureParams(
            PictureInPictureParams.Builder().setAspectRatio(razaoPip).setAutoEnterEnabled(tocando).build(),
        )
    }
    // Ao sair do app (sem PiP) o vídeo pausa
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (activity?.isInPictureInPictureMode != true) player.pause()
    }

    BackHandler { vm.fecharVideo() }

    fun escolherLegenda(f: Faixa?) {
        val b = player.trackSelectionParameters.buildUpon()
        if (f == null) {
            b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        } else {
            b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setOverrideForType(TrackSelectionOverride(f.grupo.mediaTrackGroup, 0))
        }
        player.trackSelectionParameters = b.build()
    }

    fun pular(deltaMs: Long) {
        player.seekTo((player.currentPosition + deltaMs).coerceIn(0L, maxOf(duracao, 0L)))
        indicador = if (deltaMs < 0) "−10 s" else "+10 s"
    }

    // ---------- interface ----------
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black).pastaBounds("video-${arquivo.id}", st, av)) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val limite = h * 0.18f
        val drag = rememberDraggableState { d -> scope.launch { arrasto.snapTo((arrasto.value + d).coerceAtLeast(0f)) } }

        Box(
            Modifier.fillMaxSize()
                .draggable(
                    state = drag,
                    orientation = Orientation.Vertical,
                    enabled = escala <= 1f && !turbo,
                    onDragStopped = { velocidadeDedo ->
                        if (arrasto.value > limite || velocidadeDedo > 2500f) {
                            vm.fecharVideo()
                        } else {
                            arrasto.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 300f))
                        }
                    },
                )
                // pinça: zoom no vídeo (e arrastar quando ampliado)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val evento = awaitPointerEvent()
                            val toques = evento.changes.count { it.pressed }
                            if (toques >= 2 || escala > 1f) {
                                val zoom = evento.calculateZoom()
                                val pan = evento.calculatePan()
                                val nova = (escala * zoom).coerceIn(1f, 4f)
                                val lx = w * (nova - 1f) / 2f
                                val ly = h * (nova - 1f) / 2f
                                deslocamento = if (nova <= 1f) {
                                    Offset.Zero
                                } else {
                                    Offset(
                                        (deslocamento.x + pan.x).coerceIn(-lx, lx),
                                        (deslocamento.y + pan.y).coerceIn(-ly, ly),
                                    )
                                }
                                escala = nova
                                evento.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        } while (evento.changes.any { it.pressed })
                        if (escala <= 1.02f) {
                            escala = 1f
                            deslocamento = Offset.Zero
                        }
                    }
                }
                // toque: controles; toque duplo: ±10 s; segurar: 2x enquanto estiver pressionado
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            tryAwaitRelease()
                            if (turbo) {
                                turbo = false
                                player.setPlaybackSpeed(velocidade)
                            }
                        },
                        onLongPress = {
                            turbo = true
                            player.setPlaybackSpeed(2f)
                        },
                        onDoubleTap = { posicaoToque ->
                            pular(if (posicaoToque.x < size.width / 2f) -10_000L else 10_000L)
                        },
                        onTap = { controles = !controles },
                    )
                },
        ) {
            // vídeo
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    scaleX = escala
                    scaleY = escala
                    translationX = deslocamento.x
                    translationY = arrasto.value + deslocamento.y
                },
                contentAlignment = Alignment.Center,
            ) {
                AndroidView(
                    factory = { c -> TextureView(c).also { tv -> player.setVideoTextureView(tv) } },
                    modifier = Modifier.aspectRatio(proporcao),
                )
            }

            if (carregando && erro == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
            }

            // legenda
            if (legendaTexto.isNotBlank() && !emPip) {
                Text(
                    legendaTexto,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.align(Alignment.BottomCenter)
                        .padding(bottom = if (controles) 96.dp else 32.dp, start = 24.dp, end = 24.dp)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }

            if (turbo) {
                Text(
                    "2x ▶▶",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 48.dp)
                        .background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
            indicador?.let {
                Text(
                    it,
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            // erro
            erro?.let { msg ->
                Column(
                    Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Não foi possível reproduzir este vídeo.", color = Color.White, textAlign = TextAlign.Center)
                    Text(msg, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
                    Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { erro = null; player.prepare() }) { Text("Tentar de novo") }
                        TextButton(onClick = { vm.fecharVideo() }) { Text("Voltar") }
                    }
                }
            }

            // controles
            AnimatedVisibility(visible = controles && !emPip && arrasto.value < 1f, modifier = Modifier.align(Alignment.TopCenter)) {
                Row(
                    Modifier.fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent)))
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { vm.fecharVideo() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Voltar", tint = Color.White)
                    }
                    Text(
                        arquivo.name, Modifier.weight(1f), color = Color.White, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall,
                    )
                    Box {
                        TextButton(onClick = { menuVelocidade = true }) {
                            Text(if (velocidade % 1f == 0f) "${velocidade.toInt()}x" else "${velocidade}x", color = Color.White)
                        }
                        DropdownMenu(expanded = menuVelocidade, onDismissRequest = { menuVelocidade = false }) {
                            VELOCIDADES.forEach { v ->
                                DropdownMenuItem(
                                    text = { Text("${v}x" + if (v == velocidade) "  ✓" else "") },
                                    onClick = { velocidade = v; player.setPlaybackSpeed(v); menuVelocidade = false },
                                )
                            }
                        }
                    }
                    Box {
                        IconButton(onClick = { menuLegendas = true }) {
                            Icon(Icons.Outlined.ClosedCaption, "Legendas", tint = Color.White)
                        }
                        DropdownMenu(expanded = menuLegendas, onDismissRequest = { menuLegendas = false }) {
                            DropdownMenuItem(
                                text = { Text("Sem legenda" + if (faixas.none { it.grupo.isSelected }) "  ✓" else "") },
                                onClick = { escolherLegenda(null); legendaTexto = ""; menuLegendas = false },
                            )
                            faixas.forEach { f ->
                                DropdownMenuItem(
                                    text = { Text(f.rotulo + if (f.grupo.isSelected) "  ✓" else "") },
                                    onClick = { escolherLegenda(f); menuLegendas = false },
                                )
                            }
                        }
                    }
                    if (activity != null) {
                        IconButton(onClick = {
                            activity.enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(razaoPip).build())
                        }) { Icon(Icons.Outlined.PictureInPictureAlt, "Picture-in-Picture", tint = Color.White) }
                        IconButton(onClick = {
                            paisagem = !paisagem
                            activity.requestedOrientation = if (paisagem) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        }) { Icon(Icons.Outlined.ScreenRotation, "Girar a tela", tint = Color.White) }
                    }
                }
            }

            AnimatedVisibility(visible = controles && !emPip && arrasto.value < 1f && erro == null, modifier = Modifier.align(Alignment.Center)) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { pular(-10_000L) }, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Outlined.Replay10, "Voltar 10 segundos", tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                    IconButton(
                        onClick = { if (player.isPlaying) player.pause() else player.play() },
                        modifier = Modifier.size(72.dp).background(Color.Black.copy(alpha = 0.45f), androidx.compose.foundation.shape.CircleShape),
                    ) {
                        Icon(
                            if (tocando) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            if (tocando) "Pausar" else "Reproduzir",
                            tint = Color.White, modifier = Modifier.size(44.dp),
                        )
                    }
                    IconButton(onClick = { pular(10_000L) }, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Outlined.Forward10, "Avançar 10 segundos", tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                }
            }

            AnimatedVisibility(visible = controles && !emPip && arrasto.value < 1f, modifier = Modifier.align(Alignment.BottomCenter)) {
                Column(
                    Modifier.fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Slider(
                        value = if (arrastando) posSlider else posicao.toFloat(),
                        onValueChange = { arrastando = true; posSlider = it },
                        onValueChangeFinished = { player.seekTo(posSlider.toLong()); posicao = posSlider.toLong(); arrastando = false },
                        valueRange = 0f..maxOf(1f, duracao.toFloat()),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(tempo(if (arrastando) posSlider.toLong() else posicao), color = Color.White, style = MaterialTheme.typography.bodySmall)
                        Text(tempo(duracao), color = Color.White, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
