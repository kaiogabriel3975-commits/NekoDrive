package com.nekodrive.app.ui.galeria

import android.app.PictureInPictureParams
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.util.Rational
import android.view.TextureView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.content.FileProvider
import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.nekodrive.app.data.drive.DriveFile
import com.nekodrive.app.ui.LocalVidro
import com.nekodrive.app.ui.NomeUmaLinha
import com.nekodrive.app.ui.Vidro
import com.nekodrive.app.ui.abrirComOutroApp
import com.nekodrive.app.ui.abrirPlayerExterno
import com.nekodrive.app.ui.compartilharArquivo
import com.nekodrive.app.ui.drive.DriveViewModel
import com.nekodrive.app.ui.drive.FotosAbertas
import com.nekodrive.app.ui.drive.emLoop
import com.nekodrive.app.ui.drive.pastaBounds
import com.nekodrive.app.ui.fonteVidro
import com.nekodrive.app.ui.player.CastControle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private val VELOCIDADES = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
private val CurvaIOS = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

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

/**
 * Efeito de fechamento integrado: a mídia encolhe de leve em direção ao centro e o topo inclina para trás em
 * perspectiva (base como referência). [progresso] vai de 0 a 1; a inclinação cresce com o quadrado dele,
 * então começa devagar e ganha velocidade (aceleração). Usado tanto no gesto quanto nos botões de voltar.
 */
private fun Modifier.efeitoFechar(progresso: () -> Float): Modifier = this
    .graphicsLayer {
        val q = progresso()
        val suave = q * q * (3f - 2f * q)
        val s = 1f - 0.07f * suave
        scaleX = s
        scaleY = s
        translationY = q * size.height * 0.10f
    }
    .graphicsLayer {
        val q = progresso()
        rotationX = 9f * q * q
        transformOrigin = TransformOrigin(0.5f, 1f)
        cameraDistance = 14f * density
    }

