package de.crunchymate.core

import androidx.paging.PagingSource
import androidx.paging.PagingState
import de.crunchymate.core.ffi.AnimeFilter
import de.crunchymate.core.ffi.AnimeSummary
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException

const val PAGE_SIZE = 50

/**
 * Seitenweises Laden des Katalogs über Offsets ab 0.
 *
 * Schlüssel: Bei Refresh und Append ist er der Start-Offset der Seite, bei Prepend das
 * exklusive Ende (= Start der bereits geladenen Seite). So überlappen vorangestellte Seiten
 * nie mit geladenen, auch wenn der Refresh nicht auf einer Seitengrenze begann.
 */
class AnimePagingSource(
    private val gateway: CoreGateway,
    private val filter: AnimeFilter,
) : PagingSource<Int, AnimeSummary>() {
    // Offset-Paging kann während eines Syncs dieselbe Serie auf zwei Seiten liefern;
    // doppelte IDs würden die Schlüssel im Grid verletzen. Prepend und Append laufen parallel.
    private val seenIds: MutableSet<String> = ConcurrentHashMap.newKeySet()

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, AnimeSummary> {
        val (start, limit) =
            when (params) {
                is LoadParams.Prepend -> {
                    val end = params.key
                    val first = maxOf(0, end - params.loadSize)
                    first to (end - first)
                }
                is LoadParams.Refresh, is LoadParams.Append -> (params.key ?: 0) to params.loadSize
            }
        return try {
            val page = gateway.queryAnime(filter, start, limit)
            val end = start + page.items.size
            // Leere Seite trotz total > offset: Katalog ist geschrumpft, sonst Endlosladen
            LoadResult.Page(
                data = page.items.filter { seenIds.add(it.id) },
                prevKey = if (start > 0) start else null,
                nextKey = if (page.items.isNotEmpty() && end < page.total) end else null,
                itemsBefore = start,
                itemsAfter = maxOf(0L, page.total - end).toInt(),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    // Nach einer Invalidierung (jede Änderung, auch Sync-Batches) wird rund um die zuletzt
    // sichtbare Position neu geladen, damit die Liste nicht an den Anfang springt.
    override fun getRefreshKey(state: PagingState<Int, AnimeSummary>): Int? =
        state.anchorPosition?.let { anchor -> refreshOffset(anchor, state.config.initialLoadSize) }

    companion object {
        /** Start-Offset, bei dem die Refresh-Seite die Ankerposition mittig enthält. */
        fun refreshOffset(
            anchor: Int,
            initialLoadSize: Int,
        ): Int = maxOf(0, anchor - initialLoadSize / 2)
    }
}
