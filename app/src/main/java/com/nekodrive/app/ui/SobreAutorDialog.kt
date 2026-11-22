package com.nekodrive.app.ui

import com.nekodrive.app.data.tr
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nekodrive.app.R

/** Foto, nome, links de contato e, se houver, a carteira (com QR code) para apoiar o projeto. */
@Composable
fun SobreAutorDialog(onFechar: () -> Unit) {
    val context = LocalContext.current
    var copiado by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onFechar,
        title = { Text(tr("Sobre o autor")) },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.autor_foto),
                    contentDescription = Autor.NOME,
                    modifier = Modifier.size(112.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
                Text(Autor.NOME, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                Text(
                    tr("Criador do NekoDrive, sob licença Apache 2.0."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                TextButton(onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(Autor.GITHUB)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }) { Text(tr("Abrir o GitHub")) }
                TextButton(onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + Autor.EMAIL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }) { Text(Autor.EMAIL) }

                if (Autor.CARTEIRA.isNotBlank()) {
                    Text(
                        tr("Gostou do app? Você pode apoiar o projeto (%s):", Autor.REDE),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                    // sem suavização: o QR code precisa de bordas nítidas para ser lido
                    Image(
                        bitmap = ImageBitmap.imageResource(R.drawable.qr_carteira),
                        contentDescription = tr("QR code da carteira %s", Autor.REDE),
                        modifier = Modifier.size(208.dp),
                        filterQuality = FilterQuality.None,
                    )
                    Text(
                        Autor.CARTEIRA,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                    )
                    TextButton(onClick = {
                        val gestor = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        gestor?.setPrimaryClip(ClipData.newPlainText(Autor.REDE, Autor.CARTEIRA))
                        copiado = true
                    }) { Text(if (copiado) tr("Endereço copiado") else tr("Copiar endereço")) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onFechar) { Text(tr("Fechar")) } },
    )
}
