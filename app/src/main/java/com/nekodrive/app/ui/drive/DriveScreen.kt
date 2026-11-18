package com.nekodrive.app.ui.drive

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.layout.height
import com.nekodrive.app.ui.galeria.LocalLuz
import com.nekodrive.app.ui.galeria.LocalLuzPrincipal
import com.nekodrive.app.ui.galeria.reflexoLuz
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColor
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import coil.request.CachePolicy
import com.nekodrive.app.ui.Animacoes
import com.nekodrive.app.ui.DialogRenomear
import com.nekodrive.app.ui.LocalAnimarGifs
import com.nekodrive.app.ui.LocalFundoCard
import com.nekodrive.app.ui.LocalRenovarCapa
import com.nekodrive.app.ui.LocalMiniaturasSoCache
import com.nekodrive.app.ui.LocalMostrarNomes
import com.nekodrive.app.ui.LocalVibrar
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.nekodrive.app.data.drive.DriveFile
import com.nekodrive.app.ui.FaixaVidro
import com.nekodrive.app.ui.IndicadorAtualizar
import com.nekodrive.app.ui.NomeUmaLinha
import com.nekodrive.app.ui.abrirComOutroApp
import com.nekodrive.app.ui.fonteVidro
import com.nekodrive.app.ui.galeria.BotaoVidro
import com.nekodrive.app.ui.galeria.CirculoVidro
import com.nekodrive.app.ui.galeria.ItemMenuVidro
import com.nekodrive.app.ui.galeria.MenuVidro
import com.nekodrive.app.ui.galeria.PilulaVidro
import com.nekodrive.app.ui.rememberRebote
import com.nekodrive.app.ui.vidro
import kotlinx.coroutines.launch

private val CurvaIOS = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

/** Largura mínima de cada miniatura da grade, conforme o ajuste de tamanho (1 médias, 2 grandes). */
internal fun larguraMiniatura(tamanho: Int): Dp = if (tamanho >= 2) 150.dp else 110.dp

/** Colunas da grade: fixas (2 a 4) ou automáticas pelo tamanho das miniaturas quando [colunas] é 0. */
internal fun celulasGrade(colunas: Int, tamanho: Int): GridCells =
    if (colunas in 2..4) GridCells.Fixed(colunas) else GridCells.Adaptive(larguraMiniatura(tamanho))

/** Lado da miniatura na lista, conforme o mesmo ajuste de tamanho. */
internal fun ladoMiniaturaLista(tamanho: Int): Dp = if (tamanho >= 2) 80.dp else 56.dp

/** Mesma duração de antes (cerca de 500 ms) com curva de desaceleração suave, no estilo das animações do iOS. */
@OptIn(ExperimentalSharedTransitionApi::class)
private val CurvaSuave = BoundsTransform { _, _ -> Animacoes.espec(tween(durationMillis = 500, easing = CurvaIOS), tween(220)) }

/**
 * Elemento compartilhado entre a origem (card/miniatura) e o destino (tela da pasta ou galeria).
 * Sem fade no conteúdo: o elemento se expande opaco. Com [desvanecerOrigem], o conteúdo do card (ícone e
 * legenda) some rapidamente enquanto o card cresce.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun Modifier.pastaBounds(
    chave: String,
    st: SharedTransitionScope,
    av: AnimatedVisibilityScope,
    desvanecerOrigem: Boolean = false,
): Modifier =
    with(st) {
        val saida = if (desvanecerOrigem) fadeOut(tween(140)) else ExitTransition.None
        this@pastaBounds.sharedBounds(
            rememberSharedContentState(key = chave),
            animatedVisibilityScope = av,
            enter = EnterTransition.None,
            exit = Animacoes.saida(saida, saida),
            boundsTransform = CurvaSuave,
            // Evita re-medidas durante o Lookahead quando a página contém LazyVerticalGrid.
            resizeMode = SharedTransitionScope.ResizeMode.ScaleToBounds,
        )
    }

/** Avisa que a galeria está fechando, para a volta à miniatura durar 0,2 s a mais que a abertura. */
internal object TempoGaleria {
    /** Verdadeiro do primeiro instante do fechamento até a galeria sair de cena. */
    var fechando by mutableStateOf(false)

    /** Duração da volta da mídia até a miniatura; a galeria define a cada fechamento conforme o deslize. */
    var voltaMs by mutableIntStateOf(350)

    /** Mídia parada sobre a miniatura antes da troca. É só visual, mas segura os toques: por isso é curta e ajustável. */
    var esperaMs = 100
    const val TROCA_MS = 40 // troca da mídia pela miniatura

    val totalMs: Int get() = voltaMs + esperaMs + TROCA_MS
}

/** Curva do movimento de volta: termina em VOLTA_MS e fica parada até a troca (para a mídia não sair do lugar). */
private val CurvaVolta = Easing { f -> CurvaIOS.transform((f * TempoGaleria.totalMs / TempoGaleria.voltaMs).coerceAtMost(1f)) }

@OptIn(ExperimentalSharedTransitionApi::class)
private val CurvaMidia = BoundsTransform { _, _ ->
    when {
        Animacoes.sem -> snap<Rect>()
        TempoGaleria.fechando -> tween(durationMillis = TempoGaleria.totalMs, easing = CurvaVolta)
        else -> tween(durationMillis = Animacoes.ms(500, 250), easing = CurvaIOS)
    }
}

