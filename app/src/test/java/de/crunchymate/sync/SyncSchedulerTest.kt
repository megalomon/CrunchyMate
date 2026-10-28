package de.crunchymate.sync

import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import de.crunchymate.core.CoreGateway
import de.crunchymate.core.FakeCore
import de.crunchymate.core.ffi.ErrorCode
import de.crunchymate.core.ffi.SyncKind
import de.crunchymate.core.ffi.SyncProgress
import de.crunchymate.core.ffi.SyncRunState
import de.crunchymate.core.ffi.SyncStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val SDK = 35
private const val TIMEOUT_MS = 10_000L
private const val FINISHED_AT = 1_700_000_000L

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [SDK])
class SyncSchedulerTest {
    private val core = FakeCore()
    private val gateway = CoreGateway(core, Dispatchers.IO)
    private lateinit var workManager: WorkManager
    private lateinit var scheduler: SyncScheduler

    @Before
    fun setUp() {
        // Läufe blockieren, bis der Test sie abbricht
        core.syncBehavior = { _, _ -> awaitCancellation() }
        workManager = initTestWorkManager(ApplicationProvider.getApplicationContext(), gateway)
        scheduler = SyncScheduler(workManager, gateway)
    }

    @After
    fun tearDown() {
        workManager.cancelAllWork().result.get()
    }

    private fun activeInfos(kind: SyncKind): List<WorkInfo> =
        workManager
            .getWorkInfosForUniqueWork("sync-${kind.name}")
            .get()
            .filter { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }

    @Test
    fun start_twice_enqueuesSingleWork() =
        runBlocking {
            scheduler.start(SyncKind.CATALOG)
            scheduler.start(SyncKind.CATALOG)

            assertEquals(1, workManager.getWorkInfosForUniqueWork("sync-CATALOG").get().size)
            assertEquals(1, activeInfos(SyncKind.CATALOG).size)
            withTimeout(TIMEOUT_MS) { scheduler.observe(SyncKind.CATALOG).first { it.running } }
            Unit
        }

    @Test
    fun catalogAndMetadata_canRunInParallel() =
        runBlocking {
            scheduler.start(SyncKind.CATALOG)
            scheduler.start(SyncKind.METADATA)

            assertEquals(1, activeInfos(SyncKind.CATALOG).size)
            assertEquals(1, activeInfos(SyncKind.METADATA).size)
            withTimeout(TIMEOUT_MS) { scheduler.anyRunning.first { it } }

            scheduler.cancel(SyncKind.CATALOG)
            withTimeout(TIMEOUT_MS) { scheduler.observe(SyncKind.CATALOG).first { !it.running } }
            assertTrue(scheduler.observe(SyncKind.METADATA).first().running)
            assertTrue(scheduler.anyRunning.first())
        }

    @Test
    fun observe_mapsProgressAndLastError() =
        runBlocking {
            core.syncStatuses =
                mapOf(
                    SyncKind.CATALOG to
                        SyncStatus(SyncKind.CATALOG, SyncRunState.FAILED, 3L, 40L, FINISHED_AT, ErrorCode.RATE_LIMITED, "DE"),
                )
            core.syncBehavior = { kind, observer ->
                observer.onProgress(SyncProgress(kind, 5L, 10L))
                awaitCancellation()
            }

            val idle = scheduler.observe(SyncKind.CATALOG).first()
            assertEquals(SyncUiState(false, 3L, 40L, FINISHED_AT, ErrorCode.RATE_LIMITED), idle)

            scheduler.start(SyncKind.CATALOG)
            val running = withTimeout(TIMEOUT_MS) { scheduler.observe(SyncKind.CATALOG).first { it.running && it.total == 10L } }
            assertEquals(SyncUiState(true, 5L, 10L, FINISHED_AT, ErrorCode.RATE_LIMITED), running)

            scheduler.cancel(SyncKind.CATALOG)
            val stopped = withTimeout(TIMEOUT_MS) { scheduler.observe(SyncKind.CATALOG).first { !it.running } }
            assertFalse(stopped.running)
            assertEquals(40L, stopped.total)
        }

    @Test
    fun catalogCountry_comesFromCatalogStatus() =
        runBlocking {
            core.syncStatuses =
                mapOf(SyncKind.CATALOG to SyncStatus(SyncKind.CATALOG, SyncRunState.COMPLETED, 0L, 0L, null, null, "DE"))

            assertEquals("DE", scheduler.catalogCountry.first())
        }

    @Test
    fun observe_showsErrorOnlyForFailedAndFlagsCancelled() =
        runBlocking {
            core.syncStatuses =
                mapOf(
                    SyncKind.CATALOG to
                        SyncStatus(SyncKind.CATALOG, SyncRunState.CANCELLED, 3L, 40L, FINISHED_AT, ErrorCode.CANCELLED, "DE"),
                    SyncKind.METADATA to
                        SyncStatus(SyncKind.METADATA, SyncRunState.FAILED, 1L, 2L, FINISHED_AT, ErrorCode.NETWORK, null),
                )

            val cancelled = scheduler.observe(SyncKind.CATALOG).first()
            assertEquals(SyncUiState(false, 3L, 40L, FINISHED_AT, null, cancelled = true), cancelled)
            val failed = scheduler.observe(SyncKind.METADATA).first()
            assertEquals(SyncUiState(false, 1L, 2L, FINISHED_AT, ErrorCode.NETWORK, cancelled = false), failed)
        }
}
