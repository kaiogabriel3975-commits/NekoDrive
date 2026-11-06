package com.nekodrive.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nekodrive.app.ui.drive.DriveViewModel
import com.nekodrive.app.ui.drive.formatarTamanho
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfiguracoesSheet(vm: DriveViewModel, onContas: () -> Unit, onFechar: () -> Unit) {
    var limite by remember { mutableFloatStateOf(vm.limiteGb.toFloat()) }
    val usado = remember(vm.versaoCache) { vm.tamanhoCache() }

    ModalBottomSheet(onDismissRequest = onFechar) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Configurações", style = MaterialTheme.typography.titleLarge)

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Tela inicial", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (vm.grade) "Grade de miniaturas" else "Lista com detalhes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = vm.grade, onCheckedChange = { vm.alternarModo() })
            }

            TextButton(onClick = { onFechar(); onContas() }) { Text("Gerenciar contas") }

            Text("Cache de vídeos e fotos", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            Text(
                "Em uso: ${formatarTamanho(usado) ?: "0 B"} de ${limite.roundToInt()} GB. " +
                    "Ao passar do limite, o que foi usado há mais tempo é apagado primeiro.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = limite,
                onValueChange = { limite = it },
                onValueChangeFinished = { vm.definirLimiteGb(limite.roundToInt()) },
                valueRange = 1f..50f,
                steps = 48,
            )
            Button(onClick = { vm.limparCache() }) { Text("Limpar cache") }
        }
    }
}
