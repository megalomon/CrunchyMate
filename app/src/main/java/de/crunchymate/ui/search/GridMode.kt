package de.crunchymate.ui.search

import de.crunchymate.core.ffi.AnimeFilter
import de.crunchymate.core.ffi.LanguageFilter
import de.crunchymate.core.ffi.MehFilter
import de.crunchymate.core.ffi.SearchState
import de.crunchymate.core.ffi.TagState
import de.crunchymate.core.ffi.WatchedFilter

/** Die drei Ansichten über dem Katalog-Grid; nur die Suche nutzt die Steuerelemente. */
enum class GridMode { Search, Watched, Meh }

fun SearchState.toFilter(
    mode: GridMode,
    hideAdult: Boolean,
): AnimeFilter =
    when (mode) {
        GridMode.Search -> {
            AnimeFilter(
                query = query,
                genre = genre,
                language = language,
                watched = watched,
                meh = MehFilter.HIDE,
                sort = sort,
                preferTags = tagsIn(TagState.PREFER),
                mustTags = tagsIn(TagState.MUST),
                excludeTags = tagsIn(TagState.EXCLUDE),
                hideAdult = hideAdult,
            )
        }

        GridMode.Watched -> {
            fixedFilter(WatchedFilter.WATCHED, MehFilter.INCLUDE, hideAdult)
        }

        GridMode.Meh -> {
            fixedFilter(WatchedFilter.ALL, MehFilter.ONLY, hideAdult)
        }
    }

private fun SearchState.tagsIn(state: TagState): List<String> = tags.filter { it.state == state }.map { it.name }

// Gesehen und Meh zeigen ihre Liste vollständig; nur die Sortierung folgt der Suche
private fun SearchState.fixedFilter(
    watched: WatchedFilter,
    meh: MehFilter,
    hideAdult: Boolean,
) = AnimeFilter(
    query = "",
    genre = null,
    language = LanguageFilter.ALL,
    watched = watched,
    meh = meh,
    sort = sort,
    preferTags = emptyList(),
    mustTags = emptyList(),
    excludeTags = emptyList(),
    hideAdult = hideAdult,
)
