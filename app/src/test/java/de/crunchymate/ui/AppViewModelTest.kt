package de.crunchymate.ui

import de.crunchymate.AppContainer
import de.crunchymate.R
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
            assertEquals(StartupState.Loading, viewModel.state.value)
            runCurrent()

            assertEquals(StartupState.Ready, viewModel.state.value)
            assertEquals("en-US", store.settings.value.contentLocale)

            // Eine zweite Hülle (z. B. nach Neustart der Activity) darf ungespeicherte Änderungen nicht überschreiben
            store.update { it.copy(contentLocale = "fr-FR") }
            val second = AppViewModel({}, { store })
            runCurrent()
            assertEquals(StartupState.Ready, second.state.value)
            assertEquals("fr-FR", store.settings.value.contentLocale)
        }

    private fun TestScope.startupWith(
        failure: Throwable,
        onStoreRequested: () -> Unit = {},
    ): AppViewModel {
        val container =
            AppContainer(
                RuntimeEnvironment.getApplication(),
                coreFactory = { _, _, _ -> FakeCore(failOnCreate = failure) },
                io = dispatcher,
            )
        val viewModel =
            AppViewModel(container::initializeCore, {
                onStoreRequested()
                storeFor(FakeCore())
            })
        runCurrent()
        return viewModel
    }

    @Test
    fun coreCreationFails_endsInFailedStateWithoutTouchingStore() =
        runTest(dispatcher) {
            var storeRequested = false
            val viewModel = startupWith(CoreException.Cancelled()) { storeRequested = true }

            assertEquals(StartupState.Failed(R.string.error_cancelled), viewModel.state.value)
            assertFalse(storeRequested)
        }

    @Test
    fun unsupportedSchema_namesTheReason() =
        runTest(dispatcher) {
            val viewModel = startupWith(CoreException.UnsupportedSchema(4, 3))

            assertEquals(StartupState.Failed(R.string.error_unsupported_schema), viewModel.state.value)
        }

    @Test
    fun otherFailure_hasNoSpecificReason() =
        runTest(dispatcher) {
            val viewModel = startupWith(IllegalStateException("kaputt"))

            assertEquals(StartupState.Failed(null), viewModel.state.value)
        }

    @Test
    fun settingsLoadFails_endsInFailedStateAndNeverSavesDefaults() =
        runTest(dispatcher) {
            val core = FakeCore().apply { loadError = CoreException.Database("x") }
            val store = storeFor(core)

            val viewModel = AppViewModel({}, { store })
            runCurrent()

            assertEquals(StartupState.Failed(R.string.error_database), viewModel.state.value)
            assertFalse(store.loaded.value)
            store.update { it.copy(introSeen = true) }
            store.flush()
            assertEquals(0, core.savedSettings.size)
        }
}
