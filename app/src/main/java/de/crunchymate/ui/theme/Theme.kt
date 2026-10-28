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

@Composable
fun CrunchyMateTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalCrunchyMateColors provides if (darkTheme) DarkExtendedColors else LightExtendedColors,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = CrunchyMateTypography,
            content = content,
        )
    }
}
