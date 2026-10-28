package de.crunchymate.ui

import de.crunchymate.core.CoreGateway
import de.crunchymate.core.FakeCore
import de.crunchymate.core.SettingsStore
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
    fun start_loadsSettingsOnce() =
        runTest(dispatcher) {
            val core = FakeCore(storedSettings = sampleSettings("en-US"))
            val gateway = CoreGateway(core, dispatcher)
            val store = SettingsStore(gateway, backgroundScope, dispatcher)

            val viewModel = AppViewModel(store)
            assertFalse(viewModel.ready.value)
            runCurrent()

            assertTrue(viewModel.ready.value)
            assertEquals("en-US", store.settings.value.contentLocale)

            // Eine zweite Hülle (z. B. nach Neustart der Activity) darf ungespeicherte Änderungen nicht überschreiben
            store.update { it.copy(contentLocale = "fr-FR") }
            val second = AppViewModel(store)
            runCurrent()
            assertTrue(second.ready.value)
            assertEquals("fr-FR", store.settings.value.contentLocale)
        }
}
