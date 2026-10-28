package de.crunchymate.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.crunchymate.core.SettingsStore
import de.crunchymate.core.ffi.CoreException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val LOG_TAG = "CrunchyMate"

/** Zustand der App-Hülle: Laden der Einstellungen beim Start. */
class AppViewModel(
    private val settingsStore: SettingsStore,
) : ViewModel() {
    private val _ready = MutableStateFlow(settingsStore.loaded.value)

    /** true, sobald die Suche den gespeicherten Zustand lesen darf. */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    init {
        if (!settingsStore.loaded.value) {
            viewModelScope.launch {
                try {
                    settingsStore.load()
                } catch (e: CoreException) {
                    // Ohne gespeicherten Stand startet die Suche mit den Standardwerten
                    Log.w(LOG_TAG, "Einstellungen konnten nicht geladen werden: ${e::class.simpleName}")
                }
                _ready.value = true
            }
        }
    }
}
