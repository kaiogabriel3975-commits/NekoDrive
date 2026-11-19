package com.nekodrive.app.data.auth

import android.accounts.Account
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

class NeedsConsentException : Exception("Autorização necessária: remova a conta e adicione de novo.")

/** Autoriza o escopo do Drive por conta e mantém o token de acesso em memória (cerca de 1 hora). */
class AuthManager(context: Context) {
    private val client = Identity.getAuthorizationClient(context)
    private val scopes = listOf(Scope("https://www.googleapis.com/auth/drive"))
    // Acessado por várias threads (IO, OkHttp, carregadores do player).
    private val cache = ConcurrentHashMap<String, Pair<String, Long>>()
    private val mutex = Mutex()

    /** Sem [email], o Google mostra a escolha de conta. Com [email], tenta autorizar sem tela. */
    suspend fun authorize(email: String?): AuthorizationResult {
        val builder = AuthorizationRequest.builder().setRequestedScopes(scopes)
        if (email != null) builder.setAccount(Account(email, "com.google"))
        return client.authorize(builder.build()).await()
    }

    fun fromIntent(data: Intent?): AuthorizationResult =
        client.getAuthorizationResultFromIntent(requireNotNull(data))

    fun remember(email: String, token: String) {
        cache[email] = token to System.currentTimeMillis() + VALID_MS
    }

    fun forget(email: String) {
        cache.remove(email)
    }

    suspend fun token(email: String, force: Boolean = false): String {
        val hit = cache[email]
        if (!force && hit != null && hit.second > System.currentTimeMillis()) return hit.first
        // Um pedido por vez: quando o token vence, várias requisições paralelas não disparam
        // vários authorize() juntos.
        return mutex.withLock {
            val atual = cache[email]
            if (!force && atual != null && atual.second > System.currentTimeMillis()) {
                return@withLock atual.first
            }
            val token = authorize(email).accessToken ?: throw NeedsConsentException()
            remember(email, token)
            token
        }
    }

    private companion object {
        const val VALID_MS = 45 * 60 * 1000L
    }
}
