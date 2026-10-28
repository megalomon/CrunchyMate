package de.crunchymate.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Farben ohne Entsprechung im Material-Schema, etwa die Tag-Zustände. */
@Immutable
data class CrunchyMateColors(
    val tagPrefer: Color,
    val tagMust: Color,
    val tagExclude: Color,
)

private val DarkExtendedColors = CrunchyMateColors(TagPreferBlue, TagMustGreen, TagExcludeRed)
private val LightExtendedColors = CrunchyMateColors(LightTagPreferBlue, LightTagMustGreen, LightTagExcludeRed)

val LocalCrunchyMateColors = staticCompositionLocalOf { DarkExtendedColors }

private val DarkScheme =
    darkColorScheme(
        primary = CrispyBunOrange,
        onPrimary = OnOrange,
        secondary = CrispyBunOrangeLight,
        background = DarkBackground,
        onBackground = DarkOnSurface,
        surface = DarkSurface,
        onSurface = DarkOnSurface,
        surfaceVariant = DarkSurfaceVariant,
        onSurfaceVariant = DarkOnSurfaceMuted,
        outline = DarkOutline,
    )

private val LightScheme =
    lightColorScheme(
        primary = LightPrimary,
        onPrimary = LightSurface,
        secondary = CrispyBunOrange,
        background = LightBackground,
        onBackground = LightOnSurface,
        surface = LightSurface,
        onSurface = LightOnSurface,
        surfaceVariant = LightSurfaceVariant,
        onSurfaceVariant = LightOnSurfaceMuted,
        outline = LightOutline,
    )

private val OledScheme =
    DarkScheme.copy(
        background = OledBlack,
        surface = OledBlack,
        surfaceDim = OledBlack,
        surfaceContainerLowest = OledBlack,
        surfaceContainerLow = OledSurfaceContainerLow,
        surfaceContainer = OledSurfaceContainer,
        surfaceContainerHigh = OledSurfaceContainerHigh,
        surfaceContainerHighest = OledSurfaceContainerHighest,
    )

/** Der OLED-Modus erzwingt das dunkle Schema mit reinem Schwarz, unabhängig von der Systemeinstellung. */
@Composable
fun CrunchyMateTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    oledMode: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = darkTheme || oledMode
    CompositionLocalProvider(
        LocalCrunchyMateColors provides if (dark) DarkExtendedColors else LightExtendedColors,
    ) {
        MaterialTheme(
            colorScheme =
                when {
                    oledMode -> OledScheme
                    dark -> DarkScheme
                    else -> LightScheme
                },
            typography = CrunchyMateTypography,
            content = content,
        )
    }
}
