package com.nekodrive.app.ui.theme

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Material You (cores do papel de parede). [tema]: 0 segue o sistema, 1 claro, 2 escuro.
 * minSdk 31 garante cores dinâmicas.
 */
@Composable
fun NekoDriveTheme(tema: Int = 0, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val escuro = when (tema) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    val scheme = if (escuro) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    val view = LocalView.current
    SideEffect {
        val janela = (view.context as? Activity)?.window ?: return@SideEffect
        // O fundo da janela acompanha o tema: nada aparece preto atrás das barras do sistema
        janela.setBackgroundDrawable(ColorDrawable(scheme.background.toArgb()))
        // ícones das barras do sistema escuros no tema claro e claros no escuro, mesmo com o tema forçado
        val c = WindowCompat.getInsetsController(janela, view)
        c.isAppearanceLightStatusBars = !escuro
        c.isAppearanceLightNavigationBars = !escuro
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
