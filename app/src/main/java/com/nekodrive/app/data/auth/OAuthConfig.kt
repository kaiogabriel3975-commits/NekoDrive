package com.nekodrive.app.data.auth

/**
 * Configuração do login por navegador (AppAuth). É o único arquivo que você precisa editar para o login.
 *
 * CLIENT_ID: o "ID do cliente" do cliente OAuth tipo Android criado no Google Cloud
 * (algo como 1234567890-abcdefg.apps.googleusercontent.com). Não é segredo.
 *
 * REDIRECT_URI: tem que ser exatamente "<pacote>:/oauth2redirect". O pacote é o applicationId do app.
 * O mesmo esquema (o pacote) vai em manifestPlaceholders no app/build.gradle.kts.
 */
object OAuthConfig {
    const val CLIENT_ID = "COLE_AQUI_O_ID_DO_CLIENTE.apps.googleusercontent.com"
    const val REDIRECT_URI = "com.nekodrive.app:/oauth2redirect"

    val configurado: Boolean get() = !CLIENT_ID.startsWith("COLE_AQUI")
}
