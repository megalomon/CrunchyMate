package de.crunchymate.sync

import de.crunchymate.db.AnimeRepository
import de.crunchymate.db.SyncState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class SyncManager(
    private val repository: AnimeRepository,
    private val crClient: CrunchyrollClient,
    private val aniClient: AniListClient,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    private val aniListBatchSize: Int = 10,
    private val aniListThrottleDelayMs: Long = 2100L,
    private val crunchyrollThrottleDelayMs: Long = 100L
) {
    private val crLock = Any()
    @Volatile private var crIsRunning = false
    @Volatile private var crTotalItems = 0
    @Volatile private var crSyncedCount = 0
    @Volatile private var crLastSyncTime: String? = null
    @Volatile private var crLastError = ""
    private var crJob: Job? = null

    private val aniLock = Any()
    @Volatile private var aniIsRunning = false
    @Volatile private var aniTotalInDb = 0
    @Volatile private var aniAlreadyDone = 0
    @Volatile private var aniTotalToEnrich = 0
    @Volatile private var aniEnrichedCount = 0
    @Volatile private var aniLastSyncTime: String? = null
    @Volatile private var aniLastError = ""
    @Volatile private var aniStatusMessage = ""
    private var aniJob: Job? = null

    init {
        aniClient.onRateLimitWait = { waitMs, _ ->
            val sec = (waitMs / 1000L).coerceAtLeast(1)
            aniStatusMessage = "AniList Rate-Limit erreicht (30 Req/min). Warte ${sec}s..."
        }
        aniClient.onRateLimitDone = {
            aniStatusMessage = ""
        }
    }

    fun isCrSyncRunning(): Boolean = crIsRunning
    fun isAniListSyncRunning(): Boolean = aniIsRunning

    fun cancelCrunchyrollSync() {
        val jobToCancel: Job?
        val wasRunning: Boolean
        synchronized(crLock) {
            wasRunning = crIsRunning
            jobToCancel = crJob
            crIsRunning = false
        }
        jobToCancel?.cancel()
        if (wasRunning) {
            repository.updateSyncState(
                SyncState(
                    key = "crunchyroll",
                    lastSyncedAt = crLastSyncTime,
                    totalItems = crTotalItems,
                    syncedItems = crSyncedCount,
                    status = "idle",
                    lastError = crLastError
                )
            )
        }
    }

    fun cancelAniListSync() {
        val jobToCancel: Job?
        val wasRunning: Boolean
        synchronized(aniLock) {
            wasRunning = aniIsRunning
            jobToCancel = aniJob
            aniIsRunning = false
            aniStatusMessage = ""
        }
        jobToCancel?.cancel()
        if (wasRunning) {
            repository.updateSyncState(
                SyncState(
                    key = "anilist",
                    lastSyncedAt = aniLastSyncTime,
                    totalItems = aniTotalInDb,
                    syncedItems = aniAlreadyDone,
                    status = "idle",
                    lastError = aniLastError
                )
            )
        }
    }

    fun startCrunchyrollSync(
        limit: Int = 0,
        onProgress: ((synced: Int, total: Int) -> Unit)? = null
    ): Job {
        synchronized(crLock) {
            if (crIsRunning) {
                throw IllegalStateException("Crunchyroll sync is already in progress")
            }
            crIsRunning = true
            crLastError = ""
            crSyncedCount = 0
        }

        val job = scope.launch {
            try {
                val pageSize = 50
                var start = 0
                var totalSynced = 0

                while (isActive) {
                    var countToFetch = pageSize
                    if (limit > 0 && totalSynced + countToFetch > limit) {
                        countToFetch = limit - totalSynced
                    }
                    if (countToFetch <= 0) break

                    val page = crClient.fetchCatalogPage(start, countToFetch)
                    synchronized(crLock) {
                        crTotalItems = page.total
                    }

                    if (page.items.isEmpty()) break

                    repository.upsertCrunchyrollSeries(page.items)
                    totalSynced += page.items.size

                    synchronized(crLock) {
                        crSyncedCount = totalSynced
                    }
                    onProgress?.invoke(totalSynced, page.total)

                    if (limit > 0 && totalSynced >= limit) break
                    start += page.items.size
                    if (start >= page.total) break

                    if (crunchyrollThrottleDelayMs > 0) {
                        delay(crunchyrollThrottleDelayMs)
                    }
                }

                if (!isActive) {
                    synchronized(crLock) {
                        crIsRunning = false
                    }
                    repository.updateSyncState(
                        SyncState(
                            key = "crunchyroll",
                            lastSyncedAt = crLastSyncTime,
                            totalItems = crTotalItems,
                            syncedItems = totalSynced,
                            status = "idle",
                            lastError = crLastError
                        )
                    )
                    return@launch
                }

                val nowStr = formatIsoTimestamp(Date())
                synchronized(crLock) {
                    crLastSyncTime = nowStr
                    crIsRunning = false
                }

                repository.updateSyncState(
                    SyncState(
                        key = "crunchyroll",
                        lastSyncedAt = nowStr,
                        totalItems = crTotalItems,
                        syncedItems = totalSynced,
                        status = "idle",
                        lastError = ""
                    )
                )
            } catch (e: CancellationException) {
                synchronized(crLock) {
                    crIsRunning = false
                }
                repository.updateSyncState(
                    SyncState(
                        key = "crunchyroll",
                        lastSyncedAt = crLastSyncTime,
                        totalItems = crTotalItems,
                        syncedItems = crSyncedCount,
                        status = "idle",
                        lastError = crLastError
                    )
                )
                throw e
            } catch (e: Exception) {
                val errMsg = e.message ?: "Unknown error"
                synchronized(crLock) {
                    crLastError = errMsg
                    crIsRunning = false
                }
                repository.updateSyncState(
                    SyncState(
                        key = "crunchyroll",
                        lastSyncedAt = crLastSyncTime,
                        totalItems = crTotalItems,
                        syncedItems = crSyncedCount,
                        status = "error",
                        lastError = errMsg
                    )
                )
            }
        }

        synchronized(crLock) {
            crJob = job
        }
        return job
    }

    fun startAniListSync(
        onProgress: ((done: Int, total: Int) -> Unit)? = null
    ): Job {
        synchronized(aniLock) {
            if (aniIsRunning) {
                throw IllegalStateException("AniList sync is already in progress")
            }
            aniIsRunning = true
            aniLastError = ""
            aniStatusMessage = ""
            aniEnrichedCount = 0
        }

        val job = scope.launch {
            try {
                val allResult = repository.queryAnime(meh = "all", limit = 10000)
                val itemsArray = allResult.optJSONArray("items") ?: JSONArray()
                val totalInDb = allResult.optInt("total", itemsArray.length())

                val unenrichedList = mutableListOf<Pair<String, String>>()
                var alreadyDoneCount = 0

                for (i in 0 until itemsArray.length()) {
                    val animeObj = itemsArray.optJSONObject(i) ?: continue
                    val id = animeObj.getString("id")
                    val title = animeObj.getString("title")
                    if (animeObj.isNull("anilist_id")) {
                        unenrichedList.add(Pair(id, title))
                    } else {
                        alreadyDoneCount++
                    }
                }

                synchronized(aniLock) {
                    aniTotalInDb = totalInDb
                    aniAlreadyDone = alreadyDoneCount
                    aniTotalToEnrich = unenrichedList.size
                }

                var newlyEnriched = 0

                for (i in unenrichedList.indices step aniListBatchSize) {
                    if (!isActive) break

                    val end = (i + aniListBatchSize).coerceAtMost(unenrichedList.size)
                    val batch = unenrichedList.subList(i, end)
                    val titles = batch.map { it.second }

                    try {
                        val results = aniClient.searchAnimeBatch(titles)
                        for (idx in results.indices) {
                            val media = results[idx]
                            if (media != null) {
                                val animeId = batch[idx].first
                                repository.updateAniListMetadata(animeId, media)
                                newlyEnriched++
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // Tolerate single batch errors, continue with remaining batches
                    }

                    synchronized(aniLock) {
                        aniEnrichedCount = newlyEnriched
                        aniAlreadyDone = alreadyDoneCount + end
                    }

                    onProgress?.invoke(aniAlreadyDone, aniTotalInDb)

                    if (aniListThrottleDelayMs > 0) {
                        delay(aniListThrottleDelayMs)
                    }
                }

                if (!isActive) {
                    synchronized(aniLock) {
                        aniIsRunning = false
                        aniStatusMessage = ""
                    }
                    repository.updateSyncState(
                        SyncState(
                            key = "anilist",
                            lastSyncedAt = aniLastSyncTime,
                            totalItems = aniTotalInDb,
                            syncedItems = aniAlreadyDone,
                            status = "idle",
                            lastError = aniLastError
                        )
                    )
                    return@launch
                }

                val nowStr = formatIsoTimestamp(Date())
                synchronized(aniLock) {
                    aniLastSyncTime = nowStr
                    aniIsRunning = false
                    aniStatusMessage = ""
                }

                repository.updateSyncState(
                    SyncState(
                        key = "anilist",
                        lastSyncedAt = nowStr,
                        totalItems = aniTotalInDb,
                        syncedItems = aniAlreadyDone,
                        status = "idle",
                        lastError = ""
                    )
                )
            } catch (e: CancellationException) {
                synchronized(aniLock) {
                    aniIsRunning = false
                    aniStatusMessage = ""
                }
                repository.updateSyncState(
                    SyncState(
                        key = "anilist",
                        lastSyncedAt = aniLastSyncTime,
                        totalItems = aniTotalInDb,
                        syncedItems = aniAlreadyDone,
                        status = "idle",
                        lastError = aniLastError
                    )
                )
                throw e
            } catch (e: Exception) {
                val errMsg = e.message ?: "Unknown error"
                synchronized(aniLock) {
                    aniLastError = errMsg
                    aniIsRunning = false
                    aniStatusMessage = ""
                }
                repository.updateSyncState(
                    SyncState(
                        key = "anilist",
                        lastSyncedAt = aniLastSyncTime,
                        totalItems = aniTotalInDb,
                        syncedItems = aniAlreadyDone,
                        status = "error",
                        lastError = errMsg
                    )
                )
            }
        }

        synchronized(aniLock) {
            aniJob = job
        }
        return job
    }

    fun getCrSyncStatus(): JSONObject {
        val obj = JSONObject()
        synchronized(crLock) {
            if (crIsRunning) {
                obj.put("is_running", true)
                obj.put("total_items", crTotalItems)
                obj.put("synced_count", crSyncedCount)
                if (crLastSyncTime != null) {
                    obj.put("last_sync_time", crLastSyncTime)
                } else {
                    obj.put("last_sync_time", JSONObject.NULL)
                }
                obj.put("last_error", crLastError)
                return obj
            }
        }

        val state = repository.getSyncState("crunchyroll")
        val totalItems = state?.totalItems ?: crTotalItems
        val syncedCount = state?.syncedItems ?: crSyncedCount
        val lastSyncTime = state?.lastSyncedAt ?: crLastSyncTime
        val lastError = state?.lastError ?: crLastError

        obj.put("is_running", false)
        obj.put("total_items", totalItems)
        obj.put("synced_count", syncedCount)
        if (lastSyncTime != null) {
            obj.put("last_sync_time", lastSyncTime)
        } else {
            obj.put("last_sync_time", JSONObject.NULL)
        }
        obj.put("last_error", lastError)
        return obj
    }

    fun getAniListSyncStatus(): JSONObject {
        val obj = JSONObject()
        synchronized(aniLock) {
            if (aniIsRunning) {
                obj.put("is_running", true)
                obj.put("total_to_enrich", aniTotalToEnrich)
                obj.put("enriched_count", aniEnrichedCount)
                obj.put("total_in_db", aniTotalInDb)
                obj.put("already_done", aniAlreadyDone)
                if (aniLastSyncTime != null) {
                    obj.put("last_sync_time", aniLastSyncTime)
                } else {
                    obj.put("last_sync_time", JSONObject.NULL)
                }
                obj.put("last_error", aniLastError)
                obj.put("status_message", aniStatusMessage)
                return obj
            }
        }

        val stats = repository.getStats()
        val totalInDb = stats.optInt("total_anime", aniTotalInDb)
        val enriched = stats.optInt("anilist_enriched", 0)
        val totalToEnrich = (totalInDb - enriched).coerceAtLeast(0)

        val state = repository.getSyncState("anilist")
        val lastSyncTime = state?.lastSyncedAt ?: aniLastSyncTime
        val lastError = state?.lastError ?: aniLastError

        obj.put("is_running", false)
        obj.put("total_to_enrich", totalToEnrich)
        obj.put("enriched_count", aniEnrichedCount)
        obj.put("total_in_db", totalInDb)
        obj.put("already_done", enriched)
        if (lastSyncTime != null) {
            obj.put("last_sync_time", lastSyncTime)
        } else {
            obj.put("last_sync_time", JSONObject.NULL)
        }
        obj.put("last_error", lastError)
        obj.put("status_message", "")
        return obj
    }

    private fun formatIsoTimestamp(date: Date): String {
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(date)
    }
}
