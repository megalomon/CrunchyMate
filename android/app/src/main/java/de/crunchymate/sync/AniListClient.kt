package de.crunchymate.sync

import de.crunchymate.db.AniListMediaData
import de.crunchymate.db.AniListTag
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale

fun JSONObject.optNullableString(key: String): String = if (isNull(key)) "" else optString(key, "")

class AniListClient(
    private val transport: HttpTransport = DefaultHttpTransport(),
    val endpoint: String = DEFAULT_ENDPOINT
) {
    companion object {
        const val DEFAULT_ENDPOINT = "https://graphql.anilist.co"
        const val MAX_RETRIES = 3
        const val DEFAULT_WAIT_SECONDS = 60

        private val cleanTitleRegex =
            Regex("""(?i)\s*(\(German\s*Dub\)|\[German\s*Dub\]|\(OmU\)|\(Dub\)|\(OV\)|\(Synchro\)|\(Staffel\s*\d+\))\s*""")

        fun cleanTitle(title: String): String {
            return title.replace(cleanTitleRegex, " ").trim()
        }

        fun parseWaitDuration(resp: HttpResponse): Long {
            val retryAfter = resp.getHeader("Retry-After")
            if (!retryAfter.isNullOrBlank()) {
                val trimmed = retryAfter.trim()
                val seconds = trimmed.toLongOrNull()
                if (seconds != null) {
                    return if (seconds <= 0) 10L else (seconds * 1000L + 1000L)
                }
                try {
                    val format = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US)
                    val date = format.parse(trimmed)
                    if (date != null) {
                        val wait = date.time - System.currentTimeMillis() + 1000L
                        if (wait > 0) return wait
                    }
                } catch (_: Exception) {}
            }

            val reset = resp.getHeader("X-RateLimit-Reset")
            if (!reset.isNullOrBlank()) {
                val unixSec = reset.trim().toLongOrNull()
                if (unixSec != null && unixSec > 0) {
                    val wait = (unixSec * 1000L) - System.currentTimeMillis() + 1000L
                    if (wait > 0) return wait
                }
            }

            return DEFAULT_WAIT_SECONDS * 1000L
        }

        fun parseMediaData(mediaObj: JSONObject): AniListMediaData {
            val id = mediaObj.getInt("id")
            val titleObj = mediaObj.optJSONObject("title")
            val titleRomaji = titleObj?.optNullableString("romaji") ?: ""
            val titleEnglish = titleObj?.optNullableString("english") ?: ""
            val description = mediaObj.optNullableString("description")
            val score = mediaObj.optInt("averageScore", 0)
            val status = mediaObj.optNullableString("status")

            val coverObj = mediaObj.optJSONObject("coverImage")
            val coverLarge = coverObj?.optNullableString("large") ?: ""
            val coverImage = if (coverLarge.isNotEmpty()) coverLarge else (coverObj?.optNullableString("medium") ?: "")
            val bannerImage = mediaObj.optNullableString("bannerImage")
            val siteUrl = mediaObj.optNullableString("siteUrl")

            val genres = mutableListOf<String>()
            val genresArray = mediaObj.optJSONArray("genres")
            if (genresArray != null) {
                for (i in 0 until genresArray.length()) {
                    if (genresArray.isNull(i)) continue
                    val g = genresArray.optString(i, "").trim()
                    if (g.isNotEmpty() && g != "null") {
                        genres.add(g)
                    }
                }
            }

            val tags = mutableListOf<AniListTag>()
            val tagsArray = mediaObj.optJSONArray("tags")
            if (tagsArray != null) {
                for (i in 0 until tagsArray.length()) {
                    val tagObj = tagsArray.optJSONObject(i) ?: continue
                    val isSpoiler = tagObj.optBoolean("isMediaSpoiler", false)
                    if (isSpoiler) continue

                    val rank = tagObj.optInt("rank", 0)
                    if (rank < 40) continue

                    val name = tagObj.optNullableString("name").trim()
                    if (name.isNotEmpty()) {
                        tags.add(AniListTag(name = name, rank = rank))
                    }
                }
            }

            return AniListMediaData(
                aniListId = id,
                titleRomaji = titleRomaji,
                titleEnglish = titleEnglish,
                description = description,
                score = score,
                status = status,
                coverImage = coverImage,
                bannerImage = bannerImage,
                siteUrl = siteUrl,
                tags = tags,
                genres = genres
            )
        }
    }

    var onRateLimitWait: ((waitMillis: Long, attempt: Int) -> Unit)? = null
    var onRateLimitDone: (() -> Unit)? = null

    @Volatile
    private var blockedUntil: Long = 0L
    private val clientLock = Any()

    fun searchAnime(title: String): AniListMediaData? {
        val results = searchAnimeBatch(listOf(title))
        return results.firstOrNull()
    }

    fun searchAnimeBatch(titles: List<String>): List<AniListMediaData?> {
        if (titles.isEmpty()) return emptyList()

        val sbQuery = StringBuilder()
        sbQuery.append("""
            fragment MediaFields on Media {
              id
              title { romaji english native }
              description
              genres
              tags { name rank isMediaSpoiler }
              averageScore
              status
              coverImage { large medium }
              bannerImage
              siteUrl
            }
            query(
        """.trimIndent())

        val variables = JSONObject()
        for (i in titles.indices) {
            if (i > 0) sbQuery.append(", ")
            val varName = "s$i"
            sbQuery.append("\$$varName: String")
            val raw = titles[i]
            val cleaned = cleanTitle(raw)
            variables.put(varName, if (cleaned.isNotEmpty()) cleaned else raw)
        }
        sbQuery.append(") {\n")

        for (i in titles.indices) {
            val alias = "a$i"
            val varName = "s$i"
            sbQuery.append("  $alias: Page(page: 1, perPage: 1) { media(search: \$$varName, type: ANIME) { ...MediaFields } }\n")
        }
        sbQuery.append("}")

        val payload = JSONObject()
        payload.put("query", sbQuery.toString())
        payload.put("variables", variables)

        val dataObj = executeGraphQL(payload) ?: return List(titles.size) { null }

        val results = mutableListOf<AniListMediaData?>()
        for (i in titles.indices) {
            val alias = "a$i"
            val pageObj = dataObj.optJSONObject(alias)
            val mediaArray = pageObj?.optJSONArray("media")
            if (mediaArray != null && mediaArray.length() > 0) {
                val mediaJson = mediaArray.optJSONObject(0)
                if (mediaJson != null) {
                    results.add(parseMediaData(mediaJson))
                } else {
                    results.add(null)
                }
            } else {
                results.add(null)
            }
        }

        return results
    }

    private fun executeGraphQL(payload: JSONObject): JSONObject? {
        val bodyString = payload.toString()

        for (attempt in 0..MAX_RETRIES) {
            var waitProlonged = 0L
            synchronized(clientLock) {
                val now = System.currentTimeMillis()
                if (blockedUntil > now) {
                    waitProlonged = blockedUntil - now
                }
            }

            if (waitProlonged > 0) {
                onRateLimitWait?.invoke(waitProlonged, attempt)
                try {
                    Thread.sleep(waitProlonged)
                } catch (_: InterruptedException) {}
                onRateLimitDone?.invoke()
                synchronized(clientLock) {
                    blockedUntil = 0L
                }
            }

            val headers = mapOf(
                "Content-Type" to "application/json",
                "Accept" to "application/json",
                "User-Agent" to "CrunchyMate/1.0"
            )

            val resp = transport.request(endpoint, "POST", headers, bodyString)

            // Proactively save reset time if remaining queries reached 0
            if (resp.getHeader("X-RateLimit-Remaining") == "0") {
                val reset = resp.getHeader("X-RateLimit-Reset")
                if (!reset.isNullOrBlank()) {
                    val unixSec = reset.trim().toLongOrNull()
                    if (unixSec != null && unixSec > 0) {
                        synchronized(clientLock) {
                            blockedUntil = (unixSec * 1000L) + 1000L
                        }
                    }
                }
            }

            if (resp.statusCode == 429) {
                val waitDuration = parseWaitDuration(resp)
                if (attempt < MAX_RETRIES) {
                    onRateLimitWait?.invoke(waitDuration, attempt + 1)
                    try {
                        Thread.sleep(waitDuration)
                    } catch (_: InterruptedException) {}
                    onRateLimitDone?.invoke()
                    continue
                }
                throw IllegalStateException("AniList rate limit exceeded (429) after $MAX_RETRIES retries")
            }

            if (resp.statusCode == 404) {
                return null
            }

            if (resp.statusCode != 200) {
                throw IllegalStateException("AniList returned status: ${resp.statusCode}")
            }

            val rootJson = JSONObject(resp.body)
            val dataObj = rootJson.optJSONObject("data")
            val errorsArray = rootJson.optJSONArray("errors")

            if (dataObj == null && errorsArray != null && errorsArray.length() > 0) {
                val msg = errorsArray.getJSONObject(0).optNullableString("message").ifEmpty { "Unknown error" }
                throw IllegalStateException("AniList GraphQL error: $msg")
            }

            return dataObj
        }

        throw IllegalStateException("AniList request failed after retries")
    }
}
