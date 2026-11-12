package com.nekodrive.app.ui.drive

import android.graphics.drawable.BitmapDrawable
import androidx.activity.compose.BackHandler
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
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.material.icons.rounded.Description
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
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
import com.nekodrive.app.ui.galeria.PilulaVidro
import com.nekodrive.app.ui.rememberRebote
import com.nekodrive.app.ui.vidro
import kotlinx.coroutines.launch

private val CurvaIOS = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

/** Mesma duração de antes (cerca de 500 ms) com curva de desaceleração suave, no estilo das animações do iOS. */
@OptIn(ExperimentalSharedTransitionApi::class)
private val CurvaSuave = BoundsTransform { _, _ -> tween(durationMillis = 500, easing = CurvaIOS) }

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
        this@pastaBounds.sharedBounds(
            rememberSharedContentState(key = chave),
            animatedVisibilityScope = av,
            enter = EnterTransition.None,
            exit = if (desvanecerOrigem) fadeOut(tween(140)) else ExitTransition.None,
            boundsTransform = CurvaSuave,
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
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

    BackHandler(enabled = vm.buscando || vm.pilha.size > 1) { vm.voltar() }
    BackHandler(enabled = vm.selecionados.isNotEmpty()) { vm.limparSelecao() }

    if (vm.contaAtual == null) {
        SemConta(vm.erro, onEntrar, Modifier.padding(contentPadding))
        return
    }

    val aoTocar: (DriveFile) -> Unit = { f ->
        if (vm.selecionados.isNotEmpty()) vm.alternarSelecao(f)
        else if (f.isFolder) vm.abrirPasta(f)
        else if (f.isImage || f.isVideo) vm.abrirFoto(f)
        else arquivoExterno = f
    }
    val chave = if (vm.buscando) "busca" else vm.pilha.last().id

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = chave,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = { fadeIn(tween(1)) togetherWith fadeOut(tween(500)) },
            label = "pasta",
        ) { chavePagina ->
            PaginaPasta(
                atual = chavePagina == chave,
                chave = chavePagina,
                vm = vm,
                st = st,
                av = this@AnimatedContent,
                avExterno = avExterno,
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
            Barra(vm) { confirmar = true }
            vm.erro?.let { msg ->
                PilulaVidro(Modifier.padding(horizontal = 12.dp)) {
                    Text(msg, Modifier.padding(start = 10.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { vm.recarregar() }) { Text("Tentar de novo") }
                }
            }
        }
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
private fun Barra(vm: DriveViewModel, aoExcluir: () -> Unit) {
    val cor = MaterialTheme.colorScheme.onSurface
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (vm.selecionados.isNotEmpty()) {
            CirculoVidro(aoClicar = { vm.limparSelecao() }) { Icon(Icons.Rounded.Close, "Cancelar seleção", tint = cor) }
            PilulaVidro {
                Text(
                    "${vm.selecionados.size} selecionado(s)",
                    Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = cor,
                )
            }
            Spacer(Modifier.weight(1f))
            PilulaVidro {
                BotaoVidro(Icons.Rounded.StarBorder, "Favoritar", { vm.favoritarSelecao(vm.itens) }, cor)
                BotaoVidro(Icons.Rounded.Download, "Baixar", { vm.baixarSelecionados() }, cor)
                BotaoVidro(Icons.Rounded.Delete, "Mover para a lixeira", aoExcluir, cor)
            }
        } else if (vm.buscando) {
            val foco = remember { FocusRequester() }
            LaunchedEffect(Unit) { foco.requestFocus() }
            CirculoVidro(aoClicar = { vm.sairDaBusca() }) { Icon(Icons.Rounded.Close, "Fechar busca", tint = cor) }
            Box(Modifier.weight(1f).clip(RoundedCornerShape(50)).vidro()) {
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
            var menuOrdem by remember { mutableStateOf(false) }
            if (vm.pilha.size > 1) {
                CirculoVidro(aoClicar = { vm.voltar() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Voltar", tint = cor) }
            }
            Spacer(Modifier.weight(1f))
            CirculoVidro(aoClicar = { vm.iniciarBusca() }) { Icon(Icons.Rounded.Search, "Buscar", tint = cor) }
            Box {
                CirculoVidro(aoClicar = { menuOrdem = true }) { Icon(Icons.AutoMirrored.Rounded.Sort, "Ordenar", tint = cor) }
                DropdownMenu(expanded = menuOrdem, onDismissRequest = { menuOrdem = false }) {
                    CampoOrdem.entries.forEach { campo ->
                        val seta = if (campo == vm.campo) (if (vm.crescente) "  ↑" else "  ↓") else ""
                        DropdownMenuItem(
                            text = { Text(campo.rotulo + seta) },
                            onClick = { vm.definirOrdem(campo); menuOrdem = false },
                        )
                    }
                }
            }
            CirculoVidro(aoClicar = { vm.alternarModo() }) {
                Icon(
                    if (vm.grade) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView,
                    "Alternar entre grade e lista",
                    tint = cor,
                )
            }
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
    espaco: PaddingValues,
    aoTocar: (DriveFile) -> Unit,
) {
    // A página que está saindo mostra a última lista que ela exibiu, e não a da pasta nova.
    val ultima = remember { mutableStateOf<List<DriveFile>>(emptyList()) }
    if (atual) SideEffect { ultima.value = vm.itens }
    val lista = if (atual) vm.itens else ultima.value
    val grade = vm.grade

    // A tela da pasta nasce com o formato e a cor do card e vai virando tela cheia (e o contrário ao fechar)
    val tema = MaterialTheme.colorScheme
    val raio by av.transition.animateDp(transitionSpec = { tween(500, easing = CurvaIOS) }, label = "raio") {
        if (it == EnterExitState.Visible) 0.dp else 14.dp
    }
    val fundo by av.transition.animateColor(transitionSpec = { tween(500, easing = CurvaIOS) }, label = "fundo") {
        if (it == EnterExitState.Visible) tema.background else tema.surfaceVariant
    }
    val opacidadeConteudo by av.transition.animateFloat(transitionSpec = { tween(350) }, label = "conteudo") {
        if (it == EnterExitState.Visible) 1f else 0f
    }
    val rebote = rememberRebote(carregando = vm.carregando && atual, aoAtualizar = { vm.atualizar() })

    Surface(
        Modifier.pastaBounds("pasta-$chave", st, av).fonteVidro().fillMaxSize(),
        shape = RoundedCornerShape(raio),
        color = fundo,
    ) {
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = opacidadeConteudo }.nestedScroll(rebote)) {
            Box(Modifier.fillMaxSize().graphicsLayer { translationY = rebote.deslocamento }) {
                CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
                    LazyVerticalGrid(
                        columns = if (grade) GridCells.Adaptive(110.dp) else GridCells.Fixed(1),
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
                                    placementSpec = spring(
                                        dampingRatio = 0.85f,
                                        stiffness = 380f,
                                        visibilityThreshold = IntOffset(1, 1),
                                    ),
                                ),
                            ) {
                                if (grade) {
                                    ItemGrade(
                                        f, vm.token, f.id in vm.selecionados, vm.estadoDe(f), st, av, avExterno,
                                        { aoTocar(f) }, { vm.alternarSelecao(f) },
                                    )
                                } else {
                                    ItemLinha(
                                        f, vm.token, f.id in vm.selecionados, vm.estadoDe(f), st, av, avExterno,
                                        { aoTocar(f) }, { vm.alternarSelecao(f) },
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
    escalaConteudo: ContentScale = ContentScale.Crop,
) {
    val context = LocalContext.current
    val ehGif = f.mimeType == "image/gif"
    Box(modifier.background(fundo), contentAlignment = Alignment.Center) {
        if (!f.isFolder && (f.thumbnailLink != null || ehGif)) {
            // GIFs animam na miniatura, em baixa resolução e sem reduzir os quadros por segundo
            val request = ImageRequest.Builder(context)
                .data(if (ehGif) "https://www.googleapis.com/drive/v3/files/${f.id}?alt=media" else f.thumbnailLink)
                .apply { if (token != null) addHeader("Authorization", "Bearer $token") }
                .apply { if (ehGif) { size(256, 256); emLoop() } }
                .allowHardware(false)
                .crossfade(true)
                .build()
            AsyncImage(
                request, f.name, Modifier.fillMaxSize(), contentScale = escalaConteudo,
                onSuccess = { s ->
                    if (aoCor != null) {
                        (s.result.drawable as? BitmapDrawable)?.bitmap?.let { b -> aoCor(corDominante(b)) }
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
        val progresso = estado.progresso
        if (progresso != null && progresso >= 0f) {
            CircularProgressIndicator(
                progress = { progresso },
                modifier = Modifier.align(Alignment.BottomStart).padding(4.dp).size(20.dp),
                strokeWidth = 2.dp,
            )
        } else if (estado.noCache) {
            Icon(
                Icons.Rounded.DownloadDone, "Salvo no cache", tint = Color.White,
                modifier = Modifier.align(Alignment.BottomStart).padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape).padding(3.dp).size(14.dp),
            )
        }
        if (estado.favorito) {
            Icon(
                Icons.Rounded.Star, "Favorito", tint = Color(0xFFFFC107),
                modifier = Modifier.align(Alignment.TopStart).padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape).padding(3.dp).size(14.dp),
            )
        }
        if (estado.assistido) {
            Icon(
                Icons.Rounded.TaskAlt, "Assistido", tint = Color.White,
                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape).padding(3.dp).size(14.dp),
            )
        }
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
) {
    val haptico = LocalHapticFeedback.current
    val tema = MaterialTheme.colorScheme
    var dominante by remember(f.id) { mutableStateOf(CoresMidia.obter(f.id)) }
    val alvoCard = dominante?.let { lerp(tema.surfaceVariant, it, 0.55f) } ?: tema.surfaceVariant
    val corCard by animateColorAsState(alvoCard, tween(700), label = "corCard")
    val alvoTexto = when {
        dominante == null -> tema.onSurfaceVariant
        corCard.luminance() > 0.5f -> Color(0xFF1B1B1F)
        else -> Color.White
    }
    val corTexto by animateColorAsState(alvoTexto, tween(700), label = "corTexto")

    val forma = RoundedCornerShape(14.dp)
    val origemCard = if (f.isFolder) Modifier.pastaBounds("pasta-${f.id}", st, av, desvanecerOrigem = true) else Modifier
    val origemMiniatura = when {
        f.isImage -> Modifier.pastaBounds("foto-${f.id}", st, avExterno)
        f.isVideo -> Modifier.pastaBounds("video-${f.id}", st, avExterno)
        else -> Modifier
    }
    Column(
        Modifier.then(origemCard)
            .clip(forma)
            .background(corCard)
            .then(if (selecionado) Modifier.border(3.dp, tema.primary, forma) else Modifier)
            .combinedClickable(
                onClick = onClick,
                onLongClick = { haptico.performHapticFeedback(HapticFeedbackType.LongPress); onLongClick() },
            ),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).then(origemMiniatura)) {
            Miniatura(
                f, token, estado, 44.dp, Modifier.fillMaxSize(),
                escalaConteudo = if (
                    f.isImage && avExterno.transition.targetState == EnterExitState.PostExit
                ) ContentScale.Fit else ContentScale.Crop,
                aoCor = if (f.isImage || f.isVideo) {
                    { c ->
                        if (c != Color.Unspecified) {
                            CoresMidia.guardar(f.id, c)
                            dominante = c
                        }
                    }
                } else {
                    null
                },
            )
            if (selecionado) {
                Icon(
                    Icons.Rounded.CheckCircle, contentDescription = "Selecionado",
                    tint = tema.primary,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp),
                )
            }
        }
        NomeUmaLinha(f.name, Modifier.fillMaxWidth().padding(8.dp), style = MaterialTheme.typography.bodySmall, color = corTexto)
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
) {
    val haptico = LocalHapticFeedback.current
    val origem = when {
        f.isFolder -> Modifier.pastaBounds("pasta-${f.id}", st, av, desvanecerOrigem = true)
        f.isImage -> Modifier.pastaBounds("foto-${f.id}", st, avExterno)
        f.isVideo -> Modifier.pastaBounds("video-${f.id}", st, avExterno)
        else -> Modifier
    }
    val fundo = if (selecionado) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    Row(
        Modifier.fillMaxWidth()
            .background(fundo)
            .combinedClickable(
                onClick = onClick,
                onLongClick = { haptico.performHapticFeedback(HapticFeedbackType.LongPress); onLongClick() },
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Miniatura(
            f, token, estado, 28.dp,
            Modifier.size(56.dp).then(origem).clip(RoundedCornerShape(10.dp)),
            fundo = MaterialTheme.colorScheme.surfaceVariant,
        )
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            NomeUmaLinha(f.name, Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyLarge)
            val sub = listOfNotNull(formatarData(f.modifiedTime), formatarTamanho(f.size)).joinToString("  ·  ")
            if (sub.isNotEmpty()) {
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (selecionado) {
            Icon(Icons.Rounded.CheckCircle, "Selecionado", tint = MaterialTheme.colorScheme.primary)
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
