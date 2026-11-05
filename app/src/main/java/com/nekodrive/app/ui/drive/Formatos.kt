package com.nekodrive.app.ui.drive

import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun formatarTamanho(bytes: Long?): String? {
    if (bytes == null) return null
    val unidades = arrayOf("B", "KB", "MB", "GB", "TB")
    var v = bytes.toDouble()
    var i = 0
    while (v >= 1024 && i < unidades.lastIndex) { v /= 1024; i++ }
    return if (i == 0) "$bytes B" else String.format(Locale.getDefault(), "%.1f %s", v, unidades[i])
}

fun formatarData(iso: String?): String? = try {
    iso?.let {
        OffsetDateTime.parse(it).atZoneSameInstant(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
    }
} catch (e: Exception) {
    null
}
