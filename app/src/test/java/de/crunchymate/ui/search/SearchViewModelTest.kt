package de.crunchymate.ui.search

import androidx.paging.PagingSource
import de.crunchymate.core.AnimePagingSource
import de.crunchymate.core.CoreGateway
import de.crunchymate.core.FakeCore
import de.crunchymate.core.SAVE_DEBOUNCE
import de.crunchymate.core.SettingsStore
import de.crunchymate.core.ffi.AnimeSummary
import de.crunchymate.core.ffi.NameCount
import de.crunchymate.core.ffi.TagSelection
import de.crunchymate.core.ffi.TagState
import de.crunchymate.core.ffi.WatchedFilter
import de.crunchymate.core.sampleSettings
import de.crunchymate.core.sampleSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
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

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
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
        val store: SettingsStore,
        val viewModel: SearchViewModel,
        val sources: List<PagingSource<Int, AnimeSummary>>,
    )

    private suspend fun TestScope.fixture(
        core: FakeCore = FakeCore(),
        mode: GridMode = GridMode.Search,
    ): Fixture {
        val gateway = CoreGateway(core, dispatcher)
        val store = SettingsStore(gateway, backgroundScope, dispatcher)
        store.load()
        val sources = mutableListOf<PagingSource<Int, AnimeSummary>>()
        val viewModel =
            SearchViewModel(mode, gateway, store) { filter ->
                AnimePagingSource(gateway, filter).also { sources += it }
            }
        runCurrent()
        return Fixture(core, store, viewModel, sources)
    }

    @Test
    fun tagTap_cyclesAndPersistsViaSettingsStore() =
        runTest(dispatcher) {
            val f = fixture()

            f.viewModel.onTagTapped("Magie")
            runCurrent()
            assertEquals(listOf(TagSelection("Magie", TagState.PREFER)), f.viewModel.uiState.value.activeTags)

            f.viewModel.onTagTapped("Magie")
            runCurrent()
            assertEquals(listOf(TagSelection("Magie", TagState.MUST)), f.store.settings.value.search.tags)

            advanceTimeBy(SAVE_DEBOUNCE + 1)
            runCurrent()
            val saved = f.core.savedSettings.last()
            assertEquals(listOf(TagSelection("Magie", TagState.MUST)), saved.search.tags)
        }

    @Test
    fun staleTagInSearchState_staysInActiveTags() =
        runTest(dispatcher) {
            val stored = sampleSettings().let { it.copy(search = it.search.copy(tags = listOf(TagSelection("Alt", TagState.PREFER)))) }
            val f = fixture(FakeCore(storedSettings = stored))

            val state = f.viewModel.uiState.value
            assertFalse(state.tags.any { it.name == "Alt" })
            assertEquals(listOf(TagSelection("Alt", TagState.PREFER)), state.activeTags)

            repeat(3) { f.viewModel.onTagTapped("Alt") }
            runCurrent()
            val activeTags = f.viewModel.uiState.value.activeTags
            assertEquals(emptyList<TagSelection>(), activeTags)
        }

    @Test
    fun tagQuery_filtersVisibleTagsCaseInsensitive() =
        runTest(dispatcher) {
            val core =
                FakeCore().apply {
                    tagList = listOf(NameCount("Magie", 2L), NameCount("Mecha", 1L), NameCount("Romantik", 4L))
                }
            val f = fixture(core)
            assertEquals(3, f.viewModel.uiState.value.visibleTags.size)

            f.viewModel.onTagQueryChange("MAG")
            runCurrent()

            assertEquals("MAG", f.viewModel.uiState.value.tagQuery)
            assertEquals(listOf(NameCount("Magie", 2L)), f.viewModel.uiState.value.visibleTags)
            assertEquals(3, f.viewModel.uiState.value.tags.size)
        }

    @Test
    fun toggleWatched_callsGatewayAndInvalidates() =
        runTest(dispatcher) {
            val f = fixture(FakeCore(items = listOf(sampleSummary("a"))))
            backgroundScope.launch { f.viewModel.items.collect {} }
            runCurrent()
            val totalQueriesBefore = f.core.queryCalls.count { it.third == 0 }
            assertEquals(1L, f.viewModel.uiState.value.total)

            f.viewModel.onToggleWatched(sampleSummary("a"))
            runCurrent()

            assertEquals(listOf("a" to true), f.core.watchedCalls)
            assertTrue(f.sources.first().invalid)
            assertTrue(f.core.queryCalls.count { it.third == 0 } > totalQueriesBefore)
        }

    @Test
    fun watchedSelection_updatesHideWatchedCoupling() =
        runTest(dispatcher) {
            val f = fixture()

            f.viewModel.onWatchedSelected(WatchedFilter.UNWATCHED)
            runCurrent()

            assertEquals(WatchedFilter.UNWATCHED, f.store.settings.value.search.watched)
            assertEquals(WatchedFilter.UNWATCHED, f.viewModel.uiState.value.search.watched)
        }
}
