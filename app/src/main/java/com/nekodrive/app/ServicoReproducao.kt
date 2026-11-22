package com.nekodrive.app

import com.nekodrive.app.data.tr
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/** Liga o player da galeria ao serviço que mantém o áudio e a notificação quando o Picture-in-Picture é fechado. */
object ReprodutorSegundoPlano {
    @Volatile var player: ExoPlayer? = null
    @Volatile var titulo: String = ""
    @Volatile var aoSalvar: (() -> Unit)? = null
    @Volatile var ativo: Boolean = false

    /** Deve ser chamado com o app visível (por exemplo, ao entrar no Picture-in-Picture). */
    fun iniciar(ctx: Context, p: ExoPlayer, titulo: String, aoSalvar: () -> Unit): Boolean {
        this.player = p
        this.titulo = titulo
        this.aoSalvar = aoSalvar
        return try {
            ContextCompat.startForegroundService(ctx, Intent(ctx, ServicoReproducao::class.java))
            ativo = true
            true
        } catch (e: Exception) {
            this.player = null
            ativo = false
            false
        }
    }

    /** Tira a notificação e solta o player (que continua sendo do app). */
    fun parar(ctx: Context) {
        ativo = false
        player = null
        aoSalvar = null
        ctx.stopService(Intent(ctx, ServicoReproducao::class.java))
    }
}

/** Serviço em primeiro plano de reprodução: mostra os controles na notificação enquanto o áudio toca em segundo plano. */
class ServicoReproducao : Service() {
    private var player: ExoPlayer? = null
    private var sessao: MediaSession? = null
    private var emPrimeiroPlano = false
    private val handler = Handler(Looper.getMainLooper())
    private val encerrarPorInatividade = Runnable { encerrar() }

    private val ouvinte = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            handler.removeCallbacks(encerrarPorInatividade)
            if (!isPlaying) handler.postDelayed(encerrarPorInatividade, 10 * 60 * 1000L)
            atualizar()
        }

        override fun onPlaybackStateChanged(state: Int) = atualizar()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val canal = NotificationChannel(CANAL, tr("Reprodução em segundo plano"), NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(canal)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val p = ReprodutorSegundoPlano.player
        if (p == null) {
            // um serviço iniciado como "primeiro plano" precisa mostrar uma notificação antes de sair
            entrarEmPrimeiroPlano(montarNotificacao(null))
            encerrar()
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACAO_PLAY -> p.play()
            ACAO_PAUSE -> p.pause()
            ACAO_PARAR -> { p.pause(); encerrar(); return START_NOT_STICKY }
        }
        if (player !== p) {
            player?.removeListener(ouvinte)
            player = p
            p.addListener(ouvinte)
            sessao?.release()
            sessao = MediaSession(this, "nekodrive").apply {
                setCallback(object : MediaSession.Callback() {
                    override fun onPlay() { player?.play() }
                    override fun onPause() { player?.pause() }
                    override fun onStop() { player?.pause(); encerrar() }
                    override fun onSeekTo(pos: Long) { player?.seekTo(pos) }
                })
                isActive = true
            }
        }
        atualizar()
        return START_NOT_STICKY
    }

    private fun atualizar() {
        val p = player
        val s = sessao
        if (p != null && s != null) {
            s.setMetadata(
                MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, ReprodutorSegundoPlano.titulo)
                    .putLong(MediaMetadata.METADATA_KEY_DURATION, p.duration.coerceAtLeast(0L))
                    .build(),
            )
            val tocando = p.isPlaying
            s.setPlaybackState(
                PlaybackState.Builder()
                    .setActions(
                        PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
                            PlaybackState.ACTION_STOP or PlaybackState.ACTION_SEEK_TO,
                    )
                    .setState(
                        if (tocando) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                        p.currentPosition.coerceAtLeast(0L),
                        if (tocando) p.playbackParameters.speed else 0f,
                    )
                    .build(),
            )
        }
        val n = montarNotificacao(p)
        if (emPrimeiroPlano) {
            getSystemService(NotificationManager::class.java).notify(ID, n)
        } else {
            entrarEmPrimeiroPlano(n)
        }
    }

    private fun entrarEmPrimeiroPlano(n: Notification) {
        try {
            ServiceCompat.startForeground(this, ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            emPrimeiroPlano = true
        } catch (e: Exception) {
            // o sistema não deixou ficar em primeiro plano: o áudio não deve continuar sem controle
            player?.pause()
            stopSelf()
        }
    }

    private fun montarNotificacao(p: ExoPlayer?): Notification {
        val tocando = p?.isPlaying == true
        val acaoPrincipal = if (tocando) {
            Notification.Action.Builder(Icon.createWithResource(this, android.R.drawable.ic_media_pause), tr("Pausar"), acao(ACAO_PAUSE)).build()
        } else {
            Notification.Action.Builder(Icon.createWithResource(this, android.R.drawable.ic_media_play), tr("Reproduzir"), acao(ACAO_PLAY)).build()
        }
        val acaoParar = Notification.Action.Builder(
            Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel), tr("Fechar"), acao(ACAO_PARAR),
        ).build()
        val abrir = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val estilo = Notification.MediaStyle().setShowActionsInCompactView(0, 1)
        sessao?.let { estilo.setMediaSession(it.sessionToken) }
        return Notification.Builder(this, CANAL)
            .setSmallIcon(R.drawable.ic_notificacao)
            .setContentTitle(ReprodutorSegundoPlano.titulo.ifEmpty { "nekodrive" })
            .setContentText("nekodrive")
            .setContentIntent(abrir)
            .addAction(acaoPrincipal)
            .addAction(acaoParar)
            .setStyle(estilo)
            .setOngoing(tocando)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()
    }

    private fun acao(nome: String): PendingIntent = PendingIntent.getService(
        this, nome.hashCode(), Intent(this, ServicoReproducao::class.java).setAction(nome),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun encerrar() {
        ReprodutorSegundoPlano.aoSalvar?.invoke()
        ReprodutorSegundoPlano.ativo = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(encerrarPorInatividade)
        player?.removeListener(ouvinte)
        player = null
        sessao?.release()
        sessao = null
        ReprodutorSegundoPlano.ativo = false
        super.onDestroy()
    }

    private companion object {
        const val CANAL = "reproducao"
        const val ID = 4711
        const val ACAO_PLAY = "com.nekodrive.app.PLAY"
        const val ACAO_PAUSE = "com.nekodrive.app.PAUSE"
        const val ACAO_PARAR = "com.nekodrive.app.PARAR"
    }
}
