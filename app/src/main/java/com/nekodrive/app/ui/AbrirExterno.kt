package com.nekodrive.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import com.nekodrive.app.data.drive.DriveFile
import com.nekodrive.app.ui.drive.DriveViewModel

/** Baixa o arquivo para o cache e pede ao Android um app instalado que consiga abri-lo. */
suspend fun abrirComOutroApp(ctx: Context, vm: DriveViewModel, f: DriveFile) {
    try {
        Toast.makeText(ctx, "Preparando o arquivo…", Toast.LENGTH_SHORT).show()
        val arquivo = vm.copiarParaCache(f, "abrir")
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", arquivo)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, f.mimeType.ifEmpty { "*/*" })
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(intent, "Abrir com"))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(ctx, "Nenhum app instalado abre este tipo de arquivo.", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(ctx, "Não foi possível abrir: ${e.message}", Toast.LENGTH_LONG).show()
    }
}
