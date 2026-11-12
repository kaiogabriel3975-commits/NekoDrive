package com.nekodrive.app.ui.lixeira

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nekodrive.app.ui.FaixaVidro
import com.nekodrive.app.ui.IndicadorAtualizar
import com.nekodrive.app.ui.drive.DriveViewModel
import com.nekodrive.app.ui.drive.ItemLinha
import com.nekodrive.app.ui.fonteVidro
import com.nekodrive.app.ui.galeria.BotaoVidro
import com.nekodrive.app.ui.galeria.CirculoVidro
import com.nekodrive.app.ui.galeria.PilulaVidro
import com.nekodrive.app.ui.rememberRebote

private const val CHAVE = "lixeira"

// Diálogos: 0 nenhum, 1 excluir selecionados, 2 esvaziar (primeira confirmação), 3 esvaziar (segunda confirmação)
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalFoundationApi::class)
@Composable
fun LixeiraScreen(
    vm: DriveViewModel,
    st: SharedTransitionScope,
    av: AnimatedVisibilityScope,
    contentPadding: PaddingValues,
) {
    var dialogo by remember { mutableIntStateOf(0) }
    var alturaTopo by remember { mutableStateOf(0.dp) }
    BackHandler(enabled = vm.selecionados.isNotEmpty()) { vm.limparSelecao() }
    LaunchedEffect(vm.contaAtual?.email) { vm.carregarLixeira() }
    val rebote = rememberRebote(carregando = vm.carregandoLixeira, aoAtualizar = { vm.carregarLixeira() })

    val topo = contentPadding.calculateTopPadding()
    val base = contentPadding.calculateBottomPadding()
    val cor = MaterialTheme.colorScheme.onSurface

    Box(Modifier.fillMaxSize().nestedScroll(rebote)) {
        Box(Modifier.fillMaxSize().graphicsLayer { translationY = rebote.deslocamento }.fonteVidro()) {
            CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    state = vm.estadoLista(CHAVE),
                    contentPadding = PaddingValues(top = topo + alturaTopo, bottom = base),
                ) {
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
        IndicadorAtualizar(rebote, Modifier.align(Alignment.TopCenter).padding(top = topo + alturaTopo + 8.dp))

        FaixaVidro(topo = topo, aoMedir = { alturaTopo = it }, modifier = Modifier.align(Alignment.TopCenter)) {
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
                        BotaoVidro(Icons.Rounded.RestoreFromTrash, "Restaurar", { vm.restaurarSelecionados() }, cor)
                        BotaoVidro(Icons.Rounded.DeleteForever, "Excluir definitivamente", { dialogo = 1 }, cor)
                    }
                } else {
                    PilulaVidro {
                        Text(
                            "Lixeira",
                            Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.titleSmall,
                            color = cor,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (vm.lixeira.isNotEmpty()) {
                        PilulaVidro { TextButton(onClick = { dialogo = 2 }) { Text("Esvaziar") } }
                    }
                }
            }
            vm.erroLixeira?.let { msg ->
                PilulaVidro(Modifier.padding(horizontal = 12.dp)) {
                    Text(msg, Modifier.padding(start = 10.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { vm.carregarLixeira() }) { Text("Tentar de novo") }
                }
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
