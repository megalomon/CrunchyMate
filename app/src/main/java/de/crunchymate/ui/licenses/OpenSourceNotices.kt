package de.crunchymate.ui.licenses

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val NOTICES_ASSET_NAME = "open_source_notices.json"

@Serializable
enum class NoticeGroup {
    @SerialName("rust")
    RUST,

    @SerialName("android")
    ANDROID,

    @SerialName("icons")
    ICONS,

    @SerialName("flags")
    FLAGS,
}

@Serializable
data class NoticeComponent(
    val group: NoticeGroup,
    val name: String,
    val version: String,
    val license: String,
    val holder: String,
    val licenseIds: List<String>,
)

@Serializable
data class LicenseText(
    val id: String,
    val title: String,
    val text: String,
)

/** Inhalt von `open_source_notices.json`, das `scripts/gen-oss-notices.py` aus `docs/open-source-hinweise.md` erzeugt. */
@Serializable
data class OpenSourceNotices(
    val components: List<NoticeComponent>,
    val licenses: List<LicenseText>,
) {
    companion object {
        // Neue Felder im Asset dürfen ältere App-Stände nicht am Lesen hindern
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): OpenSourceNotices = json.decodeFromString(text)
    }
}
