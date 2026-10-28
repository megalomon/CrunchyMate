package de.crunchymate.bridge

import android.content.Context
import android.webkit.JavascriptInterface
import de.crunchymate.db.AnimeRepository
import de.crunchymate.sync.SyncForegroundService
import de.crunchymate.sync.SyncManager
import org.json.JSONArray
import org.json.JSONObject

/**
 * JavaScript interface bridge exposed to WebView as `window.AndroidBridge`.
 * Provides database queries, watch status toggling, and background sync control.
 */
class AndroidBridge(
    private val context: Context? = null,
    private val repository: AnimeRepository,
    private val syncManager: SyncManager
) {

    constructor(context: Context) : this(
        context = context,
        repository = AnimeRepository(context),
        syncManager = SyncForegroundService.getSyncManager(context)
    )

    constructor(repository: AnimeRepository, syncManager: SyncManager) : this(
        context = null,
        repository = repository,
        syncManager = syncManager
    )

    @JavascriptInterface
    fun queryAnime(filterJson: String?): String {
        return try {
            val json = if (!filterJson.isNullOrBlank()) JSONObject(filterJson) else JSONObject()

            val query = json.optString("q").ifEmpty { json.optString("query") }.trim().ifEmpty { null }
            val genre = json.optString("genre").trim().ifEmpty { null }
            val watched = json.optString("watched").trim().ifEmpty { null }
            val meh = json.optString("meh").trim().ifEmpty { null }
            val sort = json.optString("sort").trim().ifEmpty { null }

            var lang = json.optString("lang").ifEmpty { json.optString("language") }.trim().ifEmpty { null }
            if (lang == null) {
                val gersub = json.optBoolean("gersub", false) || json.optString("gersub") == "true" || json.optString("gersub") == "1"
                val gerdub = json.optBoolean("gerdub", false) || json.optString("gerdub") == "true" || json.optString("gerdub") == "1"
                if (gersub && gerdub) {
                    lang = "both"
                } else if (gerdub) {
                    lang = "gerdub"
                } else if (gersub) {
                    lang = "gersub"
                }
            }

            val limit = when {
                json.has("limit") -> {
                    val l = json.optInt("limit", -1)
                    if (l > 0) l else json.optString("limit").toIntOrNull() ?: 50
                }
                else -> 50
            }

            val offset = when {
                json.has("offset") -> {
                    val o = json.optInt("offset", -1)
                    if (o >= 0) o else json.optString("offset").toIntOrNull() ?: 0
                }
                else -> 0
            }

            fun parseTags(key: String, altKey: String? = null): List<String> {
                val list = mutableListOf<String>()
                val str = json.optString(key, "")
                if (str.isNotEmpty()) {
                    for (part in str.split(",")) {
                        val trimmed = part.trim()
                        if (trimmed.isNotEmpty() && !list.contains(trimmed)) {
                            list.add(trimmed)
                        }
                    }
                }
                val arr = json.optJSONArray(key) ?: (if (altKey != null) json.optJSONArray(altKey) else null)
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val item = arr.optString(i, "").trim()
                        if (item.isNotEmpty() && !list.contains(item)) {
                            list.add(item)
                        }
                    }
                }
                return list
            }

            val preferTags = parseTags("prefer_tags", "blueTags")
            val mustTags = parseTags("must_tags", "greenTags")
            val excludeTags = parseTags("exclude_tags", "orangeTags")

            repository.queryAnime(
                query = query,
                genre = genre,
                language = lang,
                watched = watched,
                meh = meh,
                sort = sort,
                limit = limit,
                offset = offset,
                blueTags = preferTags,
                greenTags = mustTags,
                orangeTags = excludeTags
            ).toString()
        } catch (e: Exception) {
            JSONObject().apply {
                put("items", JSONArray())
                put("total", 0)
                put("limit", 50)
                put("offset", 0)
                put("error", e.message ?: "Failed to query anime")
            }.toString()
        }
    }

    @JavascriptInterface
    fun getAnime(id: String?): String {
        return try {
            if (id.isNullOrBlank()) {
                return JSONObject().apply { put("error", "missing anime id") }.toString()
            }
            val anime = repository.getAnimeById(id)
            anime?.toString() ?: JSONObject().apply { put("error", "anime not found") }.toString()
        } catch (e: Exception) {
            JSONObject().apply { put("error", e.message ?: "Failed to get anime") }.toString()
        }
    }

    @JavascriptInterface
    fun setWatched(id: String?, watched: Boolean): String {
        return try {
            if (id.isNullOrBlank()) {
                return JSONObject().apply { put("success", false); put("error", "missing anime id") }.toString()
            }
            val ok = repository.setWatched(id, watched)
            val resp = JSONObject()
            resp.put("success", ok)
            if (ok) {
                val updated = repository.getAnimeById(id)
                if (updated != null) {
                    resp.put("anime", updated)
                }
            }
            resp.toString()
        } catch (e: Exception) {
            JSONObject().apply { put("success", false); put("error", e.message ?: "Failed to set watched") }.toString()
        }
    }

    @JavascriptInterface
    fun setMeh(id: String?, isMeh: Boolean): String {
        return try {
            if (id.isNullOrBlank()) {
                return JSONObject().apply { put("success", false); put("error", "missing anime id") }.toString()
            }
            val ok = repository.setMeh(id, isMeh)
            val resp = JSONObject()
            resp.put("success", ok)
            if (ok) {
                val updated = repository.getAnimeById(id)
                if (updated != null) {
                    resp.put("anime", updated)
                }
            }
            resp.toString()
        } catch (e: Exception) {
            JSONObject().apply { put("success", false); put("error", e.message ?: "Failed to set meh") }.toString()
        }
    }

    @JavascriptInterface
    fun getGenres(): String {
        return try {
            repository.getGenres().toString()
        } catch (_: Exception) {
            JSONArray().toString()
        }
    }

    @JavascriptInterface
    fun getTags(): String {
        return try {
            repository.getTags().toString()
        } catch (_: Exception) {
            JSONArray().toString()
        }
    }

    @JavascriptInterface
    fun getStats(): String {
        return try {
            repository.getStats().toString()
        } catch (e: Exception) {
            JSONObject().apply {
                put("total_anime", 0)
                put("watched_anime", 0)
                put("meh_anime", 0)
                put("genres_count", 0)
                put("tags_count", 0)
                put("anilist_enriched", 0)
            }.toString()
        }
    }

    @JavascriptInterface
    fun startCrSync(): String {
        val resp = JSONObject()
        return try {
            if (syncManager.isCrSyncRunning()) {
                resp.put("success", false)
                resp.put("status", "already_running")
                resp.put("message", "Crunchyroll sync is already in progress")
                return resp.toString()
            }
            if (context != null) {
                try {
                    SyncForegroundService.startCrunchyrollSync(context)
                    resp.put("success", true)
                    resp.put("status", "started")
                    resp.put("message", "Crunchyroll sync started")
                    return resp.toString()
                } catch (_: Exception) {
                    // Fallback to syncManager directly in test / headless environment
                }
            }
            syncManager.startCrunchyrollSync()
            resp.put("success", true)
            resp.put("status", "started")
            resp.put("message", "Crunchyroll sync started")
            resp.toString()
        } catch (e: Exception) {
            resp.put("success", false)
            resp.put("status", "error")
            resp.put("error", e.message ?: "Failed to start Crunchyroll sync")
            resp.toString()
        }
    }

    @JavascriptInterface
    fun getCrSyncStatus(): String {
        return try {
            syncManager.getCrSyncStatus().toString()
        } catch (e: Exception) {
            JSONObject().apply {
                put("is_running", false)
                put("total_items", 0)
                put("synced_count", 0)
                put("last_sync_time", JSONObject.NULL)
                put("last_error", e.message ?: "Failed to get sync status")
            }.toString()
        }
    }

    @JavascriptInterface
    fun startAniSync(): String = startAniListSync()

    @JavascriptInterface
    fun startAniListSync(): String {
        val resp = JSONObject()
        return try {
            if (syncManager.isAniListSyncRunning()) {
                resp.put("success", false)
                resp.put("status", "already_running")
                resp.put("message", "AniList sync is already in progress")
                return resp.toString()
            }
            if (context != null) {
                try {
                    SyncForegroundService.startAniListSync(context)
                    resp.put("success", true)
                    resp.put("status", "started")
                    resp.put("message", "AniList sync started")
                    return resp.toString()
                } catch (_: Exception) {
                    // Fallback to syncManager directly in test / headless environment
                }
            }
            syncManager.startAniListSync()
            resp.put("success", true)
            resp.put("status", "started")
            resp.put("message", "AniList sync started")
            resp.toString()
        } catch (e: Exception) {
            resp.put("success", false)
            resp.put("status", "error")
            resp.put("error", e.message ?: "Failed to start AniList sync")
            resp.toString()
        }
    }

    @JavascriptInterface
    fun getAniSyncStatus(): String = getAniListSyncStatus()

    @JavascriptInterface
    fun getAniListSyncStatus(): String {
        return try {
            syncManager.getAniListSyncStatus().toString()
        } catch (e: Exception) {
            JSONObject().apply {
                put("is_running", false)
                put("total_to_enrich", 0)
                put("enriched_count", 0)
                put("total_in_db", 0)
                put("already_done", 0)
                put("last_sync_time", JSONObject.NULL)
                put("last_error", e.message ?: "Failed to get AniList status")
                put("status_message", "")
            }.toString()
        }
    }
}
