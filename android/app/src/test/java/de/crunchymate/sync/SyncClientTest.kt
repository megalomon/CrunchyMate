package de.crunchymate.sync

import de.crunchymate.db.CRSeriesItem
import de.crunchymate.db.FakeDatabaseDelegate
import de.crunchymate.db.AnimeRepository
import de.crunchymate.db.FakeRow
import de.crunchymate.db.SyncState
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class FakeHttpTransport : HttpTransport {
    var responder: (url: String, method: String, headers: Map<String, String>, body: String?) -> HttpResponse = { _, _, _, _ ->
        HttpResponse(200, emptyMap(), "{}")
    }

    val recordedRequests = mutableListOf<RequestRecord>()

    data class RequestRecord(
        val url: String,
        val method: String,
        val headers: Map<String, String>,
        val body: String?
    )

    override fun request(
        url: String,
        method: String,
        headers: Map<String, String>,
        body: String?
    ): HttpResponse {
        recordedRequests.add(RequestRecord(url, method, headers, body))
        return responder(url, method, headers, body)
    }
}

class SyncClientTest {

    @Test
    fun testCleanTitleStripsDubAndSubSuffixes() {
        assertEquals("Frieren: Beyond Journey's End", AniListClient.cleanTitle("Frieren: Beyond Journey's End (German Dub)"))
        assertEquals("Attack on Titan", AniListClient.cleanTitle("Attack on Titan [German Dub]"))
        assertEquals("Jujutsu Kaisen", AniListClient.cleanTitle("Jujutsu Kaisen (OmU)"))
        assertEquals("Demon Slayer", AniListClient.cleanTitle("Demon Slayer (Dub)"))
        assertEquals("One Piece", AniListClient.cleanTitle("One Piece (OV)"))
        assertEquals("Bleach", AniListClient.cleanTitle("Bleach (Synchro)"))
        assertEquals("Dr. STONE", AniListClient.cleanTitle("Dr. STONE (Staffel 3)"))
        assertEquals("Dr. STONE", AniListClient.cleanTitle("Dr. STONE (Staffel 12)"))
        assertEquals("Solo Leveling", AniListClient.cleanTitle("Solo Leveling (German Dub) (OmU)"))
        assertEquals("Chainsaw Man", AniListClient.cleanTitle("Chainsaw Man"))
        assertEquals("", AniListClient.cleanTitle("   "))
    }

    @Test
    fun testCrunchyrollLocaleParsingAndPosterSelection() {
        val seriesJson = JSONObject("""
            {
                "id": "GR12345",
                "title": "Test Show (German Dub)",
                "slug": "test-show",
                "description": "A great show",
                "images": {
                    "poster_tall": [
                        [
                            {"width": 240, "height": 360, "source": "https://img.cr.com/tall-small.jpg"},
                            {"width": 600, "height": 900, "source": "https://img.cr.com/tall-medium.jpg"},
                            {"width": 1200, "height": 1800, "source": "https://img.cr.com/tall-huge.jpg"}
                        ]
                    ],
                    "poster_wide": [
                        [
                            {"width": 400, "height": 225, "source": "https://img.cr.com/wide-small.jpg"},
                            {"width": 800, "height": 450, "source": "https://img.cr.com/wide-medium.jpg"}
                        ]
                    ]
                },
                "series_metadata": {
                    "episode_count": 24,
                    "season_count": 2,
                    "series_launch_year": 2024,
                    "is_subbed": true,
                    "is_dubbed": true,
                    "audio_locales": ["de-DE", "ja-JP"],
                    "subtitle_locales": ["de-DE", "en-US"],
                    "tenant_categories": ["Action", "Fantasy"]
                }
            }
        """.trimIndent())

        val item = CrunchyrollClient.parseSeriesItem(seriesJson)

        assertEquals("GR12345", item.id)
        assertEquals("Test Show (German Dub)", item.title)
        assertEquals("test-show", item.slug)
        assertEquals("A great show", item.description)
        assertEquals("https://img.cr.com/tall-medium.jpg", item.posterTall)
        assertEquals("https://img.cr.com/wide-medium.jpg", item.posterWide)
        assertEquals(24, item.episodeCount)
        assertEquals(2, item.seasonCount)
        assertEquals(2024, item.launchYear)
        assertTrue(item.isSubbed)
        assertTrue(item.isDubbed)
        assertTrue(item.hasGerDub)
        assertTrue(item.hasGerSub)
        assertEquals(listOf("Action", "Fantasy"), item.genres)
    }

