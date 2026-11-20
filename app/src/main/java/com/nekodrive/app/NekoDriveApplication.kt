package com.nekodrive.app

import android.app.Application
import android.os.Process
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.ImageDecoderDecoder
import com.nekodrive.app.data.AppContainer
import com.nekodrive.app.diagnostico.AppLogger
import kotlin.concurrent.thread

class NekoDriveApplication : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        AppLogger.install(this)
        container = AppContainer(this)
        // O cache de mídia lê o diretório inteiro ao abrir (pode demorar com milhares de arquivos) e quem o
        // usa primeiro bloqueia até terminar. Abrindo agora, em segundo plano, esse trabalho acontece
        // junto com a partida do app em vez de dentro da primeira tela.
        thread(name = "NekoDrive-AquecerCache") {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            runCatching { container.midia }
        }
    }

    /** Coil com decodificador de imagens animadas (GIF, WebP animado, AVIF animado). */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(ImageDecoderDecoder.Factory()) }
            .build()
}
