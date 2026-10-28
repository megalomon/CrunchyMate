package de.crunchymate.ui.search

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import de.crunchymate.R
import de.crunchymate.core.ffi.LanguageFilter
import de.crunchymate.core.ffi.NameCount
import de.crunchymate.core.ffi.SearchState
import de.crunchymate.core.ffi.SortOrder
import de.crunchymate.core.ffi.TagSelection
import de.crunchymate.core.ffi.TagState
import de.crunchymate.core.ffi.WatchedFilter
import de.crunchymate.ui.theme.CrunchyMateTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val SDK = 35
private const val COMEDY_COUNT = 671L

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [SDK], qualifiers = "w411dp-h891dp")
class SearchControlsTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun string(id: Int) = rule.activity.getString(id)

    private fun state(activeTags: List<TagSelection> = emptyList()) =
        SearchUiState(
            search = SearchState("", null, LanguageFilter.ALL, WatchedFilter.ALL, SortOrder.TITLE, activeTags),
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
        onRemoved: (String) -> Unit = {},
    ) = SearchActions(
        onQueryChange = {},
        onGenreSelected = onGenre,
        onLanguageSelected = {},
        onWatchedSelected = {},
        onSortSelected = {},
        onTagTapped = {},
        onTagRemoved = onRemoved,
        onTagQueryChange = {},
        onClearTags = {},
    )

    @Test
    fun dropdowns_showLabelAndCurrentValue() {
        rule.setContent { CrunchyMateTheme { SearchControls(state(), "", actions()) } }
        rule.onNodeWithText(string(R.string.filter_watched_label)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.filter_watched_all)).assertIsDisplayed()
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
    fun clearTagsButton_hiddenWithoutActiveTags() {
        rule.setContent { CrunchyMateTheme { SearchControls(state(), "", actions()) } }
        rule.onNodeWithText(string(R.string.tags_reset)).assertDoesNotExist()
    }

    @Test
    fun clearTagsButton_visibleWithActiveTagsAndChipRemovable() {
        var removed = ""
        val active = listOf(TagSelection("Magie", TagState.PREFER))
        rule.setContent { CrunchyMateTheme { SearchControls(state(active), "", actions(onRemoved = { removed = it })) } }
        rule.onNodeWithText(string(R.string.tags_reset)).assertIsDisplayed()
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.tags_remove, "Magie"), useUnmergedTree = true).performClick()
        assertEquals("Magie", removed)
    }
}
