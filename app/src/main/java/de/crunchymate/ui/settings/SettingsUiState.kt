package de.crunchymate.ui.settings

import de.crunchymate.core.ffi.Stats
import de.crunchymate.sync.SyncUiState

data class SettingsUiState(
    val catalog: SyncUiState,
    val metadata: SyncUiState,
    val stats: Stats?,
    val catalogCountry: String?,
    val contentLocale: String,
    val availableLocales: List<String>,
    val hideWatched: Boolean,
    val oledMode: Boolean,
    val showAdult: Boolean,
    val showAgeRating: Boolean,
    /** Wahr, solange die gespeicherten Daten in einer anderen als der eingestellten Inhaltssprache vorliegen. */
    val contentLocaleChangePending: Boolean,
)
