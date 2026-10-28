use rusqlite::{params, Connection, OptionalExtension};

use crate::db::USER_SCHEMA;
use crate::error::{CoreError, CoreResult};
use crate::model::{Settings, DEFAULT_CONTENT_LOCALE};

/// Version der Katalog-Datenbank (Cache aus dem Katalog der Streamingquelle und AniList).
pub const SCHEMA_VERSION: i32 = 4;
/// Version der Nutzer-Datenbank (Einstellungen und Markierungen); zählt unabhängig vom Katalog.
pub const USER_SCHEMA_VERSION: i32 = 1;

const MAIN_SCHEMA: &str = "main";

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

// Markierungen hängen bewusst nicht per Fremdschlüssel am Katalog: sie sollen ein Löschen oder
// Neuladen des Katalogs und eine Wiederherstellung vor dem ersten Sync überstehen.
const USER_MIGRATION_V1: &str = "
    CREATE TABLE userdata.settings (
        key TEXT PRIMARY KEY,
        value TEXT NOT NULL
    );
    CREATE TABLE userdata.marks (
        anime_id TEXT PRIMARY KEY,
        watched INTEGER NOT NULL DEFAULT 0,
        watched_at INTEGER,
        is_meh INTEGER NOT NULL DEFAULT 0,
        meh_at INTEGER
    );
";

// Erster Schritt von v3: Nutzerdaten aus dem Katalog in die Nutzer-Datenbank kopieren.
// Vereinigung statt Überschreiben: liegt dort schon eine Markierung (abgebrochene frühere
// Migration, Wiederherstellung), geht keine Seite verloren; mehrfaches Ausführen ändert nichts.
const COPY_USER_DATA_V3: &str = "
    INSERT INTO userdata.marks (anime_id, watched, watched_at, is_meh, meh_at)
    SELECT id, watched, watched_at, is_meh, meh_at FROM main.anime
    WHERE watched = 1 OR is_meh = 1
    ON CONFLICT(anime_id) DO UPDATE SET
        watched = MAX(marks.watched, excluded.watched),
        watched_at = COALESCE(marks.watched_at, excluded.watched_at),
        is_meh = MAX(marks.is_meh, excluded.is_meh),
        meh_at = COALESCE(marks.meh_at, excluded.meh_at);
    INSERT INTO userdata.settings (key, value)
    SELECT key, value FROM main.settings WHERE true
    ON CONFLICT(key) DO NOTHING;
";

// Zweiter Schritt von v3, erst nach dem Commit des ersten: alte Nutzerspalten entfernen und die
// Felder für Sync-Ergebnis, Inhaltssprache und erfolglose AniList-Suchen anlegen.
const MIGRATION_V3: &str = "
    DROP INDEX main.idx_anime_watched;
    DROP INDEX main.idx_anime_is_meh;
    ALTER TABLE main.anime DROP COLUMN watched;
    ALTER TABLE main.anime DROP COLUMN watched_at;
    ALTER TABLE main.anime DROP COLUMN is_meh;
    ALTER TABLE main.anime DROP COLUMN meh_at;
    DROP TABLE main.settings;
    ALTER TABLE main.anime ADD COLUMN anilist_missed_at INTEGER;
    ALTER TABLE main.sync_state ADD COLUMN content_locale TEXT;
    ALTER TABLE main.sync_state ADD COLUMN last_processed INTEGER;
    ALTER TABLE main.sync_state ADD COLUMN last_failed_batches INTEGER;
    ALTER TABLE main.sync_state ADD COLUMN last_failed_images INTEGER;
    ALTER TABLE main.sync_state ADD COLUMN last_skipped_images INTEGER;
";

// Nur neue Spalten und Tabellen: vorhandene Anime bleiben, der nächste Sync füllt die Felder.
// Ohne Angabe gilt ein Eintrag als nicht 18+ und ohne Altersstufe.
const MIGRATION_V4: &str = "
    ALTER TABLE main.anime ADD COLUMN age_rating INTEGER;
    ALTER TABLE main.anime ADD COLUMN is_adult INTEGER NOT NULL DEFAULT 0;
    CREATE TABLE main.anime_descriptors (
        anime_id TEXT NOT NULL REFERENCES anime(id) ON DELETE CASCADE,
        position INTEGER NOT NULL,
        name TEXT NOT NULL,
        PRIMARY KEY (anime_id, position)
    );
