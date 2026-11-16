package com.nekodrive.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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

@Composable
private fun Secao(titulo: String) {
    Text(titulo, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 16.dp))
}

@Composable
private fun LinhaSwitch(titulo: String, descricao: String?, marcado: Boolean, aoMudar: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(titulo, style = MaterialTheme.typography.bodyLarge)
            if (descricao != null) {
                Text(descricao, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = marcado, onCheckedChange = aoMudar)
    }
}

/** Título e uma fileira de opções (só uma fica marcada). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> LinhaOpcoes(titulo: String, opcoes: List<Pair<String, T>>, atual: T, aoEscolher: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(titulo, style = MaterialTheme.typography.bodyLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            opcoes.forEach { (rotulo, valor) ->
                FilterChip(selected = valor == atual, onClick = { aoEscolher(valor) }, label = { Text(rotulo) })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfiguracoesSheet(vm: DriveViewModel, onContas: () -> Unit, onFechar: () -> Unit) {
    var limite by remember { mutableFloatStateOf(vm.limiteGb.toFloat()) }
    val usado = remember(vm.versaoCache) { vm.tamanhoCache() }

    ModalBottomSheet(onDismissRequest = onFechar) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Configurações", style = MaterialTheme.typography.titleLarge)

            Secao("Aparência")
            LinhaOpcoes("Tema", listOf("Sistema" to 0, "Claro" to 1, "Escuro" to 2), vm.tema) { vm.definirTema(it) }
            LinhaSwitch("Tela inicial em grade", if (vm.grade) "Grade de miniaturas" else "Lista com detalhes", vm.grade) { vm.alternarModo() }
            LinhaOpcoes("Tamanho das miniaturas", listOf("Pequenas" to 0, "Médias" to 1, "Grandes" to 2), vm.tamanhoMiniatura) {
                vm.definirTamanhoMiniatura(it)
            }
            LinhaSwitch("Efeito de vidro (blur)", "Desligue se o app ficar pesado em algum aparelho", vm.vidroAtivo) { vm.definirVidro(it) }
            LinhaOpcoes(
                "Pausa ao fechar uma mídia",
                listOf("Nenhuma" to 0, "Curta" to 100, "Longa" to 200),
                vm.esperaFecharMs,
            ) { vm.definirEsperaFechar(it) }

            Secao("Player de vídeo")
            LinhaSwitch("Repetir ao terminar", "O vídeo recomeça sozinho do início", vm.repetirVideo) { vm.definirRepetirVideo(it) }
            LinhaSwitch("Continuar de onde parou", "Retoma o vídeo na posição em que você saiu", vm.retomarVideo) { vm.definirRetomarVideo(it) }
            LinhaOpcoes("Toque duplo nas laterais pula", listOf("5 s" to 5, "10 s" to 10, "15 s" to 15, "30 s" to 30), vm.saltoSeg) {
                vm.definirSaltoSeg(it)
            }
            LinhaOpcoes("Velocidade ao segurar o dedo", listOf("1,5x" to 1.5f, "2x" to 2f, "3x" to 3f), vm.velocidadeTurbo) {
                vm.definirTurbo(it)
            }
            LinhaOpcoes(
                "Resolução máxima no Wi-Fi",
                listOf("Máxima" to 4320, "1080p" to 1080, "720p" to 720, "480p" to 480),
                vm.resWifi,
            ) { vm.definirResWifi(it) }
            LinhaOpcoes(
                "Resolução máxima nos dados móveis",
                listOf("720p" to 720, "540p" to 540, "360p" to 360, "240p" to 240),
                vm.resMovel,
            ) { vm.definirResMovel(it) }
            LinhaOpcoes(
                "Esconder os controles após",
                listOf("2 s" to 2000, "3,5 s" to 3500, "5 s" to 5000, "Nunca" to 0),
                vm.controlesMs,
            ) { vm.definirControlesMs(it) }

            Secao("Conta")
            TextButton(onClick = { onFechar(); onContas() }) { Text("Gerenciar contas") }

            Secao("Cache de vídeos e fotos")
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
