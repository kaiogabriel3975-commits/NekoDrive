package com.nekodrive.app.ui

import com.nekodrive.app.data.tr
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nekodrive.app.ui.drive.DriveViewModel
import com.nekodrive.app.ui.drive.formatarTamanho
import kotlin.math.roundToInt
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.nekodrive.app.diagnostico.AppLogger

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
private fun <T> LinhaOpcoes(
    titulo: String,
    opcoes: List<Pair<String, T>>,
    atual: T,
    descricao: String? = null,
    aoEscolher: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(titulo, style = MaterialTheme.typography.bodyLarge)
        if (descricao != null) {
            Text(descricao, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            opcoes.forEach { (rotulo, valor) ->
                FilterChip(selected = valor == atual, onClick = { aoEscolher(valor) }, label = { Text(rotulo) })
            }
        }
    }
}

/** Configurações como tela própria (destino da toolbar). [padding] deixa o conteúdo livre do cabeçalho e da toolbar. */
@Composable
fun ConfiguracoesTela(vm: DriveViewModel, padding: PaddingValues, onContas: () -> Unit) {
    var limite by remember { mutableFloatStateOf(vm.limiteGb.toFloat()) }
    var estadoExportacao by remember { mutableStateOf<String?>(null) }
    var confirmarLimpeza by remember { mutableStateOf(false) }
    var configGoogle by remember { mutableStateOf(false) }
    var sobreAutor by remember { mutableStateOf(false) }
    val contexto = LocalContext.current
    val escopo = rememberCoroutineScope()
    val usado = remember(vm.versaoCache) { vm.tamanhoCache() }

    if (confirmarLimpeza) {
        AlertDialog(
            onDismissRequest = { confirmarLimpeza = false },
            title = { Text(tr("Limpar o cache?")) },
            text = { Text(tr("Apaga as fotos e os vídeos guardados neste aparelho, inclusive os que você baixou para ver sem internet.")) },
            confirmButton = { TextButton(onClick = { confirmarLimpeza = false; vm.limparCache() }) { Text(tr("Limpar")) } },
            dismissButton = { TextButton(onClick = { confirmarLimpeza = false }) { Text(tr("Cancelar")) } },
        )
    }

    if (configGoogle) ConfigurarGoogleDialog(onFechar = { configGoogle = false }, onSalvo = { configGoogle = false })
    if (sobreAutor) SobreAutorDialog(onFechar = { sobreAutor = false })

    run {
        Column(
            // fonteVidro: o conteúdo que rola por baixo da toolbar e do cabeçalho é o que o blur desfoca
            Modifier.fillMaxSize().fonteVidro().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = padding.calculateTopPadding() + 12.dp, bottom = padding.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(tr("Configurações"), style = MaterialTheme.typography.titleLarge)

            Secao(tr("Aparência"))
            LinhaOpcoes(tr("Tema"), listOf(tr("Sistema") to 0, tr("Claro") to 1, tr("Escuro") to 2), vm.tema) { vm.definirTema(it) }
            // o nome da opção e os nomes dos idiomas ficam nos dois idiomas, para quem escolheu o errado conseguir voltar
            LinhaOpcoes("Idioma / Language", listOf(tr("Automático") to 0, "Português" to 1, "English" to 2), vm.idioma) { vm.definirIdioma(it) }
            LinhaSwitch(tr("Tela inicial em grade"), if (vm.grade) tr("Grade de miniaturas") else tr("Lista com detalhes"), vm.grade) { vm.alternarModo() }
            LinhaOpcoes(tr("Tamanho das miniaturas"), listOf(tr("Médias") to 1, tr("Grandes") to 2), vm.tamanhoMiniatura) {
                vm.definirTamanhoMiniatura(it)
            }
            LinhaOpcoes(
                tr("Colunas da grade"),
                listOf(tr("Automático") to 0, "2" to 2, "3" to 3, "4" to 4),
                vm.colunas,
                descricao = tr("Automático usa o tamanho das miniaturas; um número fixo ignora esse tamanho na grade"),
            ) { vm.definirColunas(it) }
            LinhaSwitch(tr("Nomes na grade"), tr("Mostra o nome de fotos e vídeos. Pastas e outros arquivos sempre mostram"), vm.mostrarNomes) {
                vm.definirMostrarNomes(it)
            }
            LinhaSwitch(tr("Pastas sempre primeiro"), tr("Desligado, pastas e arquivos se misturam na ordenação"), vm.pastasPrimeiro) {
                vm.definirPastasPrimeiro(it)
            }
            LinhaSwitch(tr("Animar GIFs nas miniaturas"), tr("Desligado, mostra só o primeiro quadro e economiza dados e bateria"), vm.animarGifs) {
                vm.definirAnimarGifs(it)
            }
            LinhaSwitch(tr("Efeito de vidro (blur)"), tr("Desligue se o app ficar pesado em algum aparelho"), vm.vidroAtivo) { vm.definirVidro(it) }
            if (vm.vidroAtivo) {
                LinhaOpcoes(tr("Intensidade do blur"), listOf(tr("Baixa") to 0, tr("Média") to 1, tr("Alta") to 2), vm.blurIntensidade) {
                    vm.definirBlurIntensidade(it)
                }
            }
            LinhaSwitch(tr("Reflexos de luz"), tr("O vidro é iluminado pela mídia que está por perto, na cor dela"), vm.reflexoPilula) {
                vm.definirReflexoPilula(it)
            }
            if (vm.reflexoPilula) {
                LinhaSwitch(
                    tr("Luz só nas toolbars"),
                    tr("Ligado: só na pílula da galeria, na do player de vídeo, na toolbar principal e na busca. ") +
                        tr("Desligado: em todas as peças de vidro com reflexo"),
                    vm.reflexoSoToolbars,
                ) { vm.definirReflexoSoToolbars(it) }
            }
            LinhaOpcoes(
                tr("Fundo dos cards de mídia"),
                listOf(tr("Cor da mídia") to 0, tr("Sem cores") to 1, tr("Miniatura desfocada") to 2),
                vm.fundoCard,
                descricao = when (vm.fundoCard) {
                    1 -> tr("Cards neutros, sem a cor da mídia")
                    2 -> tr("A miniatura invertida na vertical, com blur forte e escurecida")
                    else -> tr("O card pega a cor que mais aparece na mídia")
                },
            ) { vm.definirFundoCard(it) }

            Secao(tr("Animações e toque"))
            LinhaOpcoes(
                tr("Animações"),
                listOf(tr("Normais") to 0, tr("Baratas") to 1, tr("Sem animações") to 2),
                vm.animacoes,
                descricao = when (vm.animacoes) {
                    1 -> tr("Transições curtas, sem blur de dissolução, sem inclinação e sem molas: pesam bem menos na GPU")
                    2 -> tr("Tudo acontece na hora, sem transições (os nomes longos também param de rolar)")
                    else -> tr("Todas as animações, como foram desenhadas")
                },
            ) { vm.definirAnimacoes(it) }
            LinhaSwitch(tr("Vibração ao tocar"), tr("Ao segurar um item e ao arrastar uma foto"), vm.vibrar) { vm.definirVibrar(it) }
            LinhaOpcoes(
                tr("Pausa ao fechar uma mídia"),
                listOf(tr("Nenhuma") to 0, tr("Curta") to 100, tr("Longa") to 200),
                vm.esperaFecharMs,
            ) { vm.definirEsperaFechar(it) }

            Secao(tr("Rede"))
            LinhaSwitch(
                tr("Miniaturas só no Wi-Fi"),
                tr("Nos dados móveis aparecem só as miniaturas já guardadas no aparelho"),
                vm.miniaturasSoWifi,
            ) { vm.definirMiniaturasSoWifi(it) }

            Secao(tr("Player de vídeo"))
            LinhaSwitch(tr("Repetir ao terminar"), tr("O vídeo recomeça sozinho do início"), vm.repetirVideo) { vm.definirRepetirVideo(it) }
            LinhaSwitch(tr("Continuar de onde parou"), tr("Retoma o vídeo na posição em que você saiu"), vm.retomarVideo) { vm.definirRetomarVideo(it) }
            LinhaOpcoes(tr("Toque duplo nas laterais pula"), listOf("5 s" to 5, "10 s" to 10, "15 s" to 15, "30 s" to 30), vm.saltoSeg) {
                vm.definirSaltoSeg(it)
            }
            LinhaOpcoes(tr("Velocidade ao segurar o dedo"), listOf("1,5x" to 1.5f, "2x" to 2f, "3x" to 3f), vm.velocidadeTurbo) {
                vm.definirTurbo(it)
            }
            LinhaOpcoes(
                tr("Resolução máxima no Wi-Fi"),
                listOf(tr("Máxima") to 4320, "1080p" to 1080, "720p" to 720, "480p" to 480),
                vm.resWifi,
            ) { vm.definirResWifi(it) }
            LinhaOpcoes(
                tr("Resolução máxima nos dados móveis"),
                listOf("720p" to 720, "540p" to 540, "360p" to 360, "240p" to 240),
                vm.resMovel,
            ) { vm.definirResMovel(it) }
            LinhaOpcoes(
                tr("Esconder os controles após"),
                listOf("2 s" to 2000, "3,5 s" to 3500, "5 s" to 5000, tr("Nunca") to 0),
                vm.controlesMs,
            ) { vm.definirControlesMs(it) }

            Secao(tr("Conta"))
            TextButton(onClick = onContas) { Text(tr("Gerenciar contas")) }
            TextButton(onClick = { configGoogle = true }) { Text(tr("ID do cliente do Google")) }

            Secao(tr("Cache de vídeos e fotos"))
            Text(
                tr("Em uso: %s de %s GB. ", formatarTamanho(usado) ?: "0 B", limite.roundToInt()) +
                    tr("Ao passar do limite, o que foi usado há mais tempo é apagado primeiro."),
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
            Button(onClick = { confirmarLimpeza = true }, enabled = !vm.limpandoCache) {
                Text(if (vm.limpandoCache) "Limpando…" else tr("Limpar cache"))
            }

            Secao(tr("Diagnóstico"))
            Text(
                tr("Exporta os eventos registrados pelo NekoDrive e detalhes de falhas do aplicativo para a pasta Downloads."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = {
                    estadoExportacao = tr("Exportando logs…")
                    escopo.launch {
                        estadoExportacao = withContext(Dispatchers.IO) {
                            runCatching { AppLogger.exportToDownloads(contexto) }
                                .fold(
                                    onSuccess = { tr("Logs salvos em Downloads/%s", it) },
                                    onFailure = { tr("Falha ao exportar: %s", it.localizedMessage ?: tr("erro desconhecido")) },
                                )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(tr("Exportar logs de funcionamento")) }
            estadoExportacao?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Secao(tr("Sobre"))
            TextButton(onClick = { sobreAutor = true }) { Text(tr("Sobre o autor")) }
        }
    }
}
