package com.nekodrive.app.ui.galeria

import android.Manifest
import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import com.nekodrive.app.ReprodutorSegundoPlano
import com.nekodrive.app.ui.drive.CoresMidia
import com.nekodrive.app.ui.drive.coresDaImagem
import kotlinx.coroutines.Job
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
import androidx.compose.foundation.layout.requiredSize
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
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
import com.nekodrive.app.ui.Animacoes
import com.nekodrive.app.ui.LocalVibrar
import com.nekodrive.app.ui.LocalVidro
import com.nekodrive.app.ui.NomeUmaLinha
import com.nekodrive.app.ui.Vidro
import com.nekodrive.app.ui.abrirComOutroApp
import com.nekodrive.app.ui.abrirPlayerExterno
import com.nekodrive.app.ui.compartilharArquivo
import com.nekodrive.app.ui.drive.DriveViewModel
import com.nekodrive.app.ui.drive.FotosAbertas
import com.nekodrive.app.ui.drive.emLoop
import com.nekodrive.app.ui.drive.ProporcoesMidia
import com.nekodrive.app.ui.drive.TempoGaleria
import com.nekodrive.app.ui.drive.midiaBounds
import com.nekodrive.app.ui.fonteVidro
import com.nekodrive.app.ui.player.CastControle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
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
 * então começa devagar e ganha velocidade (aceleração). [inclinar] (0 a 1 a 0) é a inclinação que acompanha a
 * animação de fechar até a miniatura. Deve ficar FORA do clip da mídia, senão a inclinação é cortada no formato dela.
 */
private fun Modifier.efeitoFechar(arrasto: () -> Float, encolher: () -> Float, inclinar: () -> Float): Modifier = this
    .graphicsLayer {
        // encolher: diminui com mola (elástico); o deslize vertical segue o dedo, sem elasticidade
        val e = encolher()
        val s = 1f - 0.15f * e
        scaleX = s
        scaleY = s
        translationY = arrasto() * size.height * 0.10f
    }
    .graphicsLayer {
        val q = arrasto()
        // gesto de arrastar (q) + inclinação que acompanha a volta à miniatura: sobe, passa do meio e assenta (inclinar)
        rotationX = 9f * q * q + 14f * inclinar()
        transformOrigin = TransformOrigin(0.5f, 1f)
        cameraDistance = 14f * density
    }

