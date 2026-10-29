package de.crunchymate.update

import de.crunchymate.core.CoreGateway
import de.crunchymate.core.FakeCore
import de.crunchymate.core.SAVE_DEBOUNCE
import de.crunchymate.core.SettingsStore
import de.crunchymate.core.ffi.CoreException
import de.crunchymate.core.ffi.UpdateChannel
import de.crunchymate.core.ffi.UpdateInfo
import de.crunchymate.core.sampleSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val SDK = 35
private const val NOW = 1_800_000_000L
private const val CURRENT_BUILD = "56"
private val NEWER = UpdateInfo.Nightly(57L, "2026-10-30")

// Robolectric stellt android.util.Log bereit, das der Fehlerpfad aufruft
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [SDK])
@OptIn(ExperimentalCoroutinesApi::class)
class UpdateCheckerTest {
    private class Fixture(
        val core: FakeCore,
        val store: SettingsStore,
        val checker: UpdateChecker,
        val notified: MutableList<UpdateInfo>,
    )

    private suspend fun TestScope.fixture(
        settings: de.crunchymate.core.ffi.Settings,
        channel: UpdateChannel? = UpdateChannel.NIGHTLY,
    ): Fixture {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val core = FakeCore(storedSettings = settings).apply { updateResult = NEWER }
        val store = SettingsStore(CoreGateway(core, dispatcher), this, dispatcher)
        store.load()
        val notified = mutableListOf<UpdateInfo>()
        val checker = UpdateChecker(CoreGateway(core, dispatcher), store, channel, CURRENT_BUILD, { notified += it }, { NOW })
        return Fixture(core, store, checker, notified)
    }

    @Test
    fun switchOff_doesNotAsk() =
        runTest {
            val f = fixture(sampleSettings(checkUpdates = false))
            f.checker.checkIfDue()
            runCurrent()
            assertEquals(0, f.core.updateCalls.size)
            assertEquals(emptyList<UpdateInfo>(), f.notified)
        }

    @Test
    fun devBuild_neverAsks() =
        runTest {
            val f = fixture(sampleSettings(checkUpdates = true), channel = null)
            f.checker.checkIfDue()
            runCurrent()
            assertEquals(0, f.core.updateCalls.size)
        }

    @Test
    fun dueAndNewer_notifiesAndStoresTime() =
        runTest {
            val f = fixture(sampleSettings(checkUpdates = true))
            f.checker.checkIfDue()
            advanceTimeBy(SAVE_DEBOUNCE + 1)
            runCurrent()
            assertEquals(listOf(UpdateChannel.NIGHTLY to CURRENT_BUILD), f.core.updateCalls)
            assertEquals(listOf<UpdateInfo>(NEWER), f.notified)
            assertEquals(
                NOW,
                f.core.savedSettings
                    .last()
                    .lastUpdateCheck,
            )
        }

    @Test
    fun checkedWithinInterval_doesNotAsk() =
        runTest {
            val recent = NOW - UPDATE_CHECK_INTERVAL_SECONDS + 1
            val f = fixture(sampleSettings(checkUpdates = true, lastUpdateCheck = recent))
            f.checker.checkIfDue()
            runCurrent()
            assertEquals(0, f.core.updateCalls.size)
        }

    @Test
    fun checkedExactlyOneIntervalAgo_asksAgain() =
        runTest {
            val f = fixture(sampleSettings(checkUpdates = true, lastUpdateCheck = NOW - UPDATE_CHECK_INTERVAL_SECONDS))
            f.checker.checkIfDue()
            runCurrent()
            assertEquals(1, f.core.updateCalls.size)
        }

    @Test
    fun noNewerBuild_storesTimeWithoutNotice() =
        runTest {
            val f = fixture(sampleSettings(checkUpdates = true))
            f.core.updateResult = null
            f.checker.checkIfDue()
            runCurrent()
            assertEquals(emptyList<UpdateInfo>(), f.notified)
            assertEquals(NOW, f.store.settings.value.lastUpdateCheck)
        }

    @Test
    fun failure_isSilent_andKeepsTimeSoNextStartRetries() =
        runTest {
            val f = fixture(sampleSettings(checkUpdates = true))
            f.core.updateError = CoreException.Network("offline")
            f.checker.checkIfDue()
            runCurrent()
            assertEquals(emptyList<UpdateInfo>(), f.notified)
            assertEquals(0L, f.store.settings.value.lastUpdateCheck)

            f.core.updateError = null
            f.checker.checkIfDue()
            runCurrent()
            assertEquals(listOf<UpdateInfo>(NEWER), f.notified)
        }

    @Test
    fun channelNames_mapToChannels() {
        assertEquals(UpdateChannel.NIGHTLY, updateChannelOf("nightly"))
        assertEquals(UpdateChannel.RELEASE, updateChannelOf("release"))
        assertNull(updateChannelOf("dev"))
    }
}
