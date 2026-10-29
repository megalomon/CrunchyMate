package de.crunchymate.core

import de.crunchymate.core.ffi.AnimeDetail
import de.crunchymate.core.ffi.AnimeFilter
import de.crunchymate.core.ffi.AnimePage
import de.crunchymate.core.ffi.CoreException
import de.crunchymate.core.ffi.CoreInterface
import de.crunchymate.core.ffi.NameCount
import de.crunchymate.core.ffi.Settings
import de.crunchymate.core.ffi.Stats
import de.crunchymate.core.ffi.SyncKind
import de.crunchymate.core.ffi.SyncObserver
import de.crunchymate.core.ffi.SyncStatus
import de.crunchymate.core.ffi.SyncSummary
import de.crunchymate.core.ffi.UpdateChannel
import de.crunchymate.core.ffi.UpdateInfo
import kotlinx.coroutines.CancellationException
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
    ): AnimePage = guarded { core.queryAnime(filter, offset, limit) }

    suspend fun getAnime(id: String): AnimeDetail? = guarded { core.getAnime(id) }

    suspend fun setWatched(
        id: String,
        watched: Boolean,
    ) {
        guarded { core.setWatched(id, watched) }
        notifyChanged()
    }

    suspend fun setMeh(
        id: String,
        meh: Boolean,
    ) {
        guarded { core.setMeh(id, meh) }
        notifyChanged()
    }

    suspend fun genres(): List<NameCount> = guarded { core.listGenres() }

    suspend fun tags(): List<NameCount> = guarded { core.listTags() }

    suspend fun stats(): Stats = guarded { core.stats() }

    suspend fun syncStatus(kind: SyncKind): SyncStatus = guarded { core.syncStatus(kind) }

    suspend fun syncedContentLocaleNow(): String? = syncStatus(SyncKind.CATALOG).contentLocale

    suspend fun loadSettings(): Settings = guarded { core.settings() }

    suspend fun saveSettings(settings: Settings) {
        guarded { core.saveSettings(settings) }
    }

    suspend fun supportedContentLocales(): List<String> = guarded { core.supportedContentLocales() }

    /** Neuester Stand des Kanals, wenn er neuer ist als [currentBuild]; sonst null. */
    suspend fun checkForUpdate(
        channel: UpdateChannel,
        currentBuild: String,
    ): UpdateInfo? = withContext(io) { core.checkForUpdate(channel, currentBuild) }

    suspend fun syncCatalog(observer: SyncObserver): SyncSummary = withContext(io) { core.syncCatalog(observer) }

    suspend fun enrichMetadata(observer: SyncObserver): SyncSummary = withContext(io) { core.enrichMetadata(observer) }

    // Eine Rust-Panic kommt als InternalException und wäre für die ViewModels, die nur CoreException
    // kennen, ein Absturz; so landet sie im gewohnten Fehlerpfad
    private suspend fun <T> guarded(block: () -> T): T =
        try {
            withContext(io) { block() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: CoreException) {
            throw e
        } catch (e: Exception) {
            throw CoreException.Internal(e::class.java.simpleName)
        }

    fun notifyChanged() {
        _changes.tryEmit(Unit)
    }
}