/**
 * Galeria em tela cheia: fotos e vídeos da pasta na mesma sequência, com deslize horizontal.
 * Fotos: zoom, giro, copiar, compartilhar, baixar, favoritar, informações e excluir. Vídeos: player com gestos.
 */
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalFoundationApi::class, UnstableApi::class)
@Composable
fun Galeria(vm: DriveViewModel, dados: FotosAbertas, st: SharedTransitionScope, av: AnimatedVisibilityScope) {
    if (dados.fotos.isEmpty()) return
    val ctx = LocalContext.current
    val activity = remember(ctx) { ctx.encontrarActivity() }
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(initialPage = dados.indice.coerceIn(0, dados.fotos.lastIndex)) { dados.fotos.size }
    val ativa = pager.settledPage.coerceIn(0, dados.fotos.lastIndex)
    val atual = dados.fotos[ativa]
    val ehVideo = atual.isVideo
    val ehGif = atual.mimeType == "image/gif"
    val estadoAtual = vm.estadoDe(atual)

    var controles by remember { mutableStateOf(true) }
    var zoomado by remember { mutableStateOf(false) }
    var emPip by remember { mutableStateOf(false) }
    var paisagem by remember { mutableStateOf(false) }
    var menu by remember { mutableIntStateOf(0) } // 0 fechado, 1 principal, 2 velocidade, 3 legendas, 4 resolução
    var arrastandoSlider by remember { mutableStateOf(false) }
    var posSlider by remember { mutableFloatStateOf(0f) }
    var confirmarExclusao by remember { mutableStateOf(false) }
    val giros = remember { mutableStateMapOf<String, Int>() }

    // progresso: 0 = galeria aberta, 1 = fechada. aparecer: opacidade do fundo preto (a mídia nunca fica transparente)
    val progresso = remember { Animatable(0f) }
    val aparecer = remember { Animatable(0f) }
    var abriu by remember { mutableStateOf(false) }
    var fechando by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        launch { aparecer.animateTo(1f, tween(280)) }
        delay(240)
        abriu = true
    }

    /** Fecha por qualquer caminho (gesto, voltar, botão) com a mesma animação integrada. */
    fun fechar() {
        if (fechando) return
        fechando = true
        vm.fecharFoto()
        scope.launch { aparecer.animateTo(0f, tween(450)) }
        scope.launch { progresso.animateTo(1f, spring(dampingRatio = 1f, stiffness = 110f)) }
    }

    // Vidro com blur da galeria: o pager é a fonte; botões e pílulas desfocam o que passa por baixo
    val vidroEstado = remember { HazeState() }
    val vidroEstilo = HazeStyle(
        backgroundColor = Color.Black,
        tint = HazeTint(Color.Black.copy(alpha = 0.35f)),
        blurRadius = 20.dp,
        noiseFactor = 0f,
    )
    val vidroLocal = remember(vidroEstado, vidroEstilo) { Vidro(vidroEstado, vidroEstilo) }

    // ---------- controlador do vídeo ativo ----------
    val ctrl: ControleVideo? = if (ehVideo) {
        remember(atual.id) { ControleVideo(ctx, vm, atual, vm.legendasDe(atual), scope) }
    } else {
        null
    }
    val ultimoCtrl = remember { mutableStateOf<ControleVideo?>(null) }
    if (ctrl != null) SideEffect { ultimoCtrl.value = ctrl }
    DisposableEffect(ctrl) {
        ctrl?.iniciar()
        onDispose { ctrl?.liberar() }
    }
    LaunchedEffect(ctrl) {
        if (ctrl != null) {
            while (true) {
                ctrl.passo(!arrastandoSlider)
                delay(300)
            }
        }
    }
    if (ctrl != null) {
        DisposableEffect(ctrl) {
            ctrl.cast.anexar()
            onDispose { }
        }
        LaunchedEffect(ctrl.cast.estado) { ctrl.aoMudarEstadoCast() }
        LaunchedEffect(ctrl.cast.transmitindo) { ctrl.retomarSeParou() }
    }

    LaunchedEffect(pager) { snapshotFlow { pager.currentPage }.collect { vm.indiceFoto(it) } }
    LaunchedEffect(atual.id) { controles = true; zoomado = false; menu = 0 }

    // Os controles do vídeo somem sozinhos durante a reprodução
    LaunchedEffect(controles, ctrl?.tocando, ctrl?.pronto, menu, arrastandoSlider) {
        if (ctrl != null && controles && ctrl.tocando && ctrl.pronto && menu == 0 && !arrastandoSlider) {
            delay(3500)
            controles = false
        }
    }
    // Mensagens dos gestos: 500 ms no total (100 entrando + 300 parada + 100 saindo)
    LaunchedEffect(ctrl?.idMensagem) {
        if (ctrl?.mensagem != null) {
            delay(400)
            ctrl.limparMensagem()
        }
    }

    // ---------- barras do sistema acompanham os controles do vídeo ----------
    val barrasVisiveis = !ehVideo || controles || ctrl == null || !ctrl.pronto || ctrl.erro != null
    LaunchedEffect(barrasVisiveis) {
        val janela = activity?.window ?: return@LaunchedEffect
        val c = WindowCompat.getInsetsController(janela, janela.decorView)
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (barrasVisiveis) c.show(WindowInsetsCompat.Type.systemBars()) else c.hide(WindowInsetsCompat.Type.systemBars())
    }

    // ---------- Picture-in-Picture e orientação ----------
    DisposableEffect(activity) {
        val ouvintePip = Consumer<PictureInPictureModeChangedInfo> { emPip = it.isInPictureInPictureMode }
        activity?.addOnPictureInPictureModeChangedListener(ouvintePip)
        onDispose {
            activity?.removeOnPictureInPictureModeChangedListener(ouvintePip)
            activity?.window?.let { WindowCompat.getInsetsController(it, it.decorView).show(WindowInsetsCompat.Type.systemBars()) }
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity?.setPictureInPictureParams(PictureInPictureParams.Builder().setAutoEnterEnabled(false).build())
        }
    }
    val razaoPip = Rational(((ctrl?.proporcao ?: (16f / 9f)) * 1000).roundToInt().coerceIn(420, 2380), 1000)
    LaunchedEffect(razaoPip, ctrl?.tocando, ehVideo) {
        activity?.setPictureInPictureParams(
            PictureInPictureParams.Builder().setAspectRatio(razaoPip)
                .setAutoEnterEnabled(ehVideo && ctrl?.tocando == true).build(),
        )
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (activity?.isInPictureInPictureMode != true) ctrl?.player?.pause()
    }
    BackHandler { fechar() }

    val copiar: () -> Unit = {
        scope.launch {
            try {
                Toast.makeText(ctx, "Copiando…", Toast.LENGTH_SHORT).show()
                val arquivo = vm.copiarParaCache(atual)
                val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", arquivo)
                val area = ctx.getSystemService(ClipboardManager::class.java)
                area.setPrimaryClip(ClipData.newUri(ctx.contentResolver, atual.name, uri))
                Toast.makeText(ctx, "Imagem copiada", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(ctx, "Não foi possível copiar: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    val compartilhar: () -> Unit = { scope.launch { compartilharArquivo(ctx, vm, atual) } }

    CompositionLocalProvider(LocalVidro provides vidroLocal) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val h = constraints.maxHeight.toFloat()
            val drag = rememberDraggableState { d ->
                if (!fechando) scope.launch { progresso.snapTo((progresso.value + d / (h * 0.45f)).coerceIn(0f, 1f)) }
            }

            Box(
                Modifier.fillMaxSize()
                    .background(Color.Black.copy(alpha = aparecer.value * (1f - 0.55f * progresso.value)))
                    .draggable(
                        state = drag,
                        orientation = Orientation.Vertical,
                        enabled = !zoomado && ctrl?.turbo != true && !fechando,
                        onDragStopped = { velocidadeDedo ->
                            if (progresso.value > 0.38f || velocidadeDedo > 2500f) {
                                fechar()
                            } else {
                                progresso.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 260f))
                            }
                        },
                    ),
            ) {
                Box(Modifier.fillMaxSize().fonteVidro()) {
                    HorizontalPager(
                        state = pager,
                        modifier = Modifier.fillMaxSize(),
                        beyondViewportPageCount = 1,
                        pageSpacing = 16.dp,
                        // Física de aceleração: a velocidade do dedo define até onde o álbum desliza e a mola assenta
                        flingBehavior = PagerDefaults.flingBehavior(
                            state = pager,
                            pagerSnapDistance = PagerSnapDistance.atMost(3),
                            decayAnimationSpec = rememberSplineBasedDecay(),
                            snapAnimationSpec = spring(dampingRatio = 0.82f, stiffness = 260f),
                        ),
                        userScrollEnabled = !zoomado && !fechando,
                        key = { dados.fotos[it].id },
                    ) { i ->
                        val f = dados.fotos[i]
                        Box(
                            Modifier.fillMaxSize().graphicsLayer {
                                // Entre foto e vídeo, a página se dissolve com blur enquanto o deslize acontece
                                val paginaAtual = pager.currentPage
                                val fracao = pager.currentPageOffsetFraction
                                val parceiro = if (i == paginaAtual) paginaAtual + (if (fracao > 0f) 1 else -1) else paginaAtual
                                val tipoDiferente = dados.fotos.getOrNull(parceiro)?.let { it.isVideo != f.isVideo } == true
                                if (tipoDiferente) {
                                    val d = abs(i - paginaAtual - fracao).coerceIn(0f, 1f)
                                    alpha = 1f - d
                                    val raio = d * 16.dp.toPx()
                                    renderEffect = if (raio > 0.5f) BlurEffect(raio, raio, TileMode.Decal) else null
                                }
                            },
                        ) {
                            if (f.isVideo) {
                                PaginaVideo(
                                    f = f,
                                    ctrl = if (i == ativa) ctrl else null,
                                    token = vm.token,
                                    compartilhar = i == ativa,
                                    progresso = { progresso.value },
                                    st = st,
                                    av = av,
                                    aoZoom = { zoomado = it },
                                    aoTocar = { controles = !controles },
                                )
                            } else {
                                val local = remember(f.id, vm.versaoCache) { vm.arquivoLocal(f) }
                                PaginaFoto(
                                    f = f,
                                    arquivoLocal = local,
                                    token = vm.token,
                                    giro = giros[f.id] ?: 0,
                                    ehAtual = i == ativa,
                                    compartilhar = i == ativa,
                                    progresso = { progresso.value },
                                    st = st,
                                    av = av,
                                    aoZoom = { zoomado = it },
                                    aoTocar = { controles = !controles },
                                )
                            }
                        }
                    }
                }

                // ---------- interface das fotos ----------
                val chromeOk = abriu && !fechando && !emPip
                val mostrarFoto = controles && !ehVideo && chromeOk
                Dissolver(mostrarFoto, Modifier.align(Alignment.TopCenter)) {
                    Box(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
                        CirculoVidro(aoClicar = { fechar() }, modifier = Modifier.align(Alignment.CenterStart)) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Voltar", tint = Color.White)
                        }
                        NomeUmaLinha(
                            atual.name,
                            Modifier.align(Alignment.Center).padding(horizontal = 64.dp),
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            centralizar = true,
                        )
                    }
                }
                Dissolver(mostrarFoto, Modifier.align(Alignment.BottomCenter)) {
                    PilulaVidro(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
                        BotaoVidro(Icons.Rounded.RotateRight, "Girar", { giros[atual.id] = (giros[atual.id] ?: 0) + 1 })
                        // GIFs não têm botão de copiar
                        if (!ehGif) BotaoVidro(Icons.Rounded.ContentCopy, "Copiar imagem", copiar)
                        BotaoVidro(Icons.Rounded.Share, "Compartilhar", compartilhar)
                        BotaoVidro(
                            if (estadoAtual.noCache) Icons.Rounded.DownloadDone else Icons.Rounded.Download,
                            if (estadoAtual.noCache) "Salvo no cache" else "Baixar",
                            { vm.baixar(atual) },
                        )
                        BotaoVidro(
                            if (estadoAtual.favorito) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                            if (estadoAtual.favorito) "Remover dos favoritos" else "Favoritar",
                            { vm.alternarFavorito(atual) },
                            if (estadoAtual.favorito) Color(0xFFFFC107) else Color.White,
                        )
                        BotaoVidro(Icons.Rounded.Info, "Informações", { vm.abrirDetalhes(atual) })
                        BotaoVidro(Icons.Rounded.Delete, "Mover para a lixeira", { confirmarExclusao = true })
                    }
                }

                // ---------- interface dos vídeos ----------
                val c = ctrl ?: ultimoCtrl.value
                val mostrarVideo = ehVideo && ctrl != null && controles && ctrl.pronto && chromeOk &&
                    !ctrl.cast.transmitindo && ctrl.erro == null
                Dissolver(mostrarVideo, Modifier.fillMaxSize()) {
                    if (c != null) {
                        CirculoVidro(
                            aoClicar = { fechar() },
                            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp),
                        ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Voltar", tint = Color.White) }

                        Column(
                            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                CirculoVidro(aoClicar = { c.alternar() }) {
                                    Icon(
                                        if (c.tocando) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                        if (c.tocando) "Pausar" else "Reproduzir",
                                        tint = Color.White,
                                    )
                                }
                                Column(Modifier.weight(1f)) {
                                    Slider(
                                        value = if (arrastandoSlider) posSlider else c.posicao.toFloat(),
                                        onValueChange = { arrastandoSlider = true; posSlider = it },
                                        onValueChangeFinished = { c.buscar(posSlider.toLong()); arrastandoSlider = false },
                                        valueRange = 0f..maxOf(1f, c.duracao.toFloat()),
                                        colors = SliderDefaults.colors(
                                            thumbColor = Color.White,
                                            activeTrackColor = Color.White,
                                            inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                                        ),
                                    )
                                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(tempo(if (arrastandoSlider) posSlider.toLong() else c.posicao), color = Color.White, style = MaterialTheme.typography.bodySmall)
                                        Text(tempo(c.duracao), color = Color.White, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                CirculoVidro(aoClicar = {
                                    paisagem = !paisagem
                                    activity?.requestedOrientation = if (paisagem) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                    else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                }) { Icon(Icons.Rounded.ScreenRotation, "Girar a tela", tint = Color.White) }
                            }

                            Box(Modifier.fillMaxWidth()) {
                                PilulaVidro(Modifier.align(Alignment.Center)) {
                                    BotaoVidro(
                                        if (estadoAtual.favorito) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                        if (estadoAtual.favorito) "Remover dos favoritos" else "Favoritar",
                                        { vm.alternarFavorito(atual) },
                                        if (estadoAtual.favorito) Color(0xFFFFC107) else Color.White,
                                    )
                                    BotaoVidro(Icons.Rounded.Share, "Compartilhar", compartilhar)
                                    BotaoVidro(
                                        if (estadoAtual.noCache) Icons.Rounded.DownloadDone else Icons.Rounded.Download,
                                        if (estadoAtual.noCache) "Salvo no cache" else "Baixar",
                                        { vm.baixar(atual) },
                                    )
                                    if (activity != null) {
                                        BotaoVidro(Icons.Rounded.PictureInPictureAlt, "Picture-in-Picture", {
                                            activity.enterPictureInPictureMode(
                                                PictureInPictureParams.Builder().setAspectRatio(razaoPip).build(),
                                            )
                                        })
                                    }
                                    BotaoVidro(Icons.Rounded.Delete, "Mover para a lixeira", { confirmarExclusao = true })
                                }
                                // Menu de três pontos (o menu em si é desenhado com blur mais abaixo)
                                CirculoVidro(aoClicar = { menu = 1 }, modifier = Modifier.align(Alignment.CenterEnd)) {
                                    Icon(Icons.Rounded.MoreVert, "Mais opções", tint = Color.White)
                                }
                            }
                        }
                    }
                }

                // Menu com blur: velocidade, resolução, transmitir, legendas, informações e player externo
                MenuVidro(
                    aberto = menu != 0 && c != null,
                    aoFechar = { menu = 0 },
                    modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 16.dp, bottom = 130.dp),
                ) {
                    if (c != null) {
                        when (menu) {
                            1 -> {
                                ItemMenuVidro("Velocidade", { menu = 2 })
                                if (c.qualidades.size > 1) ItemMenuVidro("Resolução", { menu = 4 })
                                ItemMenuVidro("Transmitir para a TV", {
                                    menu = 0
                                    if (activity is FragmentActivity) c.cast.escolherDispositivo(activity)
                                })
                                ItemMenuVidro("Legendas", { menu = 3 })
                                ItemMenuVidro("Informações", { menu = 0; vm.abrirDetalhes(atual, c.extrasInfo()) })
                                ItemMenuVidro("Abrir em outro player", { menu = 0; abrirPlayerExterno(ctx, vm, atual) })
                            }
                            2 -> VELOCIDADES.forEach { v ->
                                ItemMenuVidro("${v}x", { c.definirVelocidade(v); menu = 0 }, marcado = v == c.velocidade)
                            }
                            3 -> {
                                ItemMenuVidro("Sem legenda", { c.escolherLegenda(null); menu = 0 }, marcado = c.faixas.none { it.grupo.isSelected })
                                c.faixas.forEach { fx ->
                                    ItemMenuVidro(fx.rotulo, { c.escolherLegenda(fx); menu = 0 }, marcado = fx.grupo.isSelected)
                                }
                            }
                            4 -> {
                                ItemMenuVidro("Automática", { c.escolherQualidade(null); menu = 0 }, marcado = c.qualidadeAutomatica)
                                c.qualidades.forEach { q ->
                                    ItemMenuVidro(
                                        q.rotulo,
                                        { c.escolherQualidade(q); menu = 0 },
                                        marcado = !c.qualidadeAutomatica && q === c.qualidadeAtual,
                                    )
                                }
                            }
                        }
                    }
                }

                // Legenda do vídeo
                if (ctrl != null && ctrl.legendaTexto.isNotBlank() && !emPip) {
                    Text(
                        ctrl.legendaTexto,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                            .padding(bottom = if (mostrarVideo) 170.dp else 32.dp, start = 24.dp, end = 24.dp)
                            .background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }

                // Mensagem curta dos gestos (+ 10 segundos, - 10 segundos, Velocidade 2x)
                val msg = ctrl?.mensagem
                var ultimaMsg by remember { mutableStateOf("") }
                LaunchedEffect(msg) { if (msg != null) ultimaMsg = msg }
                AnimatedVisibility(
                    visible = msg != null && !emPip,
                    enter = fadeIn(tween(100)),
                    exit = fadeOut(tween(100)),
                    modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 12.dp),
                ) {
                    Text(
                        msg ?: ultimaMsg,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.clip(RoundedCornerShape(20.dp))
                            .background(Color.Black.copy(alpha = 0.5f))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }

                // Ícone de carregamento sempre que o vídeo estiver carregando (inclusive ao reencher o buffer)
                if (ctrl != null && (ctrl.carregando || !ctrl.pronto) && ctrl.erro == null) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
                }
                ctrl?.erro?.let { msgErro ->
                    Column(
                        Modifier.align(Alignment.Center).padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("Não foi possível reproduzir este vídeo.", color = Color.White, textAlign = TextAlign.Center)
                        Text(
                            msgErro, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp),
                        )
                        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { ctrl.tentarNovamente() }) { Text("Tentar de novo") }
                            if (!ctrl.convertida) TextButton(onClick = { ctrl.tentarConvertida() }) { Text("Versão convertida") }
                        }
                        TextButton(onClick = { abrirPlayerExterno(ctx, vm, atual) }) { Text("Abrir em outro player") }
                        TextButton(onClick = { scope.launch { abrirComOutroApp(ctx, vm, atual) } }) { Text("Baixar e abrir em outro app") }
                    }
                }

                if (ctrl != null && ctrl.cast.transmitindo) {
                    TelaTransmissao(ctrl.cast, onVoltar = { fechar() })
                }
            }
        }
    }

    if (confirmarExclusao) {
        AlertDialog(
            onDismissRequest = { confirmarExclusao = false },
            title = { Text("Mover para a lixeira?") },
            text = { Text("“${atual.name}” vai para a lixeira do Drive. Dá para restaurar depois.") },
            confirmButton = {
                TextButton(onClick = { confirmarExclusao = false; vm.excluirDaGaleria(atual) }) { Text("Mover") }
            },
            dismissButton = { TextButton(onClick = { confirmarExclusao = false }) { Text("Cancelar") } },
        )
    }
}

