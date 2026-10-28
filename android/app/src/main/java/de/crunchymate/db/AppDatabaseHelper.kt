package de.crunchymate.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class AppDatabaseHelper(
    context: Context,
    dbName: String = DATABASE_NAME
) : SQLiteOpenHelper(context.applicationContext, dbName, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "crunchymate.db"
        const val DATABASE_VERSION = 1

        @Volatile
        private var instance: AppDatabaseHelper? = null

        fun getInstance(context: Context): AppDatabaseHelper {
            return instance ?: synchronized(this) {
                instance ?: AppDatabaseHelper(context).also { instance = it }
            }
        }
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS anime (
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                slug TEXT NOT NULL DEFAULT '',
                description TEXT NOT NULL DEFAULT '',
                poster_tall TEXT NOT NULL DEFAULT '',
                poster_wide TEXT NOT NULL DEFAULT '',
                episode_count INTEGER NOT NULL DEFAULT 0,
                season_count INTEGER NOT NULL DEFAULT 0,
                launch_year INTEGER NOT NULL DEFAULT 0,
                is_subbed BOOLEAN NOT NULL DEFAULT 0,
                is_dubbed BOOLEAN NOT NULL DEFAULT 0,
                has_gersub BOOLEAN NOT NULL DEFAULT 0,
                has_gerdub BOOLEAN NOT NULL DEFAULT 0,
                cr_updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                anilist_id INTEGER,
                anilist_title_romaji TEXT NOT NULL DEFAULT '',
                anilist_title_english TEXT NOT NULL DEFAULT '',
                anilist_description TEXT NOT NULL DEFAULT '',
                anilist_score INTEGER NOT NULL DEFAULT 0,
                anilist_status TEXT NOT NULL DEFAULT '',
                anilist_cover_image TEXT NOT NULL DEFAULT '',
                anilist_banner_image TEXT NOT NULL DEFAULT '',
                anilist_site_url TEXT NOT NULL DEFAULT '',
                anilist_synced_at DATETIME,
                watched BOOLEAN NOT NULL DEFAULT 0,
                watched_at DATETIME,
                is_meh BOOLEAN NOT NULL DEFAULT 0,
                meh_at DATETIME,
                notes TEXT NOT NULL DEFAULT ''
            );
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS genres (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT UNIQUE NOT NULL
            );
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS anime_genres (
                anime_id TEXT NOT NULL REFERENCES anime(id) ON DELETE CASCADE,
                genre_id INTEGER NOT NULL REFERENCES genres(id) ON DELETE CASCADE,
                PRIMARY KEY (anime_id, genre_id)
            );
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS tags (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT UNIQUE NOT NULL
            );
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS anime_tags (
                anime_id TEXT NOT NULL REFERENCES anime(id) ON DELETE CASCADE,
                tag_id INTEGER NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
                rank INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY (anime_id, tag_id)
            );
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS sync_state (
                key TEXT PRIMARY KEY,
                last_synced_at DATETIME,
                total_items INTEGER NOT NULL DEFAULT 0,
                synced_items INTEGER NOT NULL DEFAULT 0,
                status TEXT NOT NULL DEFAULT 'idle',
                last_error TEXT NOT NULL DEFAULT ''
            );
        """.trimIndent())

        db.execSQL("CREATE INDEX IF NOT EXISTS idx_anime_title ON anime(title);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_anime_watched ON anime(watched);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_anime_is_meh ON anime(is_meh);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_tags_name ON tags(name);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_anime_tags_tag ON anime_tags(tag_id);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_anime_gersub ON anime(has_gersub);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_anime_gerdub ON anime(has_gerdub);")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Reserved for future migrations
    }
}
