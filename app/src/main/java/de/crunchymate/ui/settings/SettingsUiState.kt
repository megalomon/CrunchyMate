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
)
