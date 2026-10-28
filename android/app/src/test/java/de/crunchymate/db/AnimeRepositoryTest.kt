package de.crunchymate.db

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AnimeRepositoryTest {

    private lateinit var fakeDelegate: FakeDatabaseDelegate
    private lateinit var repository: AnimeRepository

    @Before
    fun setUp() {
        fakeDelegate = FakeDatabaseDelegate()
        fakeDelegate.existingAnimeIds.add("CR123")
        fakeDelegate.existingAnimeIds.add("G6WEM0E06")
        repository = AnimeRepository(fakeDelegate)
    }

    @Test
    fun testBuildSelectQuery_textSearch() {
        val query = repository.buildSelectQuery(
            query = "Frieren",
            genre = null,
            language = null,
            watched = null,
            meh = null,
            sort = null,
            limit = 50,
            offset = 0,
            blueTags = emptyList(),
            greenTags = emptyList(),
            orangeTags = emptyList()
        )

        assertTrue("Query must contain title / romaji / english search",
            query.sql.contains("(a.title LIKE ? OR a.anilist_title_romaji LIKE ? OR a.anilist_title_english LIKE ?)"))
        assertTrue("Arguments must contain search query with wildcards",
            query.args.contains("%Frieren%"))
        assertEquals("Should have 3 occurrences of %Frieren% in args",
            3, query.args.count { it == "%Frieren%" })
        // Default meh filter should be included
        assertTrue("Default meh condition must hide meh animes", query.sql.contains("a.is_meh = 0"))
    }

    @Test
    fun testBuildSelectQuery_genreFilter() {
        val query = repository.buildSelectQuery(
            query = null,
            genre = "Fantasy",
            language = null,
            watched = null,
            meh = null,
            sort = null,
            limit = 50,
            offset = 0,
            blueTags = emptyList(),
            greenTags = emptyList(),
            orangeTags = emptyList()
        )

        assertTrue("Query must filter by anime_genres EXISTS subquery",
            query.sql.contains("EXISTS (SELECT 1 FROM anime_genres ag JOIN genres g ON ag.genre_id = g.id WHERE ag.anime_id = a.id AND g.name = ?)"))
        assertTrue("Arguments must include genre name", query.args.contains("Fantasy"))
    }

    @Test
    fun testBuildSelectQuery_languageFiltering() {
        // Test GerDub
        val gerdubQuery = repository.buildSelectQuery(
            query = null, genre = null, language = "gerdub", watched = null, meh = null, sort = null,
            limit = 50, offset = 0, blueTags = emptyList(), greenTags = emptyList(), orangeTags = emptyList()
        )
        assertTrue("GerDub must require has_gerdub = 1", gerdubQuery.sql.contains("a.has_gerdub = 1"))
        assertFalse("GerDub alone should not require has_gersub", gerdubQuery.sql.contains("a.has_gersub = 1"))

        // Test GerSub
        val gersubQuery = repository.buildSelectQuery(
            query = null, genre = null, language = "gersub", watched = null, meh = null, sort = null,
            limit = 50, offset = 0, blueTags = emptyList(), greenTags = emptyList(), orangeTags = emptyList()
        )
        assertTrue("GerSub must require has_gersub = 1", gersubQuery.sql.contains("a.has_gersub = 1"))
        assertFalse("GerSub alone should not require has_gerdub", gersubQuery.sql.contains("a.has_gerdub = 1"))

        // Test Both
        val bothQuery = repository.buildSelectQuery(
            query = null, genre = null, language = "both", watched = null, meh = null, sort = null,
            limit = 50, offset = 0, blueTags = emptyList(), greenTags = emptyList(), orangeTags = emptyList()
        )
        assertTrue("Both must require has_gersub = 1 AND a.has_gerdub = 1",
            bothQuery.sql.contains("a.has_gersub = 1 AND a.has_gerdub = 1"))

        // Test Any / Either
        val anyQuery = repository.buildSelectQuery(
            query = null, genre = null, language = "any", watched = null, meh = null, sort = null,
            limit = 50, offset = 0, blueTags = emptyList(), greenTags = emptyList(), orangeTags = emptyList()
        )
        assertTrue("Any must require (a.has_gersub = 1 OR a.has_gerdub = 1)",
            anyQuery.sql.contains("(a.has_gersub = 1 OR a.has_gerdub = 1)"))
    }

    @Test
    fun testBuildSelectQuery_tagFiltering_greenMustTags() {
        val query = repository.buildSelectQuery(
            query = null, genre = null, language = null, watched = null, meh = null, sort = null,
            limit = 50, offset = 0, blueTags = emptyList(), greenTags = listOf("Isekai", "Magic"), orangeTags = emptyList()
        )

        // Each green tag must have its own EXISTS condition
        val greenConditions = query.sql.split("EXISTS (SELECT 1 FROM anime_tags at JOIN tags t ON at.tag_id = t.id WHERE at.anime_id = a.id AND t.name = ?)")
        assertEquals("Must have 2 green tag EXISTS subqueries", 3, greenConditions.size)
        assertTrue("Arguments must contain both green tags", query.args.contains("Isekai") && query.args.contains("Magic"))
    }

    @Test
    fun testBuildSelectQuery_tagFiltering_orangeExcludeTags() {
        val query = repository.buildSelectQuery(
            query = null, genre = null, language = null, watched = null, meh = null, sort = null,
            limit = 50, offset = 0, blueTags = emptyList(), greenTags = emptyList(), orangeTags = listOf("Horror", "Gore")
        )

        assertTrue("Orange tags must generate NOT EXISTS with IN clause",
            query.sql.contains("NOT EXISTS (SELECT 1 FROM anime_tags at JOIN tags t ON at.tag_id = t.id WHERE at.anime_id = a.id AND t.name IN (?, ?))"))
        assertTrue("Arguments must contain both exclude tags", query.args.contains("Horror") && query.args.contains("Gore"))
    }

    @Test
    fun testBuildSelectQuery_tagFiltering_bluePreferBoost() {
        val query = repository.buildSelectQuery(
            query = null, genre = null, language = null, watched = null, meh = null, sort = "score",
            limit = 50, offset = 0, blueTags = listOf("Comedy", "School"), greenTags = emptyList(), orangeTags = emptyList()
        )

        assertTrue("Query must calculate prefer_score in SELECT clause",
            query.sql.contains("COALESCE((SELECT COUNT(*) FROM anime_tags at JOIN tags t ON at.tag_id = t.id WHERE at.anime_id = a.id AND t.name IN (?, ?)), 0) AS prefer_score"))
        assertTrue("ORDER BY must place prefer_score DESC first before sort field",
            query.sql.contains("ORDER BY prefer_score DESC, a.anilist_score DESC, a.title ASC"))
        // Check argument ordering: select args for prefer_score must precede whereClause args
        assertEquals("First two args should be blue tags", listOf("Comedy", "School"), query.args.take(2))
    }

    @Test
    fun testBuildSelectQuery_watchedAndMehFilters() {
        // Watched = "watched"
        val watchedQuery = repository.buildSelectQuery(
            query = null, genre = null, language = null, watched = "watched", meh = "all", sort = null,
            limit = 50, offset = 0, blueTags = emptyList(), greenTags = emptyList(), orangeTags = emptyList()
        )
        assertTrue("Must filter a.watched = 1", watchedQuery.sql.contains("a.watched = 1"))
        val whereClause = watchedQuery.sql.substringAfter("WHERE").substringBefore("ORDER BY")
        assertFalse("meh=all should not filter is_meh in WHERE clause", whereClause.contains("a.is_meh"))

        // Watched = "unwatched"
        val unwatchedQuery = repository.buildSelectQuery(
            query = null, genre = null, language = null, watched = "unwatched", meh = "all", sort = null,
            limit = 50, offset = 0, blueTags = emptyList(), greenTags = emptyList(), orangeTags = emptyList()
        )
        assertTrue("Must filter a.watched = 0", unwatchedQuery.sql.contains("a.watched = 0"))

        // Meh = "only"
        val mehOnlyQuery = repository.buildSelectQuery(
            query = null, genre = null, language = null, watched = null, meh = "only", sort = null,
            limit = 50, offset = 0, blueTags = emptyList(), greenTags = emptyList(), orangeTags = emptyList()
        )
        assertTrue("Must filter a.is_meh = 1", mehOnlyQuery.sql.contains("a.is_meh = 1"))
    }

    @Test
    fun testBuildSelectQuery_noTrailingSemicolon() {
        val query = repository.buildSelectQuery()
        assertFalse("Select query must not end with a semicolon", query.sql.trim().endsWith(";"))
    }

    @Test
    fun testBuildCountQuery_matchesWhereClause() {
        val countQuery = repository.buildCountQuery(
            query = "Slime",
            genre = "Action",
            language = "gerdub",
            watched = "unwatched",
            meh = "exclude",
            greenTags = listOf("Monsters"),
            orangeTags = listOf("Mecha")
        )

        assertTrue("Count query must use SELECT COUNT(*) as count", countQuery.sql.startsWith("SELECT COUNT(*) as count FROM anime a WHERE "))
        assertFalse("Count query should not contain ORDER BY or LIMIT", countQuery.sql.contains("ORDER BY") || countQuery.sql.contains("LIMIT"))
        assertTrue("Count query args must match WHERE clause args", countQuery.args.contains("%Slime%") && countQuery.args.contains("Action"))
    }

    @Test
    fun testBuildCountQuery_emptyWhereClause() {
        val countQuery = repository.buildCountQuery(
            query = null, genre = null, language = null, watched = null, meh = "all", greenTags = emptyList(), orangeTags = emptyList()
        )
        assertEquals("SELECT COUNT(*) as count FROM anime a", countQuery.sql)
        assertTrue(countQuery.args.isEmpty())
    }

    @Test
    fun testSetWatched_togglesState() {
        // When setting watched = true
        val successTrue = repository.setWatched("CR123", true)
        assertTrue("setWatched should return true on success", successTrue)
        assertEquals("UPDATE anime SET watched = 1, watched_at = CURRENT_TIMESTAMP WHERE id = ?",
            fakeDelegate.lastExecutedSql)
        assertEquals(listOf("CR123"), fakeDelegate.lastExecutedArgs)

        // When setting watched = false
        val successFalse = repository.setWatched("CR123", false)
        assertTrue("setWatched should return true on success", successFalse)
        assertEquals("UPDATE anime SET watched = 0, watched_at = NULL WHERE id = ?",
            fakeDelegate.lastExecutedSql)
        assertEquals(listOf("CR123"), fakeDelegate.lastExecutedArgs)
    }

    @Test
    fun testSetWatched_nonExistentId_returnsFalse() {
        val success = repository.setWatched("NON_EXISTENT_ID", true)
        assertFalse("setWatched should return false for non-existent ID", success)
    }

    @Test
    fun testSetMeh_togglesState() {
        // When setting meh = true
        val successTrue = repository.setMeh("CR123", true)
        assertTrue("setMeh should return true on success", successTrue)
        assertEquals("UPDATE anime SET is_meh = 1, meh_at = CURRENT_TIMESTAMP WHERE id = ?",
            fakeDelegate.lastExecutedSql)
        assertEquals(listOf("CR123"), fakeDelegate.lastExecutedArgs)

        // When setting meh = false
        val successFalse = repository.setMeh("CR123", false)
        assertTrue("setMeh should return true on success", successFalse)
        assertEquals("UPDATE anime SET is_meh = 0, meh_at = NULL WHERE id = ?",
            fakeDelegate.lastExecutedSql)
        assertEquals(listOf("CR123"), fakeDelegate.lastExecutedArgs)
    }

    @Test
    fun testSetMeh_nonExistentId_returnsFalse() {
        val success = repository.setMeh("NON_EXISTENT_ID", true)
        assertFalse("setMeh should return false for non-existent ID", success)
    }

    @Test
    fun testAndroidCursorDelegate_countFallback() {
        // Test fallback from "count" to "COUNT(*)"
        val mockCursorCountStar = java.lang.reflect.Proxy.newProxyInstance(
            android.database.Cursor::class.java.classLoader,
            arrayOf(android.database.Cursor::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getColumnIndex" -> if (args?.get(0) == "COUNT(*)") 0 else -1
                "isNull" -> false
                "getInt" -> 42
                "getColumnCount" -> 1
                else -> null
            }
        } as android.database.Cursor

        val delegateCountStar = AndroidCursorDelegate(mockCursorCountStar)
        assertEquals(42, delegateCountStar.getInt("count"))

        // Test fallback to single column (index 0) when columnCount == 1
        val mockCursorSingleCol = java.lang.reflect.Proxy.newProxyInstance(
            android.database.Cursor::class.java.classLoader,
            arrayOf(android.database.Cursor::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "getColumnIndex" -> -1
                "isNull" -> false
                "getInt" -> 99
                "getColumnCount" -> 1
                else -> null
            }
        } as android.database.Cursor

        val delegateSingleCol = AndroidCursorDelegate(mockCursorSingleCol)
        assertEquals(99, delegateSingleCol.getInt("count"))
    }

    @Test
    fun testQueryAnime_returnsExpectedJsonObjectStructure() {
        // Configure fake results
        fakeDelegate.countResult = 1
        fakeDelegate.animeRows = listOf(
            FakeRow(mapOf(
                "id" to "G6WEM0E06",
                "title" to "Frieren: Beyond Journey's End",
                "slug" to "frieren-beyond-journeys-end",
                "description" to "An elf mage journeys...",
                "poster_tall" to "https://img.crunchyroll.com/tall.jpg",
                "poster_wide" to "https://img.crunchyroll.com/wide.jpg",
                "episode_count" to 28,
                "season_count" to 1,
                "launch_year" to 2023,
                "is_subbed" to 1,
                "is_dubbed" to 1,
                "has_gersub" to 1,
                "has_gerdub" to 1,
                "cr_updated_at" to "2024-03-01 12:00:00",
                "anilist_id" to 154587,
                "anilist_title_romaji" to "Sousou no Frieren",
                "anilist_title_english" to "Frieren: Beyond Journey's End",
                "anilist_description" to "The adventure is over...",
                "anilist_score" to 91,
                "anilist_status" to "FINISHED",
                "anilist_cover_image" to "https://anilist.co/cover.jpg",
                "anilist_banner_image" to "https://anilist.co/banner.jpg",
                "anilist_site_url" to "https://anilist.co/anime/154587",
                "anilist_synced_at" to "2024-03-01 12:00:00",
                "watched" to 0,
                "watched_at" to null,
                "is_meh" to 0,
                "meh_at" to null,
                "notes" to ""
            ))
        )
        fakeDelegate.genresByAnimeId["G6WEM0E06"] = listOf("Adventure", "Drama", "Fantasy")
        fakeDelegate.tagsByAnimeId["G6WEM0E06"] = listOf(
            AniListTag("Magic", 95),
            AniListTag("Elf", 90)
        )

        val result = repository.queryAnime(
            query = "Frieren",
            genre = null,
            language = null,
            watched = null,
            meh = null,
            sort = null,
            limit = 20,
            offset = 0,
            blueTags = emptyList(),
            greenTags = emptyList(),
            orangeTags = emptyList()
        )

        assertNotNull(result)
        assertEquals(1, result.getInt("total"))
        assertEquals(20, result.getInt("limit"))
        assertEquals(0, result.getInt("offset"))
        val items = result.getJSONArray("items")
        assertEquals(1, items.length())

        val item = items.getJSONObject(0)
        assertEquals("G6WEM0E06", item.getString("id"))
        assertEquals("Frieren: Beyond Journey's End", item.getString("title"))
        assertEquals(91, item.getInt("anilist_score"))
        assertTrue(item.getBoolean("has_gerdub"))
        assertTrue(item.getBoolean("has_gersub"))
        assertEquals(3, item.getJSONArray("genres").length())
        assertEquals(2, item.getJSONArray("tags").length())
    }

    @Test
    fun testGetStats_returnsTotalWatchedMehAndEnriched() {
        fakeDelegate.statsMap = mapOf(
            "total_anime" to 1200,
            "watched_anime" to 45,
            "meh_anime" to 12,
            "genres_count" to 22,
            "tags_count" to 310,
            "anilist_enriched" to 850
        )

        val stats = repository.getStats()
        assertEquals(1200, stats.getInt("total_anime"))
        assertEquals(45, stats.getInt("watched_anime"))
        assertEquals(12, stats.getInt("meh_anime"))
        assertEquals(22, stats.getInt("genres_count"))
        assertEquals(310, stats.getInt("tags_count"))
        assertEquals(850, stats.getInt("anilist_enriched"))
    }

    @Test
    fun testUpsertCrunchyrollSeries_executesUpsertAndGenreInserts() {
        val series = listOf(
            CRSeriesItem(
                id = "CR_SERIES_1",
                title = "Jujutsu Kaisen",
                slug = "jujutsu-kaisen",
                description = "Sorcerers fight curses...",
                posterTall = "https://img.cr.com/jjk_tall.jpg",
                posterWide = "https://img.cr.com/jjk_wide.jpg",
                episodeCount = 47,
                seasonCount = 2,
                launchYear = 2020,
                isSubbed = true,
                isDubbed = true,
                hasGerSub = true,
                hasGerDub = true,
                genres = listOf("Action", "Supernatural")
            )
        )

        repository.upsertCrunchyrollSeries(series)

        // Verify statements executed
        val statements = fakeDelegate.executedStatements
        assertTrue("Should have executed multiple statements (anime upsert + genres)", statements.size >= 3)

        // 1. Anime upsert
        val animeUpsert = statements[0]
        assertTrue("First statement must be INSERT INTO anime ON CONFLICT", animeUpsert.first.startsWith("INSERT INTO anime"))
        assertEquals("CR_SERIES_1", animeUpsert.second?.get(0))
        assertEquals("Jujutsu Kaisen", animeUpsert.second?.get(1))
        assertEquals(47, animeUpsert.second?.get(6)) // episode_count
        assertEquals(1, animeUpsert.second?.get(11)) // has_gersub
        assertEquals(1, animeUpsert.second?.get(12)) // has_gerdub

        // 2. Genre inserts
        val genreInserts = statements.filter { it.first.contains("INSERT OR IGNORE INTO genres") }
        assertEquals(2, genreInserts.size)
        assertEquals("Action", genreInserts[0].second?.get(0))
        assertEquals("Supernatural", genreInserts[1].second?.get(0))
    }

    @Test
    fun testUpdateAniListMetadata_executesUpdateAndTagInserts() {
        val aniData = AniListMediaData(
            aniListId = 113415,
            titleRomaji = "Jujutsu Kaisen",
            titleEnglish = "JUJUTSU KAISEN",
            description = "Yuji Itadori...",
            score = 86,
            status = "FINISHED",
            coverImage = "https://anilist.co/jjk_cover.jpg",
            bannerImage = "https://anilist.co/jjk_banner.jpg",
            siteUrl = "https://anilist.co/anime/113415",
            tags = listOf(
                AniListTag("Curse", 95),
                AniListTag("Shounen", 90)
            ),
            genres = listOf("Action")
        )

        repository.updateAniListMetadata("CR_SERIES_1", aniData)

        val statements = fakeDelegate.executedStatements
        assertTrue("Should have executed update anime + tag inserts", statements.isNotEmpty())

        val updateAnime = statements[0]
        assertTrue("First statement must be UPDATE anime SET anilist_id", updateAnime.first.startsWith("UPDATE anime SET"))
        assertEquals(113415, updateAnime.second?.get(0)) // anilist_id
        assertEquals(86, updateAnime.second?.get(4)) // score
        assertEquals("CR_SERIES_1", updateAnime.second?.get(9)) // WHERE id = ?

        // Tags upsert
        val tagInserts = statements.filter { it.first.contains("INSERT OR IGNORE INTO tags") }
        assertEquals(2, tagInserts.size)
        assertEquals("Curse", tagInserts[0].second?.get(0))
        assertEquals("Shounen", tagInserts[1].second?.get(0))

        val animeTagLinks = statements.filter { it.first.contains("INSERT INTO anime_tags") }
        assertEquals(2, animeTagLinks.size)
        assertEquals("CR_SERIES_1", animeTagLinks[0].second?.get(0))
        assertEquals(95, animeTagLinks[0].second?.get(1)) // rank
        assertEquals("Curse", animeTagLinks[0].second?.get(2))
    }

    @Test
    fun testGetAnimeById_foundAndNotFound() {
        // Not found case
        fakeDelegate.singleAnimeRow = null
        val notFound = repository.getAnimeById("NON_EXISTING")
        assertNull("Should return null for non-existing anime", notFound)

        // Found case
        fakeDelegate.singleAnimeRow = FakeRow(mapOf(
            "id" to "CR123",
            "title" to "Chainsaw Man",
            "slug" to "chainsaw-man",
            "description" to "Denji is a teenage boy...",
            "poster_tall" to "https://img.cr.com/csm.jpg",
            "poster_wide" to "",
            "episode_count" to 12,
            "season_count" to 1,
            "launch_year" to 2022,
            "is_subbed" to 1,
            "is_dubbed" to 1,
            "has_gersub" to 1,
            "has_gerdub" to 1,
            "cr_updated_at" to "2024-01-01",
            "anilist_id" to 127230,
            "anilist_title_romaji" to "Chainsaw Man",
            "anilist_title_english" to "Chainsaw Man",
            "anilist_description" to "...",
            "anilist_score" to 85,
            "anilist_status" to "FINISHED",
            "anilist_cover_image" to "",
            "anilist_banner_image" to "",
            "anilist_site_url" to "https://anilist.co/anime/127230",
            "anilist_synced_at" to "2024-01-01",
            "watched" to 1,
            "watched_at" to "2024-01-05",
            "is_meh" to 0,
            "meh_at" to null,
            "notes" to ""
        ))
        fakeDelegate.genresByAnimeId["CR123"] = listOf("Action", "Horror")
        fakeDelegate.tagsByAnimeId["CR123"] = listOf(AniListTag("Gore", 90))

        val found = repository.getAnimeById("CR123")
        assertNotNull(found)
        assertEquals("CR123", found!!.getString("id"))
        assertEquals("Chainsaw Man", found.getString("title"))
        assertEquals(127230, found.getInt("anilist_id"))
        assertTrue(found.getBoolean("watched"))
        assertEquals(2, found.getJSONArray("genres").length())
        assertEquals(1, found.getJSONArray("tags").length())
    }

    @Test
    fun testGetGenres_returnsJsonArray() {
        fakeDelegate.genreCounts = listOf(
            GenreCount("Action", 150),
            GenreCount("Comedy", 120),
            GenreCount("Romance", 80)
        )

        val genres = repository.getGenres()
        assertEquals(3, genres.length())
        assertEquals("Action", genres.getJSONObject(0).getString("name"))
        assertEquals(150, genres.getJSONObject(0).getInt("count"))
        assertEquals("Comedy", genres.getJSONObject(1).getString("name"))
        assertEquals(120, genres.getJSONObject(1).getInt("count"))
    }

    @Test
    fun testGetTags_returnsJsonArray() {
        fakeDelegate.tagCounts = listOf(
            TagCount("Magic", 200),
            TagCount("Demons", 110)
        )

        val tags = repository.getTags()
        assertEquals(2, tags.length())
        assertEquals("Magic", tags.getJSONObject(0).getString("name"))
        assertEquals(200, tags.getJSONObject(0).getInt("count"))
    }

    @Test
    fun testSyncState_getAndUpdate() {
        val state = SyncState(
            key = "crunchyroll",
            lastSyncedAt = "2024-03-01T12:00:00Z",
            totalItems = 950,
            syncedItems = 950,
            status = "completed",
            lastError = ""
        )

        repository.updateSyncState(state)
        assertTrue("updateSyncState must execute INSERT INTO sync_state ON CONFLICT",
            fakeDelegate.lastExecutedSql!!.startsWith("INSERT INTO sync_state"))
        assertEquals("crunchyroll", fakeDelegate.lastExecutedArgs?.get(0))
        assertEquals("2024-03-01T12:00:00Z", fakeDelegate.lastExecutedArgs?.get(1))
        assertEquals(950, fakeDelegate.lastExecutedArgs?.get(2))

        fakeDelegate.syncStates["crunchyroll"] = state
        val loaded = repository.getSyncState("crunchyroll")
        assertNotNull(loaded)
        assertEquals("crunchyroll", loaded!!.key)
        assertEquals(950, loaded.totalItems)
        assertEquals("completed", loaded.status)
    }
}
