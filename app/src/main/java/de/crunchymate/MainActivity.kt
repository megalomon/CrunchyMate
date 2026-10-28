package de.crunchymate

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.crunchymate.ui.CrunchyMateApp
import de.crunchymate.ui.theme.CrunchyMateTheme
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as CrunchyMateApplication).container
        setContent {
            val oledFlow = remember { container.settingsStore.settings.map { it.oledMode } }
            val oledMode by oledFlow.collectAsStateWithLifecycle(initialValue = false)
            // Status- und Navigationsleiste folgen sonst dem Systemthema und blieben im OLED-Modus grau
            DisposableEffect(oledMode) {
                if (oledMode) {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.dark(Color.BLACK),
                        navigationBarStyle = SystemBarStyle.dark(Color.BLACK),
                    )
                } else {
                    enableEdgeToEdge()
                }
                onDispose {}
            }
            CrunchyMateTheme(oledMode = oledMode) { CrunchyMateApp(container) }
        }
    }

    // Die gedrosselte Speicherung darf nicht verloren gehen, wenn der Prozess im Hintergrund beendet wird
    override fun onStop() {
        super.onStop()
        val container = (application as CrunchyMateApplication).container
        container.applicationScope.launch { container.flushSettings() }
    }
}
