package com.nekodrive.app

import android.app.Application
import com.nekodrive.app.data.AppContainer

class NekoDriveApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