/** Sem corte no overlay: a mídia inclinada pode aparecer em qualquer parte da tela. */
@OptIn(ExperimentalSharedTransitionApi::class)
private val SemCorte = object : SharedTransitionScope.OverlayClip {
    override fun getClipPath(
        sharedContentState: SharedTransitionScope.SharedContentState,
        bounds: Rect,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Path? = null
}

/**
 * Origem/destino da animação de fotos e vídeos. Do lado da miniatura, o conteúdo some logo que a abertura
 * começa e só reaparece no fim do fechamento: durante o trajeto quem aparece é a própria mídia, que se
 * adapta suavemente do recorte quadrado da miniatura para o formato original.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun Modifier.midiaBounds(
    chave: String,
    st: SharedTransitionScope,
    av: AnimatedVisibilityScope,
    miniatura: Boolean,
): Modifier =
    with(st) {
        // abrindo: a miniatura some logo e a mídia cresce sozinha; fechando: a mídia volta, fica 0,2 s
        // sobre o lugar da miniatura e então é trocada por ela
        val entrada = if (miniatura) {
            fadeIn(tween(TempoGaleria.TROCA_MS, delayMillis = TempoGaleria.voltaMs + TempoGaleria.esperaMs))
        } else {
            EnterTransition.None
        }
        val saida = if (miniatura) {
            fadeOut(tween(120))
        } else {
            fadeOut(tween(TempoGaleria.TROCA_MS, delayMillis = TempoGaleria.voltaMs + TempoGaleria.esperaMs))
        }
        this@midiaBounds.sharedBounds(
            rememberSharedContentState(key = chave),
            animatedVisibilityScope = av,
            enter = Animacoes.entrada(entrada, entrada),
            exit = Animacoes.saida(saida, saida),
            boundsTransform = CurvaMidia,
            // Mantém o layout interno estável durante a transição entre miniatura e player.
            resizeMode = SharedTransitionScope.ResizeMode.ScaleToBounds,
            clipInOverlayDuringTransition = SemCorte,
        )
    }

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun DriveScreen(
    vm: DriveViewModel,
    onEntrar: () -> Unit,
    st: SharedTransitionScope,
    avExterno: AnimatedVisibilityScope,
    contentPadding: PaddingValues,
) {
    var confirmar by remember { mutableStateOf(false) }
    var alturaTopo by remember { mutableStateOf(0.dp) }
    var arquivoExterno by remember { mutableStateOf<DriveFile?>(null) }
    val ctxDrive = LocalContext.current
    val escopoDrive = rememberCoroutineScope()
    var menuOrdem by remember { mutableStateOf(false) }
    var menuHamburguer by remember { mutableStateOf(false) }
    var novaPasta by remember { mutableStateOf(false) }
    var renomear by remember { mutableStateOf<DriveFile?>(null) }
    val foco = LocalFocusManager.current
    val escolherArquivos = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        vm.enviarArquivos(uris)
    }

    // Troca grade/lista: o texto some rápido, o layout troca (itens deslizam) e o texto volta
    var gradeExibida by remember { mutableStateOf(vm.grade) }
    val alfaTextos = remember { Animatable(1f) }
    LaunchedEffect(vm.grade) {
        if (vm.grade != gradeExibida) {
            alfaTextos.animateTo(0f, Animacoes.espec(tween(150, easing = CurvaIOS), tween(80)))
            gradeExibida = vm.grade
            alfaTextos.animateTo(1f, Animacoes.espec(tween(280, easing = CurvaIOS), tween(120)))
        }
    }

    // Direção da navegação entre pastas (para saber em qual página fica a legenda do card)
    val profundidade = vm.pilha.size
    val historico = remember { intArrayOf(profundidade, profundidade) }
    if (historico[1] != profundidade) {
        historico[0] = historico[1]
        historico[1] = profundidade
    }
    val avancando = historico[1] > historico[0]
    val voltando = historico[1] < historico[0]

    // enquanto a mídia está fechando, voltar de pasta é ignorado (espera a animação terminar)
    BackHandler(enabled = vm.buscando || vm.pilha.size > 1 || TempoGaleria.fechando) {
        if (!TempoGaleria.fechando) vm.voltar()
    }
    BackHandler(enabled = vm.selecionados.isNotEmpty()) { vm.limparSelecao() }

    if (vm.contaAtual == null) {
        SemConta(vm.erro, onEntrar, Modifier.padding(contentPadding))
        return
    }

    val aoTocar: (DriveFile) -> Unit = { f ->
        if (TempoGaleria.fechando) Unit
        else if (vm.selecionados.isNotEmpty()) vm.alternarSelecao(f)
        else if (f.isFolder) vm.abrirPasta(f)
        else if (f.isImage || f.isVideo) { foco.clearFocus(); vm.abrirFoto(f) }
        else arquivoExterno = f
    }
    val chave = if (vm.buscando) "busca" else vm.pilha.last().id

    // A toolbar principal é iluminada pelas miniaturas visíveis da lista (cor e posição de cada uma)
    val luzPrincipal = LocalLuzPrincipal.current
    val estadoG = vm.estadoGrade(chave)
    val estadoL = vm.estadoLista(chave)
    SideEffect {
        luzPrincipal?.movendo = { if (gradeExibida) estadoG.isScrollInProgress else estadoL.isScrollInProgress }
        luzPrincipal?.fontes = { col ->
            fun fonte(chaveItem: Any, x0: Float, y0: Float, x1: Float, y1: Float) {
                val id = chaveItem as? String ?: return
                val dom = CoresMidia.obter(id) ?: return
                col.fonte(x0, y0, x1, y1, dom, CoresMidia.obterForte(id) ?: dom)
            }
            if (gradeExibida) {
                estadoG.layoutInfo.visibleItemsInfo.forEach {
                    fonte(it.key, it.offset.x.toFloat(), it.offset.y.toFloat(), (it.offset.x + it.size.width).toFloat(), (it.offset.y + it.size.height).toFloat())
                }
            } else {
                val larg = luzPrincipal?.largura ?: 0f
                estadoL.layoutInfo.visibleItemsInfo.forEach {
                    fonte(it.key, 0f, it.offset.toFloat(), larg, (it.offset + it.size).toFloat())
                }
            }
        }
    }
    DisposableEffect(luzPrincipal) {
        onDispose {
            luzPrincipal?.fontes = null
            luzPrincipal?.movendo = { false }
        }
    }

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = chave,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                if (Animacoes.sem) EnterTransition.None togetherWith ExitTransition.None
                else fadeIn(tween(1)) togetherWith fadeOut(tween(Animacoes.ms(500, 150)))
            },
            label = "pasta",
        ) { chavePagina ->
            PaginaPasta(
                atual = chavePagina == chave,
                chave = chavePagina,
                vm = vm,
                st = st,
                av = this@AnimatedContent,
                avExterno = avExterno,
                grade = gradeExibida,
                alfaTextos = { alfaTextos.value },
                legendaDoCard = !vm.buscando && chavePagina != "busca" && chavePagina != vm.pilha.first().id &&
                    (if (avancando) chavePagina == chave else if (voltando) chavePagina != chave else false),
                espaco = PaddingValues(
                    top = contentPadding.calculateTopPadding() + alturaTopo,
                    bottom = contentPadding.calculateBottomPadding(),
                ),
                aoTocar = aoTocar,
            )
        }
        FaixaVidro(
            topo = contentPadding.calculateTopPadding(),
            aoMedir = { alturaTopo = it },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Barra(vm, aoOrdem = { menuOrdem = true }, aoMenu = { menuHamburguer = true }, aoCapa = { vm.abrirSeletorCapa(it) }) { confirmar = true }
            vm.operacao?.let { texto ->
                PilulaVidro(Modifier.padding(horizontal = 12.dp)) {
                    CircularProgressIndicator(Modifier.padding(start = 10.dp).size(18.dp), strokeWidth = 2.dp)
                    Text(texto, Modifier.padding(horizontal = 10.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
            }
            vm.erro?.let { msg ->
                PilulaVidro(Modifier.padding(horizontal = 12.dp)) {
                    Text(msg, Modifier.padding(start = 10.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { vm.recarregar() }) { Text("Tentar de novo") }
                }
            }
        }
        // Hambúrguer: mover, copiar e renomear (quando há seleção), criar pasta e enviar arquivos
        MenuVidro(
            aberto = menuHamburguer,
            aoFechar = { menuHamburguer = false },
            chave = vm.selecionados.size,
            modifier = Modifier.align(Alignment.TopEnd)
                .padding(top = contentPadding.calculateTopPadding() + 62.dp, end = 12.dp),
        ) {
            val cor = MaterialTheme.colorScheme.onSurface
            if (vm.selecionados.isNotEmpty()) {
                val unico = if (vm.selecionados.size == 1) vm.itens.firstOrNull { it.id in vm.selecionados } else null
                ItemMenuVidro("Mover para…", { menuHamburguer = false; vm.abrirSeletorDestino(false) }, cor = cor, icone = Icons.Rounded.DriveFileMove)
                ItemMenuVidro("Copiar para…", { menuHamburguer = false; vm.abrirSeletorDestino(true) }, cor = cor, icone = Icons.Rounded.ContentCopy)
                if (unico != null) {
                    ItemMenuVidro("Renomear", { menuHamburguer = false; renomear = unico }, cor = cor, icone = Icons.Rounded.Edit)
                }
            }
            ItemMenuVidro("Criar pasta", { menuHamburguer = false; novaPasta = true }, cor = cor, icone = Icons.Rounded.CreateNewFolder)
            ItemMenuVidro("Enviar arquivos", { menuHamburguer = false; escolherArquivos.launch("*/*") }, cor = cor, icone = Icons.Rounded.UploadFile)
        }
        MenuVidro(
            aberto = menuOrdem,
            aoFechar = { menuOrdem = false },
            modifier = Modifier.align(Alignment.TopEnd)
                .padding(top = contentPadding.calculateTopPadding() + 62.dp, end = 12.dp),
        ) {
            CampoOrdem.entries.forEach { campo ->
                val seta = if (campo == vm.campo) (if (vm.crescente) "  ↑" else "  ↓") else ""
                ItemMenuVidro(
                    campo.rotulo + seta,
                    { vm.definirOrdem(campo); menuOrdem = false },
                    cor = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }

    if (novaPasta) {
        DialogRenomear(
            nomeAtual = "Nova pasta",
            aoConfirmar = { nome -> novaPasta = false; vm.criarPasta(nome) },
            aoCancelar = { novaPasta = false },
            titulo = "Nova pasta",
            rotuloConfirmar = "Criar",
        )
    }
    if (vm.seletorDestino != null) SeletorDestino(vm)

    renomear?.let { f ->
        DialogRenomear(
            nomeAtual = f.name,
            aoConfirmar = { novo -> renomear = null; vm.renomear(f, novo) { vm.limparSelecao() } },
            aoCancelar = { renomear = null },
        )
    }

    arquivoExterno?.let { f ->
        AlertDialog(
            onDismissRequest = { arquivoExterno = null },
            title = { Text("Abrir com outro app?") },
            text = { Text("O nekodrive não abre este tipo de arquivo. Dá para baixá-lo e abri-lo com outro app instalado no celular.") },
            confirmButton = {
                TextButton(onClick = {
                    arquivoExterno = null
                    escopoDrive.launch { abrirComOutroApp(ctxDrive, vm, f) }
                }) { Text("Abrir com…") }
            },
            dismissButton = { TextButton(onClick = { arquivoExterno = null }) { Text("Cancelar") } },
        )
    }

    if (confirmar) {
        val n = vm.selecionados.size
        AlertDialog(
            onDismissRequest = { confirmar = false },
            title = { Text("Mover para a lixeira?") },
            text = { Text("$n item(ns) vão para a lixeira do Drive. Dá para restaurar depois.") },
            confirmButton = { TextButton(onClick = { confirmar = false; vm.moverParaLixeira() }) { Text("Mover") } },
            dismissButton = { TextButton(onClick = { confirmar = false }) { Text("Cancelar") } },
        )
    }
}

// ---------- botões flutuantes do topo (cada um em círculo ou pílula de vidro) ----------

@Composable
private fun Barra(
    vm: DriveViewModel,
    aoOrdem: () -> Unit,
    aoMenu: () -> Unit,
    aoCapa: (DriveFile) -> Unit,
    aoExcluir: () -> Unit,
) {
    val cor = MaterialTheme.colorScheme.onSurface
    // altura fixa: a barra de edição (botões de 48 dp) e a normal (círculos de 46 dp) ocupam o mesmo espaço,
    // então a lista não dá um pulinho quando a seleção começa ou termina
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).height(56.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (vm.selecionados.isNotEmpty()) {
            CirculoVidro(aoClicar = { vm.limparSelecao() }) { Icon(Icons.Rounded.Close, "Cancelar seleção", tint = cor) }
            PilulaVidro {
                // só o número: com mais botões na pílula ao lado, o texto "selecionado(s)" não caberia
                Text(
                    "${vm.selecionados.size}",
                    Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = cor,
                )
            }
            Spacer(Modifier.weight(1f))
            val unico = if (vm.selecionados.size == 1) vm.itens.firstOrNull { it.id in vm.selecionados } else null
            PilulaVidro {
                BotaoVidro(Icons.Rounded.StarBorder, "Favoritar", { vm.favoritarSelecao(vm.itens) }, cor)
                BotaoVidro(Icons.Rounded.Download, "Baixar", { vm.baixarSelecionados() }, cor)
                if (unico != null && unico.isFolder) BotaoVidro(Icons.Rounded.AddPhotoAlternate, "Definir capa", { aoCapa(unico) }, cor)
                BotaoVidro(Icons.Rounded.Delete, "Mover para a lixeira", aoExcluir, cor)
                BotaoVidro(Icons.Rounded.Menu, "Mais ações: mover, copiar, renomear, criar pasta e enviar", aoMenu, cor)
            }
        } else if (vm.buscando) {
            val foco = remember { FocusRequester() }
            // o teclado só abre quando a busca começa; ao voltar de uma mídia a tela é recriada e não deve reabrir
            LaunchedEffect(Unit) {
                if (vm.pedirFocoBusca) {
                    vm.consumirFocoBusca()
                    runCatching { foco.requestFocus() }
                }
            }
            CirculoVidro(aoClicar = { vm.sairDaBusca() }) { Icon(Icons.Rounded.Close, "Fechar busca", tint = cor) }
            // a barra de busca conta como toolbar para os reflexos
            val luzBusca = LocalLuz.current
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(50)).vidro()
                    .then(if (luzBusca != null) Modifier.reflexoLuz(luzBusca) else Modifier),
            ) {
                TextField(
                    value = vm.termo,
                    onValueChange = { vm.definirTermo(it) },
                    placeholder = { Text("Buscar no Drive") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(foco),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                )
            }
        } else {
            if (vm.pilha.size > 1) {
                CirculoVidro(aoClicar = { if (!TempoGaleria.fechando) vm.voltar() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Voltar", tint = cor) }
            }
            Spacer(Modifier.weight(1f))
            CirculoVidro(aoClicar = aoOrdem) { Icon(Icons.AutoMirrored.Rounded.Sort, "Ordenar", tint = cor) }
            CirculoVidro(aoClicar = { vm.alternarModo() }) {
                Icon(
                    if (vm.grade) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView,
                    "Alternar entre grade e lista",
                    tint = cor,
                )
            }
            CirculoVidro(aoClicar = aoMenu) { Icon(Icons.Rounded.Menu, "Criar pasta e enviar arquivos", tint = cor) }
        }
    }
}

// ---------- página de uma pasta ----------

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalFoundationApi::class)
@Composable
private fun PaginaPasta(
    atual: Boolean,
    chave: String,
    vm: DriveViewModel,
    st: SharedTransitionScope,
    av: AnimatedVisibilityScope,
    avExterno: AnimatedVisibilityScope,
    grade: Boolean,
    alfaTextos: () -> Float,
    legendaDoCard: Boolean,
    espaco: PaddingValues,
    aoTocar: (DriveFile) -> Unit,
) {
    // A página que está saindo mostra a última lista que ela exibiu, e não a da pasta nova.
    val ultima = remember { mutableStateOf<List<DriveFile>>(emptyList()) }
    if (atual) SideEffect { ultima.value = vm.itens }
    val lista = if (atual) vm.itens else ultima.value
    val nomePasta = remember {
        mutableStateOf(if (atual && !vm.buscando) vm.pilha.last().nome else "")
    }
    if (atual && !vm.buscando) SideEffect { nomePasta.value = vm.pilha.last().nome }

    // A tela da pasta nasce com o formato e a cor do card e vai virando tela cheia (e o contrário ao fechar)
    val tema = MaterialTheme.colorScheme
    // Só a página que nasce de um card (ou volta para ele) usa a forma e a cor do card; as demais
    // (busca, raiz) ficam sempre na cor de fundo, sem o clarão cinza durante a transição.
    val casada = legendaDoCard
    val raio by av.transition.animateDp(transitionSpec = { Animacoes.espec(tween(500, easing = CurvaIOS), tween(200)) }, label = "raio") {
        if (it == EnterExitState.Visible || !casada) 0.dp else 14.dp
    }
    val fundo by av.transition.animateColor(transitionSpec = { Animacoes.espec(tween(500, easing = CurvaIOS), tween(200)) }, label = "fundo") {
        if (it == EnterExitState.Visible || !casada) tema.background else tema.surfaceVariant
    }
    // O conteúdo da pasta entra um pouco depois, para a legenda do card acompanhar a expansão primeiro
    val opacidadeConteudo by av.transition.animateFloat(
        transitionSpec = {
            Animacoes.espec<Float>(
                if (!casada) tween(350)
                else if (targetState == EnterExitState.Visible) tween(300, 140)
                else tween(180),
                tween(150),
            )
        },
        label = "conteudo",
    ) {
        if (it == EnterExitState.Visible) 1f else 0f
    }
    val alfaLegenda by av.transition.animateFloat(
        transitionSpec = { Animacoes.espec(tween(if (targetState == EnterExitState.Visible) 260 else 200), tween(120)) },
        label = "legenda",
    ) {
        if (it == EnterExitState.Visible) 0f else 1f
    }
    val rebote = rememberRebote(carregando = vm.carregando && atual, aoAtualizar = { vm.atualizar() })

    Surface(
        Modifier.pastaBounds("pasta-$chave", st, av).fonteVidro().fillMaxSize(),
        shape = RoundedCornerShape(raio),
        color = fundo,
    ) {
        Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = opacidadeConteudo }.nestedScroll(rebote)) {
            Box(Modifier.fillMaxSize().graphicsLayer { translationY = rebote.deslocamento }) {
                CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
                    LazyVerticalGrid(
                        columns = if (grade) celulasGrade(vm.colunas, vm.tamanhoMiniatura) else GridCells.Fixed(1),
                        state = vm.estadoGrade(chave),
                        contentPadding = PaddingValues(
                            start = if (grade) 12.dp else 0.dp,
                            end = if (grade) 12.dp else 0.dp,
                            top = espaco.calculateTopPadding() + if (grade) 12.dp else 0.dp,
                            bottom = espaco.calculateBottomPadding() + if (grade) 12.dp else 0.dp,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(if (grade) 8.dp else 0.dp),
                        verticalArrangement = Arrangement.spacedBy(if (grade) 8.dp else 0.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(lista, key = { it.id }) { f ->
                            // Ao trocar entre grade e lista, os itens deslizam suavemente para o novo lugar
                            Box(
                                Modifier.animateItem(
                                    fadeInSpec = null,
                                    fadeOutSpec = null,
                                    placementSpec = if (Animacoes.normais) {
                                        spring(
                                            dampingRatio = 0.9f,
                                            stiffness = 300f,
                                            visibilityThreshold = IntOffset(1, 1),
                                        )
                                    } else {
                                        null
                                    },
                                ).graphicsLayer {
                                    // troca grade/lista: o item esmaece e encolhe de leve, muda de lugar e volta
                                    // (só nas animações normais: uma camada por item pesa na GPU)
                                    if (Animacoes.normais) {
                                        val a = alfaTextos()
                                        alpha = 0.25f + 0.75f * a
                                        val e = 0.96f + 0.04f * a
                                        scaleX = e
                                        scaleY = e
                                    }
                                },
                            ) {
                                if (grade) {
                                    ItemGrade(
                                        f, vm.token, f.id in vm.selecionados, vm.estadoDe(f), st, av, avExterno,
                                        { aoTocar(f) }, { vm.alternarSelecao(f) },
                                        alfaTexto = alfaTextos,
                                    )
                                } else {
                                    ItemLinha(
                                        f, vm.token, f.id in vm.selecionados, vm.estadoDe(f), st, av, avExterno,
                                        { aoTocar(f) }, { vm.alternarSelecao(f) },
                                        alfaTexto = alfaTextos,
                                        ladoMiniatura = ladoMiniaturaLista(vm.tamanhoMiniatura),
                                    )
                                }
                            }
                        }
                        if (atual && vm.temMais) {
                            item(span = { GridItemSpan(maxLineSpan) }) { CarregandoMais(vm) }
                        }
                    }
                }
            }
            if (atual) {
                IndicadorAtualizar(
                    rebote,
                    Modifier.align(Alignment.TopCenter).padding(top = espaco.calculateTopPadding() + 8.dp),
                )
                if (vm.carregando && lista.isEmpty()) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                } else if (!vm.carregando && lista.isEmpty() && vm.erro == null) {
                    val msg = when {
                        !vm.buscando -> "Pasta vazia"
                        vm.termo.isBlank() -> "Digite para buscar no Drive"
                        else -> "Nada encontrado"
                    }
                    Text(msg, Modifier.align(Alignment.Center))
                }
            }
        }
        if (legendaDoCard && nomePasta.value.isNotEmpty()) {
            FantasmaCard(nomePasta.value, grade) { alfaLegenda }
        }
        }
    }
}

