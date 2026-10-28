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
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.assertTouchWidthIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import de.crunchymate.R
import de.crunchymate.core.ffi.AnimeSummary
import de.crunchymate.core.ffi.ErrorCode
import de.crunchymate.core.ffi.SyncKind
import de.crunchymate.core.ffi.SyncPhase
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
private val MIN_TOUCH_TARGET = 48.dp
private val OVERLAY_INSET = 6.dp
private val OVERLAY_GAP = 4.dp

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [SDK], qualifiers = "w411dp-h891dp")
class ComponentsTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun string(
        id: Int,
        vararg args: Any,
    ) = rule.activity.getString(id, *args)

    private fun anime(
        score: Int = 80,
        posterPath: String = "",
    ) = AnimeSummary(
        id = "a1",
        title = "Testtitel",
        posterPath = posterPath,
        anilistScore = score,
        hasTargetDub = true,
        hasTargetSub = true,
        watched = false,
        isMeh = false,
        preferMatches = 0,
        episodeCount = 128,
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
    fun animeCard_togglesInvokeCallbacks() {
        var watched = 0
        var meh = 0
        var opened = 0
        rule.setContent {
            // Realistische Rasterzellenbreite auf einem Telefon-Bildschirm
            CrunchyMateTheme {
                Box(Modifier.width(CARD_WIDTH)) { AnimeCard(anime(), "de-DE", { opened++ }, { watched++ }, { meh++ }) }
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
            CrunchyMateTheme { SyncCard("Katalog", SyncKind.CATALOG, state, onStart = {}, onCancel = {}) }
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
        rule.setContent { CrunchyMateTheme { SyncCard("Katalog", SyncKind.CATALOG, state, {}, {}) } }
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
        rule.setContent { CrunchyMateTheme { SyncCard("Katalog", SyncKind.CATALOG, state, {}, {}) } }
        assertNotNull(
            rule.onNodeWithText(rule.activity.getString(R.string.sync_last_error, string(R.string.error_network))).fetchSemanticsNode(),
        )
    }

    @Test
    fun syncCard_cancelledShowsStatusWithoutErrorLine() {
        val state = SyncUiState(false, 3, 10, TIMESTAMP_SECONDS, null, cancelled = true)
        rule.setContent { CrunchyMateTheme { SyncCard("Katalog", SyncKind.CATALOG, state, {}, {}) } }
        rule.onNodeWithText(string(R.string.sync_status_cancelled)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.sync_status_idle)).assertDoesNotExist()
        rule.onNodeWithText(rule.activity.getString(R.string.sync_last_error, string(R.string.error_cancelled))).assertDoesNotExist()
    }

    @Test
    fun animeCard_showsScoreBadgeOnlyWhenScorePositive() {
        var score by mutableIntStateOf(0)
        rule.setContent { CrunchyMateTheme { AnimeCard(anime(score = score), "de-DE", {}, {}, {}) } }
        rule.onNodeWithText(string(R.string.badge_score, 71)).assertDoesNotExist()
        score = 71
        rule.waitForIdle()
        rule.onNodeWithText(string(R.string.badge_score, 71)).assertIsDisplayed()
    }

    @Test
    fun animeCard_showsEpisodeCount() {
        rule.setContent { CrunchyMateTheme { AnimeCard(anime(), "de-DE", {}, {}, {}) } }
        rule.onNodeWithText(string(R.string.card_episodes, 128)).assertIsDisplayed()
    }

    @Test
    fun animeCard_withoutPosterPathShowsPlaceholder() {
        rule.setContent { CrunchyMateTheme { AnimeCard(anime(posterPath = ""), "de-DE", {}, {}, {}) } }
        rule.onNodeWithTag(IMAGE_PLACEHOLDER_TAG, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun animeCard_toggleButtonsKeepMinimumTouchTarget() {
        rule.setContent { CrunchyMateTheme { AnimeCard(anime(), "de-DE", {}, {}, {}) } }
        rule.onNodeWithContentDescription(string(R.string.action_toggle_watched)).assertTouchHeightIsEqualTo(MIN_TOUCH_TARGET)
        rule.onNodeWithContentDescription(string(R.string.action_toggle_meh)).assertTouchHeightIsEqualTo(MIN_TOUCH_TARGET)
    }

    @Test
    fun animeCard_flagDescribesAvailability() {
        rule.setContent { CrunchyMateTheme { AnimeCard(anime(), "de-DE", {}, {}, {}) } }
        rule.onNodeWithContentDescription(string(R.string.flag_dub_description)).assertIsDisplayed()
    }

    @Test
    fun syncCard_showsPhaseNameAndProgress() {
        var state by mutableStateOf(SyncUiState(true, 5, 10, null, null, phase = SyncPhase.ITEMS))
        rule.setContent { CrunchyMateTheme { SyncCard("Anreicherung", SyncKind.METADATA, state, {}, {}) } }
        rule.onNodeWithText(string(R.string.sync_phase_progress, string(R.string.sync_phase_enrichment), 5, 10)).assertIsDisplayed()
        state = SyncUiState(true, 7, 3300, null, null, phase = SyncPhase.IMAGES)
        rule.waitForIdle()
        rule.onNodeWithText(string(R.string.sync_phase_progress, string(R.string.sync_phase_images), 7, 3300)).assertIsDisplayed()
    }

    @Test
    fun syncCard_catalogItemsPhaseIsNamedCatalog() {
        val state = SyncUiState(true, 5, 10, null, null)
        rule.setContent { CrunchyMateTheme { SyncCard("Katalog", SyncKind.CATALOG, state, {}, {}) } }
        rule.onNodeWithText(string(R.string.sync_phase_progress, string(R.string.sync_phase_catalog), 5, 10)).assertIsDisplayed()
    }

    @Test
    fun animeCard_overlaysShareInsetAndAlign() {
        rule.setContent {
            CrunchyMateTheme { Box(Modifier.width(CARD_WIDTH)) { AnimeCard(anime(), "de-DE", {}, {}, {}) } }
        }

        fun bounds(tag: String) = rule.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val poster = bounds(IMAGE_PLACEHOLDER_TAG)
        val watched = bounds(WATCHED_CIRCLE_TAG)
        val meh = bounds(MEH_CIRCLE_TAG)
        val score = bounds(SCORE_BADGE_TAG)
        assertEquals(OVERLAY_INSET, poster.right - watched.right)
        assertEquals(OVERLAY_INSET, watched.top - poster.top)
        assertEquals(OVERLAY_INSET, score.left - poster.left)
        assertEquals(watched.top, score.top)
        assertEquals(watched.bottom - watched.top, score.bottom - score.top)
        assertEquals(OVERLAY_GAP, meh.top - watched.bottom)
        rule.onNodeWithContentDescription(string(R.string.action_toggle_watched)).assertTouchWidthIsEqualTo(MIN_TOUCH_TARGET)
    }
}
