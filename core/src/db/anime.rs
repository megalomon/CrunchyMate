use rusqlite::{params, Connection, OptionalExtension, Transaction};

use crate::db::images::{image_stats, local_path, ImageSlot};
use crate::error::CoreResult;
use crate::images::store::ImageStore;
use crate::model::{
    AniListInfo, AnimeDetail, CatalogItem, MetadataItem, NameCount, Stats, TagRank,
};

const UPSERT_ANIME: &str = "
    INSERT INTO anime (
        id, title, description, poster_tall, poster_wide, episode_count, launch_year,
        is_subbed, is_dubbed, has_target_sub, has_target_dub, cr_updated_at
    ) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, unixepoch())
    ON CONFLICT(id) DO UPDATE SET
        title = excluded.title,
        description = excluded.description,
        poster_tall = excluded.poster_tall,
        poster_wide = excluded.poster_wide,
        episode_count = excluded.episode_count,
        launch_year = excluded.launch_year,
        is_subbed = excluded.is_subbed,
        is_dubbed = excluded.is_dubbed,
        has_target_sub = excluded.has_target_sub,
        has_target_dub = excluded.has_target_dub,
        cr_updated_at = excluded.cr_updated_at";

const UPDATE_ANILIST: &str = "
    UPDATE anime SET
        anilist_id = ?2,
        anilist_title_romaji = ?3,
        anilist_title_english = ?4,
        anilist_description = ?5,
        anilist_score = ?6,
        anilist_status = ?7,
        anilist_banner_image = ?8,
        anilist_site_url = ?9,
        anilist_synced_at = unixepoch(),
        anilist_missed_at = NULL
    WHERE id = ?1";

const SELECT_DETAIL: &str = "
    SELECT a.id, a.title, a.anilist_title_romaji, a.anilist_title_english, a.description,
           a.episode_count, a.launch_year, a.has_target_dub, a.has_target_sub,
           COALESCE(m.watched, 0), COALESCE(m.is_meh, 0),
           a.anilist_id, a.anilist_score, a.anilist_status, a.anilist_site_url,
           a.anilist_description
    FROM anime a LEFT JOIN userdata.marks m ON m.anime_id = a.id
    WHERE a.id = ?1";

// Eine Zeile ohne jede Markierung hat keinen Zweck mehr
const DELETE_EMPTY_MARK: &str =
    "DELETE FROM userdata.marks WHERE anime_id = ?1 AND watched = 0 AND is_meh = 0";

pub fn upsert_catalog_page(tx: &Transaction, items: &[CatalogItem]) -> CoreResult<()> {
    let mut upsert = tx.prepare_cached(UPSERT_ANIME)?;
    for item in items {
        upsert.execute(params![
            item.id,
            item.title,
            item.description,
            item.poster_tall,
            item.poster_wide,
            item.episode_count,
            item.launch_year,
            item.is_subbed,
            item.is_dubbed,
            item.has_target_sub,
            item.has_target_dub,
        ])?;
        link_genres(tx, &item.id, &item.genres)?;
    }
    Ok(())
}

pub fn apply_metadata(tx: &Transaction, anime_id: &str, item: &MetadataItem) -> CoreResult<()> {
    let updated = tx.prepare_cached(UPDATE_ANILIST)?.execute(params![
        anime_id,
        item.anilist_id,
        item.title_romaji,
        item.title_english,
        item.description,
        item.score,
        item.status,
        item.banner_image,
        item.site_url,
    ])?;
    // Unbekannte ID ist wie bei set_watched/set_meh kein Fehler.
    if updated == 0 {
        return Ok(());
    }
    link_genres(tx, anime_id, &item.genres)?;
    let mut insert_tag = tx.prepare_cached("INSERT OR IGNORE INTO tags (name) VALUES (?1)")?;
    let mut link_tag = tx.prepare_cached(
        "INSERT INTO anime_tags (anime_id, tag_id, rank)
         SELECT ?1, id, ?3 FROM tags WHERE name = ?2
         ON CONFLICT(anime_id, tag_id) DO UPDATE SET rank = excluded.rank",
    )?;
    for tag in &item.tags {
        insert_tag.execute([&tag.name])?;
        link_tag.execute(params![anime_id, tag.name, tag.rank])?;
    }
    Ok(())
}

