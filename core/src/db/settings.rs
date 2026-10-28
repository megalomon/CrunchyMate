use rusqlite::{params, Connection, OptionalExtension};

use crate::error::CoreResult;
use crate::model::Settings;

const SETTINGS_KEY: &str = "settings";

/// Lädt die Einstellungen; fehlend oder unlesbar ergibt die Standardwerte, ein einzelnes
/// unlesbares Feld nur für dieses Feld.
pub fn load(conn: &Connection) -> CoreResult<Settings> {
    let raw: Option<String> = conn
        .query_row(
            "SELECT value FROM userdata.settings WHERE key = ?1",
            params![SETTINGS_KEY],
            |row| row.get(0),
        )
        .optional()?;
    Ok(raw
        .and_then(|json| serde_json::from_str(&json).ok())
        .unwrap_or_default())
}

pub fn save(conn: &Connection, settings: &Settings) -> CoreResult<()> {
    let json = serde_json::to_string(settings)?;
    conn.execute(
        "INSERT INTO userdata.settings(key, value) VALUES (?1, ?2)
         ON CONFLICT(key) DO UPDATE SET value = excluded.value",
        params![SETTINGS_KEY, json],
    )?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::db::Database;
    use crate::model::{
        LanguageFilter, SearchState, SortOrder, TagSelection, TagState, WatchedFilter,
    };

    #[test]
    fn settings_default_when_missing() {
        let db = Database::open_in_memory().unwrap();
        let s = db.with_conn(load).unwrap();
        assert_eq!(s.content_locale, "de-DE");
        assert_eq!(s, Settings::default());
    }

    #[test]
    fn settings_roundtrip_with_tags() {
        let db = Database::open_in_memory().unwrap();
        let mut s = Settings {
            content_locale: "en-US".to_string(),
            ..Settings::default()
        };
        s.search.query = "Größe".to_string();
        s.search.genre = Some("Action".to_string());
        s.search.language = LanguageFilter::Both;
        s.search.tags = vec![
            TagSelection {
                name: "Isekai".into(),
                state: TagState::Must,
            },
            TagSelection {
                name: "Horror".into(),
                state: TagState::Exclude,
            },
        ];
        db.with_conn(|c| save(c, &s)).unwrap();
        db.with_conn(|c| save(c, &s)).unwrap();
        assert_eq!(db.with_conn(load).unwrap(), s);
    }

    #[test]
    fn settings_default_when_corrupt_json() {
        let db = Database::open_in_memory().unwrap();
        db.with_conn(|c| {
            c.execute(
                "INSERT INTO userdata.settings(key, value) VALUES ('settings', '{kaputt')",
                [],
            )?;
            Ok(())
        })
        .unwrap();
        assert_eq!(db.with_conn(load).unwrap(), Settings::default());
    }

    #[test]
    fn settings_partial_json_fills_defaults() {
        let db = Database::open_in_memory().unwrap();
        db.with_conn(|c| {
            c.execute(
                "INSERT INTO userdata.settings(key, value) VALUES ('settings', '{\"content_locale\":\"fr-FR\"}')",
                [],
            )?;
            Ok(())
        })
        .unwrap();
        let s = db.with_conn(load).unwrap();
        assert_eq!(s.content_locale, "fr-FR");
        assert_eq!(s.search, Default::default());
    }

    fn store_raw(db: &Database, json: &str) {
        db.with_conn(|c| {
            c.execute(
                "INSERT INTO userdata.settings(key, value) VALUES ('settings', ?1)",
                [json],
            )?;
            Ok(())
        })
        .unwrap();
    }

    fn tag(name: &str, state: TagState) -> TagSelection {
        TagSelection {
            name: name.to_string(),
            state,
        }
    }

    // Gespeicherte Einstellungen müssen über Versionen hinweg lesbar bleiben: ändert sich eine
    // dieser Zeichenketten, ist das eine Formatänderung und braucht eine Migration.
    #[test]
    fn golden_json_for_every_variant() {
        let variants: [(String, &str); 14] = [
            (to_json(&LanguageFilter::All), "\"All\""),
            (to_json(&LanguageFilter::Dub), "\"Dub\""),
            (to_json(&LanguageFilter::Sub), "\"Sub\""),
            (to_json(&LanguageFilter::Both), "\"Both\""),
            (to_json(&WatchedFilter::All), "\"All\""),
            (to_json(&WatchedFilter::Unwatched), "\"Unwatched\""),
            (to_json(&WatchedFilter::Watched), "\"Watched\""),
            (to_json(&SortOrder::Title), "\"Title\""),
            (to_json(&SortOrder::Score), "\"Score\""),
            (to_json(&SortOrder::Episodes), "\"Episodes\""),
            (to_json(&SortOrder::Year), "\"Year\""),
            (to_json(&TagState::Prefer), "\"Prefer\""),
            (to_json(&TagState::Must), "\"Must\""),
            (to_json(&TagState::Exclude), "\"Exclude\""),
        ];
        for (actual, golden) in variants {
            assert_eq!(actual, golden);
        }
    }

    fn to_json<T: serde::Serialize>(value: &T) -> String {
        serde_json::to_string(value).unwrap()
    }

    const GOLDEN_SETTINGS: &str = r#"{"content_locale":"en-US","search":{"query":"Größe","genre":"Action","language":"Both","watched":"Unwatched","sort":"Episodes","tags":[{"name":"Isekai","state":"Prefer"},{"name":"Magie","state":"Must"},{"name":"Horror","state":"Exclude"}]}}"#;

    fn golden_settings() -> Settings {
        Settings {
            content_locale: "en-US".to_string(),
            search: SearchState {
                query: "Größe".to_string(),
                genre: Some("Action".to_string()),
                language: LanguageFilter::Both,
                watched: WatchedFilter::Unwatched,
                sort: SortOrder::Episodes,
                tags: vec![
                    tag("Isekai", TagState::Prefer),
                    tag("Magie", TagState::Must),
                    tag("Horror", TagState::Exclude),
                ],
            },
        }
    }

    #[test]
    fn golden_json_for_full_settings() {
        assert_eq!(to_json(&golden_settings()), GOLDEN_SETTINGS);
        let db = Database::open_in_memory().unwrap();
        store_raw(&db, GOLDEN_SETTINGS);
        assert_eq!(db.with_conn(load).unwrap(), golden_settings());
    }

    #[test]
    fn unreadable_field_falls_back_only_for_that_field() {
        let db = Database::open_in_memory().unwrap();
        store_raw(
            &db,
            r#"{"content_locale":"fr-FR","search":{"query":"x","language":"Gibtsnicht","watched":"Unwatched","sort":42,"tags":[{"name":"A","state":"Prefer"},{"name":"B","state":"Vielleicht"},{"state":"Must"},{"name":"C","state":"Exclude"}]}}"#,
        );
        let s = db.with_conn(load).unwrap();
        assert_eq!(s.content_locale, "fr-FR");
        assert_eq!(s.search.query, "x");
        assert_eq!(s.search.language, LanguageFilter::All);
        assert_eq!(s.search.watched, WatchedFilter::Unwatched);
        assert_eq!(s.search.sort, SortOrder::Title);
        assert_eq!(
            s.search.tags,
            vec![tag("A", TagState::Prefer), tag("C", TagState::Exclude)]
        );
    }

    #[test]
    fn removed_language_any_loads_as_all() {
        let db = Database::open_in_memory().unwrap();
        store_raw(
            &db,
            r#"{"content_locale":"de-DE","search":{"language":"Any","sort":"Score"}}"#,
        );
        let s = db.with_conn(load).unwrap();
        assert_eq!(s.search.language, LanguageFilter::All);
        assert_eq!(s.search.sort, SortOrder::Score);
    }

    #[test]
    fn wrong_types_fall_back_per_field() {
        let db = Database::open_in_memory().unwrap();
        store_raw(
            &db,
            r#"{"content_locale":5,"search":{"tags":"kein Array","genre":3}}"#,
        );
        assert_eq!(db.with_conn(load).unwrap(), Settings::default());
        let db = Database::open_in_memory().unwrap();
        store_raw(&db, r#"{"content_locale":"it-IT","search":[]}"#);
        let s = db.with_conn(load).unwrap();
        assert_eq!(
            (s.content_locale.as_str(), s.search),
            ("it-IT", SearchState::default())
        );
    }
}
