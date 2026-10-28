package de.crunchymate.ui.settings

import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import de.crunchymate.core.CoreGateway
import de.crunchymate.core.FakeCore
import de.crunchymate.core.SAVE_DEBOUNCE
import de.crunchymate.core.SettingsStore
import de.crunchymate.core.ffi.Stats
import de.crunchymate.core.ffi.SyncKind
import de.crunchymate.core.ffi.SyncRunState
import de.crunchymate.core.ffi.SyncStatus
import de.crunchymate.core.ffi.WatchedFilter
import de.crunchymate.sync.SyncScheduler
import de.crunchymate.sync.initTestWorkManager
import de.crunchymate.ui.search.GridMode
import de.crunchymate.ui.search.SearchViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val SDK = 35

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [SDK])
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class Fixture(
        val core: FakeCore,
        val gateway: CoreGateway,
        val store: SettingsStore,
        val workManager: WorkManager,
        val viewModel: SettingsViewModel,
    )

    private suspend fun TestScope.fixture(core: FakeCore = FakeCore()): Fixture {
        val gateway = CoreGateway(core, dispatcher)
        val store = SettingsStore(gateway, backgroundScope, dispatcher)
        store.load()
        val workManager = initTestWorkManager(ApplicationProvider.getApplicationContext(), gateway)
        val viewModel = SettingsViewModel(gateway, store, SyncScheduler(workManager, gateway))
        runCurrent()
        return Fixture(core, gateway, store, workManager, viewModel)
    }

    @Test
    fun settings_hideWatched_togglesSearchWatched() =
        runTest(dispatcher) {
            val f = fixture()
            val search = SearchViewModel(GridMode.Search, f.gateway, f.store)
            runCurrent()
            assertFalse(f.viewModel.uiState.value.hideWatched)

            f.viewModel.onHideWatchedChange(true)
            runCurrent()
            assertTrue(f.viewModel.uiState.value.hideWatched)
            assertEquals(WatchedFilter.UNWATCHED, f.store.settings.value.search.watched)
            // Die Suche liest denselben Speicher und übernimmt den Schalter sofort
            assertEquals(WatchedFilter.UNWATCHED, search.uiState.value.search.watched)

            f.viewModel.onHideWatchedChange(false)
            runCurrent()
            assertFalse(f.viewModel.uiState.value.hideWatched)
            assertEquals(WatchedFilter.ALL, search.uiState.value.search.watched)
        }

    @Test
    fun settings_contentLocale_savedViaStore() =
        runTest(dispatcher) {
            val f = fixture()
            assertEquals(listOf("de-DE", "en-US"), f.viewModel.uiState.value.availableLocales)

            f.viewModel.onContentLocaleChange("en-US")
            runCurrent()
            assertEquals("en-US", f.viewModel.uiState.value.contentLocale)

            advanceTimeBy(SAVE_DEBOUNCE + 1)
            runCurrent()
            assertEquals(
                "en-US",
                f.core.savedSettings
                    .last()
                    .contentLocale,
            )
        }

    @Test
    fun settings_contentLocaleHint_onlyAfterChange() =
        runTest(dispatcher) {
            val f = fixture()
            assertFalse(f.viewModel.uiState.value.contentLocaleChangePending)

            f.viewModel.onContentLocaleChange(f.store.settings.value.contentLocale)
            runCurrent()
            assertFalse(f.viewModel.uiState.value.contentLocaleChangePending)

            f.viewModel.onContentLocaleChange("en-US")
            runCurrent()
            assertTrue(f.viewModel.uiState.value.contentLocaleChangePending)
        }

    @Test
    fun settings_locales_loadedAsyncNotInConstructor() =
        runTest(dispatcher) {
            val core = FakeCore()
            val gateway = CoreGateway(core, dispatcher)
            val store = SettingsStore(gateway, backgroundScope, dispatcher)
            val workManager = initTestWorkManager(ApplicationProvider.getApplicationContext(), gateway)

            // Der FFI-Aufruf darf nicht synchron im Konstruktor (Main-Thread) laufen
            val viewModel = SettingsViewModel(gateway, store, SyncScheduler(workManager, gateway))
            assertEquals(0, core.localeCalls)
            assertEquals(emptyList<String>(), viewModel.uiState.value.availableLocales)

            runCurrent()
            assertEquals(1, core.localeCalls)
            assertEquals(listOf("de-DE", "en-US"), viewModel.uiState.value.availableLocales)
        }

    @Test
    fun settings_showsCatalogCountry() =
        runTest(dispatcher) {
            val core =
                FakeCore().apply {
                    syncStatuses =
                        mapOf(SyncKind.CATALOG to SyncStatus(SyncKind.CATALOG, SyncRunState.COMPLETED, 0L, 0L, null, null, "DE"))
                    currentStats = Stats(total = 5, watched = 1, meh = 2, enriched = 3, tags = 4, imageCount = 0, imageBytes = 0)
                }
            val f = fixture(core)

            assertEquals("DE", f.viewModel.uiState.value.catalogCountry)
            assertEquals(Stats(5, 1, 2, 3, 4, 0, 0), f.viewModel.uiState.value.stats)
        }
}
