package com.nekodrive.app.ui.drive

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Folder
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nekodrive.app.data.drive.DriveFile
import kotlinx.coroutines.CancellationException

/**
 * Escolha da pasta de destino para mover ou copiar: navega pelas pastas do Drive (começando no Meu Drive)
 * e confirma em "aqui". Pastas que estão entre os itens escolhidos não podem ser abertas (nem como destino).
 */
@Composable
fun SeletorDestino(vm: DriveViewModel) {
    val copiar = vm.seletorDestino ?: return
    var trilha by remember { mutableStateOf(listOf("root" to "Meu Drive")) }
    var pastas by remember { mutableStateOf<List<DriveFile>>(emptyList()) }
    var carregando by remember { mutableStateOf(true) }
    var falha by remember { mutableStateOf<String?>(null) }
    val atual = trilha.last()
    val bloqueadas = remember(vm.alvosDestino) { vm.alvosDestino.filter { it.isFolder }.map { it.id }.toSet() }

    LaunchedEffect(atual.first) {
        carregando = true
        falha = null
        pastas = emptyList()
        try {
            val todas = mutableListOf<DriveFile>()
            var pagina: String? = null
            do {
                val p = vm.pastasDe(atual.first, pagina)
                todas += p.files
                pagina = p.nextPageToken
            } while (pagina != null)
            pastas = todas
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            falha = e.message ?: "Não foi possível listar as pastas"
        } finally {
            carregando = false
        }
    }

    AlertDialog(
        onDismissRequest = { vm.fecharSeletorDestino() },
        title = { Text(if (copiar) "Copiar para…" else "Mover para…") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (trilha.size > 1) {
                        IconButton(onClick = { trilha = trilha.dropLast(1) }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Voltar") }
                    }
                    Text(atual.second, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 8.dp))
                }
                Box(Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 320.dp)) {
                    when {
                        carregando -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                        falha != null -> Text(falha!!, color = MaterialTheme.colorScheme.error)
                        pastas.isEmpty() -> Text("Sem subpastas aqui", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else -> LazyColumn {
                            items(pastas, key = { it.id }) { f ->
                                val livre = f.id !in bloqueadas
                                Row(
                                    Modifier.fillMaxWidth().clickable(enabled = livre) { trilha = trilha + (f.id to f.name) }
                                        .padding(vertical = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Rounded.Folder, null,
                                        tint = if (livre) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    )
                                    Text(
                                        f.name, maxLines = 1,
                                        color = if (livre) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.confirmarDestino(atual.first, atual.second) }) { Text(if (copiar) "Copiar aqui" else "Mover aqui") }
        },
        dismissButton = { TextButton(onClick = { vm.fecharSeletorDestino() }) { Text("Cancelar") } },
    )
}