/** Uma foto (ou GIF/imagem animada): zoom com pinça ou toque duplo, giro de 90° e toque para a interface. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun PaginaFoto(
    f: DriveFile,
    arquivoLocal: java.io.File?,
    token: String?,
    giro: Int,
    ehAtual: Boolean,
    compartilhar: Boolean,
    progresso: () -> Float,
    st: SharedTransitionScope,
    av: AnimatedVisibilityScope,
    aoZoom: (Boolean) -> Unit,
    aoTocar: () -> Unit,
) {
    val ctx = LocalContext.current
    val escopo = rememberCoroutineScope()
    var escala by remember { mutableFloatStateOf(1f) }
    var deslocamento by remember { mutableStateOf(Offset.Zero) }
    var proporcao by remember { mutableStateOf<Float?>(null) }
    val rotacao = remember { Animatable(giro * 90f) }
    val ajuste = remember { Animatable(1f) }
    var giroAnterior by remember { mutableIntStateOf(giro) }

    // Cantos arredondados da miniatura viram cantos retos aos poucos (e o contrário ao fechar)
    val raio by av.transition.animateDp(transitionSpec = { tween(500, easing = CurvaIOS) }, label = "raio") {
        if (it == EnterExitState.Visible) 0.dp else 14.dp
    }
    val origem = if (compartilhar) Modifier.pastaBounds("foto-${f.id}", st, av) else Modifier

    BoxWithConstraints(Modifier.fillMaxSize().then(origem)) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()

        // Escala exata para a foto girada caber sem bordas desnecessárias (usa a proporção real da imagem)
        val alvoAjuste = if (giro % 2 != 0) {
            val r = proporcao
            if (r == null) {
                minOf(w / h, h / w)
            } else {
                val larguraExibida = if (w / h > r) h * r else w
                val alturaExibida = if (w / h > r) h else w / r
                minOf(w / alturaExibida, h / larguraExibida)
            }
        } else {
            1f
        }
        LaunchedEffect(giro, proporcao) {
            if (giro != giroAnterior) {
                giroAnterior = giro
                launch { rotacao.animateTo(giro * 90f, tween(520, easing = FastOutSlowInEasing)) }
                // zoom out no meio do giro e acomoda no ajuste exato
                val mergulho = minOf(ajuste.value, alvoAjuste) * 0.88f
                ajuste.animateTo(alvoAjuste, keyframes { durationMillis = 520; mergulho at 220 })
            } else {
                rotacao.snapTo(giro * 90f)
                ajuste.snapTo(alvoAjuste)
            }
        }

        fun alternarZoom() {
            escopo.launch {
                val inicio = escala
                val alvo = if (inicio > 1.05f) 1f else 2.5f
                val inicioDesloc = deslocamento
                animate(0f, 1f, animationSpec = spring(dampingRatio = 0.9f, stiffness = 260f)) { t, _ ->
                    escala = inicio + (alvo - inicio) * t
                    deslocamento = if (alvo == 1f) Offset(inicioDesloc.x * (1 - t), inicioDesloc.y * (1 - t)) else Offset.Zero
                }
                if (ehAtual) aoZoom(alvo > 1f)
            }
        }

        Box(
            Modifier.fillMaxSize()
                .clip(if (compartilhar) RoundedCornerShape(raio) else RectangleShape)
                .efeitoFechar(progresso),
        ) {
            Box(
                Modifier.fillMaxSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val evento = awaitPointerEvent()
                                val toques = evento.changes.count { it.pressed }
                                if (toques >= 2 || escala > 1f) {
                                    val zoom = evento.calculateZoom()
                                    val pan = evento.calculatePan()
                                    val nova = (escala * zoom).coerceIn(1f, 6f)
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
                                    if (ehAtual) aoZoom(escala > 1f)
                                    evento.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            } while (evento.changes.any { it.pressed })
                            if (escala <= 1.02f) {
                                escala = 1f
                                deslocamento = Offset.Zero
                                if (ehAtual) aoZoom(false)
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { aoTocar() }, onDoubleTap = { alternarZoom() })
                    }
                    .graphicsLayer {
                        scaleX = escala * ajuste.value
                        scaleY = escala * ajuste.value
                        translationX = deslocamento.x
                        translationY = deslocamento.y
                        rotationZ = rotacao.value
                    },
            ) {
                val original = ImageRequest.Builder(ctx)
                    .data(arquivoLocal ?: "https://www.googleapis.com/drive/v3/files/${f.id}?alt=media")
                    .apply { if (token != null) addHeader("Authorization", "Bearer $token") }
                    .size(2400, 2400)
                    .emLoop() // GIFs e imagens animadas repetem enquanto a tela estiver aberta
                    .crossfade(true)
                    .build()
                SubcomposeAsyncImage(
                    model = original,
                    contentDescription = f.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    onSuccess = { s ->
                        val tam = s.painter.intrinsicSize
                        if (tam.isSpecified && tam.width > 0f && tam.height > 0f) proporcao = tam.width / tam.height
                    },
                    loading = {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            if (f.thumbnailLink != null) {
                                val miniatura = ImageRequest.Builder(ctx)
                                    .data(f.thumbnailLink)
                                    .apply { if (token != null) addHeader("Authorization", "Bearer $token") }
                                    .build()
                                AsyncImage(miniatura, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                            }
                            CircularProgressIndicator(Modifier.size(32.dp), color = Color.White)
                        }
                    },
                )
            }
        }
    }
}

/** Página de um vídeo: miniatura até o player ficar pronto; só o vídeo ativo tem player e gestos. */
@OptIn(ExperimentalSharedTransitionApi::class, UnstableApi::class)
@Composable
private fun PaginaVideo(
    f: DriveFile,
    ctrl: ControleVideo?,
    token: String?,
    compartilhar: Boolean,
    progresso: () -> Float,
    st: SharedTransitionScope,
    av: AnimatedVisibilityScope,
    aoZoom: (Boolean) -> Unit,
    aoTocar: () -> Unit,
) {
    val ctx = LocalContext.current
    val escopo = rememberCoroutineScope()
    var escala by remember { mutableFloatStateOf(1f) }
    var deslocamento by remember { mutableStateOf(Offset.Zero) }

    val raio by av.transition.animateDp(transitionSpec = { tween(500, easing = CurvaIOS) }, label = "raio") {
        if (it == EnterExitState.Visible) 0.dp else 14.dp
    }
    val origem = if (compartilhar) Modifier.pastaBounds("video-${f.id}", st, av) else Modifier

    BoxWithConstraints(Modifier.fillMaxSize().then(origem)) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()

        fun alternarZoom() {
            escopo.launch {
                val inicio = escala
                val alvo = if (inicio > 1.05f) 1f else 2f
                val inicioDesloc = deslocamento
                animate(0f, 1f, animationSpec = spring(dampingRatio = 0.9f, stiffness = 260f)) { t, _ ->
                    escala = inicio + (alvo - inicio) * t
                    deslocamento = if (alvo == 1f) Offset(inicioDesloc.x * (1 - t), inicioDesloc.y * (1 - t)) else Offset.Zero
                }
                aoZoom(alvo > 1f)
            }
        }

        val gestos = if (ctrl == null) {
            Modifier.pointerInput(Unit) { detectTapGestures(onTap = { aoTocar() }) }
        } else {
            Modifier
                // pinça: zoom no vídeo (e arrastar quando ampliado)
                .pointerInput(ctrl) {
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
                                aoZoom(escala > 1f)
                                evento.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        } while (evento.changes.any { it.pressed })
                        if (escala <= 1.02f) {
                            escala = 1f
                            deslocamento = Offset.Zero
                            aoZoom(false)
                        }
                    }
                }
                // toque: interface; segurar: 2x; toque duplo: laterais ±10 s e centro aplica/reduz o zoom
                .pointerInput(ctrl) {
                    detectTapGestures(
                        onPress = {
                            tryAwaitRelease()
                            ctrl.desligarTurbo()
                        },
                        onLongPress = { ctrl.ligarTurbo() },
                        onDoubleTap = { toque ->
                            val terco = size.width / 3f
                            when {
                                toque.x < terco -> ctrl.pular(-10_000L)
                                toque.x > 2 * terco -> ctrl.pular(10_000L)
                                else -> alternarZoom()
                            }
                        },
                        onTap = { aoTocar() },
                    )
                }
        }
        Box(
            Modifier.fillMaxSize()
                .clip(if (compartilhar) RoundedCornerShape(raio) else RectangleShape)
                .efeitoFechar(progresso),
        ) {
            Box(
                Modifier.fillMaxSize().then(gestos).graphicsLayer {
                    scaleX = escala
                    scaleY = escala
                    translationX = deslocamento.x
                    translationY = deslocamento.y
                },
                contentAlignment = Alignment.Center,
            ) {
                if (f.thumbnailLink != null) {
                    val miniatura = ImageRequest.Builder(ctx)
                        .data(f.thumbnailLink)
                        .apply { if (token != null) addHeader("Authorization", "Bearer $token") }
                        .build()
                    AsyncImage(miniatura, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                }
                if (ctrl != null) {
                    key(ctrl) {
                        AndroidView(
                            factory = { c -> TextureView(c).also { tv -> ctrl.player.setVideoTextureView(tv) } },
                            modifier = Modifier.aspectRatio(ctrl.proporcao),
                        )
                    }
                }
            }
        }
    }
}

