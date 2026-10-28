package de.crunchymate.db

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class AndroidDatabaseDelegate(private val dbHelper: AppDatabaseHelper) : DatabaseDelegate {
    override fun rawQuery(sql: String, selectionArgs: Array<String>?): CursorDelegate {
        val cursor = dbHelper.writableDatabase.rawQuery(sql, selectionArgs)
        return AndroidCursorDelegate(cursor)
    }

    override fun execSQL(sql: String, bindArgs: Array<Any?>?) {
        val db = dbHelper.writableDatabase
        if (bindArgs != null) {
            db.execSQL(sql, bindArgs)
        } else {
            db.execSQL(sql)
        }
    }

    override fun <T> transaction(block: () -> T): T {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            val result = block()
            db.setTransactionSuccessful()
            return result
        } finally {
            db.endTransaction()
        }
    }
}

class AndroidCursorDelegate(private val cursor: android.database.Cursor) : CursorDelegate {
    override fun moveToNext(): Boolean = cursor.moveToNext()

    override fun getString(columnName: String): String? {
        val idx = cursor.getColumnIndex(columnName)
        return if (idx >= 0 && !cursor.isNull(idx)) cursor.getString(idx) else null
    }

    override fun getInt(columnName: String): Int {
        var idx = cursor.getColumnIndex(columnName)
        if (idx < 0) {
            if (columnName.equals("count", ignoreCase = true)) {
                idx = cursor.getColumnIndex("COUNT(*)")
                if (idx < 0) idx = cursor.getColumnIndex("changes()")
                if (idx < 0 && cursor.columnCount == 1) idx = 0
            } else if (columnName.equals("COUNT(*)", ignoreCase = true)) {
                idx = cursor.getColumnIndex("count")
                if (idx < 0) idx = cursor.getColumnIndex("changes()")
                if (idx < 0 && cursor.columnCount == 1) idx = 0
            }
        }
        return if (idx >= 0 && !cursor.isNull(idx)) cursor.getInt(idx) else 0
    }

    override fun getLong(columnName: String): Long {
        var idx = cursor.getColumnIndex(columnName)
        if (idx < 0) {
            if (columnName.equals("count", ignoreCase = true)) {
                idx = cursor.getColumnIndex("COUNT(*)")
                if (idx < 0) idx = cursor.getColumnIndex("changes()")
                if (idx < 0 && cursor.columnCount == 1) idx = 0
            } else if (columnName.equals("COUNT(*)", ignoreCase = true)) {
                idx = cursor.getColumnIndex("count")
                if (idx < 0) idx = cursor.getColumnIndex("changes()")
                if (idx < 0 && cursor.columnCount == 1) idx = 0
            }
        }
        return if (idx >= 0 && !cursor.isNull(idx)) cursor.getLong(idx) else 0L
    }

    override fun getDouble(columnName: String): Double {
        val idx = cursor.getColumnIndex(columnName)
        return if (idx >= 0 && !cursor.isNull(idx)) cursor.getDouble(idx) else 0.0
    }

    override fun getBoolean(columnName: String): Boolean {
        val idx = cursor.getColumnIndex(columnName)
        return if (idx >= 0 && !cursor.isNull(idx)) cursor.getInt(idx) == 1 else false
    }

    override fun isNull(columnName: String): Boolean {
        val idx = cursor.getColumnIndex(columnName)
        return idx < 0 || cursor.isNull(idx)
    }

    override val count: Int
        get() = cursor.count

    override fun close() {
        cursor.close()
    }
}

