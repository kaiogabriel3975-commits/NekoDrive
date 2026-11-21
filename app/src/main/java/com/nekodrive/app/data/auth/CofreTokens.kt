package com.nekodrive.app.data.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Guarda o token de renovação de cada conta criptografado (AES-GCM) com uma chave que fica no Android Keystore
 * e não sai do aparelho. Se a chave se perder (aparelho novo, restauração de backup), a leitura devolve null
 * e o app simplesmente pede o login de novo.
 */
class CofreTokens(context: Context) {
    private val prefs = context.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE)

    private fun chave(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gerador = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gerador.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gerador.generateKey()
    }

    /** Devolve false se não deu para criptografar (nada é guardado em texto puro). */
    fun guardar(email: String, token: String): Boolean = try {
        val cifra = Cipher.getInstance(TRANSFORMACAO)
        cifra.init(Cipher.ENCRYPT_MODE, chave())
        val dados = cifra.iv + cifra.doFinal(token.toByteArray(Charsets.UTF_8))
        prefs.edit().putString(PREFIXO + email, Base64.encodeToString(dados, Base64.NO_WRAP)).apply()
        true
    } catch (e: Exception) {
        false
    }

    fun ler(email: String): String? {
        val texto = prefs.getString(PREFIXO + email, null) ?: return null
        return try {
            val dados = Base64.decode(texto, Base64.NO_WRAP)
            val cifra = Cipher.getInstance(TRANSFORMACAO)
            cifra.init(Cipher.DECRYPT_MODE, chave(), GCMParameterSpec(128, dados, 0, TAMANHO_IV))
            String(cifra.doFinal(dados, TAMANHO_IV, dados.size - TAMANHO_IV), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    fun apagar(email: String) {
        prefs.edit().remove(PREFIXO + email).apply()
    }

    private companion object {
        const val ARQUIVO = "tokens_renovacao"
        const val ALIAS = "nekodrive_token_renovacao"
        const val PREFIXO = "rt:"
        const val TRANSFORMACAO = "AES/GCM/NoPadding"
        const val TAMANHO_IV = 12
    }
}
