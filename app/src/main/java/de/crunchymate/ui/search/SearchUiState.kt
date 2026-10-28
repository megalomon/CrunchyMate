package de.crunchymate.ui.search

import de.crunchymate.core.ffi.NameCount
import de.crunchymate.core.ffi.SearchState
import de.crunchymate.core.ffi.TagSelection

/** [total] ist null, solange die Trefferzahl noch nicht ermittelt wurde oder [totalError] gesetzt ist. */
data class SearchUiState(
    val search: SearchState,
    val genres: List<NameCount>,
    val tags: List<NameCount>,
    val tagQuery: String,
    val visibleTags: List<NameCount>,
    val activeTags: List<TagSelection>,
    val total: Long?,
    val totalError: Boolean,
)
