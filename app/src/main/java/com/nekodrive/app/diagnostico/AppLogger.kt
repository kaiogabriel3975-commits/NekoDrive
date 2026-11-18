package com.nekodrive.app.diagnostico

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Registro local de eventos e falhas do próprio NekoDrive. Não lê o Logcat do sistema. */
object AppLogger {
    private const val FILE_NAME = "nekodrive-runtime.log"
    private const val MAX_BYTES = 2 * 1024 * 1024L
    private val lock = Any()
    @Volatile private var installed = false

    fun install(context: Context) {
        if (installed) return
        synchronized(lock) {
            if (installed) return
            installed = true
            val app = context.applicationContext
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, error ->
                write(app, "FATAL", "Exceção não capturada na thread ${thread.name}: ${error.stackTraceToString()}")
                previous?.uncaughtException(thread, error)
            }
            write(app, "INFO", "Aplicativo iniciado; Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        }
    }

    fun log(context: Context, message: String, level: String = "INFO") {
        write(context.applicationContext, level, message)
    }

    private fun write(context: Context, level: String, message: String) {
        synchronized(lock) {
            runCatching {
                val file = File(context.filesDir, FILE_NAME)
                if (file.exists() && file.length() > MAX_BYTES) {
                    val old = File(context.filesDir, "$FILE_NAME.1")
                    old.delete()
                    file.renameTo(old)
                }
                val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
                file.appendText("[$stamp] [$level] $message\n", Charsets.UTF_8)
            }
        }
    }

    /** Salva uma cópia em Downloads usando MediaStore, sem pedir permissão de armazenamento. */
    fun exportToDownloads(context: Context): String {
        log(context, "Exportação de logs solicitada")
        val resolver = context.contentResolver
        val source = File(context.filesDir, FILE_NAME)
        val old = File(context.filesDir, "$FILE_NAME.1")
        val content = buildString {
            appendLine("NekoDrive - logs de funcionamento")
            appendLine("Exportado em: ${Date()}")
            appendLine("Dispositivo: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Versão do app: ${runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "desconhecida"}")
            appendLine("----------------------------------------")
            if (old.exists()) appendLine(old.readText(Charsets.UTF_8))
            if (source.exists()) appendLine(source.readText(Charsets.UTF_8))
            if (!old.exists() && !source.exists()) appendLine("Nenhum evento registrado ainda.")
        }
        val name = "NekoDrive-logs-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())}.txt"
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.RELATIVE_PATH, "Download")
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Não foi possível criar o arquivo em Downloads")
        try {
            resolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(content) }
                ?: error("Não foi possível gravar o arquivo de logs")
        } catch (error: Exception) {
            resolver.delete(uri, null, null)
            throw error
        }
        return name
    }
}
