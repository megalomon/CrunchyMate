package de.crunchymate.sync

import de.crunchymate.db.CRSeriesItem
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

data class CrunchyrollBrowsePage(
    val total: Int,
    val items: List<CRSeriesItem>
)

class CrunchyrollClient(
    private val transport: HttpTransport = DefaultHttpTransport(),
    val authUrl: String = DEFAULT_AUTH_URL,
    val browseUrl: String = DEFAULT_BROWSE_URL
) {
    companion object {
        const val DEFAULT_AUTH_URL = "https://beta-api.crunchyroll.com/auth/v1/token"
        const val DEFAULT_BROWSE_URL = "https://beta-api.crunchyroll.com/content/v2/discover/browse"
        const val BASIC_AUTH_HEADER = "Basic Y3Jfd2ViOg=="
        const val USER_AGENT_HEADER =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        fun parseSeriesItem(item: JSONObject): CRSeriesItem {
            val id = item.optString("id", "")
            val title = item.optString("title", "")
            val slug = item.optString("slug", "").ifEmpty { item.optString("slug_title", "") }
            val description = item.optString("description", "")

            val images = item.optJSONObject("images")
            val posterTall = extractPosterTall(images)
            val posterWide = extractPosterWide(images)

            val meta = item.optJSONObject("series_metadata") ?: JSONObject()
            val episodeCount = meta.optInt("episode_count", 0)
            val seasonCount = meta.optInt("season_count", 0)
            val launchYear = meta.optInt("series_launch_year", 0)
            val isSubbed = meta.optBoolean("is_subbed", false)
            val isDubbed = meta.optBoolean("is_dubbed", false)

            var hasGerDub = false
            val audioLocales = meta.optJSONArray("audio_locales")
            if (audioLocales != null) {
                for (i in 0 until audioLocales.length()) {
                    val loc = audioLocales.optString(i, "").lowercase(Locale.ROOT)
                    if (loc.startsWith("de")) {
                        hasGerDub = true
                        break
                    }
                }
            }
            if (!hasGerDub) {
                val lowerTitle = title.lowercase(Locale.ROOT)
                if (lowerTitle.contains("german dub") || lowerTitle.contains("synchro")) {
                    hasGerDub = true
                }
            }

            var hasGerSub = false
            val subLocales = meta.optJSONArray("subtitle_locales")
            if (subLocales != null) {
                for (i in 0 until subLocales.length()) {
                    val loc = subLocales.optString(i, "").lowercase(Locale.ROOT)
                    if (loc.startsWith("de")) {
                        hasGerSub = true
                        break
                    }
                }
            }
            if (!hasGerSub) {
                val lowerTitle = title.lowercase(Locale.ROOT)
                if (lowerTitle.contains("omu") || lowerTitle.contains("(ov)")) {
                    hasGerSub = true
                }
            }

            val genres = mutableListOf<String>()
            val tenantCategories = meta.optJSONArray("tenant_categories")
            if (tenantCategories != null) {
                for (i in 0 until tenantCategories.length()) {
                    val cat = tenantCategories.optString(i, "").trim()
                    if (cat.isNotEmpty()) {
                        genres.add(cat)
                    }
                }
            }

            return CRSeriesItem(
                id = id,
                title = title,
                slug = slug,
                description = description,
                posterTall = posterTall,
                posterWide = posterWide,
                episodeCount = episodeCount,
                seasonCount = seasonCount,
                launchYear = launchYear,
                isSubbed = isSubbed,
                isDubbed = isDubbed,
                hasGerSub = hasGerSub,
                hasGerDub = hasGerDub,
                genres = genres
            )
        }

        private fun extractPosterTall(images: JSONObject?): String {
            if (images == null) return ""
            val array = images.optJSONArray("poster_tall") ?: return ""
            if (array.length() == 0) return ""
            val firstList = array.optJSONArray(0) ?: return ""
            if (firstList.length() == 0) return ""

            var chosen = ""
            for (i in 0 until firstList.length()) {
                val img = firstList.optJSONObject(i) ?: continue
                val src = img.optString("source", "")
                val width = img.optInt("width", 0)
                if (chosen.isEmpty() || i == firstList.length() - 1) {
                    chosen = src
                }
                if (width in 480..800) {
                    return src
                }
            }
            return chosen
        }

        private fun extractPosterWide(images: JSONObject?): String {
            if (images == null) return ""
            val array = images.optJSONArray("poster_wide") ?: return ""
            if (array.length() == 0) return ""
            val firstList = array.optJSONArray(0) ?: return ""
            if (firstList.length() == 0) return ""

            var chosen = ""
            for (i in 0 until firstList.length()) {
                val img = firstList.optJSONObject(i) ?: continue
                val src = img.optString("source", "")
                val width = img.optInt("width", 0)
                if (chosen.isEmpty() || i == firstList.length() - 1) {
                    chosen = src
                }
                if (width in 640..1280) {
                    return src
                }
            }
            return chosen
        }
    }

    @Volatile
    private var token: String? = null

    @Volatile
    private var tokenExpiresAt: Long = 0L

    private val authLock = Any()

    fun authenticate(): String {
        synchronized(authLock) {
            val body = "grant_type=client_id"
            val headers = mapOf(
                "Authorization" to BASIC_AUTH_HEADER,
                "Content-Type" to "application/x-www-form-urlencoded",
                "User-Agent" to USER_AGENT_HEADER
            )

            val resp = transport.request(authUrl, "POST", headers, body)
            if (resp.statusCode != 200) {
                throw IllegalStateException("Token request returned status ${resp.statusCode}: ${resp.body}")
            }

            val json = JSONObject(resp.body)
            val accessToken = json.getString("access_token")
            val expiresIn = json.optInt("expires_in", 3600)
            token = accessToken
            tokenExpiresAt = System.currentTimeMillis() + (expiresIn - 60).coerceAtLeast(10) * 1000L
            return accessToken
        }
    }

    fun ensureToken(): String {
        val currentToken = token
        if (currentToken != null && System.currentTimeMillis() < tokenExpiresAt) {
            return currentToken
        }
        return authenticate()
    }

    fun fetchCatalogPage(start: Int, count: Int): CrunchyrollBrowsePage {
        val currentToken = ensureToken()
        val pageUrl = buildBrowseUrl(start, count)
        val headers = mutableMapOf(
            "Authorization" to "Bearer $currentToken",
            "User-Agent" to USER_AGENT_HEADER,
            "Accept-Language" to "de-DE"
        )

        var resp = transport.request(pageUrl, "GET", headers, null)

        // On 401 Unauthorized, re-authenticate once and retry
        if (resp.statusCode == 401) {
            val refreshedToken = authenticate()
            headers["Authorization"] = "Bearer $refreshedToken"
            resp = transport.request(pageUrl, "GET", headers, null)
        }

        if (resp.statusCode != 200) {
            throw IllegalStateException("Browse request failed with status ${resp.statusCode}: ${resp.body}")
        }

        val json = JSONObject(resp.body)
        val total = json.optInt("total", 0)
        val dataArray = json.optJSONArray("data") ?: JSONArray()
        val items = mutableListOf<CRSeriesItem>()

        for (i in 0 until dataArray.length()) {
            val itemObj = dataArray.optJSONObject(i) ?: continue
            items.add(parseSeriesItem(itemObj))
        }

        return CrunchyrollBrowsePage(total, items)
    }

    private fun buildBrowseUrl(start: Int, count: Int): String {
        val separator = if (browseUrl.contains("?")) "&" else "?"
        return "$browseUrl${separator}locale=de-DE&start=$start&n=$count&sort_by=popularity"
    }
}
