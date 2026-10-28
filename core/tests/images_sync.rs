mod common;

use std::sync::Mutex;
use std::time::Duration;

use crunchymate_core::db::anime::{apply_metadata, get_anime, stats, upsert_catalog_page};
use crunchymate_core::db::images::{delete_image, image_stats, local_ref, record, ImageSlot};
use crunchymate_core::db::Database;
use crunchymate_core::error::CoreResult;
use crunchymate_core::images::encode::DecodeLimits;
use crunchymate_core::model::{
    AnimeFilter, CatalogItem, CatalogPage, LanguageFilter, MehFilter, MetadataItem, SortOrder,
    SyncKind, SyncPhase, SyncProgress, WatchedFilter,
};
use crunchymate_core::query::query_anime;
use crunchymate_core::remote::catalog_source::CatalogSource;
use crunchymate_core::remote::metadata_source::MetadataSource;
use crunchymate_core::sync::catalog_sync::run_catalog_sync;
use crunchymate_core::sync::config::SyncConfig;
use crunchymate_core::sync::enrichment::run_enrichment;
use crunchymate_core::sync::images::run_image_sync;
use crunchymate_core::sync::progress::SyncObserver;

use common::{webp_bytes, FakeFetcher, TempDir};

const TALL: [ImageSlot; 1] = [ImageSlot::PosterTall];

fn fast() -> SyncConfig {
    SyncConfig {
        catalog_page_size: 50,
        catalog_page_delay: Duration::ZERO,
        metadata_batch_size: 10,
        metadata_batch_delay: Duration::ZERO,
        ..SyncConfig::default()
    }
}

fn item(id: &str, tall: &str, wide: &str) -> CatalogItem {
    CatalogItem {
        id: id.to_string(),
        title: format!("Titel {id}"),
        description: String::new(),
        poster_tall: tall.to_string(),
        poster_wide: wide.to_string(),
        episode_count: 12,
        launch_year: 2020,
        is_subbed: true,
        is_dubbed: false,
        has_target_sub: true,
        has_target_dub: false,
        genres: Vec::new(),
    }
}

fn seed(db: &Database, items: &[CatalogItem]) {
    db.with_tx(|tx| upsert_catalog_page(tx, items)).unwrap();
}

fn tall_url(id: &str) -> String {
    format!("http://img/tall-{id}")
}

/// Legt Anime a, b, c mit hohen Postern an und liefert deren Bytes aus.
fn seed_three(db: &Database, fetcher: &FakeFetcher) {
    let ids = ["a", "b", "c"];
    let items: Vec<_> = ids.iter().map(|id| item(id, &tall_url(id), "")).collect();
    seed(db, &items);
    for id in ids {
        fetcher.serve(&tall_url(id), webp_bytes(id));
    }
}

#[derive(Default)]
struct Recorder {
    seen: Mutex<Vec<SyncProgress>>,
}

impl Recorder {
    fn events(&self) -> Vec<(SyncKind, SyncPhase, i64, i64)> {
        self.seen
            .lock()
            .unwrap()
            .iter()
            .map(|p| (p.kind, p.phase, p.done, p.total))
            .collect()
    }
}

impl SyncObserver for Recorder {
    fn on_progress(&self, progress: SyncProgress) {
        self.seen.lock().unwrap().push(progress);
    }
}

fn reference(db: &Database, id: &str, slot: ImageSlot) -> Option<(String, String)> {
    db.with_conn(|c| local_ref(c, id, slot)).unwrap()
}

fn file_count(dir: &TempDir) -> usize {
    std::fs::read_dir(dir.path())
        .unwrap()
        .filter(|e| e.as_ref().unwrap().path().is_file())
        .count()
}