    @Test
    fun testCrunchyrollLocaleFallbackFromTitle() {
        // audio_locales is empty, but title has "(Synchro)" -> hasGerDub should be true
        val dubJson = JSONObject("""
            {
                "id": "DUB1",
                "title": "Anime X (Synchro)",
                "images": {},
                "series_metadata": {
                    "audio_locales": [],
                    "subtitle_locales": []
                }
            }
        """.trimIndent())
        val dubItem = CrunchyrollClient.parseSeriesItem(dubJson)
        assertTrue(dubItem.hasGerDub)
        assertFalse(dubItem.hasGerSub)

        // subtitle_locales is empty, but title has "(OmU)" -> hasGerSub should be true
        val subJson = JSONObject("""
            {
                "id": "SUB1",
                "title": "Anime Y (OmU)",
                "images": {},
                "series_metadata": {
                    "audio_locales": [],
                    "subtitle_locales": []
                }
            }
        """.trimIndent())
        val subItem = CrunchyrollClient.parseSeriesItem(subJson)
        assertFalse(subItem.hasGerDub)
        assertTrue(subItem.hasGerSub)
    }

    @Test
    fun testCrunchyrollAuthenticationAndPagination() {
        val fakeTransport = FakeHttpTransport()
        val client = CrunchyrollClient(
            transport = fakeTransport,
            authUrl = "https://beta-api.crunchyroll.com/auth/v1/token",
            browseUrl = "https://beta-api.crunchyroll.com/content/v2/discover/browse"
        )

        var authCalls = 0
        var browseCalls = 0

        fakeTransport.responder = { url, _, headers, body ->
            when {
                url.contains("/auth/v1/token") -> {
                    authCalls++
                    assertEquals("Basic Y3Jfd2ViOg==", headers["Authorization"])
                    assertEquals("application/x-www-form-urlencoded", headers["Content-Type"])
                    assertTrue(body?.contains("grant_type=client_id") == true)
                    HttpResponse(
                        200,
                        mapOf("Content-Type" to listOf("application/json")),
                        """{"access_token": "token-abc-123", "expires_in": 3600, "token_type": "Bearer"}"""
                    )
                }
                url.contains("/content/v2/discover/browse") -> {
                    browseCalls++
                    assertEquals("Bearer token-abc-123", headers["Authorization"])
                    assertTrue(url.contains("locale=de-DE"))
                    HttpResponse(
                        200,
                        mapOf("Content-Type" to listOf("application/json")),
                        """
                        {
                            "total": 1,
                            "data": [
                                {
                                    "id": "S1",
                                    "title": "Series One",
                                    "images": {},
                                    "series_metadata": {
                                        "episode_count": 12,
                                        "audio_locales": ["de-DE"]
                                    }
                                }
                            ]
                        }
                        """.trimIndent()
                    )
                }
                else -> HttpResponse(404, emptyMap(), "Not Found")
            }
        }

        val page = client.fetchCatalogPage(start = 0, count = 10)
        assertEquals(1, authCalls)
        assertEquals(1, browseCalls)
        assertEquals(1, page.total)
        assertEquals(1, page.items.size)
        assertEquals("Series One", page.items[0].title)

        // Second fetch within token expiry should not re-authenticate
        val page2 = client.fetchCatalogPage(start = 10, count = 10)
        assertNotNull(page2)
        assertEquals(1, authCalls)
        assertEquals(2, browseCalls)
    }

