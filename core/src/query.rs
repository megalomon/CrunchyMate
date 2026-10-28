//! Filter-, Such- und Sortierabfrage für die Kachelansicht.

use rusqlite::types::Value;
use rusqlite::{params_from_iter, Connection};

use crate::db::images::ImageSlot;
use crate::error::CoreResult;
use crate::images::store::ImageStore;
use crate::model::{
    AnimeFilter, AnimePage, AnimeSummary, LanguageFilter, MehFilter, SortOrder, WatchedFilter,
};

/// Markierungen liegen in der Nutzer-Datenbank; ohne Zeile gilt ein Eintrag als unmarkiert.
const MARKS_JOIN: &str = "LEFT JOIN userdata.marks m ON m.anime_id = a.id";

/// Bausteine der Abfrage; Werte sind ausschließlich gebundene Parameter.
struct BuiltQuery {
    where_sql: String,
    where_params: Vec<Value>,
    order_sql: String,
    prefer_params: Vec<Value>,
}

/// Maskiert `\`, `%` und `_` für `LIKE ... ESCAPE '\'`.
fn escape_like(text: &str) -> String {
    let mut out = String::with_capacity(text.len());
    for c in text.chars() {
        if matches!(c, '\\' | '%' | '_') {
            out.push('\\');
        }
        out.push(c);
    }
    out
}

fn clean_names(names: &[String]) -> Vec<String> {
    names
        .iter()
        .map(|n| n.trim())
        .filter(|n| !n.is_empty())
        .map(str::to_string)
        .collect()
}

fn placeholders(count: usize) -> String {
    vec!["?"; count].join(", ")
}

fn build(filter: &AnimeFilter) -> BuiltQuery {
    let mut conditions: Vec<String> = Vec::new();
    let mut params: Vec<Value> = Vec::new();

    let text = filter.query.trim();
    if !text.is_empty() {
        let pattern = format!("%{}%", escape_like(text));
        conditions.push(
            "(fold(a.title) LIKE fold(?) ESCAPE '\\' \
             OR fold(a.anilist_title_romaji) LIKE fold(?) ESCAPE '\\' \
             OR fold(a.anilist_title_english) LIKE fold(?) ESCAPE '\\')"
                .to_string(),
        );
        for _ in 0..3 {
            params.push(Value::Text(pattern.clone()));
        }
    }

    if let Some(genre) = filter.genre.as_deref().map(str::trim) {
        if !genre.is_empty() {
            conditions.push(
                "EXISTS (SELECT 1 FROM anime_genres ag JOIN genres g ON ag.genre_id = g.id \
                 WHERE ag.anime_id = a.id AND g.name = ?)"
                    .to_string(),
            );
            params.push(Value::Text(genre.to_string()));
        }
    }

    match filter.language {
        LanguageFilter::All => {}
        LanguageFilter::Dub => conditions.push("a.has_target_dub = 1".to_string()),
        LanguageFilter::Sub => conditions.push("a.has_target_sub = 1".to_string()),
        LanguageFilter::Both => {
            conditions.push("(a.has_target_dub = 1 AND a.has_target_sub = 1)".to_string())
        }
    }

    match filter.watched {
        WatchedFilter::All => {}
        WatchedFilter::Unwatched => conditions.push("COALESCE(m.watched, 0) = 0".to_string()),
        WatchedFilter::Watched => conditions.push("m.watched = 1".to_string()),
    }

    match filter.meh {
        MehFilter::Include => {}
        MehFilter::Hide => conditions.push("COALESCE(m.is_meh, 0) = 0".to_string()),
        MehFilter::Only => conditions.push("m.is_meh = 1".to_string()),
    }

    for tag in clean_names(&filter.must_tags) {
        conditions.push(
            "EXISTS (SELECT 1 FROM anime_tags at JOIN tags t ON at.tag_id = t.id \
             WHERE at.anime_id = a.id AND t.name = ?)"
                .to_string(),
        );
        params.push(Value::Text(tag));
    }

    let exclude = clean_names(&filter.exclude_tags);
    if !exclude.is_empty() {
        conditions.push(format!(
            "NOT EXISTS (SELECT 1 FROM anime_tags at JOIN tags t ON at.tag_id = t.id \
             WHERE at.anime_id = a.id AND t.name IN ({}))",
            placeholders(exclude.len())
        ));
        params.extend(exclude.into_iter().map(Value::Text));
    }

    let prefer = clean_names(&filter.prefer_tags);
    let mut order: Vec<&str> = Vec::new();
    if !prefer.is_empty() {
        order.push("prefer_matches DESC");
    }
    match filter.sort {
        SortOrder::Title => {}
        SortOrder::Score => order.push("a.anilist_score DESC"),
        SortOrder::Episodes => order.push("a.episode_count DESC"),
        SortOrder::Year => order.push("a.launch_year DESC"),
    }
    // Die ID macht die Reihenfolge bei gleichen Titeln eindeutig; ohne sie kann Offset-Paging Einträge doppeln oder auslassen
    order.push("a.title COLLATE NOCASE ASC");
    order.push("a.id ASC");

    BuiltQuery {
        where_sql: if conditions.is_empty() {
            String::new()
        } else {
            format!(" WHERE {}", conditions.join(" AND "))
        },
        where_params: params,
        order_sql: order.join(", "),
        prefer_params: prefer.into_iter().map(Value::Text).collect(),
    }
}