#[tokio::test]
async fn image_sync_downloads_only_missing_or_changed() {
    let db = Database::open_in_memory().unwrap();
    let dir = TempDir::new("missing");
    let store = dir.store();
    let fetcher = FakeFetcher::default();
    seed_three(&db, &fetcher);

    let first = run_image_sync(
        &db,
        &store,
        &fetcher,
        &TALL,
        SyncKind::Catalog,
        &fast(),
        &Recorder::default(),
    )
    .await
    .unwrap();
    assert_eq!((first.downloaded, first.failed), (3, 0));
    assert_eq!(fetcher.calls().len(), 3);
    let (hash, ext) = reference(&db, "a", ImageSlot::PosterTall).unwrap();
    assert!(store.path(&hash, &ext).exists());

    let second = run_image_sync(
        &db,
        &store,
        &fetcher,
        &TALL,
        SyncKind::Catalog,
        &fast(),
        &Recorder::default(),
    )
    .await
    .unwrap();
    assert_eq!(second.downloaded, 0);
    assert_eq!(fetcher.calls().len(), 3);

    // Geänderte Quelle: nur dieses Bild wird neu geladen, die alte Datei verschwindet
    let changed_url = "http://img/tall-a-neu";
    seed(&db, &[item("a", changed_url, "")]);
    fetcher.serve(changed_url, webp_bytes("a-neu"));
    let third = run_image_sync(
        &db,
        &store,
        &fetcher,
        &TALL,
        SyncKind::Catalog,
        &fast(),
        &Recorder::default(),
    )
    .await
    .unwrap();
    assert_eq!(third.downloaded, 1);
    assert_eq!(fetcher.calls().len(), 4);
    assert!(!store.path(&hash, &ext).exists());
    assert_eq!(file_count(&dir), 3);
}

#[tokio::test]
async fn image_sync_counts_failures_and_retries_next_run() {
    let db = Database::open_in_memory().unwrap();
    let dir = TempDir::new("failures");
    let store = dir.store();
    let fetcher = FakeFetcher::default();
    seed_three(&db, &fetcher);
    fetcher.fail(&tall_url("b"));
    // Unlesbare Bytes scheitern beim Umwandeln, nicht beim Laden
    fetcher.serve(&tall_url("c"), b"kein Bild".to_vec());

    let first = run_image_sync(
        &db,
        &store,
        &fetcher,
        &TALL,
        SyncKind::Catalog,
        &fast(),
        &Recorder::default(),
    )
    .await
    .unwrap();
    assert_eq!((first.downloaded, first.failed), (1, 2));
    assert!(reference(&db, "a", ImageSlot::PosterTall).is_some());
    assert!(reference(&db, "b", ImageSlot::PosterTall).is_none());
    assert!(reference(&db, "c", ImageSlot::PosterTall).is_none());

    fetcher.heal(&tall_url("b"));
    fetcher.serve(&tall_url("c"), webp_bytes("c"));
    let second = run_image_sync(
        &db,
        &store,
        &fetcher,
        &TALL,
        SyncKind::Catalog,
        &fast(),
        &Recorder::default(),
    )
    .await
    .unwrap();
    assert_eq!((second.downloaded, second.failed), (2, 0));
    assert!(reference(&db, "b", ImageSlot::PosterTall).is_some());
}

#[tokio::test]
async fn image_sync_reports_images_phase_progress() {
    let db = Database::open_in_memory().unwrap();
    let dir = TempDir::new("progress");
    let fetcher = FakeFetcher::default();
    seed_three(&db, &fetcher);
    fetcher.fail(&tall_url("b"));
    let observer = Recorder::default();

    run_image_sync(
        &db,
        &dir.store(),
        &fetcher,
        &TALL,
        SyncKind::Metadata,
        &fast(),
        &observer,
    )
    .await
    .unwrap();

    let images = SyncPhase::Images;
    let kind = SyncKind::Metadata;
    assert_eq!(
        observer.events(),
        vec![
            (kind, images, 1, 3),
            (kind, images, 2, 3),
            (kind, images, 3, 3)
        ]
    );
}

#[tokio::test]
async fn image_sync_cancel_keeps_completed_files() {
    let db = Database::open_in_memory().unwrap();
    let dir = TempDir::new("cancel");
    let store = dir.store();
    let fetcher = FakeFetcher::default();
    seed_three(&db, &fetcher);
    // Reihenfolge nach Titel: a, b, c
    fetcher.block(&tall_url("b"));
    let observer = Recorder::default();
    let config = fast();
    {
        let future = run_image_sync(
            &db,
            &store,
            &fetcher,
            &TALL,
            SyncKind::Catalog,
            &config,
            &observer,
        );
        tokio::pin!(future);
        tokio::select! {
            _ = &mut future => panic!("Sync darf nicht abschließen"),
            _ = async {
                while fetcher.calls().len() < 2 {
                    tokio::task::yield_now().await;
                }
            } => {}
        }
    }
    let (hash, ext) = reference(&db, "a", ImageSlot::PosterTall).unwrap();
    assert!(store.path(&hash, &ext).exists());
    assert!(reference(&db, "b", ImageSlot::PosterTall).is_none());
    assert!(reference(&db, "c", ImageSlot::PosterTall).is_none());
    assert_eq!(observer.events().len(), 1);

    // Nach dem Abbruch holt der nächste Lauf den Rest nach
    let fetcher = FakeFetcher::default();
    seed_three(&db, &fetcher);
    let rest = run_image_sync(
        &db,
        &store,
        &fetcher,
        &TALL,
        SyncKind::Catalog,
        &fast(),
        &Recorder::default(),
    )
    .await
    .unwrap();
    assert_eq!(rest.downloaded, 2);
}

