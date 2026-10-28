package de.crunchymate.ui

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.crunchymate.core.SettingsStore
import de.crunchymate.core.ffi.CoreException
import de.crunchymate.core.messageRes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val LOG_TAG = "CrunchyMate"

sealed interface StartupState {
    data object Loading : StartupState

    data object Ready : StartupState

    /** [reason] nennt den konkreten Grund, wenn der Kern einen Fehlercode geliefert hat. */
    data class Failed(
        @StringRes val reason: Int?,
    ) : StartupState
}

/**
 * Zustand der App-Hülle: Kern öffnen, dann Einstellungen laden.
 * [openCore] läuft abseits des Main-Threads; [settingsStore] wird erst danach angefragt,
 * weil schon der Zugriff auf den Store den Kern öffnen würde.
 */
class AppViewModel(
    private val openCore: suspend () -> Unit,
    private val settingsStore: () -> SettingsStore,
) : ViewModel() {
    private val _state = MutableStateFlow<StartupState>(StartupState.Loading)

    /** Ready, sobald die Suche den gespeicherten Zustand lesen darf. */
    val state: StateFlow<StartupState> = _state.asStateFlow()

    init {
        viewModelScope.launch { _state.value = start() }
    }

    private suspend fun start(): StartupState =
        try {
            openCore()
            val store = settingsStore()
            // Ohne gelesenen Stand dürfte die App nie mit Standardwerten laufen: ein Speichern würde die echten überschreiben
            if (!store.loaded.value) store.load()
            StartupState.Ready
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Start fehlgeschlagen: ${e::class.simpleName}")
            StartupState.Failed((e as? CoreException)?.messageRes())
        }
}
