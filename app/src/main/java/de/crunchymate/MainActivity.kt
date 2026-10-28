package de.crunchymate

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import de.crunchymate.core.ffi.CoreException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { Text(stringResource(R.string.app_name)) }
        logCatalogTotal()
    }

    private fun logCatalogTotal() {
        val container = (application as CrunchyMateApplication).container
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                Log.i(LOG_TAG, "Anime im Katalog: ${container.core.stats().total}")
            } catch (e: CoreException) {
                Log.e(LOG_TAG, "Statistik konnte nicht geladen werden", e)
            }
        }
    }

    private companion object {
        const val LOG_TAG = "CrunchyMate"
    }
}
