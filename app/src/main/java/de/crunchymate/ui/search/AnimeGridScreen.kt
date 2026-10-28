package de.crunchymate.ui.search

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import de.crunchymate.R
import de.crunchymate.core.ffi.AnimeSummary
import de.crunchymate.core.messageRes
import de.crunchymate.ui.components.AnimeCard
import de.crunchymate.ui.components.EmptyState
import de.crunchymate.ui.theme.Dimens

/** Texte, die sich zwischen Suche, Gesehen und Meh unterscheiden. */
private data class ModeTexts(
    @StringRes val bannerTitle: Int?,
    @StringRes val bannerSubtitle: Int?,
    @StringRes val emptyTitle: Int,
    @StringRes val emptySubtitle: Int,
)

private fun GridMode.texts(): ModeTexts =
    when (this) {
        GridMode.Search -> ModeTexts(null, null, R.string.empty_search_title, R.string.empty_search_subtitle)
        GridMode.Watched ->
            ModeTexts(
                R.string.banner_watched_title,
                R.string.banner_watched_subtitle,
                R.string.empty_watched_title,
                R.string.empty_watched_subtitle,
            )
        GridMode.Meh ->
            ModeTexts(R.string.banner_meh_title, R.string.banner_meh_subtitle, R.string.empty_meh_title, R.string.empty_meh_subtitle)
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
    AnimeGridScreen(
        mode = mode,
        state = state,
        items = items,
        actions =
            SearchActions(
                onQueryChange = viewModel::onQueryChange,
                onGenreSelected = viewModel::onGenreSelected,
                onLanguageSelected = viewModel::onLanguageSelected,
                onWatchedSelected = viewModel::onWatchedSelected,
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
    items: LazyPagingItems<AnimeSummary>,
    actions: SearchActions,
    onOpen: (String) -> Unit,
    onToggleWatched: (AnimeSummary) -> Unit,
    onToggleMeh: (AnimeSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    val texts = mode.texts()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(Dimens.GridCellMinWidth),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(Dimens.SpaceMd),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
    ) {
        if (mode == GridMode.Search) {
            fullWidth { SearchControls(state, actions) }
        }
        if (texts.bannerTitle != null && texts.bannerSubtitle != null) {
            fullWidth { Banner(texts.bannerTitle, texts.bannerSubtitle) }
        }
        fullWidth { ResultCount(state.total) }
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
                            subtitle = stringResource(texts.emptySubtitle),
                        )
                    }
                }
        }
        // Ohne Schlüssel: Offset-Paging kann während eines Syncs dieselbe ID auf zwei Seiten liefern,
        // doppelte Schlüssel würden das Grid zum Absturz bringen
        items(count = items.itemCount) { index ->
            items[index]?.let { anime ->
                AnimeCard(
                    anime = anime,
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
    @StringRes subtitle: Int,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Dimens.SpaceMd), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)) {
            Text(
                stringResource(title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ResultCount(total: Long?) {
    Text(
        if (total == null) stringResource(R.string.results_loading) else stringResource(R.string.results_count, total),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun LoadingIndicator() {
    Column(Modifier.fillMaxWidth().padding(Dimens.SpaceLg), horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
    }
}
