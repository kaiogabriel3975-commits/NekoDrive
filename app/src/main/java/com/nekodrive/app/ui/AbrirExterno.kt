package com.nekodrive.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
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

/** Compartilha o arquivo (baixado para o cache) pelo menu de compartilhamento do Android. */
suspend fun compartilharArquivo(ctx: Context, vm: DriveViewModel, f: DriveFile) {
    try {
        Toast.makeText(ctx, "Preparando para compartilhar…", Toast.LENGTH_SHORT).show()
        val arquivo = vm.copiarParaCache(f, "abrir")
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", arquivo)
        val intent = Intent(Intent.ACTION_SEND)
            .setType(f.mimeType.ifEmpty { "*/*" })
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(intent, "Compartilhar"))
    } catch (e: Exception) {
        Toast.makeText(ctx, "Não foi possível compartilhar: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

/**
 * Abre o vídeo em outro player instalado, sem baixar: o app serve o arquivo do Drive em um endereço local
 * (127.0.0.1) que o outro player consegue ler com avanço e retorno.
 */
fun abrirPlayerExterno(ctx: Context, vm: DriveViewModel, f: DriveFile) {
    try {
        val servidor = vm.midia.servidor
        servidor.iniciarSeNecessario()
        val chave = servidor.registrar(vm.midia.urlOriginal(f.id), f.mimeType.ifEmpty { "video/*" })
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(Uri.parse("http://127.0.0.1:${servidor.porta}/v/$chave"), f.mimeType.ifEmpty { "video/*" })
        ctx.startActivity(Intent.createChooser(intent, "Abrir com o player"))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(ctx, "Nenhum player instalado consegue abrir este vídeo.", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(ctx, "Não foi possível abrir o player: ${e.message}", Toast.LENGTH_LONG).show()
    }
}
