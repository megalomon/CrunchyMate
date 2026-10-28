mod common;

use std::sync::Mutex;
use std::time::Duration;

use crunchymate_core::db::anime::{apply_metadata, stats, upsert_catalog_page};
use crunchymate_core::db::sync_state::status;
use crunchymate_core::db::Database;
use crunchymate_core::error::{CoreError, CoreResult, ErrorCode};
use crunchymate_core::model::{
    CatalogItem, CatalogPage, MetadataItem, SyncKind, SyncPhase, SyncProgress, SyncRunState,
    SyncSummary,
};
use crunchymate_core::remote::catalog_source::CatalogSource;
use crunchymate_core::remote::metadata_source::MetadataSource;
use crunchymate_core::sync::catalog_sync::run_catalog_sync;
use crunchymate_core::sync::config::SyncConfig;
use crunchymate_core::sync::enrichment::run_enrichment;
use crunchymate_core::sync::progress::SyncObserver;

use common::{FakeFetcher, TempDir};

// Testkonfiguration ohne Pausen
fn fast() -> SyncConfig {
    SyncConfig {
        catalog_page_size: 50,
        catalog_page_delay: Duration::ZERO,
        metadata_batch_size: 10,
        metadata_batch_delay: Duration::ZERO,
    }
}

fn item(id: &str) -> CatalogItem {
    CatalogItem {
        id: id.to_string(),
        title: format!("Titel {id}"),
        description: String::new(),
        poster_tall: String::new(),
        poster_wide: String::new(),
        episode_count: 12,
        launch_year: 2020,
        is_subbed: true,
        is_dubbed: false,
        has_target_sub: true,
        has_target_dub: false,
        genres: vec!["Action".to_string()],
    }
}

fn items(from: usize, count: usize) -> Vec<CatalogItem> {
    (from..from + count)
        .map(|i| item(&format!("a{i:03}")))
        .collect()
}

fn metadata(id: i64) -> MetadataItem {
    MetadataItem {
        anilist_id: id,
        title_romaji: format!("Romaji {id}"),
        title_english: String::new(),
        description: String::new(),
        score: 80,
        status: "FINISHED".to_string(),
        banner_image: String::new(),
        site_url: String::new(),
        genres: Vec::new(),
        tags: Vec::new(),
    }
}

/// Verhalten einer einzelnen Katalogseite im Fake.
enum PageReply {
    Page(CatalogPage),
    Fail(CoreError),
    /// Wartet unbegrenzt (für den Abbruchtest).
    Block,
}

struct FakeCatalog {
    country: String,
    replies: Mutex<Vec<PageReply>>,
    calls: Mutex<Vec<(u32, u32, String)>>,
}

impl FakeCatalog {
    fn new(replies: Vec<PageReply>) -> Self {
        Self {
            country: "DE".to_string(),
            replies: Mutex::new(replies),
            calls: Mutex::new(Vec::new()),
        }
    }

    fn pages(total: u32, sizes: &[usize]) -> Self {
        let mut from = 0;
        let replies = sizes
            .iter()
            .map(|&size| {
                let page = PageReply::Page(CatalogPage {
                    total,
                    items: items(from, size),
                });
                from += size;
                page
            })
            .collect();
        Self::new(replies)
    }
}

#[async_trait::async_trait]
impl CatalogSource for FakeCatalog {
    async fn session_country(&self) -> CoreResult<String> {
        Ok(self.country.clone())
    }

    async fn fetch_page(&self, start: u32, count: u32, locale: &str) -> CoreResult<CatalogPage> {
        self.calls
            .lock()
            .unwrap()
            .push((start, count, locale.to_string()));
        let reply = {
            let mut replies = self.replies.lock().unwrap();
            if replies.is_empty() {
                PageReply::Page(CatalogPage {
                    total: 0,
                    items: Vec::new(),
                })
            } else {
                replies.remove(0)
            }
        };
        match reply {
            PageReply::Page(page) => Ok(page),
            PageReply::Fail(error) => Err(error),
            PageReply::Block => {
                // Notify, das nie ausgelöst wird: die Future bleibt hängen
                let never = tokio::sync::Notify::new();
                never.notified().await;
                unreachable!("Notify wird nie ausgelöst")
            }
        }
    }
}

