package com.nekodrive.app.ui.drive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp

/** Confirmação antes de baixar tudo que está dentro de uma ou mais pastas. */
@Composable
fun DialogDownloadPasta(vm: DriveViewModel, plano: PlanoDownload) {
    val nomes = plano.nomes.joinToString(", ") { "“$it”" }
    AlertDialog(
        onDismissRequest = { vm.cancelarDownloadDePastas() },
        title = { Text("Baixar pasta?") },
        text = {
            if (plano.calculando) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    Text("Contando os arquivos de $nomes… (${plano.contados})")
                }
            } else if (plano.arquivos.isEmpty()) {
                Text("Não há arquivos para baixar em $nomes.")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "$nomes tem ${plano.arquivos.size} arquivo(s)" +
                            (formatarTamanho(plano.bytes)?.let { " ($it)" } ?: "") + " contando as subpastas.",
                    )
                    Text("Fotos e vídeos ficam no cache do app; os outros arquivos vão para Downloads, em uma pasta com o mesmo nome.")
                    if (plano.ignorados > 0) {
                        Text("${plano.ignorados} arquivo(s) do Google Docs, Planilhas ou Apresentações não podem ser baixados e serão ignorados.")
                    }
                    if (plano.bytesMidia > vm.limiteGb * 1024L * 1024L * 1024L) {
                        Text("As fotos e vídeos passam do limite do cache (${vm.limiteGb} GB): os usados há mais tempo serão apagados do cache.")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { vm.confirmarDownloadDePastas() },
                enabled = !plano.calculando && plano.arquivos.isNotEmpty(),
            ) { Text("Baixar") }
        },
        dismissButton = { TextButton(onClick = { vm.cancelarDownloadDePastas() }) { Text("Cancelar") } },
    )
}
