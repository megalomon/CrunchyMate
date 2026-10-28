@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package de.crunchymate.core

import androidx.paging.PagingSource
import androidx.paging.PagingSource.LoadParams
import androidx.paging.PagingSource.LoadResult
import de.crunchymate.core.ffi.CoreException
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
            var key: Int? = 0
            while (key != null) {
                val result = source.load(LoadParams.Refresh(key, PAGE_SIZE, false)) as LoadResult.Page
                assertNull(result.prevKey)
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
    fun getRefreshKey_restartsAtZero() {
        val core = FakeCore()
        val source = AnimePagingSource(CoreGateway(core, kotlinx.coroutines.Dispatchers.Unconfined), sampleFilter())
        val state = PagingSource.LoadResult.Page<Int, de.crunchymate.core.ffi.AnimeSummary>(emptyList(), null, null)
        assertNull(source.getRefreshKey(androidx.paging.PagingState(listOf(state), null, androidx.paging.PagingConfig(PAGE_SIZE), 0)))
    }
}
