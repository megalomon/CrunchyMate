package de.crunchymate.ui.settings

import android.text.format.Formatter
import androidx.activity.ComponentActivity
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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
            contentLocaleChangePending = false,
        )

    private fun tileNode(label: Int) = rule.onNodeWithTag(statTileTag(label)).fetchSemanticsNode()

    @Test
    fun stats_showImageCountAndImageStorageAsSeparateItems() {
        rule.setContent { CrunchyMateTheme { SettingsScreen(stateWithStats(), {}, {}, {}, {}, {}) } }
        val size = Formatter.formatShortFileSize(rule.activity, IMAGE_BYTES)
        rule.onNodeWithText(rule.activity.getString(R.string.stat_images)).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(IMAGE_COUNT.toString()).assertIsDisplayed()
        rule.onNodeWithText(rule.activity.getString(R.string.stat_image_storage)).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(size).assertIsDisplayed()
    }

    @Test
    fun stats_bothColumnsHaveEqualWidthEvenForTheLoneLastTile() {
        rule.setContent { CrunchyMateTheme { SettingsScreen(stateWithStats(), {}, {}, {}, {}, {}) } }
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
}
