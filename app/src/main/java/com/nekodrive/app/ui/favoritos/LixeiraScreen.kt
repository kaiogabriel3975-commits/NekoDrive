package com.nekodrive.app.ui.lixeira

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nekodrive.app.ui.drive.DriveViewModel
import com.nekodrive.app.ui.drive.ItemLinha

private const val CHAVE = "lixeira"

// Diálogos: 0 nenhum, 1 excluir selecionados, 2 esvaziar (primeira confirmação), 3 esvaziar (segunda confirmação)
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun LixeiraScreen(vm: DriveViewModel, st: SharedTransitionScope, av: AnimatedVisibilityScope, modifier: Modifier = Modifier) {
    var dialogo by remember { mutableIntStateOf(0) }
    BackHandler(enabled = vm.selecionados.isNotEmpty()) { vm.limparSelecao() }
    LaunchedEffect(vm.contaAtual?.email) { vm.carregarLixeira() }

    Column(modifier.fillMaxSize()) {
        if (vm.selecionados.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.limparSelecao() }) { Icon(Icons.Outlined.Close, "Cancelar seleção") }
                Text("${vm.selecionados.size} selecionado(s)", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { vm.restaurarSelecionados() }) { Icon(Icons.Outlined.RestoreFromTrash, "Restaurar") }
                IconButton(onClick = { dialogo = 1 }) { Icon(Icons.Outlined.DeleteForever, "Excluir definitivamente") }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Lixeira", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { dialogo = 2 }, enabled = vm.lixeira.isNotEmpty()) { Text("Esvaziar") }
            }
        }

        vm.erroLixeira?.let { msg ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(msg, Modifier.weight(1f), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { vm.carregarLixeira() }) { Text("Tentar de novo") }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(Modifier.fillMaxSize(), state = vm.estadoLista(CHAVE)) {
                items(vm.lixeira, key = { it.id }) { f ->
                    // Na lixeira o toque também seleciona: não dá para abrir itens apagados
                    ItemLinha(
                        f, vm.token, f.id in vm.selecionados, vm.estadoDe(f), st, av, av,
                        { vm.alternarSelecao(f) }, { vm.alternarSelecao(f) },
                    )
                }
                if (vm.temMaisLixeira) {
                    item {
                        LaunchedEffect(vm.lixeira.size) { vm.carregarMaisLixeira() }
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(24.dp))
                        }
                    }
                }
            }
            if (vm.carregandoLixeira && vm.lixeira.isEmpty()) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else if (!vm.carregandoLixeira && vm.lixeira.isEmpty() && vm.erroLixeira == null) {
                Text(
                    "A lixeira está vazia.",
                    Modifier.align(Alignment.Center).padding(32.dp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    when (dialogo) {
        1 -> AlertDialog(
            onDismissRequest = { dialogo = 0 },
            title = { Text("Excluir definitivamente?") },
            text = { Text("${vm.selecionados.size} item(ns) serão apagados para sempre. Não dá para desfazer.") },
            confirmButton = { TextButton(onClick = { dialogo = 0; vm.excluirDefinitivoSelecionados() }) { Text("Excluir") } },
            dismissButton = { TextButton(onClick = { dialogo = 0 }) { Text("Cancelar") } },
        )
        2 -> AlertDialog(
            onDismissRequest = { dialogo = 0 },
            title = { Text("Esvaziar a lixeira?") },
            text = { Text("Tudo que está na lixeira desta conta será apagado.") },
            confirmButton = { TextButton(onClick = { dialogo = 3 }) { Text("Continuar") } },
            dismissButton = { TextButton(onClick = { dialogo = 0 }) { Text("Cancelar") } },
        )
        3 -> AlertDialog(
            onDismissRequest = { dialogo = 0 },
            title = { Text("Tem certeza?") },
            text = { Text("Esvaziar a lixeira apaga tudo para sempre, inclusive itens que não aparecem na lista. Não dá para desfazer.") },
            confirmButton = { TextButton(onClick = { dialogo = 0; vm.esvaziarLixeira() }) { Text("Apagar tudo") } },
            dismissButton = { TextButton(onClick = { dialogo = 0 }) { Text("Cancelar") } },
        )
    }
}