#[derive(Default)]
struct Recorder {
    seen: Mutex<Vec<SyncProgress>>,
}

impl Recorder {
    fn pairs(&self) -> Vec<(i64, i64)> {
        self.seen
            .lock()
            .unwrap()
            .iter()
            .map(|p| (p.done, p.total))
            .collect()
    }
}

impl SyncObserver for Recorder {
    fn on_progress(&self, progress: SyncProgress) {
        self.seen.lock().unwrap().push(progress);
    }
}

struct FakeMetadata {
    // Antwort je Aufruf; fehlt sie, gibt es für jeden Titel einen Treffer
    failures: Mutex<Vec<Option<CoreError>>>,
    calls: Mutex<Vec<Vec<String>>>,
}

impl FakeMetadata {
    fn new(failures: Vec<Option<CoreError>>) -> Self {
        Self {
            failures: Mutex::new(failures),
            calls: Mutex::new(Vec::new()),
        }
    }

    fn calls(&self) -> Vec<Vec<String>> {
        self.calls.lock().unwrap().clone()
    }
}

#[async_trait::async_trait]
impl MetadataSource for FakeMetadata {
    async fn search_batch(&self, titles: &[String]) -> CoreResult<Vec<Option<MetadataItem>>> {
        let call_index = {
            let mut calls = self.calls.lock().unwrap();
            calls.push(titles.to_vec());
            calls.len()
        };
        let failure = {
            let mut failures = self.failures.lock().unwrap();
            if failures.is_empty() {
                None
            } else {
                failures.remove(0)
            }
        };
        if let Some(error) = failure {
            return Err(error);
        }
        Ok(titles
            .iter()
            .enumerate()
            .map(|(i, _)| Some(metadata((call_index * 100 + i) as i64)))
            .collect())
    }
}

fn network() -> CoreError {
    CoreError::Network {
        detail: "weg".to_string(),
    }
}

fn seed(db: &Database, count: usize) {
    db.with_tx(|tx| upsert_catalog_page(tx, &items(0, count)))
        .unwrap();
}

fn anime_total(db: &Database) -> i64 {
    db.with_conn(stats).unwrap().total
}

#[tokio::test]
async fn catalog_sync_upserts_all_pages_and_reports_progress() {
    let dir = TempDir::new("sync");
    let db = Database::open_in_memory().unwrap();
    let source = FakeCatalog::pages(120, &[50, 50, 20]);
    let observer = Recorder::default();

    let summary = run_catalog_sync(
        &db,
        &source,
        &dir.store(),
        &FakeFetcher::default(),
        "de-DE",
        &fast(),
        &observer,
    )
    .await
    .unwrap();

    assert_eq!(anime_total(&db), 120);
    assert_eq!(summary.processed, 120);
    assert_eq!(observer.pairs(), vec![(50, 120), (100, 120), (120, 120)]);
    assert!(observer
        .seen
        .lock()
        .unwrap()
        .iter()
        .all(|p| p.kind == SyncKind::Catalog && p.phase == SyncPhase::Items));
    let calls = source.calls.lock().unwrap().clone();
    assert_eq!(
        calls,
        vec![
            (0, 50, "de-DE".to_string()),
            (50, 50, "de-DE".to_string()),
            (100, 50, "de-DE".to_string()),
        ]
    );
    let s = db.with_conn(|c| status(c, SyncKind::Catalog)).unwrap();
    assert_eq!(s.state, SyncRunState::Completed);
    assert_eq!((s.done, s.total), (120, 120));
    assert_eq!(s.last_error, None);
    assert!(s.last_finished_at.is_some());
}

