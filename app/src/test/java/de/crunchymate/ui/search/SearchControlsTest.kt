package de.crunchymate.ui.search

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import de.crunchymate.R
import de.crunchymate.core.ffi.LanguageFilter
import de.crunchymate.core.ffi.NameCount
import de.crunchymate.core.ffi.SearchState
import de.crunchymate.core.ffi.SortOrder
import de.crunchymate.core.ffi.TagSelection
import de.crunchymate.core.ffi.TagState
import de.crunchymate.core.ffi.WatchedFilter
import de.crunchymate.ui.components.TagChip
import de.crunchymate.ui.theme.CrunchyMateTheme
import de.crunchymate.ui.theme.Dimens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val SDK = 35
private const val COMEDY_COUNT = 671L

// Robolectric misst Text sehr schmal; der Name muss breiter als der gekürzte Innenabstand sein
private val LONG_NAME = "Magie".repeat(10)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [SDK], qualifiers = "w411dp-h891dp")
class SearchControlsTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun string(id: Int) = rule.activity.getString(id)

    private fun state(
        activeTags: List<TagSelection> = emptyList(),
        language: LanguageFilter = LanguageFilter.ALL,
    ) = SearchUiState(
        search = SearchState("", null, language, WatchedFilter.ALL, SortOrder.TITLE, activeTags),
        genres = listOf(NameCount("Comedy", COMEDY_COUNT)),
        tags = listOf(NameCount("Magie", 3L)),
        tagQuery = "",
        visibleTags = listOf(NameCount("Magie", 3L)),
        activeTags = activeTags,
        total = 1L,
        totalError = false,
    )

    private fun actions(
        onGenre: (String?) -> Unit = {},
        onLanguage: (LanguageFilter) -> Unit = {},
        onCleared: () -> Unit = {},
        onTapped: (String) -> Unit = {},
    ) = SearchActions(
        onQueryChange = {},
        onGenreSelected = onGenre,
        onLanguageSelected = onLanguage,
        onSortSelected = {},
        onTagTapped = onTapped,
        onTagQueryChange = {},
        onClearTags = onCleared,
    )

    @Test
    fun dropdowns_showLabelAndCurrentValue() {
        rule.setContent { CrunchyMateTheme { SearchControls(state(), "", actions()) } }
        rule.onNodeWithText(string(R.string.filter_genre_all)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.sort_label)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.sort_title)).assertIsDisplayed()
    }

    @Test
    fun selectingGenre_showsCountAndInvokesCallback() {
        var selected: String? = "unset"
        rule.setContent { CrunchyMateTheme { SearchControls(state(), "", actions(onGenre = { selected = it })) } }
        rule.onNodeWithText(string(R.string.filter_genre_all)).performClick()
        rule.onNodeWithText("Comedy ($COMEDY_COUNT)").performClick()
        assertEquals("Comedy", selected)
    }

    @Test
    fun searchControls_offersNoWatchedFilter() {
        rule.setContent { CrunchyMateTheme { SearchControls(state(), "", actions()) } }
        rule.onNodeWithText("Gesehene").assertDoesNotExist()
        rule.onNodeWithText("Mit anzeigen").assertDoesNotExist()
    }

    @Test
    fun languageChips_toggleIndependently() {
        val selected = mutableListOf<LanguageFilter>()
        var language by mutableStateOf(LanguageFilter.ALL)
        rule.setContent {
            CrunchyMateTheme {
                SearchControls(
                    state(language = language),
                    "",
                    actions(onLanguage = {
                        selected += it
                        language = it
                    }),
                )
            }
        }
        rule.onNodeWithText(string(R.string.filter_language_dub_chip)).performClick()
        rule.onNodeWithText(string(R.string.filter_language_sub_chip)).performClick()
        rule.onNodeWithText(string(R.string.filter_language_dub_chip)).performClick()
        assertEquals(listOf(LanguageFilter.DUB, LanguageFilter.BOTH, LanguageFilter.SUB), selected)
    }

    @Test
    fun clearTagsButton_hiddenWithoutActiveTags() {
        rule.setContent { CrunchyMateTheme { SearchControls(state(), "", actions()) } }
        rule.onNodeWithContentDescription(string(R.string.tags_reset)).assertDoesNotExist()
    }

    @Test
    fun clearTagsButton_visibleWithActiveTags() {
        var cleared = 0
        val active = listOf(TagSelection("Magie", TagState.PREFER))
        rule.setContent {
            CrunchyMateTheme { SearchControls(state(active), "", actions(onCleared = { cleared++ })) }
        }
        rule.onNodeWithContentDescription(string(R.string.tags_reset)).assertIsDisplayed().performClick()
        assertEquals(1, cleared)
    }

    @Test
    fun activeTagChip_tapCyclesInsteadOfRemoving() {
        var tapped = ""
        val active = listOf(TagSelection("Magie", TagState.PREFER))
        rule.setContent { CrunchyMateTheme { SearchControls(state(active), "", actions(onTapped = { tapped = it })) } }
        rule.onNodeWithText("Magie").performClick()
        assertEquals("Magie", tapped)
    }

    @Test
    fun languageChips_stackedAtRowEndAndNotTallerThanTagField() {
        rule.setContent { CrunchyMateTheme { SearchControls(state(), "", actions()) } }
        val sub = rule.onNodeWithText(string(R.string.filter_language_sub_chip)).fetchSemanticsNode().boundsInRoot
        val dub = rule.onNodeWithText(string(R.string.filter_language_dub_chip)).fetchSemanticsNode().boundsInRoot
        val field = rule.onNode(hasSetTextAction() and hasText(string(R.string.tags_filter_label))).fetchSemanticsNode().boundsInRoot
        assertTrue(sub.top < dub.top)
        assertTrue(sub.bottom <= dub.top)
        assertEquals(sub.left, dub.left, 1f)
        assertEquals(sub.width, dub.width, 1f)
        assertTrue(dub.bottom - sub.top <= field.height)
        assertTrue(field.right <= sub.left)
    }

    @Test
    fun tagChip_horizontalPaddingIsTrimmedButHeightKept() {
        var chipSize = IntSize.Zero
        var referenceSize = IntSize.Zero
        var density = Density(1f)
        rule.setContent {
            density = LocalDensity.current
            CrunchyMateTheme {
                Column {
                    TagChip(name = LONG_NAME, count = null, state = null, onClick = {}, modifier = Modifier.onSizeChanged { chipSize = it })
                    // Referenz: derselbe Chip mit dem unveränderten M3-Innenabstand
                    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                        FilterChip(
                            selected = false,
                            onClick = {},
                            label = { Text(LONG_NAME, style = MaterialTheme.typography.labelMedium) },
                            modifier = Modifier.onSizeChanged { referenceSize = it },
                        )
                    }
                }
            }
        }
        rule.waitForIdle()
        val trimmedPx = with(density) { Dimens.TagChipLabelInset.roundToPx() * 2 }
        assertEquals(referenceSize.width - trimmedPx, chipSize.width)
        assertEquals(with(density) { Dimens.TagChipHeight.roundToPx() }, chipSize.height)
    }
}
