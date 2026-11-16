package com.nekodrive.app.ui.galeria

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nekodrive.app.ui.DialogRenomear
import com.nekodrive.app.ui.drive.DriveViewModel

/** Folha com as informações do arquivo que o Drive consegue confirmar (campos ausentes não aparecem) e o botão de renomear. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoSheet(vm: DriveViewModel) {
    val arquivo = vm.arquivoDetalhes
    var renomear by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = { vm.fecharDetalhes() }) {
        if (vm.carregandoDetalhes) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
                item {
                    Row(Modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(vm.tituloDetalhes, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        if (arquivo != null) {
                            TextButton(onClick = { renomear = true }) {
                                Icon(Icons.Rounded.Edit, null, Modifier.size(18.dp))
                                Text("Renomear", Modifier.padding(start = 6.dp))
                            }
                        }
                    }
                }
                items(vm.detalhes.orEmpty()) { (chave, valor) ->
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Text(chave, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        SelectionContainer { Text(valor, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
        }
    }
    if (renomear && arquivo != null) {
        DialogRenomear(
            nomeAtual = arquivo.name,
            aoConfirmar = { novo -> renomear = false; vm.renomear(arquivo, novo) },
            aoCancelar = { renomear = false },
        )
    }
}