#[tokio::test]
async fn image_sync_gc_removes_unreferenced() {
    let db = Database::open_in_memory().unwrap();
    let dir = TempDir::new("gc");
    let store = dir.store();
    let fetcher = FakeFetcher::default();
    seed_three(&db, &fetcher);
    run_image_sync(
        &db,
        &store,
        &fetcher,
        &TALL,
        SyncKind::Catalog,
        &fast(),
        &Recorder::default(),
    )
    .await
    .unwrap();
    let (hash, ext) = reference(&db, "b", ImageSlot::PosterTall).unwrap();
    assert!(store.path(&hash, &ext).exists());

    // Anime verschwindet: die Verweise fallen per Cascade weg
    db.with_conn(|c| {
        c.execute("DELETE FROM anime WHERE id = 'b'", [])?;
        Ok(())
    })
    .unwrap();
    run_image_sync(
        &db,
        &store,
        &fetcher,
        &TALL,
        SyncKind::Catalog,
        &fast(),
        &Recorder::default(),
    )
    .await
    .unwrap();

    assert!(!store.path(&hash, &ext).exists());
    assert_eq!(db.with_conn(image_stats).unwrap().0, 2);
}

struct OnePage(Vec<CatalogItem>);

#[async_trait::async_trait]
impl CatalogSource for OnePage {
    async fn session_country(&self) -> CoreResult<String> {
        Ok("DE".to_string())
    }

    async fn fetch_page(&self, start: u32, _count: u32, _locale: &str) -> CoreResult<CatalogPage> {
        let items = if start == 0 {
            self.0.clone()
        } else {
            Vec::new()
        };
        Ok(CatalogPage {
            total: u32::try_from(self.0.len()).unwrap(),
            items,
        })
    }
}

#[tokio::test]
async fn catalog_sync_runs_image_phase() {
    let db = Database::open_in_memory().unwrap();
    let dir = TempDir::new("catalog");
    let store = dir.store();
    let fetcher = FakeFetcher::default();
    fetcher.serve("http://img/t1", webp_bytes("t1"));
    fetcher.serve("http://img/w1", webp_bytes("w1"));
    fetcher.fail("http://img/t2");
    let source = OnePage(vec![
        item("a1", "http://img/t1", "http://img/w1"),
        item("a2", "http://img/t2", ""),
    ]);
    let observer = Recorder::default();

    let summary = run_catalog_sync(&db, &source, &store, &fetcher, "de-DE", &fast(), &observer)
        .await
        .unwrap();

    assert_eq!(summary.processed, 2);
    assert_eq!(summary.failed_images, 1);
    let catalog = SyncKind::Catalog;
    assert_eq!(
        observer.events(),
        vec![
            (catalog, SyncPhase::Items, 2, 2),
            (catalog, SyncPhase::Images, 1, 3),
            (catalog, SyncPhase::Images, 2, 3),
            (catalog, SyncPhase::Images, 3, 3),
        ]
    );
    assert!(reference(&db, "a1", ImageSlot::PosterTall).is_some());
    assert!(reference(&db, "a1", ImageSlot::PosterWide).is_some());
    assert!(reference(&db, "a2", ImageSlot::PosterTall).is_none());
}

struct BannerMetadata;

#[async_trait::async_trait]
impl MetadataSource for BannerMetadata {
    async fn search_batch(&self, titles: &[String]) -> CoreResult<Vec<Option<MetadataItem>>> {
        Ok((0..titles.len())
            .map(|i| Some(metadata(i as i64 + 1)))
            .collect())
    }
}

fn metadata(anilist_id: i64) -> MetadataItem {
    MetadataItem {
        anilist_id,
        title_romaji: String::new(),
        title_english: String::new(),
        description: String::new(),
        score: 80,
        status: String::new(),
        banner_image: format!("http://img/banner-{anilist_id}"),
        site_url: String::new(),
        genres: Vec::new(),
        tags: Vec::new(),
    }
}