// Zuordnungen nur ergänzen: bereits bekannte Genres bleiben erhalten.
fn link_genres(tx: &Transaction, anime_id: &str, genres: &[String]) -> CoreResult<()> {
    let mut insert_genre = tx.prepare_cached("INSERT OR IGNORE INTO genres (name) VALUES (?1)")?;
    let mut link_genre = tx.prepare_cached(
        "INSERT OR IGNORE INTO anime_genres (anime_id, genre_id)
         SELECT ?1, id FROM genres WHERE name = ?2",
    )?;
    for genre in genres {
        insert_genre.execute([genre])?;
        link_genre.execute(params![anime_id, genre])?;
    }
    Ok(())
}

/// Markierungen gelten auch für IDs, die (noch) nicht im Katalog stehen, etwa nach einer
/// Wiederherstellung vor dem ersten Sync.
pub fn set_watched(conn: &Connection, id: &str, watched: bool, now: i64) -> CoreResult<()> {
    conn.execute(
        "INSERT INTO userdata.marks (anime_id, watched, watched_at)
         VALUES (?1, ?2, CASE WHEN ?2 THEN ?3 END)
         ON CONFLICT(anime_id) DO UPDATE SET
             watched = excluded.watched, watched_at = excluded.watched_at",
        params![id, watched, now],
    )?;
    conn.execute(DELETE_EMPTY_MARK, [id])?;
    Ok(())
}

pub fn set_meh(conn: &Connection, id: &str, meh: bool, now: i64) -> CoreResult<()> {
    conn.execute(
        "INSERT INTO userdata.marks (anime_id, is_meh, meh_at)
         VALUES (?1, ?2, CASE WHEN ?2 THEN ?3 END)
         ON CONFLICT(anime_id) DO UPDATE SET
             is_meh = excluded.is_meh, meh_at = excluded.meh_at",
        params![id, meh, now],
    )?;
    conn.execute(DELETE_EMPTY_MARK, [id])?;
    Ok(())
}

pub fn get_anime(
    conn: &Connection,
    store: &ImageStore,
    id: &str,
) -> CoreResult<Option<AnimeDetail>> {
    let detail = conn
        .query_row(SELECT_DETAIL, [id], |row| {
            let anilist = match row.get::<_, Option<i64>>(11)? {
                Some(_) => Some(AniListInfo {
                    score: row.get(12)?,
                    status: row.get(13)?,
                    site_url: row.get(14)?,
                    description: row.get(15)?,
                }),
                None => None,
            };
            Ok(AnimeDetail {
                id: row.get(0)?,
                title: row.get(1)?,
                title_romaji: row.get(2)?,
                title_english: row.get(3)?,
                description: row.get(4)?,
                poster_path: String::new(),
                banner_path: String::new(),
                episode_count: row.get(5)?,
                launch_year: row.get(6)?,
                has_target_dub: row.get(7)?,
                has_target_sub: row.get(8)?,
                watched: row.get(9)?,
                is_meh: row.get(10)?,
                genres: Vec::new(),
                tags: Vec::new(),
                anilist,
            })
        })
        .optional()?;
    let Some(mut detail) = detail else {
        return Ok(None);
    };
    detail.genres = detail_genres(conn, id)?;
    detail.tags = detail_tags(conn, id)?;
    detail.poster_path = local_path(conn, store, id, ImageSlot::PosterTall)?.unwrap_or_default();
    // Ohne Banner dient das breite Poster als Ersatz
    detail.banner_path = match local_path(conn, store, id, ImageSlot::Banner)? {
        Some(path) => path,
        None => local_path(conn, store, id, ImageSlot::PosterWide)?.unwrap_or_default(),
    };
    Ok(Some(detail))
}

fn detail_genres(conn: &Connection, id: &str) -> CoreResult<Vec<String>> {
    let mut stmt = conn.prepare_cached(
        "SELECT g.name FROM genres g JOIN anime_genres ag ON ag.genre_id = g.id
         WHERE ag.anime_id = ?1 ORDER BY g.name",
    )?;
    let names = stmt
        .query_map([id], |row| row.get(0))?
        .collect::<Result<Vec<String>, _>>()?;
    Ok(names)
}

