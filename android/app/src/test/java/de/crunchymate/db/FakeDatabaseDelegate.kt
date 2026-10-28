package de.crunchymate.db

data class FakeRow(
    val columns: Map<String, Any?>
)

class FakeCursor(
    private val rows: List<FakeRow>
) : CursorDelegate {
    private var index = -1

    override fun moveToNext(): Boolean {
        if (index + 1 < rows.size) {
            index++
            return true
        }
        return false
    }

    override fun getString(columnName: String): String? {
        val v = rows[index].columns[columnName]
        return v?.toString()
    }

    override fun getInt(columnName: String): Int {
        val v = rows[index].columns[columnName]
            ?: if (columnName.equals("count", ignoreCase = true)) rows[index].columns["COUNT(*)"] ?: rows[index].columns["changes()"]
               else if (columnName.equals("COUNT(*)", ignoreCase = true)) rows[index].columns["count"]
               else null
            ?: return 0
        return when (v) {
            is Number -> v.toInt()
            is Boolean -> if (v) 1 else 0
            else -> v.toString().toIntOrNull() ?: 0
        }
    }

    override fun getLong(columnName: String): Long {
        val v = rows[index].columns[columnName] ?: return 0L
        return when (v) {
            is Number -> v.toLong()
            is Boolean -> if (v) 1L else 0L
            else -> v.toString().toLongOrNull() ?: 0L
        }
    }

    override fun getDouble(columnName: String): Double {
        val v = rows[index].columns[columnName] ?: return 0.0
        return when (v) {
            is Number -> v.toDouble()
            else -> v.toString().toDoubleOrNull() ?: 0.0
        }
    }

    override fun getBoolean(columnName: String): Boolean {
        val v = rows[index].columns[columnName] ?: return false
        return when (v) {
            is Boolean -> v
            is Number -> v.toInt() == 1
            else -> v.toString() == "1" || v.toString().equals("true", ignoreCase = true)
        }
    }

    override fun isNull(columnName: String): Boolean {
        return rows[index].columns[columnName] == null
    }

    override val count: Int
        get() = rows.size

    override fun close() {}
}

class FakeDatabaseDelegate : DatabaseDelegate {
    var lastExecutedSql: String? = null
    var lastExecutedArgs: List<Any?>? = null
    val executedStatements: MutableList<Pair<String, List<Any?>?>> = mutableListOf()

    var countResult: Int = 0
    var changesCount: Int? = null
    val existingAnimeIds: MutableSet<String> = mutableSetOf()
    private var lastUpdatedId: String? = null

    var animeRows: List<FakeRow> = emptyList()
    var singleAnimeRow: FakeRow? = null
    var genresByAnimeId: MutableMap<String, List<String>> = mutableMapOf()
    var tagsByAnimeId: MutableMap<String, List<AniListTag>> = mutableMapOf()
    var genreCounts: List<GenreCount> = emptyList()
    var tagCounts: List<TagCount> = emptyList()
    var statsMap: Map<String, Int> = emptyMap()
    var syncStates: MutableMap<String, SyncState> = mutableMapOf()

