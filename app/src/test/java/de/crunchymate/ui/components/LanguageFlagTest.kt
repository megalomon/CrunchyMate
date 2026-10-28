package de.crunchymate.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import de.crunchymate.R
import de.crunchymate.ui.theme.CrunchyMateTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val SDK = 35

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [SDK], qualifiers = "w411dp-h891dp")
class LanguageFlagTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun flagDrawable_mapsAllSupportedLocales() {
        val expected =
            mapOf(
                "de-DE" to R.drawable.ic_flag_de,
                "en-US" to R.drawable.ic_flag_us,
                "es-419" to R.drawable.ic_flag_mx,
                "es-ES" to R.drawable.ic_flag_es,
                "fr-FR" to R.drawable.ic_flag_fr,
                "it-IT" to R.drawable.ic_flag_it,
                "pt-BR" to R.drawable.ic_flag_br,
                "ru-RU" to R.drawable.ic_flag_ru,
                "ar-SA" to R.drawable.ic_flag_sa,
                "hi-IN" to R.drawable.ic_flag_in,
            )
        expected.forEach { (locale, drawable) -> assertEquals(locale, drawable, flagDrawable(locale)) }
    }

    @Test
    fun flagDrawable_unknownLocaleHasNoFlag() {
        assertNull(flagDrawable("ja-JP"))
        assertNull(flagDrawable(""))
    }

    @Test
    fun flagState_dubWinsOverSub() {
        assertEquals(FlagState.DUB, flagState(hasTargetDub = true, hasTargetSub = true))
        assertEquals(FlagState.DUB, flagState(hasTargetDub = true, hasTargetSub = false))
        assertEquals(FlagState.SUB_ONLY, flagState(hasTargetDub = false, hasTargetSub = true))
        assertEquals(FlagState.NONE, flagState(hasTargetDub = false, hasTargetSub = false))
    }

    @Test
    fun greyTriangle_coversUpperLeftOfDiagonalFromBottomLeftToTopRight() {
        val points = greyTrianglePoints(Size(20f, 15f))
        assertEquals(listOf(Offset(0f, 0f), Offset(20f, 0f), Offset(0f, 15f)), points)
    }

    @Test
    fun languageFlag_describesEachState() {
        val descriptions =
            mapOf(
                FlagState.DUB to R.string.flag_dub_description,
                FlagState.SUB_ONLY to R.string.flag_sub_only_description,
                FlagState.NONE to R.string.flag_none_description,
            )
        var current by mutableStateOf(FlagState.DUB)
        rule.setContent { CrunchyMateTheme { LanguageFlag("de-DE", current) } }
        descriptions.forEach { (state, text) ->
            current = state
            rule.waitForIdle()
            rule.onNodeWithContentDescription(rule.activity.getString(text)).assertIsDisplayed()
        }
    }

    @Test
    fun languageFlag_unknownLocaleShowsNothing() {
        rule.setContent { CrunchyMateTheme { LanguageFlag("ja-JP", FlagState.DUB) } }
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.flag_dub_description)).assertDoesNotExist()
    }
}
