package com.nekodrive.app.data.auth

import android.content.Context
import android.content.pm.PackageManager
import java.security.MessageDigest

/**
 * Configuração do login por navegador (AppAuth).
 *
 * O ID do cliente é o do SEU projeto no Google Cloud: cada pessoa que usa o app cria o próprio (de graça) e cola
 * na tela "ID do cliente do Google". Fica guardado no aparelho. Não é segredo.
 *
 * CLIENT_ID_PADRAO: opcional. Para um build seu, que já nasce configurado, coloque o ID aqui. Em um repositório
 * público deixe vazio.
 *
 * REDIRECT_URI: tem que ser exatamente "<pacote>:/oauth2redirect". O pacote é o applicationId do app.
 * O mesmo esquema (o pacote) vai em manifestPlaceholders no app/build.gradle.kts.
 */
object OAuthConfig {
    const val CLIENT_ID_PADRAO = ""
    const val REDIRECT_URI = "com.nekodrive.app:/oauth2redirect"
    const val PACOTE = "com.nekodrive.app"
    private const val SUFIXO = ".apps.googleusercontent.com"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("config_google", Context.MODE_PRIVATE)

    /** O ID salvo no app; se não houver, o padrão do build; vazio se nenhum. */
    fun clientId(context: Context): String =
        prefs(context).getString("client_id", null)?.trim().takeUnless { it.isNullOrEmpty() } ?: CLIENT_ID_PADRAO

    fun configurado(context: Context): Boolean = idValido(clientId(context))

    fun idValido(id: String): Boolean = id.trim().length > SUFIXO.length && id.trim().endsWith(SUFIXO)

    fun definir(context: Context, id: String) {
        prefs(context).edit().putString("client_id", id.trim()).apply()
    }

    /** SHA-1 da assinatura deste app instalado (é o que o Google pede ao criar o cliente Android). */
    fun sha1(context: Context): String? {
        return try {
            val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            val assinatura = info.signingInfo?.apkContentsSigners?.firstOrNull() ?: return null
            MessageDigest.getInstance("SHA-1").digest(assinatura.toByteArray())
                .joinToString(":") { "%02X".format(it) }
        } catch (e: Exception) {
            null
        }
    }
}
