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

/** Material You (cores do papel de parede), claro/escuro seguindo o sistema. minSdk 31 garante cores dinâmicas. */
@Composable
fun NekoDriveTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scheme = if (isSystemInDarkTheme()) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    val view = LocalView.current
    SideEffect {
        // O fundo da janela acompanha o tema: nada aparece preto atrás das barras do sistema
        (view.context as? Activity)?.window?.setBackgroundDrawable(ColorDrawable(scheme.background.toArgb()))
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
