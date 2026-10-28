package de.crunchymate.ui.theme

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val SDK = 35

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [SDK])
class ThemeTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun backgroundAndSurface(
        darkTheme: Boolean,
        oledMode: Boolean,
    ): Pair<Color, Color> {
        var result = Color.Unspecified to Color.Unspecified
        rule.setContent {
            CrunchyMateTheme(darkTheme = darkTheme, oledMode = oledMode) {
                result = MaterialTheme.colorScheme.background to MaterialTheme.colorScheme.surface
            }
        }
        return result
    }

    @Test
    fun oledMode_isPureBlackEvenWhenSystemIsLight() {
        assertEquals(OledBlack to OledBlack, backgroundAndSurface(darkTheme = false, oledMode = true))
    }

    @Test
    fun oledMode_isPureBlackWhenSystemIsDark() {
        assertEquals(OledBlack to OledBlack, backgroundAndSurface(darkTheme = true, oledMode = true))
    }

    @Test
    fun oledOff_followsSystem() {
        assertEquals(LightBackground to LightSurface, backgroundAndSurface(darkTheme = false, oledMode = false))
    }

    @Test
    fun oledOff_darkSystemKeepsRegularDarkScheme() {
        assertEquals(DarkBackground to DarkSurface, backgroundAndSurface(darkTheme = true, oledMode = false))
    }
}
