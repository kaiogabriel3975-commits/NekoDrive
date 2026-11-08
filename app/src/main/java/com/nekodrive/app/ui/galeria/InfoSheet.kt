package com.nekodrive.app.ui.galeria

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nekodrive.app.ui.drive.DriveViewModel

/** Folha com as informações do arquivo que o Drive consegue confirmar (campos ausentes não aparecem). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoSheet(vm: DriveViewModel) {
    ModalBottomSheet(onDismissRequest = { vm.fecharDetalhes() }) {
        if (vm.carregandoDetalhes) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
                item {
                    Text(vm.tituloDetalhes, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
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
}