";

/// Bringt Nutzer- und Katalog-Datenbank auf den aktuellen Stand. Eine Datei aus einer neueren
/// App-Version wird abgewiesen statt still weiterverwendet.
pub fn migrate(conn: &Connection) -> CoreResult<()> {
    let (catalog_version, user_version) = ensure_supported(conn)?;
    // Zuerst die Nutzer-Datenbank: v3 des Katalogs kopiert in deren Tabellen
    if user_version < 1 {
        run_step(conn, USER_SCHEMA, 1, |tx| {
            tx.execute_batch(USER_MIGRATION_V1)?;
            Ok(())
        })?;
    }
    if catalog_version < 1 {
        run_step(conn, MAIN_SCHEMA, 1, |tx| {
            Ok(tx.execute_batch(MIGRATION_V1)?)
        })?;
    }
    if catalog_version < 2 {
        run_step(conn, MAIN_SCHEMA, 2, |tx| {
            Ok(tx.execute_batch(MIGRATION_V2)?)
        })?;
    }
    if catalog_version < 3 {
        migrate_v3(conn)?;
    }
    if catalog_version < 4 {
        run_step(conn, MAIN_SCHEMA, 4, |tx| {
            Ok(tx.execute_batch(MIGRATION_V4)?)
        })?;
    }
    Ok(())
}

/// Zwei Transaktionen, weil SQLite im WAL-Modus keine dateiübergreifend atomaren Commits kennt:
/// erst ist die Kopie in der Nutzer-Datei sicher, dann werden die Quellspalten entfernt. Bricht
/// der Ablauf dazwischen ab, steht der Katalog noch auf v2 und die Kopie wird wiederholt.
fn migrate_v3(conn: &Connection) -> CoreResult<()> {
    let tx = conn.unchecked_transaction()?;
    tx.execute_batch(COPY_USER_DATA_V3)?;
    tx.commit()?;
    run_step(conn, MAIN_SCHEMA, 3, |tx| {
        let synced_locale = legacy_content_locale(tx)?;
        tx.execute_batch(MIGRATION_V3)?;
        // Bisher wurde die Sprache des letzten Syncs nicht gespeichert; die damals eingestellte
        // ist die beste Annahme, damit nach dem Update kein grundloser Hinweis erscheint.
        tx.execute(
            "UPDATE main.sync_state SET content_locale = ?1
             WHERE kind = 'catalog' AND last_finished_at IS NOT NULL",
            params![synced_locale],
        )?;
        Ok(())
    })
}

fn legacy_content_locale(conn: &Connection) -> CoreResult<String> {
    let raw: Option<String> = conn
        .query_row(
            "SELECT value FROM main.settings WHERE key = 'settings'",
            [],
            |row| row.get(0),
        )
        .optional()?;
    Ok(raw
        .and_then(|json| serde_json::from_str::<Settings>(&json).ok())
        .map(|settings| settings.content_locale)
        .unwrap_or_else(|| DEFAULT_CONTENT_LOCALE.to_string()))
}

fn schema_version(conn: &Connection, schema: &str) -> CoreResult<i32> {
    Ok(
        conn.query_row(&format!("PRAGMA {schema}.user_version"), [], |row| {
            row.get(0)
        })?,
    )
}

/// Liefert die Versionen von Katalog und Nutzer-Datenbank, sofern beide unterstützt sind.
pub fn ensure_supported(conn: &Connection) -> CoreResult<(i32, i32)> {
    Ok((
        supported_version(conn, MAIN_SCHEMA, SCHEMA_VERSION)?,
        supported_version(conn, USER_SCHEMA, USER_SCHEMA_VERSION)?,
    ))
}

