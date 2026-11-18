package com.nekodrive.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/** Preferências de visualização (grade/lista, ordenação) e ajustes do app. */
class ViewPrefs(context: Context) {
    private val p = context.getSharedPreferences("view", Context.MODE_PRIVATE)

    var grade: Boolean
        get() = p.getBoolean("grade", true)
        set(v) { p.edit().putBoolean("grade", v).apply() }

    var campo: String
        get() = p.getString("campo", "Nome") ?: "Nome"
        set(v) { p.edit().putString("campo", v).apply() }

    /** Limite do cache de mídia, em GB. */
    var limiteGb: Int
        get() = p.getInt("limiteGb", 5)
        set(v) { p.edit().putInt("limiteGb", v).apply() }

    var crescente: Boolean
        get() = p.getBoolean("crescente", true)
        set(v) { p.edit().putBoolean("crescente", v).apply() }

    /** Tema: 0 = sistema, 1 = claro, 2 = escuro. Fica em estado do Compose para o tema reagir na hora. */
    private var temaEstado by mutableIntStateOf(p.getInt("tema", 0))
    var tema: Int
        get() = temaEstado
        set(v) { temaEstado = v; p.edit().putInt("tema", v).apply() }

    /** Efeito de vidro (blur) nos botões e barras. */
    var vidro: Boolean
        get() = p.getBoolean("vidro", true)
        set(v) { p.edit().putBoolean("vidro", v).apply() }

    /** Tamanho das miniaturas: 1 médias, 2 grandes (o antigo 0 "pequenas" foi removido e vale como médias). */
    var miniatura: Int
        get() = p.getInt("miniatura", 1).coerceAtLeast(1)
        set(v) { p.edit().putInt("miniatura", v).apply() }

    var repetirVideo: Boolean
        get() = p.getBoolean("repetirVideo", true)
        set(v) { p.edit().putBoolean("repetirVideo", v).apply() }

    var retomarVideo: Boolean
        get() = p.getBoolean("retomarVideo", true)
        set(v) { p.edit().putBoolean("retomarVideo", v).apply() }

    /** Segundos que os toques duplos avançam/voltam. */
    var saltoSeg: Int
        get() = p.getInt("saltoSeg", 10)
        set(v) { p.edit().putInt("saltoSeg", v).apply() }

    /** Velocidade ao segurar o dedo no vídeo. */
    var turbo: Float
        get() = p.getFloat("turbo", 2f)
        set(v) { p.edit().putFloat("turbo", v).apply() }

    /** Resolução máxima no Wi-Fi e nos dados móveis (lado menor, em pixels). */
    var resWifi: Int
        get() = p.getInt("resWifi", 1080)
        set(v) { p.edit().putInt("resWifi", v).apply() }

    var resMovel: Int
        get() = p.getInt("resMovel", 540)
        set(v) { p.edit().putInt("resMovel", v).apply() }

    /** Tempo até os controles do vídeo sumirem; 0 = nunca. */
    var controlesMs: Int
        get() = p.getInt("controlesMs", 3500)
        set(v) { p.edit().putInt("controlesMs", v).apply() }

    /** Pausa da mídia sobre a miniatura ao fechar a galeria. */
    var esperaFecharMs: Int
        get() = p.getInt("esperaFecharMs", 100)
        set(v) { p.edit().putInt("esperaFecharMs", v).apply() }

    /** GIFs animam nas miniaturas (desligado: só o primeiro quadro). */
    var animarGifs: Boolean
        get() = p.getBoolean("animarGifs", true)
        set(v) { p.edit().putBoolean("animarGifs", v).apply() }

    /** Colunas da grade: 0 = automático (pelo tamanho das miniaturas), ou 2, 3 ou 4. */
    var colunas: Int
        get() = p.getInt("colunas", 0)
        set(v) { p.edit().putInt("colunas", v).apply() }

    /** Intensidade do blur do vidro: 0 baixa, 1 média, 2 alta. */
    var blurIntensidade: Int
        get() = p.getInt("blurIntensidade", 1)
        set(v) { p.edit().putInt("blurIntensidade", v).apply() }

    /** Reflexos sutis no contorno das pílulas da galeria. */
    var reflexoPilula: Boolean
        get() = p.getBoolean("reflexoPilula", true)
        set(v) { p.edit().putBoolean("reflexoPilula", v).apply() }

    /** Reflexos de luz só nas toolbars (pílula da galeria, player de vídeo, toolbar principal e busca). */
    var reflexoSoToolbars: Boolean
        get() = p.getBoolean("reflexoSoToolbars", false)
        set(v) { p.edit().putBoolean("reflexoSoToolbars", v).apply() }

    /** Fundo dos cards de mídia: 0 cor da mídia, 1 neutro (sem cores), 2 miniatura invertida, desfocada e escurecida. */
    var fundoCard: Int
        get() = p.getInt("fundoCard", 0)
        set(v) { p.edit().putInt("fundoCard", v).apply() }

    /** Pastas sempre antes dos arquivos na ordenação. */
    var pastasPrimeiro: Boolean
        get() = p.getBoolean("pastasPrimeiro", true)
        set(v) { p.edit().putBoolean("pastasPrimeiro", v).apply() }

    /** Nomes de fotos e vídeos nos cards da grade. */
    var mostrarNomes: Boolean
        get() = p.getBoolean("mostrarNomes", true)
        set(v) { p.edit().putBoolean("mostrarNomes", v).apply() }

    /** Miniaturas novas só são baixadas no Wi-Fi; nos dados móveis aparecem só as que já estão guardadas. */
    var miniaturasSoWifi: Boolean
        get() = p.getBoolean("miniaturasSoWifi", false)
        set(v) { p.edit().putBoolean("miniaturasSoWifi", v).apply() }

    /** Vibração ao segurar um item ou arrastar uma foto. */
    var vibrar: Boolean
        get() = p.getBoolean("vibrar", true)
        set(v) { p.edit().putBoolean("vibrar", v).apply() }

    /** Animações: 0 normais, 1 baratas para a GPU, 2 sem animações. */
    var animacoes: Int
        get() = p.getInt("animacoes", 0)
        set(v) { p.edit().putInt("animacoes", v).apply() }

    var pediuNotificacao: Boolean
        get() = p.getBoolean("pediuNotificacao", false)
        set(v) { p.edit().putBoolean("pediuNotificacao", v).apply() }
}