#[tokio::test]
async fn enrichment_runs_banner_phase() {
    let db = Database::open_in_memory().unwrap();
    let dir = TempDir::new("banner");
    let store = dir.store();
    let fetcher = FakeFetcher::default();
    fetcher.serve("http://img/banner-1", webp_bytes("b1"));
    seed(&db, &[item("a1", "", "")]);
    let observer = Recorder::default();

    let summary = run_enrichment(&db, &BannerMetadata, &store, &fetcher, &fast(), &observer)
        .await
        .unwrap();

    assert_eq!((summary.processed, summary.failed_images), (1, 0));
    let meta = SyncKind::Metadata;
    assert_eq!(
        observer.events(),
        vec![
            (meta, SyncPhase::Items, 1, 1),
            (meta, SyncPhase::Images, 1, 1)
        ]
    );
    assert!(reference(&db, "a1", ImageSlot::Banner).is_some());
    assert!(reference(&db, "a1", ImageSlot::PosterTall).is_none());
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

#[tokio::test]
async fn query_returns_local_poster_path() {
    let db = Database::open_in_memory().unwrap();
    let dir = TempDir::new("query");
    let store = dir.store();
    let fetcher = FakeFetcher::default();
    seed(
        &db,
        &[
            item("a", "http://img/ta", ""),
            item("b", "http://img/tb", ""),
        ],
    );
    fetcher.serve("http://img/ta", webp_bytes("ta"));
    fetcher.fail("http://img/tb");
    run_image_sync(
        &db,
        &store,
        &fetcher,
        &TALL,
        SyncKind::Catalog,
        &fast(),
        &Recorder::default(),
    )
    .await
    .unwrap();

    let page = db
        .with_conn(|c| query_anime(c, &store, &all_filter(), 0, 10))
        .unwrap();

    let (hash, ext) = reference(&db, "a", ImageSlot::PosterTall).unwrap();
    assert_eq!(
        page.items[0].poster_path,
        store.path(&hash, &ext).to_string_lossy()
    );
    assert!(std::path::Path::new(&page.items[0].poster_path).is_absolute());
    assert_eq!(page.items[1].poster_path, "");
}

#[tokio::test]
async fn detail_banner_falls_back_to_wide_poster() {
    let db = Database::open_in_memory().unwrap();
    let dir = TempDir::new("detail");
    let store = dir.store();
    let fetcher = FakeFetcher::default();
    seed(
        &db,
        &[
            item("a", "http://img/ta", "http://img/wa"),
            item("b", "", "http://img/wb"),
        ],
    );
    for url in ["http://img/ta", "http://img/wa", "http://img/wb"] {
        fetcher.serve(url, webp_bytes(url));
    }
    let both = [ImageSlot::PosterTall, ImageSlot::PosterWide];
    run_image_sync(
        &db,
        &store,
        &fetcher,
        &both,
        SyncKind::Catalog,
        &fast(),
        &Recorder::default(),
    )
    .await
    .unwrap();

    let path = |id: &str, slot| {
        let (hash, ext) = reference(&db, id, slot).unwrap();
        store.path(&hash, &ext).to_string_lossy().into_owned()
    };
    let a = db
        .with_conn(|c| get_anime(c, &store, "a"))
        .unwrap()
        .unwrap();
    assert_eq!(a.poster_path, path("a", ImageSlot::PosterTall));
    assert_eq!(a.banner_path, path("a", ImageSlot::PosterWide));
    let b = db
        .with_conn(|c| get_anime(c, &store, "b"))
        .unwrap()
        .unwrap();
    assert_eq!(b.poster_path, "");
    assert_eq!(b.banner_path, path("b", ImageSlot::PosterWide));

    // Ein geladenes Banner hat Vorrang
    db.with_tx(|tx| apply_metadata(tx, "a", &metadata(7)))
        .unwrap();
    fetcher.serve("http://img/banner-7", webp_bytes("banner-7"));
    run_image_sync(
        &db,
        &store,
        &fetcher,
        &[ImageSlot::Banner],
        SyncKind::Metadata,
        &fast(),
        &Recorder::default(),
    )
    .await
    .unwrap();
    let a = db
        .with_conn(|c| get_anime(c, &store, "a"))
        .unwrap()
        .unwrap();
    assert_eq!(a.banner_path, path("a", ImageSlot::Banner));
}

#[tokio::test]
async fn stats_include_image_totals() {
    let db = Database::open_in_memory().unwrap();
    let dir = TempDir::new("stats");
    let fetcher = FakeFetcher::default();
    seed_three(&db, &fetcher);
    assert_eq!(db.with_conn(stats).unwrap().image_count, 0);

    run_image_sync(
        &db,
        &dir.store(),
        &fetcher,
        &TALL,
        SyncKind::Catalog,
        &fast(),
        &Recorder::default(),
    )
    .await
    .unwrap();

    let s = db.with_conn(stats).unwrap();
    let expected_bytes: i64 = ["a", "b", "c"]
        .iter()
        .map(|id| webp_bytes(id).len() as i64)
        .sum();
    assert_eq!((s.image_count, s.image_bytes), (3, expected_bytes));
}

#[tokio::test]
async fn image_sync_gc_removes_orphan_files_without_row() {
    let db = Database::open_in_memory().unwrap();
    let dir = TempDir::new("orphan");
    let store = dir.store();
    let fetcher = FakeFetcher::default();
    seed_three(&db, &fetcher);
    let orphan = store.path(&"ab".repeat(32), "webp");
    std::fs::write(&orphan, b"Rest eines Abbruchs").unwrap();
    let foreign = dir.path().join("notiz.txt");
    std::fs::write(&foreign, b"fremd").unwrap();

    run_image_sync(
        &db,
        &store,
        &fetcher,
        &TALL,
        SyncKind::Catalog,
        &fast(),
        &Recorder::default(),
    )
    .await
    .unwrap();

    assert!(!orphan.exists());
    assert!(foreign.exists());
    assert_eq!(file_count(&dir), 4);
}

#[test]
fn conditional_delete_keeps_row_that_got_a_new_ref() {
    let db = Database::open_in_memory().unwrap();
    seed(&db, &[item("a", "", "")]);
    db.with_tx(|tx| record(tx, "a", ImageSlot::Banner, "http://b", "h1", "webp", 5))
        .unwrap();
    assert!(!db.with_conn(|c| delete_image(c, "h1")).unwrap());
    assert!(reference(&db, "a", ImageSlot::Banner).is_some());
}

#[tokio::test]
async fn concurrent_image_phases_do_not_fail_and_keep_files() {
    let db = Database::open_in_memory().unwrap();
    let dir = TempDir::new("concurrent");
    let store = dir.store();
    let fetcher = FakeFetcher::default();
    // Gleicher Inhalt in zwei Arten: beide Läufe teilen sich Hash und Datei
    seed(
        &db,
        &[
            item("a", "http://img/t", ""),
            item("b", "http://img/t2", ""),
        ],
    );
    db.with_tx(|tx| apply_metadata(tx, "a", &metadata(1)))
        .unwrap();
    fetcher.serve("http://img/t", webp_bytes("gleich"));
    fetcher.serve("http://img/t2", webp_bytes("zwei"));
    fetcher.serve("http://img/banner-1", webp_bytes("gleich"));
    let banner = [ImageSlot::Banner];
    let (observer_a, observer_b) = (Recorder::default(), Recorder::default());
    let config = fast();

    let (first, second) = tokio::join!(
        run_image_sync(
            &db,
            &store,
            &fetcher,
            &TALL,
            SyncKind::Catalog,
            &config,
            &observer_a
        ),
        run_image_sync(
            &db,
            &store,
            &fetcher,
            &banner,
            SyncKind::Metadata,
            &config,
            &observer_b
        ),
    );

    assert_eq!(first.unwrap().failed, 0);
    assert_eq!(second.unwrap().failed, 0);
    for (id, slot) in [
        ("a", ImageSlot::PosterTall),
        ("b", ImageSlot::PosterTall),
        ("a", ImageSlot::Banner),
    ] {
        let (hash, ext) = reference(&db, id, slot).unwrap();
        assert!(store.path(&hash, &ext).exists());
    }
}

#[tokio::test]
async fn image_sync_reports_empty_images_phase_when_nothing_pending() {
    let db = Database::open_in_memory().unwrap();
    let dir = TempDir::new("empty");
    let observer = Recorder::default();
    run_image_sync(
        &db,
        &dir.store(),
        &FakeFetcher::default(),
        &TALL,
        SyncKind::Catalog,
        &fast(),
        &observer,
    )
    .await
    .unwrap();
    assert_eq!(
        observer.events(),
        vec![(SyncKind::Catalog, SyncPhase::Images, 0, 0)]
    );
}

#[tokio::test]
async fn image_sync_store_failure_is_fatal_but_corrupt_image_only_counted() {
    let db = Database::open_in_memory().unwrap();
    let dir = TempDir::new("fatal");
    let store = dir.store();
    let fetcher = FakeFetcher::default();
    seed_three(&db, &fetcher);
    // a ist unlesbar und wird nur gezählt; b scheitert an der Ablage
    fetcher.serve(&tall_url("a"), b"kein Bild".to_vec());
    // Eine Datei anstelle des Temp-Verzeichnisses lässt jedes Schreiben scheitern, auch mit
    // Root-Rechten (anders als entzogene Schreibrechte); der Test läuft daher immer wirklich
    let tmp = dir.path().join("tmp");
    std::fs::remove_dir_all(&tmp).unwrap();
    std::fs::write(&tmp, b"kein Verzeichnis").unwrap();

    let result = run_image_sync(
        &db,
        &store,
        &fetcher,
        &TALL,
        SyncKind::Catalog,
        &fast(),
        &Recorder::default(),
    )
    .await;

    assert!(
        matches!(
            result,
            Err(crunchymate_core::error::CoreError::Storage { .. })
        ),
        "{result:?}"
    );
    assert!(reference(&db, "a", ImageSlot::PosterTall).is_none());
    assert!(reference(&db, "b", ImageSlot::PosterTall).is_none());
    assert_eq!(fetcher.calls().len(), 2);
}

fn small_jpeg(width: u32, height: u32) -> Vec<u8> {
    let mut out = std::io::Cursor::new(Vec::new());
    image::RgbImage::from_pixel(width, height, image::Rgb([200, 100, 50]))
        .write_to(&mut out, image::ImageFormat::Jpeg)
        .unwrap();
    out.into_inner()
}

const LIMIT_BYTES: usize = 4096;

#[tokio::test]
async fn images_beyond_size_limits_are_skipped_and_counted() {
    let db = Database::open_in_memory().unwrap();
    let dir = TempDir::new("limits");
    let store = dir.store();
    let fetcher = FakeFetcher::default();
    seed_three(&db, &fetcher);
    // a: Datei zu groß, b: Abmessungen zu groß, c: passt
    fetcher.serve(&tall_url("a"), webp_bytes(&"x".repeat(LIMIT_BYTES)));
    fetcher.serve(&tall_url("b"), small_jpeg(64, 32));
    fetcher.serve(&tall_url("c"), small_jpeg(16, 16));
    let config = SyncConfig {
        image_max_download_bytes: LIMIT_BYTES as u64,
        image_decode_limits: DecodeLimits {
            max_width: 32,
            max_height: 32,
            ..SyncConfig::default().image_decode_limits
        },
        ..fast()
    };
    assert!(
        small_jpeg(64, 32).len() <= LIMIT_BYTES,
        "Testbild b muss unter der Downloadgrenze liegen"
    );

    let summary = run_image_sync(
        &db,
        &store,
        &fetcher,
        &TALL,
        SyncKind::Catalog,
        &config,
        &Recorder::default(),
    )
    .await
    .unwrap();

    assert_eq!(
        (summary.downloaded, summary.failed, summary.skipped),
        (1, 0, 2)
    );
    assert!(reference(&db, "a", ImageSlot::PosterTall).is_none());
    assert!(reference(&db, "b", ImageSlot::PosterTall).is_none());
    assert!(reference(&db, "c", ImageSlot::PosterTall).is_some());
}

#[tokio::test]
async fn catalog_summary_reports_skipped_images() {
    let db = Database::open_in_memory().unwrap();
    let dir = TempDir::new("limits-catalog");
    let fetcher = FakeFetcher::default();
    fetcher.serve("http://img/t1", webp_bytes(&"x".repeat(200)));
    let source = OnePage(vec![item("a1", "http://img/t1", "")]);
    let config = SyncConfig {
        image_max_download_bytes: 100,
        ..fast()
    };

    let summary = run_catalog_sync(
        &db,
        &source,
        &dir.store(),
        &fetcher,
        "de-DE",
        &config,
        &Recorder::default(),
    )
    .await
    .unwrap();

    assert_eq!((summary.failed_images, summary.skipped_images), (0, 1));
    let status = db
        .with_conn(|c| crunchymate_core::db::sync_state::status(c, SyncKind::Catalog))
        .unwrap();
    assert_eq!(status.last_summary, Some(summary));
}
