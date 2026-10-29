package de.crunchymate.ui.theme

import androidx.compose.ui.graphics.Color

val CrispyBunOrange = Color(0xFFF47521)
val CrispyBunOrangeLight = Color(0xFFFF8533)
val OnOrange = Color(0xFF1A0E05)

// Tag-Zustände wie in der früheren Weboberfläche (style.css)
val TagPreferBlue = Color(0xFF3DB4F2)
val TagMustGreen = Color(0xFF2ECC71)
val TagExcludeRed = Color(0xFFE5484D)

val DarkBackground = Color(0xFF0C0D10)
val DarkSurface = Color(0xFF15181E)
val DarkSurfaceVariant = Color(0xFF1C212A)
val DarkOutline = Color(0xFF262B36)
val DarkOnSurface = Color(0xFFF0F3F8)
val DarkOnSurfaceMuted = Color(0xFF8B94A5)

val LightBackground = Color(0xFFFAF7F5)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFF1ECE8)
val LightOutline = Color(0xFFD3CCC6)
val LightOnSurface = Color(0xFF1B1B1F)
val LightOnSurfaceMuted = Color(0xFF5E6573)
val LightPrimary = Color(0xFFB85000)

// Im hellen Schema dunkler getönt, damit Text und Icons genug Kontrast behalten
val LightTagPreferBlue = Color(0xFF0B76B0)
val LightTagMustGreen = Color(0xFF1E8449)
val LightTagExcludeRed = Color(0xFFC62828)

// Stützfarben der Bewertungs-Einfärbung; hell genug für den dunklen Plakat-Untergrund
val ScoreLowRed = Color(0xFFFF5C5C)
val ScoreMidAmber = Color(0xFFFFC107)
val ScoreHighGreen = Color(0xFF4ADE80)

// OLED-Modus: reines Schwarz, die Containerstufen nur minimal aufgehellt
val OledBlack = Color(0xFF000000)
val OledSurfaceContainerLow = Color(0xFF080809)
val OledSurfaceContainer = Color(0xFF0E0E10)
val OledSurfaceContainerHigh = Color(0xFF141417)
val OledSurfaceContainerHighest = Color(0xFF1A1A1E)

// Halbtransparenter Grund für Beschriftungen und Schalter auf Postern, unabhängig vom Farbschema
val PosterOverlayBackground = Color(0xB3000000)
val PosterOverlayContent = Color(0xFFFFFFFF)

// USK-Farben der Altersstufen; 14 ist keine USK-Stufe und liegt als Mitte zwischen Grün (12) und Blau (16)
val AgeRatingAll = Color(0xFFFFFFFF)
val AgeRating6 = Color(0xFFFFF200)
val AgeRating12 = Color(0xFF00A651)
val AgeRating14 = Color(0xFF00AAA0)
val AgeRating16 = Color(0xFF00AEEF)
val AgeRating18 = Color(0xFFED1C24)

// Schwarz erreicht auf allen USK-Farben mindestens 4,5:1; Weiß scheitert auf Grün, Türkis, Blau und Rot
val AgeRatingContent = Color(0xFF000000)
