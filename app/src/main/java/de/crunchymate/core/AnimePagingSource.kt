package de.crunchymate.core

import androidx.paging.PagingSource
import androidx.paging.PagingState
import de.crunchymate.core.ffi.AnimeFilter
import de.crunchymate.core.ffi.AnimeSummary
import kotlin.coroutines.cancellation.CancellationException

const val PAGE_SIZE = 50

/** Seitenweises Laden des Katalogs; der Schlüssel ist der Offset ab 0. */
class AnimePagingSource(
    private val gateway: CoreGateway,
    private val filter: AnimeFilter,
) : PagingSource<Int, AnimeSummary>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, AnimeSummary> {
        val offset = params.key ?: 0
        return try {
            val page = gateway.queryAnime(filter, offset, params.loadSize)
            val next = offset + page.items.size
            LoadResult.Page(
                data = page.items,
                prevKey = null,
                nextKey = if (page.items.isNotEmpty() && next < page.total) next else null,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    // Nach einer Invalidierung beginnt die Liste wieder bei 0
    override fun getRefreshKey(state: PagingState<Int, AnimeSummary>): Int? = null
}
