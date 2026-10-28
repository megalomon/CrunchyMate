package de.crunchymate.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/** Bis zu dieser Bewertung bleibt der Stern rot. */
const val SCORE_LOW_END = 40

/** Bei dieser Bewertung ist der Stern amber. */
const val SCORE_MID = 65

/** Ab dieser Bewertung bleibt der Stern grün. */
const val SCORE_HIGH_START = 85

/**
 * Stufenlose Farbe zur AniList-Bewertung (0 bis 100): rot, amber, grün. [lerp] mischt im
 * wahrnehmungsgleichen Oklab-Raum, daher entsteht zwischen den Stützwerten kein trüber Übergang.
 */
fun scoreColor(score: Int): Color =
    when {
        score <= SCORE_LOW_END -> ScoreLowRed
        score < SCORE_MID -> lerp(ScoreLowRed, ScoreMidAmber, (score - SCORE_LOW_END).toFloat() / (SCORE_MID - SCORE_LOW_END))
        score < SCORE_HIGH_START -> lerp(ScoreMidAmber, ScoreHighGreen, (score - SCORE_MID).toFloat() / (SCORE_HIGH_START - SCORE_MID))
        else -> ScoreHighGreen
    }
