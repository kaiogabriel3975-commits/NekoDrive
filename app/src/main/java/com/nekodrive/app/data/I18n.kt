package com.nekodrive.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * Idioma do app. Os textos do código estão em português e valem como chave: no modo inglês, [tr] procura a tradução
 * em [INGLES] e, se não houver, mostra o português (nunca quebra). O idioma é um estado do Compose: ao trocar,
 * tudo o que usa [tr] na tela se redesenha na hora.
 */
object I18n {
    private var ingles by mutableStateOf(false)

    val emIngles: Boolean get() = ingles

    /** [opcao]: 0 = automático (inglês, a menos que o aparelho esteja em português), 1 = português, 2 = inglês. */
    fun definir(opcao: Int) {
        ingles = when (opcao) {
            1 -> false
            2 -> true
            else -> Locale.getDefault().language != "pt"
        }
    }

    fun texto(pt: String): String = if (ingles) INGLES[pt] ?: pt else pt
}

/** Texto na língua escolhida. A chave é o texto em português. */
fun tr(pt: String): String = I18n.texto(pt)

/** Igual a [tr], para textos com valores: use %s no texto e passe os valores em [args]. */
fun tr(pt: String, vararg args: Any?): String = String.format(Locale.ROOT, I18n.texto(pt), *args)
