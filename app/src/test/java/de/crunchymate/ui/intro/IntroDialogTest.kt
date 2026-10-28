package de.crunchymate.ui.intro

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import de.crunchymate.R
import de.crunchymate.core.CoreGateway
import de.crunchymate.core.FakeCore
import de.crunchymate.core.SettingsStore
import de.crunchymate.core.sampleSettings
import de.crunchymate.ui.theme.CrunchyMateTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val SDK = 35

private val INTRO_TEXTS =
    listOf(
        R.string.intro_disclaimer_title,
        R.string.intro_disclaimer,
        R.string.intro_purpose_title,
        R.string.intro_purpose,
        R.string.intro_free_title,
        R.string.intro_free,
        R.string.intro_first_step_title,
        R.string.intro_first_step,
        R.string.intro_privacy_title,
        R.string.intro_privacy,
    )

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [SDK], qualifiers = "w411dp-h891dp")
class IntroDialogTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun string(id: Int) = rule.activity.getString(id)

    private fun loadedStore(core: FakeCore): SettingsStore {
        val store = SettingsStore(CoreGateway(core, Dispatchers.Unconfined), CoroutineScope(Dispatchers.Unconfined), Dispatchers.Unconfined)
        runBlocking { store.load() }
        return store
    }

    @Test
    fun firstStart_showsAllSectionsAndConfirmHidesDialogPermanently() {
        val core = FakeCore(storedSettings = sampleSettings(introSeen = false))
        val store = loadedStore(core)
        rule.setContent { CrunchyMateTheme { IntroDialogHost(store) } }

        rule.onNodeWithText(string(R.string.intro_title)).assertIsDisplayed()
        INTRO_TEXTS.forEach { rule.onNodeWithText(string(it)).assertIsDisplayed() }
        rule.onNodeWithText(string(R.string.intro_confirm)).performClick()
        rule.waitForIdle()

        rule.onNodeWithText(string(R.string.intro_title)).assertDoesNotExist()
        assertTrue(store.settings.value.introSeen)
        runBlocking { store.flush() }
        assertTrue(core.savedSettings.last().introSeen)
    }

    @Test
    fun laterStart_withStoredFlag_showsNoDialog() {
        val store = loadedStore(FakeCore(storedSettings = sampleSettings(introSeen = true)))
        rule.setContent { CrunchyMateTheme { IntroDialogHost(store) } }

        rule.onNodeWithText(string(R.string.intro_title)).assertDoesNotExist()
        assertEquals(true, store.settings.value.introSeen)
    }

    @Test
    fun back_finishesActivityWithoutSettingFlag() {
        val core = FakeCore(storedSettings = sampleSettings(introSeen = false))
        val store = loadedStore(core)
        rule.setContent { CrunchyMateTheme { IntroDialogHost(store) } }

        Espresso.pressBack()
        rule.waitForIdle()

        assertTrue(rule.activity.isFinishing)
        assertEquals(false, store.settings.value.introSeen)
        runBlocking { store.flush() }
        assertEquals(0, core.savedSettings.size)
    }
}
