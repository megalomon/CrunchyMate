package de.crunchymate.ui.licenses

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.FileNotFoundException

private const val SDK = 35

// Robolectric stellt android.util.Log bereit, das die Fehlerpfade aufrufen
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [SDK])
@OptIn(ExperimentalCoroutinesApi::class)
class LicensesViewModelTest {
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
    fun load_success_emitsLoaded() =
        runTest(dispatcher) {
            val notices = OpenSourceNotices(emptyList(), emptyList())
            val viewModel = LicensesViewModel { notices }
            assertEquals(LicensesUiState.Loading, viewModel.uiState.value)
            runCurrent()
            assertEquals(LicensesUiState.Loaded(notices), viewModel.uiState.value)
        }

    @Test
    fun load_failure_emitsFailed() =
        runTest(dispatcher) {
            val viewModel = LicensesViewModel { throw FileNotFoundException() }
            runCurrent()
            assertEquals(LicensesUiState.Failed, viewModel.uiState.value)
        }
}
