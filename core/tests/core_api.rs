mod common;

use std::sync::{Arc, Mutex};
use std::time::Duration;

use crunchymate_core::core_api::Core;
use crunchymate_core::db::sync_state::mark_running;
use crunchymate_core::db::Database;
use crunchymate_core::error::{CoreError, CoreResult};
use crunchymate_core::images::store::ImageStore;
use crunchymate_core::model::{
    AnimeFilter, CatalogItem, CatalogPage, LanguageFilter, MehFilter, MetadataItem, Settings,
    SortOrder, SyncKind, SyncProgress, SyncRunState, WatchedFilter,
};
use crunchymate_core::remote::catalog_source::CatalogSource;
use crunchymate_core::remote::metadata_source::MetadataSource;
use crunchymate_core::sync::config::SyncConfig;
use crunchymate_core::sync::progress::SyncObserver;

use common::{FakeFetcher, TempDir};

// Testkonfiguration ohne Pausen
fn fast() -> SyncConfig {
    SyncConfig {
        catalog_page_size: 50,
        catalog_page_delay: Duration::ZERO,
        metadata_batch_size: 10,
        metadata_batch_delay: Duration::ZERO,
        ..SyncConfig::default()
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

/// Liefert eine einzige Seite mit festen Einträgen und merkt sich die Locales.
struct FakeCatalog {
    items: Vec<CatalogItem>,
    locales: Mutex<Vec<String>>,
}

impl FakeCatalog {
    fn new(ids: &[&str]) -> Self {
        Self {
            items: ids.iter().map(|id| item(id)).collect(),
            locales: Mutex::new(Vec::new()),
        }
    }
}

#[async_trait::async_trait]
impl CatalogSource for FakeCatalog {
    async fn session_country(&self) -> CoreResult<String> {
        Ok("DE".to_string())
    }

    async fn fetch_page(&self, start: u32, _count: u32, locale: &str) -> CoreResult<CatalogPage> {
        self.locales.lock().unwrap().push(locale.to_string());
        let total = u32::try_from(self.items.len()).unwrap();
        let items = if start == 0 {
            self.items.clone()
        } else {
            Vec::new()
        };
        Ok(CatalogPage { total, items })
    }
}

/// Findet nie einen Treffer.
struct EmptyMetadata;

#[async_trait::async_trait]
impl MetadataSource for EmptyMetadata {
    async fn search_batch(&self, titles: &[String]) -> CoreResult<Vec<Option<MetadataItem>>> {
        Ok(vec![None; titles.len()])
    }
}

#[derive(Default)]
struct RecordingObserver {
    events: Mutex<Vec<SyncProgress>>,
}

impl SyncObserver for RecordingObserver {
    fn on_progress(&self, progress: SyncProgress) {
        self.events.lock().unwrap().push(progress);
    }
}

fn all_filter() -> AnimeFilter {
    AnimeFilter {
        query: String::new(),
        genre: None,
        language: LanguageFilter::All,
        watched: WatchedFilter::All,
        meh: MehFilter::Include,
        sort: SortOrder::Title,
        prefer_tags: Vec::new(),
        must_tags: Vec::new(),
        exclude_tags: Vec::new(),
    }
}

fn core_with(catalog: Arc<FakeCatalog>) -> (Arc<Core>, TempDir) {
    let dir = TempDir::new("core");
    let core = Core::with_sources(
        Database::open_in_memory().unwrap(),
        catalog,
        Arc::new(EmptyMetadata),
        dir.store(),
        Arc::new(FakeFetcher::default()),
        fast(),
    );
    (core, dir)
}

/// Katalog-, Nutzer-Datenbank und Bildordner in einem temporären Verzeichnis.
struct AppFiles {
    dir: TempDir,
}

impl AppFiles {
    fn new(name: &str) -> Self {
        let dir = TempDir::new(name);
        std::fs::create_dir_all(dir.path()).unwrap();
        Self { dir }
    }

    fn path(&self, name: &str) -> String {
        self.dir.path().join(name).to_string_lossy().into_owned()
    }

    fn catalog(&self) -> String {
        self.path("crunchymate.db")
    }

    fn user(&self) -> String {
        self.path("crunchymate-user.db")
    }

    fn open_core(&self, catalog: Arc<dyn CatalogSource>) -> Arc<Core> {
        Core::with_sources(
            Database::open(&self.catalog(), &self.user()).unwrap(),
            catalog,
            Arc::new(EmptyMetadata),
            ImageStore::new(self.path("images")).unwrap(),
            Arc::new(FakeFetcher::default()),
            fast(),
        )
    }

    /// Wie ein Neuaufbau des Caches: die Katalog-Datei verschwindet, die Nutzer-Datei bleibt.
    fn delete_catalog(&self) {
        for suffix in ["", "-wal", "-shm"] {
            let _ = std::fs::remove_file(format!("{}{suffix}", self.catalog()));
        }
    }
}

#[tokio::test]
async fn new_resets_orphaned_running_state() {
    let files = AppFiles::new("orphan");
    {
        let db = Database::open(&files.catalog(), &files.user()).unwrap();
        db.with_conn(|c| mark_running(c, SyncKind::Catalog, 5))
            .unwrap();
    }
    let core = Core::new(files.catalog(), files.user(), files.path("images")).unwrap();
    let status = core.sync_status(SyncKind::Catalog).unwrap();
    assert_eq!(status.state, SyncRunState::Cancelled);
    assert!(status.last_finished_at.is_some());
    assert_eq!(
        core.sync_status(SyncKind::Metadata).unwrap().state,
        SyncRunState::Idle
    );
}

#[tokio::test]
async fn new_rejects_database_from_newer_app_version() {
    let files = AppFiles::new("too-new");
    {
        let conn = rusqlite::Connection::open(files.catalog()).unwrap();
        conn.execute_batch("PRAGMA user_version = 999;").unwrap();
    }
    let result = Core::new(files.catalog(), files.user(), files.path("images"));
    assert!(matches!(
        result,
        Err(CoreError::UnsupportedSchema { found: 999, .. })
    ));
}

fn ids_where(core: &Core, watched: WatchedFilter, meh: MehFilter) -> Vec<String> {
    let filter = AnimeFilter {
        watched,
        meh,
        ..all_filter()
    };
    core.query_anime(filter, 0, 100)
        .unwrap()
        .items
        .into_iter()
        .map(|i| i.id)
        .collect()
}

#[tokio::test]
async fn restore_with_only_user_db_shows_marks_after_first_sync() {
    // Altes Gerät: markieren, danach wird nur die Nutzer-Datei gesichert
    let old_device = AppFiles::new("restore-old");
    let core = old_device.open_core(Arc::new(FakeCatalog::new(&["a1", "a2", "a3"])));
    core.sync_catalog(Arc::new(RecordingObserver::default()))
        .await
        .unwrap();
    core.set_watched("a1".to_string(), true).unwrap();
    core.set_meh("a3".to_string(), true).unwrap();
    let saved = Settings {
        content_locale: "en-US".to_string(),
        ..Settings::default()
    };
    core.save_settings(saved.clone()).unwrap();
    drop(core);

    // Neues Gerät: nur die Nutzer-Datei ist wiederhergestellt, der Katalog fehlt
    let new_device = AppFiles::new("restore-new");
    std::fs::copy(old_device.user(), new_device.user()).unwrap();
    let core = new_device.open_core(Arc::new(FakeCatalog::new(&["a1", "a2", "a3"])));
    assert_eq!(core.settings().unwrap(), saved);
    assert_eq!(core.stats().unwrap().total, 0);
    assert!(ids_where(&core, WatchedFilter::Watched, MehFilter::Include).is_empty());

    core.sync_catalog(Arc::new(RecordingObserver::default()))
        .await
        .unwrap();

    assert_eq!(
        ids_where(&core, WatchedFilter::Watched, MehFilter::Include),
        ["a1"]
    );
    assert_eq!(
        ids_where(&core, WatchedFilter::All, MehFilter::Only),
        ["a3"]
    );
    assert_eq!(
        ids_where(&core, WatchedFilter::Unwatched, MehFilter::Hide),
        ["a2"]
    );
    let stats = core.stats().unwrap();
    assert_eq!((stats.total, stats.watched, stats.meh), (3, 1, 1));
    assert!(core.get_anime("a1".to_string()).unwrap().unwrap().watched);
}

#[tokio::test]
async fn rebuilding_catalog_db_keeps_every_mark() {
    let files = AppFiles::new("rebuild");
    let core = files.open_core(Arc::new(FakeCatalog::new(&["a1", "a2"])));
    core.sync_catalog(Arc::new(RecordingObserver::default()))
        .await
        .unwrap();
    core.set_watched("a1".to_string(), true).unwrap();
    core.set_meh("a1".to_string(), true).unwrap();
    core.set_meh("a2".to_string(), true).unwrap();
    drop(core);

    files.delete_catalog();
    let core = files.open_core(Arc::new(FakeCatalog::new(&["a1", "a2"])));
    assert_eq!(core.stats().unwrap().total, 0);
    core.sync_catalog(Arc::new(RecordingObserver::default()))
        .await
        .unwrap();

    let a1 = core.get_anime("a1".to_string()).unwrap().unwrap();
    assert!(a1.watched && a1.is_meh);
    let a2 = core.get_anime("a2".to_string()).unwrap().unwrap();
    assert!(!a2.watched && a2.is_meh);
}

#[tokio::test]
async fn facade_roundtrip_with_fake_sources() {
    let (core, _dir) = core_with(Arc::new(FakeCatalog::new(&["a1", "a2", "a3"])));
    let observer = Arc::new(RecordingObserver::default());

    let summary = core.sync_catalog(observer.clone()).await.unwrap();
    assert_eq!(summary.processed, 3);
    assert!(!observer.events.lock().unwrap().is_empty());
    assert_eq!(
        core.sync_status(SyncKind::Catalog).unwrap().state,
        SyncRunState::Completed
    );

    let page = core.query_anime(all_filter(), 0, 10).unwrap();
    assert_eq!(page.total, 3);
    assert_eq!(page.items.len(), 3);

    core.set_watched("a2".to_string(), true).unwrap();
    core.set_meh("a3".to_string(), true).unwrap();
    let stats = core.stats().unwrap();
    assert_eq!((stats.total, stats.watched, stats.meh), (3, 1, 1));
    assert!(core.get_anime("a2".to_string()).unwrap().unwrap().watched);
    assert_eq!(core.get_anime("fehlt".to_string()).unwrap(), None);
    assert_eq!(core.list_genres().unwrap()[0].name, "Action");
    assert!(core.list_tags().unwrap().is_empty());

    let enriched = core.enrich_metadata(observer).await.unwrap();
    assert_eq!(enriched.failed_batches, 0);
    assert_eq!(
        core.sync_status(SyncKind::Metadata).unwrap().state,
        SyncRunState::Completed
    );
}

#[tokio::test]
async fn query_anime_clamps_negative_and_zero_limit() {
    let (core, _dir) = core_with(Arc::new(FakeCatalog::new(&["a1", "a2"])));
    core.sync_catalog(Arc::new(RecordingObserver::default()))
        .await
        .unwrap();

    let empty = core.query_anime(all_filter(), 0, 0).unwrap();
    assert!(empty.items.is_empty());
    assert_eq!(empty.total, 2);

    let negative_limit = core.query_anime(all_filter(), 0, -5).unwrap();
    assert!(negative_limit.items.is_empty());
    assert_eq!(negative_limit.total, 2);

    let negative_offset = core.query_anime(all_filter(), -3, 10).unwrap();
    assert_eq!(negative_offset.items.len(), 2);
}

#[tokio::test]
async fn sync_catalog_uses_content_locale_from_settings() {
    let catalog = Arc::new(FakeCatalog::new(&["a1"]));
    let (core, _dir) = core_with(catalog.clone());
    assert_eq!(core.settings().unwrap(), Settings::default());

    let settings = Settings {
        content_locale: "fr-FR".to_string(),
        ..Settings::default()
    };
    core.save_settings(settings.clone()).unwrap();
    assert_eq!(core.settings().unwrap(), settings);

    core.sync_catalog(Arc::new(RecordingObserver::default()))
        .await
        .unwrap();
    let locales = catalog.locales.lock().unwrap();
    assert!(!locales.is_empty());
    assert!(locales.iter().all(|locale| locale == "fr-FR"));
}

#[test]
fn supported_locales_start_with_german() {
    let (core, _dir) = core_with(Arc::new(FakeCatalog::new(&[])));
    let locales = core.supported_content_locales();
    assert_eq!(locales[0], "de-DE");
    assert!(locales.contains(&"en-US".to_string()));
}
