//! Tabellen `images` und `image_refs`.

use rusqlite::{params, Connection, Transaction};

use crate::error::CoreResult;

/// Bildart je Anime; der Text ist in `image_refs.slot` gespeichert und muss stabil bleiben.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum ImageSlot {
    PosterTall,
    PosterWide,
    Banner,
}

impl ImageSlot {
    pub fn as_str(self) -> &'static str {
        match self {
            ImageSlot::PosterTall => "poster_tall",
            ImageSlot::PosterWide => "poster_wide",
            ImageSlot::Banner => "banner",
        }
    }

    /// Spalte der Tabelle `anime` mit der Quell-URL.
    fn source_column(self) -> &'static str {
        match self {
            ImageSlot::PosterTall => "poster_tall",
            ImageSlot::PosterWide => "poster_wide",
            ImageSlot::Banner => "anilist_banner_image",
        }
    }
}

/// Bild, dessen Quelle noch nicht (oder in anderer Version) gespeichert ist.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct PendingImage {
    pub anime_id: String,
    pub slot: ImageSlot,
    pub source_url: String,
}

pub fn pending(conn: &Connection, slots: &[ImageSlot]) -> CoreResult<Vec<PendingImage>> {
    let mut result = Vec::new();
    for &slot in slots {
        // Spaltenname stammt aus einer festen Zuordnung, nicht aus Eingaben
        let column = slot.source_column();
        let mut stmt = conn.prepare(&format!(
            "SELECT a.id, a.{column}
             FROM anime a
             LEFT JOIN image_refs r ON r.anime_id = a.id AND r.slot = ?1
             WHERE a.{column} <> '' AND (r.source_url IS NULL OR r.source_url <> a.{column})
             ORDER BY a.title, a.id"
        ))?;
        let rows = stmt.query_map([slot.as_str()], |row| {
            Ok(PendingImage {
                anime_id: row.get(0)?,
                slot,
                source_url: row.get(1)?,
            })
        })?;
        for row in rows {
            result.push(row?);
        }
    }
    Ok(result)
}

/// Speichert Bild und Verweis; erst aufrufen, wenn die Datei vollständig vorliegt.
pub fn record(
    tx: &Transaction,
    anime_id: &str,
    slot: ImageSlot,
    source_url: &str,
    hash: &str,
    ext: &str,
    bytes: i64,
) -> CoreResult<()> {
    tx.prepare_cached(
        "INSERT INTO images (hash, ext, bytes) VALUES (?1, ?2, ?3)
         ON CONFLICT(hash) DO UPDATE SET ext = excluded.ext, bytes = excluded.bytes",
    )?
    .execute(params![hash, ext, bytes])?;
    tx.prepare_cached(
        "INSERT INTO image_refs (anime_id, slot, source_url, hash) VALUES (?1, ?2, ?3, ?4)
         ON CONFLICT(anime_id, slot) DO UPDATE SET
             source_url = excluded.source_url,
             hash = excluded.hash",
    )?
    .execute(params![anime_id, slot.as_str(), source_url, hash])?;
    Ok(())
}

/// Hash und Endung der lokalen Datei, falls vorhanden.
pub fn local_ref(
    conn: &Connection,
    anime_id: &str,
    slot: ImageSlot,
) -> CoreResult<Option<(String, String)>> {
    let mut stmt = conn.prepare_cached(
        "SELECT i.hash, i.ext FROM image_refs r JOIN images i ON i.hash = r.hash
         WHERE r.anime_id = ?1 AND r.slot = ?2",
    )?;
    let mut rows = stmt.query(params![anime_id, slot.as_str()])?;
    match rows.next()? {
        Some(row) => Ok(Some((row.get(0)?, row.get(1)?))),
        None => Ok(None),
    }
}

/// Bilder ohne Verweis als (Hash, Endung); ihre Dateien dürfen gelöscht werden.
pub fn unreferenced(conn: &Connection) -> CoreResult<Vec<(String, String)>> {
    let mut stmt = conn.prepare(
        "SELECT hash, ext FROM images
         WHERE NOT EXISTS (SELECT 1 FROM image_refs r WHERE r.hash = images.hash)",
    )?;
    let rows = stmt
        .query_map([], |row| Ok((row.get(0)?, row.get(1)?)))?
        .collect::<Result<Vec<_>, _>>()?;
    Ok(rows)
}

pub fn delete_image(conn: &Connection, hash: &str) -> CoreResult<()> {
    conn.execute("DELETE FROM images WHERE hash = ?1", [hash])?;
    Ok(())
}

