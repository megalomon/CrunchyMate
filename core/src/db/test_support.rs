//! Fixture-Builder für Tests: baut Datensätze über die echten Schreibfunktionen auf.

use crate::db::{anime, Database};
use crate::error::CoreResult;
use crate::model::{CatalogItem, MetadataItem, TagRank};

const FIXTURE_NOW: i64 = 1_700_000_000;

#[derive(Debug, Clone)]
pub struct AnimeFixture {
    id: String,
    title: String,
    description: String,
    poster_tall: String,
    poster_wide: String,
    episodes: u32,
    year: u32,
    is_subbed: bool,
    is_dubbed: bool,
    has_target_sub: bool,
    has_target_dub: bool,
    genres: Vec<String>,
    watched: bool,
    meh: bool,
    score: Option<u32>,
    romaji: Option<String>,
    english: Option<String>,
    banner: Option<String>,
    tags: Vec<TagRank>,
}

impl AnimeFixture {
    pub fn new(id: &str, title: &str) -> Self {
        Self {
            id: id.to_string(),
            title: title.to_string(),
            description: String::new(),
            poster_tall: format!("tall-{id}"),
            poster_wide: format!("wide-{id}"),
            episodes: 0,
            year: 0,
            is_subbed: false,
            is_dubbed: false,
            has_target_sub: false,
            has_target_dub: false,
            genres: Vec::new(),
            watched: false,
            meh: false,
            score: None,
            romaji: None,
            english: None,
            banner: None,
            tags: Vec::new(),
        }
    }

    pub fn no_poster_tall(mut self) -> Self {
        self.poster_tall = String::new();
        self
    }

    pub fn episodes(mut self, count: u32) -> Self {
        self.episodes = count;
        self
    }

    pub fn year(mut self, year: u32) -> Self {
        self.year = year;
        self
    }

    /// Synchronfassung in der Zielsprache vorhanden.
    pub fn dub(mut self) -> Self {
        self.is_dubbed = true;
        self.has_target_dub = true;
        self
    }

    /// Untertitelfassung in der Zielsprache vorhanden.
    pub fn sub(mut self) -> Self {
        self.is_subbed = true;
        self.has_target_sub = true;
        self
    }

    pub fn watched(mut self) -> Self {
        self.watched = true;
        self
    }

    pub fn meh(mut self) -> Self {
        self.meh = true;
        self
    }

    pub fn genre(mut self, name: &str) -> Self {
        self.genres.push(name.to_string());
        self
    }

    pub fn tag(mut self, name: &str, rank: i32) -> Self {
        self.tags.push(TagRank {
            name: name.to_string(),
            rank,
        });
        self
    }

    /// AniList-Wertung; markiert den Eintrag als angereichert.
    pub fn score(mut self, score: u32) -> Self {
        self.score = Some(score);
        self
    }

    pub fn romaji(mut self, title: &str) -> Self {
        self.romaji = Some(title.to_string());
        self
    }

    pub fn english(mut self, title: &str) -> Self {
        self.english = Some(title.to_string());
        self
    }

    pub fn banner(mut self, url: &str) -> Self {
        self.banner = Some(url.to_string());
        self
    }

    fn is_enriched(&self) -> bool {
        self.score.is_some()
            || self.romaji.is_some()
            || self.english.is_some()
            || self.banner.is_some()
            || !self.tags.is_empty()
    }

    fn catalog_item(&self) -> CatalogItem {
        CatalogItem {
            id: self.id.clone(),
            title: self.title.clone(),
            description: self.description.clone(),
            poster_tall: self.poster_tall.clone(),
            poster_wide: self.poster_wide.clone(),
            episode_count: self.episodes,
            launch_year: self.year,
            is_subbed: self.is_subbed,
            is_dubbed: self.is_dubbed,
            has_target_sub: self.has_target_sub,
            has_target_dub: self.has_target_dub,
            genres: self.genres.clone(),
        }
    }

    fn metadata_item(&self, anilist_id: i64) -> MetadataItem {
        MetadataItem {
            anilist_id,
            title_romaji: self.romaji.clone().unwrap_or_default(),
            title_english: self.english.clone().unwrap_or_default(),
            description: String::new(),
            score: self.score.unwrap_or(0),
            status: String::new(),
            banner_image: self.banner.clone().unwrap_or_default(),
            site_url: String::new(),
            genres: Vec::new(),
            tags: self.tags.clone(),
        }
    }
}

/// Legt alle Fixtures an. Angereicherte Einträge erhalten eine eindeutige AniList-ID.
pub fn insert_all(db: &Database, fixtures: &[AnimeFixture]) -> CoreResult<()> {
    let items: Vec<CatalogItem> = fixtures.iter().map(AnimeFixture::catalog_item).collect();
    db.with_tx(|tx| anime::upsert_catalog_page(tx, &items))?;
    for (index, fixture) in fixtures.iter().enumerate() {
        if fixture.is_enriched() {
            let metadata = fixture.metadata_item(index as i64 + 1);
            db.with_tx(|tx| anime::apply_metadata(tx, &fixture.id, &metadata))?;
        }
        db.with_conn(|conn| {
            if fixture.watched {
                anime::set_watched(conn, &fixture.id, true, FIXTURE_NOW)?;
            }
            if fixture.meh {
                anime::set_meh(conn, &fixture.id, true, FIXTURE_NOW)?;
            }
            Ok(())
        })?;
    }
    Ok(())
}
