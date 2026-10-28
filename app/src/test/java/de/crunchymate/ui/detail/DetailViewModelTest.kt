package de.crunchymate.ui.detail

import de.crunchymate.R
import de.crunchymate.core.CoreGateway
import de.crunchymate.core.FakeCore
import de.crunchymate.core.SettingsStore
import de.crunchymate.core.ffi.TagRank
import de.crunchymate.core.ffi.TagSelection
import de.crunchymate.core.ffi.TagState
import de.crunchymate.core.sampleDetail
import de.crunchymate.core.sampleSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun detail_loadsAnimeAndExposesTagStates() =
        runTest(dispatcher) {
            val selection = listOf(TagSelection("Magie", TagState.MUST))
            val core =
                FakeCore(storedSettings = sampleSettings().let { it.copy(search = it.search.copy(tags = selection)) }).apply {
                    details = mapOf("a" to sampleDetail("a", tags = listOf(TagRank("Magie", 80))))
                }
            val gateway = CoreGateway(core, dispatcher)
            val store = SettingsStore(gateway, backgroundScope, dispatcher)
            store.load()

            val viewModel = DetailViewModel("a", gateway, store)
            runCurrent()

            val state = viewModel.uiState.value
            assertEquals("a", state.anime?.id)
            assertFalse(state.loading)
            assertNull(state.error)
            assertEquals(selection, state.tagStates)
        }

    @Test
    fun detail_tagTap_cyclesSearchTagState() =
        runTest(dispatcher) {
            val core = FakeCore().apply { details = mapOf("a" to sampleDetail("a", tags = listOf(TagRank("Magie", 80)))) }
            val gateway = CoreGateway(core, dispatcher)
            val store = SettingsStore(gateway, backgroundScope, dispatcher)
            store.load()
            val viewModel = DetailViewModel("a", gateway, store)
            runCurrent()

            viewModel.onTagTapped("Magie")
            runCurrent()

            val expected = listOf(TagSelection("Magie", TagState.PREFER))
            assertEquals(expected, store.settings.value.search.tags)
            assertEquals(expected, viewModel.uiState.value.tagStates)
        }

    @Test
    fun detail_unknownId_showsError() =
        runTest(dispatcher) {
            val gateway = CoreGateway(FakeCore(), dispatcher)
            val store = SettingsStore(gateway, backgroundScope, dispatcher)

            val viewModel = DetailViewModel("fehlt", gateway, store)
            runCurrent()

            val state = viewModel.uiState.value
            assertNull(state.anime)
            assertFalse(state.loading)
            assertEquals(R.string.detail_not_found, state.error)
        }
}
