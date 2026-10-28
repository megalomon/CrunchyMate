use rusqlite::{params, Connection, OptionalExtension};

use crate::error::CoreResult;
use crate::model::Settings;

const SETTINGS_KEY: &str = "settings";

/// Lädt die Einstellungen; fehlend oder unlesbar ergibt die Standardwerte.
pub fn load(conn: &Connection) -> CoreResult<Settings> {
    let raw: Option<String> = conn
        .query_row(
            "SELECT value FROM settings WHERE key = ?1",
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
        "INSERT INTO settings(key, value) VALUES (?1, ?2)
         ON CONFLICT(key) DO UPDATE SET value = excluded.value",
        params![SETTINGS_KEY, json],
    )?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::db::Database;
    use crate::model::{LanguageFilter, TagSelection, TagState};

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
                "INSERT INTO settings(key, value) VALUES ('settings', '{kaputt')",
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
                "INSERT INTO settings(key, value) VALUES ('settings', '{\"content_locale\":\"fr-FR\"}')",
                [],
            )?;
            Ok(())
        })
        .unwrap();
        let s = db.with_conn(load).unwrap();
        assert_eq!(s.content_locale, "fr-FR");
        assert_eq!(s.search, Default::default());
    }
}
