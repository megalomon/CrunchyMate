package de.crunchymate.ui.detail

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.crunchymate.R
import de.crunchymate.core.CoreGateway
import de.crunchymate.core.SettingsStore
import de.crunchymate.core.ffi.AnimeDetail
import de.crunchymate.core.ffi.CoreException
import de.crunchymate.core.ffi.TagSelection
import de.crunchymate.core.messageRes
import de.crunchymate.ui.tags.cycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DetailUiState(
    val anime: AnimeDetail?,
    val loading: Boolean,
    @StringRes val error: Int?,
    val tagStates: List<TagSelection>,
)

private data class LoadState(
    val anime: AnimeDetail?,
    val loading: Boolean,
    @StringRes val error: Int?,
)

class DetailViewModel(
    private val id: String,
    private val gateway: CoreGateway,
    private val settingsStore: SettingsStore,
) : ViewModel() {
    private val load = MutableStateFlow(LoadState(anime = null, loading = true, error = null))

    val uiState: StateFlow<DetailUiState> =
        combine(load, settingsStore.settings) { state, settings ->
            DetailUiState(state.anime, state.loading, state.error, settings.search.tags)
        }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            DetailUiState(anime = null, loading = true, error = null, tagStates = settingsStore.settings.value.search.tags),
        )

    init {
        // Neu laden bei jeder Änderung, damit Gesehen/Meh nach dem Umschalten stimmen
        gateway.changes
            .onStart { emit(Unit) }
            .onEach { reload() }
            .launchIn(viewModelScope)
    }

    fun onToggleWatched() {
        val anime = load.value.anime ?: return
        launchCatching { gateway.setWatched(anime.id, !anime.watched) }
    }

    fun onToggleMeh() {
        val anime = load.value.anime ?: return
        launchCatching { gateway.setMeh(anime.id, !anime.isMeh) }
    }

    /** Übernimmt ein Tag aus der Detailansicht in den Tag-Filter der Suche. */
    fun onTagTapped(name: String) {
        settingsStore.update { it.copy(search = it.search.copy(tags = it.search.tags.cycle(name))) }
    }

    private suspend fun reload() {
        load.value =
            try {
                val anime = gateway.getAnime(id)
                LoadState(anime, loading = false, error = if (anime == null) R.string.detail_not_found else null)
            } catch (e: CoreException) {
                LoadState(load.value.anime, loading = false, error = e.messageRes())
            }
    }

    private fun launchCatching(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CoreException) {
                load.value = load.value.copy(error = e.messageRes())
            }
        }
    }
}