/**
 * Ícone e legenda do card, desenhados dentro da própria tela da pasta durante a transição: acompanham a
 * borda do card enquanto ele se expande (e voltam com ela ao fechar), em vez de sumirem de uma vez.
 */
@Composable
private fun FantasmaCard(nome: String, grade: Boolean, alfa: () -> Float) {
    val tema = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().graphicsLayer { this.alpha = alfa() }) {
        if (grade) {
            Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Folder, contentDescription = null, modifier = Modifier.size(44.dp), tint = tema.primary)
            }
            NomeUmaLinha(
                nome,
                Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = tema.onSurfaceVariant,
            )
        } else {
            Icon(
                Icons.Rounded.Folder, contentDescription = null,
                modifier = Modifier.align(Alignment.Center).size(28.dp), tint = tema.primary,
            )
        }
    }
}

@Composable
private fun CarregandoMais(vm: DriveViewModel) {
    LaunchedEffect(vm.itens.size) { vm.carregarMais() }
    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(24.dp))
    }
}

// ---------- itens ----------

/** Miniatura (ou ícone) do arquivo, com os indicadores de cache, assistido e favorito. */
@Composable
private fun Miniatura(
    f: DriveFile,
    token: String?,
    estado: EstadoArquivo,
    tamanhoIcone: Dp,
    modifier: Modifier = Modifier,
    fundo: Color = Color.Transparent,
    aoCor: ((Color) -> Unit)? = null,
    /** Na grade, a capa da pasta é desenhada pelo card inteiro; aqui fica só o selo de pasta. */
    capaNoCard: Boolean = false,
) {
    val context = LocalContext.current
    val ehGif = f.mimeType == "image/gif"
    val animarGifs = LocalAnimarGifs.current
    val soCache = LocalMiniaturasSoCache.current
    Box(modifier.background(fundo), contentAlignment = Alignment.Center) {
        val capa = estado.capa
        if (f.isFolder && capa != null) {
            // pasta com capa: a foto escolhida preenche o espaço, com um selo de pasta no canto
            if (!capaNoCard) CapaPasta(capa, token, f.name, Modifier.fillMaxSize(), link = estado.capaLink)
        } else if (!f.isFolder && (f.thumbnailLink != null || ehGif)) {
            // GIFs animam na miniatura, em baixa resolução e sem reduzir os quadros por segundo
            // GIF animado (ajuste ligado) vem do arquivo; senão usa a miniatura estática do Drive, que é bem mais leve
            val animar = ehGif && animarGifs
            val doArquivo = animar || (ehGif && f.thumbnailLink == null)
            val request = ImageRequest.Builder(context)
                .data(if (doArquivo) "https://www.googleapis.com/drive/v3/files/${f.id}?alt=media" else f.thumbnailLink)
                .apply { if (token != null) addHeader("Authorization", "Bearer $token") }
                .apply { if (animar) { size(256, 256); emLoop() } }
                // chave estável: a miniatura guardada continua valendo mesmo quando o link do Drive muda
                .diskCacheKey("miniatura-${if (doArquivo) "arq" else "thumb"}-${f.id}-${f.modifiedTime}")
                .memoryCacheKey("miniatura-${if (doArquivo) "arq" else "thumb"}-${f.id}-${f.modifiedTime}")
                .networkCachePolicy(if (soCache) CachePolicy.DISABLED else CachePolicy.ENABLED)
                .allowHardware(false)
                .crossfade(!Animacoes.sem)
                .build()
            AsyncImage(
                request, f.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                onSuccess = { s ->
                    if (f.isImage || f.isVideo) ProporcoesMidia.guardar(f.id, s.result.drawable)
                    if (aoCor != null) runCatching {
                        val cores = coresDaImagem(s.result.drawable)
                        if (cores.forte != Color.Unspecified) CoresMidia.guardarForte(f.id, cores.forte)
                        aoCor(cores.dominante)
                    }
                },
            )
        } else {
            val icone = when {
                f.isFolder -> Icons.Rounded.Folder
                f.isVideo -> Icons.Rounded.Movie
                f.isImage -> Icons.Rounded.Image
                else -> Icons.Rounded.Description
            }
            Icon(
                icone, contentDescription = null, modifier = Modifier.size(tamanhoIcone),
                tint = if (f.isFolder) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // indicadores: sempre na borda inferior esquerda, enfileirados (mídias privadas não têm indicador de link)
        val progresso = estado.progresso
        val comProgresso = progresso != null && progresso >= 0f
        val capaDePasta = f.isFolder && estado.capa != null
        if (capaDePasta || comProgresso || estado.noCache || estado.favorito || f.publico) {
            Row(
                Modifier.align(Alignment.BottomStart).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (capaDePasta) Indicador(Icons.Rounded.Folder, "Pasta")
                if (comProgresso) {
                    CircularProgressIndicator(progress = { progresso!! }, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else if (estado.noCache) {
                    Indicador(Icons.Rounded.DownloadDone, "Salvo no cache")
                }
                if (estado.favorito) Indicador(Icons.Rounded.Star, "Favorito", Color(0xFFFFC107))
                if (f.publico) Indicador(Icons.Rounded.Link, "Qualquer pessoa com o link pode abrir")
            }
        }
    }
}

/** Ícone pequeno sobre a miniatura, num círculo escuro translúcido. */
@Composable
private fun Indicador(icone: androidx.compose.ui.graphics.vector.ImageVector, descricao: String, tint: Color = Color.White) {
    Icon(
        icone, descricao, tint = tint,
        modifier = Modifier.background(Color.Black.copy(alpha = 0.55f), CircleShape).padding(3.dp).size(14.dp),
    )
}

/** Foto de capa de uma pasta, cortada para cobrir todo o espaço. [aoCarregar] avisa quando a imagem já apareceu. */
@Composable
private fun CapaPasta(
    capa: String,
    token: String?,
    nome: String,
    modifier: Modifier,
    aoCarregar: () -> Unit = {},
    link: String? = null, // capa em vídeo: miniatura que o Drive gera (foto: baixa o arquivo)
    idPasta: String? = null,
) {
    val context = LocalContext.current
    val soCache = LocalMiniaturasSoCache.current
    val semAnimacao = Animacoes.sem
    val renovar = LocalRenovarCapa.current
    val pedido = remember(capa, link, token, soCache, semAnimacao) {
        ImageRequest.Builder(context)
            .data(link ?: "https://www.googleapis.com/drive/v3/files/$capa?alt=media")
            .apply { if (token != null) addHeader("Authorization", "Bearer $token") }
            .size(480, 480)
            .diskCacheKey("capa-$capa")
            .memoryCacheKey("capa-$capa")
            .networkCachePolicy(if (soCache) CachePolicy.DISABLED else CachePolicy.ENABLED)
            .crossfade(!semAnimacao)
            .build()
    }
    AsyncImage(
        pedido, nome, modifier, contentScale = ContentScale.Crop,
        onSuccess = { aoCarregar() },
        onError = { if (link != null && idPasta != null) renovar(idPasta) },
    )
}

/** Sombra sutil do texto sobre a capa de uma pasta. */
private val SombraTexto = Shadow(color = Color.Black.copy(alpha = 0.55f), offset = Offset(0f, 1.5f), blurRadius = 5f)

/** Cores de um item de mídia: fundo e texto seguem a cor que mais aparece na miniatura, de forma gradual. */
private class TintaMidia(val fundo: Color, val texto: Color, val tingido: Boolean, val aoCor: ((Color) -> Unit)?)

@Composable
private fun tintaMidia(f: DriveFile, neutro: Color, textoNeutro: Color): TintaMidia {
    val tema = MaterialTheme.colorScheme
    // ajuste "Fundo dos cards": 0 cor da mídia, 1 neutro (sem cores), 2 miniatura desfocada (texto sempre branco)
    val modo = LocalFundoCard.current
    val midia = f.isImage || f.isVideo
    var dominante by remember(f.id) { mutableStateOf(CoresMidia.obter(f.id)) }
    // um toque de preto (22%) deixa o card mais escuro e o texto branco mais legível
    val alvoFundo = if (modo != 0) neutro else dominante?.let { lerp(lerp(tema.surfaceVariant, it, 0.55f), Color.Black, 0.22f) } ?: neutro
    val fundo by animateColorAsState(alvoFundo, Animacoes.espec(tween(700), tween(150)), label = "corCard")
    val alvoTexto = when {
        modo == 2 && midia && f.thumbnailLink != null -> Color.White
        modo != 0 || dominante == null -> textoNeutro
        fundo.luminance() > 0.5f -> Color(0xFF1B1B1F)
        else -> Color.White
    }
    val texto by animateColorAsState(alvoTexto, Animacoes.espec(tween(700), tween(150)), label = "corTexto")
    val aoCor: ((Color) -> Unit)? = if (f.isImage || f.isVideo) {
        { c ->
            if (c != Color.Unspecified) {
                CoresMidia.guardar(f.id, c)
                dominante = c
            }
        }
    } else {
        null
    }
    return TintaMidia(fundo, texto, (modo == 0 && dominante != null) || (modo == 2 && midia && f.thumbnailLink != null), aoCor)
}

/** Fundo de card no modo "miniatura desfocada": a miniatura invertida na vertical, bem borrada e escurecida. */
@Composable
private fun FundoMiniatura(f: DriveFile, token: String?, modifier: Modifier) {
    val link = f.thumbnailLink ?: return
    val context = LocalContext.current
    val soCache = LocalMiniaturasSoCache.current
    val pedido = remember(f.id, link, token, soCache) {
        ImageRequest.Builder(context)
            .data(link)
            .apply { if (token != null) addHeader("Authorization", "Bearer $token") }
            .size(32, 32) // miniatura minúscula esticada pelo card: o próprio escalonamento borra forte
            .diskCacheKey("miniatura-thumb-${f.id}-${f.modifiedTime}")
            .memoryCacheKey("fundo-${f.id}-${f.modifiedTime}")
            .networkCachePolicy(if (soCache) CachePolicy.DISABLED else CachePolicy.ENABLED)
            .build()
    }
    Box(modifier) {
        AsyncImage(
            pedido, null,
            Modifier.matchParentSize().graphicsLayer { scaleY = -1f },
            contentScale = ContentScale.Crop,
        )
        Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.5f)))
    }
}

/**
 * Card da grade: miniatura e legenda formam uma peça só, na mesma cor. Em fotos e vídeos a cor segue a cor que
 * mais aparece na mídia (surge aos poucos quando a miniatura carrega). Em pastas, o card inteiro é a origem da animação.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
internal fun ItemGrade(
    f: DriveFile,
    token: String?,
    selecionado: Boolean,
    estado: EstadoArquivo,
    st: SharedTransitionScope,
    av: AnimatedVisibilityScope,
    avExterno: AnimatedVisibilityScope,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    alfaTexto: () -> Float = { 1f },
) {
    val haptico = LocalHapticFeedback.current
    val vibrar = LocalVibrar.current
    val tema = MaterialTheme.colorScheme
    val tinta = tintaMidia(f, tema.surfaceVariant, tema.onSurfaceVariant)
    val corCard = tinta.fundo
    val corTexto = tinta.texto

    val forma = RoundedCornerShape(14.dp)
    val origemCard = if (f.isFolder) Modifier.pastaBounds("pasta-${f.id}", st, av, desvanecerOrigem = true) else Modifier
    val origemMiniatura = when {
        f.isImage -> Modifier.midiaBounds("foto-${f.id}", st, avExterno, miniatura = true)
        f.isVideo -> Modifier.midiaBounds("video-${f.id}", st, avExterno, miniatura = true)
        else -> Modifier
    }
    // pasta com capa: a foto preenche o card inteiro (miniatura + legenda) e o nome ganha uma sombra sutil
    val capaPasta = if (f.isFolder) estado.capa else null
    var capaPronta by remember(capaPasta) { mutableStateOf(false) }
    val corNome by animateColorAsState(if (capaPasta != null && capaPronta) Color.White else corTexto, Animacoes.espec(tween(300), tween(100)), label = "corNomePasta")
    val estiloNome = MaterialTheme.typography.bodySmall.let { if (capaPasta != null && capaPronta) it.copy(shadow = SombraTexto) else it }
    Box(
        Modifier.then(origemCard)
            .clip(forma)
            .background(corCard)
            .then(if (selecionado) Modifier.border(3.dp, tema.primary, forma) else Modifier)
            .combinedClickable(
                onClick = onClick,
                onLongClick = { if (vibrar) haptico.performHapticFeedback(HapticFeedbackType.LongPress); onLongClick() },
            ),
    ) {
        if (capaPasta != null) {
            CapaPasta(capaPasta, token, f.name, Modifier.matchParentSize(), aoCarregar = { capaPronta = true }, link = estado.capaLink, idPasta = f.id)
        } else if (LocalFundoCard.current == 2 && (f.isImage || f.isVideo)) {
            FundoMiniatura(f, token, Modifier.matchParentSize())
        }
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(1f).then(origemMiniatura)) {
                Miniatura(
                    f, token, estado, 44.dp, Modifier.fillMaxSize(),
                    aoCor = tinta.aoCor,
                    capaNoCard = true,
                )
                if (selecionado) {
                    Icon(
                        Icons.Rounded.CheckCircle, contentDescription = "Selecionado",
                        tint = tema.primary,
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp),
                    )
                }
            }
            // com "nomes na grade" desligado, só fotos e vídeos ficam sem nome (pastas e outros arquivos mantêm)
            if (LocalMostrarNomes.current || !(f.isImage || f.isVideo)) {
                NomeUmaLinha(
                    f.name,
                    Modifier.fillMaxWidth().padding(8.dp).graphicsLayer { alpha = alfaTexto() },
                    style = estiloNome,
                    color = corNome,
                )
            }
        }
    }
}

/** Linha da lista: só a miniatura é a origem da animação (nunca a linha inteira). */
@OptIn(ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
internal fun ItemLinha(
    f: DriveFile,
    token: String?,
    selecionado: Boolean,
    estado: EstadoArquivo,
    st: SharedTransitionScope,
    av: AnimatedVisibilityScope,
    avExterno: AnimatedVisibilityScope,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    alfaTexto: () -> Float = { 1f },
    ladoMiniatura: Dp = 56.dp,
) {
    val haptico = LocalHapticFeedback.current
    val vibrar = LocalVibrar.current
    val tema = MaterialTheme.colorScheme
    val tinta = tintaMidia(f, Color.Transparent, tema.onSurface)
    val forma = RoundedCornerShape(14.dp)
    val origem = when {
        f.isFolder -> Modifier.pastaBounds("pasta-${f.id}", st, av, desvanecerOrigem = true)
        f.isImage -> Modifier.midiaBounds("foto-${f.id}", st, avExterno, miniatura = true)
        f.isVideo -> Modifier.midiaBounds("video-${f.id}", st, avExterno, miniatura = true)
        else -> Modifier
    }
    val fundo = if (selecionado) tema.primaryContainer else tinta.fundo
    val corNome = if (selecionado) tema.onPrimaryContainer else tinta.texto
    Box(
        Modifier.fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .clip(forma)
            .background(fundo)
            .combinedClickable(
                onClick = onClick,
                onLongClick = { if (vibrar) haptico.performHapticFeedback(HapticFeedbackType.LongPress); onLongClick() },
            ),
    ) {
    if (!selecionado && LocalFundoCard.current == 2 && (f.isImage || f.isVideo)) {
        FundoMiniatura(f, token, Modifier.matchParentSize())
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Miniatura(
            f, token, estado, ladoMiniatura / 2,
            Modifier.size(ladoMiniatura).then(origem).clip(RoundedCornerShape(10.dp)),
            fundo = tema.surfaceVariant,
            aoCor = tinta.aoCor,
        )
        Column(Modifier.weight(1f).padding(start = 12.dp).graphicsLayer { alpha = alfaTexto() }) {
            NomeUmaLinha(f.name, Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyLarge, color = corNome)
            val sub = listOfNotNull(formatarData(f.modifiedTime), formatarTamanho(f.size)).joinToString("  ·  ")
            if (sub.isNotEmpty()) {
                val corSub = if (selecionado) tema.onPrimaryContainer else if (tinta.tingido) tinta.texto.copy(alpha = 0.8f) else tema.onSurfaceVariant
                Text(sub, style = MaterialTheme.typography.bodySmall, color = corSub)
            }
        }
        if (selecionado) {
            Icon(Icons.Rounded.CheckCircle, "Selecionado", tint = MaterialTheme.colorScheme.primary)
        }
    }
    }
}

@Composable
private fun SemConta(erro: String?, onEntrar: () -> Unit, modifier: Modifier) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "Entre com sua conta do Google para ver seus vídeos e fotos.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
        )
        erro?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp), textAlign = TextAlign.Center)
        }
        Button(onClick = onEntrar, modifier = Modifier.padding(top = 20.dp)) { Text("Entrar com Google") }
    }
}
