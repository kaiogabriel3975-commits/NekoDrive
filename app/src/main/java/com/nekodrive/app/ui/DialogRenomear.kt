package com.nekodrive.app.ui

import com.nekodrive.app.data.tr
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/** Pede o novo nome de um arquivo ou pasta. O nome já vem selecionado, sem a extensão. */
@Composable
fun DialogRenomear(
    nomeAtual: String,
    aoConfirmar: (String) -> Unit,
    aoCancelar: () -> Unit,
    titulo: String = tr("Renomear"),
    rotuloConfirmar: String = tr("Renomear"),
) {
    val semExtensao = nomeAtual.substringBeforeLast('.', nomeAtual)
    var campo by remember { mutableStateOf(TextFieldValue(nomeAtual, TextRange(0, semExtensao.length))) }
    val foco = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { foco.requestFocus() } }
    AlertDialog(
        onDismissRequest = aoCancelar,
        title = { Text(titulo) },
        text = {
            OutlinedTextField(
                value = campo,
                onValueChange = { campo = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(foco),
            )
        },
        confirmButton = {
            TextButton(onClick = { aoConfirmar(campo.text) }, enabled = campo.text.isNotBlank()) { Text(rotuloConfirmar) }
        },
        dismissButton = { TextButton(onClick = aoCancelar) { Text(tr("Cancelar")) } },
    )
}