    @Test
    fun testCrunchyroll401ReAuthenticateOnce() {
        val fakeTransport = FakeHttpTransport()
        val client = CrunchyrollClient(
            transport = fakeTransport,
            authUrl = "https://test.cr.com/auth",
            browseUrl = "https://test.cr.com/browse"
        )

        var authCount = 0
        var browseAttempts = 0

        fakeTransport.responder = { url, _, _, _ ->
            if (url.contains("/auth")) {
                authCount++
                HttpResponse(200, emptyMap(), """{"access_token": "token-$authCount", "expires_in": 3600}""")
            } else {
                browseAttempts++
                if (browseAttempts == 1) {
                    // First browse attempt fails with 401
                    HttpResponse(401, emptyMap(), """{"error": "invalid_token"}""")
                } else {
                    // Retry succeeds
                    HttpResponse(200, emptyMap(), """{"total": 1, "data": [{"id": "S2", "title": "Series Two"}]}""")
                }
            }
        }

        val page = client.fetchCatalogPage(0, 10)
        assertEquals(2, authCount)
        assertEquals(2, browseAttempts)
        assertEquals(1, page.items.size)
        assertEquals("Series Two", page.items[0].title)
    }

    @Test
    fun testAniListRateLimitRetryAfterBackoff() {
        val fakeTransport = FakeHttpTransport()
        val client = AniListClient(
            transport = fakeTransport,
            endpoint = "https://graphql.anilist.co"
        )

        var attempts = 0
        var waitCalledCount = 0
        var doneCalledCount = 0

        client.onRateLimitWait = { _, attempt ->
            waitCalledCount++
            assertEquals(1, attempt)
        }
        client.onRateLimitDone = {
            doneCalledCount++
        }

        fakeTransport.responder = { _, _, _, _ ->
            attempts++
            if (attempts == 1) {
                // Return 429 Too Many Requests with Retry-After 0
                HttpResponse(
                    429,
                    mapOf("Retry-After" to listOf("0")),
                    """{"errors": [{"message": "Too Many Requests."}]}"""
                )
            } else {
                HttpResponse(
                    200,
                    mapOf("Content-Type" to listOf("application/json")),
                    """
                    {
                        "data": {
                            "a0": {
                                "media": [
                                    {
                                        "id": 1234,
                                        "title": {"romaji": "Success On Retry"},
                                        "averageScore": 85,
                                        "tags": []
                                    }
                                ]
                            }
                        }
                    }
                    """.trimIndent()
                )
            }
        }

        val result = client.searchAnime("Success On Retry")
        assertNotNull(result)
        assertEquals(1234, result?.aniListId)
        assertEquals("Success On Retry", result?.titleRomaji)
        assertEquals(2, attempts)
        assertEquals(1, waitCalledCount)
        assertEquals(1, doneCalledCount)
    }

    @Test
    fun testAniListRateLimitExhaustion() {
        val fakeTransport = FakeHttpTransport()
        val client = AniListClient(
            transport = fakeTransport,
            endpoint = "https://graphql.anilist.co"
        )

        var attempts = 0
        fakeTransport.responder = { _, _, _, _ ->
            attempts++
            HttpResponse(429, mapOf("Retry-After" to listOf("0")), """{"errors": [{"message": "Too Many Requests."}]}""")
        }

        try {
            client.searchAnime("Limit Test")
            fail("Expected exception when rate limit retries exhausted")
        } catch (e: Exception) {
            assertTrue(e.message?.contains("rate limit", ignoreCase = true) == true)
        }
        assertEquals(4, attempts) // Initial attempt + 3 retries = 4
    }

