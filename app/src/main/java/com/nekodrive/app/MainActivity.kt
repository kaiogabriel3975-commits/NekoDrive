package com.nekodrive.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.nekodrive.app.diagnostico.AppLogger
import com.nekodrive.app.ui.NekoDriveApp
import com.nekodrive.app.ui.theme.NekoDriveTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLogger.log(this, "MainActivity.onCreate")
        // Edge-to-edge sem a faixa escura/translúcida nas barras do sistema
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        window.isNavigationBarContrastEnforced = false
        window.navigationBarDividerColor = Color.TRANSPARENT
        val container = (application as NekoDriveApplication).container
        setContent { NekoDriveTheme(container.prefs.tema) { NekoDriveApp() } }
    }

    override fun onResume() {
        super.onResume()
        AppLogger.log(this, "MainActivity.onResume")
    }

    override fun onPause() {
        AppLogger.log(this, "MainActivity.onPause")
        super.onPause()
    }

    override fun onDestroy() {
        AppLogger.log(this, "MainActivity.onDestroy; mudandoConfiguração=$isChangingConfigurations")
        super.onDestroy()
    }
}
