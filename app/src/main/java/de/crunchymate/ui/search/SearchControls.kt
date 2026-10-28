package de.crunchymate.ui.search

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import de.crunchymate.R
import de.crunchymate.core.ffi.LanguageFilter
import de.crunchymate.core.ffi.SortOrder
import de.crunchymate.ui.components.FilterDropdown
import de.crunchymate.ui.components.TagChip
import de.crunchymate.ui.tags.stateOf
import de.crunchymate.ui.theme.Dimens

/** Ereignisse der Suchsteuerung; gebündelt, damit die Composables zustandslos bleiben. */
data class SearchActions(
    val onQueryChange: (String) -> Unit,
    val onGenreSelected: (String?) -> Unit,
    val onLanguageSelected: (LanguageFilter) -> Unit,
    val onSortSelected: (SortOrder) -> Unit,
    val onTagTapped: (String) -> Unit,
    val onTagRemoved: (String) -> Unit,
    val onTagQueryChange: (String) -> Unit,
    val onClearTags: () -> Unit,
)

// Kein else-Zweig: neue Enum-Werte sollen die Kompilierung brechen
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
        FilterDropdowns(state, actions)
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
private fun FilterDropdowns(
    state: SearchUiState,
    actions: SearchActions,
) {
    val allGenres = stringResource(R.string.filter_genre_all)
    val genreCounts = state.genres.associate { it.name to it.count }
    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
        FilterDropdown(
            label = stringResource(R.string.filter_genre_label),
            options = listOf<String?>(null) + state.genres.map { it.name },
            selected = state.search.genre,
            optionLabel = { name ->
                val count = genreCounts[name]
                if (name == null || count == null) name ?: allGenres else stringResource(R.string.tag_label_with_count, name, count)
            },
            onSelect = actions.onGenreSelected,
            modifier = Modifier.weight(1f),
        )
        FilterDropdown(
            label = stringResource(R.string.sort_label),
            options = SortOrder.entries,
            selected = state.search.sort,
            optionLabel = { stringResource(it.labelRes()) },
            onSelect = actions.onSortSelected,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun LanguageChips(
    language: LanguageFilter,
    onSelect: (LanguageFilter) -> Unit,
) {
    val sub = language.requiresSub()
    val dub = language.requiresDub()
    FilterChip(
        selected = sub,
        onClick = { onSelect(languageFilterOf(sub = !sub, dub = dub)) },
        label = { Text(stringResource(R.string.filter_language_sub_chip)) },
    )
    FilterChip(
        selected = dub,
        onClick = { onSelect(languageFilterOf(sub = sub, dub = !dub)) },
        label = { Text(stringResource(R.string.filter_language_dub_chip)) },
    )
}

@Composable
private fun TagSection(
    state: SearchUiState,
    actions: SearchActions,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
        OutlinedTextField(
            value = state.tagQuery,
            onValueChange = actions.onTagQueryChange,
            modifier = Modifier.weight(1f).heightIn(min = Dimens.FilterFieldHeight),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium,
            label = { Text(stringResource(R.string.tags_filter_label)) },
        )
        if (state.activeTags.isNotEmpty()) {
            IconButton(onClick = actions.onClearTags, modifier = Modifier.size(Dimens.TagResetButton)) {
                Icon(
                    painterResource(R.drawable.ic_tags_reset),
                    contentDescription = stringResource(R.string.tags_reset),
                    modifier = Modifier.size(Dimens.TagResetIcon),
                )
            }
        }
        LanguageChips(state.search.language, actions.onLanguageSelected)
    }
    if (state.activeTags.isNotEmpty()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
            verticalArrangement = Arrangement.spacedBy(Dimens.TagChipRowGap),
        ) {
            state.activeTags.forEach { selection ->
                TagChip(
                    name = selection.name,
                    count = null,
                    state = selection.state,
                    onClick = { actions.onTagRemoved(selection.name) },
                    removable = true,
                )
            }
        }
    }
    val remaining = state.visibleTags.filter { state.activeTags.stateOf(it.name) == null }
    // Begrenzte Höhe, damit die Tag-Liste das Ergebnis-Grid nicht aus dem Bild schiebt
    FlowRow(
        modifier = Modifier.heightIn(max = Dimens.TagListMaxHeight).verticalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
        verticalArrangement = Arrangement.spacedBy(Dimens.TagChipRowGap),
    ) {
        remaining.forEach { tag ->
            TagChip(name = tag.name, count = tag.count, state = null, onClick = { actions.onTagTapped(tag.name) })
        }
    }
}
