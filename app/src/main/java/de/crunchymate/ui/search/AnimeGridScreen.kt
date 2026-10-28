package de.crunchymate.ui.search

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import de.crunchymate.R
import de.crunchymate.core.ffi.AnimeSummary
import de.crunchymate.core.messageRes
import de.crunchymate.ui.components.AnimeCard
import de.crunchymate.ui.components.EmptyState
import de.crunchymate.ui.theme.Dimens

/** Anzahl der Spalten; auf allen Bildschirmbreiten gleich, damit die Karten überall gleich aufgebaut sind. */
const val GRID_COLUMNS = 3

fun animeGridCells(): GridCells = GridCells.Fixed(GRID_COLUMNS)

// Poster (2:3) plus zweizeiliger Titel und Zeile mit Episoden und Flagge der AnimeCard, grob angenähert
private const val PLACEHOLDER_ASPECT_RATIO = 0.5f

/** Texte, die sich zwischen Suche, Gesehen und Meh unterscheiden. */
private data class ModeTexts(
    @StringRes val bannerTitle: Int?,
    @StringRes val emptyTitle: Int,
)

private fun GridMode.texts(): ModeTexts =
    when (this) {
        GridMode.Search -> ModeTexts(null, R.string.empty_search_title)
        GridMode.Watched -> ModeTexts(R.string.banner_watched_title, R.string.empty_watched_title)
        GridMode.Meh -> ModeTexts(R.string.banner_meh_title, R.string.empty_meh_title)
    }

@Composable
fun AnimeGridDestination(
    mode: GridMode,
    viewModel: SearchViewModel,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val items = viewModel.items.collectAsLazyPagingItems()
    val contentLocale by viewModel.contentLocale.collectAsStateWithLifecycle()
    AnimeGridScreen(
        mode = mode,
        state = state,
        query = viewModel.query,
        items = items,
        contentLocale = contentLocale,
        actions =
            SearchActions(
                onQueryChange = viewModel::onQueryChange,
                onGenreSelected = viewModel::onGenreSelected,
                onLanguageSelected = viewModel::onLanguageSelected,
                onSortSelected = viewModel::onSortSelected,
                onTagTapped = viewModel::onTagTapped,
                onTagQueryChange = viewModel::onTagQueryChange,
                onClearTags = viewModel::onClearTags,
            ),
        onOpen = onOpen,
        onToggleWatched = viewModel::onToggleWatched,
        onToggleMeh = viewModel::onToggleMeh,
        modifier = modifier,
    )
}

@Composable
fun AnimeGridScreen(
    mode: GridMode,
    state: SearchUiState,
    query: String,
    items: LazyPagingItems<AnimeSummary>,
    contentLocale: String,
    actions: SearchActions,
    onOpen: (String) -> Unit,
    onToggleWatched: (AnimeSummary) -> Unit,
    onToggleMeh: (AnimeSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    val texts = mode.texts()
    LazyVerticalGrid(
        columns = animeGridCells(),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(Dimens.GridSpacing),
        horizontalArrangement = Arrangement.spacedBy(Dimens.GridSpacing),
        verticalArrangement = Arrangement.spacedBy(Dimens.GridSpacing),
    ) {
        if (mode == GridMode.Search) {
            fullWidth { SearchControls(state, query, actions) }
        }
        if (texts.bannerTitle != null) {
            fullWidth { Banner(texts.bannerTitle) }
        }
        fullWidth { ResultCount(state.total, state.totalError) }
        when (val refresh = items.loadState.refresh) {
            is LoadState.Error ->
                fullWidth {
                    EmptyState(
                        icon = R.drawable.ic_state_error,
                        title = stringResource(R.string.error_load_title),
                        subtitle = stringResource(refresh.error.messageRes()),
                    )
                }
            is LoadState.Loading ->
                if (items.itemCount == 0) {
                    fullWidth { LoadingIndicator() }
                }
            is LoadState.NotLoading ->
                if (items.itemCount == 0) {
                    fullWidth {
                        EmptyState(
                            icon = R.drawable.ic_state_empty,
                            title = stringResource(texts.emptyTitle),
                        )
                    }
                }
        }
        // Eindeutige IDs garantiert AnimePagingSource je Ladegeneration
        items(count = items.itemCount, key = items.itemKey { it.id }) { index ->
            val anime = items[index]
            if (anime == null) {
                CardPlaceholder()
            } else {
                AnimeCard(
                    anime = anime,
                    contentLocale = contentLocale,
                    onOpen = { onOpen(anime.id) },
                    onToggleWatched = { onToggleWatched(anime) },
                    onToggleMeh = { onToggleMeh(anime) },
                )
            }
        }
    }
}

private fun LazyGridScope.fullWidth(content: @Composable () -> Unit) {
    item(span = { GridItemSpan(maxLineSpan) }) { content() }
}

@Composable
private fun Banner(
    @StringRes title: Int,
) {
    Card(Modifier.fillMaxWidth()) {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(Dimens.SpaceMd).semantics { heading() },
        )
    }
}

@Composable
private fun ResultCount(
    total: Long?,
    error: Boolean,
) {
    if (error) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
            Icon(
                painterResource(R.drawable.ic_state_error),
                contentDescription = null,
                modifier = Modifier.size(Dimens.BadgeIcon),
                tint = MaterialTheme.colorScheme.error,
            )
            Text(
                stringResource(R.string.results_error),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error,
            )
        }
        return
    }
    Text(
        if (total == null) stringResource(R.string.results_loading) else stringResource(R.string.results_count, total),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Platzhalter für noch nicht geladene Einträge; hält die Positionen im Grid stabil. */
@Composable
private fun CardPlaceholder() {
    Card(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().aspectRatio(PLACEHOLDER_ASPECT_RATIO))
    }
}

@Composable
private fun LoadingIndicator() {
    Column(Modifier.fillMaxWidth().padding(Dimens.SpaceLg), horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
    }
}
