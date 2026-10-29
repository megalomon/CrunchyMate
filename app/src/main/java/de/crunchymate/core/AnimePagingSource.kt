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

    // Zuletzt gesehene Gesamtzahl; begrenzt den Refresh-Key, wenn der Katalog geschrumpft ist
    @Volatile
    private var knownTotal: Long? = null

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, AnimeSummary> {
        val (requestedStart, limit) =
            when (params) {
                is LoadParams.Prepend -> {
                    val end = params.key
                    val first = maxOf(0, end - params.loadSize)
                    first to (end - first)
                }

                is LoadParams.Refresh, is LoadParams.Append -> {
                    (params.key ?: 0) to params.loadSize
                }
            }
        return try {
            var start = requestedStart
            var page = gateway.queryAnime(filter, start, limit)
            // Ein Refresh hinter dem Ende (Katalog geschrumpft) landet sonst auf einer leeren Seite
            if (params is LoadParams.Refresh && start > 0 && page.items.isEmpty() && page.total > 0) {
                start = clampOffset(start, page.total, limit)
                page = gateway.queryAnime(filter, start, limit)
            }
            knownTotal = page.total
            val end = start + page.items.size
            val data = page.items.filter { seenIds.add(it.id) }
            // Verworfene Duplikate bleiben als Platzhalter erhalten, damit die Listenlänge der Gesamtzahl entspricht
            val dropped = page.items.size - data.size
            // Leere Seite trotz total > offset: Katalog ist geschrumpft, sonst Endlosladen
            LoadResult.Page(
                data = data,
                prevKey = if (start > 0) start else null,
                nextKey = if (page.items.isNotEmpty() && end < page.total) end else null,
                itemsBefore = start,
                itemsAfter = (maxOf(0L, page.total - end) + dropped).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
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
        state.anchorPosition?.let { anchor ->
            val offset = refreshOffset(anchor, state.config.initialLoadSize)
            knownTotal?.let { clampOffset(offset, it, state.config.initialLoadSize) } ?: offset
        }

    companion object {
        /** Start-Offset, bei dem die Refresh-Seite die Ankerposition mittig enthält. */
        fun refreshOffset(
            anchor: Int,
            initialLoadSize: Int,
        ): Int = maxOf(0, anchor - initialLoadSize / 2)

        /** Begrenzt den Start-Offset so, dass eine Seite der Größe [pageSize] noch in [total] Einträge passt. */
        fun clampOffset(
            offset: Int,
            total: Long,
            pageSize: Int,
        ): Int = offset.toLong().coerceIn(0L, maxOf(0L, total - pageSize)).toInt()
    }
}