/// Anzahl und Gesamtgröße der gespeicherten Bilder.
pub fn image_stats(conn: &Connection) -> CoreResult<(i64, i64)> {
    Ok(conn.query_row(
        "SELECT COUNT(*), COALESCE(SUM(bytes), 0) FROM images",
        [],
        |row| Ok((row.get(0)?, row.get(1)?)),
    )?)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::db::Database;

    fn conn_with_anime() -> Database {
        let db = Database::open_in_memory().unwrap();
        db.with_conn(|c| {
            c.execute_batch(
                "INSERT INTO anime (id, title, poster_tall, poster_wide, anilist_banner_image) VALUES
                    ('a', 'A', 'http://t/a', '', 'http://b/a'),
                    ('b', 'B', '', '', ''),
                    ('c', 'C', 'http://t/c', 'http://w/c', '');",
            )?;
            Ok(())
        })
        .unwrap();
        db
    }

    fn store(db: &Database, id: &str, slot: ImageSlot, url: &str, hash: &str, bytes: i64) {
        db.with_tx(|tx| record(tx, id, slot, url, hash, "webp", bytes))
            .unwrap();
    }

    #[test]
    fn pending_lists_missing_and_changed_urls() {
        let db = conn_with_anime();
        let all = [
            ImageSlot::PosterTall,
            ImageSlot::PosterWide,
            ImageSlot::Banner,
        ];
        let list = db.with_conn(|c| pending(c, &all)).unwrap();
        let keys: Vec<_> = list.iter().map(|p| (p.anime_id.as_str(), p.slot)).collect();
        assert_eq!(
            keys,
            vec![
                ("a", ImageSlot::PosterTall),
                ("c", ImageSlot::PosterTall),
                ("c", ImageSlot::PosterWide),
                ("a", ImageSlot::Banner),
            ]
        );

        store(&db, "a", ImageSlot::PosterTall, "http://t/a", "h1", 10);
        store(&db, "c", ImageSlot::PosterTall, "http://alt/c", "h2", 10);
        let list = db
            .with_conn(|c| pending(c, &[ImageSlot::PosterTall]))
            .unwrap();
        assert_eq!(list.len(), 1);
        assert_eq!(list[0].anime_id, "c");
        assert_eq!(list[0].source_url, "http://t/c");
    }

    #[test]
    fn record_then_local_ref() {
        let db = conn_with_anime();
        assert_eq!(
            db.with_conn(|c| local_ref(c, "a", ImageSlot::Banner))
                .unwrap(),
            None
        );
        store(&db, "a", ImageSlot::Banner, "http://b/a", "h1", 10);
        assert_eq!(
            db.with_conn(|c| local_ref(c, "a", ImageSlot::Banner))
                .unwrap(),
            Some(("h1".to_string(), "webp".to_string()))
        );
        store(&db, "a", ImageSlot::Banner, "http://b/a2", "h2", 20);
        assert_eq!(
            db.with_conn(|c| local_ref(c, "a", ImageSlot::Banner))
                .unwrap(),
            Some(("h2".to_string(), "webp".to_string()))
        );
    }

    #[test]
    fn unreferenced_after_ref_removed() {
        let db = conn_with_anime();
        store(&db, "a", ImageSlot::Banner, "http://b/a", "h1", 10);
        store(&db, "c", ImageSlot::PosterTall, "http://t/c", "h1", 10);
        assert!(db.with_conn(unreferenced).unwrap().is_empty());
        db.with_conn(|c| {
            c.execute("DELETE FROM anime WHERE id = 'a'", [])?;
            Ok(())
        })
        .unwrap();
        assert!(db.with_conn(unreferenced).unwrap().is_empty());
        db.with_conn(|c| {
            c.execute("DELETE FROM anime WHERE id = 'c'", [])?;
            Ok(())
        })
        .unwrap();
        let orphans = db.with_conn(unreferenced).unwrap();
        assert_eq!(orphans, vec![("h1".to_string(), "webp".to_string())]);
        db.with_conn(|c| delete_image(c, "h1")).unwrap();
        assert!(db.with_conn(unreferenced).unwrap().is_empty());
    }

    #[test]
    fn image_stats_counts_bytes() {
        let db = conn_with_anime();
        assert_eq!(db.with_conn(image_stats).unwrap(), (0, 0));
        store(&db, "a", ImageSlot::Banner, "http://b/a", "h1", 10);
        store(&db, "a", ImageSlot::PosterTall, "http://t/a", "h2", 32);
        assert_eq!(db.with_conn(image_stats).unwrap(), (2, 42));
    }
}
