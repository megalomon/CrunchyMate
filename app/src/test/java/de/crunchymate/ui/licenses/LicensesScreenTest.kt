package de.crunchymate.ui.licenses

import androidx.activity.ComponentActivity
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import de.crunchymate.R
import de.crunchymate.ui.theme.CrunchyMateTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val SDK = 35
private const val LICENSE_BODY = "Volltext der Testlizenz"

private val NOTICES =
    OpenSourceNotices(
        components =
            listOf(
                NoticeComponent(NoticeGroup.APP, "CrunchyMate", "", "Apache-2.0", "Copyright 2026 Megalomon", listOf("Apache-2.0")),
                NoticeComponent(NoticeGroup.RUST, "beispiel-crate", "1.2.3", "MIT", "Copyright (c) Beispiel", listOf("MIT")),
                NoticeComponent(NoticeGroup.FLAGS, "flag-icons", "7.6.0", "MIT (flag-icons)", "", listOf("MIT")),
            ),
        licenses = listOf(LicenseText("MIT", "MIT-Lizenz", LICENSE_BODY)),
    )

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [SDK], qualifiers = "w411dp-h891dp")
class LicensesScreenTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun string(
        id: Int,
        vararg args: Any,
    ) = rule.activity.getString(id, *args)

    @Test
    fun loaded_showsComponentsGroupsAndExpandableLicenseText() {
        rule.setContent { CrunchyMateTheme { LicensesScreen(LicensesUiState.Loaded(NOTICES), {}) } }
        rule.onNodeWithText(string(R.string.licenses_group_rust)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.licenses_component_line, "beispiel-crate", "1.2.3")).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.licenses_component_detail, "MIT", "Copyright (c) Beispiel")).assertIsDisplayed()
        rule.onNodeWithText(LICENSE_BODY).assertDoesNotExist()
        rule.onNodeWithText("MIT").performClick()
        rule.onNodeWithText(LICENSE_BODY).assertIsDisplayed()
    }

    @Test
    fun loaded_listsOwnLicenseBeforeLibraries() {
        rule.setContent { CrunchyMateTheme { LicensesScreen(LicensesUiState.Loaded(NOTICES), {}) } }
        rule.onNodeWithText(string(R.string.licenses_group_app)).assertIsDisplayed()
        rule.onNodeWithText("CrunchyMate").assertIsDisplayed()
        rule.onNodeWithText(string(R.string.licenses_component_detail, "Apache-2.0", "Copyright 2026 Megalomon")).assertIsDisplayed()
        val appTop =
            rule
                .onNodeWithText(string(R.string.licenses_group_app))
                .fetchSemanticsNode()
                .boundsInRoot.top
        val rustTop =
            rule
                .onNodeWithText(string(R.string.licenses_group_rust))
                .fetchSemanticsNode()
                .boundsInRoot.top
        assertTrue(appTop < rustTop)
    }

    @Test
    fun failed_showsErrorState() {
        rule.setContent { CrunchyMateTheme { LicensesScreen(LicensesUiState.Failed, {}) } }
        rule.onNodeWithText(string(R.string.licenses_error_title)).assertIsDisplayed()
    }

    @Test
    fun backButton_invokesCallback() {
        var backs = 0
        rule.setContent { CrunchyMateTheme { LicensesScreen(LicensesUiState.Loading, { backs++ }) } }
        rule.onNodeWithContentDescription(string(R.string.action_back)).performClick()
        assertEquals(1, backs)
    }
}
