package de.crunchymate.ui.licenses

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// Gradle führt Unit-Tests im Verzeichnis des Moduls aus
private val ASSET_FILE = File("src/main/assets/$NOTICES_ASSET_NAME")
private val DOC_FILE = File("../docs/open-source-hinweise.md")
private const val RUST_HEADING = "### Rust"
private const val ANDROID_HEADING = "### Android/Kotlin"
private const val UNKNOWN_HEADING = "### Lizenz nicht ermittelbar"

class OpenSourceNoticesTest {
    private val notices = OpenSourceNotices.parse(ASSET_FILE.readText())
    private val doc = DOC_FILE.readText()

    private fun tableRows(
        from: String,
        to: String,
    ): Int =
        doc
            .substringAfter(from)
            .substringBefore(to)
            .lines()
            .count { it.startsWith("| ") && !it.startsWith("| Crate") && !it.startsWith("| Bibliothek") }

    @Test
    fun asset_isValidAndCoversAllGroups() {
        assertTrue(notices.components.isNotEmpty())
        assertTrue(notices.licenses.isNotEmpty())
        NoticeGroup.entries.forEach { group ->
            assertTrue("Gruppe $group fehlt", notices.components.any { it.group == group })
        }
        assertTrue(notices.licenses.all { it.text.isNotBlank() })
    }

    @Test
    fun everyComponent_hasNameLicenseAndResolvableLicenseText() {
        val knownIds = notices.licenses.map { it.id }.toSet()
        notices.components.forEach { component ->
            assertFalse("Name fehlt", component.name.isBlank())
            assertFalse("Lizenz fehlt bei ${component.name}", component.license.isBlank())
            assertTrue("Kein Lizenztext bei ${component.name}", component.licenseIds.isNotEmpty())
            assertTrue(component.name, knownIds.containsAll(component.licenseIds))
        }
    }

    @Test
    fun asset_matchesDocumentTables() {
        assertEquals(tableRows(RUST_HEADING, ANDROID_HEADING), notices.components.count { it.group == NoticeGroup.RUST })
        assertEquals(tableRows(ANDROID_HEADING, UNKNOWN_HEADING), notices.components.count { it.group == NoticeGroup.ANDROID })
        notices.licenses.forEach { assertTrue("Lizenztext ${it.id} fehlt im Dokument", doc.contains("### ${it.id}\n")) }
    }
}
