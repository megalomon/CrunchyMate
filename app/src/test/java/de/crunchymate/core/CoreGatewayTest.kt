@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package de.crunchymate.core

import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executors

class CoreGatewayTest {
    @Test
    fun setWatched_emitsChange() =
        runTest(UnconfinedTestDispatcher()) {
            val core = FakeCore()
            val gateway = CoreGateway(core, UnconfinedTestDispatcher(testScheduler))
            var received = 0
            val job = launch { gateway.changes.collect { received++ } }

            gateway.setWatched("a", true)
            gateway.setMeh("a", true)

            assertEquals(listOf("a" to true), core.watchedCalls)
            assertEquals(listOf("a" to true), core.mehCalls)
            assertEquals(2, received)
            job.cancel()
        }

    @Test
    fun syncCatalogAndEnrich_delegateToCore() =
        runTest(UnconfinedTestDispatcher()) {
            val core = FakeCore()
            val kinds = mutableListOf<de.crunchymate.core.ffi.SyncKind>()
            core.syncBehavior = { kind, _ ->
                kinds += kind
                de.crunchymate.core.ffi
                    .SyncSummary(0L, 0, 0, 0)
            }
            val gateway = CoreGateway(core, UnconfinedTestDispatcher(testScheduler))
            val observer =
                object : de.crunchymate.core.ffi.SyncObserver {
                    override fun onProgress(progress: de.crunchymate.core.ffi.SyncProgress) = Unit
                }

            gateway.syncCatalog(observer)
            gateway.enrichMetadata(observer)

            assertEquals(
                listOf(de.crunchymate.core.ffi.SyncKind.CATALOG, de.crunchymate.core.ffi.SyncKind.METADATA),
                kinds,
            )
        }

    @Test
    fun notifyChanged_emitsChange() =
        runTest(UnconfinedTestDispatcher()) {
            val gateway = CoreGateway(FakeCore(), UnconfinedTestDispatcher(testScheduler))
            var received = 0
            val job = launch { gateway.changes.collect { received++ } }
            gateway.notifyChanged()
            assertEquals(1, received)
            job.cancel()
        }

    @Test
    fun queryAnime_runsOnIoDispatcher() {
        val executor = Executors.newSingleThreadExecutor { Thread(it, "io-test") }
        try {
            val core = FakeCore(items = listOf(sampleSummary("a")))
            val gateway = CoreGateway(core, executor.asCoroutineDispatcher())
            val page = runBlocking { gateway.queryAnime(sampleFilter(), 0, 10) }
            assertEquals(1L, page.total)
            assertTrue(core.queryThreadName.orEmpty().startsWith("io-test"))
        } finally {
            executor.shutdown()
        }
    }

    @Test
    fun settings_roundTripThroughCore() =
        runTest {
            val core = FakeCore()
            val gateway = CoreGateway(core, UnconfinedTestDispatcher(testScheduler))
            val s = sampleSettings("en-US")
            gateway.saveSettings(s)
            assertEquals(s, gateway.loadSettings())
            assertTrue(core.savedSettings.size == 1)
            assertEquals(listOf("de-DE", "en-US"), gateway.supportedContentLocales())
            assertEquals("Action", gateway.genres().first().name)
            assertEquals("Magie", gateway.tags().first().name)
        }
}
