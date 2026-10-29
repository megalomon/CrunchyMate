package de.crunchymate.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.crunchymate.auth.DeviceAuthenticator
import de.crunchymate.core.CoreGateway
import de.crunchymate.core.SettingsStore
import de.crunchymate.core.contentLocaleSyncPending
import de.crunchymate.core.ffi.CoreException
import de.crunchymate.core.ffi.Settings
import de.crunchymate.core.ffi.Stats
import de.crunchymate.core.ffi.SyncKind
import de.crunchymate.core.ffi.WatchedFilter
import de.crunchymate.core.syncedContentLocale
import de.crunchymate.sync.SyncScheduler
import de.crunchymate.sync.SyncUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val LOG_TAG = "CrunchyMate"

class SettingsViewModel(
    private val gateway: CoreGateway,
    private val settingsStore: SettingsStore,
    private val scheduler: SyncScheduler,
    private val authenticator: DeviceAuthenticator,
) : ViewModel() {
    private val availableLocales = MutableStateFlow(emptyList<String>())
    private val stats = MutableStateFlow<Stats?>(null)

    // WorkManager meldet sich asynchron; bis dahin zeigt die Karte den Ruhezustand statt gar nichts
    private val catalog = scheduler.observe(SyncKind.CATALOG).onStart { emit(SyncUiState.Idle) }
    private val metadata = scheduler.observe(SyncKind.METADATA).onStart { emit(SyncUiState.Idle) }
    private val syncStates = combine(catalog, metadata) { catalogState, metadataState -> catalogState to metadataState }

    // Die Sprache der Daten liegt im Kern; so überlebt der Hinweis Prozessende und Bildschirmwechsel
    private val statsAndSyncedLocale =
        combine(stats, gateway.syncedContentLocale()) { currentStats, synced -> currentStats to synced }

    val uiState: StateFlow<SettingsUiState> =
        combine(
            syncStates,
            statsAndSyncedLocale,
            scheduler.catalogCountry.onStart { emit(null) },
            settingsStore.settings,
            availableLocales,
        ) {
            (catalogState, metadataState),
            (currentStats, syncedLocale),
            country,
            settings,
            locales,
            ->
            state(catalogState, metadataState, currentStats, country, settings, locales, syncedLocale)
        }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            state(SyncUiState.Idle, SyncUiState.Idle, null, null, settingsStore.settings.value, emptyList(), syncedLocale = null),
        )

    init {
        viewModelScope.launch { loadLocales() }
        gateway.changes
            .onStart { emit(Unit) }
            .onEach { refreshStats() }
            .launchIn(viewModelScope)
    }

    fun onStartSync(kind: SyncKind) {
        // Der Sync liest die Inhaltssprache aus der Datenbank; ausstehende Änderungen müssen vorher dort liegen
        viewModelScope.launch {
            settingsStore.flush()
            scheduler.start(kind)
        }
    }

    fun onCancelSync(kind: SyncKind) = scheduler.cancel(kind)

    fun onHideWatchedChange(hide: Boolean) {
        val watched = if (hide) WatchedFilter.UNWATCHED else WatchedFilter.ALL
        settingsStore.update { it.copy(search = it.search.copy(watched = watched)) }
    }

    fun onOledModeChange(enabled: Boolean) {
        settingsStore.update { it.copy(oledMode = enabled) }
    }

    /** Einschalten verlangt die Geräte-Authentifizierung, sofern eine Displaysperre besteht; Ausschalten nie. */
    fun onShowAdultChange(enabled: Boolean) {
        if (!enabled || !authenticator.isDeviceSecure()) {
            settingsStore.update { it.copy(showAdult = enabled) }
            return
        }
        viewModelScope.launch {
            // Bei Abbruch oder Fehlschlag bleibt die Einstellung unverändert und der Schalter aus
            if (authenticator.authenticate()) settingsStore.update { it.copy(showAdult = true) }
        }
    }

    fun onShowAgeRatingChange(enabled: Boolean) {
        settingsStore.update { it.copy(showAgeRating = enabled) }
    }

    fun onContentLocaleChange(locale: String) {
        settingsStore.update { it.copy(contentLocale = locale) }
    }

    private fun state(
        catalog: SyncUiState,
        metadata: SyncUiState,
        stats: Stats?,
        country: String?,
        settings: Settings,
        locales: List<String>,
        syncedLocale: String?,
    ) = SettingsUiState(
        catalog = catalog,
        metadata = metadata,
        stats = stats,
        catalogCountry = country,
        contentLocale = settings.contentLocale,
        availableLocales = locales,
        hideWatched = settings.search.watched == WatchedFilter.UNWATCHED,
        oledMode = settings.oledMode,
        showAdult = settings.showAdult,
        showAgeRating = settings.showAgeRating,
        contentLocaleChangePending = contentLocaleSyncPending(syncedLocale, settings.contentLocale),
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
