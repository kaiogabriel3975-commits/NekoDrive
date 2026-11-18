package com.nekodrive.app.ui.drive

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest

/** Folha para escolher, entre as fotos de uma pasta, a que vira a capa dela. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeletorCapa(vm: DriveViewModel) {
    val pasta = vm.pastaDaCapa ?: return
    val ctx = LocalContext.current
    ModalBottomSheet(onDismissRequest = { vm.fecharSeletorCapa() }) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text("Capa de “${pasta.name}”", style = MaterialTheme.typography.titleMedium)
            if (vm.capaDe(pasta.id) != null) {
                TextButton(onClick = { vm.escolherCapa(null) }) { Text("Remover capa") }
            }
            val lista = vm.imagensCapa
            when {
                vm.carregandoCapa && lista.isEmpty() -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                lista.isEmpty() -> Text(
                    "Esta pasta não tem fotos nem vídeos.",
                    Modifier.padding(vertical = 24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.heightIn(max = 480.dp).padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(lista, key = { it.id }) { foto ->
                        val pedido = ImageRequest.Builder(ctx)
                            .data(foto.thumbnailLink ?: "https://www.googleapis.com/drive/v3/files/${foto.id}?alt=media")
                            .apply { vm.token?.let { addHeader("Authorization", "Bearer $it") } }
                            .size(300, 300)
                            .build()
                        Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(10.dp)).clickable { vm.escolherCapa(foto) }) {
                            AsyncImage(pedido, foto.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            if (foto.isVideo) {
                                Icon(
                                    Icons.Rounded.PlayArrow, "Vídeo", tint = Color.White,
                                    modifier = Modifier.align(Alignment.BottomStart).padding(4.dp)
                                        .background(Color.Black.copy(alpha = 0.55f), CircleShape).padding(3.dp).size(16.dp),
                                )
                            }
                        }
                    }
                    if (vm.temMaisCapa) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            LaunchedEffect(lista.size) { vm.carregarMaisCapa() }
                            Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