    override fun rawQuery(sql: String, selectionArgs: Array<String>?): CursorDelegate {
        val trimmed = sql.trim()

        if (trimmed.contains("COUNT(DISTINCT g.id)")) {
            val countVal = statsMap["genres_count"] ?: 0
            return FakeCursor(listOf(FakeRow(mapOf("count" to countVal))))
        }

        if (trimmed.contains("COUNT(DISTINCT t.id)")) {
            val countVal = statsMap["tags_count"] ?: 0
            return FakeCursor(listOf(FakeRow(mapOf("count" to countVal))))
        }

        if (trimmed.startsWith("SELECT changes()", ignoreCase = true)) {
            val countVal = when {
                changesCount != null -> changesCount!!
                lastUpdatedId != null && existingAnimeIds.contains(lastUpdatedId) -> 1
                lastUpdatedId != null && (animeRows.any { it.columns["id"] == lastUpdatedId } || singleAnimeRow?.columns?.get("id") == lastUpdatedId) -> 1
                lastUpdatedId != null -> 0
                else -> 1
            }
            return FakeCursor(listOf(FakeRow(mapOf("count" to countVal, "changes()" to countVal))))
        }

        if (trimmed.startsWith("SELECT COUNT(*)", ignoreCase = true)) {
            val countVal = if (statsMap.isNotEmpty()) {
                when {
                    trimmed.contains("WHERE watched = 1") -> statsMap["watched_anime"] ?: 0
                    trimmed.contains("WHERE is_meh = 1") -> statsMap["meh_anime"] ?: 0
                    trimmed.contains("WHERE anilist_id IS NOT NULL") -> statsMap["anilist_enriched"] ?: 0
                    trimmed.contains("FROM anime a WHERE") || trimmed.contains("FROM anime WHERE") -> countResult
                    trimmed.contains("FROM anime") -> statsMap["total_anime"] ?: countResult
                    else -> countResult
                }
            } else {
                countResult
            }
            return FakeCursor(listOf(FakeRow(mapOf("count" to countVal, "COUNT(*)" to countVal))))
        }

        if (trimmed.contains("anime_genres") && trimmed.contains("ag.anime_id = ?")) {
            val animeId = selectionArgs?.firstOrNull() ?: ""
            val list = genresByAnimeId[animeId] ?: emptyList()
            val rows = list.map { FakeRow(mapOf("name" to it)) }
            return FakeCursor(rows)
        }

        if (trimmed.contains("anime_tags") && trimmed.contains("at.anime_id = ?")) {
            val animeId = selectionArgs?.firstOrNull() ?: ""
            val list = tagsByAnimeId[animeId] ?: emptyList()
            val rows = list.map { FakeRow(mapOf("name" to it.name, "rank" to it.rank)) }
            return FakeCursor(rows)
        }

        if (trimmed.contains("FROM genres g")) {
            val rows = genreCounts.map { FakeRow(mapOf("name" to it.name, "count" to it.count)) }
            return FakeCursor(rows)
        }

        if (trimmed.contains("FROM tags t")) {
            val rows = tagCounts.map { FakeRow(mapOf("name" to it.name, "count" to it.count)) }
            return FakeCursor(rows)
        }

        if (trimmed.contains("FROM sync_state") && trimmed.contains("key = ?")) {
            val key = selectionArgs?.firstOrNull() ?: ""
            val st = syncStates[key]
            val rows = if (st != null) {
                listOf(FakeRow(mapOf(
                    "key" to st.key,
                    "last_synced_at" to st.lastSyncedAt,
                    "total_items" to st.totalItems,
                    "synced_items" to st.syncedItems,
                    "status" to st.status,
                    "last_error" to st.lastError
                )))
            } else emptyList()
            return FakeCursor(rows)
        }

        if (trimmed.contains("FROM anime") && trimmed.contains("WHERE id = ?")) {
            val rows = if (singleAnimeRow != null) listOf(singleAnimeRow!!) else emptyList()
            return FakeCursor(rows)
        }

        if (trimmed.contains("FROM anime")) {
            return FakeCursor(animeRows)
        }

        return FakeCursor(emptyList())
    }

    override fun execSQL(sql: String, bindArgs: Array<Any?>?) {
        val trimmed = sql.trim()
        lastExecutedSql = trimmed
        lastExecutedArgs = bindArgs?.toList()
        executedStatements.add(Pair(trimmed, bindArgs?.toList()))

        if (trimmed.startsWith("UPDATE anime", ignoreCase = true)) {
            lastUpdatedId = bindArgs?.firstOrNull()?.toString()
        }

        if (trimmed.startsWith("INSERT INTO sync_state", ignoreCase = true)) {
            val key = bindArgs?.getOrNull(0)?.toString() ?: ""
            val lastSyncedAt = bindArgs?.getOrNull(1)?.toString()
            val totalItems = when (val v = bindArgs?.getOrNull(2)) {
                is Number -> v.toInt()
                else -> v?.toString()?.toIntOrNull() ?: 0
            }
            val syncedItems = when (val v = bindArgs?.getOrNull(3)) {
                is Number -> v.toInt()
                else -> v?.toString()?.toIntOrNull() ?: 0
            }
            val status = bindArgs?.getOrNull(4)?.toString() ?: "idle"
            val lastError = bindArgs?.getOrNull(5)?.toString() ?: ""
            syncStates[key] = SyncState(key, lastSyncedAt, totalItems, syncedItems, status, lastError)
        }
    }

    override fun <T> transaction(block: () -> T): T {
        return block()
    }
}
