package de.crunchymate.ui.theme

import androidx.compose.ui.graphics.Color

private const val AGE_6 = 6
private const val AGE_12 = 12
private const val AGE_14 = 14
private const val AGE_16 = 16
private const val AGE_18 = 18

/** Hintergrund und Beschriftung eines Altersstufen-Kästchens. */
data class AgeRatingColors(
    val background: Color,
    val content: Color,
)

/** Stufen ohne eigene Farbe fallen auf die nächstniedrigere (7 -> 6, 13 -> 12, 17 -> 16); ab 18 gilt Rot. */
fun ageRatingColors(age: Int): AgeRatingColors =
    when {
        age >= AGE_18 -> AgeRatingColors(AgeRating18, AgeRatingContent)
        age >= AGE_16 -> AgeRatingColors(AgeRating16, AgeRatingContent)
        age >= AGE_14 -> AgeRatingColors(AgeRating14, AgeRatingContent)
        age >= AGE_12 -> AgeRatingColors(AgeRating12, AgeRatingContent)
        age >= AGE_6 -> AgeRatingColors(AgeRating6, AgeRatingContent)
        else -> AgeRatingColors(AgeRatingAll, AgeRatingContent)
    }
