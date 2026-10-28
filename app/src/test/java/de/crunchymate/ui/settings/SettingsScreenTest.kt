package de.crunchymate.ui.settings

import android.text.format.Formatter
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import de.crunchymate.R
import de.crunchymate.core.ffi.Stats
import de.crunchymate.sync.SyncUiState
import de.crunchymate.ui.theme.CrunchyMateTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val SDK = 35
private const val IMAGE_COUNT = 3312L
private const val IMAGE_BYTES = 412_000_000L
private const val ROUNDING_TOLERANCE_PX = 1

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [SDK], qualifiers = "w411dp-h891dp")
class SettingsScreenTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun stateWithStats() =
        SettingsUiState(
            catalog = SyncUiState.Idle,
            metadata = SyncUiState.Idle,
            stats = Stats(5, 1, 2, 3, 4, IMAGE_COUNT, IMAGE_BYTES),
            catalogCountry = null,
            contentLocale = "de-DE",
            availableLocales = listOf("de-DE"),
            hideWatched = false,
            oledMode = false,
            showAdult = true,
            showAgeRating = true,
            contentLocaleChangePending = false,
        )

    private fun tileNode(label: Int) = rule.onNodeWithTag(statTileTag(label)).fetchSemanticsNode()

    @Test
    fun stats_showImageCountAndImageStorageAsSeparateItems() {
        rule.setContent { CrunchyMateTheme { SettingsScreen(stateWithStats(), {}, {}, {}, {}, {}, {}, {}, {}) } }
        val size = Formatter.formatShortFileSize(rule.activity, IMAGE_BYTES)
        rule.onNodeWithText(rule.activity.getString(R.string.stat_images)).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(IMAGE_COUNT.toString()).assertIsDisplayed()
        rule.onNodeWithText(rule.activity.getString(R.string.stat_image_storage)).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(size).assertIsDisplayed()
    }

    @Test
    fun stats_bothColumnsHaveEqualWidthEvenForTheLoneLastTile() {
        rule.setContent { CrunchyMateTheme { SettingsScreen(stateWithStats(), {}, {}, {}, {}, {}, {}, {}, {}) } }
        val left = tileNode(R.string.stat_total)
        val right = tileNode(R.string.stat_watched)
        val lone = tileNode(R.string.stat_image_storage)
        assertTrue(left.size.width > 0)
        assertEqualWidth(left.size.width, right.size.width)
        assertEqualWidth(left.size.width, lone.size.width)
        assertEquals(left.positionInRoot.x, lone.positionInRoot.x, 0f)
        assertTrue(right.positionInRoot.x > left.positionInRoot.x)
    }

    // Bei ungerader Pixelbreite verteilt Compose den Rest auf eine der beiden Spalten
    private fun assertEqualWidth(
        expected: Int,
        actual: Int,
    ) = assertTrue("$expected != $actual", kotlin.math.abs(expected - actual) <= ROUNDING_TOLERANCE_PX)

    @Test
    fun adultSwitches_showLabelsAndReportChanges() {
        var adult: Boolean? = null
        var age: Boolean? = null
        rule.setContent {
            CrunchyMateTheme {
                SettingsScreen(stateWithStats(), {}, {}, {}, {}, {}, { adult = it }, { age = it }, {})
            }
        }
        rule.onNodeWithText(rule.activity.getString(R.string.settings_show_adult)).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(rule.activity.getString(R.string.settings_show_age_rating)).performScrollTo().assertIsDisplayed()
        // Reihenfolge der Schalter: Gesehene ausblenden, OLED-Modus, 18+, Altersfreigabe
        rule.onAllNodes(isToggleable())[2].performScrollTo().performClick()
        rule.onAllNodes(isToggleable())[3].performScrollTo().performClick()
        assertEquals(false, adult)
        assertEquals(false, age)
    }

    private fun showInfoDialog() {
        rule.setContent { CrunchyMateTheme { SettingsScreen(stateWithStats(), {}, {}, {}, {}, {}, {}, {}, {}) } }
        rule.onNodeWithText(rule.activity.getString(R.string.settings_info)).performScrollTo().performClick()
    }

    @Test
    fun infoButton_showsIntroAndConfirmOnlyCloses() {
        showInfoDialog()
        rule.onNodeWithText(rule.activity.getString(R.string.intro_title)).assertIsDisplayed()

        rule.onNodeWithText(rule.activity.getString(R.string.intro_confirm)).performClick()
        rule.waitForIdle()

        rule.onNodeWithText(rule.activity.getString(R.string.intro_title)).assertDoesNotExist()
        assertTrue(!rule.activity.isFinishing)
    }

    @OptIn(InternalComposeUiApi::class)
    @Test
    fun infoDialog_backClosesDialogWithoutFinishingActivity() {
        showInfoDialog()

        // Der Dialog hat ein eigenes Fenster; Espresso.pressBack träfe die Activity
        val dialogView = (rule.onNode(isDialog()).fetchSemanticsNode().root as ViewRootForTest).view.rootView
        dialogView.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
        dialogView.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
        rule.waitForIdle()

        rule.onNodeWithText(rule.activity.getString(R.string.intro_title)).assertDoesNotExist()
        assertTrue(!rule.activity.isFinishing)
    }

    @Test
    fun appLanguageDropdown_listsAllLanguagesAndSelectingSetsApplicationLocale() {
        rule.setContent { CrunchyMateTheme { SettingsScreen(stateWithStats(), {}, {}, {}, {}, {}, {}, {}, {}) } }
        rule.onNodeWithText(rule.activity.getString(R.string.settings_app_language)).performScrollTo().performClick()
        rule.onNodeWithText("Deutsch").assertIsDisplayed()
        rule.onNodeWithText("English").assertIsDisplayed()
        rule.onNodeWithText("Boykisser").assertIsDisplayed().performClick()
        rule.waitForIdle()

        assertEquals(AppLanguage.BOYKISSER, rule.activity.currentAppLanguage())
    }

    @Test
    fun oledSwitch_reportsNewValue() {
        var enabled: Boolean? = null
        rule.setContent {
            CrunchyMateTheme { SettingsScreen(stateWithStats(), {}, {}, {}, {}, { enabled = it }, {}, {}, {}) }
        }
        // Reihenfolge der Schalter: Gesehene ausblenden, OLED-Modus
        rule.onAllNodes(isToggleable())[1].performScrollTo().performClick()
        assertEquals(true, enabled)
    }
}
