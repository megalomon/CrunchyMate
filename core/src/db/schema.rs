use rusqlite::Connection;

use crate::error::CoreResult;

pub const SCHEMA_VERSION: i32 = 2;

const MIGRATIONS: [&str; SCHEMA_VERSION as usize] = [MIGRATION_V1, MIGRATION_V2];

const MIGRATION_V1: &str = "
    CREATE TABLE anime (
        id TEXT PRIMARY KEY,
        title TEXT NOT NULL,
        description TEXT NOT NULL DEFAULT '',
        poster_tall TEXT NOT NULL DEFAULT '',
        poster_wide TEXT NOT NULL DEFAULT '',
        episode_count INTEGER NOT NULL DEFAULT 0,
        launch_year INTEGER NOT NULL DEFAULT 0,
        is_subbed INTEGER NOT NULL DEFAULT 0,
        is_dubbed INTEGER NOT NULL DEFAULT 0,
        has_target_sub INTEGER NOT NULL DEFAULT 0,
        has_target_dub INTEGER NOT NULL DEFAULT 0,
        cr_updated_at INTEGER NOT NULL DEFAULT 0,
        anilist_id INTEGER,
        anilist_title_romaji TEXT NOT NULL DEFAULT '',
        anilist_title_english TEXT NOT NULL DEFAULT '',
        anilist_description TEXT NOT NULL DEFAULT '',
        anilist_score INTEGER NOT NULL DEFAULT 0,
        anilist_status TEXT NOT NULL DEFAULT '',
        anilist_banner_image TEXT NOT NULL DEFAULT '',
        anilist_site_url TEXT NOT NULL DEFAULT '',
        anilist_synced_at INTEGER,
        watched INTEGER NOT NULL DEFAULT 0,
        watched_at INTEGER,
        is_meh INTEGER NOT NULL DEFAULT 0,
        meh_at INTEGER
    );
    CREATE TABLE genres (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        name TEXT UNIQUE NOT NULL
    );
    CREATE TABLE anime_genres (
        anime_id TEXT NOT NULL REFERENCES anime(id) ON DELETE CASCADE,
        genre_id INTEGER NOT NULL REFERENCES genres(id) ON DELETE CASCADE,
        PRIMARY KEY (anime_id, genre_id)
    );
    CREATE TABLE tags (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        name TEXT UNIQUE NOT NULL
    );
    CREATE TABLE anime_tags (
        anime_id TEXT NOT NULL REFERENCES anime(id) ON DELETE CASCADE,
        tag_id INTEGER NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
        rank INTEGER NOT NULL DEFAULT 0,
        PRIMARY KEY (anime_id, tag_id)
    );
    CREATE TABLE sync_state (
        kind TEXT PRIMARY KEY,
        state TEXT NOT NULL DEFAULT 'idle',
        done INTEGER NOT NULL DEFAULT 0,
        total INTEGER NOT NULL DEFAULT 0,
        last_finished_at INTEGER,
        last_error TEXT,
        catalog_country TEXT
    );
    CREATE TABLE settings (
        key TEXT PRIMARY KEY,
        value TEXT NOT NULL
    );
    CREATE INDEX idx_anime_title ON anime(title);
    CREATE INDEX idx_anime_watched ON anime(watched);
    CREATE INDEX idx_anime_is_meh ON anime(is_meh);
    CREATE INDEX idx_anime_target_sub ON anime(has_target_sub);
    CREATE INDEX idx_anime_target_dub ON anime(has_target_dub);
    CREATE INDEX idx_tags_name ON tags(name);
    CREATE INDEX idx_anime_tags_tag ON anime_tags(tag_id);
";

// Nur neue Tabellen: vorhandene Anime bleiben, Bilder lädt der nächste Sync.
const MIGRATION_V2: &str = "
    CREATE TABLE images (
        hash TEXT PRIMARY KEY,
        ext TEXT NOT NULL,
        bytes INTEGER NOT NULL
    );
    CREATE TABLE image_refs (
        anime_id TEXT NOT NULL REFERENCES anime(id) ON DELETE CASCADE,
        slot TEXT NOT NULL,
        source_url TEXT NOT NULL,
        hash TEXT NOT NULL REFERENCES images(hash),
        PRIMARY KEY (anime_id, slot)
    );
    CREATE INDEX idx_image_refs_hash ON image_refs(hash);
";

