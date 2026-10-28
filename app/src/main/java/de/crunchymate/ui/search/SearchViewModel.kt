package de.crunchymate.ui.search

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import de.crunchymate.core.dataLocale
import de.crunchymate.core.ffi.AnimeFilter
import de.crunchymate.core.ffi.AnimeSummary
import de.crunchymate.core.ffi.CoreException
import de.crunchymate.core.ffi.LanguageFilter
import de.crunchymate.core.ffi.NameCount
import de.crunchymate.core.ffi.SearchState
import de.crunchymate.core.ffi.SortOrder
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

    /**
     * Text im Suchfeld als synchroner Compose-Zustand: Ein an einen asynchronen Flow gebundenes
     * TextField verliert bei schnellem Tippen Zeichen. Geladen wird erst nach einer Tipp-Pause.
     */
    var query by mutableStateOf(settingsStore.settings.value.search.query)
        private set

    private val loadedQuery = MutableStateFlow(query)
    private var queryJob: Job? = null

    private val filter: Flow<AnimeFilter> =
        combine(search, loadedQuery) { state, query -> state.copy(query = query).toFilter(mode) }
            .distinctUntilChanged()

    private val genres = MutableStateFlow(emptyList<NameCount>())
    private val tags = MutableStateFlow(emptyList<NameCount>())
    private val tagQuery = MutableStateFlow("")
    private val total = MutableStateFlow(TotalState(count = null, error = false))

    private val changesWithStart = gateway.changes.onStart { emit(Unit) }

    @Volatile
    private var currentFactory: InvalidatingPagingSourceFactory<Int, AnimeSummary>? = null

    val items: Flow<PagingData<AnimeSummary>> =
        filter
            .flatMapLatest { current ->
                val factory = InvalidatingPagingSourceFactory { pagingSourceFactory(current) }
                currentFactory = factory
                // Platzhalter halten die Positionen stabil, wenn nach einer Änderung mitten in der Liste neu geladen wird
                Pager(PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = true), pagingSourceFactory = factory).flow
            }.cachedIn(viewModelScope)

    /** Sprache der gespeicherten Daten, für die die Karten die Verfügbarkeit der Fassung anzeigen. */
    val contentLocale: StateFlow<String> =
        gateway
            .dataLocale(settingsStore.settings)
            .stateIn(viewModelScope, SharingStarted.Eagerly, settingsStore.settings.value.contentLocale)

    val uiState: StateFlow<SearchUiState> =
        combine(search, genres, tags, tagQuery, total) { state, genreList, tagList, tagFilter, totalState ->
            SearchUiState(
                search = state,
                genres = genreList,
                tags = tagList,
                tagQuery = tagFilter,
                visibleTags = tagList.filterByName(tagFilter),
                activeTags = state.tags,
                total = totalState.count,
                totalError = totalState.error,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, initialState(settingsStore.settings.value.search))

    init {
        gateway.changes.onEach { currentFactory?.invalidate() }.launchIn(viewModelScope)
        // Genres und Tags braucht nur die Suche; Gesehen und Meh haben keine Steuerelemente
        if (mode == GridMode.Search) {
            changesWithStart.onEach { loadFacets() }.launchIn(viewModelScope)
        }
        combine(filter, changesWithStart) { current, _ -> current }
            .mapLatest { current -> loadTotal(current) }
            .onEach { total.value = it }
            .launchIn(viewModelScope)
    }

    fun onQueryChange(q: String) {
        query = q
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

    fun onSortSelected(s: SortOrder) = updateSearch { it.copy(sort = s) }

    fun onTagTapped(name: String) = updateSearch { it.copy(tags = it.tags.cycle(name)) }

    fun onTagRemoved(name: String) = updateSearch { it.copy(tags = it.tags.filterNot { selection -> selection.name == name }) }

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

    private suspend fun loadTotal(current: AnimeFilter): TotalState =
        try {
            TotalState(count = gateway.queryAnime(current, 0, TOTAL_ONLY_LIMIT).total, error = false)
        } catch (e: CoreException) {
            Log.w(LOG_TAG, "Trefferzahl konnte nicht geladen werden: ${e::class.simpleName}")
            TotalState(count = null, error = true)
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
                totalError = false,
            )
    }
}

private data class TotalState(
    val count: Long?,
    val error: Boolean,
)

private fun List<NameCount>.filterByName(query: String): List<NameCount> {
    val needle = query.trim()
    return if (needle.isEmpty()) this else filter { it.name.contains(needle, ignoreCase = true) }
}
