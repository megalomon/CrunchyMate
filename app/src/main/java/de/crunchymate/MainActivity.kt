package de.crunchymate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import de.crunchymate.ui.CrunchyMateApp
import de.crunchymate.ui.theme.CrunchyMateTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as CrunchyMateApplication).container
        setContent { CrunchyMateTheme { CrunchyMateApp(container) } }
    }
}
