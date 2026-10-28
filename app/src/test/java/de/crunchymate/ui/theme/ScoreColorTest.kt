package de.crunchymate.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val SCORE_MAX = 100
private const val LUMINANCE_OFFSET = 0.05f
private const val MIN_CONTRAST = 2.5f

private fun contrast(
    a: Color,
    b: Color,
): Float {
    val (light, dark) = listOf(a.luminance(), b.luminance()).sortedDescending()
    return (light + LUMINANCE_OFFSET) / (dark + LUMINANCE_OFFSET)
}

class ScoreColorTest {
    @Test
    fun endpoints_areRedAndGreenAndClampedBeyond() {
        assertEquals(ScoreLowRed, scoreColor(0))
        assertEquals(ScoreLowRed, scoreColor(SCORE_LOW_END))
        assertEquals(ScoreHighGreen, scoreColor(SCORE_HIGH_START))
        assertEquals(ScoreHighGreen, scoreColor(SCORE_MAX))
    }

    @Test
    fun middleSupportValue_isAmber() {
        assertEquals(ScoreMidAmber, scoreColor(SCORE_MID))
    }

    @Test
    fun betweenSupportValues_colorIsBlended() {
        val between = scoreColor((SCORE_LOW_END + SCORE_MID) / 2)
        assertNotEquals(ScoreLowRed, between)
        assertNotEquals(ScoreMidAmber, between)
    }

    @Test
    fun channels_changeMonotonicallyAlongTheGradient() {
        // Rot nach amber: Grünanteil steigt; amber nach grün: Rotanteil sinkt
        val reds = (SCORE_MID..SCORE_HIGH_START).map { scoreColor(it).red }
        assertTrue(reds.zipWithNext().all { (a, b) -> b <= a + EPSILON })
        val greens = (0..SCORE_MID).map { scoreColor(it).green }
        assertTrue(greens.zipWithNext().all { (a, b) -> b >= a - EPSILON })
    }

    @Test
    fun everyColor_staysReadableOnPosterOverlay() {
        // Schlechtester Fall: heller Poster-Untergrund unter dem halbtransparenten Schwarz
        val worstBackground = PosterOverlayBackground.compositeOver(Color.White)
        (0..SCORE_MAX).forEach { assertTrue("Bewertung $it", contrast(scoreColor(it), worstBackground) >= MIN_CONTRAST) }
    }

    private companion object {
        const val EPSILON = 0.001f
    }
}