fn detail_tags(conn: &Connection, id: &str) -> CoreResult<Vec<TagRank>> {
    let mut stmt = conn.prepare_cached(
        "SELECT t.name, at.rank FROM tags t JOIN anime_tags at ON at.tag_id = t.id
         WHERE at.anime_id = ?1 ORDER BY at.rank DESC, t.name",
    )?;
    let tags = stmt
        .query_map([id], |row| {
            Ok(TagRank {
                name: row.get(0)?,
                rank: row.get(1)?,
            })
        })?
        .collect::<Result<Vec<_>, _>>()?;
    Ok(tags)
}

fn name_counts(conn: &Connection, sql: &str) -> CoreResult<Vec<NameCount>> {
    let mut stmt = conn.prepare_cached(sql)?;
    let rows = stmt
        .query_map([], |row| {
            Ok(NameCount {
                name: row.get(0)?,
                count: row.get(1)?,
            })
        })?
        .collect::<Result<Vec<_>, _>>()?;
    Ok(rows)
}

pub fn list_genres(conn: &Connection) -> CoreResult<Vec<NameCount>> {
    name_counts(
        conn,
        "SELECT g.name, COUNT(ag.anime_id) FROM genres g
         LEFT JOIN anime_genres ag ON ag.genre_id = g.id
         GROUP BY g.id ORDER BY g.name",
    )
}

pub fn list_tags(conn: &Connection) -> CoreResult<Vec<NameCount>> {
    name_counts(
        conn,
        "SELECT t.name, COUNT(at.anime_id) AS usage FROM tags t
         LEFT JOIN anime_tags at ON at.tag_id = t.id
         GROUP BY t.id ORDER BY usage DESC, t.name",
    )
}

/// Gezählt werden nur Markierungen von Einträgen im Katalog, passend zu den Tabs Gesehen und Meh.
pub fn stats(conn: &Connection) -> CoreResult<Stats> {
    let stats = conn.query_row(
        "SELECT COUNT(*),
                COALESCE(SUM(m.watched = 1), 0),
                COALESCE(SUM(m.is_meh = 1), 0),
                COALESCE(SUM(a.anilist_id IS NOT NULL), 0),
                (SELECT COUNT(*) FROM tags)
         FROM anime a LEFT JOIN userdata.marks m ON m.anime_id = a.id",
        [],
        |row| {
            Ok(Stats {
                total: row.get(0)?,
                watched: row.get(1)?,
                meh: row.get(2)?,
                enriched: row.get(3)?,
                tags: row.get(4)?,
                image_count: 0,
                image_bytes: 0,
            })
        },
    )?;
    let (image_count, image_bytes) = image_stats(conn)?;
    Ok(Stats {
        image_count,
        image_bytes,
        ..stats
    })
}

/// Noch nicht angereicherte Einträge als (ID, Titel). Titel, deren letzte Suche nach
/// `retry_missed_before` (Unix-Sekunden) ohne Treffer blieb, werden noch nicht erneut versucht.
pub fn unenriched(
    conn: &Connection,
    retry_missed_before: i64,
) -> CoreResult<Vec<(String, String)>> {
    let mut stmt = conn.prepare_cached(
        "SELECT id, title FROM anime
         WHERE anilist_id IS NULL AND (anilist_missed_at IS NULL OR anilist_missed_at <= ?1)
         ORDER BY title, id",
    )?;
    let rows = stmt
        .query_map([retry_missed_before], |row| Ok((row.get(0)?, row.get(1)?)))?
        .collect::<Result<Vec<_>, _>>()?;
    Ok(rows)
}

/// Merkt sich eine Suche ohne Treffer, damit der Titel erst nach einer Frist erneut angefragt wird.
pub fn mark_metadata_miss(tx: &Transaction, anime_id: &str, now: i64) -> CoreResult<()> {
    tx.prepare_cached("UPDATE anime SET anilist_missed_at = ?2 WHERE id = ?1")?
        .execute(params![anime_id, now])?;
    Ok(())
}

