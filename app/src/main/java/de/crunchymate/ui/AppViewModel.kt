package de.crunchymate.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.crunchymate.core.CoreGateway
import de.crunchymate.core.SettingsStore
import de.crunchymate.core.ffi.CoreException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

private const val LOG_TAG = "CrunchyMate"

/** Zähler für die Badges der Navigationsleiste. */
data class BadgeCounts(
    val watched: Long,
    val meh: Long,
)

/** Zustand der App-Hülle: Laden der Einstellungen beim Start und die Badge-Zähler. */
class AppViewModel(
    private val gateway: CoreGateway,
    private val settingsStore: SettingsStore,
) : ViewModel() {
    private val _ready = MutableStateFlow(settingsStore.loaded.value)

    /** true, sobald die Suche den gespeicherten Zustand lesen darf. */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _badges = MutableStateFlow(BadgeCounts(watched = 0, meh = 0))
    val badges: StateFlow<BadgeCounts> = _badges.asStateFlow()

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
        gateway.changes
            .onStart { emit(Unit) }
            .onEach { refreshBadges() }
            .launchIn(viewModelScope)
    }

    private suspend fun refreshBadges() {
        try {
            val stats = gateway.stats()
            _badges.value = BadgeCounts(watched = stats.watched, meh = stats.meh)
        } catch (e: CoreException) {
            Log.w(LOG_TAG, "Statistik konnte nicht geladen werden: ${e::class.simpleName}")
        }
    }
}
