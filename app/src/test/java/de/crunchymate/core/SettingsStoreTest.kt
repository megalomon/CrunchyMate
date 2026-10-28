package de.crunchymate.core

import de.crunchymate.core.ffi.CoreException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsStoreTest {
    @Test
    fun settingsStore_updateIsVisibleImmediately_andSavedOnceAfterDebounce() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val core = FakeCore()
            val store = SettingsStore(CoreGateway(core, dispatcher), this, dispatcher)

            store.update { it.copy(contentLocale = "en-US") }
            assertEquals("en-US", store.settings.value.contentLocale)
            advanceTimeBy(100)
            store.update { it.copy(contentLocale = "fr-FR") }
            assertEquals("fr-FR", store.settings.value.contentLocale)

            advanceTimeBy(499)
            runCurrent()
            assertEquals(0, core.savedSettings.size)

            advanceTimeBy(2)
            runCurrent()
            assertEquals(1, core.savedSettings.size)
            assertEquals("fr-FR", core.savedSettings.single().contentLocale)
        }

    @Test
    fun saveFailure_keepsStateAndLaterUpdateSavesAgain() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val core = FakeCore().apply { saveError = CoreException.Database("x") }
            val store = SettingsStore(CoreGateway(core, dispatcher), this, dispatcher)

            store.update { it.copy(contentLocale = "en-US") }
            advanceTimeBy(SAVE_DEBOUNCE + 1)
            runCurrent()
            assertEquals(0, core.savedSettings.size)
            assertEquals("en-US", store.settings.value.contentLocale)

            core.saveError = null
            store.update { it.copy(contentLocale = "fr-FR") }
            advanceTimeBy(SAVE_DEBOUNCE + 1)
            runCurrent()
            assertEquals("fr-FR", core.savedSettings.single().contentLocale)
        }

    @Test
    fun flush_persistsPendingChangeImmediately_andDebounceDoesNotSaveTwice() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val core = FakeCore()
            val store = SettingsStore(CoreGateway(core, dispatcher), this, dispatcher)

            store.update { it.copy(contentLocale = "en-US") }
            store.flush()
            assertEquals("en-US", core.savedSettings.single().contentLocale)

            advanceTimeBy(SAVE_DEBOUNCE + 1)
            runCurrent()
            assertEquals(1, core.savedSettings.size)
        }

    @Test
    fun flush_withoutPendingChange_savesNothing() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val core = FakeCore()
            val store = SettingsStore(CoreGateway(core, dispatcher), this, dispatcher)

            store.flush()
            assertEquals(0, core.savedSettings.size)
        }

    @Test
    fun load_replacesDefaultWithStoredSettings() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val core = FakeCore(storedSettings = sampleSettings("en-US"))
            val store = SettingsStore(CoreGateway(core, dispatcher), this, dispatcher)
            assertEquals(sampleSettings("de-DE"), store.settings.value)
            assertEquals(false, store.loaded.value)
            store.load()
            assertEquals("en-US", store.settings.value.contentLocale)
            assertEquals(true, store.loaded.value)
        }
}