pub fn count_enriched(conn: &Connection) -> CoreResult<u64> {
    let count: i64 = conn.query_row(
        "SELECT COUNT(*) FROM anime WHERE anilist_id IS NOT NULL",
        [],
        |row| row.get(0),
    )?;
    Ok(u64::try_from(count).unwrap_or(0))
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::db::test_support::{insert_all, AnimeFixture};
    use crate::db::Database;
    use crate::model::{CatalogItem, MetadataItem, TagRank};

    // Es werden keine Dateien geschrieben; das Verzeichnis dient nur der Pfadbildung
    fn store() -> ImageStore {
        ImageStore::new(std::env::temp_dir().join("crunchymate-anime-unit")).unwrap()
    }

    fn catalog_item(id: &str, title: &str) -> CatalogItem {
        CatalogItem {
            id: id.to_string(),
            title: title.to_string(),
            description: String::new(),
            poster_tall: format!("tall-{id}"),
            poster_wide: format!("wide-{id}"),
            episode_count: 12,
            launch_year: 2020,
            is_subbed: true,
            is_dubbed: false,
            has_target_sub: true,
            has_target_dub: false,
            genres: vec!["Action".to_string()],
        }
    }

    fn metadata(anilist_id: i64, tags: &[(&str, i32)]) -> MetadataItem {
        MetadataItem {
            anilist_id,
            title_romaji: "Romaji".to_string(),
            title_english: "English".to_string(),
            description: "Beschreibung".to_string(),
            score: 80,
            status: "FINISHED".to_string(),
            banner_image: String::new(),
            site_url: "https://anilist.co/anime/1".to_string(),
            genres: vec!["Drama".to_string()],
            tags: tags
                .iter()
                .map(|(name, rank)| TagRank {
                    name: name.to_string(),
                    rank: *rank,
                })
                .collect(),
        }
    }

    fn column_i64(db: &Database, sql: &str, id: &str) -> Option<i64> {
        db.with_conn(|conn| Ok(conn.query_row(sql, [id], |row| row.get(0))?))
            .unwrap()
    }

    #[test]
    fn upsert_is_idempotent() {
        let db = Database::open_in_memory().unwrap();
        let items = vec![catalog_item("a", "A"), catalog_item("b", "B")];
        for _ in 0..2 {
            db.with_tx(|tx| upsert_catalog_page(tx, &items)).unwrap();
        }
        assert_eq!(db.with_conn(stats).unwrap().total, 2);
        let genres = db.with_conn(list_genres).unwrap();
        assert_eq!(genres[0].count, 2);
    }

    #[test]
    fn upsert_keeps_user_marks_and_anilist_data() {
        let db = Database::open_in_memory().unwrap();
        db.with_tx(|tx| upsert_catalog_page(tx, &[catalog_item("a", "Alt")]))
            .unwrap();
        db.with_conn(|c| set_watched(c, "a", true, 100)).unwrap();
        db.with_conn(|c| set_meh(c, "a", true, 200)).unwrap();
        db.with_tx(|tx| apply_metadata(tx, "a", &metadata(7, &[("Magie", 60)])))
            .unwrap();
        db.with_tx(|tx| upsert_catalog_page(tx, &[catalog_item("a", "Neu")]))
            .unwrap();

        let detail = db
            .with_conn(|c| get_anime(c, &store(), "a"))
            .unwrap()
            .unwrap();
        assert_eq!(detail.title, "Neu");
        assert!(detail.watched && detail.is_meh);
        assert_eq!(detail.anilist.unwrap().score, 80);
        assert_eq!(detail.tags.len(), 1);
        assert_eq!(
            column_i64(
                &db,
                "SELECT watched_at FROM userdata.marks WHERE anime_id = ?1",
                "a"
            ),
            Some(100)
        );
    }

    #[test]
    fn set_watched_sets_and_clears_timestamp() {
        let db = Database::open_in_memory().unwrap();
        db.with_tx(|tx| upsert_catalog_page(tx, &[catalog_item("a", "A")]))
            .unwrap();
        let sql = "SELECT watched_at FROM userdata.marks WHERE anime_id = ?1";
        db.with_conn(|c| set_watched(c, "a", true, 123)).unwrap();
        assert_eq!(column_i64(&db, sql, "a"), Some(123));
        db.with_conn(|c| set_meh(c, "a", true, 124)).unwrap();
        db.with_conn(|c| set_watched(c, "a", false, 456)).unwrap();
        assert_eq!(column_i64(&db, sql, "a"), None);
        db.with_conn(|c| set_watched(c, "unbekannt", true, 1))
            .unwrap();
    }

    #[test]
    fn set_meh_sets_and_clears_timestamp() {
        let db = Database::open_in_memory().unwrap();
        db.with_tx(|tx| upsert_catalog_page(tx, &[catalog_item("a", "A")]))
            .unwrap();
        let sql = "SELECT meh_at FROM userdata.marks WHERE anime_id = ?1";
        db.with_conn(|c| set_meh(c, "a", true, 123)).unwrap();
        assert_eq!(column_i64(&db, sql, "a"), Some(123));
        db.with_conn(|c| set_watched(c, "a", true, 124)).unwrap();
        db.with_conn(|c| set_meh(c, "a", false, 456)).unwrap();
        assert_eq!(column_i64(&db, sql, "a"), None);
        db.with_conn(|c| set_meh(c, "unbekannt", true, 1)).unwrap();
    }

    fn mark_rows(db: &Database) -> i64 {
        db.with_conn(|c| Ok(c.query_row("SELECT COUNT(*) FROM userdata.marks", [], |r| r.get(0))?))
            .unwrap()
    }

    #[test]
    fn unmarking_both_removes_mark_row() {
        let db = Database::open_in_memory().unwrap();
        db.with_conn(|c| {
            set_watched(c, "a", true, 1)?;
            set_meh(c, "a", true, 2)?;
            set_watched(c, "a", false, 3)?;
            Ok(())
        })
        .unwrap();
        assert_eq!(mark_rows(&db), 1);
        db.with_conn(|c| set_meh(c, "a", false, 4)).unwrap();
        assert_eq!(mark_rows(&db), 0);
    }

    #[test]
    fn marks_for_unknown_ids_appear_once_the_catalog_has_them() {
        let db = Database::open_in_memory().unwrap();
        db.with_conn(|c| set_watched(c, "spaeter", true, 5))
            .unwrap();
        assert_eq!(db.with_conn(stats).unwrap().watched, 0);
        db.with_tx(|tx| upsert_catalog_page(tx, &[catalog_item("spaeter", "Später")]))
            .unwrap();
        let detail = db
            .with_conn(|c| get_anime(c, &store(), "spaeter"))
            .unwrap()
            .unwrap();
        assert!(detail.watched && !detail.is_meh);
        assert_eq!(db.with_conn(stats).unwrap().watched, 1);
    }

    #[test]
    fn metadata_miss_postpones_retry_until_deadline() {
        let db = Database::open_in_memory().unwrap();
        insert_all(
            &db,
            &[AnimeFixture::new("a", "A"), AnimeFixture::new("b", "B")],
        )
        .unwrap();
        db.with_tx(|tx| mark_metadata_miss(tx, "a", 1_000)).unwrap();
        let ids = |before: i64| -> Vec<String> {
            db.with_conn(|c| unenriched(c, before))
                .unwrap()
                .into_iter()
                .map(|(id, _)| id)
                .collect()
        };
        assert_eq!(ids(999), ["b"]);
        assert_eq!(ids(1_000), ["a", "b"]);
        // Ein späterer Treffer löscht den Vermerk
        db.with_tx(|tx| apply_metadata(tx, "a", &metadata(3, &[])))
            .unwrap();
        assert_eq!(
            column_i64(
                &db,
                "SELECT anilist_missed_at FROM anime WHERE id = ?1",
                "a"
            ),
            None
        );
    }

    #[test]
    fn get_anime_returns_detail_with_sorted_tags_and_fallbacks() {
        let db = Database::open_in_memory().unwrap();
        insert_all(
            &db,
            &[
                AnimeFixture::new("a", "A")
                    .tag("Klein", 50)
                    .tag("Gross", 90)
                    .genre("Action")
                    .romaji("A Romaji")
                    .english("A English")
                    .no_poster_tall(),
                AnimeFixture::new("b", "B").banner("banner-b").score(70),
            ],
        )
        .unwrap();

        let a = db
            .with_conn(|c| get_anime(c, &store(), "a"))
            .unwrap()
            .unwrap();
        let ranks: Vec<i32> = a.tags.iter().map(|t| t.rank).collect();
        assert_eq!(ranks, vec![90, 50]);
        assert_eq!(a.title_romaji, "A Romaji");
        assert_eq!(a.title_english, "A English");
        assert_eq!(a.genres, vec!["Action".to_string()]);
        assert!(a.anilist.is_some());

        let b = db
            .with_conn(|c| get_anime(c, &store(), "b"))
            .unwrap()
            .unwrap();
        assert_eq!((b.poster_path.as_str(), b.banner_path.as_str()), ("", ""));

        insert_all(&db, &[AnimeFixture::new("c", "C")]).unwrap();
        let c = db
            .with_conn(|c| get_anime(c, &store(), "c"))
            .unwrap()
            .unwrap();
        assert!(c.anilist.is_none());
        assert_eq!(c.title_romaji, "");
    }

    #[test]
    fn get_anime_unknown_id_is_none() {
        let db = Database::open_in_memory().unwrap();
        assert!(db
            .with_conn(|c| get_anime(c, &store(), "x"))
            .unwrap()
            .is_none());
    }

    #[test]
    fn stats_count_watched_regardless_of_meh() {
        let db = Database::open_in_memory().unwrap();
        insert_all(
            &db,
            &[
                AnimeFixture::new("a", "A").watched().meh().tag("T", 10),
                AnimeFixture::new("b", "B"),
            ],
        )
        .unwrap();
        let stats = db.with_conn(stats).unwrap();
        assert_eq!(
            (
                stats.total,
                stats.watched,
                stats.meh,
                stats.enriched,
                stats.tags
            ),
            (2, 1, 1, 1, 1)
        );
        assert_eq!(db.with_conn(count_enriched).unwrap(), 1);
    }

    #[test]
    fn unenriched_lists_only_items_without_anilist_id() {
        let db = Database::open_in_memory().unwrap();
        insert_all(
            &db,
            &[
                AnimeFixture::new("a", "Zeta"),
                AnimeFixture::new("b", "Beta").score(50),
                AnimeFixture::new("c", "Alpha"),
            ],
        )
        .unwrap();
        let list = db.with_conn(|c| unenriched(c, 0)).unwrap();
        assert_eq!(
            list,
            vec![
                ("c".to_string(), "Alpha".to_string()),
                ("a".to_string(), "Zeta".to_string())
            ]
        );
    }

    #[test]
    fn list_tags_sorted_by_count_then_name() {
        let db = Database::open_in_memory().unwrap();
        insert_all(
            &db,
            &[
                AnimeFixture::new("a", "A").tag("Zebra", 1).tag("Apfel", 1),
                AnimeFixture::new("b", "B").tag("Zebra", 1).tag("Birne", 1),
                AnimeFixture::new("c", "C").tag("Zebra", 1),
            ],
        )
        .unwrap();
        let tags = db.with_conn(list_tags).unwrap();
        let names: Vec<(&str, i64)> = tags.iter().map(|t| (t.name.as_str(), t.count)).collect();
        assert_eq!(names, vec![("Zebra", 3), ("Apfel", 1), ("Birne", 1)]);
    }

    #[test]
    fn apply_metadata_unknown_id_is_noop() {
        let db = Database::open_in_memory().unwrap();
        db.with_tx(|tx| apply_metadata(tx, "unbekannt", &metadata(9, &[("Magie", 60)])))
            .unwrap();
        for table in ["genres", "tags", "anime_tags", "anime_genres"] {
            let rows: i64 = db
                .with_conn(|c| {
                    Ok(c.query_row(&format!("SELECT COUNT(*) FROM {table}"), [], |r| r.get(0))?)
                })
                .unwrap();
            assert_eq!(rows, 0, "{table} sollte leer bleiben");
        }
    }
}
