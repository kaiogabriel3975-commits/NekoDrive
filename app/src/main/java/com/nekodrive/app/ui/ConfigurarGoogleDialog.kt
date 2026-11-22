package com.nekodrive.app.ui

import com.nekodrive.app.data.tr
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nekodrive.app.data.auth.OAuthConfig

/**
 * Tela do ID do cliente do Google Cloud, que o login por navegador precisa. Mostra o passo a passo, o pacote e o
 * SHA-1 deste app (para copiar na criação do cliente) e o campo para colar o ID.
 */
@Composable
fun ConfigurarGoogleDialog(onFechar: () -> Unit, onSalvo: () -> Unit) {
    val context = LocalContext.current
    var id by remember { mutableStateOf(OAuthConfig.clientId(context)) }
    val sha1 = remember { OAuthConfig.sha1(context) }
    val valido = OAuthConfig.idValido(id)

    AlertDialog(
        onDismissRequest = onFechar,
        title = { Text(tr("ID do cliente do Google")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    tr("Para entrar no seu Drive, o app usa um cliente OAuth do seu próprio Google Cloud (grátis). ") +
                        tr("Os nomes dos menus do Google mudam de vez em quando."),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    tr("1. Crie um projeto no Google Cloud e ative a Google Drive API.") + "\n" +
                        tr("2. Em Google Auth Platform, configure a tela de consentimento (Externo), adicione o escopo %s e publique como “Em produção”.", "https://www.googleapis.com/auth/drive") + "\n" +
                        tr("3. Crie um cliente OAuth do tipo Android com o pacote e o SHA-1 abaixo, e ligue “Ativar esquema de URI personalizado” nas configurações avançadas.") + "\n" +
                        tr("4. Cole aqui o ID do cliente."),
                    style = MaterialTheme.typography.bodySmall,
                )
                LinhaCopiavel(context, tr("Pacote"), OAuthConfig.PACOTE)
                LinhaCopiavel(context, tr("SHA-1 deste app"), sha1 ?: tr("não foi possível ler"))
                OutlinedTextField(
                    value = id,
                    onValueChange = { id = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(tr("ID do cliente")) },
                    placeholder = { Text("123456-abc.apps.googleusercontent.com") },
                    supportingText = {
                        if (id.isNotBlank() && !valido) Text(tr("O ID termina em .apps.googleusercontent.com"))
                    },
                    isError = id.isNotBlank() && !valido,
                )
                TextButton(onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://console.cloud.google.com/apis/credentials"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }) { Text(tr("Abrir o Google Cloud")) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valido,
                onClick = {
                    OAuthConfig.definir(context, id)
                    onSalvo()
                },
            ) { Text(tr("Salvar")) }
        },
        dismissButton = { TextButton(onClick = onFechar) { Text(tr("Cancelar")) } },
    )
}

@Composable
private fun LinhaCopiavel(context: Context, rotulo: String, valor: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(rotulo, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(valor, style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = {
            val gestor = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            gestor?.setPrimaryClip(ClipData.newPlainText(rotulo, valor))
        }) { Text(tr("Copiar")) }
    }
}
