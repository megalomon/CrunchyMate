package de.crunchymate.bridge

import de.crunchymate.db.AniListTag
import de.crunchymate.db.AnimeRepository
import de.crunchymate.db.FakeDatabaseDelegate
import de.crunchymate.db.FakeRow
import de.crunchymate.db.GenreCount
import de.crunchymate.db.TagCount
import de.crunchymate.sync.AniListClient
import de.crunchymate.sync.CrunchyrollClient
import de.crunchymate.sync.FakeHttpTransport
import de.crunchymate.sync.SyncManager
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AndroidBridgeTest {

    private lateinit var fakeDb: FakeDatabaseDelegate
    private lateinit var repository: AnimeRepository
    private lateinit var fakeTransport: FakeHttpTransport
    private lateinit var crClient: CrunchyrollClient
    private lateinit var aniClient: AniListClient
    private lateinit var syncManager: SyncManager
    private lateinit var bridge: AndroidBridge

    @Before
    fun setUp() {
        fakeDb = FakeDatabaseDelegate()
        repository = AnimeRepository(fakeDb)
        fakeTransport = FakeHttpTransport()
        crClient = CrunchyrollClient(fakeTransport, "https://test.cr.com/auth", "https://test.cr.com/browse")
        aniClient = AniListClient(fakeTransport, "https://test.anilist.co")
        syncManager = SyncManager(
            repository = repository,
            crClient = crClient,
            aniClient = aniClient,
            crunchyrollThrottleDelayMs = 0L,
            aniListThrottleDelayMs = 0L
        )
        bridge = AndroidBridge(repository = repository, syncManager = syncManager)
    }

    @Test
    fun testQueryAnime_defaultAndEmptyJson() {
        fakeDb.countResult = 0
        fakeDb.animeRows = emptyList()

        val resEmpty = bridge.queryAnime("")
        val jsonEmpty = JSONObject(resEmpty)
        assertEquals(0, jsonEmpty.getInt("total"))
        assertEquals(50, jsonEmpty.getInt("limit"))
        assertEquals(0, jsonEmpty.getInt("offset"))
        assertEquals(0, jsonEmpty.getJSONArray("items").length())

        val resBraces = bridge.queryAnime("{}")
        val jsonBraces = JSONObject(resBraces)
        assertEquals(0, jsonBraces.getInt("total"))
    }

    @Test
    fun testQueryAnime_parameterParsing() {
        fakeDb.countResult = 1
        fakeDb.animeRows = listOf(
            FakeRow(
                mapOf(
                    "id" to "G62P407P6",
                    "title" to "Frieren: Beyond Journey's End",
                    "slug" to "frieren",
                    "description" to "Elfenmagierin Frieren",
                    "poster_tall" to "https://img.cr.com/frieren.jpg",
                    "poster_wide" to null,
                    "episode_count" to 28,
                    "season_count" to 1,
                    "launch_year" to 2023,
                    "is_subbed" to 1,
                    "is_dubbed" to 1,
                    "has_gersub" to 1,
                    "has_gerdub" to 1,
                    "cr_updated_at" to "2024-01-01T00:00:00Z",
                    "anilist_id" to 154587,
                    "anilist_title_romaji" to "Sousou no Frieren",
                    "anilist_title_english" to "Frieren: Beyond Journey's End",
                    "anilist_description" to "After the party defeated the demon king...",
                    "anilist_score" to 91,
                    "anilist_status" to "FINISHED",
                    "anilist_cover_image" to "https://img.al.co/frieren.jpg",
                    "anilist_banner_image" to null,
                    "anilist_site_url" to "https://anilist.co/anime/154587",
                    "anilist_synced_at" to "2024-01-01T00:00:00Z",
                    "watched" to 1,
                    "watched_at" to "2024-01-02T12:00:00Z",
                    "is_meh" to 0,
                    "meh_at" to null,
                    "notes" to "Meisterwerk"
                )
            )
        )
        fakeDb.genresByAnimeId["G62P407P6"] = listOf("Adventure", "Fantasy")
        fakeDb.tagsByAnimeId["G62P407P6"] = listOf(AniListTag("Magic", 95))

        val filter = JSONObject().apply {
            put("q", "Frieren")
            put("genre", "Fantasy")
            put("watched", "watched")
            put("meh", "exclude")
            put("sort", "score")
            put("limit", "10")
            put("offset", "5")
            put("must_tags", "Magic, Elves")
            put("exclude_tags", "Ecchi")
            put("prefer_tags", "Adventure")
            put("gersub", true)
            put("gerdub", true)
        }

        val res = bridge.queryAnime(filter.toString())
        val json = JSONObject(res)

        assertEquals(1, json.getInt("total"))
        assertEquals(10, json.getInt("limit"))
        assertEquals(5, json.getInt("offset"))

        val items = json.getJSONArray("items")
        assertEquals(1, items.length())

        val anime = items.getJSONObject(0)
        assertEquals("G62P407P6", anime.getString("id"))
        assertEquals("Frieren: Beyond Journey's End", anime.getString("title"))
        assertTrue(anime.getBoolean("watched"))
        assertFalse(anime.getBoolean("is_meh"))
        assertEquals(91, anime.getInt("anilist_score"))

        val genres = anime.getJSONArray("genres")
        assertEquals(2, genres.length())
        assertEquals("Adventure", genres.getString(0))
        assertEquals("Fantasy", genres.getString(1))

        val tags = anime.getJSONArray("tags")
        assertEquals(1, tags.length())
        assertEquals("Magic", tags.getJSONObject(0).getString("name"))
        assertEquals(95, tags.getJSONObject(0).getInt("rank"))
    }

    @Test
    fun testGetAnime_existingAndNonExisting() {
        fakeDb.singleAnimeRow = FakeRow(
            mapOf(
                "id" to "TEST1234",
                "title" to "Steins;Gate",
                "slug" to "steins-gate",
                "description" to "Time travel adventure",
                "poster_tall" to "https://img.cr.com/sg.jpg",
                "poster_wide" to null,
                "episode_count" to 24,
                "season_count" to 1,
                "launch_year" to 2011,
                "is_subbed" to 1,
                "is_dubbed" to 1,
                "has_gersub" to 1,
                "has_gerdub" to 1,
                "cr_updated_at" to "2024-01-01T00:00:00Z",
                "anilist_id" to 9253,
                "anilist_title_romaji" to "Steins;Gate",
                "anilist_title_english" to "Steins;Gate",
                "anilist_description" to "Self-proclaimed mad scientist...",
                "anilist_score" to 89,
                "anilist_status" to "FINISHED",
                "anilist_cover_image" to "https://img.al.co/sg.jpg",
                "anilist_banner_image" to null,
                "anilist_site_url" to "https://anilist.co/anime/9253",
                "anilist_synced_at" to "2024-01-01T00:00:00Z",
                "watched" to 0,
                "watched_at" to null,
                "is_meh" to 0,
                "meh_at" to null,
                "notes" to null
            )
        )
        fakeDb.genresByAnimeId["TEST1234"] = listOf("Sci-Fi", "Thriller")

        // 1. Existing anime
        val res = bridge.getAnime("TEST1234")
        val json = JSONObject(res)
        assertEquals("TEST1234", json.getString("id"))
        assertEquals("Steins;Gate", json.getString("title"))
        assertEquals(2, json.getJSONArray("genres").length())

        // 2. Non-existing anime
        fakeDb.singleAnimeRow = null
        val notFoundRes = bridge.getAnime("NON_EXISTING")
        val notFoundJson = JSONObject(notFoundRes)
        assertTrue(notFoundJson.has("error"))
        assertEquals("anime not found", notFoundJson.getString("error"))

        // 3. Blank id
        val blankRes = bridge.getAnime("   ")
        val blankJson = JSONObject(blankRes)
        assertTrue(blankJson.has("error"))
        assertEquals("missing anime id", blankJson.getString("error"))
    }

    @Test
    fun testSetWatched_successAndFailure() {
        val animeRow = FakeRow(
            mapOf(
                "id" to "ANIME_1",
                "title" to "Solo Leveling",
                "slug" to "solo-leveling",
                "description" to "Sung Jinwoo leveling up",
                "poster_tall" to null,
                "poster_wide" to null,
                "episode_count" to 12,
                "season_count" to 1,
                "launch_year" to 2024,
                "is_subbed" to 1,
                "is_dubbed" to 1,
                "has_gersub" to 1,
                "has_gerdub" to 1,
                "cr_updated_at" to null,
                "anilist_id" to 151807,
                "anilist_title_romaji" to "Ore dake Level Up na Ken",
                "anilist_title_english" to "Solo Leveling",
                "anilist_description" to null,
                "anilist_score" to 84,
                "anilist_status" to "FINISHED",
                "anilist_cover_image" to null,
                "anilist_banner_image" to null,
                "anilist_site_url" to null,
                "anilist_synced_at" to null,
                "watched" to 1,
                "watched_at" to "2024-03-01T12:00:00Z",
                "is_meh" to 0,
                "meh_at" to null,
                "notes" to null
            )
        )

        // Success case
        fakeDb.changesCount = 1
        fakeDb.singleAnimeRow = animeRow

        val res = bridge.setWatched("ANIME_1", true)
        val json = JSONObject(res)
        assertTrue(json.getBoolean("success"))
        assertTrue(json.has("anime"))
        assertEquals("ANIME_1", json.getJSONObject("anime").getString("id"))
        assertTrue(json.getJSONObject("anime").getBoolean("watched"))

        // Failure case (not found)
        fakeDb.changesCount = 0
        fakeDb.singleAnimeRow = null
        val failRes = bridge.setWatched("MISSING", true)
        val failJson = JSONObject(failRes)
        assertFalse(failJson.getBoolean("success"))

        // Blank ID
        val blankRes = bridge.setWatched("", true)
        val blankJson = JSONObject(blankRes)
        assertFalse(blankJson.getBoolean("success"))
    }

    @Test
    fun testSetMeh_successAndFailure() {
        val animeRow = FakeRow(
            mapOf(
                "id" to "MEH_ANIME",
                "title" to "Boring Anime",
                "slug" to "boring-anime",
                "description" to "Not interesting",
                "poster_tall" to null,
                "poster_wide" to null,
                "episode_count" to 12,
                "season_count" to 1,
                "launch_year" to 2020,
                "is_subbed" to 1,
                "is_dubbed" to 0,
                "has_gersub" to 1,
                "has_gerdub" to 0,
                "cr_updated_at" to null,
                "anilist_id" to null,
                "anilist_title_romaji" to null,
                "anilist_title_english" to null,
                "anilist_description" to null,
                "anilist_score" to 40,
                "anilist_status" to null,
                "anilist_cover_image" to null,
                "anilist_banner_image" to null,
                "anilist_site_url" to null,
                "anilist_synced_at" to null,
                "watched" to 0,
                "watched_at" to null,
                "is_meh" to 1,
                "meh_at" to "2024-03-01T12:00:00Z",
                "notes" to null
            )
        )

        // Success case
        fakeDb.changesCount = 1
        fakeDb.singleAnimeRow = animeRow

        val res = bridge.setMeh("MEH_ANIME", true)
        val json = JSONObject(res)
        assertTrue(json.getBoolean("success"))
        assertTrue(json.has("anime"))
        assertEquals("MEH_ANIME", json.getJSONObject("anime").getString("id"))
        assertTrue(json.getJSONObject("anime").getBoolean("is_meh"))

        // Failure case
        fakeDb.changesCount = 0
        fakeDb.singleAnimeRow = null
        val failRes = bridge.setMeh("MISSING", false)
        val failJson = JSONObject(failRes)
        assertFalse(failJson.getBoolean("success"))
    }

    @Test
    fun testGetGenres() {
        fakeDb.genreCounts = listOf(
            GenreCount("Action", 15),
            GenreCount("Comedy", 8)
        )

        val res = bridge.getGenres()
        val arr = JSONArray(res)
        assertEquals(2, arr.length())
        assertEquals("Action", arr.getJSONObject(0).getString("name"))
        assertEquals(15, arr.getJSONObject(0).getInt("count"))
        assertEquals("Comedy", arr.getJSONObject(1).getString("name"))
        assertEquals(8, arr.getJSONObject(1).getInt("count"))
    }

    @Test
    fun testGetTags() {
        fakeDb.tagCounts = listOf(
            TagCount("Magic", 22),
            TagCount("Shounen", 18)
        )

        val res = bridge.getTags()
        val arr = JSONArray(res)
        assertEquals(2, arr.length())
        assertEquals("Magic", arr.getJSONObject(0).getString("name"))
        assertEquals(22, arr.getJSONObject(0).getInt("count"))
        assertEquals("Shounen", arr.getJSONObject(1).getString("name"))
        assertEquals(18, arr.getJSONObject(1).getInt("count"))
    }

    @Test
    fun testGetStats() {
        fakeDb.statsMap = mapOf(
            "total_anime" to 450,
            "watched_anime" to 82,
            "meh_anime" to 14,
            "anilist_enriched" to 420,
            "genres_count" to 18,
            "tags_count" to 125
        )

        val res = bridge.getStats()
        val json = JSONObject(res)
        assertEquals(450, json.getInt("total_anime"))
        assertEquals(82, json.getInt("watched_anime"))
        assertEquals(14, json.getInt("meh_anime"))
        assertEquals(420, json.getInt("anilist_enriched"))
        assertEquals(18, json.getInt("genres_count"))
        assertEquals(125, json.getInt("tags_count"))
    }

    @Test
    fun testCrunchyrollSyncLifecycle() {
        fakeTransport.responder = { url, _, _, _ ->
            if (url.contains("/auth/v1/token")) {
                de.crunchymate.sync.HttpResponse(200, emptyMap(), """{"access_token": "token123", "token_type": "Bearer"}""")
            } else {
                de.crunchymate.sync.HttpResponse(200, emptyMap(), """{"total": 0, "data": []}""")
            }
        }

        // Initially idle
        val initialStatus = JSONObject(bridge.getCrSyncStatus())
        assertFalse(initialStatus.getBoolean("is_running"))

        // Start sync
        val startRes = JSONObject(bridge.startCrSync())
        assertTrue(startRes.getBoolean("success"))
        assertEquals("started", startRes.getString("status"))

        // Calling start again while running returns already_running
        val secondStart = JSONObject(bridge.startCrSync())
        assertFalse(secondStart.getBoolean("success"))
        assertEquals("already_running", secondStart.getString("status"))
    }

    @Test
    fun testAniListSyncLifecycleAndMethodAliases() {
        fakeDb.animeRows = emptyList()

        // Initially idle
        val initialStatus = JSONObject(bridge.getAniListSyncStatus())
        assertFalse(initialStatus.getBoolean("is_running"))

        // Alias getAniSyncStatus matches getAniListSyncStatus
        val aliasStatus = JSONObject(bridge.getAniSyncStatus())
        assertEquals(initialStatus.getBoolean("is_running"), aliasStatus.getBoolean("is_running"))

        // Start sync via startAniSync alias
        val startRes = JSONObject(bridge.startAniSync())
        assertTrue(startRes.getBoolean("success"))
        assertEquals("started", startRes.getString("status"))

        // Calling startAniListSync while running returns already_running
        val secondStart = JSONObject(bridge.startAniListSync())
        assertFalse(secondStart.getBoolean("success"))
        assertEquals("already_running", secondStart.getString("status"))
    }
}
