package com.nekodrive.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nekodrive.app.NekoDriveApplication
import com.nekodrive.app.ui.drive.ContasSheet
import com.nekodrive.app.ui.drive.DriveScreen
import com.nekodrive.app.ui.drive.DriveViewModel
import kotlinx.coroutines.launch

enum class Destino(val rotulo: String, val icone: ImageVector, val iconeSelecionado: ImageVector) {
    Drive("Drive", Icons.Outlined.Folder, Icons.Filled.Folder),
    Favoritos("Favoritos", Icons.Outlined.FavoriteBorder, Icons.Filled.Favorite),
    Lixeira("Lixeira", Icons.Outlined.Delete, Icons.Filled.Delete),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NekoDriveApp() {
    val context = LocalContext.current
    val container = (context.applicationContext as NekoDriveApplication).container
    val vm: DriveViewModel = viewModel(factory = viewModelFactory { initializer { DriveViewModel(container) } })
    val scope = rememberCoroutineScope()

    var atual by rememberSaveable { mutableStateOf(Destino.Drive) }
    var mostrarContas by rememberSaveable { mutableStateOf(false) }
    // Etapa 1-2: barras translúcidas. O blur real (Haze) entra na etapa de polimento.
    val vidro = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)

    // Retorno da tela de consentimento do Google
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        try {
            vm.aoAutorizar(container.auth.fromIntent(res.data))
        } catch (e: Exception) {
            vm.mostrarErro("Login cancelado ou não concluído.")
        }
    }
    val entrar: () -> Unit = {
        scope.launch {
            try {
                val r = container.auth.authorize(null)
                val pendente = r.pendingIntent
                if (r.hasResolution() && pendente != null) {
                    launcher.launch(IntentSenderRequest.Builder(pendente.intentSender).build())
                } else {
                    vm.aoAutorizar(r)
                }
            } catch (e: Exception) {
                vm.mostrarErro("Falha ao entrar: ${e.message}")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("nekodrive", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(8.dp))
                        PawIcon()
                    }
                },
                actions = {
                    IconButton(onClick = { mostrarContas = true }) {
                        Icon(Icons.Outlined.AccountCircle, contentDescription = "Contas")
                    }
                    IconButton(onClick = { /* etapa 8: configurações */ }) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Configurações")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = vidro),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = vidro) {
                Destino.entries.forEach { d ->
                    NavigationBarItem(
                        selected = atual == d,
                        onClick = { atual = d },
                        icon = { Icon(if (atual == d) d.iconeSelecionado else d.icone, contentDescription = d.rotulo) },
                        label = { Text(d.rotulo) },
                    )
                }
            }
        },
    ) { padding ->
        if (atual == Destino.Drive) {
            DriveScreen(vm, onEntrar = entrar, modifier = Modifier.padding(padding))
        } else {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(atual.rotulo, style = MaterialTheme.typography.titleLarge)
            }
        }
    }

    if (mostrarContas) {
        ContasSheet(
            contas = vm.contas,
            atual = vm.contaAtual,
            onSelecionar = vm::selecionar,
            onRemover = vm::remover,
            onAdicionar = entrar,
            onFechar = { mostrarContas = false },
        )
    }
}
