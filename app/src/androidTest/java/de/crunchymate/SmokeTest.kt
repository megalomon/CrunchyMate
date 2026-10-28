package de.crunchymate

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val LOAD_TIMEOUT_MILLIS = 15_000L
private const val FIXTURE_TITLE_PREFIX = "Fixture"
private const val ENRICHED_TITLE = "Fixture Enriched"
private const val TAG_NAME = "Action"
private val NAV_LABELS = listOf(R.string.nav_search, R.string.nav_watched, R.string.nav_meh, R.string.nav_settings)

@RunWith(AndroidJUnit4::class)
class SmokeTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val targetContext get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun navTab(labelRes: Int) =
        hasText(targetContext.getString(labelRes)) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    private fun fixtureItems() = rule.onAllNodes(hasText(FIXTURE_TITLE_PREFIX, substring = true))

    private fun waitForFixtureItems() {
        rule.waitUntil(LOAD_TIMEOUT_MILLIS) { fixtureItems().fetchSemanticsNodes().isNotEmpty() }
    }

    private fun backButton() = rule.onAllNodes(hasContentDescription(targetContext.getString(R.string.action_back)))

    private fun openEnrichedDetail() {
        waitForFixtureItems()
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(ENRICHED_TITLE))
        rule.onNode(hasText(ENRICHED_TITLE) and hasClickAction()).performClick()
        rule.waitUntil(LOAD_TIMEOUT_MILLIS) { backButton().fetchSemanticsNodes().isNotEmpty() }
    }

    private fun tagChip() = rule.onNode(hasText(TAG_NAME, substring = true) and hasClickAction())

    private fun tagStateDescription(): String? = tagChip().fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)

    @Test
    fun appStarts_andShowsFixtureItems() {
        waitForFixtureItems()
        fixtureItems().onFirst().assertExists()
        rule.onNode(navTab(R.string.nav_search) and isSelected()).assertExists()
    }

    @Test
    fun bottomNavigation_switchesAllTabs() {
        waitForFixtureItems()
        NAV_LABELS.forEach { label ->
            rule.onNode(navTab(label)).performClick()
            rule.onNode(navTab(label) and isSelected()).assertExists()
        }
    }

    @Test
    fun tagChip_cyclesThroughStates() {
        openEnrichedDetail()
        tagChip().performScrollTo()
        val expectedStates =
            listOf(
                R.string.tag_state_none,
                R.string.tag_state_prefer,
                R.string.tag_state_must,
                R.string.tag_state_exclude,
                R.string.tag_state_none,
            ).map(targetContext::getString)
        expectedStates.forEachIndexed { index, expected ->
            if (index > 0) {
                tagChip().performClick()
            }
            rule.waitUntil(LOAD_TIMEOUT_MILLIS) { tagStateDescription() == expected }
            assertEquals(expected, tagStateDescription())
        }
    }

    @Test
    fun openDetail_andNavigateBack() {
        openEnrichedDetail()
        backButton().onFirst().performClick()
        rule.waitUntil(LOAD_TIMEOUT_MILLIS) {
            rule.onAllNodes(navTab(R.string.nav_search) and isSelected()).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
