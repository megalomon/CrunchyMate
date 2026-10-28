package de.crunchymate.core

import android.util.Log
import de.crunchymate.core.ffi.CoreException
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
private const val LOG_TAG = "CrunchyMate"

/** Hält die Einstellungen im Speicher und schreibt Änderungen verzögert in den Kern. */
class SettingsStore(
    private val gateway: CoreGateway,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
) {
    private val _settings = MutableStateFlow(defaultSettings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private val _loaded = MutableStateFlow(false)

    /** true, sobald der gespeicherte Stand gelesen wurde; ein erneutes Laden würde ungespeicherte Änderungen überschreiben. */
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val lock = Any()
    private var saveJob: Job? = null
    private var dirty = false

    suspend fun load() {
        _settings.value = gateway.loadSettings()
        _loaded.value = true
    }

    fun update(transform: (Settings) -> Settings) {
        _settings.update(transform)
        synchronized(lock) {
            dirty = true
            saveJob?.cancel()
            saveJob =
                scope.launch(io) {
                    delay(SAVE_DEBOUNCE)
                    persistIfDirty()
                }
        }
    }

    /** Schreibt ausstehende Änderungen sofort, etwa bevor der Kern die Inhaltssprache für einen Sync aus der Datenbank liest. */
    suspend fun flush() {
        synchronized(lock) { saveJob?.cancel() }
        persistIfDirty()
    }

    private suspend fun persistIfDirty() {
        val snapshot =
            synchronized(lock) {
                if (!dirty) return
                dirty = false
                _settings.value
            }
        try {
            gateway.saveSettings(snapshot)
        } catch (e: CoreException) {
            // Der Stand bleibt im Speicher; die nächste Änderung versucht das Speichern erneut
            synchronized(lock) { dirty = true }
            Log.w(LOG_TAG, "Einstellungen konnten nicht gespeichert werden: ${e::class.simpleName}")
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
