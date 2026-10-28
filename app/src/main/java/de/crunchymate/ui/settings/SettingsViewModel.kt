package de.crunchymate.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.crunchymate.core.CoreGateway
import de.crunchymate.core.SettingsStore
import de.crunchymate.core.ffi.CoreException
import de.crunchymate.core.ffi.Settings
import de.crunchymate.core.ffi.Stats
import de.crunchymate.core.ffi.SyncKind
import de.crunchymate.core.ffi.WatchedFilter
import de.crunchymate.sync.SyncScheduler
import de.crunchymate.sync.SyncUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val LOG_TAG = "CrunchyMate"

class SettingsViewModel(
    private val gateway: CoreGateway,
    private val settingsStore: SettingsStore,
    private val scheduler: SyncScheduler,
) : ViewModel() {
    private val availableLocales = MutableStateFlow(emptyList<String>())
    private val stats = MutableStateFlow<Stats?>(null)
    private val localeChangePending = MutableStateFlow(false)

    // WorkManager meldet sich asynchron; bis dahin zeigt die Karte den Ruhezustand statt gar nichts
    private val catalog = scheduler.observe(SyncKind.CATALOG).onStart { emit(SyncUiState.Idle) }
    private val metadata = scheduler.observe(SyncKind.METADATA).onStart { emit(SyncUiState.Idle) }
    private val syncStates = combine(catalog, metadata) { catalogState, metadataState -> catalogState to metadataState }

    private val statsAndHint = combine(stats, localeChangePending) { currentStats, pending -> currentStats to pending }

    val uiState: StateFlow<SettingsUiState> =
        combine(syncStates, statsAndHint, scheduler.catalogCountry.onStart { emit(null) }, settingsStore.settings, availableLocales) {
            (catalogState, metadataState),
            (currentStats, localeHint),
            country,
            settings,
            locales,
            ->
            state(catalogState, metadataState, currentStats, country, settings, locales, localeHint)
        }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            state(SyncUiState.Idle, SyncUiState.Idle, null, null, settingsStore.settings.value, emptyList(), localeHint = false),
        )

    init {
        viewModelScope.launch { loadLocales() }
        gateway.changes
            .onStart { emit(Unit) }
            .onEach { refreshStats() }
            .launchIn(viewModelScope)
        // Ein abgeschlossener Katalog-Sync hat die neue Inhaltssprache übernommen
        catalog
            .map { it.lastFinishedAt }
            .distinctUntilChanged()
            .onEach { localeChangePending.value = false }
            .launchIn(viewModelScope)
    }

    fun onStartSync(kind: SyncKind) {
        scheduler.start(kind)
    }

    fun onCancelSync(kind: SyncKind) = scheduler.cancel(kind)

    fun onHideWatchedChange(hide: Boolean) {
        val watched = if (hide) WatchedFilter.UNWATCHED else WatchedFilter.ALL
        settingsStore.update { it.copy(search = it.search.copy(watched = watched)) }
    }

    fun onContentLocaleChange(locale: String) {
        if (locale != settingsStore.settings.value.contentLocale) localeChangePending.value = true
        settingsStore.update { it.copy(contentLocale = locale) }
    }

    private fun state(
        catalog: SyncUiState,
        metadata: SyncUiState,
        stats: Stats?,
        country: String?,
        settings: Settings,
        locales: List<String>,
        localeHint: Boolean,
    ) = SettingsUiState(
        catalog = catalog,
        metadata = metadata,
        stats = stats,
        catalogCountry = country,
        contentLocale = settings.contentLocale,
        availableLocales = locales,
        hideWatched = settings.search.watched == WatchedFilter.UNWATCHED,
        contentLocaleChangePending = localeHint,
    )

    private suspend fun loadLocales() {
        try {
            availableLocales.value = gateway.supportedContentLocales()
        } catch (e: CoreException) {
            Log.w(LOG_TAG, "Inhaltssprachen konnten nicht geladen werden: ${e::class.simpleName}")
        }
    }

    private suspend fun refreshStats() {
        try {
            stats.value = gateway.stats()
        } catch (e: CoreException) {
            Log.w(LOG_TAG, "Statistik konnte nicht geladen werden: ${e::class.simpleName}")
        }
    }
}