class AnimeRepository(
    private val delegate: DatabaseDelegate
) {
    constructor(dbHelper: AppDatabaseHelper) : this(AndroidDatabaseDelegate(dbHelper))
    constructor(context: Context) : this(AppDatabaseHelper.getInstance(context))

    /**
     * Builds the WHERE clause and associated arguments matching Go db.go ListAnime logic.
     */
    private fun buildWhereClause(
        query: String?,
        genre: String?,
        language: String?,
        watched: String?,
        meh: String?,
        greenTags: List<String>,
        orangeTags: List<String>
    ): Pair<String, List<String>> {
        val conditions = mutableListOf<String>()
        val args = mutableListOf<String>()

        val cleanQuery = query?.trim()
        if (!cleanQuery.isNullOrEmpty()) {
            conditions.add("(a.title LIKE ? OR a.anilist_title_romaji LIKE ? OR a.anilist_title_english LIKE ?)")
            val q = "%$cleanQuery%"
            args.add(q)
            args.add(q)
            args.add(q)
        }

        val cleanGenre = genre?.trim()
        if (!cleanGenre.isNullOrEmpty()) {
            conditions.add("EXISTS (SELECT 1 FROM anime_genres ag JOIN genres g ON ag.genre_id = g.id WHERE ag.anime_id = a.id AND g.name = ?)")
            args.add(cleanGenre)
        }

        // MustTags (Green): All must be present
        for (mt in greenTags) {
            val clean = mt.trim()
            if (clean.isNotEmpty()) {
                conditions.add("EXISTS (SELECT 1 FROM anime_tags at JOIN tags t ON at.tag_id = t.id WHERE at.anime_id = a.id AND t.name = ?)")
                args.add(clean)
            }
        }

        // ExcludeTags (Orange): None may be present
        val cleanExclude = orangeTags.map { it.trim() }.filter { it.isNotEmpty() }
        if (cleanExclude.isNotEmpty()) {
            val placeholders = cleanExclude.joinToString(", ") { "?" }
            conditions.add("NOT EXISTS (SELECT 1 FROM anime_tags at JOIN tags t ON at.tag_id = t.id WHERE at.anime_id = a.id AND t.name IN ($placeholders))")
            args.addAll(cleanExclude)
        }

        when (watched?.trim()?.lowercase(Locale.ROOT)) {
            "watched" -> conditions.add("a.watched = 1")
            "unwatched" -> conditions.add("a.watched = 0")
        }

        when (meh?.trim()?.lowercase(Locale.ROOT)) {
            "only" -> conditions.add("a.is_meh = 1")
            "all" -> { /* do not filter is_meh */ }
            else -> conditions.add("a.is_meh = 0") // default: hide meh
        }

        when (language?.trim()?.lowercase(Locale.ROOT)) {
            "gerdub" -> conditions.add("a.has_gerdub = 1")
            "gersub" -> conditions.add("a.has_gersub = 1")
            "both" -> conditions.add("a.has_gersub = 1 AND a.has_gerdub = 1")
            "any", "either" -> conditions.add("(a.has_gersub = 1 OR a.has_gerdub = 1)")
        }

        val whereClause = if (conditions.isNotEmpty()) {
            "WHERE " + conditions.joinToString(" AND ")
        } else {
            ""
        }

        return Pair(whereClause, args)
    }

    fun buildCountQuery(
        query: String? = null,
        genre: String? = null,
        language: String? = null,
        watched: String? = null,
        meh: String? = null,
        greenTags: List<String> = emptyList(),
        orangeTags: List<String> = emptyList()
    ): SqlQuery {
        val (whereClause, args) = buildWhereClause(query, genre, language, watched, meh, greenTags, orangeTags)
        val sql = if (whereClause.isNotEmpty()) {
            "SELECT COUNT(*) as count FROM anime a $whereClause"
        } else {
            "SELECT COUNT(*) as count FROM anime a"
        }
        return SqlQuery(sql, args)
    }

    fun buildSelectQuery(
        query: String? = null,
        genre: String? = null,
        language: String? = null,
        watched: String? = null,
        meh: String? = null,
        sort: String? = null,
        limit: Int = 50,
        offset: Int = 0,
        blueTags: List<String> = emptyList(),
        greenTags: List<String> = emptyList(),
        orangeTags: List<String> = emptyList()
    ): SqlQuery {
        val (whereClause, whereArgs) = buildWhereClause(query, genre, language, watched, meh, greenTags, orangeTags)

        val cleanPrefer = blueTags.map { it.trim() }.filter { it.isNotEmpty() }
        var preferSelect = ""
        var preferOrderPrefix = ""
        val selectArgs = mutableListOf<String>()

        if (cleanPrefer.isNotEmpty()) {
            val placeholders = cleanPrefer.joinToString(", ") { "?" }
            preferSelect = ", COALESCE((SELECT COUNT(*) FROM anime_tags at JOIN tags t ON at.tag_id = t.id WHERE at.anime_id = a.id AND t.name IN ($placeholders)), 0) AS prefer_score"
            preferOrderPrefix = "prefer_score DESC, "
            selectArgs.addAll(cleanPrefer)
        }

        selectArgs.addAll(whereArgs)

        val orderBy = when (sort?.trim()?.lowercase(Locale.ROOT)) {
            "score" -> "ORDER BY ${preferOrderPrefix}a.anilist_score DESC, a.title ASC"
            "episodes" -> "ORDER BY ${preferOrderPrefix}a.episode_count DESC, a.title ASC"
            "year" -> "ORDER BY ${preferOrderPrefix}a.launch_year DESC, a.title ASC"
            else -> "ORDER BY ${preferOrderPrefix}a.title ASC"
        }

        val limitClause = if (limit > 0) {
            "LIMIT $limit OFFSET $offset"
        } else {
            ""
        }

        val sql = """
            SELECT
                a.id, a.title, a.slug, a.description, a.poster_tall, a.poster_wide,
                a.episode_count, a.season_count, a.launch_year, a.is_subbed, a.is_dubbed,
                a.has_gersub, a.has_gerdub,
                a.cr_updated_at, a.anilist_id, a.anilist_title_romaji, a.anilist_title_english,
                a.anilist_description, a.anilist_score, a.anilist_status, a.anilist_cover_image,
                a.anilist_banner_image, a.anilist_site_url, a.anilist_synced_at,
                a.watched, a.watched_at, a.is_meh, a.meh_at, a.notes
                $preferSelect
            FROM anime a
            $whereClause
            $orderBy
            $limitClause
        """.trimIndent().trim()

        return SqlQuery(sql, selectArgs)
    }

    fun queryAnime(
        query: String? = null,
        genre: String? = null,
        language: String? = null,
        watched: String? = null,
        meh: String? = null,
        sort: String? = null,
        limit: Int = 50,
        offset: Int = 0,
        blueTags: List<String> = emptyList(),
        greenTags: List<String> = emptyList(),
        orangeTags: List<String> = emptyList()
    ): JSONObject {
        val countQuery = buildCountQuery(query, genre, language, watched, meh, greenTags, orangeTags)
        var total = 0
        delegate.rawQuery(countQuery.sql, countQuery.args.toTypedArray()).use { cursor ->
            if (cursor.moveToNext()) {
                total = cursor.getInt("count")
            }
        }

        val selectQuery = buildSelectQuery(
            query, genre, language, watched, meh, sort, limit, offset, blueTags, greenTags, orangeTags
        )

        val itemsArray = JSONArray()
        delegate.rawQuery(selectQuery.sql, selectQuery.args.toTypedArray()).use { cursor ->
            while (cursor.moveToNext()) {
                val animeObj = cursorToAnimeJson(cursor)
                val animeId = animeObj.getString("id")
                animeObj.put("genres", getAnimeGenres(animeId))
                animeObj.put("tags", getAnimeTags(animeId))
                itemsArray.put(animeObj)
            }
        }

        val result = JSONObject()
        result.put("items", itemsArray)
        result.put("total", total)
        result.put("limit", limit)
        result.put("offset", offset)
        return result
    }

    fun getAnimeById(id: String): JSONObject? {
        val sql = """
            SELECT
                id, title, slug, description, poster_tall, poster_wide,
                episode_count, season_count, launch_year, is_subbed, is_dubbed,
                has_gersub, has_gerdub,
                cr_updated_at, anilist_id, anilist_title_romaji, anilist_title_english,
                anilist_description, anilist_score, anilist_status, anilist_cover_image,
                anilist_banner_image, anilist_site_url, anilist_synced_at,
                watched, watched_at, is_meh, meh_at, notes
            FROM anime
            WHERE id = ?;
        """.trimIndent()

        delegate.rawQuery(sql, arrayOf(id)).use { cursor ->
            if (cursor.moveToNext()) {
                val animeObj = cursorToAnimeJson(cursor)
                animeObj.put("genres", getAnimeGenres(id))
                animeObj.put("tags", getAnimeTags(id))
                return animeObj
            }
        }
        return null
    }

    fun setWatched(id: String, watched: Boolean): Boolean {
        val sql = if (watched) {
            "UPDATE anime SET watched = 1, watched_at = CURRENT_TIMESTAMP WHERE id = ?"
        } else {
            "UPDATE anime SET watched = 0, watched_at = NULL WHERE id = ?"
        }
        delegate.execSQL(sql, arrayOf(id))
        return checkRowsAffected()
    }

    fun setMeh(id: String, isMeh: Boolean): Boolean {
        val sql = if (isMeh) {
            "UPDATE anime SET is_meh = 1, meh_at = CURRENT_TIMESTAMP WHERE id = ?"
        } else {
            "UPDATE anime SET is_meh = 0, meh_at = NULL WHERE id = ?"
        }
        delegate.execSQL(sql, arrayOf(id))
        return checkRowsAffected()
    }

    private fun checkRowsAffected(): Boolean {
        delegate.rawQuery("SELECT changes() as count", null).use { cursor ->
            if (cursor.moveToNext()) {
                return cursor.getInt("count") > 0
            }
        }
        return false
    }

    fun getGenres(): JSONArray {
        val sql = """
            SELECT g.name, COUNT(ag.anime_id) as count
            FROM genres g
            LEFT JOIN anime_genres ag ON ag.genre_id = g.id
            GROUP BY g.id, g.name
            HAVING count > 0
            ORDER BY g.name ASC;
        """.trimIndent()

        val arr = JSONArray()
        delegate.rawQuery(sql, null).use { cursor ->
            while (cursor.moveToNext()) {
                val obj = JSONObject()
                obj.put("name", cursor.getString("name") ?: "")
                obj.put("count", cursor.getInt("count"))
                arr.put(obj)
            }
        }
        return arr
    }

    fun getTags(): JSONArray {
        val sql = """
            SELECT t.name, COUNT(at.anime_id) as count
            FROM tags t
            JOIN anime_tags at ON at.tag_id = t.id
            GROUP BY t.id, t.name
            HAVING count > 0
            ORDER BY count DESC, t.name ASC;
        """.trimIndent()

        val arr = JSONArray()
        delegate.rawQuery(sql, null).use { cursor ->
            while (cursor.moveToNext()) {
                val obj = JSONObject()
                obj.put("name", cursor.getString("name") ?: "")
                obj.put("count", cursor.getInt("count"))
                arr.put(obj)
            }
        }
        return arr
    }

    fun getStats(): JSONObject {
        fun queryCount(sql: String): Int {
            delegate.rawQuery(sql, null).use { cursor ->
                if (cursor.moveToNext()) {
                    return cursor.getInt("count")
                }
            }
            return 0
        }

        val totalAnime = queryCount("SELECT COUNT(*) as count FROM anime")
        val watchedAnime = queryCount("SELECT COUNT(*) as count FROM anime WHERE watched = 1")
        val mehAnime = queryCount("SELECT COUNT(*) as count FROM anime WHERE is_meh = 1")
        val enrichedAnime = queryCount("SELECT COUNT(*) as count FROM anime WHERE anilist_id IS NOT NULL")
        val genresCount = queryCount("SELECT COUNT(DISTINCT g.id) as count FROM genres g JOIN anime_genres ag ON ag.genre_id = g.id")
        val tagsCount = queryCount("SELECT COUNT(DISTINCT t.id) as count FROM tags t JOIN anime_tags at ON at.tag_id = t.id")

        val stats = JSONObject()
        stats.put("total_anime", totalAnime)
        stats.put("watched_anime", watchedAnime)
        stats.put("meh_anime", mehAnime)
        stats.put("genres_count", genresCount)
        stats.put("tags_count", tagsCount)
        stats.put("anilist_enriched", enrichedAnime)
        return stats
    }

    fun upsertCrunchyrollSeries(items: List<CRSeriesItem>) {
        if (items.isEmpty()) return

        delegate.transaction {
            val upsertSql = """
                INSERT INTO anime (
                    id, title, slug, description, poster_tall, poster_wide,
                    episode_count, season_count, launch_year, is_subbed, is_dubbed,
                    has_gersub, has_gerdub, cr_updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT(id) DO UPDATE SET
                    title = excluded.title,
                    slug = excluded.slug,
                    description = excluded.description,
                    poster_tall = excluded.poster_tall,
                    poster_wide = excluded.poster_wide,
                    episode_count = excluded.episode_count,
                    season_count = excluded.season_count,
                    launch_year = excluded.launch_year,
                    is_subbed = excluded.is_subbed,
                    is_dubbed = excluded.is_dubbed,
                    has_gersub = excluded.has_gersub,
                    has_gerdub = excluded.has_gerdub,
                    cr_updated_at = CURRENT_TIMESTAMP;
            """.trimIndent()

            for (item in items) {
                delegate.execSQL(
                    upsertSql,
                    arrayOf(
                        item.id,
                        item.title,
                        item.slug,
                        item.description,
                        item.posterTall,
                        item.posterWide,
                        item.episodeCount,
                        item.seasonCount,
                        item.launchYear,
                        if (item.isSubbed) 1 else 0,
                        if (item.isDubbed) 1 else 0,
                        if (item.hasGerSub) 1 else 0,
                        if (item.hasGerDub) 1 else 0
                    )
                )

                // Manage genres
                for (g in item.genres) {
                    val genreName = g.trim()
                    if (genreName.isEmpty()) continue
                    delegate.execSQL("INSERT OR IGNORE INTO genres (name) VALUES (?)", arrayOf(genreName))
                    delegate.execSQL(
                        """
                        INSERT OR IGNORE INTO anime_genres (anime_id, genre_id)
                        SELECT ?, id FROM genres WHERE name = ?
                        """.trimIndent(),
                        arrayOf(item.id, genreName)
                    )
                }
            }
        }
    }

    fun updateAniListMetadata(id: String, aniData: AniListMediaData) {
        delegate.transaction {
            val updateSql = """
                UPDATE anime SET
                    anilist_id = ?,
                    anilist_title_romaji = ?,
                    anilist_title_english = ?,
                    anilist_description = ?,
                    anilist_score = ?,
                    anilist_status = ?,
                    anilist_cover_image = ?,
                    anilist_banner_image = ?,
                    anilist_site_url = ?,
                    anilist_synced_at = CURRENT_TIMESTAMP
                WHERE id = ?;
            """.trimIndent()

            delegate.execSQL(
                updateSql,
                arrayOf(
                    aniData.aniListId,
                    aniData.titleRomaji,
                    aniData.titleEnglish,
                    aniData.description,
                    aniData.score,
                    aniData.status,
                    aniData.coverImage,
                    aniData.bannerImage,
                    aniData.siteUrl,
                    id
                )
            )

            // Manage tags
            for (tag in aniData.tags) {
                val tagName = tag.name.trim()
                if (tagName.isEmpty()) continue
                delegate.execSQL("INSERT OR IGNORE INTO tags (name) VALUES (?)", arrayOf(tagName))
                delegate.execSQL(
                    """
                    INSERT INTO anime_tags (anime_id, tag_id, rank)
                    SELECT ?, id, ? FROM tags WHERE name = ?
                    ON CONFLICT(anime_id, tag_id) DO UPDATE SET rank = excluded.rank
                    """.trimIndent(),
                    arrayOf(id, tag.rank, tagName)
                )
            }

            // Manage optional AniList genres
            for (g in aniData.genres) {
                val genreName = g.trim()
                if (genreName.isEmpty()) continue
                delegate.execSQL("INSERT OR IGNORE INTO genres (name) VALUES (?)", arrayOf(genreName))
                delegate.execSQL(
                    """
                    INSERT OR IGNORE INTO anime_genres (anime_id, genre_id)
                    SELECT ?, id FROM genres WHERE name = ?
                    """.trimIndent(),
                    arrayOf(id, genreName)
                )
            }
        }
    }

    fun getSyncState(key: String): SyncState? {
        val sql = "SELECT key, last_synced_at, total_items, synced_items, status, last_error FROM sync_state WHERE key = ?"
        delegate.rawQuery(sql, arrayOf(key)).use { cursor ->
            if (cursor.moveToNext()) {
                return SyncState(
                    key = cursor.getString("key") ?: key,
                    lastSyncedAt = cursor.getString("last_synced_at"),
                    totalItems = cursor.getInt("total_items"),
                    syncedItems = cursor.getInt("synced_items"),
                    status = cursor.getString("status") ?: "idle",
                    lastError = cursor.getString("last_error") ?: ""
                )
            }
        }
        return null
    }

    fun updateSyncState(syncState: SyncState) {
        val sql = """
            INSERT INTO sync_state (key, last_synced_at, total_items, synced_items, status, last_error)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(key) DO UPDATE SET
                last_synced_at = excluded.last_synced_at,
                total_items = excluded.total_items,
                synced_items = excluded.synced_items,
                status = excluded.status,
                last_error = excluded.last_error;
        """.trimIndent()

        delegate.execSQL(
            sql,
            arrayOf(
                syncState.key,
                syncState.lastSyncedAt,
                syncState.totalItems,
                syncState.syncedItems,
                syncState.status,
                syncState.lastError
            )
        )
    }

    private fun getAnimeGenres(animeId: String): JSONArray {
        val sql = """
            SELECT g.name
            FROM genres g
            JOIN anime_genres ag ON ag.genre_id = g.id
            WHERE ag.anime_id = ?
            ORDER BY g.name ASC;
        """.trimIndent()

        val arr = JSONArray()
        delegate.rawQuery(sql, arrayOf(animeId)).use { cursor ->
            while (cursor.moveToNext()) {
                cursor.getString("name")?.let { arr.put(it) }
            }
        }
        return arr
    }

    private fun getAnimeTags(animeId: String): JSONArray {
        val sql = """
            SELECT t.name, at.rank
            FROM tags t
            JOIN anime_tags at ON at.tag_id = t.id
            WHERE at.anime_id = ?
            ORDER BY at.rank DESC, t.name ASC;
        """.trimIndent()

        val arr = JSONArray()
        delegate.rawQuery(sql, arrayOf(animeId)).use { cursor ->
            while (cursor.moveToNext()) {
                val tagObj = JSONObject()
                tagObj.put("name", cursor.getString("name") ?: "")
                tagObj.put("rank", cursor.getInt("rank"))
                arr.put(tagObj)
            }
        }
        return arr
    }

    private fun cursorToAnimeJson(cursor: CursorDelegate): JSONObject {
        val obj = JSONObject()
        obj.put("id", cursor.getString("id") ?: "")
        obj.put("title", cursor.getString("title") ?: "")
        obj.put("slug", cursor.getString("slug") ?: "")
        obj.put("description", cursor.getString("description") ?: "")
        obj.put("poster_tall", cursor.getString("poster_tall") ?: "")
        obj.put("poster_wide", cursor.getString("poster_wide") ?: "")
        obj.put("episode_count", cursor.getInt("episode_count"))
        obj.put("season_count", cursor.getInt("season_count"))
        obj.put("launch_year", cursor.getInt("launch_year"))
        obj.put("is_subbed", cursor.getBoolean("is_subbed"))
        obj.put("is_dubbed", cursor.getBoolean("is_dubbed"))
        obj.put("has_gersub", cursor.getBoolean("has_gersub"))
        obj.put("has_gerdub", cursor.getBoolean("has_gerdub"))
        obj.put("cr_updated_at", cursor.getString("cr_updated_at") ?: "")

        if (!cursor.isNull("anilist_id")) {
            obj.put("anilist_id", cursor.getInt("anilist_id"))
        } else {
            obj.put("anilist_id", JSONObject.NULL)
        }

        obj.put("anilist_title_romaji", cursor.getString("anilist_title_romaji") ?: "")
        obj.put("anilist_title_english", cursor.getString("anilist_title_english") ?: "")
        obj.put("anilist_description", cursor.getString("anilist_description") ?: "")
        obj.put("anilist_score", cursor.getInt("anilist_score"))
        obj.put("anilist_status", cursor.getString("anilist_status") ?: "")
        obj.put("anilist_cover_image", cursor.getString("anilist_cover_image") ?: "")
        obj.put("anilist_banner_image", cursor.getString("anilist_banner_image") ?: "")
        obj.put("anilist_site_url", cursor.getString("anilist_site_url") ?: "")

        if (!cursor.isNull("anilist_synced_at")) {
            obj.put("anilist_synced_at", cursor.getString("anilist_synced_at"))
        } else {
            obj.put("anilist_synced_at", JSONObject.NULL)
        }

        obj.put("watched", cursor.getBoolean("watched"))
        if (!cursor.isNull("watched_at")) {
            obj.put("watched_at", cursor.getString("watched_at"))
        } else {
            obj.put("watched_at", JSONObject.NULL)
        }

        obj.put("is_meh", cursor.getBoolean("is_meh"))
        if (!cursor.isNull("meh_at")) {
            obj.put("meh_at", cursor.getString("meh_at"))
        } else {
            obj.put("meh_at", JSONObject.NULL)
        }

        obj.put("notes", cursor.getString("notes") ?: "")
        return obj
    }
}
