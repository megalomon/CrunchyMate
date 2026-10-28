package de.crunchymate.ui.tags

import de.crunchymate.core.ffi.TagSelection
import de.crunchymate.core.ffi.TagState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TagCycleTest {
    @Test
    fun nextTagState_cyclesThroughAllStates() {
        assertEquals(TagState.PREFER, nextTagState(null))
        assertEquals(TagState.MUST, nextTagState(TagState.PREFER))
        assertEquals(TagState.EXCLUDE, nextTagState(TagState.MUST))
        assertNull(nextTagState(TagState.EXCLUDE))
    }

    @Test
    fun cycle_addsThenAdvancesThenRemoves() {
        val added = emptyList<TagSelection>().cycle("Action")
        assertEquals(listOf(TagSelection("Action", TagState.PREFER)), added)

        val advanced = added.cycle("Action")
        assertEquals(TagState.MUST, advanced.stateOf("Action"))

        val excluded = advanced.cycle("Action")
        assertEquals(TagState.EXCLUDE, excluded.stateOf("Action"))

        val removed = excluded.cycle("Action")
        assertEquals(emptyList<TagSelection>(), removed)
        assertNull(removed.stateOf("Action"))
    }

    @Test
    fun cycle_leavesOtherTagsUntouched() {
        val other = TagSelection("Drama", TagState.MUST)
        val result = listOf(other).cycle("Action")
        assertEquals(listOf(other, TagSelection("Action", TagState.PREFER)), result)
    }
}
