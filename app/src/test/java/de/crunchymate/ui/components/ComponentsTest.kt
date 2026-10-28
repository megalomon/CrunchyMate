package de.crunchymate.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import de.crunchymate.R
import de.crunchymate.core.ffi.AnimeSummary
import de.crunchymate.core.ffi.ErrorCode
import de.crunchymate.core.ffi.TagState
import de.crunchymate.sync.SyncUiState
import de.crunchymate.ui.theme.CrunchyMateTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private const val SDK = 35
private const val TIMESTAMP_SECONDS = 1_700_000_000L
private val CARD_WIDTH = 160.dp

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [SDK], qualifiers = "w411dp-h891dp")
class ComponentsTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun string(id: Int) = rule.activity.getString(id)

    private fun anime(preferMatches: Int = 0) =
        AnimeSummary(
            id = "a1",
            title = "Testtitel",
            posterUrl = "",
            anilistScore = 80,
            hasTargetDub = true,
            hasTargetSub = true,
            watched = false,
            isMeh = false,
            preferMatches = preferMatches,
        )

    @Test
    fun tagChip_exposesStateDescriptionPerState() {
        var state by mutableStateOf<TagState?>(null)
        rule.setContent {
            CrunchyMateTheme { TagChip("Action", count = null, state = state, onClick = {}) }
        }
        val descriptions =
            listOf<TagState?>(null, TagState.PREFER, TagState.MUST, TagState.EXCLUDE).map {
                state = it
                rule.waitForIdle()
                rule
                    .onNodeWithText("Action")
                    .fetchSemanticsNode()
                    .config[SemanticsProperties.StateDescription]
            }
        assertEquals(4, descriptions.toSet().size)
    }

    @Test
    fun animeCard_showsBoostBadgeOnlyWhenMatches() {
        var matches by mutableIntStateOf(0)
        rule.setContent {
            CrunchyMateTheme { AnimeCard(anime(matches), {}, {}, {}) }
        }
        val badge = rule.activity.resources.getQuantityString(R.plurals.badge_boost_matches, 2, 2)
        rule.onNodeWithText(badge).assertDoesNotExist()
        matches = 2
        rule.waitForIdle()
        rule.onNodeWithText(badge).assertIsDisplayed()
    }

    @Test
    fun animeCard_togglesInvokeCallbacks() {
        var watched = 0
        var meh = 0
        var opened = 0
        rule.setContent {
            // Realistische Rasterzellenbreite auf einem Telefon-Bildschirm
            CrunchyMateTheme {
                Box(Modifier.width(CARD_WIDTH)) { AnimeCard(anime(), { opened++ }, { watched++ }, { meh++ }) }
            }
        }
        rule.onNodeWithContentDescription(string(R.string.action_toggle_watched), useUnmergedTree = true).performClick()
        rule.onNodeWithContentDescription(string(R.string.action_toggle_meh), useUnmergedTree = true).performClick()
        rule.onNodeWithText("Testtitel").performClick()
        assertEquals(1, watched)
        assertEquals(1, meh)
        assertEquals(1, opened)
    }

    @Test
    fun syncCard_showsCancelWhileRunningAndStartOtherwise() {
        var state by mutableStateOf(SyncUiState.Idle)
        rule.setContent {
            CrunchyMateTheme { SyncCard("Katalog", state, onStart = {}, onCancel = {}) }
        }
        rule.onNodeWithContentDescription(string(R.string.sync_start)).assertIsDisplayed()
        rule.onNodeWithContentDescription(string(R.string.sync_cancel)).assertDoesNotExist()
        state = SyncUiState(true, 5, 10, null, null)
        rule.waitForIdle()
        rule.onNodeWithContentDescription(string(R.string.sync_cancel)).assertIsDisplayed()
        rule.onNodeWithContentDescription(string(R.string.sync_start)).assertDoesNotExist()
    }

    @Test
    fun syncCard_formatsLastFinishedFromUnixSeconds() {
        val state = SyncUiState(false, 0, 0, TIMESTAMP_SECONDS, null)
        rule.setContent { CrunchyMateTheme { SyncCard("Katalog", state, {}, {}) } }
        val expected =
            DateTimeFormatter
                .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                .withLocale(Locale.getDefault())
                .withZone(ZoneId.systemDefault())
                .format(Instant.ofEpochSecond(TIMESTAMP_SECONDS))
        rule.onNodeWithText(rule.activity.getString(R.string.sync_last_finished, expected)).assertIsDisplayed()
        // 2023 statt Jahr 55841 bei fälschlicher Millisekunden-Deutung
        assertTrue(expected.contains("2023"))
    }

    @Test
    fun syncCard_showsLastErrorText() {
        val state = SyncUiState(false, 0, 0, TIMESTAMP_SECONDS, ErrorCode.NETWORK)
        rule.setContent { CrunchyMateTheme { SyncCard("Katalog", state, {}, {}) } }
        assertNotNull(
            rule.onNodeWithText(rule.activity.getString(R.string.sync_last_error, string(R.string.error_network))).fetchSemanticsNode(),
        )
    }

    @Test
    fun syncCard_cancelledShowsStatusWithoutErrorLine() {
        val state = SyncUiState(false, 3, 10, TIMESTAMP_SECONDS, null, cancelled = true)
        rule.setContent { CrunchyMateTheme { SyncCard("Katalog", state, {}, {}) } }
        rule.onNodeWithText(string(R.string.sync_status_cancelled)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.sync_status_idle)).assertDoesNotExist()
        rule.onNodeWithText(rule.activity.getString(R.string.sync_last_error, string(R.string.error_cancelled))).assertDoesNotExist()
    }
}
