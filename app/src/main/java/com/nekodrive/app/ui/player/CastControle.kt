package com.nekodrive.app.ui.player

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.mediarouter.app.MediaRouteChooserDialogFragment
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaSeekOptions
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastState
import com.google.android.gms.cast.framework.CastStateListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.nekodrive.app.data.cache.ServidorLocal
import java.io.IOException
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

/** Endereço do celular na rede Wi-Fi (o Chromecast busca o vídeo aqui). */
private fun ipLocal(): String? {
    val interfaces = try {
        Collections.list(NetworkInterface.getNetworkInterfaces())
    } catch (e: Exception) {
        return null
    }
    return interfaces
        .filter { it.isUp && !it.isLoopback }
        .sortedByDescending { it.name.startsWith("wlan") }
        .flatMap { Collections.list(it.inetAddresses) }
        .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
        ?.hostAddress
}

/** Sessão de transmissão para o Chromecast: estado observável e controles remotos. */
class CastControle(private val ctx: Context, private val servidor: ServidorLocal) {
    private val contexto: CastContext? = try {
        CastContext.getSharedInstance(ctx)
    } catch (e: Exception) {
        null
    }

    val disponivel: Boolean get() = contexto != null

    var estado by mutableIntStateOf(contexto?.castState ?: CastState.NO_DEVICES_AVAILABLE); private set
    var transmitindo by mutableStateOf(false); private set
    var dispositivo by mutableStateOf(""); private set
    var posicao by mutableLongStateOf(0L); private set
    var duracao by mutableLongStateOf(0L); private set
    var tocando by mutableStateOf(false); private set

    private var registrado: RemoteMediaClient? = null
    private val ouvinteEstado = CastStateListener { estado = it }
    private val ouvinteProgresso = RemoteMediaClient.ProgressListener { p, d ->
        posicao = p
        if (d > 0) duracao = d
    }
    private val retorno = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() {
            tocando = cliente()?.isPlaying == true
        }
    }

    private fun cliente(): RemoteMediaClient? = contexto?.sessionManager?.currentCastSession?.remoteMediaClient

    fun anexar() {
        contexto?.addCastStateListener(ouvinteEstado)
        estado = contexto?.castState ?: estado
    }

    fun soltar() {
        contexto?.removeCastStateListener(ouvinteEstado)
        parar(encerrarSessao = true)
    }

    /** Abre a lista de dispositivos Cast da rede. */
    fun escolherDispositivo(activity: FragmentActivity) {
        val c = contexto ?: return
        val dialogo = MediaRouteChooserDialogFragment()
        dialogo.routeSelector = c.mergedSelector
        dialogo.show(activity.supportFragmentManager, "cast")
    }

    /** Começa a transmitir [url] (endereço do Drive) pela TV, a partir de [posicaoMs]. */
    fun iniciar(titulo: String, url: String, mime: String, posicaoMs: Long) {
        val sessao = contexto?.sessionManager?.currentCastSession ?: throw IOException("Sem sessão do Cast")
        val remoto = sessao.remoteMediaClient ?: throw IOException("O dispositivo não está pronto")
        val ip = ipLocal() ?: throw IOException("Conecte o celular ao Wi-Fi")
        servidor.iniciarSeNecessario()
        val chave = servidor.registrar(url, mime)
        val endereco = "http://$ip:${servidor.porta}/v/$chave"
        val meta = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply { putString(MediaMetadata.KEY_TITLE, titulo) }
        val info = MediaInfo.Builder(endereco)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(mime)
            .setMetadata(meta)
            .build()
        remoto.registerCallback(retorno)
        remoto.addProgressListener(ouvinteProgresso, 500)
        registrado = remoto
        remoto.load(
            MediaLoadRequestData.Builder().setMediaInfo(info).setAutoplay(true).setCurrentTime(posicaoMs).build(),
        )
        dispositivo = sessao.castDevice?.friendlyName.orEmpty()
        posicao = posicaoMs
        tocando = true
        transmitindo = true
    }

    fun alternar() {
        val r = cliente() ?: return
        if (r.isPlaying) r.pause() else r.play()
    }

    fun buscar(ms: Long) {
        cliente()?.seek(MediaSeekOptions.Builder().setPosition(ms.coerceAtLeast(0L)).build())
    }

    fun pular(deltaMs: Long) = buscar(posicao + deltaMs)

    /** A sessão terminou do lado da TV (ou do celular): só limpa o estado. */
    fun terminouSessao() = parar(encerrarSessao = false)

    fun parar(encerrarSessao: Boolean) {
        registrado?.unregisterCallback(retorno)
        registrado?.removeProgressListener(ouvinteProgresso)
        registrado = null
        val estavaTransmitindo = transmitindo
        transmitindo = false
        if (encerrarSessao && estavaTransmitindo) contexto?.sessionManager?.endCurrentSession(true)
    }
}
