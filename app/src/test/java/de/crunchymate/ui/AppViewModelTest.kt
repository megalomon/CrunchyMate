package de.crunchymate.ui

import de.crunchymate.core.CoreGateway
import de.crunchymate.core.FakeCore
import de.crunchymate.core.SettingsStore
import de.crunchymate.core.ffi.Stats
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {
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
    fun start_loadsSettingsOnceAndCountsBadges() =
        runTest(dispatcher) {
            val core =
                FakeCore(storedSettings = sampleSettings("en-US")).apply {
                    currentStats = Stats(total = 10, watched = 3, meh = 2, enriched = 0, tags = 0)
                }
            val gateway = CoreGateway(core, dispatcher)
            val store = SettingsStore(gateway, backgroundScope, dispatcher)

            val viewModel = AppViewModel(gateway, store)
            assertFalse(viewModel.ready.value)
            runCurrent()

            assertTrue(viewModel.ready.value)
            assertEquals("en-US", store.settings.value.contentLocale)
            assertEquals(BadgeCounts(watched = 3, meh = 2), viewModel.badges.value)

            // Eine zweite Hülle (z. B. nach Neustart der Activity) darf ungespeicherte Änderungen nicht überschreiben
            store.update { it.copy(contentLocale = "fr-FR") }
            val second = AppViewModel(gateway, store)
            runCurrent()
            assertTrue(second.ready.value)
            assertEquals("fr-FR", store.settings.value.contentLocale)
        }

    @Test
    fun changes_refreshBadges() =
        runTest(dispatcher) {
            val core = FakeCore()
            val gateway = CoreGateway(core, dispatcher)
            val viewModel = AppViewModel(gateway, SettingsStore(gateway, backgroundScope, dispatcher))
            runCurrent()

            core.currentStats = Stats(total = 1, watched = 1, meh = 0, enriched = 0, tags = 0)
            gateway.notifyChanged()
            runCurrent()

            assertEquals(BadgeCounts(watched = 1, meh = 0), viewModel.badges.value)
        }
}
