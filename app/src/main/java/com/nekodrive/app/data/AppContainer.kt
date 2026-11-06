package com.nekodrive.app.data

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
    val playback = PlaybackStore(context)
    val cacheDir: File = context.cacheDir
    val auth = AuthManager(context)
    val drive = DriveRepository(auth)

    /** Downloads continuam enquanto o processo do app estiver vivo. */
    val escopo = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val semaforo = Semaphore(2)
    val midia by lazy { MidiaCache(context, auth, prefs.limiteGb) }
}