/** Tela mostrada enquanto o vídeo toca na TV: controles remotos e botão para parar. */
@Composable
private fun TelaTransmissao(cast: CastControle, onVoltar: () -> Unit) {
    var arrastando by remember { mutableStateOf(false) }
    var posSlider by remember { mutableFloatStateOf(0f) }
    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .pointerInput(Unit) { detectTapGestures { } }, // impede toques de chegarem ao player escondido
    ) {
        CirculoVidro(aoClicar = onVoltar, modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp)) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Voltar", tint = Color.White)
        }
        Column(
            Modifier.align(Alignment.Center).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(Icons.Rounded.Cast, null, tint = Color.White, modifier = Modifier.size(48.dp))
            Text(
                "Transmitindo para " + cast.dispositivo.ifEmpty { "a TV" },
                color = Color.White, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center,
            )
            Slider(
                value = if (arrastando) posSlider else cast.posicao.toFloat(),
                onValueChange = { arrastando = true; posSlider = it },
                onValueChangeFinished = { cast.buscar(posSlider.toLong()); arrastando = false },
                valueRange = 0f..maxOf(1f, cast.duracao.toFloat()),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(tempo(if (arrastando) posSlider.toLong() else cast.posicao), color = Color.White, style = MaterialTheme.typography.bodySmall)
                Text(tempo(cast.duracao), color = Color.White, style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { cast.pular(-10_000L) }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.Replay10, "Voltar 10 segundos", tint = Color.White, modifier = Modifier.size(36.dp))
                }
                IconButton(
                    onClick = { cast.alternar() },
                    modifier = Modifier.size(72.dp).background(Color.White.copy(alpha = 0.15f), CircleShape),
                ) {
                    Icon(
                        if (cast.tocando) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        if (cast.tocando) "Pausar" else "Reproduzir",
                        tint = Color.White, modifier = Modifier.size(44.dp),
                    )
                }
                IconButton(onClick = { cast.pular(10_000L) }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.Forward10, "Avançar 10 segundos", tint = Color.White, modifier = Modifier.size(36.dp))
                }
            }
            TextButton(onClick = { cast.parar(encerrarSessao = true) }) { Text("Parar transmissão") }
        }
    }
}
