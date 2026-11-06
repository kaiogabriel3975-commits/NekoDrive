package com.nekodrive.app.data

import android.content.Context
import java.io.File
import com.nekodrive.app.data.auth.AccountStore
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
}