/**
 * Galeria em tela cheia: fotos e vídeos da pasta na mesma sequência, com deslize horizontal.
 * Fotos: zoom, copiar, compartilhar, baixar, favoritar, informações e excluir. Vídeos: player com gestos.
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
    // luz do ambiente para os reflexos das pílulas: posição e cor das mídias visíveis no pager
    val luz = remember { AmbienteLuz().also { it.zoomado = { zoomado } } }
    luz.pager = pager
    luz.fotos = dados.fotos
    luz.espaco = with(LocalDensity.current) { 16.dp.toPx() }
    luz.raioDp = 520f // distância grande o bastante para um vídeo 16:9 centralizado ainda acender a barra
    var emPip by remember { mutableStateOf(false) }
    var paisagem by remember { mutableStateOf(false) }
    var menu by remember { mutableIntStateOf(0) } // 0 fechado, 1 principal, 2 velocidade, 3 legendas, 4 resolução
    var arrastandoSlider by remember { mutableStateOf(false) }
    var posSlider by remember { mutableFloatStateOf(0f) }
    var confirmarExclusao by remember { mutableStateOf(false) }

    // progresso: 0 = galeria aberta, 1 = fechada. aparecer: opacidade do fundo preto (a mídia nunca fica transparente)
    val progresso = remember { Animatable(0f) }
    val aparecer = remember { Animatable(0f) }
    val inclinar = remember { Animatable(0f) }
    val encolher = remember { Animatable(0f) }
    val ctrlRef = remember { mutableStateOf<ControleVideo?>(null) } // o player só existe mais abaixo; fechar() precisa dele
    var abriu by remember { mutableStateOf(false) }
    var fechando by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        launch { aparecer.animateTo(1f, Animacoes.espec(tween(280), tween(120))) }
        delay(Animacoes.ms(240).toLong())
        abriu = true
    }
    DisposableEffect(Unit) { onDispose { TempoGaleria.fechando = false } }
    // a mídia diminui com mola atrás do arrasto (e volta com mola ao soltar)
    LaunchedEffect(Unit) {
        snapshotFlow { progresso.value }.collectLatest { q ->
            encolher.animateTo(q, Animacoes.espec(spring(dampingRatio = 0.8f, stiffness = 170f), tween(100)))
        }
    }

    /** Fecha por qualquer caminho (gesto, voltar, botão) com a mesma animação integrada. */
    fun fechar(velocidade: Float = 0f) {
        if (fechando) return
        fechando = true
        // Duração da volta: 350 ms ao fechar por botão; quanto mais longe e mais rápido o deslize, mais curta
        val longe = progresso.value
        val rapidez = (velocidade / 3500f).coerceIn(0f, 1f)
        val ms = Animacoes.ms((350f * (1f - 0.35f * longe) * (1f - 0.30f * rapidez)).toInt().coerceIn(180, 350), barato = 180)
        TempoGaleria.voltaMs = ms
        TempoGaleria.fechando = true
        ctrlRef.value?.player?.pause() // ao soltar o dedo e começar a fechar, o vídeo pausa
        vm.fecharFoto()
        // a mídia volta em [ms] e fica mais 0,2 s sobre a miniatura antes da troca
        scope.launch { aparecer.animateTo(0f, Animacoes.espec(tween(ms + 100), tween(ms))) }
        scope.launch { progresso.animateTo(0f, Animacoes.espec(tween(minOf(ms, 300), easing = CurvaIOS), tween(ms))) }
        // a inclinação só existe nas animações normais (nas baratas e sem animações a mídia volta reta)
        if (Animacoes.normais) {
            scope.launch {
                inclinar.animateTo(1f, tween((ms * 0.43f).toInt(), easing = FastOutSlowInEasing))
                inclinar.animateTo(0f, tween((ms * 0.57f).toInt(), easing = CurvaIOS))
            }
        }
    }

    // Vidro com blur da galeria: o pager é a fonte; botões e pílulas desfocam o que passa por baixo
    val vidroEstado = remember { HazeState() }
    val vidroEstilo = HazeStyle(
        backgroundColor = Color.Black,
        tint = HazeTint(Color.Black.copy(alpha = 0.35f)),
        blurRadius = 20.dp * (when (vm.blurIntensidade) { 0 -> 0.5f; 2 -> 1.5f; else -> 1f }),
        noiseFactor = 0f,
    )
    val vidroLocal = remember(vidroEstado, vidroEstilo) { Vidro(vidroEstado, vidroEstilo, fundoSolido = Color.Black) }

    // ---------- controlador do vídeo ativo ----------
    val ctrl: ControleVideo? = if (ehVideo) {
        remember(atual.id) { ControleVideo(ctx, vm, atual, vm.legendasDe(atual), scope) }
    } else {
        null
    }
    val ultimoCtrl = remember { mutableStateOf<ControleVideo?>(null) }
    if (ctrl != null) SideEffect { ultimoCtrl.value = ctrl }
    SideEffect { ctrlRef.value = ctrl }
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

    // Reflexos reativos ao vídeo: enquanto toca, 4 vezes por segundo uma amostra minúscula do quadro atual
    // atualiza as cores que iluminam o vidro (pausado, uma amostra basta)
    luz.videoTocando = ctrl?.tocando == true
    // Só há vidro para iluminar quando os controles estão na tela: com eles ocultos (ou em PiP) a amostragem
    // para. Ao voltarem, o efeito reinicia e a primeira amostra sai na hora, antes de a pílula aparecer.
    val vidroVisivel = controles && !emPip
    LaunchedEffect(ctrl, ctrl?.pronto, ctrl?.tocando, atual.id, vidroVisivel) {
        val c = ctrl ?: return@LaunchedEffect
        if (!vidroVisivel) return@LaunchedEffect
        while (c.pronto) {
            val tv = c.textura
            if (tv != null && tv.isAvailable) {
                val quadro = runCatching { tv.getBitmap(24, 14) }.getOrNull()
                if (quadro != null) {
                    val cores = coresDaImagem(quadro)
                    if (cores.dominante != Color.Unspecified) luz.vivas[atual.id] = cores
                    quadro.recycle()
                }
            }
            if (!c.tocando) break
            delay(250)
        }
    }

    // Os controles do vídeo somem sozinhos durante a reprodução
    LaunchedEffect(controles, ctrl?.tocando, ctrl?.pronto, menu, arrastandoSlider, vm.controlesMs) {
        if (ctrl != null && vm.controlesMs > 0 && controles && ctrl.tocando && ctrl.pronto && menu == 0 && !arrastandoSlider) {
            delay(vm.controlesMs.toLong())
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
    // Entrou no Picture-in-Picture tocando: cria o serviço com a notificação (o app ainda está visível, então o
    // sistema permite). Se o PiP for fechado no X, o áudio segue e os controles ficam na notificação.
    LaunchedEffect(emPip) {
        val c = ctrl
        if (emPip && c != null && c.tocando) {
            ReprodutorSegundoPlano.iniciar(ctx, c.player, atual.name) { c.salvarAgora() }
        }
    }
    // Voltou para o app (PiP ampliado ou app reaberto): a notificação sai, o vídeo segue na tela
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { ReprodutorSegundoPlano.parar(ctx) }
    DisposableEffect(Unit) { onDispose { ReprodutorSegundoPlano.parar(ctx) } }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        val p = ctrl?.player
        if (activity?.isInPictureInPictureMode == true) {
            // PiP fechado: só continua se o serviço está de pé e o vídeo estava tocando
            if (p != null && !(p.isPlaying && ReprodutorSegundoPlano.ativo)) {
                p.pause()
                ReprodutorSegundoPlano.parar(ctx)
            }
        } else {
            p?.pause()
        }
    }
    BackHandler { fechar() }

    // A galeria tem fundo preto: no tema claro os ícones da barra de status e de navegação seriam escuros e sumiriam.
    // Enquanto ela está aberta os ícones ficam claros; ao começar a fechar voltam ao padrão do tema.
    val temaEscuro = MaterialTheme.colorScheme.background.luminance() < 0.5f
    DisposableEffect(activity) {
        val janela = activity?.window
        val c = janela?.let { WindowCompat.getInsetsController(it, it.decorView) }
        c?.isAppearanceLightStatusBars = false
        c?.isAppearanceLightNavigationBars = false
        onDispose {
            c?.isAppearanceLightStatusBars = !temaEscuro
            c?.isAppearanceLightNavigationBars = !temaEscuro
        }
    }
    LaunchedEffect(fechando) {
        if (fechando) {
            val janela = activity?.window ?: return@LaunchedEffect
            val c = WindowCompat.getInsetsController(janela, janela.decorView)
            c.isAppearanceLightStatusBars = !temaEscuro
            c.isAppearanceLightNavigationBars = !temaEscuro
        }
    }

    // Primeira vez que um vídeo abre: pede a permissão de notificações (Android 13+) para os controles em segundo plano
    val pedirNotificacao = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(ehVideo) {
        if (ehVideo && Build.VERSION.SDK_INT >= 33 && !vm.pediuNotificacao &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            vm.marcarPediuNotificacao()
            pedirNotificacao.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

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

    CompositionLocalProvider(
        LocalVidro provides vidroLocal,
        LocalLuz provides (if (vm.reflexoPilula) luz else null),
        LocalLuzSoToolbars provides vm.reflexoSoToolbars,
    ) {
        BoxWithConstraints(
            Modifier.fillMaxSize().onPlaced { c ->
                val p = c.positionInRoot()
                luz.origemX = p.x; luz.origemY = p.y
            },
        ) {
            val h = constraints.maxHeight.toFloat()
            luz.largura = constraints.maxWidth.toFloat()
            luz.altura = h
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
                                fechar(velocidadeDedo)
                            } else {
                                progresso.animateTo(0f, Animacoes.espec(spring(dampingRatio = 1f, stiffness = 300f), tween(120)))
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
                            snapAnimationSpec = Animacoes.espec(spring(dampingRatio = 0.82f, stiffness = 260f), tween(150)),
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
                                if (tipoDiferente && !Animacoes.sem) {
                                    val d = abs(i - paginaAtual - fracao).coerceIn(0f, 1f)
                                    alpha = 1f - d
                                    val raio = d * 16.dp.toPx()
                                    renderEffect = if (raio > 0.5f && Animacoes.normais) BlurEffect(raio, raio, TileMode.Decal) else null
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
                                    inclinar = { inclinar.value },
                                    encolher = { encolher.value },
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
                                    ehAtual = i == ativa,
                                    compartilhar = i == ativa,
                                    progresso = { progresso.value },
                                    inclinar = { inclinar.value },
                                    encolher = { encolher.value },
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
                    PilulaVidro(Modifier.navigationBarsPadding().padding(bottom = 16.dp), toolbar = true) {
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
                        NomeUmaLinha(
                            atual.name,
                            Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(start = 72.dp, end = 72.dp, top = 24.dp),
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            centralizar = true,
                        )

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
                                PilulaVidro(Modifier.align(Alignment.Center), toolbar = true) {
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
                                    // aparelhos sem Picture-in-Picture (alguns Android Go) não mostram o botão
                                    if (activity != null &&
                                        activity.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)
                                    ) {
                                        BotaoVidro(Icons.Rounded.PictureInPictureAlt, "Picture-in-Picture", {
                                            val entrou = runCatching {
                                                activity.enterPictureInPictureMode(
                                                    PictureInPictureParams.Builder().setAspectRatio(razaoPip).build(),
                                                )
                                            }.getOrDefault(false)
                                            // PiP desativado para o app nas configurações do Android: avisa em vez de não fazer nada
                                            if (!entrou) {
                                                android.widget.Toast.makeText(
                                                    activity, "Picture-in-Picture indisponível", android.widget.Toast.LENGTH_SHORT,
                                                ).show()
                                            }
                                        })
                                    }
                                    BotaoVidro(Icons.Rounded.Delete, "Mover para a lixeira", { confirmarExclusao = true })
                                    // Três pontos: o menu em si é desenhado com blur mais abaixo
                                    BotaoVidro(Icons.Rounded.MoreVert, "Mais opções", { menu = 1 })
                                }
                            }
                        }
                    }
                }

                // Menu com blur: velocidade, resolução, transmitir, legendas, informações e player externo
                MenuVidro(
                    aberto = menu != 0 && c != null,
                    aoFechar = { menu = 0 },
                    chave = menu,
                    modifier = Modifier.align(Alignment.BottomEnd).statusBarsPadding().navigationBarsPadding()
                        .padding(end = 16.dp, top = 12.dp, bottom = 130.dp),
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
                            2 -> {
                                ItemMenuVidro("Voltar", { menu = 1 }, icone = Icons.AutoMirrored.Rounded.ArrowBack)
                                VELOCIDADES.forEach { v ->
                                    ItemMenuVidro("${v}x", { c.definirVelocidade(v); menu = 0 }, marcado = v == c.velocidade)
                                }
                            }
                            3 -> {
                                ItemMenuVidro("Voltar", { menu = 1 }, icone = Icons.AutoMirrored.Rounded.ArrowBack)
                                ItemMenuVidro("Sem legenda", { c.escolherLegenda(null); menu = 0 }, marcado = c.faixas.none { it.grupo.isSelected })
                                c.faixas.forEach { fx ->
                                    ItemMenuVidro(fx.rotulo, { c.escolherLegenda(fx); menu = 0 }, marcado = fx.grupo.isSelected)
                                }
                            }
                            4 -> {
                                ItemMenuVidro("Voltar", { menu = 1 }, icone = Icons.AutoMirrored.Rounded.ArrowBack)
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
                    enter = Animacoes.entrada(fadeIn(tween(100))),
                    exit = Animacoes.saida(fadeOut(tween(100))),
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

/** Foco do zoom: novo deslocamento para o ponto tocado ficar parado na tela ao ir da escala [s0] para [s1] (> 1). */
private fun deslocamentoParaFoco(foco: Offset, d0: Offset, s0: Float, s1: Float, w: Float, h: Float): Offset {
    val p = foco - Offset(w / 2f, h / 2f)
    val bruto = p - (p - d0) * (s1 / s0)
    val lx = w * (s1 - 1f) / 2f
    val ly = h * (s1 - 1f) / 2f
    return Offset(bruto.x.coerceIn(-lx, lx), bruto.y.coerceIn(-ly, ly))
}

/**
 * Pinça e arrasto de uma mídia. O zoom acontece em volta do ponto entre os dedos. Dá para afastar a mídia
 * abaixo do tamanho normal (zoom out, até 0,6x); ao soltar, [aoSoltar] devolve ao tamanho certo.
 */
private suspend fun AwaitPointerEventScope.tratarPinca(
    w: Float,
    h: Float,
    maximo: Float,
    escala: () -> Float,
    deslocamento: () -> Offset,
    aplicar: (Float, Offset) -> Unit,
    aoMudarZoom: (Boolean) -> Unit,
    aoSoltar: () -> Unit,
) {
    awaitFirstDown(requireUnconsumed = false)
    // com zoom, um dedo só vira arrasto depois de passar do limiar de toque: um toque leve (com a mão tremendo
    // um pouco) continua sendo toque e mostra/esconde os controles
    var movido = false
    var percorrido = 0f
    do {
        val evento = awaitPointerEvent()
        val toques = evento.changes.count { it.pressed }
        if (!movido && toques >= 2) movido = true
        if (!movido && toques == 1 && escala() > 1f) {
            percorrido += evento.calculatePan().getDistance()
            if (percorrido > viewConfiguration.touchSlop) movido = true
        }
        if (movido) {
            val zoom = evento.calculateZoom()
            val pan = evento.calculatePan()
            val centroide = evento.calculateCentroid(useCurrent = true)
            val s0 = escala()
            val nova = (s0 * zoom).coerceIn(0.6f, maximo)
            val novoDesloc = if (nova <= 1f) {
                Offset.Zero
            } else {
                val centro = Offset(w / 2f, h / 2f)
                val p = (if (centroide.isSpecified) centroide else centro) - centro
                val d = (p - (p - deslocamento()) * (nova / s0)) + pan
                val lx = w * (nova - 1f) / 2f
                val ly = h * (nova - 1f) / 2f
                Offset(d.x.coerceIn(-lx, lx), d.y.coerceIn(-ly, ly))
            }
            aplicar(nova, novoDesloc)
            aoMudarZoom(nova > 1f)
            evento.changes.forEach { if (it.positionChanged()) it.consume() }
        }
    } while (evento.changes.any { it.pressed })
    aoSoltar()
}

/**
 * Uma foto (ou GIF/imagem animada): zoom com pinça (também para menos de 1x, voltando ao soltar) ou toque duplo
 * no ponto tocado, e toque para a interface. Segurar a foto por um instante a leva para outro app (arrastar e soltar).
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun PaginaFoto(
    f: DriveFile,
    arquivoLocal: java.io.File?,
    token: String?,
    ehAtual: Boolean,
    compartilhar: Boolean,
    progresso: () -> Float,
    inclinar: () -> Float,
    encolher: () -> Float,
    st: SharedTransitionScope,
    av: AnimatedVisibilityScope,
    aoZoom: (Boolean) -> Unit,
    aoTocar: () -> Unit,
) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val vibrar = LocalVibrar.current
    val escopo = rememberCoroutineScope()
    var escala by remember { mutableFloatStateOf(1f) }
    var deslocamento by remember { mutableStateOf(Offset.Zero) }
    var proporcao by remember { mutableStateOf<Float?>(null) }
    val carregada = remember { arrayOfNulls<Drawable>(1) }
    val animacaoZoom = remember { arrayOfNulls<Job>(1) }

    // Cantos arredondados da miniatura viram cantos retos aos poucos (e o contrário ao fechar)
    val raio by av.transition.animateDp(
        transitionSpec = {
            Animacoes.espec(
                tween(if (targetState == EnterExitState.Visible) 500 else TempoGaleria.voltaMs, easing = CurvaIOS),
                tween(if (targetState == EnterExitState.Visible) 250 else TempoGaleria.voltaMs, easing = CurvaIOS),
            )
        },
        label = "raio",
    ) {
        if (it == EnterExitState.Visible) 0.dp else 14.dp
    }
    val origem = if (compartilhar) Modifier.midiaBounds("foto-${f.id}", st, av, miniatura = false) else Modifier
    // formato real da mídia: a miniatura já carregada na lista (ou a imagem original, se ainda não houver)
    val razao = ProporcoesMidia.obter(f.id) ?: proporcao

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        // caixa com a proporção da mídia, centralizada: é ela que cresce da miniatura (recorte quadrado) até o formato original
        val caixa = if (razao != null && razao > 0f) {
            val largo = maxWidth / maxHeight > razao
            Modifier.size(if (largo) maxHeight * razao else maxWidth, if (largo) maxHeight else maxWidth / razao)
        } else {
            Modifier.fillMaxSize()
        }

        /** Anima até [alvo]; com [foco], o ponto tocado fica parado ao ampliar. Voltar a 1x centraliza a foto. */
        fun animarZoom(alvo: Float, foco: Offset?) {
            animacaoZoom[0]?.cancel()
            animacaoZoom[0] = escopo.launch {
                val inicio = escala
                val inicioDesloc = deslocamento
                val fim = if (alvo <= 1f) Offset.Zero
                else if (foco != null) deslocamentoParaFoco(foco, inicioDesloc, inicio, alvo, w, h)
                else inicioDesloc
                animate(0f, 1f, animationSpec = Animacoes.espec(spring(dampingRatio = 0.9f, stiffness = 260f), tween(150))) { t, _ ->
                    escala = inicio + (alvo - inicio) * t
                    deslocamento = inicioDesloc + (fim - inicioDesloc) * t
                }
                if (ehAtual) aoZoom(alvo > 1f)
            }
        }

        fun arrastar() {
            val cor = CoresMidia.obter(f.id)?.toArgb() ?: android.graphics.Color.DKGRAY
            iniciarArrasto(view, f, miniaturaPequena(carregada[0]), cor, vibrar)
        }

        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxSize()
                    .pointerInput(w, h) {
                        awaitEachGesture {
                            tratarPinca(
                                w, h, 6f,
                                escala = { escala },
                                deslocamento = { deslocamento },
                                aplicar = { e, d -> animacaoZoom[0]?.cancel(); escala = e; deslocamento = d },
                                aoMudarZoom = { if (ehAtual) aoZoom(it) },
                                aoSoltar = {
                                    if (escala < 1f) {
                                        animarZoom(1f, null) // zoom out elástico: volta ao tamanho normal
                                    } else if (escala <= 1.02f) {
                                        escala = 1f
                                        deslocamento = Offset.Zero
                                        if (ehAtual) aoZoom(false)
                                    }
                                },
                            )
                        }
                    }
                    .pointerInput(w, h) {
                        detectTapGestures(
                            onTap = { aoTocar() },
                            // toque duplo: amplia no ponto tocado; com a foto ampliada, volta ao tamanho normal
                            onDoubleTap = { toque -> animarZoom(if (escala > 1.05f) 1f else 2.5f, toque) },
                            onLongPress = { arrastar() },
                        )
                    }
                    .graphicsLayer {
                        scaleX = escala
                        scaleY = escala
                        translationX = deslocamento.x
                        translationY = deslocamento.y
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    caixa.then(origem)
                        .efeitoFechar(progresso, encolher, inclinar)
                        .clip(if (compartilhar) RoundedCornerShape(raio) else RectangleShape),
                ) {
                    val original = ImageRequest.Builder(ctx)
                        .data(arquivoLocal ?: "https://www.googleapis.com/drive/v3/files/${f.id}?alt=media")
                        .apply { if (token != null) addHeader("Authorization", "Bearer $token") }
                        .size(2400, 2400)
                        .emLoop() // GIFs e imagens animadas repetem enquanto a tela estiver aberta
                        .crossfade(!Animacoes.sem)
                        .build()
                    SubcomposeAsyncImage(
                        model = original,
                        contentDescription = f.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = if (razao != null) ContentScale.Crop else ContentScale.Fit,
                        onSuccess = { s ->
                            carregada[0] = s.result.drawable
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
                                    AsyncImage(
                                        miniatura, null, Modifier.fillMaxSize(),
                                        contentScale = if (razao != null) ContentScale.Crop else ContentScale.Fit,
                                        onSuccess = { carregada[0] = it.result.drawable },
                                    )
                                }
                                CircularProgressIndicator(Modifier.size(32.dp), color = Color.White)
                            }
                        },
                    )
                }
            }
        }
    }
}

