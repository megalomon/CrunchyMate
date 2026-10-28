package de.crunchymate.core

import de.crunchymate.core.ffi.AnimeDetail
import de.crunchymate.core.ffi.AnimeFilter
import de.crunchymate.core.ffi.AnimePage
import de.crunchymate.core.ffi.AnimeSummary
import de.crunchymate.core.ffi.CoreInterface
import de.crunchymate.core.ffi.LanguageFilter
import de.crunchymate.core.ffi.MehFilter
import de.crunchymate.core.ffi.NameCount
import de.crunchymate.core.ffi.SearchState
import de.crunchymate.core.ffi.Settings
import de.crunchymate.core.ffi.SortOrder
import de.crunchymate.core.ffi.Stats
import de.crunchymate.core.ffi.SyncKind
import de.crunchymate.core.ffi.SyncObserver
import de.crunchymate.core.ffi.SyncRunState
import de.crunchymate.core.ffi.SyncStatus
import de.crunchymate.core.ffi.SyncSummary
import de.crunchymate.core.ffi.TagRank
import de.crunchymate.core.ffi.WatchedFilter

/** In-Memory-Ersatz für den Rust-Kern; merkt sich Aufrufe und den aufrufenden Thread. */
class FakeCore(
    var items: List<AnimeSummary> = emptyList(),
    var storedSettings: Settings = sampleSettings(),
    private val totalOverride: Long? = null,
) : CoreInterface {
    val watchedCalls = mutableListOf<Pair<String, Boolean>>()
    val mehCalls = mutableListOf<Pair<String, Boolean>>()
    val savedSettings = mutableListOf<Settings>()
    val queryCalls = mutableListOf<Triple<AnimeFilter, Int, Int>>()
    var queryThreadName: String? = null
    var queryError: Throwable? = null
    var saveError: Throwable? = null
    var details: Map<String, AnimeDetail> = emptyMap()
    var tagList: List<NameCount> = listOf(NameCount("Magie", 2L))
    var currentStats: Stats? = null
    var facetCalls = 0

    @Volatile
    var syncStatuses: Map<SyncKind, SyncStatus> = emptyMap()

    /** Ersatz für den Rust-Sync; Standard: sofort fertig ohne Fortschritt. */
    @Volatile
    var syncBehavior: suspend (SyncKind, SyncObserver) -> SyncSummary = { _, _ -> SyncSummary(0L, 0, 0) }

    override fun queryAnime(
        filter: AnimeFilter,
        offset: Int,
        limit: Int,
    ): AnimePage {
        queryThreadName = Thread.currentThread().name
        queryCalls += Triple(filter, offset, limit)
        queryError?.let { throw it }
        return AnimePage(items.drop(offset).take(limit), totalOverride ?: items.size.toLong())
    }

    override fun getAnime(id: String): AnimeDetail? = details[id]

    override fun setWatched(
        id: String,
        watched: Boolean,
    ) {
        watchedCalls += id to watched
    }

    override fun setMeh(
        id: String,
        meh: Boolean,
    ) {
        mehCalls += id to meh
    }

    override fun listGenres(): List<NameCount> {
        facetCalls++
        return listOf(NameCount("Action", 3L))
    }

    override fun listTags(): List<NameCount> {
        facetCalls++
        return tagList
    }

    override fun stats(): Stats = currentStats ?: Stats(items.size.toLong(), 0L, 0L, 0L, 0L, 0L, 0L)

    override fun settings(): Settings = storedSettings

    override fun saveSettings(settings: Settings) {
        saveError?.let { throw it }
        savedSettings += settings
        storedSettings = settings
    }

    var localeCalls = 0

    override fun supportedContentLocales(): List<String> {
        localeCalls++
        return listOf("de-DE", "en-US")
    }

    override fun syncStatus(kind: SyncKind): SyncStatus =
        syncStatuses[kind] ?: SyncStatus(kind, SyncRunState.IDLE, 0L, 0L, null, null, null)

    override suspend fun syncCatalog(observer: SyncObserver): SyncSummary = syncBehavior(SyncKind.CATALOG, observer)

    override suspend fun enrichMetadata(observer: SyncObserver): SyncSummary = syncBehavior(SyncKind.METADATA, observer)
}

fun sampleSettings(locale: String = "de-DE") =
    Settings(
        locale,
        SearchState("", null, LanguageFilter.ALL, WatchedFilter.ALL, SortOrder.TITLE, emptyList()),
    )

fun sampleFilter() =
    AnimeFilter(
        "",
        null,
        LanguageFilter.ALL,
        WatchedFilter.ALL,
        MehFilter.INCLUDE,
        SortOrder.TITLE,
        emptyList(),
        emptyList(),
        emptyList(),
    )

fun sampleSummary(id: String) = AnimeSummary(id, "Titel $id", "", 0, false, false, false, false, 0, 0)

fun sampleDetail(
    id: String,
    tags: List<TagRank> = emptyList(),
) = AnimeDetail(
    id = id,
    title = "Titel $id",
    titleRomaji = "",
    titleEnglish = "",
    description = "",
    posterPath = "",
    bannerPath = "",
    episodeCount = 12,
    launchYear = 2024,
    isSubbed = true,
    isDubbed = false,
    hasTargetDub = false,
    hasTargetSub = true,
    watched = false,
    isMeh = false,
    genres = listOf("Action"),
    tags = tags,
    anilist = null,
)