pub fn migrate(conn: &Connection) -> CoreResult<()> {
    let current: i32 = conn.query_row("PRAGMA user_version", [], |row| row.get(0))?;
    for (index, sql) in MIGRATIONS.iter().enumerate() {
        let target = index as i32 + 1;
        if current >= target {
            continue;
        }
        // Schritt und Versionsnummer gemeinsam, damit ein Abbruch nichts halb anlegt.
        let step = format!("BEGIN;\n{sql}\nPRAGMA user_version = {target};\nCOMMIT;");
        if let Err(error) = conn.execute_batch(&step) {
            let _ = conn.execute_batch("ROLLBACK;");
            return Err(error.into());
        }
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::db::Database;
    use crate::error::{CoreError, ErrorCode};
    use rusqlite::Connection;

    fn user_version(conn: &Connection) -> i32 {
        conn.query_row("PRAGMA user_version", [], |row| row.get(0))
            .unwrap()
    }

    #[test]
    fn migrate_sets_user_version() {
        let db = Database::open_in_memory().unwrap();
        let version = db.with_conn(|conn| Ok(user_version(conn))).unwrap();
        assert_eq!(version, SCHEMA_VERSION);
    }

    #[test]
    fn migrate_is_idempotent() {
        let db = Database::open_in_memory().unwrap();
        let version = db
            .with_conn(|conn| {
                migrate(conn)?;
                Ok(user_version(conn))
            })
            .unwrap();
        assert_eq!(version, SCHEMA_VERSION);
    }

    #[test]
    fn schema_contains_expected_tables() {
        let db = Database::open_in_memory().unwrap();
        let tables: Vec<String> = db
            .with_conn(|conn| {
                let mut stmt =
                    conn.prepare("SELECT name FROM sqlite_master WHERE type = 'table'")?;
                let names = stmt
                    .query_map([], |row| row.get(0))?
                    .collect::<Result<Vec<String>, _>>()?;
                Ok(names)
            })
            .unwrap();
        for expected in [
            "anime",
            "genres",
            "anime_genres",
            "tags",
            "anime_tags",
            "sync_state",
            "settings",
            "images",
            "image_refs",
        ] {
            assert!(
                tables.iter().any(|t| t == expected),
                "Tabelle {expected} fehlt"
            );
        }
    }

    #[test]
    fn migration_v1_to_v2_keeps_anime() {
        let conn = Connection::open_in_memory().unwrap();
        conn.execute_batch(&format!("{MIGRATION_V1}\nPRAGMA user_version = 1;"))
            .unwrap();
        conn.execute(
            "INSERT INTO anime (id, title, poster_tall, watched) VALUES ('a', 'Alt', 'url', 1)",
            [],
        )
        .unwrap();

        migrate(&conn).unwrap();

        assert_eq!(user_version(&conn), 2);
        let (title, poster, watched): (String, String, bool) = conn
            .query_row(
                "SELECT title, poster_tall, watched FROM anime WHERE id = 'a'",
                [],
                |r| Ok((r.get(0)?, r.get(1)?, r.get(2)?)),
            )
            .unwrap();
        assert_eq!(
            (title.as_str(), poster.as_str(), watched),
            ("Alt", "url", true)
        );
        for table in ["images", "image_refs"] {
            let rows: i64 = conn
                .query_row(&format!("SELECT COUNT(*) FROM {table}"), [], |r| r.get(0))
                .unwrap();
            assert_eq!(rows, 0, "{table} sollte leer starten");
        }
    }

    #[test]
    fn fold_lowercases_non_ascii() {
        let db = Database::open_in_memory().unwrap();
        let folded: String = db
            .with_conn(|conn| Ok(conn.query_row("SELECT fold('ÜBER Ärger')", [], |r| r.get(0))?))
            .unwrap();
        assert_eq!(folded, "über ärger");
    }

    #[test]
    fn fold_keeps_null() {
        let db = Database::open_in_memory().unwrap();
        let folded: Option<String> = db
            .with_conn(|conn| Ok(conn.query_row("SELECT fold(NULL)", [], |r| r.get(0))?))
            .unwrap();
        assert_eq!(folded, None);
    }

    #[test]
    fn error_code_matches_variant() {
        let error = CoreError::RateLimited {
            retry_after_secs: 5,
        };
        assert_eq!(error.code(), ErrorCode::RateLimited);
    }
}
