package com.nekodrive.app.data.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class NeedsConsentException : Exception("Autorização necessária: entre de novo com a conta.")

/** Resultado do login: [renovacao] é o token que mantém a conta autorizada sem pedir login de novo. */
class LoginConcluido(val accessToken: String, val renovacao: String?, val validoMs: Long)

/**
 * Login por navegador (Custom Tab, código de autorização + PKCE) e token de acesso por conta.
 * O token de renovação fica criptografado no aparelho; o de acesso (cerca de 1 hora) só em memória.
 * Quem usa continua chamando [token] (email), que renova sozinho quando vence.
 */
class AuthManager(context: Context) {
    private val appContext = context.applicationContext
    private val cofre = CofreTokens(appContext)
    private val http = OkHttpClient()

    // Acessado por várias threads (IO, OkHttp, carregadores do player).
    private val cache = ConcurrentHashMap<String, Pair<String, Long>>()
    private val mutex = Mutex()

    /**
     * Intent que abre o login do Google no navegador. Com [emailSugerido] o Google já vem na conta certa;
     * sem ele, mostra a escolha de conta (qualquer conta, mesmo a que não está no aparelho).
     * "consent" garante que o Google devolva o token de renovação mesmo numa conta já autorizada antes.
     */
    fun intentDeLogin(emailSugerido: String?): Intent {
        val config = AuthorizationServiceConfiguration(Uri.parse(URL_AUTORIZAR), Uri.parse(URL_TOKEN))
        val pedido = AuthorizationRequest.Builder(
            config, OAuthConfig.clientId(appContext), ResponseTypeValues.CODE, Uri.parse(OAuthConfig.REDIRECT_URI),
        )
            .setScope(ESCOPO_DRIVE)
            // com conta já conhecida o Google vai direto nela; sem conta, mostra a escolha
            .setPrompt(if (emailSugerido != null) "consent" else "select_account consent")
            .setAdditionalParameters(mapOf("access_type" to "offline"))
            .apply { if (emailSugerido != null) setLoginHint(emailSugerido) }
            .build()
        val servico = AuthorizationService(appContext)
        try {
            return servico.getAuthorizationRequestIntent(pedido)
        } finally {
            servico.dispose()
        }
    }

    /** Troca o código que o navegador devolveu pelos tokens (usa o PKCE guardado no próprio pedido). */
    suspend fun concluirLogin(data: Intent?): LoginConcluido = withContext(Dispatchers.IO) {
        if (data == null) throw IOException("Login não concluído.")
        val resposta = AuthorizationResponse.fromIntent(data)
        if (resposta == null) {
            val erro = AuthorizationException.fromIntent(data)
            throw IOException(erro?.errorDescription ?: erro?.error ?: "Login não concluído.")
        }
        val codigo = resposta.authorizationCode ?: throw IOException("O Google não devolveu o código de acesso.")
        val corpo = FormBody.Builder()
            .add("client_id", resposta.request.clientId)
            .add("grant_type", "authorization_code")
            .add("code", codigo)
            .add("redirect_uri", resposta.request.redirectUri.toString())
            .add("code_verifier", resposta.request.codeVerifier ?: "")
            .build()
        http.newCall(Request.Builder().url(URL_TOKEN).post(corpo).build()).execute().use { resp ->
            val texto = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException(descreverErro(resp.code, texto))
            val json = JSONObject(texto)
            LoginConcluido(
                accessToken = json.getString("access_token"),
                renovacao = json.optString("refresh_token").ifEmpty { null },
                validoMs = validade(json),
            )
        }
    }

    fun temRenovacao(email: String): Boolean = cofre.ler(email) != null

    /** Guarda o token de renovação da conta. Devolve false se não foi possível guardar com segurança. */
    fun guardarRenovacao(email: String, renovacao: String): Boolean = cofre.guardar(email, renovacao)

    fun remember(email: String, token: String, validoMs: Long = VALID_MS) {
        cache[email] = token to System.currentTimeMillis() + validoMs
    }

    /** Esquece a conta: token de acesso e de renovação (a conta precisa entrar de novo para voltar). */
    fun forget(email: String) {
        cache.remove(email)
        cofre.apagar(email)
    }

    suspend fun token(email: String, force: Boolean = false): String {
        val hit = cache[email]
        if (!force && hit != null && hit.second > System.currentTimeMillis()) return hit.first
        // Um pedido por vez: quando o token vence, várias requisições paralelas não disparam
        // várias renovações juntas.
        return mutex.withLock {
            val atual = cache[email]
            if (!force && atual != null && atual.second > System.currentTimeMillis()) {
                return@withLock atual.first
            }
            val renovacao = cofre.ler(email) ?: throw NeedsConsentException()
            renovar(email, renovacao)
        }
    }

    private suspend fun renovar(email: String, renovacao: String): String = withContext(Dispatchers.IO) {
        val corpo = FormBody.Builder()
            .add("client_id", OAuthConfig.clientId(appContext))
            .add("grant_type", "refresh_token")
            .add("refresh_token", renovacao)
            .build()
        http.newCall(Request.Builder().url(URL_TOKEN).post(corpo).build()).execute().use { resp ->
            val texto = resp.body?.string().orEmpty()
            if (resp.isSuccessful) {
                val json = JSONObject(texto)
                val novo = json.getString("access_token")
                remember(email, novo, validade(json))
                // o Google raramente manda outro token de renovação; se mandar, passa a valer o novo
                json.optString("refresh_token").takeIf { it.isNotEmpty() }?.let { cofre.guardar(email, it) }
                novo
            } else {
                val codigo = runCatching { JSONObject(texto).optString("error") }.getOrDefault("")
                if (codigo == "invalid_grant" || codigo == "unauthorized_client") {
                    // revogado, expirado, conta removida ou ID do cliente trocado: só um login novo resolve
                    cofre.apagar(email)
                    cache.remove(email)
                    throw NeedsConsentException()
                }
                throw IOException(descreverErro(resp.code, texto))
            }
        }
    }

    /** Validade do token de acesso com folga de 5 minutos (nunca menos de 1 minuto). */
    private fun validade(json: JSONObject): Long {
        val segundos = json.optLong("expires_in", 0L)
        return if (segundos > 0L) maxOf(segundos * 1000L - FOLGA_MS, 60_000L) else VALID_MS
    }

    private fun descreverErro(codigo: Int, corpo: String): String {
        val detalhe = runCatching {
            val j = JSONObject(corpo)
            j.optString("error_description").ifEmpty { j.optString("error") }
        }.getOrDefault("")
        return "Falha no login do Google ($codigo)" + if (detalhe.isNotEmpty()) ": $detalhe" else ""
    }

    private companion object {
        const val URL_AUTORIZAR = "https://accounts.google.com/o/oauth2/v2/auth"
        const val URL_TOKEN = "https://oauth2.googleapis.com/token"
        const val ESCOPO_DRIVE = "https://www.googleapis.com/auth/drive"
        const val VALID_MS = 45 * 60 * 1000L
        const val FOLGA_MS = 5 * 60 * 1000L
    }
}
