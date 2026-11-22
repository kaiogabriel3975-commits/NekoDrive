package com.nekodrive.app.ui.drive

import com.nekodrive.app.data.tr
import com.nekodrive.app.data.I18n
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

fun formatarTamanho(bytes: Long?): String? {
    if (bytes == null) return null
    val unidades = arrayOf("B", "KB", "MB", "GB", "TB")
    var v = bytes.toDouble()
    var i = 0
    while (v >= 1024 && i < unidades.lastIndex) { v /= 1024; i++ }
    return if (i == 0) "$bytes B" else String.format(Locale.getDefault(), "%.1f %s", v, unidades[i])
}

private fun formatoData(): DateTimeFormatter =
    DateTimeFormatter.ofPattern(if (I18n.emIngles) "MM/dd/yyyy" else "dd/MM/yyyy")

fun formatarData(iso: String?): String? = try {
    iso?.let {
        OffsetDateTime.parse(it).atZoneSameInstant(ZoneId.systemDefault()).format(formatoData())
    }
} catch (e: Exception) {
    null
}

private fun formatoDataHora(): DateTimeFormatter =
    DateTimeFormatter.ofPattern(if (I18n.emIngles) "MM/dd/yyyy HH:mm" else "dd/MM/yyyy HH:mm")

fun formatarDataHora(iso: String?): String? = try {
    iso?.takeIf { it.isNotBlank() }?.let {
        OffsetDateTime.parse(it).atZoneSameInstant(ZoneId.systemDefault()).format(formatoDataHora())
    }
} catch (e: Exception) {
    null
}

/** A data da foto (EXIF) vem como "2024:05:01 15:30:00", sem fuso. */
fun formatarDataExif(exif: String?): String? = try {
    exif?.takeIf { it.length >= 19 }?.let {
        val iso = it.substring(0, 19).replaceFirst(':', '-').replaceFirst(':', '-').replace(' ', 'T')
        LocalDateTime.parse(iso).format(formatoDataHora())
    }
} catch (e: Exception) {
    null
}

private fun numero(d: Double): String =
    if (d % 1.0 == 0.0) d.toLong().toString() else String.format(Locale.getDefault(), "%.1f", d)

private fun exposicao(segundos: Double): String? = when {
    segundos <= 0 -> null
    segundos < 1 -> "1/${(1 / segundos).roundToInt()} s"
    else -> "${numero(segundos)} s"
}

/**
 * Monta o menu de informações só com o que o Drive confirma: campos ausentes ou vazios não aparecem.
 * Para vídeos, a API não informa data de captura, dispositivo nem câmera.
 */
fun montarInformacoes(o: JSONObject, caminho: String): List<Pair<String, String>> {
    val lista = mutableListOf<Pair<String, String>>()
    fun add(rotulo: String, valor: String?) {
        if (!valor.isNullOrBlank()) lista += rotulo to valor
    }
    val img = o.optJSONObject("imageMediaMetadata")
    val vid = o.optJSONObject("videoMediaMetadata")

    add(tr("Data de criação"), formatarDataExif(img?.optString("time")))
    add(tr("Enviado ao Drive em"), formatarDataHora(o.optString("createdTime")))
    add(tr("Nome no Drive"), o.optString("name"))
    add(tr("Local no Drive"), caminho)
    add(tr("Tamanho"), formatarTamanho(o.optString("size").toLongOrNull()))
    val dim = img ?: vid
    val largura = dim?.optInt("width", 0) ?: 0
    val altura = dim?.optInt("height", 0) ?: 0
    if (largura > 0 && altura > 0) add(tr("Resolução"), tr("%s × %s", largura, altura))
    add(tr("Formato"), o.optString("mimeType"))
    add(
        tr("Dispositivo de captura"),
        listOf(img?.optString("cameraMake").orEmpty(), img?.optString("cameraModel").orEmpty())
            .filter { it.isNotBlank() }.joinToString(" "),
    )
    add(tr("Lente"), img?.optString("lens"))
    if (img?.has("focalLength") == true) add(tr("Distância focal"), "${numero(img.optDouble("focalLength"))} mm")
    if (img?.has("aperture") == true) add(tr("Abertura"), "f/${numero(img.optDouble("aperture"))}")
    if (img?.has("exposureTime") == true) add(tr("Exposição"), exposicao(img.optDouble("exposureTime")))
    if (img?.has("isoSpeed") == true) add("ISO", img.optInt("isoSpeed").toString())
    if (img?.has("flashUsed") == true) add(tr("Flash"), if (img.optBoolean("flashUsed")) tr("Usado") else tr("Não usado"))
    add(tr("Conta proprietária"), o.optJSONArray("owners")?.optJSONObject(0)?.optString("emailAddress"))
    add(tr("Link no Drive"), o.optString("webViewLink"))
    return lista
}
