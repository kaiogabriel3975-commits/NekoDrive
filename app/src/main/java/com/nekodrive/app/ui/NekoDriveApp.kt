package com.nekodrive.app.ui

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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class Destino(val rotulo: String, val icone: ImageVector, val iconeSelecionado: ImageVector) {
    Drive("Drive", Icons.Outlined.Folder, Icons.Filled.Folder),
    Favoritos("Favoritos", Icons.Outlined.FavoriteBorder, Icons.Filled.Favorite),
    Lixeira("Lixeira", Icons.Outlined.Delete, Icons.Filled.Delete),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NekoDriveApp() {
    var atual by rememberSaveable { mutableStateOf(Destino.Drive) }
    // Etapa 1: barras translúcidas. O blur real (Haze) entra na etapa de polimento.
    val vidro = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)

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
                    IconButton(onClick = { /* etapa 2: trocar de conta */ }) {
                        Icon(Icons.Outlined.AccountCircle, contentDescription = "Conta")
                    }
                    IconButton(onClick = { /* configurações */ }) {
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
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Text(atual.rotulo, style = MaterialTheme.typography.titleLarge)
        }
    }
}