#[tokio::test]
async fn catalog_sync_stores_country() {
    let dir = TempDir::new("sync");
    let db = Database::open_in_memory().unwrap();
    let source = FakeCatalog::pages(10, &[10]);
    run_catalog_sync(
        &db,
        &source,
        &dir.store(),
        &FakeFetcher::default(),
        "de-DE",
        &fast(),
        &Recorder::default(),
    )
    .await
    .unwrap();
    let s = db.with_conn(|c| status(c, SyncKind::Catalog)).unwrap();
    assert_eq!(s.catalog_country.as_deref(), Some("DE"));
}

#[tokio::test]
async fn catalog_sync_stops_on_empty_page() {
    let dir = TempDir::new("sync");
    let db = Database::open_in_memory().unwrap();
    // total behauptet 200, aber Seite 2 ist leer
    let source = FakeCatalog::pages(200, &[50, 0, 50]);
    let observer = Recorder::default();
    run_catalog_sync(
        &db,
        &source,
        &dir.store(),
        &FakeFetcher::default(),
        "de-DE",
        &fast(),
        &observer,
    )
    .await
    .unwrap();
    assert_eq!(source.calls.lock().unwrap().len(), 2);
    assert_eq!(anime_total(&db), 50);
    assert_eq!(observer.pairs(), vec![(50, 200)]);
    let s = db.with_conn(|c| status(c, SyncKind::Catalog)).unwrap();
    assert_eq!(s.state, SyncRunState::Completed);
}

#[tokio::test]
async fn catalog_sync_failure_marks_failed_and_keeps_saved_pages() {
    let dir = TempDir::new("sync");
    let db = Database::open_in_memory().unwrap();
    let source = FakeCatalog::new(vec![
        PageReply::Page(CatalogPage {
            total: 120,
            items: items(0, 50),
        }),
        PageReply::Fail(network()),
    ]);
    let result = run_catalog_sync(
        &db,
        &source,
        &dir.store(),
        &FakeFetcher::default(),
        "de-DE",
        &fast(),
        &Recorder::default(),
    )
    .await;
    assert!(matches!(result, Err(CoreError::Network { .. })));
    assert_eq!(anime_total(&db), 50);
    let s = db.with_conn(|c| status(c, SyncKind::Catalog)).unwrap();
    assert_eq!(s.state, SyncRunState::Failed);
    assert_eq!(s.last_error, Some(ErrorCode::Network));
    assert_eq!((s.done, s.total), (50, 120));
}

#[tokio::test]
async fn dropping_sync_future_marks_cancelled_and_keeps_pages() {
    let dir = TempDir::new("sync");
    let db = Database::open_in_memory().unwrap();
    let source = FakeCatalog::new(vec![
        PageReply::Page(CatalogPage {
            total: 120,
            items: items(0, 50),
        }),
        PageReply::Block,
    ]);
    let observer = Recorder::default();
    let config = fast();
    let store = dir.store();
    let fetcher = FakeFetcher::default();
    {
        let future = run_catalog_sync(&db, &source, &store, &fetcher, "de-DE", &config, &observer);
        tokio::pin!(future);
        // Die Future läuft bis zur blockierenden Seite 2; dann wird sie verworfen.
        tokio::select! {
            _ = &mut future => panic!("Sync darf nicht abschließen"),
            _ = async {
                while source.calls.lock().unwrap().len() < 2 {
                    tokio::task::yield_now().await;
                }
            } => {}
        }
    }
    assert_eq!(anime_total(&db), 50);
    assert_eq!(observer.pairs(), vec![(50, 120)]);
    let s = db.with_conn(|c| status(c, SyncKind::Catalog)).unwrap();
    assert_eq!(s.state, SyncRunState::Cancelled);
    assert_eq!(s.last_error, Some(ErrorCode::Cancelled));
    assert!(s.last_finished_at.is_some());
}

