package de.crunchymate.ui.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import de.crunchymate.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

private const val SDK = 35
private const val RES_DIR = "src/main/res"

private fun stringNames(valuesDir: String): Set<String> {
    val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("$RES_DIR/$valuesDir/strings.xml"))
    return (doc.documentElement.childNodes)
        .let { nodes -> (0 until nodes.length).map { nodes.item(it) } }
        .filter { it.attributes != null }
        .map { it.attributes.getNamedItem("name").nodeValue }
        .toSet()
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [SDK])
class AppLanguageTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    @Config(qualifiers = "b+en+Qaau")
    fun boykisserLocale_loadsBoykisserStrings() {
        assertEquals("Got it, nya~", context.getString(R.string.intro_confirm))
        assertEquals("1 image faiwed :c", context.resources.getQuantityString(R.plurals.sync_failed_images, 1, 1))
    }

    @Test
    @Config(qualifiers = "en")
    fun englishLocale_keepsEnglishStrings() {
        assertEquals("Got it", context.getString(R.string.intro_confirm))
    }

    @Test
    @Config(qualifiers = "de")
    fun germanLocale_keepsGermanStrings() {
        assertEquals("Verstanden", context.getString(R.string.intro_confirm))
    }

    @Test
    fun boykisserTranslation_coversEveryEnglishString() {
        assertEquals(stringNames("values-en"), stringNames("values-b+en+Qaau"))
    }

    @Test
    fun selectedLanguage_roundTripsThroughLocaleManager() {
        AppLanguage.entries.forEach {
            context.applyAppLanguage(it)
            assertEquals(it, context.currentAppLanguage())
        }
    }
}