/// Mit `limit == 0` bleibt `items` leer, `total` ist trotzdem korrekt (reine Zählabfrage).
pub fn query_anime(
    conn: &Connection,
    store: &ImageStore,
    filter: &AnimeFilter,
    offset: u32,
    limit: u32,
) -> CoreResult<AnimePage> {
    let built = build(filter);

    let total: i64 = conn.query_row(
        &format!(
            "SELECT COUNT(*) FROM anime a {MARKS_JOIN}{}",
            built.where_sql
        ),
        params_from_iter(built.where_params.iter()),
        |row| row.get(0),
    )?;

    let prefer_select = if built.prefer_params.is_empty() {
        "0".to_string()
    } else {
        format!(
            "(SELECT COUNT(*) FROM anime_tags pat JOIN tags pt ON pat.tag_id = pt.id \
             WHERE pat.anime_id = a.id AND pt.name IN ({}))",
            placeholders(built.prefer_params.len())
        )
    };
    // prefer_matches dient nur der Sortierung und wird nicht ausgelesen
    let sql = format!(
        "SELECT a.id, a.title, \
         a.anilist_score, a.has_target_dub, a.has_target_sub, \
         COALESCE(m.watched, 0), COALESCE(m.is_meh, 0), \
         {prefer_select} AS prefer_matches, pi.hash, pi.ext, a.episode_count \
         FROM anime a {MARKS_JOIN} \
         LEFT JOIN image_refs pr ON pr.anime_id = a.id AND pr.slot = '{poster_slot}' \
         LEFT JOIN images pi ON pi.hash = pr.hash{} ORDER BY {} LIMIT ? OFFSET ?",
        built.where_sql,
        built.order_sql,
        poster_slot = ImageSlot::PosterTall.as_str()
    );

    // Reihenfolge der Platzhalter: Auswahlliste, WHERE, LIMIT/OFFSET.
    let mut args: Vec<Value> = built.prefer_params;
    args.extend(built.where_params);
    args.push(Value::Integer(i64::from(limit)));
    args.push(Value::Integer(i64::from(offset)));

    let mut stmt = conn.prepare(&sql)?;
    let items = stmt
        .query_map(params_from_iter(args.iter()), |row| {
            Ok(AnimeSummary {
                id: row.get(0)?,
                title: row.get(1)?,
                poster_path: poster_path(store, row.get(8)?, row.get(9)?),
                anilist_score: row.get(2)?,
                has_target_dub: row.get(3)?,
                has_target_sub: row.get(4)?,
                watched: row.get(5)?,
                is_meh: row.get(6)?,
                episode_count: row.get(10)?,
            })
        })?
        .collect::<Result<Vec<_>, _>>()?;

    Ok(AnimePage { items, total })
}

