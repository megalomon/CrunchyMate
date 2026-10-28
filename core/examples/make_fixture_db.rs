//! Erzeugt eine kleine Fixture-Datenbank für die Smoke-Tests auf dem Gerät.
//! Aufruf: cargo run -p crunchymate-core --example make_fixture_db -- <ziel.db>

use crunchymate_core::db::{anime, Database};
use crunchymate_core::model::{CatalogItem, MetadataItem, TagRank};

const ENRICHED_ID: &str = "fixture-5";
const ENRICHED_ANILIST_ID: i64 = 105;
const ENRICHED_SCORE: u32 = 80;
const TAG_RANK_HIGH: i32 = 90;
const TAG_RANK_LOW: i32 = 60;
const EPISODE_COUNT: u32 = 12;
const LAUNCH_YEAR: u32 = 2020;
const FIXTURE_TITLES: [&str; 5] = [
    "Fixture Alpha",
    "Fixture Beta",
    "Fixture Gamma",
    "Fixture Delta",
    "Fixture Enriched",
];

fn catalog_item(index: usize, title: &str) -> CatalogItem {
    let dubbed = index.is_multiple_of(2);
    CatalogItem {
        id: format!("fixture-{}", index + 1),
        title: title.to_string(),
        description: format!("Beschreibung von {title}."),
        poster_tall: String::new(),
        poster_wide: String::new(),
        episode_count: EPISODE_COUNT,
        launch_year: LAUNCH_YEAR,
        is_subbed: true,
        is_dubbed: dubbed,
        has_target_sub: true,
        has_target_dub: dubbed,
        genres: vec!["Drama".to_string()],
    }
}

fn enriched_metadata() -> MetadataItem {
    MetadataItem {
        anilist_id: ENRICHED_ANILIST_ID,
        title_romaji: String::new(),
        title_english: String::new(),
        description: "Angereicherte Beschreibung.".to_string(),
        score: ENRICHED_SCORE,
        status: "FINISHED".to_string(),
        banner_image: String::new(),
        site_url: String::new(),
        genres: Vec::new(),
        tags: vec![
            TagRank {
                name: "Action".to_string(),
                rank: TAG_RANK_HIGH,
            },
            TagRank {
                name: "Isekai".to_string(),
                rank: TAG_RANK_LOW,
            },
        ],
    }
}

fn main() {
    let target = std::env::args()
        .nth(1)
        .expect("Zielpfad der Fixture-Datenbank fehlt");
    // Eine alte Datei samt WAL-Resten würde sonst Einträge mischen
    for suffix in ["", "-wal", "-shm"] {
        let _ = std::fs::remove_file(format!("{target}{suffix}"));
    }
    let db = Database::open(&target).expect("Datenbank konnte nicht angelegt werden");
    let items: Vec<CatalogItem> = FIXTURE_TITLES
        .iter()
        .enumerate()
        .map(|(index, title)| catalog_item(index, title))
        .collect();
    db.with_tx(|tx| anime::upsert_catalog_page(tx, &items))
        .expect("Katalog konnte nicht geschrieben werden");
    db.with_tx(|tx| anime::apply_metadata(tx, ENRICHED_ID, &enriched_metadata()))
        .expect("Metadaten konnten nicht geschrieben werden");
    // Die Datei wird als einzelnes Asset ausgeliefert; ein WAL daneben ginge verloren
    db.with_conn(|conn| {
        conn.pragma_update(None, "journal_mode", "DELETE")?;
        Ok(())
    })
    .expect("Journal-Modus konnte nicht umgestellt werden");
}
