package com.nekodrive.app.ui.drive

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.nekodrive.app.data.drive.DriveFile

@Composable
fun DriveScreen(vm: DriveViewModel, onEntrar: () -> Unit, modifier: Modifier = Modifier) {
    BackHandler(enabled = vm.pilha.size > 1) { vm.voltar() }

    if (vm.contaAtual == null) {
        SemConta(vm.erro, onEntrar, modifier)
        return
    }

    Column(modifier.fillMaxSize()) {
        Text(
            vm.pilha.joinToString("  ›  ") { it.nome },
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        vm.erro?.let { msg ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(msg, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.recarregar() }) { Text("Tentar de novo") }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(110.dp),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(vm.itens, key = { it.id }) { f ->
                    ArquivoCard(f, vm.token) { if (f.isFolder) vm.abrirPasta(f) }
                }
                if (vm.temMais) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        LaunchedEffect(vm.itens.size) { vm.carregarMais() }
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(24.dp))
                        }
                    }
                }
            }
            if (vm.carregando && vm.itens.isEmpty()) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else if (!vm.carregando && vm.itens.isEmpty() && vm.erro == null) {
                Text("Pasta vazia", Modifier.align(Alignment.Center))
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

@Composable
private fun ArquivoCard(f: DriveFile, token: String?, onClick: () -> Unit) {
    val context = LocalContext.current
    Card(onClick = onClick) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
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
                    icone, contentDescription = null, modifier = Modifier.size(44.dp),
                    tint = if (f.isFolder) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
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