    @Test
    fun testAniListTagFilteringAndMediaDataExtraction() {
        val fakeTransport = FakeHttpTransport()
        val client = AniListClient(
            transport = fakeTransport,
            endpoint = "https://graphql.anilist.co"
        )

        fakeTransport.responder = { _, _, _, _ ->
            HttpResponse(
                200,
                emptyMap(),
                """
                {
                    "data": {
                        "a0": {
                            "media": [
                                {
                                    "id": 154587,
                                    "title": {
                                        "romaji": "Sousou no Frieren",
                                        "english": "Frieren: Beyond Journey's End"
                                    },
                                    "description": "The adventure is over...",
                                    "averageScore": 91,
                                    "status": "FINISHED",
                                    "coverImage": {
                                        "large": "https://img.example.com/frieren.jpg"
                                    },
                                    "bannerImage": "https://img.example.com/banner.jpg",
                                    "siteUrl": "https://anilist.co/anime/154587",
                                    "genres": ["Adventure", "Fantasy"],
                                    "tags": [
                                        {"name": "Magic", "rank": 95, "isMediaSpoiler": false},
                                        {"name": "Low Rank Tag", "rank": 35, "isMediaSpoiler": false},
                                        {"name": "Spoiler Tag", "rank": 85, "isMediaSpoiler": true},
                                        {"name": "Elf", "rank": 80, "isMediaSpoiler": false}
                                    ]
                                }
                            ]
                        }
                    }
                }
                """.trimIndent()
            )
        }

        val media = client.searchAnime("Sousou no Frieren")
        assertNotNull(media)
        assertEquals(154587, media!!.aniListId)
        assertEquals("Sousou no Frieren", media.titleRomaji)
        assertEquals("Frieren: Beyond Journey's End", media.titleEnglish)
        assertEquals(91, media.score)
        assertEquals("FINISHED", media.status)
        assertEquals("https://img.example.com/frieren.jpg", media.coverImage)
        assertEquals("https://img.example.com/banner.jpg", media.bannerImage)
        assertEquals("https://anilist.co/anime/154587", media.siteUrl)
        assertEquals(listOf("Adventure", "Fantasy"), media.genres)

        // Verify tags: Only "Magic" (95) and "Elf" (80) should pass; "Low Rank Tag" (35) and "Spoiler Tag" (spoiler) excluded
        assertEquals(2, media.tags.size)
        assertEquals("Magic", media.tags[0].name)
        assertEquals(95, media.tags[0].rank)
        assertEquals("Elf", media.tags[1].name)
        assertEquals(80, media.tags[1].rank)
    }

    @Test
    fun testSyncManagerCrunchyrollSyncCoordination() = runBlocking {
        val fakeDb = FakeDatabaseDelegate()
        val repository = AnimeRepository(fakeDb)

        val fakeTransport = FakeHttpTransport()
        fakeTransport.responder = { url, _, _, _ ->
            if (url.contains("/auth")) {
                HttpResponse(200, emptyMap(), """{"access_token": "token-1", "expires_in": 3600}""")
            } else {
                HttpResponse(
                    200,
                    emptyMap(),
                    """
                    {
                        "total": 2,
                        "data": [
                            {"id": "CR1", "title": "Anime One", "images": {}, "series_metadata": {"episode_count": 12}},
                            {"id": "CR2", "title": "Anime Two", "images": {}, "series_metadata": {"episode_count": 24}}
                        ]
                    }
                    """.trimIndent()
                )
            }
        }

        val crClient = CrunchyrollClient(fakeTransport, "https://test.cr.com/auth", "https://test.cr.com/browse")
        val aniClient = AniListClient(fakeTransport, "https://test.anilist.co")
        val syncManager = SyncManager(repository, crClient, aniClient)

        val progressUpdates = mutableListOf<Pair<Int, Int>>()
        val syncJob = syncManager.startCrunchyrollSync(limit = 2) { synced, total ->
            progressUpdates.add(Pair(synced, total))
        }

        syncJob.join()

        val status = syncManager.getCrSyncStatus()
        assertFalse(status.getBoolean("is_running"))
        assertEquals(2, status.getInt("synced_count"))
        assertEquals(2, status.getInt("total_items"))
        assertEquals("", status.getString("last_error"))
        assertTrue(progressUpdates.isNotEmpty())

        // Verify state saved to repository
        val savedState = fakeDb.syncStates["crunchyroll"]
        assertNotNull(savedState)
        assertEquals(2, savedState?.syncedItems)
        assertEquals("idle", savedState?.status)
    }

