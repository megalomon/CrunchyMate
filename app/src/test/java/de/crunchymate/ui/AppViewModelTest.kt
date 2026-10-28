package de.crunchymate.ui

import de.crunchymate.AppContainer
import de.crunchymate.core.CoreGateway
import de.crunchymate.core.FakeCore
import de.crunchymate.core.SettingsStore
import de.crunchymate.core.ffi.CoreException
import de.crunchymate.core.sampleSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

private const val SDK = 35

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [SDK])
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

    private fun TestScope.storeFor(core: FakeCore) = SettingsStore(CoreGateway(core, dispatcher), backgroundScope, dispatcher)

    @Test
    fun start_loadsSettingsOnce() =
        runTest(dispatcher) {
            val core = FakeCore(storedSettings = sampleSettings("en-US"))
            val store = storeFor(core)

            val viewModel = AppViewModel({}, { store })
            assertEquals(StartupState.LOADING, viewModel.state.value)
            runCurrent()

            assertEquals(StartupState.READY, viewModel.state.value)
            assertEquals("en-US", store.settings.value.contentLocale)

            // Eine zweite Hülle (z. B. nach Neustart der Activity) darf ungespeicherte Änderungen nicht überschreiben
            store.update { it.copy(contentLocale = "fr-FR") }
            val second = AppViewModel({}, { store })
            runCurrent()
            assertEquals(StartupState.READY, second.state.value)
            assertEquals("fr-FR", store.settings.value.contentLocale)
        }

    @Test
    fun coreCreationFails_endsInFailedStateWithoutTouchingStore() =
        runTest(dispatcher) {
            var storeRequested = false
            val container =
                AppContainer(
                    RuntimeEnvironment.getApplication(),
                    coreFactory = { _, _, _ -> FakeCore(failOnCreate = CoreException.Cancelled()) },
                    io = dispatcher,
                )

            val viewModel =
                AppViewModel(container::initializeCore, {
                    storeRequested = true
                    storeFor(FakeCore())
                })
            runCurrent()

            assertEquals(StartupState.FAILED, viewModel.state.value)
            assertFalse(storeRequested)
        }
}