fn poster_path(store: &ImageStore, hash: Option<String>, ext: Option<String>) -> String {
    match (hash, ext) {
        (Some(hash), Some(ext)) => store.path(&hash, &ext).to_string_lossy().into_owned(),
        _ => String::new(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::db::test_support::{insert_all, AnimeFixture};
    use crate::db::Database;

    fn fixtures() -> Vec<AnimeFixture> {
        vec![
            AnimeFixture::new("a1", "Über die Welt")
                .score(80)
                .episodes(12)
                .year(2020)
                .dub()
                .genre("Drama")
                .tag("Schule", 90)
                .tag("Magie", 70),
            AnimeFixture::new("a2", "100% Liebe")
                .score(70)
                .episodes(24)
                .year(2018)
                .sub()
                .genre("Romanze")
                .tag("Schule", 60),
            AnimeFixture::new("a3", "A_B")
                .score(70)
                .episodes(24)
                .year(2022)
                .dub()
                .sub()
                .watched()
                .genre("Drama")
                .tag("Magie", 50)
                .tag("Schule", 50),
            AnimeFixture::new("a4", "AxB")
                .score(90)
                .episodes(6)
                .year(2019),
            AnimeFixture::new("a5", "Zeta")
                .romaji("Zetto no Monogatari")
                .english("Tale of Zed")
                .meh()
                .watched()
                .sub()
                .genre("Drama")
                .tag("Magie", 99),
            AnimeFixture::new("a7", "alpha").score(90),
            AnimeFixture::new("a6", "beta")
                .score(60)
                .episodes(12)
                .year(2020),
        ]
    }

    // Es werden keine Dateien geschrieben; das Verzeichnis dient nur der Pfadbildung
    fn store() -> ImageStore {
        ImageStore::new(std::env::temp_dir().join("crunchymate-query-unit")).unwrap()
    }

    fn db() -> Database {
        let db = Database::open_in_memory().unwrap();
        insert_all(&db, &fixtures()).unwrap();
        db
    }

    fn base() -> AnimeFilter {
        AnimeFilter {
            query: String::new(),
            genre: None,
            language: LanguageFilter::All,
            watched: WatchedFilter::All,
            meh: MehFilter::Hide,
            sort: SortOrder::Title,
            prefer_tags: vec![],
            must_tags: vec![],
            exclude_tags: vec![],
        }
    }

    fn run(db: &Database, filter: &AnimeFilter) -> AnimePage {
        db.with_conn(|c| query_anime(c, &store(), filter, 0, 100))
            .unwrap()
    }

    fn ids(db: &Database, filter: &AnimeFilter) -> Vec<String> {
        run(db, filter).items.into_iter().map(|i| i.id).collect()
    }

    fn strs(v: &[&str]) -> Vec<String> {
        v.iter().map(|s| s.to_string()).collect()
    }

    #[test]
    fn default_filter_hides_meh_and_sorts_by_title() {
        let page = run(&db(), &base());
        assert_eq!(page.total, 6);
        let titles: Vec<_> = page.items.iter().map(|i| i.title.as_str()).collect();
        assert_eq!(
            titles,
            ["100% Liebe", "A_B", "alpha", "AxB", "beta", "Über die Welt"]
        );
        assert!(page.items.iter().all(|i| i.poster_path.is_empty()));
    }

    #[test]
    fn text_search_matches_title_romaji_and_english() {
        let db = db();
        let mut f = base();
        f.meh = MehFilter::Include;
        f.query = "  zetto ".into();
        assert_eq!(ids(&db, &f), ["a5"]);
        f.query = "tale of".into();
        assert_eq!(ids(&db, &f), ["a5"]);
        f.query = "zeta".into();
        assert_eq!(ids(&db, &f), ["a5"]);
        f.query = "   ".into();
        assert_eq!(ids(&db, &f).len(), 7);
    }

    #[test]
    fn text_search_is_case_insensitive_for_umlauts() {
        let db = db();
        let mut f = base();
        f.query = "über".into();
        assert_eq!(ids(&db, &f), ["a1"]);
        f.query = "ÜBER".into();
        assert_eq!(ids(&db, &f), ["a1"]);
    }

    #[test]
    fn text_search_treats_wildcards_literally() {
        let db = db();
        let mut f = base();
        f.query = "100%".into();
        assert_eq!(ids(&db, &f), ["a2"]);
        f.query = "A_B".into();
        assert_eq!(ids(&db, &f), ["a3"]);
        f.query = "\\".into();
        assert!(ids(&db, &f).is_empty());
        f.query = "%".into();
        assert_eq!(ids(&db, &f), ["a2"]);
    }

    #[test]
    fn genre_filter() {
        let db = db();
        let mut f = base();
        f.genre = Some("Drama".into());
        assert_eq!(ids(&db, &f), ["a3", "a1"]);
        f.genre = Some("Unbekannt".into());
        assert_eq!(run(&db, &f).total, 0);
    }

    #[test]
    fn must_tags_require_all() {
        let db = db();
        let mut f = base();
        f.must_tags = strs(&["Schule", " Magie "]);
        assert_eq!(ids(&db, &f), ["a3", "a1"]);
        f.must_tags = strs(&["Schule", "  "]);
        assert_eq!(ids(&db, &f), ["a2", "a3", "a1"]);
    }

    #[test]
    fn exclude_tags_remove_any_match() {
        let db = db();
        let mut f = base();
        f.exclude_tags = strs(&["Magie", ""]);
        assert_eq!(ids(&db, &f), ["a2", "a7", "a4", "a6"]);
        f.exclude_tags = strs(&["Magie", "Schule"]);
        assert_eq!(ids(&db, &f), ["a7", "a4", "a6"]);
    }

    #[test]
    fn unknown_must_tag_returns_empty_page_without_error() {
        let page = run(&db(), &{
            let mut f = base();
            f.must_tags = strs(&["Gibtsnicht"]);
            f
        });
        assert_eq!(page.total, 0);
        assert!(page.items.is_empty());
    }

    #[test]
    fn prefer_tags_rank_first_then_sort_then_title() {
        let db = db();
        let mut f = base();
        f.sort = SortOrder::Score;
        f.prefer_tags = strs(&["Schule", "Magie"]);
        // a1 und a3: 2 Treffer (80 vor 70); a2: 1 Treffer; 0 Treffer: a7 und a4
        // (beide 90, Titelgleichstand alpha vor AxB), dann a6 (60).
        assert_eq!(ids(&db, &f), ["a1", "a3", "a2", "a7", "a4", "a6"]);
        // Gleiche Trefferzahl und gleiche Wertung (a2, a3: je 1 Treffer, 70): Titel entscheidet.
        f.prefer_tags = strs(&["Schule"]);
        assert_eq!(ids(&db, &f), ["a1", "a2", "a3", "a7", "a4", "a6"]);
        f.sort = SortOrder::Title;
        f.prefer_tags = strs(&["Magie"]);
        assert_eq!(ids(&db, &f), ["a3", "a1", "a2", "a7", "a4", "a6"]);
    }

    #[test]
    fn episode_count_reported_per_item() {
        let db = db();
        let counts: Vec<_> = run(&db, &base())
            .items
            .iter()
            .filter(|i| i.id == "a1" || i.id == "a2")
            .map(|i| (i.id.clone(), i.episode_count))
            .collect();
        assert_eq!(counts, [("a2".to_string(), 24), ("a1".to_string(), 12)]);
    }

    #[test]
    fn prefer_tags_order_by_match_count_within_title_sort() {
        let db = db();
        let mut f = base();
        f.prefer_tags = strs(&["Schule", "Magie"]);
        // a3 und a1 je 2 Treffer (Titel A_B vor Über), a2 einer, dann ohne Treffer nach Titel
        assert_eq!(ids(&db, &f), ["a3", "a1", "a2", "a7", "a4", "a6"]);
        f.prefer_tags = vec![];
        assert_eq!(ids(&db, &f), ids(&db, &base()));
    }

    #[test]
    fn watched_filters() {
        let db = db();
        let mut f = base();
        f.meh = MehFilter::Include;
        f.watched = WatchedFilter::All;
        assert_eq!(ids(&db, &f).len(), 7);
        f.watched = WatchedFilter::Unwatched;
        assert_eq!(ids(&db, &f), ["a2", "a7", "a4", "a6", "a1"]);
        f.watched = WatchedFilter::Watched;
        assert_eq!(ids(&db, &f), ["a3", "a5"]);
    }

    #[test]
    fn meh_filters() {
        let db = db();
        let mut f = base();
        f.meh = MehFilter::Hide;
        assert_eq!(ids(&db, &f).len(), 6);
        f.meh = MehFilter::Only;
        assert_eq!(ids(&db, &f), ["a5"]);
        f.meh = MehFilter::Include;
        assert_eq!(ids(&db, &f).len(), 7);
    }

    #[test]
    fn language_filters() {
        let db = db();
        let mut f = base();
        f.meh = MehFilter::Include;
        f.language = LanguageFilter::All;
        assert_eq!(ids(&db, &f).len(), 7);
        f.language = LanguageFilter::Dub;
        assert_eq!(ids(&db, &f), ["a3", "a1"]);
        f.language = LanguageFilter::Sub;
        assert_eq!(ids(&db, &f), ["a2", "a3", "a5"]);
        f.language = LanguageFilter::Both;
        assert_eq!(ids(&db, &f), ["a3"]);
    }

    #[test]
    fn sort_orders_descending_with_title_tiebreak() {
        let db = db();
        let mut f = base();
        f.sort = SortOrder::Score;
        assert_eq!(ids(&db, &f), ["a7", "a4", "a1", "a2", "a3", "a6"]);
        f.sort = SortOrder::Episodes;
        assert_eq!(ids(&db, &f), ["a2", "a3", "a6", "a1", "a4", "a7"]);
        f.sort = SortOrder::Year;
        assert_eq!(ids(&db, &f), ["a3", "a6", "a1", "a4", "a2", "a7"]);
    }

    #[test]
    fn equal_titles_are_ordered_by_id() {
        let db = Database::open_in_memory().unwrap();
        insert_all(
            &db,
            &[
                AnimeFixture::new("b2", "Gleich"),
                AnimeFixture::new("b1", "gleich"),
            ],
        )
        .unwrap();
        assert_eq!(ids(&db, &base()), ["b1", "b2"]);
    }

    #[test]
    fn paging_returns_slice_and_full_total() {
        let db = db();
        let page = db
            .with_conn(|c| query_anime(c, &store(), &base(), 2, 2))
            .unwrap();
        let got: Vec<_> = page.items.iter().map(|i| i.id.as_str()).collect();
        assert_eq!(got, ["a7", "a4"]);
        assert_eq!(page.total, 6);
    }

    #[test]
    fn limit_zero_returns_total_only() {
        let db = db();
        let page = db
            .with_conn(|c| query_anime(c, &store(), &base(), 0, 0))
            .unwrap();
        assert!(page.items.is_empty());
        assert_eq!(page.total, 6);
    }

    #[test]
    fn tab_watched_includes_meh() {
        let db = db();
        let mut f = base();
        f.watched = WatchedFilter::Watched;
        f.meh = MehFilter::Include;
        assert_eq!(ids(&db, &f), ["a3", "a5"]);
    }

    #[test]
    fn tab_meh_only_meh() {
        let db = db();
        let mut f = base();
        f.meh = MehFilter::Only;
        assert_eq!(ids(&db, &f), ["a5"]);
    }
}
