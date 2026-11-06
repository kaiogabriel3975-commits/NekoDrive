package com.nekodrive.app.ui.drive

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.nekodrive.app.data.drive.DriveFile

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun DriveScreen(
    vm: DriveViewModel,
    onEntrar: () -> Unit,
    st: SharedTransitionScope,
    avExterno: AnimatedVisibilityScope,
    modifier: Modifier = Modifier,
) {
    var confirmar by remember { mutableStateOf(false) }

    BackHandler(enabled = vm.buscando || vm.pilha.size > 1) { vm.voltar() }
    BackHandler(enabled = vm.selecionados.isNotEmpty()) { vm.limparSelecao() }

    if (vm.contaAtual == null) {
        SemConta(vm.erro, onEntrar, modifier)
        return
    }

    val aoTocar: (DriveFile) -> Unit = { f ->
        if (vm.selecionados.isNotEmpty()) vm.alternarSelecao(f)
        else if (f.isFolder) vm.abrirPasta(f)
        else if (f.isImage) vm.abrirFoto(f)
        else if (f.isVideo) vm.abrirVideo(f)
    }
    val chave = if (vm.buscando) "busca" else vm.pilha.last().id

    Column(modifier.fillMaxSize()) {
        Barra(vm) { confirmar = true }
        vm.erro?.let { msg ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(msg, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.recarregar() }) { Text("Tentar de novo") }
            }
        }
        AnimatedContent(
            targetState = chave,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(240)) },
            label = "pasta",
        ) { chavePagina ->
            PaginaPasta(
                atual = chavePagina == chave,
                chave = chavePagina,
                vm = vm,
                st = st,
                av = this@AnimatedContent,
                avExterno = avExterno,
                aoTocar = aoTocar,
            )
        }
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

// ---------- barra superior da tela (caminho, busca, ordenação, modo) ----------

@Composable
private fun Barra(vm: DriveViewModel, aoExcluir: () -> Unit) {
    if (vm.selecionados.isNotEmpty()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.limparSelecao() }) { Icon(Icons.Outlined.Close, "Cancelar seleção") }
            Text("${vm.selecionados.size} selecionado(s)", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { vm.favoritarSelecao(vm.itens) }) { Icon(Icons.Outlined.StarBorder, "Favoritar") }
            IconButton(onClick = { vm.baixarSelecionados() }) { Icon(Icons.Outlined.Download, "Baixar") }
            IconButton(onClick = aoExcluir) { Icon(Icons.Outlined.Delete, "Mover para a lixeira") }
        }
    } else if (vm.buscando) {
        val foco = remember { FocusRequester() }
        LaunchedEffect(Unit) { foco.requestFocus() }
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.sairDaBusca() }) { Icon(Icons.Outlined.Close, "Fechar busca") }
            TextField(
                value = vm.termo,
                onValueChange = { vm.definirTermo(it) },
                placeholder = { Text("Buscar no Drive") },
                singleLine = true,
                modifier = Modifier.weight(1f).focusRequester(foco),
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
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                vm.pilha.joinToString("  ›  ") { it.nome },
                Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(onClick = { vm.iniciarBusca() }) { Icon(Icons.Outlined.Search, "Buscar") }
            Box {
                IconButton(onClick = { menuOrdem = true }) { Icon(Icons.AutoMirrored.Outlined.Sort, "Ordenar") }
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
            IconButton(onClick = { vm.alternarModo() }) {
                Icon(
                    if (vm.grade) Icons.AutoMirrored.Outlined.ViewList else Icons.Outlined.GridView,
                    "Alternar entre grade e lista",
                )
            }
        }
    }
}

