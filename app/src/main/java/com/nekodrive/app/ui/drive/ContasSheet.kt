package com.nekodrive.app.ui.drive

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nekodrive.app.data.auth.GoogleAccount

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContasSheet(
    contas: List<GoogleAccount>,
    atual: GoogleAccount?,
    onSelecionar: (GoogleAccount) -> Unit,
    onRemover: (GoogleAccount) -> Unit,
    onAdicionar: () -> Unit,
    onFechar: () -> Unit,
) {
    val transparente = ListItemDefaults.colors(containerColor = Color.Transparent)
    ModalBottomSheet(onDismissRequest = onFechar) {
        Column(Modifier.padding(bottom = 24.dp)) {
            contas.forEach { conta ->
                ListItem(
                    headlineContent = { Text(conta.name ?: conta.email) },
                    supportingContent = { Text(if (conta.email == atual?.email) "${conta.email} (em uso)" else conta.email) },
                    leadingContent = {
                        if (conta.photoUrl != null) {
                            AsyncImage(conta.photoUrl, null, Modifier.size(40.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                        } else {
                            Icon(Icons.Rounded.AccountCircle, null, Modifier.size(40.dp))
                        }
                    },
                    trailingContent = {
                        IconButton(onClick = { onRemover(conta) }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Remover conta")
                        }
                    },
                    colors = transparente,
                    modifier = Modifier.clickable { onSelecionar(conta); onFechar() },
                )
            }
            ListItem(
                headlineContent = { Text("Adicionar conta") },
                leadingContent = { Icon(Icons.Rounded.PersonAdd, null) },
                colors = transparente,
                modifier = Modifier.clickable { onAdicionar(); onFechar() },
            )
        }
    }
}
