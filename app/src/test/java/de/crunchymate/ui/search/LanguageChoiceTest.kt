package de.crunchymate.ui.search

import de.crunchymate.core.ffi.LanguageFilter
import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageChoiceTest {
    @Test
    fun languageFilterOf_mapsAllCombinations() {
        assertEquals(LanguageFilter.ALL, languageFilterOf(sub = false, dub = false))
        assertEquals(LanguageFilter.DUB, languageFilterOf(sub = false, dub = true))
        assertEquals(LanguageFilter.SUB, languageFilterOf(sub = true, dub = false))
        assertEquals(LanguageFilter.BOTH, languageFilterOf(sub = true, dub = true))
    }

    @Test
    fun requires_roundTripsForOfferedValues() {
        LanguageFilter.entries.filter { it != LanguageFilter.ANY }.forEach {
            assertEquals(it, languageFilterOf(it.requiresSub(), it.requiresDub()))
        }
    }

    @Test
    fun any_showsBothChipsOff() {
        assertEquals(false, LanguageFilter.ANY.requiresSub())
        assertEquals(false, LanguageFilter.ANY.requiresDub())
    }
}
