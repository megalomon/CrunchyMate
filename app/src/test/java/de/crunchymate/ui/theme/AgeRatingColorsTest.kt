package de.crunchymate.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgeRatingColorsTest {
    private fun background(age: Int) = ageRatingColors(age).background

    private fun contrast(
        a: Color,
        b: Color,
    ): Float {
        val lighter = maxOf(a.luminance(), b.luminance())
        val darker = minOf(a.luminance(), b.luminance())
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    @Test
    fun usk_levels_have_their_colors() {
        assertEquals(Color(0xFFFFFFFF), background(0))
        assertEquals(Color(0xFFFFF200), background(6))
        assertEquals(Color(0xFF00A651), background(12))
        assertEquals(Color(0xFF00AEEF), background(16))
        assertEquals(Color(0xFFED1C24), background(18))
    }

    @Test
    fun fourteen_has_its_own_mixed_color() {
        val fourteen = background(14)
        assertEquals(AgeRating14, fourteen)
        assertTrue(fourteen !in listOf(AgeRating12, AgeRating16))
    }

    @Test
    fun other_values_map_to_next_lower_level() {
        assertEquals(background(0), background(1))
        assertEquals(background(6), background(7))
        assertEquals(background(6), background(11))
        assertEquals(background(12), background(13))
        assertEquals(background(14), background(15))
        assertEquals(background(16), background(17))
        assertEquals(background(18), background(21))
    }

    @Test
    fun content_color_keeps_contrast_of_at_least_4_5() {
        listOf(0, 6, 12, 14, 16, 18).forEach { age ->
            val colors = ageRatingColors(age)
            assertTrue("Stufe $age: ${contrast(colors.background, colors.content)}", contrast(colors.background, colors.content) >= 4.5f)
        }
    }
}