fn supported_version(conn: &Connection, schema: &str, supported: i32) -> CoreResult<i32> {
    let found = schema_version(conn, schema)?;
    if found > supported {
        return Err(CoreError::UnsupportedSchema { found, supported });
    }
    Ok(found)
}

/// Schritt und Versionsnummer gemeinsam, damit ein Abbruch nichts halb anlegt.
fn run_step(
    conn: &Connection,
    schema: &str,
    target: i32,
    apply: impl FnOnce(&Connection) -> CoreResult<()>,
) -> CoreResult<()> {
    let tx = conn.unchecked_transaction()?;
    apply(&tx)?;
    tx.execute_batch(&format!("PRAGMA {schema}.user_version = {target};"))?;
    tx.commit()?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::db::{anime, settings, sync_state, Database};
    use crate::error::{CoreError, ErrorCode};
    use crate::images::store::test_dir::TempDir;
    use crate::images::store::ImageStore;
    use crate::model::{
        AnimeFilter, LanguageFilter, MehFilter, SortOrder, SyncKind, TagState, WatchedFilter,
    };
    use crate::query::query_anime;
    use rusqlite::Connection;

    /// Einstellungen in der Form, wie sie die App bis Schema v2 gespeichert hat.
    const V2_SETTINGS_JSON: &str = r#"{"content_locale":"de-DE","search":{"query":"","genre":null,"language":"All","watched":"Unwatched","sort":"Title","tags":[{"name":"Female Protagonist","state":"Prefer"},{"name":"School","state":"Prefer"}]}}"#;

    fn version(conn: &Connection, schema: &str) -> i32 {
        schema_version(conn, schema).unwrap()
    }

    fn paths(dir: &TempDir) -> (String, String) {
        std::fs::create_dir_all(dir.path()).unwrap();
        let catalog = dir.path().join("crunchymate.db");
        let user = dir.path().join("crunchymate-user.db");
        (
            catalog.to_string_lossy().into_owned(),
            user.to_string_lossy().into_owned(),
        )
    }

    /// Baut eine Katalog-Datei im Stand von Schema v2 mit dem Inhalt einer echten Installation:
    /// gesehen mit und ohne Meh, nur Meh, unmarkiert, Einstellungen und abgeschlossener Sync.
    fn write_v2_catalog(path: &str) {
        let conn = Connection::open(path).unwrap();
        conn.pragma_update(None, "journal_mode", "WAL").unwrap();
        conn.execute_batch(&format!(
            "{MIGRATION_V1}\n{MIGRATION_V2}\nPRAGMA user_version = 2;"
        ))
        .unwrap();
        conn.execute_batch(
            "INSERT INTO anime (id, title, poster_tall, has_target_dub, watched, watched_at, is_meh, meh_at)
             VALUES
                ('w1', 'Gesehen Eins', 'https://t/w1', 1, 1, 1793217805, 0, NULL),
                ('w2', 'Gesehen Zwei', '', 0, 1, 1793217513, 0, NULL),
                ('wm', 'Gesehen und Meh', '', 0, 1, 1793220096, 1, 1793220097),
                ('m1', 'Meh Eins', '', 0, 0, NULL, 1, 1793217508),
                ('m2', 'Meh Zwei', '', 0, 0, NULL, 1, 1793220140),
                ('n1', 'Nichts', '', 1, 0, NULL, 0, NULL);
             INSERT INTO tags (name) VALUES ('School');
             INSERT INTO anime_tags (anime_id, tag_id, rank) SELECT 'w1', id, 80 FROM tags;
             INSERT INTO sync_state (kind, state, done, total, last_finished_at, catalog_country)
             VALUES ('catalog', 'completed', 6, 6, 1793210000, 'DE'),
                    ('metadata', 'completed', 1, 1, 1793210500, NULL);",
        )
        .unwrap();
        conn.execute(
            "INSERT INTO settings (key, value) VALUES ('settings', ?1)",
            [V2_SETTINGS_JSON],
        )
        .unwrap();
    }

    type Mark = (String, bool, Option<i64>, bool, Option<i64>);

    fn marks(db: &Database) -> Vec<Mark> {
        db.with_conn(|c| {
            let mut stmt = c.prepare(
                "SELECT anime_id, watched, watched_at, is_meh, meh_at FROM userdata.marks
                 ORDER BY anime_id",
            )?;
            let rows = stmt
                .query_map([], |r| {
                    Ok((r.get(0)?, r.get(1)?, r.get(2)?, r.get(3)?, r.get(4)?))
                })?
                .collect::<Result<Vec<_>, _>>()?;
            Ok(rows)
        })
        .unwrap()
    }

    fn expected_v2_marks() -> Vec<Mark> {
        vec![
            ("m1".into(), false, None, true, Some(1793217508)),
            ("m2".into(), false, None, true, Some(1793220140)),
            ("w1".into(), true, Some(1793217805), false, None),
            ("w2".into(), true, Some(1793217513), false, None),
            ("wm".into(), true, Some(1793220096), true, Some(1793220097)),
        ]
    }

    fn filter(watched: WatchedFilter, meh: MehFilter) -> AnimeFilter {
        AnimeFilter {
            query: String::new(),
            genre: None,
            language: LanguageFilter::All,
            watched,
            meh,
            sort: SortOrder::Title,
            prefer_tags: vec![],
            must_tags: vec![],
            exclude_tags: vec![],
            hide_adult: false,
        }
    }

    fn ids(db: &Database, filter: &AnimeFilter) -> Vec<String> {
        let store = ImageStore::new(std::env::temp_dir().join("crunchymate-schema-unit")).unwrap();
        db.with_conn(|c| query_anime(c, &store, filter, 0, 100))
            .unwrap()
            .items
            .into_iter()
            .map(|i| i.id)
            .collect()
    }

    fn column_names(db: &Database, table: &str) -> Vec<String> {
        db.with_conn(|c| {
            let mut stmt = c.prepare(&format!("SELECT name FROM pragma_table_info('{table}')"))?;
            let names = stmt
                .query_map([], |r| r.get(0))?
                .collect::<Result<Vec<String>, _>>()?;
            Ok(names)
        })
        .unwrap()
    }

    #[test]
    fn migrate_sets_both_versions() {
        let db = Database::open_in_memory().unwrap();
        db.with_conn(|c| {
            assert_eq!(version(c, MAIN_SCHEMA), SCHEMA_VERSION);
            assert_eq!(version(c, USER_SCHEMA), USER_SCHEMA_VERSION);
            Ok(())
        })
        .unwrap();
    }

    #[test]
    fn migrate_is_idempotent() {
        let db = Database::open_in_memory().unwrap();
        db.with_conn(|c| {
            migrate(c)?;
            assert_eq!(version(c, MAIN_SCHEMA), SCHEMA_VERSION);
            assert_eq!(version(c, USER_SCHEMA), USER_SCHEMA_VERSION);
            Ok(())
        })
        .unwrap();
    }

    #[test]
    fn schema_contains_expected_tables() {
        let db = Database::open_in_memory().unwrap();
        let tables = |schema: &str| -> Vec<String> {
            db.with_conn(|conn| {
                let mut stmt = conn.prepare(&format!(
                    "SELECT name FROM {schema}.sqlite_master WHERE type = 'table'"
                ))?;
                let names = stmt
                    .query_map([], |row| row.get(0))?
                    .collect::<Result<Vec<String>, _>>()?;
                Ok(names)
            })
            .unwrap()
        };
        let mut catalog = tables(MAIN_SCHEMA);
        catalog.retain(|t| t != "sqlite_sequence");
        catalog.sort();
        assert_eq!(
            catalog,
            [
                "anime",
                "anime_descriptors",
                "anime_genres",
                "anime_tags",
                "genres",
                "image_refs",
                "images",
                "sync_state",
                "tags"
            ]
        );
        let mut user = tables(USER_SCHEMA);
        user.sort();
        assert_eq!(user, ["marks", "settings"]);
        let anime_columns = column_names(&db, "anime");
        for removed in ["watched", "watched_at", "is_meh", "meh_at"] {
            assert!(!anime_columns.iter().any(|c| c == removed), "{removed}");
        }
    }

    #[test]
    fn migration_from_real_v2_file_keeps_all_marks_and_settings() {
        let dir = TempDir::new("schema-v2");
        let (catalog, user) = paths(&dir);
        write_v2_catalog(&catalog);

        let db = Database::open(&catalog, &user).unwrap();

        assert_eq!(marks(&db), expected_v2_marks());
        let stored = db.with_conn(settings::load).unwrap();
        assert_eq!(stored.content_locale, "de-DE");
        assert_eq!(stored.search.watched, WatchedFilter::Unwatched);
        assert_eq!(
            stored
                .search
                .tags
                .iter()
                .map(|t| (t.name.as_str(), t.state))
                .collect::<Vec<_>>(),
            [
                ("Female Protagonist", TagState::Prefer),
                ("School", TagState::Prefer)
            ]
        );
        let raw: String = db
            .with_conn(|c| {
                Ok(c.query_row(
                    "SELECT value FROM userdata.settings WHERE key = 'settings'",
                    [],
                    |r| r.get(0),
                )?)
            })
            .unwrap();
        assert_eq!(
            raw, V2_SETTINGS_JSON,
            "Einstellungen unverändert übernommen"
        );

        // Filter, Zähler und Detail lesen die übernommenen Markierungen
        let stats = db.with_conn(anime::stats).unwrap();
        assert_eq!((stats.total, stats.watched, stats.meh), (6, 3, 3));
        assert_eq!(
            ids(&db, &filter(WatchedFilter::Watched, MehFilter::Include)),
            ["w1", "wm", "w2"]
        );
        assert_eq!(
            ids(&db, &filter(WatchedFilter::All, MehFilter::Only)),
            ["wm", "m1", "m2"]
        );
        assert_eq!(
            ids(&db, &filter(WatchedFilter::Unwatched, MehFilter::Hide)),
            ["n1"]
        );
        let store = ImageStore::new(dir.path().join("images")).unwrap();
        let wm = db
            .with_conn(|c| anime::get_anime(c, &store, "wm"))
            .unwrap()
            .unwrap();
        assert!(wm.watched && wm.is_meh);

        // Katalogdaten und Sync-Stand bleiben, die Sprache des letzten Syncs ist übernommen
        let catalog_status = db
            .with_conn(|c| sync_state::status(c, SyncKind::Catalog))
            .unwrap();
        assert_eq!(catalog_status.catalog_country.as_deref(), Some("DE"));
        assert_eq!(catalog_status.content_locale.as_deref(), Some("de-DE"));
        assert_eq!(
            db.with_conn(|c| sync_state::status(c, SyncKind::Metadata))
                .unwrap()
                .content_locale,
            None
        );
        assert_eq!(wm.title, "Gesehen und Meh");
        assert_eq!(db.with_conn(anime::list_tags).unwrap()[0].name, "School");
    }

    /// Katalog im Stand von Schema v3: die migrierte Datei ohne die Ergänzungen von v4.
    fn write_v3_catalog(catalog: &str, user: &str) {
        write_v2_catalog(catalog);
        drop(Database::open(catalog, user).unwrap());
        let conn = Connection::open(catalog).unwrap();
        conn.execute_batch(
            "DROP TABLE anime_descriptors;
             ALTER TABLE anime DROP COLUMN age_rating;
             ALTER TABLE anime DROP COLUMN is_adult;
             PRAGMA user_version = 3;",
        )
        .unwrap();
    }

    #[test]
    fn migration_v3_to_v4_keeps_data_and_defaults_new_fields() {
        let dir = TempDir::new("schema-v3");
        let (catalog, user) = paths(&dir);
        write_v3_catalog(&catalog, &user);

        let db = Database::open(&catalog, &user).unwrap();

        db.with_conn(|c| {
            assert_eq!(version(c, MAIN_SCHEMA), 4);
            Ok(())
        })
        .unwrap();
        assert_eq!(marks(&db), expected_v2_marks());
        let store = ImageStore::new(dir.path().join("images")).unwrap();
        let detail = db
            .with_conn(|c| anime::get_anime(c, &store, "wm"))
            .unwrap()
            .unwrap();
        assert_eq!(detail.title, "Gesehen und Meh");
        assert_eq!(detail.age_rating, None);
        assert!(detail.content_descriptors.is_empty());
        assert!(detail.watched && detail.is_meh);
        let stats = db.with_conn(anime::stats).unwrap();
        assert_eq!(stats.total, 6);
        let mut hide = filter(WatchedFilter::All, MehFilter::Include);
        hide.hide_adult = true;
        assert_eq!(ids(&db, &hide).len(), 6, "ohne Angabe gilt nichts als 18+");
        let adult: i64 = db
            .with_conn(|c| Ok(c.query_row("SELECT SUM(is_adult) FROM anime", [], |r| r.get(0))?))
            .unwrap();
        assert_eq!(adult, 0);
    }

    #[test]
    fn migrated_files_reopen_unchanged() {
        let dir = TempDir::new("schema-reopen");
        let (catalog, user) = paths(&dir);
        write_v2_catalog(&catalog);
        drop(Database::open(&catalog, &user).unwrap());

        let db = Database::open(&catalog, &user).unwrap();

        assert_eq!(marks(&db), expected_v2_marks());
        db.with_conn(|c| {
            assert_eq!(version(c, MAIN_SCHEMA), SCHEMA_VERSION);
            assert_eq!(version(c, USER_SCHEMA), USER_SCHEMA_VERSION);
            Ok(())
        })
        .unwrap();
    }

    #[test]
    fn migration_repeated_after_interrupted_copy_is_idempotent() {
        let dir = TempDir::new("schema-interrupted");
        let (catalog, user) = paths(&dir);
        write_v2_catalog(&catalog);
        // Abbruch nach dem ersten Schritt: Kopie liegt vor, Katalog steht noch auf v2
        {
            let conn = Connection::open(&catalog).unwrap();
            conn.execute(&format!("ATTACH DATABASE ?1 AS {USER_SCHEMA}"), [&user])
                .unwrap();
            conn.execute_batch(&format!(
                "{USER_MIGRATION_V1}\nPRAGMA {USER_SCHEMA}.user_version = 1;\n{COPY_USER_DATA_V3}"
            ))
            .unwrap();
            assert_eq!(version(&conn, MAIN_SCHEMA), 2);
        }

        let db = Database::open(&catalog, &user).unwrap();

        assert_eq!(marks(&db), expected_v2_marks());
    }

    #[test]
    fn migration_merges_with_existing_user_data_without_losing_marks() {
        let dir = TempDir::new("schema-merge");
        let (catalog, user) = paths(&dir);
        write_v2_catalog(&catalog);
        // Bereits vorhandene Nutzer-Datenbank (z. B. wiederhergestellt) mit eigenem Stand
        {
            let conn = Connection::open(&user).unwrap();
            conn.execute_batch(&USER_MIGRATION_V1.replace("userdata.", ""))
                .unwrap();
            conn.execute_batch(
                "PRAGMA user_version = 1;
                 INSERT INTO marks (anime_id, watched, watched_at, is_meh, meh_at)
                 VALUES ('m1', 1, 42, 0, NULL), ('fremd', 1, 7, 0, NULL);
                 INSERT INTO settings (key, value) VALUES ('settings', '{\"content_locale\":\"fr-FR\"}');",
            )
            .unwrap();
        }

        let db = Database::open(&catalog, &user).unwrap();

        let all = marks(&db);
        assert!(all.contains(&("fremd".into(), true, Some(7), false, None)));
        assert!(all.contains(&("m1".into(), true, Some(42), true, Some(1793217508))));
        assert!(all.contains(&("w1".into(), true, Some(1793217805), false, None)));
        assert_eq!(all.len(), 6);
        assert_eq!(
            db.with_conn(settings::load).unwrap().content_locale,
            "fr-FR",
            "vorhandene Einstellungen werden nicht überschrieben"
        );
    }

    #[test]
    fn catalog_version_newer_than_supported_is_rejected() {
        let dir = TempDir::new("schema-too-new");
        let (catalog, user) = paths(&dir);
        {
            let conn = Connection::open(&catalog).unwrap();
            conn.execute_batch(&format!("PRAGMA user_version = {};", SCHEMA_VERSION + 1))
                .unwrap();
        }
        match Database::open(&catalog, &user) {
            Err(error @ CoreError::UnsupportedSchema { .. }) => {
                assert_eq!(error.code(), ErrorCode::UnsupportedSchema);
                assert!(matches!(
                    error,
                    CoreError::UnsupportedSchema { found, supported }
                        if found == SCHEMA_VERSION + 1 && supported == SCHEMA_VERSION
                ));
            }
            Err(other) => panic!("UnsupportedSchema erwartet: {other:?}"),
            Ok(_) => panic!("UnsupportedSchema erwartet, Öffnen gelang"),
        }
        // Die neuere Datei bleibt unangetastet
        let conn = Connection::open(&catalog).unwrap();
        assert_eq!(version(&conn, MAIN_SCHEMA), SCHEMA_VERSION + 1);
    }

    #[test]
    fn user_version_newer_than_supported_is_rejected() {
        let dir = TempDir::new("schema-user-too-new");
        let (catalog, user) = paths(&dir);
        {
            let conn = Connection::open(&user).unwrap();
            conn.execute_batch(&format!(
                "PRAGMA user_version = {};",
                USER_SCHEMA_VERSION + 1
            ))
            .unwrap();
        }
        assert!(matches!(
            Database::open(&catalog, &user),
            Err(CoreError::UnsupportedSchema { found, .. }) if found == USER_SCHEMA_VERSION + 1
        ));
        // Weder Katalog noch Nutzer-Datenbank wurden angefasst
        let catalog_conn = Connection::open(&catalog).unwrap();
        assert_eq!(version(&catalog_conn, MAIN_SCHEMA), 0);
        let catalog_tables: i64 = catalog_conn
            .query_row("SELECT COUNT(*) FROM sqlite_master", [], |r| r.get(0))
            .unwrap();
        assert_eq!(catalog_tables, 0, "der Katalog bleibt leer");
        let user_conn = Connection::open(&user).unwrap();
        assert_eq!(version(&user_conn, MAIN_SCHEMA), USER_SCHEMA_VERSION + 1);
    }

    #[test]
    fn foreign_keys_are_enforced_and_cascade() {
        let db = Database::open_in_memory().unwrap();
        db.with_conn(|c| {
            let enabled: bool = c.query_row("PRAGMA foreign_keys", [], |r| r.get(0))?;
            assert!(enabled);
            c.execute_batch(
                "INSERT INTO anime (id, title) VALUES ('a', 'A');
                 INSERT INTO genres (name) VALUES ('Drama');
                 INSERT INTO tags (name) VALUES ('Magie');
                 INSERT INTO anime_genres (anime_id, genre_id) SELECT 'a', id FROM genres;
                 INSERT INTO anime_tags (anime_id, tag_id, rank) SELECT 'a', id, 50 FROM tags;
                 INSERT INTO images (hash, ext, bytes) VALUES ('h', 'webp', 1);
                 INSERT INTO image_refs (anime_id, slot, source_url, hash)
                 VALUES ('a', 'banner', 'https://b', 'h');",
            )?;
            // Verweis auf einen unbekannten Anime wird abgewiesen
            assert!(c
                .execute(
                    "INSERT INTO anime_genres (anime_id, genre_id) SELECT 'fehlt', id FROM genres",
                    [],
                )
                .is_err());
            c.execute("DELETE FROM anime WHERE id = 'a'", [])?;
            for table in ["anime_genres", "anime_tags", "image_refs"] {
                let rows: i64 =
                    c.query_row(&format!("SELECT COUNT(*) FROM {table}"), [], |r| r.get(0))?;
                assert_eq!(rows, 0, "{table} muss mitgelöscht werden");
            }
            Ok(())
        })
        .unwrap();
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
