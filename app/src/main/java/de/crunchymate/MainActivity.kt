package de.crunchymate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import de.crunchymate.ui.CrunchyMateApp
import de.crunchymate.ui.theme.CrunchyMateTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as CrunchyMateApplication).container
        setContent { CrunchyMateTheme { CrunchyMateApp(container) } }
    }

    // Die gedrosselte Speicherung darf nicht verloren gehen, wenn der Prozess im Hintergrund beendet wird
    override fun onStop() {
        super.onStop()
        val container = (application as CrunchyMateApplication).container
        container.applicationScope.launch { container.settingsStore.flush() }
    }
}
