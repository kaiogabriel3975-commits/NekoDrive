package com.nekodrive.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.ImageDecoderDecoder
import com.nekodrive.app.data.AppContainer
import com.nekodrive.app.diagnostico.AppLogger

class NekoDriveApplication : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        AppLogger.install(this)
        container = AppContainer(this)
    }

    /** Coil com decodificador de imagens animadas (GIF, WebP animado, AVIF animado). */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(ImageDecoderDecoder.Factory()) }
            .build()
}
