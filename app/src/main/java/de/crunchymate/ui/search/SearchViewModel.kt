package de.crunchymate.ui.search

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.InvalidatingPagingSourceFactory
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.cachedIn
import de.crunchymate.core.AnimePagingSource
import de.crunchymate.core.CoreGateway
import de.crunchymate.core.PAGE_SIZE
import de.crunchymate.core.SettingsStore
import de.crunchymate.core.ffi.AnimeFilter
import de.crunchymate.core.ffi.AnimeSummary
import de.crunchymate.core.ffi.CoreException
import de.crunchymate.core.ffi.LanguageFilter
import de.crunchymate.core.ffi.NameCount
import de.crunchymate.core.ffi.SearchState
import de.crunchymate.core.ffi.SortOrder
import de.crunchymate.core.ffi.WatchedFilter
import de.crunchymate.ui.tags.cycle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Verzögerung zwischen letzter Eingabe im Suchfeld und dem Neuladen der Treffer. */
const val QUERY_DEBOUNCE_MS = 300L

// Limit 0 liefert nur die Gesamtzahl (Ruling 11)
private const val TOTAL_ONLY_LIMIT = 0
private const val LOG_TAG = "CrunchyMate"

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModel internal constructor(
    private val mode: GridMode,
    private val gateway: CoreGateway,
    private val settingsStore: SettingsStore,
    private val pagingSourceFactory: (AnimeFilter) -> PagingSource<Int, AnimeSummary>,
) : ViewModel() {
    constructor(mode: GridMode, gateway: CoreGateway, settingsStore: SettingsStore) :
        this(mode, gateway, settingsStore, { filter -> AnimePagingSource(gateway, filter) })

    private val search = settingsStore.settings.map { it.search }.distinctUntilChanged()

    // Das Suchfeld zeigt jede Eingabe sofort, geladen wird erst nach einer Tipp-Pause
    private val loadedQuery = MutableStateFlow(settingsStore.settings.value.search.query)
    private var queryJob: Job? = null

    private val filter: Flow<AnimeFilter> =
        combine(search, loadedQuery) { state, query -> state.copy(query = query).toFilter(mode) }
            .distinctUntilChanged()

    private val genres = MutableStateFlow(emptyList<NameCount>())
    private val tags = MutableStateFlow(emptyList<NameCount>())
    private val tagQuery = MutableStateFlow("")
    private val total = MutableStateFlow<Long?>(null)

    private val changesWithStart = gateway.changes.onStart { emit(Unit) }

    @Volatile
    private var currentFactory: InvalidatingPagingSourceFactory<Int, AnimeSummary>? = null

    val items: Flow<PagingData<AnimeSummary>> =
        filter
            .flatMapLatest { current ->
                val factory = InvalidatingPagingSourceFactory { pagingSourceFactory(current) }
                currentFactory = factory
                Pager(PagingConfig(pageSize = PAGE_SIZE), pagingSourceFactory = factory).flow
            }.cachedIn(viewModelScope)

    val uiState: StateFlow<SearchUiState> =
        combine(search, genres, tags, tagQuery, total) { state, genreList, tagList, query, count ->
            SearchUiState(
                search = state,
                genres = genreList,
                tags = tagList,
                tagQuery = query,
                visibleTags = if (query.isBlank()) tagList else tagList.filter { it.name.contains(query.trim(), ignoreCase = true) },
                activeTags = state.tags,
                total = count,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, initialState(settingsStore.settings.value.search))

    init {
        gateway.changes.onEach { currentFactory?.invalidate() }.launchIn(viewModelScope)
        changesWithStart.onEach { loadFacets() }.launchIn(viewModelScope)
        combine(filter, changesWithStart) { current, _ -> current }
            .mapLatest { current -> loadTotal(current) }
            .onEach { total.value = it }
            .launchIn(viewModelScope)
    }

    fun onQueryChange(q: String) {
        updateSearch { it.copy(query = q) }
        queryJob?.cancel()
        queryJob =
            viewModelScope.launch {
                delay(QUERY_DEBOUNCE_MS)
                loadedQuery.value = q
            }
    }

    fun onGenreSelected(genre: String?) = updateSearch { it.copy(genre = genre) }

    fun onLanguageSelected(l: LanguageFilter) = updateSearch { it.copy(language = l) }

    fun onWatchedSelected(w: WatchedFilter) = updateSearch { it.copy(watched = w) }

    fun onSortSelected(s: SortOrder) = updateSearch { it.copy(sort = s) }

    fun onTagTapped(name: String) = updateSearch { it.copy(tags = it.tags.cycle(name)) }

    fun onTagQueryChange(q: String) {
        tagQuery.value = q
    }

    fun onClearTags() = updateSearch { it.copy(tags = emptyList()) }

    fun onToggleWatched(anime: AnimeSummary) = runCatchingCore { gateway.setWatched(anime.id, !anime.watched) }

    fun onToggleMeh(anime: AnimeSummary) = runCatchingCore { gateway.setMeh(anime.id, !anime.isMeh) }

    private fun updateSearch(transform: (SearchState) -> SearchState) {
        settingsStore.update { it.copy(search = transform(it.search)) }
    }

    private suspend fun loadFacets() {
        try {
            genres.value = gateway.genres()
            tags.value = gateway.tags()
        } catch (e: CoreException) {
            Log.w(LOG_TAG, "Genres und Tags konnten nicht geladen werden: ${e::class.simpleName}")
        }
    }

    private suspend fun loadTotal(current: AnimeFilter): Long? =
        try {
            gateway.queryAnime(current, 0, TOTAL_ONLY_LIMIT).total
        } catch (e: CoreException) {
            Log.w(LOG_TAG, "Trefferzahl konnte nicht geladen werden: ${e::class.simpleName}")
            null
        }

    private fun runCatchingCore(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CoreException) {
                Log.w(LOG_TAG, "Markierung konnte nicht gespeichert werden: ${e::class.simpleName}")
            }
        }
    }

    private companion object {
        fun initialState(search: SearchState) =
            SearchUiState(
                search = search,
                genres = emptyList(),
                tags = emptyList(),
                tagQuery = "",
                visibleTags = emptyList(),
                activeTags = search.tags,
                total = null,
            )
    }
}