#[tokio::test]
async fn enrichment_processes_only_unenriched() {
    let dir = TempDir::new("sync");
    let db = Database::open_in_memory().unwrap();
    seed(&db, 3);
    db.with_tx(|tx| apply_metadata(tx, "a001", &metadata(1)))
        .unwrap();
    let source = FakeMetadata::new(Vec::new());
    let observer = Recorder::default();

    let summary = run_enrichment(
        &db,
        &source,
        &dir.store(),
        &FakeFetcher::default(),
        &fast(),
        &observer,
    )
    .await
    .unwrap();

    assert_eq!(
        source.calls(),
        vec![vec!["Titel a000".to_string(), "Titel a002".to_string()]]
    );
    assert_eq!(
        summary,
        SyncSummary {
            processed: 2,
            failed_batches: 0,
            failed_images: 0
        }
    );
    assert_eq!(observer.pairs(), vec![(2, 2)]);
    assert!(observer
        .seen
        .lock()
        .unwrap()
        .iter()
        .all(|p| p.kind == SyncKind::Metadata));
    let s = db.with_conn(|c| status(c, SyncKind::Metadata)).unwrap();
    assert_eq!(s.state, SyncRunState::Completed);
    assert_eq!((s.done, s.total), (2, 2));
    assert_eq!(db.with_conn(stats).unwrap().enriched, 3);
}

#[tokio::test]
async fn enrichment_tolerates_failed_batch() {
    let dir = TempDir::new("sync");
    let db = Database::open_in_memory().unwrap();
    seed(&db, 15);
    let source = FakeMetadata::new(vec![Some(network()), None]);
    let observer = Recorder::default();

    let summary = run_enrichment(
        &db,
        &source,
        &dir.store(),
        &FakeFetcher::default(),
        &fast(),
        &observer,
    )
    .await
    .unwrap();

    assert_eq!(
        summary,
        SyncSummary {
            processed: 5,
            failed_batches: 1,
            failed_images: 0
        }
    );
    assert_eq!(observer.pairs(), vec![(10, 15), (15, 15)]);
    let s = db.with_conn(|c| status(c, SyncKind::Metadata)).unwrap();
    assert_eq!(s.state, SyncRunState::Completed);
    assert_eq!(db.with_conn(stats).unwrap().enriched, 5);
}

#[tokio::test]
async fn enrichment_aborts_on_rate_limit() {
    let dir = TempDir::new("sync");
    let db = Database::open_in_memory().unwrap();
    seed(&db, 25);
    let source = FakeMetadata::new(vec![
        None,
        Some(CoreError::RateLimited {
            retry_after_secs: 60,
        }),
    ]);

    let result = run_enrichment(
        &db,
        &source,
        &dir.store(),
        &FakeFetcher::default(),
        &fast(),
        &Recorder::default(),
    )
    .await;

    assert!(matches!(result, Err(CoreError::RateLimited { .. })));
    assert_eq!(source.calls().len(), 2);
    let s = db.with_conn(|c| status(c, SyncKind::Metadata)).unwrap();
    assert_eq!(s.state, SyncRunState::Failed);
    assert_eq!(s.last_error, Some(ErrorCode::RateLimited));
    // Der erste Batch bleibt gespeichert
    assert_eq!(db.with_conn(stats).unwrap().enriched, 10);
}

#[tokio::test]
async fn enrichment_uses_clean_batches_of_configured_size() {
    let dir = TempDir::new("sync");
    let db = Database::open_in_memory().unwrap();
    seed(&db, 25);
    let source = FakeMetadata::new(Vec::new());
    let observer = Recorder::default();

    run_enrichment(
        &db,
        &source,
        &dir.store(),
        &FakeFetcher::default(),
        &fast(),
        &observer,
    )
    .await
    .unwrap();

    let sizes: Vec<usize> = source.calls().iter().map(Vec::len).collect();
    assert_eq!(sizes, vec![10, 10, 5]);
    assert_eq!(observer.pairs(), vec![(10, 25), (20, 25), (25, 25)]);
}

#[test]
fn default_config_matches_spec() {
    let config = SyncConfig::default();
    assert_eq!(config.catalog_page_size, 50);
    assert_eq!(config.catalog_page_delay, Duration::from_millis(100));
    assert_eq!(config.metadata_batch_size, 10);
    assert_eq!(config.metadata_batch_delay, Duration::from_millis(2100));
}
