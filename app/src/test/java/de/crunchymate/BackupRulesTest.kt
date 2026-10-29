package de.crunchymate

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

private const val SDK = 35

/** Sichert ab, dass das Backup genau die Nutzer-Datenbank erfasst, auch nach einer Umbenennung. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [SDK])
class BackupRulesTest {
    private data class Rule(
        val section: String,
        val tag: String,
        val domain: String?,
        val path: String?,
    )

    private fun rules(): List<Rule> {
        val parser = ApplicationProvider.getApplicationContext<android.content.Context>().resources.getXml(R.xml.data_extraction_rules)
        val rules = mutableListOf<Rule>()
        var section = ""
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "cloud-backup", "device-transfer" -> {
                    section = parser.name
                }

                "include", "exclude" -> {
                    rules += Rule(section, parser.name, parser.getAttributeValue(null, "domain"), parser.getAttributeValue(null, "path"))
                }
            }
        }
        return rules
    }

    @Test
    fun backupIncludesOnlyUserDatabaseAndItsJournals() {
        val name = AppContainer.DEFAULT_USER_DB_NAME
        val expected = listOf(name, "$name-journal", "$name-wal", "$name-shm")
        for (section in listOf("cloud-backup", "device-transfer")) {
            val sectionRules = rules().filter { it.section == section }
            assertEquals(expected.map { Rule(section, "include", "database", it) }, sectionRules)
        }
    }
}
