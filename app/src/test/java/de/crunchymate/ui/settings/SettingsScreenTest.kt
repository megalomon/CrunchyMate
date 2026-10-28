package de.crunchymate.ui.settings

import android.text.format.Formatter
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import de.crunchymate.R
import de.crunchymate.core.ffi.Stats
import de.crunchymate.sync.SyncUiState
import de.crunchymate.ui.theme.CrunchyMateTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val SDK = 35
private const val IMAGE_COUNT = 3312L
private const val IMAGE_BYTES = 412_000_000L

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [SDK], qualifiers = "w411dp-h891dp")
class SettingsScreenTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun stats_showImagesTileWithCountAndSize() {
        val state =
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
        rule.setContent { CrunchyMateTheme { SettingsScreen(state, {}, {}, {}, {}, {}) } }
        val size = Formatter.formatShortFileSize(rule.activity, IMAGE_BYTES)
        rule.onNodeWithText(rule.activity.getString(R.string.stat_images)).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(rule.activity.getString(R.string.stat_images_value, IMAGE_COUNT, size)).assertIsDisplayed()
    }
}
