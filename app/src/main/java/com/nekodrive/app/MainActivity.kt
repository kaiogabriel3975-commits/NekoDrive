package com.nekodrive.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.nekodrive.app.ui.NekoDriveApp
import com.nekodrive.app.ui.theme.NekoDriveTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { NekoDriveTheme { NekoDriveApp() } }
    }
}
