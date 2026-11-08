package com.nekodrive.app.ui.favoritos

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nekodrive.app.data.drive.DriveFile
import com.nekodrive.app.ui.FaixaVidro
import com.nekodrive.app.ui.drive.DriveViewModel
import com.nekodrive.app.ui.drive.ItemGrade
import com.nekodrive.app.ui.drive.ItemLinha
import com.nekodrive.app.ui.fonteVidro

private const val CHAVE = "favoritos"

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun FavoritosScreen(
    vm: DriveViewModel,
    st: SharedTransitionScope,
    av: AnimatedVisibilityScope,
    contentPadding: PaddingValues,
) {
    var confirmar by remember { mutableStateOf(false) }
    var alturaTopo by remember { mutableStateOf(0.dp) }
    BackHandler(enabled = vm.selecionados.isNotEmpty()) { vm.limparSelecao() }
    LaunchedEffect(vm.contaAtual?.email) { vm.carregarFavoritos() }

    val lista = vm.favoritosItens.filter { it.id !in vm.indisponiveis }
    val aoTocar: (DriveFile) -> Unit = { f ->
        if (vm.selecionados.isNotEmpty()) vm.alternarSelecao(f)
        else if (f.isImage) vm.abrirFoto(f, lista, CHAVE)
        else if (f.isVideo) vm.abrirVideo(f, lista, CHAVE)
    }
    val topo = contentPadding.calculateTopPadding()
    val base = contentPadding.calculateBottomPadding()

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().fonteVidro()) {
            if (vm.grade) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(110.dp),
                    state = vm.estadoGrade(CHAVE),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = topo + alturaTopo + 12.dp, bottom = base + 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(lista, key = { it.id }) { f ->
                        ItemGrade(
                            f, vm.token, f.id in vm.selecionados, vm.estadoDe(f), st, av, av,
                            { aoTocar(f) }, { vm.alternarSelecao(f) },
                        )
                    }
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    state = vm.estadoLista(CHAVE),
                    contentPadding = PaddingValues(top = topo + alturaTopo, bottom = base),
                ) {
                    items(lista, key = { it.id }) { f ->
                        ItemLinha(
                            f, vm.token, f.id in vm.selecionados, vm.estadoDe(f), st, av, av,
                            { aoTocar(f) }, { vm.alternarSelecao(f) },
                        )
                    }
                }
            }
            if (lista.isEmpty()) {
                Text(
                    "Nenhum favorito ainda.\nSegure um arquivo e toque na estrela.",
                    Modifier.align(Alignment.Center).padding(32.dp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        FaixaVidro(topo = topo, aoMedir = { alturaTopo = it }, modifier = Modifier.align(Alignment.TopCenter)) {
            if (vm.selecionados.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { vm.limparSelecao() }) { Icon(Icons.Rounded.Close, "Cancelar seleção") }
                    Text("${vm.selecionados.size} selecionado(s)", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { vm.favoritarSelecao(lista) }) { Icon(Icons.Rounded.Star, "Remover dos favoritos") }
                    IconButton(onClick = { vm.baixarSelecionados() }) { Icon(Icons.Rounded.Download, "Baixar") }
                    IconButton(onClick = { confirmar = true }) { Icon(Icons.Rounded.Delete, "Mover para a lixeira") }
                }
            } else {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Favoritos", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { vm.alternarModo() }) {
                        Icon(
                            if (vm.grade) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView,
                            "Alternar entre grade e lista",
                        )
                    }
                }
            }
            if (vm.indisponiveis.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${vm.indisponiveis.size} favorito(s) não estão mais no Drive.",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = { vm.removerIndisponiveis() }) { Text("Remover da lista") }
                }
            }
        }
    }

    if (confirmar) {
        AlertDialog(
            onDismissRequest = { confirmar = false },
            title = { Text("Mover para a lixeira?") },
            text = { Text("${vm.selecionados.size} item(ns) vão para a lixeira do Drive e saem dos favoritos.") },
            confirmButton = { TextButton(onClick = { confirmar = false; vm.moverParaLixeira() }) { Text("Mover") } },
            dismissButton = { TextButton(onClick = { confirmar = false }) { Text("Cancelar") } },
        )
    }
}
