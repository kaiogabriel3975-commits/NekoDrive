package com.nekodrive.app.data

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.nekodrive.app.NekoDriveApplication
import com.nekodrive.app.data.drive.DriveFile
import kotlinx.coroutines.runBlocking
import java.io.FileNotFoundException

/**
 * Entrega fotos e vídeos do Drive para outros apps durante o arrastar e soltar. O arquivo não precisa estar
 * baixado: se não estiver no cache, o conteúdo é buscado no Drive e repassado aos poucos.
 */
class ProvedorMidia : ContentProvider() {
    override fun onCreate() = true

    override fun getType(uri: Uri): String = uri.getQueryParameter("t")?.ifEmpty { null } ?: "application/octet-stream"

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val colunas = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val cursor = MatrixCursor(colunas)
        val nome = uri.getQueryParameter("n") ?: "arquivo"
        val tamanho = uri.getQueryParameter("s")?.toLongOrNull()
        cursor.addRow(
            colunas.map {
                when (it) {
                    OpenableColumns.DISPLAY_NAME -> nome
                    OpenableColumns.SIZE -> tamanho
                    else -> null
                }
            }.toTypedArray(),
        )
        return cursor
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val id = uri.pathSegments.getOrNull(1) ?: throw FileNotFoundException("Arquivo desconhecido")
        val c = (context!!.applicationContext as NekoDriveApplication).container
        val email = c.accounts.currentEmail ?: throw FileNotFoundException("Nenhuma conta selecionada")
        val tamanho = uri.getQueryParameter("s")?.toLongOrNull()
        val local = if (tamanho != null) c.midia.arquivoLocal(email, DriveFile(id, "", "", tamanho, null, null)) else null
        if (local != null) return ParcelFileDescriptor.open(local, ParcelFileDescriptor.MODE_READ_ONLY)

        val (leitura, escrita) = ParcelFileDescriptor.createPipe()
        Thread {
            try {
                ParcelFileDescriptor.AutoCloseOutputStream(escrita).use { saida ->
                    runBlocking { c.drive.baixarParaStream(email, id, saida) { } }
                }
            } catch (e: Exception) {
                runCatching { escrita.closeWithError(e.message ?: "Falha ao baixar") }
            }
        }.start()
        return leitura
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    companion object {
        /** Endereço do arquivo no provedor; o último trecho é o nome, para apps que usam o final do caminho. */
        fun uriDe(ctx: Context, f: DriveFile): Uri =
            Uri.Builder().scheme("content").authority(ctx.packageName + ".midia")
                .appendPath("m").appendPath(f.id).appendPath(f.name)
                .appendQueryParameter("n", f.name)
                .appendQueryParameter("t", f.mimeType)
                .apply { f.size?.let { appendQueryParameter("s", it.toString()) } }
                .build()
    }
}
