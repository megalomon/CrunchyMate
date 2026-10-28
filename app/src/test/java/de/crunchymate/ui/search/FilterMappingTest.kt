package de.crunchymate.ui.search

import de.crunchymate.core.ffi.LanguageFilter
import de.crunchymate.core.ffi.MehFilter
import de.crunchymate.core.ffi.SearchState
import de.crunchymate.core.ffi.SortOrder
import de.crunchymate.core.ffi.TagSelection
import de.crunchymate.core.ffi.TagState
import de.crunchymate.core.ffi.WatchedFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterMappingTest {
    private val search =
        SearchState(
            query = "Frieren",
            genre = "Fantasy",
            language = LanguageFilter.DUB,
            watched = WatchedFilter.UNWATCHED,
            sort = SortOrder.SCORE,
            tags =
                listOf(
                    TagSelection("Magie", TagState.PREFER),
                    TagSelection("Elfen", TagState.MUST),
                    TagSelection("Horror", TagState.EXCLUDE),
                    TagSelection("Reise", TagState.PREFER),
                ),
        )

    @Test
    fun toFilter_search_mapsTagsAndHidesMeh() {
        val filter = search.toFilter(GridMode.Search)

        assertEquals("Frieren", filter.query)
        assertEquals("Fantasy", filter.genre)
        assertEquals(LanguageFilter.DUB, filter.language)
        assertEquals(WatchedFilter.UNWATCHED, filter.watched)
        assertEquals(MehFilter.HIDE, filter.meh)
        assertEquals(SortOrder.SCORE, filter.sort)
        assertEquals(listOf("Magie", "Reise"), filter.preferTags)
        assertEquals(listOf("Elfen"), filter.mustTags)
        assertEquals(listOf("Horror"), filter.excludeTags)
    }

    @Test
    fun toFilter_watched_ignoresSearchControlsAndIncludesMeh() {
        val filter = search.toFilter(GridMode.Watched)

        assertEquals("", filter.query)
        assertNull(filter.genre)
        assertEquals(LanguageFilter.ALL, filter.language)
        assertEquals(WatchedFilter.WATCHED, filter.watched)
        assertEquals(MehFilter.INCLUDE, filter.meh)
        assertEquals(SortOrder.SCORE, filter.sort)
        assertTrue(filter.preferTags.isEmpty() && filter.mustTags.isEmpty() && filter.excludeTags.isEmpty())
    }

    @Test
    fun toFilter_meh_onlyMeh() {
        val filter = search.toFilter(GridMode.Meh)

        assertEquals("", filter.query)
        assertNull(filter.genre)
        assertEquals(LanguageFilter.ALL, filter.language)
        assertEquals(WatchedFilter.ALL, filter.watched)
        assertEquals(MehFilter.ONLY, filter.meh)
        assertEquals(SortOrder.SCORE, filter.sort)
        assertTrue(filter.preferTags.isEmpty() && filter.mustTags.isEmpty() && filter.excludeTags.isEmpty())
    }
}