    @Test
    fun testSyncManagerAniListSyncCoordination() = runBlocking {
        val fakeDb = FakeDatabaseDelegate()
        // Provide 2 anime in DB that need enrichment
        fakeDb.animeRows = listOf(
            FakeRow(mapOf("id" to "CR1", "title" to "Anime One", "anilist_id" to null)),
            FakeRow(mapOf("id" to "CR2", "title" to "Anime Two", "anilist_id" to null))
        )
        fakeDb.statsMap = mapOf(
            "total_anime" to 2,
            "anilist_enriched" to 0
        )

        val repository = AnimeRepository(fakeDb)
        val fakeTransport = FakeHttpTransport()
        fakeTransport.responder = { _, _, _, _ ->
            HttpResponse(
                200,
                emptyMap(),
                """
                {
                    "data": {
                        "a0": {
                            "media": [{"id": 1001, "title": {"romaji": "Anime One"}, "averageScore": 80, "tags": []}]
                        },
                        "a1": {
                            "media": [{"id": 1002, "title": {"romaji": "Anime Two"}, "averageScore": 85, "tags": []}]
                        }
                    }
                }
                """.trimIndent()
            )
        }

        val crClient = CrunchyrollClient(fakeTransport, "https://test.cr.com/auth", "https://test.cr.com/browse")
        val aniClient = AniListClient(fakeTransport, "https://test.anilist.co")
        val syncManager = SyncManager(repository, crClient, aniClient, aniListThrottleDelayMs = 0L)

        val progressUpdates = mutableListOf<Pair<Int, Int>>()
        val syncJob = syncManager.startAniListSync { done, total ->
            progressUpdates.add(Pair(done, total))
        }

        syncJob.join()

        val status = syncManager.getAniListSyncStatus()
        assertFalse(status.getBoolean("is_running"))
        assertEquals(2, status.getInt("enriched_count"))
        assertEquals("", status.getString("last_error"))
        assertTrue(progressUpdates.isNotEmpty())

        // Verify state saved to repository
        val savedState = fakeDb.syncStates["anilist"]
        assertNotNull(savedState)
        assertEquals("idle", savedState?.status)
    }

    @Test
    fun testCrunchyrollSyncCancellationResetsStateWithoutError() = runBlocking {
        val fakeDb = FakeDatabaseDelegate()
        val repository = AnimeRepository(fakeDb)
        val fakeTransport = FakeHttpTransport()

        var pageCount = 0
        fakeTransport.responder = { url, _, _, _ ->
            if (url.contains("/auth")) {
                HttpResponse(200, emptyMap(), """{"access_token": "token-1", "expires_in": 3600}""")
            } else {
                pageCount++
                HttpResponse(
                    200,
                    emptyMap(),
                    """
                    {
                        "total": 1000,
                        "data": [
                            {"id": "CR$pageCount", "title": "Anime $pageCount", "images": {}, "series_metadata": {"episode_count": 12}}
                        ]
                    }
                    """.trimIndent()
                )
            }
        }

        val crClient = CrunchyrollClient(fakeTransport, "https://test.cr.com/auth", "https://test.cr.com/browse")
        val aniClient = AniListClient(fakeTransport, "https://test.anilist.co")
        val syncManager = SyncManager(repository, crClient, aniClient, crunchyrollThrottleDelayMs = 50L)

        val syncJob = syncManager.startCrunchyrollSync()
        assertTrue(syncManager.isCrSyncRunning())

        syncManager.cancelCrunchyrollSync()
        syncJob.join()

        assertFalse(syncManager.isCrSyncRunning())
        val status = syncManager.getCrSyncStatus()
        assertFalse(status.getBoolean("is_running"))
        assertEquals("", status.getString("last_error"))
        assertTrue(status.isNull("last_sync_time"))

        val savedState = fakeDb.syncStates["crunchyroll"]
        assertEquals("idle", savedState?.status)
        assertEquals("", savedState?.lastError)
    }

