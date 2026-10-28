package de.crunchymate.sync

import de.crunchymate.db.AnimeRepository
import de.crunchymate.db.FakeDatabaseDelegate
import de.crunchymate.db.FakeRow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class SyncForegroundServiceTest {

    private lateinit var fakeDb: FakeDatabaseDelegate
    private lateinit var repository: AnimeRepository
    private lateinit var fakeTransport: FakeHttpTransport
    private lateinit var crClient: CrunchyrollClient
    private lateinit var aniClient: AniListClient
    private lateinit var syncManager: SyncManager

    @Before
    fun setUp() {
        fakeDb = FakeDatabaseDelegate()
        repository = AnimeRepository(fakeDb)
        fakeTransport = FakeHttpTransport()
        crClient = CrunchyrollClient(fakeTransport, "https://test.cr.com/auth", "https://test.cr.com/browse")
        aniClient = AniListClient(fakeTransport, "https://test.anilist.co")
        syncManager = SyncManager(repository, crClient, aniClient, crunchyrollThrottleDelayMs = 0L, aniListThrottleDelayMs = 0L)
        SyncForegroundService.clearProgressListeners()
    }

    @Test
    fun testConstantsAndChannelConfiguration() {
        assertEquals("crunchymate_sync", SyncForegroundService.CHANNEL_ID)
        assertEquals("CrunchyMate Sync", SyncForegroundService.CHANNEL_NAME)
        assertEquals(1001, SyncForegroundService.NOTIFICATION_ID)

        assertEquals("de.crunchymate.sync.action.START_CR_SYNC", SyncForegroundService.ACTION_START_CR_SYNC)
        assertEquals("de.crunchymate.sync.action.START_ANI_SYNC", SyncForegroundService.ACTION_START_ANI_SYNC)
        assertEquals("de.crunchymate.sync.action.CANCEL_SYNC", SyncForegroundService.ACTION_CANCEL_SYNC)
        assertEquals("de.crunchymate.sync.action.CANCEL_CR", SyncForegroundService.ACTION_CANCEL_CR)
        assertEquals("de.crunchymate.sync.action.CANCEL_ANI", SyncForegroundService.ACTION_CANCEL_ANI)

        assertEquals("de.crunchymate.sync.extra.LIMIT", SyncForegroundService.EXTRA_LIMIT)

        assertEquals("de.crunchymate.sync.action.SYNC_PROGRESS", SyncForegroundService.ACTION_SYNC_PROGRESS)
        assertEquals("phase", SyncForegroundService.EXTRA_PHASE)
        assertEquals("synced", SyncForegroundService.EXTRA_SYNCED)
        assertEquals("total", SyncForegroundService.EXTRA_TOTAL)
        assertEquals("is_running", SyncForegroundService.EXTRA_IS_RUNNING)
        assertEquals("error", SyncForegroundService.EXTRA_ERROR)
    }

    @Test
    fun testProgressFormatting() {
        assertEquals(
            "Crunchyroll: 45 / 1200 Serien",
            SyncForegroundService.formatCrunchyrollProgress(45, 1200)
        )
        assertEquals(
            "Crunchyroll: 10 Serien geladen",
            SyncForegroundService.formatCrunchyrollProgress(10, 0)
        )
        assertEquals(
            "AniList: 15 / 200 angereichert",
            SyncForegroundService.formatAniListProgress(15, 200)
        )
        assertEquals(
            "AniList: 5 angereichert",
            SyncForegroundService.formatAniListProgress(5, 0)
        )
    }

    @Test
    fun testProgressListenerManagement() {
        val calledPhase = StringBuilder()
        val calledSynced = AtomicInteger(0)
        val calledTotal = AtomicInteger(0)
        val calledRunning = AtomicBoolean(false)

        val listener = SyncForegroundService.SyncProgressListener { phase, synced, total, isRunning, _ ->
            calledPhase.setLength(0)
            calledPhase.append(phase.toString())
            calledSynced.set(synced)
            calledTotal.set(total)
            calledRunning.set(isRunning)
        }

        SyncForegroundService.addProgressListener(listener)
        SyncForegroundService.notifyProgressListeners("crunchyroll", 50, 100, true, null)

        assertEquals("crunchyroll", calledPhase.toString())
        assertEquals(50, calledSynced.get())
        assertEquals(100, calledTotal.get())
        assertTrue(calledRunning.get())

        // Test remove listener
        SyncForegroundService.removeProgressListener(listener)
        SyncForegroundService.notifyProgressListeners("anilist", 20, 40, false, null)

        // Should not have updated
        assertEquals("crunchyroll", calledPhase.toString())
        assertEquals(50, calledSynced.get())
    }

    @Test
    fun testActionRoutingTriggersCrunchyrollSync() = runBlocking {
        fakeTransport.responder = { url, _, _, _ ->
            if (url.contains("/auth")) {
                HttpResponse(200, emptyMap(), """{"access_token": "token-1", "expires_in": 3600}""")
            } else {
                HttpResponse(
                    200,
                    emptyMap(),
                    """{"total": 1, "data": [{"id": "CR1", "title": "Series 1", "images": {}}]}"""
                )
            }
        }

        val service = SyncForegroundService()
        service.syncManager = syncManager

        val handled = service.handleAction(SyncForegroundService.ACTION_START_CR_SYNC, limit = 1)
        assertTrue(handled)
        assertTrue(
            syncManager.isCrSyncRunning() ||
            syncManager.getCrSyncStatus().getInt("synced_count") >= 1 ||
            syncManager.getCrSyncStatus().getString("status") != ""
        )

        // Wait for sync to complete
        while (syncManager.isCrSyncRunning()) {
            kotlinx.coroutines.delay(10)
        }

        assertEquals(1, syncManager.getCrSyncStatus().getInt("synced_count"))
    }

    @Test
    fun testActionRoutingTriggersAniListSync() = runBlocking {
        fakeDb.animeRows = listOf(
            FakeRow(mapOf("id" to "CR1", "title" to "Anime 1", "anilist_id" to null))
        )
        fakeDb.statsMap = mapOf("total_anime" to 1, "anilist_enriched" to 0)

        fakeTransport.responder = { _, _, _, _ ->
            HttpResponse(
                200,
                emptyMap(),
                """{"data": {"a0": {"media": [{"id": 101, "title": {"romaji": "Anime 1"}}]}}}"""
            )
        }

        val service = SyncForegroundService()
        service.syncManager = syncManager

        val handled = service.handleAction(SyncForegroundService.ACTION_START_ANI_SYNC)
        assertTrue(handled)
        assertTrue(
            syncManager.isAniListSyncRunning() ||
            syncManager.getAniListSyncStatus().getInt("enriched_count") >= 1 ||
            syncManager.getAniListSyncStatus().getString("status") != ""
        )

        while (syncManager.isAniListSyncRunning()) {
            kotlinx.coroutines.delay(10)
        }

        assertEquals(1, syncManager.getAniListSyncStatus().getInt("enriched_count"))
    }

    @Test
    fun testActionRoutingCancelStopsSync() = runBlocking {
        fakeTransport.responder = { url, _, _, _ ->
            if (url.contains("/auth")) {
                HttpResponse(200, emptyMap(), """{"access_token": "token-1", "expires_in": 3600}""")
            } else {
                HttpResponse(
                    200,
                    emptyMap(),
                    """{"total": 1000, "data": [{"id": "CR1", "title": "Series 1", "images": {}}]}"""
                )
            }
        }

        val service = SyncForegroundService()
        service.syncManager = syncManager

        service.handleAction(SyncForegroundService.ACTION_START_CR_SYNC)
        assertTrue(syncManager.isCrSyncRunning())

        val handledCancel = service.handleAction(SyncForegroundService.ACTION_CANCEL_SYNC)
        assertTrue(handledCancel)

        // Wait a bit
        kotlinx.coroutines.delay(50)
        assertFalse(syncManager.isCrSyncRunning())
    }

    @Test
    fun testStartForegroundCalledOnEveryStartInvocationEvenIfAlreadyActive() {
        val service = SyncForegroundService()
        assertEquals(0, service.startForegroundCount)
        assertFalse(service.isForegroundActive)

        service.startForegroundServiceIfNeeded("Crunchyroll-Katalog")
        assertEquals(1, service.startForegroundCount)
        assertTrue(service.isForegroundActive)

        // Second call while already foreground: MUST still call startForeground without skipping
        service.startForegroundServiceIfNeeded("AniList-Metadaten")
        assertEquals(2, service.startForegroundCount)
        assertTrue(service.isForegroundActive)
    }

    @Test
    fun testCancelWhenIdleBroadcastsActualStatusNotZeroZero() {
        fakeDb.statsMap = mapOf("total_anime" to 120, "anilist_enriched" to 45)
        repository.updateSyncState(
            de.crunchymate.db.SyncState(
                key = "crunchyroll",
                totalItems = 250,
                syncedItems = 150,
                status = "idle"
            )
        )
        repository.updateSyncState(
            de.crunchymate.db.SyncState(
                key = "anilist",
                totalItems = 120,
                syncedItems = 45,
                status = "idle"
            )
        )

        val receivedEvents = mutableListOf<String>()
        val listener = SyncForegroundService.SyncProgressListener { phase, synced, total, isRunning, _ ->
            receivedEvents.add("$phase:$synced/$total:running=$isRunning")
        }
        SyncForegroundService.addProgressListener(listener)

        val service = SyncForegroundService()
        service.syncManager = syncManager

        service.handleAction(SyncForegroundService.ACTION_CANCEL_CR)
        assertTrue(receivedEvents.any { it.contains("crunchyroll:150/250:running=false") })
        assertFalse("Should never broadcast 0/0 when real items exist", receivedEvents.any { it.contains("crunchyroll:0/0") })

        receivedEvents.clear()
        service.handleAction(SyncForegroundService.ACTION_CANCEL_ANI)
        assertTrue(receivedEvents.any { it.contains("anilist:45/120:running=false") })
        assertFalse("Should never broadcast 0/0 when real items exist", receivedEvents.any { it.contains("anilist:0/0") })
    }

    @Test
    fun testCancelRunningSyncBroadcastsActualProgressFromSyncManager() = runBlocking {
        fakeTransport.responder = { url, _, _, _ ->
            if (url.contains("/auth")) {
                HttpResponse(200, emptyMap(), """{"access_token": "token-1", "expires_in": 3600}""")
            } else {
                HttpResponse(
                    200,
                    emptyMap(),
                    """{"total": 500, "data": [{"id": "CR1", "title": "Series 1", "images": {}}]}"""
                )
            }
        }

        val receivedProgress = mutableListOf<String>()
        val listener = SyncForegroundService.SyncProgressListener { phase, synced, total, isRunning, _ ->
            receivedProgress.add("$phase:$synced/$total:running=$isRunning")
        }
        SyncForegroundService.addProgressListener(listener)

        val service = SyncForegroundService()
        service.syncManager = syncManager

        service.handleAction(SyncForegroundService.ACTION_START_CR_SYNC)
        // Give time for first page to be processed
        var count = 0
        while (syncManager.getCrSyncStatus().getInt("synced_count") < 1 && count < 50) {
            kotlinx.coroutines.delay(10)
            count++
        }

        // Cancel while running
        service.handleAction(SyncForegroundService.ACTION_CANCEL_CR)

        // Wait until sync is completely stopped
        while (syncManager.isCrSyncRunning()) {
            kotlinx.coroutines.delay(10)
        }
        kotlinx.coroutines.delay(50)

        // None of the cancel broadcasts should be dummy 0/0
        val finalEvents = receivedProgress.filter { it.contains("running=false") }
        assertTrue("Expected at least one completion event", finalEvents.isNotEmpty())
        for (event in finalEvents) {
            assertFalse("Dummy 0/0 must not be broadcast upon cancellation: $event", event.contains("crunchyroll:0/0"))
        }
    }

    @Test
    fun testNotificationManagerLazyFallbackAndSetter() {
        val service = SyncForegroundService()
        // Accessing notificationManager before onCreate must not crash
        service.notificationManager
        // Setting custom value works
        service.notificationManager = null
        assertNull(service.notificationManager)
    }

    @Test
    fun testSafeListenersCleanupAndExceptionTolerance() {
        val reached = AtomicBoolean(false)
        val explodingListener = SyncForegroundService.SyncProgressListener { _, _, _, _, _ ->
            throw RuntimeException("Exploding listener should not crash notification loop")
        }
        val safeListener = SyncForegroundService.SyncProgressListener { _, _, _, _, _ ->
            reached.set(true)
        }

        SyncForegroundService.addProgressListener(explodingListener)
        SyncForegroundService.addProgressListener(safeListener)

        // Must not throw exception
        SyncForegroundService.notifyProgressListeners("crunchyroll", 10, 20, true, null)
        assertTrue(reached.get())

        SyncForegroundService.clearProgressListeners()
    }
}
