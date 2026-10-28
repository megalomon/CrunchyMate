package de.crunchymate.ui.detail

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.assertTouchWidthIsEqualTo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import de.crunchymate.R
import de.crunchymate.core.sampleDetail
import de.crunchymate.ui.theme.CrunchyMateTheme
import de.crunchymate.ui.theme.Dimens
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val SDK = 35

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [SDK], qualifiers = "w411dp-h891dp")
class DetailScreenTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun string(id: Int) = rule.activity.getString(id)

    private fun show(
        watched: Boolean = false,
        meh: Boolean = false,
        onWatched: () -> Unit = {},
        onMeh: () -> Unit = {},
    ) {
        val state = DetailUiState(sampleDetail("a1").copy(watched = watched, isMeh = meh), false, null, emptyList(), "de-DE")
        rule.setContent { CrunchyMateTheme { DetailScreen(state, {}, onWatched, onMeh, {}) } }
    }

    private fun stateOf(description: String) =
        rule.onNodeWithContentDescription(description).fetchSemanticsNode().config[SemanticsProperties.StateDescription]

    @Test
    fun flag_showsSubOnlyStateOfSampleDetail() {
        show()
        rule.onNodeWithContentDescription(string(R.string.flag_sub_only_description)).assertIsDisplayed()
    }

    @Test
    fun toggles_areIconOnlyWithShortLabelsAndState() {
        show(watched = true)
        rule.onNodeWithContentDescription(string(R.string.action_toggle_watched)).assertIsDisplayed()
        rule.onNodeWithContentDescription(string(R.string.action_toggle_meh)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.action_toggle_watched)).assertDoesNotExist()
        assertEquals(string(R.string.state_marked), stateOf(string(R.string.action_toggle_watched)))
        assertEquals(string(R.string.state_not_marked), stateOf(string(R.string.action_toggle_meh)))
    }

    @Test
    fun toggles_keepMinimumTouchTargetAndInvokeCallbacks() {
        var watched = 0
        var meh = 0
        show(onWatched = { watched++ }, onMeh = { meh++ })
        listOf(R.string.action_toggle_watched, R.string.action_toggle_meh).forEach {
            rule
                .onNodeWithContentDescription(string(it))
                .assertTouchWidthIsEqualTo(Dimens.MinTouchTarget)
                .assertTouchHeightIsEqualTo(Dimens.MinTouchTarget)
        }
        rule.onNodeWithContentDescription(string(R.string.action_toggle_watched)).performClick()
        rule.onNodeWithContentDescription(string(R.string.action_toggle_meh)).performClick()
        assertEquals(1, watched)
        assertEquals(1, meh)
    }
}
