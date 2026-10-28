package de.crunchymate.core

import de.crunchymate.core.ffi.LanguageFilter
import de.crunchymate.core.ffi.SearchState
import de.crunchymate.core.ffi.Settings
import de.crunchymate.core.ffi.SortOrder
import de.crunchymate.core.ffi.WatchedFilter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val SAVE_DEBOUNCE = 500L

/** Hält die Einstellungen im Speicher und schreibt Änderungen verzögert in den Kern. */
class SettingsStore(
    private val gateway: CoreGateway,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
) {
    private val _settings = MutableStateFlow(defaultSettings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private var saveJob: Job? = null

    suspend fun load() {
        _settings.value = gateway.loadSettings()
    }

    fun update(transform: (Settings) -> Settings) {
        _settings.update(transform)
        saveJob?.cancel()
        saveJob =
            scope.launch(io) {
                delay(SAVE_DEBOUNCE)
                gateway.saveSettings(_settings.value)
            }
    }

    private companion object {
        /** Entspricht dem Standard des Rust-Kerns. */
        fun defaultSettings() =
            Settings(
                contentLocale = "de-DE",
                search =
                    SearchState(
                        query = "",
                        genre = null,
                        language = LanguageFilter.ALL,
                        watched = WatchedFilter.ALL,
                        sort = SortOrder.TITLE,
                        tags = emptyList(),
                    ),
            )
    }
}