// ---------- página de uma pasta (com a animação de expansão) ----------

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun Modifier.pastaBounds(chave: String, st: SharedTransitionScope, av: AnimatedVisibilityScope): Modifier =
    with(st) { this@pastaBounds.sharedBounds(rememberSharedContentState(key = chave), animatedVisibilityScope = av) }

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun PaginaPasta(
    atual: Boolean,
    chave: String,
    vm: DriveViewModel,
    st: SharedTransitionScope,
    av: AnimatedVisibilityScope,
    avExterno: AnimatedVisibilityScope,
    aoTocar: (DriveFile) -> Unit,
) {
    // A página que está saindo mostra a última lista que ela exibiu, e não a da pasta nova.
    val ultima = remember { mutableStateOf<List<DriveFile>>(emptyList()) }
    if (atual) SideEffect { ultima.value = vm.itens }
    val lista = if (atual) vm.itens else ultima.value

    Surface(Modifier.pastaBounds("pasta-$chave", st, av).fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            if (vm.grade) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(110.dp),
                    state = vm.estadoGrade(chave),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(lista, key = { it.id }) { f ->
                        ItemGrade(f, vm.token, f.id in vm.selecionados, vm.estadoDe(f), st, av, avExterno, { aoTocar(f) }, { vm.alternarSelecao(f) })
                    }
                    if (atual && vm.temMais) {
                        item(span = { GridItemSpan(maxLineSpan) }) { CarregandoMais(vm) }
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize(), state = vm.estadoLista(chave)) {
                    items(lista, key = { it.id }) { f ->
                        ItemLinha(f, vm.token, f.id in vm.selecionados, vm.estadoDe(f), st, av, avExterno, { aoTocar(f) }, { vm.alternarSelecao(f) })
                    }
                    if (atual && vm.temMais) {
                        item { CarregandoMais(vm) }
                    }
                }
            }
            if (atual) {
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

@Composable
private fun Miniatura(f: DriveFile, token: String?, estado: EstadoArquivo, tamanhoIcone: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        if (f.thumbnailLink != null && !f.isFolder) {
            val request = ImageRequest.Builder(context)
                .data(f.thumbnailLink)
                .apply { if (token != null) addHeader("Authorization", "Bearer $token") }
                .crossfade(true)
                .build()
            AsyncImage(request, f.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            val icone = when {
                f.isFolder -> Icons.Outlined.Folder
                f.isVideo -> Icons.Outlined.Movie
                f.isImage -> Icons.Outlined.Image
                else -> Icons.Outlined.Description
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
                Icons.Outlined.DownloadDone, "Salvo no cache", tint = Color.White,
                modifier = Modifier.align(Alignment.BottomStart).padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape).padding(3.dp).size(14.dp),
            )
        }
        if (estado.favorito) {
            Icon(
                Icons.Filled.Star, "Favorito", tint = Color(0xFFFFC107),
                modifier = Modifier.align(Alignment.TopStart).padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape).padding(3.dp).size(14.dp),
            )
        }
        if (estado.assistido) {
            Icon(
                Icons.Outlined.TaskAlt, "Assistido", tint = Color.White,
                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape).padding(3.dp).size(14.dp),
            )
        }
    }
}

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
    val borda = if (selecionado) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null
    val base = when {
        f.isFolder -> Modifier.pastaBounds("pasta-${f.id}", st, av)
        f.isImage -> Modifier.pastaBounds("foto-${f.id}", st, avExterno)
        f.isVideo -> Modifier.pastaBounds("video-${f.id}", st, avExterno)
        else -> Modifier
    }
    Card(modifier = base.combinedClickable(onClick = onClick, onLongClick = onLongClick), border = borda) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            Miniatura(f, token, estado, 44.dp, Modifier.fillMaxSize())
            if (selecionado) {
                Icon(
                    Icons.Filled.CheckCircle, contentDescription = "Selecionado",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp),
                )
            }
        }
        Text(
            f.name,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(8.dp),
        )
    }
}

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
    val base = when {
        f.isFolder -> Modifier.pastaBounds("pasta-${f.id}", st, av)
        f.isImage -> Modifier.pastaBounds("foto-${f.id}", st, avExterno)
        f.isVideo -> Modifier.pastaBounds("video-${f.id}", st, avExterno)
        else -> Modifier
    }
    val fundo = if (selecionado) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    Row(
        base.fillMaxWidth()
            .background(fundo)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Miniatura(f, token, estado, 28.dp, Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            val sub = listOfNotNull(formatarData(f.modifiedTime), formatarTamanho(f.size)).joinToString("  ·  ")
            if (sub.isNotEmpty()) {
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (selecionado) {
            Icon(Icons.Filled.CheckCircle, "Selecionado", tint = MaterialTheme.colorScheme.primary)
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