/**
 * Página de um vídeo: miniatura até o player ficar pronto; só o vídeo ativo tem player e gestos.
 * Segurar: velocidade turbo; continuar segurando por mais um instante leva o vídeo para outro app (arrastar e soltar).
 */
@OptIn(ExperimentalSharedTransitionApi::class, UnstableApi::class)
@Composable
private fun PaginaVideo(
    f: DriveFile,
    ctrl: ControleVideo?,
    token: String?,
    compartilhar: Boolean,
    progresso: () -> Float,
    inclinar: () -> Float,
    encolher: () -> Float,
    st: SharedTransitionScope,
    av: AnimatedVisibilityScope,
    aoZoom: (Boolean) -> Unit,
    aoTocar: () -> Unit,
) {
    val ctx = LocalContext.current
    val escopo = rememberCoroutineScope()
    var escala by remember { mutableFloatStateOf(1f) }
    var deslocamento by remember { mutableStateOf(Offset.Zero) }
    val miniaturaCarregada = remember { arrayOfNulls<Drawable>(1) }
    val animacaoZoom = remember { arrayOfNulls<Job>(1) }

    val raio by av.transition.animateDp(
        transitionSpec = {
            Animacoes.espec(
                tween(if (targetState == EnterExitState.Visible) 500 else TempoGaleria.voltaMs, easing = CurvaIOS),
                tween(if (targetState == EnterExitState.Visible) 250 else TempoGaleria.voltaMs, easing = CurvaIOS),
            )
        },
        label = "raio",
    ) {
        if (it == EnterExitState.Visible) 0.dp else 14.dp
    }
    val origem = if (compartilhar) Modifier.midiaBounds("video-${f.id}", st, av, miniatura = false) else Modifier
    val razao = ctrl?.takeIf { it.pronto }?.proporcao ?: ProporcoesMidia.obter(f.id)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val caixa = if (razao != null && razao > 0f) {
            val largo = maxWidth / maxHeight > razao
            Modifier.size(if (largo) maxHeight * razao else maxWidth, if (largo) maxHeight else maxWidth / razao)
        } else {
            Modifier.fillMaxSize()
        }

        fun animarZoom(alvo: Float, foco: Offset?) {
            animacaoZoom[0]?.cancel()
            animacaoZoom[0] = escopo.launch {
                val inicio = escala
                val inicioDesloc = deslocamento
                val fim = if (alvo <= 1f) Offset.Zero
                else if (foco != null) deslocamentoParaFoco(foco, inicioDesloc, inicio, alvo, w, h)
                else inicioDesloc
                animate(0f, 1f, animationSpec = Animacoes.espec(spring(dampingRatio = 0.9f, stiffness = 260f), tween(150))) { t, _ ->
                    escala = inicio + (alvo - inicio) * t
                    deslocamento = inicioDesloc + (fim - inicioDesloc) * t
                }
                aoZoom(alvo > 1f)
            }
        }

        val gestos = if (ctrl == null) {
            Modifier.pointerInput(Unit) { detectTapGestures(onTap = { aoTocar() }) }
        } else {
            Modifier
                // pinça: zoom no vídeo em volta dos dedos (e arrastar quando ampliado); abaixo de 1x volta ao soltar
                .pointerInput(ctrl, w, h) {
                    awaitEachGesture {
                        tratarPinca(
                            w, h, 4f,
                            escala = { escala },
                            deslocamento = { deslocamento },
                            aplicar = { e, d -> animacaoZoom[0]?.cancel(); escala = e; deslocamento = d },
                            aoMudarZoom = { aoZoom(it) },
                            aoSoltar = {
                                if (escala < 1f) {
                                    animarZoom(1f, null)
                                } else if (escala <= 1.02f) {
                                    escala = 1f
                                    deslocamento = Offset.Zero
                                    aoZoom(false)
                                }
                            },
                        )
                    }
                }
                // toque: interface; segurar: turbo (vídeos não são copiados nem arrastados para outros apps, só fotos);
                // toque duplo: laterais ±salto e centro amplia no ponto tocado ou volta ao normal
                .pointerInput(ctrl, w, h) {
                    detectTapGestures(
                        onPress = {
                            tryAwaitRelease()
                            ctrl.desligarTurbo()
                        },
                        onLongPress = { ctrl.ligarTurbo() },
                        onDoubleTap = { toque ->
                            val terco = size.width / 3f
                            when {
                                toque.x < terco -> ctrl.pular(-1)
                                toque.x > 2 * terco -> ctrl.pular(1)
                                else -> animarZoom(if (escala > 1.05f) 1f else 2f, toque)
                            }
                        },
                        onTap = { aoTocar() },
                    )
                }
        }
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxSize().then(gestos).graphicsLayer {
                    scaleX = escala
                    scaleY = escala
                    translationX = deslocamento.x
                    translationY = deslocamento.y
                },
                contentAlignment = Alignment.Center,
            ) {
              Box(
                caixa.then(origem)
                    .efeitoFechar(progresso, encolher, inclinar)
                    .clip(if (compartilhar) RoundedCornerShape(raio) else RectangleShape),
                contentAlignment = Alignment.Center,
              ) {
                // ao fechar, com o vídeo pronto, só o vídeo diminui (a miniatura entra depois, na troca)
                val soVideo = TempoGaleria.fechando && ctrl != null && ctrl.pronto
                if (f.thumbnailLink != null && !soVideo) {
                    val miniatura = ImageRequest.Builder(ctx)
                        .data(f.thumbnailLink)
                        .apply { if (token != null) addHeader("Authorization", "Bearer $token") }
                        .build()
                    AsyncImage(
                        miniatura, null, Modifier.fillMaxSize(),
                        contentScale = if (razao != null) ContentScale.Crop else ContentScale.Fit,
                        onSuccess = { miniaturaCarregada[0] = it.result.drawable },
                    )
                }
                if (ctrl != null) {
                    key(ctrl) {
                        // O vídeo COBRE a moldura (recorte central, igual à miniatura): em pé encosta nas laterais,
                        // deitado encosta no topo e na base. O tamanho é calculado na medição (layout) e não numa
                        // subcomposição (BoxWithConstraints), que durante a animação ficaria presa ao tamanho final.
                        AndroidView(
                            factory = { c -> TextureView(c).also { tv -> ctrl.player.setVideoTextureView(tv); ctrl.textura = tv } },
                            modifier = Modifier.fillMaxSize().layout { medido, restricoes ->
                                val w = restricoes.maxWidth
                                val h = restricoes.maxHeight
                                val v = ctrl.proporcao
                                val cobre = h <= 0 || w.toFloat() / h > v
                                val lv = if (cobre) w else (h * v).roundToInt()
                                val av = if (cobre) (w / v).roundToInt() else h
                                val p = medido.measure(Constraints.fixed(lv.coerceAtLeast(1), av.coerceAtLeast(1)))
                                layout(w, h) { p.place((w - lv) / 2, (h - av) / 2) }
                            },
                        )
                    }
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
