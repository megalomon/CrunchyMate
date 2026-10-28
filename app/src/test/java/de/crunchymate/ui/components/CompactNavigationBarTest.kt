package de.crunchymate.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import de.crunchymate.ui.theme.CrunchyMateTheme
import de.crunchymate.ui.theme.Dimens
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val SDK = 35
private val LABELS = listOf("Eins", "Zwei", "Drei")

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [SDK], qualifiers = "w411dp-h891dp")
class CompactNavigationBarTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun showBar(
        selected: () -> Int,
        onClick: (Int) -> Unit,
    ) {
        rule.setContent {
            CrunchyMateTheme {
                CompactNavigationBar {
                    LABELS.forEachIndexed { index, label ->
                        CompactNavigationItem(
                            selected = selected() == index,
                            onClick = { onClick(index) },
                            icon = { Text("i") },
                            label = label,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun items_haveTabRoleAndReflectSelection() {
        var selected by mutableIntStateOf(1)
        showBar({ selected }, {})
        rule.onNodeWithText("Zwei").assertIsSelected()
        rule.onNodeWithText("Eins").assertIsNotSelected()
        rule.onNodeWithText("Drei").assertIsNotSelected()
        assertEquals(Role.Tab, rule.onNodeWithText("Zwei").fetchSemanticsNode().config[SemanticsProperties.Role])
        selected = 2
        rule.waitForIdle()
        rule.onNodeWithText("Drei").assertIsSelected()
        rule.onNodeWithText("Zwei").assertIsNotSelected()
    }

    @Test
    fun click_invokesCallbackOfThatItem() {
        val clicked = mutableListOf<Int>()
        showBar({ 0 }, { clicked += it })
        rule.onNodeWithText("Drei").performClick()
        rule.onNodeWithText("Eins").performClick()
        assertEquals(listOf(2, 0), clicked)
    }

    @Test
    fun bar_hasCompactHeight() {
        showBar({ 0 }, {})
        // Der Test-Activity hat keine Systemleisten-Abstände; die Zeile der Einträge ist das Maß
        rule.waitForIdle()
        rule
            .onRoot()
            .onChildren()
            .onFirst()
            .assertHeightIsEqualTo(Dimens.NavigationBarHeight)
    }
}
