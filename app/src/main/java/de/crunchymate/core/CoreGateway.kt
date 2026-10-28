package de.crunchymate.core

import de.crunchymate.core.ffi.AnimeDetail
import de.crunchymate.core.ffi.AnimeFilter
import de.crunchymate.core.ffi.AnimePage
import de.crunchymate.core.ffi.CoreInterface
import de.crunchymate.core.ffi.NameCount
import de.crunchymate.core.ffi.Settings
import de.crunchymate.core.ffi.Stats
import de.crunchymate.core.ffi.SyncKind
import de.crunchymate.core.ffi.SyncObserver
import de.crunchymate.core.ffi.SyncStatus
import de.crunchymate.core.ffi.SyncSummary
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext

/**
 * Einziger Kotlin-Einstiegspunkt zum Rust-Kern. Alle Aufrufe laufen auf dem
 * übergebenen IO-Dispatcher; Änderungen am Datenbestand werden über [changes] gemeldet.
 */
class CoreGateway(
    private val core: CoreInterface,
    private val io: CoroutineDispatcher,
) {
    private val _changes =
        MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Meldet, dass sich Katalog- oder Statusdaten geändert haben. */
    val changes: SharedFlow<Unit> = _changes.asSharedFlow()

    suspend fun queryAnime(
        filter: AnimeFilter,
        offset: Int,
        limit: Int,
    ): AnimePage = withContext(io) { core.queryAnime(filter, offset, limit) }

    suspend fun getAnime(id: String): AnimeDetail? = withContext(io) { core.getAnime(id) }

    suspend fun setWatched(
        id: String,
        watched: Boolean,
    ) {
        withContext(io) { core.setWatched(id, watched) }
        notifyChanged()
    }

    suspend fun setMeh(
        id: String,
        meh: Boolean,
    ) {
        withContext(io) { core.setMeh(id, meh) }
        notifyChanged()
    }

    suspend fun genres(): List<NameCount> = withContext(io) { core.listGenres() }

    suspend fun tags(): List<NameCount> = withContext(io) { core.listTags() }

    suspend fun stats(): Stats = withContext(io) { core.stats() }

    suspend fun syncStatus(kind: SyncKind): SyncStatus = withContext(io) { core.syncStatus(kind) }

    suspend fun syncedContentLocaleNow(): String? = syncStatus(SyncKind.CATALOG).contentLocale

    suspend fun loadSettings(): Settings = withContext(io) { core.settings() }

    suspend fun saveSettings(settings: Settings) {
        withContext(io) { core.saveSettings(settings) }
    }

    suspend fun supportedContentLocales(): List<String> = withContext(io) { core.supportedContentLocales() }

    suspend fun syncCatalog(observer: SyncObserver): SyncSummary = withContext(io) { core.syncCatalog(observer) }

    suspend fun enrichMetadata(observer: SyncObserver): SyncSummary = withContext(io) { core.enrichMetadata(observer) }

    fun notifyChanged() {
        _changes.tryEmit(Unit)
    }
}
