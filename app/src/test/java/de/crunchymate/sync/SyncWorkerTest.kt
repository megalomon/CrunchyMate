package de.crunchymate.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import de.crunchymate.core.CoreGateway
import de.crunchymate.core.FakeCore
import de.crunchymate.core.ffi.CoreException
import de.crunchymate.core.ffi.SyncKind
import de.crunchymate.core.ffi.SyncPhase
import de.crunchymate.core.ffi.SyncProgress
import de.crunchymate.core.ffi.SyncSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val SDK = 35
private const val RETRY_AFTER_SECS = 30L

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [SDK])
class SyncWorkerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val core = FakeCore()
    private val gateway = CoreGateway(core, Dispatchers.IO)

    @Before
    fun setUp() {
        initTestWorkManager(context, gateway)
    }

    private fun worker(kind: SyncKind): SyncWorker =
        TestListenableWorkerBuilder<SyncWorker>(context)
            .setInputData(workDataOf(SyncWorker.KEY_KIND to kind.name))
            .setWorkerFactory(TestSyncWorkerFactory(gateway))
            .build()

    @Test
    fun worker_success_returnsSuccess() =
        runBlocking {
            val kinds = mutableListOf<SyncKind>()
            core.syncBehavior = { kind, observer ->
                kinds += kind
                observer.onProgress(SyncProgress(kind, SyncPhase.ITEMS, 1L, 2L))
                SyncSummary(2L, 0, 0, 0)
            }

            val result = worker(SyncKind.METADATA).doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            assertEquals(listOf(SyncKind.METADATA), kinds)
        }

    @Test
    fun worker_coreException_returnsFailureWithCode() =
        runBlocking {
            core.syncBehavior = { _, _ -> throw CoreException.RateLimited(RETRY_AFTER_SECS) }

            val result = worker(SyncKind.CATALOG).doWork()

            assertTrue(result is ListenableWorker.Result.Failure)
            assertEquals("RATE_LIMITED", result.outputData.getString(SyncWorker.KEY_ERROR))
        }

    @Test
    fun worker_unexpectedException_returnsFailureWithInternalCode() =
        runBlocking {
            // So meldet UniFFI eine Rust-Panic: keine CoreException
            core.syncBehavior = { _, _ -> throw IllegalStateException("Panic im Kern") }

            val result = worker(SyncKind.CATALOG).doWork()

            assertTrue(result is ListenableWorker.Result.Failure)
            assertEquals("INTERNAL", result.outputData.getString(SyncWorker.KEY_ERROR))
        }

    @Test
    fun shouldPostUpdate_requiresForegroundAndEnabledNotifications() {
        assertTrue(shouldPostUpdate(foregroundStarted = true, notificationsEnabled = true))
        assertFalse(shouldPostUpdate(foregroundStarted = false, notificationsEnabled = true))
        assertFalse(shouldPostUpdate(foregroundStarted = true, notificationsEnabled = false))
    }
}
