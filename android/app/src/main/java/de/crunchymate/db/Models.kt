package de.crunchymate.db

data class AniListTag(
    val name: String,
    val rank: Int = 0
)

data class GenreCount(
    val name: String,
    val count: Int
)

data class TagCount(
    val name: String,
    val count: Int
)

data class CRSeriesItem(
    val id: String,
    val title: String,
    val slug: String = "",
    val description: String = "",
    val posterTall: String = "",
    val posterWide: String = "",
    val episodeCount: Int = 0,
    val seasonCount: Int = 0,
    val launchYear: Int = 0,
    val isSubbed: Boolean = false,
    val isDubbed: Boolean = false,
    val hasGerSub: Boolean = false,
    val hasGerDub: Boolean = false,
    val genres: List<String> = emptyList()
)

data class AniListMediaData(
    val aniListId: Int,
    val titleRomaji: String = "",
    val titleEnglish: String = "",
    val description: String = "",
    val score: Int = 0,
    val status: String = "",
    val coverImage: String = "",
    val bannerImage: String = "",
    val siteUrl: String = "",
    val tags: List<AniListTag> = emptyList(),
    val genres: List<String> = emptyList()
)

data class SyncState(
    val key: String,
    val lastSyncedAt: String? = null,
    val totalItems: Int = 0,
    val syncedItems: Int = 0,
    val status: String = "idle",
    val lastError: String = ""
)

data class SqlQuery(
    val sql: String,
    val args: List<String>
)

interface CursorDelegate : AutoCloseable {
    fun moveToNext(): Boolean
    fun getString(columnName: String): String?
    fun getInt(columnName: String): Int
    fun getLong(columnName: String): Long
    fun getDouble(columnName: String): Double
    fun getBoolean(columnName: String): Boolean
    fun isNull(columnName: String): Boolean
    val count: Int
}

interface DatabaseDelegate {
    fun rawQuery(sql: String, selectionArgs: Array<String>?): CursorDelegate
    fun execSQL(sql: String, bindArgs: Array<Any?>? = null)
    fun <T> transaction(block: () -> T): T
}