    @Test
    fun testAniListSyncCancellationResetsStateWithoutError() = runBlocking {
        val fakeDb = FakeDatabaseDelegate()
        fakeDb.animeRows = (1..50).map { i ->
            FakeRow(mapOf("id" to "CR$i", "title" to "Anime $i", "anilist_id" to null))
        }
        fakeDb.statsMap = mapOf("total_anime" to 50, "anilist_enriched" to 0)
        val repository = AnimeRepository(fakeDb)

        val fakeTransport = FakeHttpTransport()
        fakeTransport.responder = { _, _, _, _ ->
            HttpResponse(
                200,
                emptyMap(),
                """
                {
                    "data": {
                        "a0": {"media": [{"id": 1, "title": {"romaji": "Anime 1"}}]}
                    }
                }
                """.trimIndent()
            )
        }

        val crClient = CrunchyrollClient(fakeTransport, "https://test.cr.com/auth", "https://test.cr.com/browse")
        val aniClient = AniListClient(fakeTransport, "https://test.anilist.co")
        val syncManager = SyncManager(repository, crClient, aniClient, aniListThrottleDelayMs = 50L)

        val syncJob = syncManager.startAniListSync()
        assertTrue(syncManager.isAniListSyncRunning())

        syncManager.cancelAniListSync()
        syncJob.join()

        assertFalse(syncManager.isAniListSyncRunning())
        val status = syncManager.getAniListSyncStatus()
        assertFalse(status.getBoolean("is_running"))
        assertEquals("", status.getString("last_error"))
        assertEquals("", status.getString("status_message"))
        assertTrue(status.isNull("last_sync_time"))

        val savedState = fakeDb.syncStates["anilist"]
        assertEquals("idle", savedState?.status)
        assertEquals("", savedState?.lastError)
    }

    @Test
    fun testOptNullableStringAndNullFieldsHandling() {
        val json = JSONObject("""
            {
                "id": 9999,
                "title": {
                    "romaji": "Only Romaji",
                    "english": null
                },
                "description": null,
                "averageScore": 75,
                "status": null,
                "coverImage": {
                    "large": null,
                    "medium": "https://img.example.com/med.jpg"
                },
                "bannerImage": null,
                "siteUrl": null,
                "genres": ["Action", null, "Adventure"],
                "tags": [
                    {"name": "Valid Tag", "rank": 80, "isMediaSpoiler": false},
                    {"name": null, "rank": 90, "isMediaSpoiler": false}
                ]
            }
        """.trimIndent())

        assertEquals("", json.optNullableString("bannerImage"))
        assertEquals("", json.optNullableString("description"))
        assertEquals("", json.optNullableString("non_existent_key"))
        assertEquals("Only Romaji", json.getJSONObject("title").optNullableString("romaji"))
        assertEquals("", json.getJSONObject("title").optNullableString("english"))

        val media = AniListClient.parseMediaData(json)
        assertEquals(9999, media.aniListId)
        assertEquals("Only Romaji", media.titleRomaji)
        assertEquals("", media.titleEnglish)
        assertEquals("", media.description)
        assertEquals("", media.status)
        assertEquals("https://img.example.com/med.jpg", media.coverImage)
        assertEquals("", media.bannerImage)
        assertEquals("", media.siteUrl)
        assertEquals(listOf("Action", "Adventure"), media.genres)
        assertEquals(1, media.tags.size)
        assertEquals("Valid Tag", media.tags[0].name)
    }
}
