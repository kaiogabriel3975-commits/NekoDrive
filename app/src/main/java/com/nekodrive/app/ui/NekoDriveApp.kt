package com.nekodrive.app.ui

import android.accounts.AccountManager
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.nekodrive.app.ui.drive.FotosAbertas
import com.nekodrive.app.ui.viewer.DetalhesSheet
import com.nekodrive.app.ui.viewer.Visualizador
import kotlinx.coroutines.launch

enum class Destino(val rotulo: String, val icone: ImageVector, val iconeSelecionado: ImageVector) {
    Drive("Drive", Icons.Outlined.Folder, Icons.Filled.Folder),
    Favoritos("Favoritos", Icons.Outlined.FavoriteBorder, Icons.Filled.Favorite),
    Lixeira("Lixeira", Icons.Outlined.Delete, Icons.Filled.Delete),
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
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

    // 1) tela de consentimento do Google (quando necessária)
    val consentimento = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        try {
            vm.aoAutorizar(container.auth.fromIntent(res.data).accessToken)
        } catch (e: Exception) {
            vm.mostrarErro("Login cancelado ou não concluído.")
        }
    }
    // 2) autoriza o Drive para a conta escolhida
    val autorizar: (String) -> Unit = { email ->
        scope.launch {
            try {
                val r = container.auth.authorize(email)
                val pendente = r.pendingIntent
                if (r.hasResolution() && pendente != null) {
                    consentimento.launch(IntentSenderRequest.Builder(pendente.intentSender).build())
                } else {
                    vm.aoAutorizar(r.accessToken)
                }
            } catch (e: Exception) {
                vm.mostrarErro("Falha ao entrar: ${e.message}")
            }
        }
    }
    // 3) seletor de contas do Android (permite escolher outra conta ou adicionar uma nova)
    val escolherConta = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val email = res.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
        if (res.resultCode == Activity.RESULT_OK && email != null) autorizar(email)
    }
    val entrar: () -> Unit = {
        val intent = AccountManager.newChooseAccountIntent(null, null, arrayOf("com.google"), null, null, null, null)
        escolherConta.launch(intent)
    }

    val ultimo = remember { mutableStateOf<FotosAbertas?>(null) }
    if (vm.visualizador != null) SideEffect { ultimo.value = vm.visualizador }

    SharedTransitionLayout {
        val st = this
        AnimatedContent(
            targetState = vm.visualizador != null,
            transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(240)) },
            label = "visualizador",
        ) { aberto ->
            val av = this
            // Guarda a última lista aberta para a tela de saída não ficar vazia durante a animação.
            val dados = vm.visualizador ?: ultimo.value
            if (aberto && dados != null) {
                Visualizador(vm, dados, st, av)
            } else {
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
            DriveScreen(vm, onEntrar = entrar, st = st, avExterno = av, modifier = Modifier.padding(padding))
        } else {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(atual.rotulo, style = MaterialTheme.typography.titleLarge)
            }
        }
    }

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
    if (vm.detalhes != null) DetalhesSheet(vm)
}
