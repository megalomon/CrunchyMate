@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package de.crunchymate.core

import androidx.paging.PagingConfig
import androidx.paging.PagingSource.LoadParams
import androidx.paging.PagingSource.LoadResult
import androidx.paging.PagingState
import de.crunchymate.core.ffi.AnimeSummary
import de.crunchymate.core.ffi.CoreException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimePagingSourceTest {
    @Test
    fun pagingSource_returnsNextKeyUntilTotalReached() =
        runTest {
            val core = FakeCore(items = (0 until 120).map { sampleSummary("$it") })
            val source = AnimePagingSource(CoreGateway(core, UnconfinedTestDispatcher(testScheduler)), sampleFilter())

            val keys = mutableListOf<Int?>()
            val first = source.load(LoadParams.Refresh(0, PAGE_SIZE, false)) as LoadResult.Page
            assertNull(first.prevKey)
            var key: Int? = first.nextKey
            keys += key
            while (key != null) {
                val result = source.load(LoadParams.Append(key, PAGE_SIZE, false)) as LoadResult.Page
                keys += result.nextKey
                key = result.nextKey
            }
            assertEquals(listOf<Int?>(50, 100, null), keys)
            assertEquals(listOf(0, 50, 100), core.queryCalls.map { it.second })
            assertTrue(core.queryCalls.all { it.third == PAGE_SIZE })
        }

    @Test
    fun pagingSource_errorBecomesLoadResultError() =
        runTest {
            val core = FakeCore().apply { queryError = CoreException.Cancelled() }
            val source = AnimePagingSource(CoreGateway(core, UnconfinedTestDispatcher(testScheduler)), sampleFilter())
            val result = source.load(LoadParams.Refresh(0, PAGE_SIZE, false))
            assertTrue(result is LoadResult.Error)
            assertTrue((result as LoadResult.Error).throwable is CoreException.Cancelled)
        }

    @Test
    fun getRefreshKey_withoutAnchorIsNull() {
        val source = AnimePagingSource(CoreGateway(FakeCore(), Dispatchers.Unconfined), sampleFilter())
        val page = LoadResult.Page<Int, AnimeSummary>(emptyList(), null, null)
        assertNull(source.getRefreshKey(PagingState(listOf(page), null, PagingConfig(PAGE_SIZE), 0)))
    }

    @Test
    fun getRefreshKey_centersOnAnchor() {
        val source = AnimePagingSource(CoreGateway(FakeCore(), Dispatchers.Unconfined), sampleFilter())
        val config = PagingConfig(PAGE_SIZE)
        val page = LoadResult.Page<Int, AnimeSummary>((0 until 300).map { sampleSummary("$it") }, null, null, 0, 0)
        val key = source.getRefreshKey(PagingState(listOf(page), 200, config, 0))
        assertEquals(200 - config.initialLoadSize / 2, key)
        assertEquals(0, AnimePagingSource.refreshOffset(anchor = 10, initialLoadSize = config.initialLoadSize))
    }

    @Test
    fun loadAtOffset_returnsPrevKeyAndPlaceholderCounts() =
        runTest {
            val core = FakeCore(items = (0 until 120).map { sampleSummary("$it") })
            val source = AnimePagingSource(CoreGateway(core, UnconfinedTestDispatcher(testScheduler)), sampleFilter())

            val result = source.load(LoadParams.Refresh(60, PAGE_SIZE, false)) as LoadResult.Page
            assertEquals(60, result.prevKey)
            assertEquals(60, result.itemsBefore)
            assertEquals(10, result.itemsAfter)
        }

    @Test
    fun prepend_loadsOnlyItemsBeforeKeyWithoutOverlap() =
        runTest {
            val core = FakeCore(items = (0 until 120).map { sampleSummary("$it") })
            val source = AnimePagingSource(CoreGateway(core, UnconfinedTestDispatcher(testScheduler)), sampleFilter())

            val partial = source.load(LoadParams.Prepend(20, PAGE_SIZE, false)) as LoadResult.Page
            assertEquals((0 until 20).map { "$it" }, partial.data.map { it.id })
            assertNull(partial.prevKey)

            val full = source.load(LoadParams.Prepend(100, PAGE_SIZE, false)) as LoadResult.Page
            assertEquals((50 until 100).map { "$it" }, full.data.map { it.id })
            assertEquals(50, full.prevKey)
        }

    @Test
    fun duplicateIdsWithinOneGeneration_areDropped() =
        runTest {
            // Während eines Syncs verschiebt sich der Katalog: "b" erscheint auf zwei Seiten
            val core = FakeCore(items = listOf("a", "b", "c", "b", "d").map { sampleSummary(it) })
            val source = AnimePagingSource(CoreGateway(core, UnconfinedTestDispatcher(testScheduler)), sampleFilter())

            val first = source.load(LoadParams.Refresh(0, 3, false)) as LoadResult.Page
            val second = source.load(LoadParams.Append(3, 3, false)) as LoadResult.Page

            assertEquals(listOf("a", "b", "c"), first.data.map { it.id })
            assertEquals(listOf("d"), second.data.map { it.id })
            assertNull(second.nextKey)
        }

    @Test
    fun pagingSource_emptyPageDespiteTotalEndsPaging() =
        runTest {
            // total 120, aber ab Offset 60 liefert der Kern nichts mehr (Katalog geschrumpft)
            val core = FakeCore(items = (0 until 60).map { sampleSummary("$it") }, totalOverride = 120L)
            val source = AnimePagingSource(CoreGateway(core, UnconfinedTestDispatcher(testScheduler)), sampleFilter())
            val result = source.load(LoadParams.Refresh(60, PAGE_SIZE, false)) as LoadResult.Page
            assertNull(result.nextKey)
        }
}
