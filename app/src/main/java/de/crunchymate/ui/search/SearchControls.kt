package de.crunchymate.ui.search

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import de.crunchymate.R
import de.crunchymate.core.ffi.LanguageFilter
import de.crunchymate.core.ffi.SortOrder
import de.crunchymate.core.ffi.WatchedFilter
import de.crunchymate.ui.components.FilterDropdownChip
import de.crunchymate.ui.components.TagChip
import de.crunchymate.ui.tags.stateOf
import de.crunchymate.ui.theme.Dimens

/** Ereignisse der Suchsteuerung; gebündelt, damit die Composables zustandslos bleiben. */
data class SearchActions(
    val onQueryChange: (String) -> Unit,
    val onGenreSelected: (String?) -> Unit,
    val onLanguageSelected: (LanguageFilter) -> Unit,
    val onWatchedSelected: (WatchedFilter) -> Unit,
    val onSortSelected: (SortOrder) -> Unit,
    val onTagTapped: (String) -> Unit,
    val onTagQueryChange: (String) -> Unit,
    val onClearTags: () -> Unit,
)

// Kein else-Zweig: neue Enum-Werte sollen die Kompilierung brechen
@StringRes
private fun WatchedFilter.labelRes(): Int =
    when (this) {
        WatchedFilter.ALL -> R.string.filter_watched_all
        WatchedFilter.UNWATCHED -> R.string.filter_watched_unwatched
        WatchedFilter.WATCHED -> R.string.filter_watched_watched
    }

@StringRes
private fun LanguageFilter.labelRes(): Int =
    when (this) {
        LanguageFilter.ALL -> R.string.filter_language_all
        LanguageFilter.DUB -> R.string.filter_language_dub
        LanguageFilter.SUB -> R.string.filter_language_sub
        LanguageFilter.BOTH -> R.string.filter_language_both
        LanguageFilter.ANY -> R.string.filter_language_any
    }

@StringRes
private fun SortOrder.labelRes(): Int =
    when (this) {
        SortOrder.TITLE -> R.string.sort_title
        SortOrder.SCORE -> R.string.sort_score
        SortOrder.EPISODES -> R.string.sort_episodes
        SortOrder.YEAR -> R.string.sort_year
    }

@Composable
fun SearchControls(
    state: SearchUiState,
    query: String,
    actions: SearchActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
        QueryField(query, actions.onQueryChange)
        FilterChips(state, actions)
        TagSection(state, actions)
    }
}

@Composable
private fun QueryField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text(stringResource(R.string.search_placeholder)) },
        leadingIcon = { Icon(painterResource(R.drawable.ic_nav_search), contentDescription = null) },
        trailingIcon =
            if (query.isEmpty()) {
                null
            } else {
                {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(painterResource(R.drawable.ic_clear), contentDescription = stringResource(R.string.search_clear))
                    }
                }
            },
    )
}

@Composable
private fun FilterChips(
    state: SearchUiState,
    actions: SearchActions,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
        FilterDropdownChip(
            label = stringResource(R.string.filter_watched_label),
            options = WatchedFilter.entries,
            selected = state.search.watched,
            optionLabel = { stringResource(it.labelRes()) },
            onSelect = actions.onWatchedSelected,
        )
        val allGenres = stringResource(R.string.filter_genre_all)
        FilterDropdownChip(
            label = stringResource(R.string.filter_genre_label),
            options = listOf<String?>(null) + state.genres.map { it.name },
            selected = state.search.genre,
            optionLabel = { it ?: allGenres },
            onSelect = actions.onGenreSelected,
        )
        FilterDropdownChip(
            label = stringResource(R.string.filter_language_label),
            options = LanguageFilter.entries,
            selected = state.search.language,
            optionLabel = { stringResource(it.labelRes()) },
            onSelect = actions.onLanguageSelected,
        )
        FilterDropdownChip(
            label = stringResource(R.string.sort_label),
            options = SortOrder.entries,
            selected = state.search.sort,
            optionLabel = { stringResource(it.labelRes()) },
            onSelect = actions.onSortSelected,
        )
    }
}

@Composable
private fun TagSection(
    state: SearchUiState,
    actions: SearchActions,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
        Text(stringResource(R.string.tags_heading), style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = state.tagQuery,
            onValueChange = actions.onTagQueryChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            placeholder = { Text(stringResource(R.string.tags_filter_placeholder)) },
        )
    }
    if (state.activeTags.isNotEmpty()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
            verticalArrangement = Arrangement.Center,
        ) {
            state.activeTags.forEach { selection ->
                TagChip(selection.name, count = null, state = selection.state, onClick = { actions.onTagTapped(selection.name) })
            }
            TextButton(onClick = actions.onClearTags) {
                Icon(painterResource(R.drawable.ic_tags_reset), contentDescription = null)
                Text(stringResource(R.string.tags_reset))
            }
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
        state.visibleTags.forEach { tag ->
            TagChip(
                name = tag.name,
                count = tag.count,
                state = state.activeTags.stateOf(tag.name),
                onClick = { actions.onTagTapped(tag.name) },
            )
        }
    }
}
