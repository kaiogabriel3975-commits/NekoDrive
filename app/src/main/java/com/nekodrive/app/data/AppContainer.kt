package com.nekodrive.app.data

import android.content.ContentResolver
import android.content.Context
import java.io.File
import com.nekodrive.app.data.auth.AccountStore
import com.nekodrive.app.data.cache.MidiaCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Semaphore
import com.nekodrive.app.data.auth.AuthManager
import com.nekodrive.app.data.drive.DriveRepository

/** Injeção manual de dependências (simples; dá para migrar para Hilt depois). */
class AppContainer(context: Context) {
    val accounts = AccountStore(context)
    val prefs = ViewPrefs(context)

    // idioma escolhido, antes de qualquer tela ou notificação usar textos
    init {
        I18n.definir(prefs.idioma)
    }
    val playback = PlaybackStore(context)
    val favoritos = FavoritosStore(context)
    val capas = CapasStore(context)
    val cacheDir: File = context.cacheDir
    val resolver: ContentResolver = context.contentResolver
    val auth = AuthManager(context)
    val drive = DriveRepository(auth)

    /** Downloads continuam enquanto o processo do app estiver vivo. */
    val escopo = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val semaforo = Semaphore(2)

    /** Guarda no Drive (pasta "etc") o que só o app enxerga: ajustes, favoritos e capas. */
    val sync = ConfigSync(context, drive, accounts, favoritos, capas, prefs, escopo)
    val midia by lazy { MidiaCache(context, auth, prefs.limiteGb) }
}
