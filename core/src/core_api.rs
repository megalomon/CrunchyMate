use std::sync::Arc;

use crate::db::{anime, settings, sync_state, Database};
use crate::error::CoreResult;
use crate::images::fetch::{HttpImageFetcher, ImageFetcher};
use crate::images::store::ImageStore;
use crate::model::{
    AnimeDetail, AnimeFilter, AnimePage, NameCount, Settings, Stats, SyncKind, SyncStatus,
    SyncSummary,
};
use crate::query;
use crate::remote::anilist::{AniListClient, RetryConfig};
use crate::remote::catalog_mapping::SUPPORTED_CONTENT_LOCALES;
use crate::remote::catalog_source::CatalogSource;
use crate::remote::crispybun::CrispyBunSource;
use crate::remote::metadata_source::MetadataSource;
use crate::sync::catalog_sync::run_catalog_sync;
use crate::sync::config::SyncConfig;
use crate::sync::enrichment::run_enrichment;
use crate::sync::now_unix;
use crate::sync::progress::SyncObserver;

/// Einziger Einstiegspunkt für die Kotlin-Seite.
#[derive(uniffi::Object)]
pub struct Core {
    db: Arc<Database>,
    catalog: Arc<dyn CatalogSource>,
    metadata: Arc<dyn MetadataSource>,
    store: ImageStore,
    fetcher: Arc<dyn ImageFetcher>,
    config: SyncConfig,
}

// async_runtime = "tokio": reqwest und tokio::time brauchen einen Tokio-Kontext,
// auch wenn Kotlin die Futures über seine Coroutines abfragt.
#[uniffi::export(async_runtime = "tokio")]
impl Core {
    /// Öffnet Katalog- und Nutzer-Datenbank und baut die echten Quellen auf, ohne
    /// Netzwerkzugriff. Läufe, die beim letzten Prozessende noch liefen, gelten danach als
    /// abgebrochen. `user_db_path` enthält Einstellungen und Markierungen und ist die einzige
    /// Datei, die ins Backup gehört.
    #[uniffi::constructor]
    pub fn new(db_path: String, user_db_path: String, image_dir: String) -> CoreResult<Arc<Self>> {
        let db = Database::open(&db_path, &user_db_path)?;
        db.with_conn(|conn| sync_state::reset_orphaned_runs(conn, now_unix()))?;
        let catalog = Arc::new(CrispyBunSource::new()?);
        let metadata = Arc::new(AniListClient::new(
            AniListClient::DEFAULT_ENDPOINT.to_string(),
            RetryConfig::default(),
        )?);
        let store = ImageStore::new(image_dir)?;
        let fetcher = Arc::new(HttpImageFetcher::new()?);
        Ok(Self::with_sources(
            db,
            catalog,
            metadata,
            store,
            fetcher,
            SyncConfig::default(),
        ))
    }

    /// Negative Werte für `offset` und `limit` gelten als 0; `limit == 0`
    /// liefert keine Einträge, aber die korrekte Gesamtzahl.
    pub fn query_anime(
        &self,
        filter: AnimeFilter,
        offset: i32,
        limit: i32,
    ) -> CoreResult<AnimePage> {
        let offset = non_negative(offset);
        let limit = non_negative(limit);
        self.db
            .with_conn(|conn| query::query_anime(conn, &self.store, &filter, offset, limit))
    }

    pub fn get_anime(&self, id: String) -> CoreResult<Option<AnimeDetail>> {
        self.db
            .with_conn(|conn| anime::get_anime(conn, &self.store, &id))
    }

    pub fn set_watched(&self, id: String, watched: bool) -> CoreResult<()> {
        self.db
            .with_conn(|conn| anime::set_watched(conn, &id, watched, now_unix()))
    }

    pub fn set_meh(&self, id: String, meh: bool) -> CoreResult<()> {
        self.db
            .with_conn(|conn| anime::set_meh(conn, &id, meh, now_unix()))
    }

    pub fn list_genres(&self) -> CoreResult<Vec<NameCount>> {
        self.db.with_conn(anime::list_genres)
    }

    pub fn list_tags(&self) -> CoreResult<Vec<NameCount>> {
        self.db.with_conn(anime::list_tags)
    }

    pub fn stats(&self) -> CoreResult<Stats> {
        self.db.with_conn(anime::stats)
    }

    pub fn settings(&self) -> CoreResult<Settings> {
        self.db.with_conn(settings::load)
    }

    pub fn save_settings(&self, settings: Settings) -> CoreResult<()> {
        self.db.with_conn(|conn| settings::save(conn, &settings))
    }

    pub fn supported_content_locales(&self) -> Vec<String> {
        SUPPORTED_CONTENT_LOCALES
            .iter()
            .map(|locale| locale.to_string())
            .collect()
    }

    pub fn sync_status(&self, kind: SyncKind) -> CoreResult<SyncStatus> {
        self.db.with_conn(|conn| sync_state::status(conn, kind))
    }

    /// Lädt den Katalog in der beim Aufruf gespeicherten Inhalts-Locale.
    pub async fn sync_catalog(&self, observer: Arc<dyn SyncObserver>) -> CoreResult<SyncSummary> {
        let locale = self.db.with_conn(settings::load)?.content_locale;
        run_catalog_sync(
            &self.db,
            self.catalog.as_ref(),
            &self.store,
            self.fetcher.as_ref(),
            &locale,
            &self.config,
            &*observer,
        )
        .await
    }

    pub async fn enrich_metadata(
        &self,
        observer: Arc<dyn SyncObserver>,
    ) -> CoreResult<SyncSummary> {
        run_enrichment(
            &self.db,
            self.metadata.as_ref(),
            &self.store,
            self.fetcher.as_ref(),
            &self.config,
            &*observer,
        )
        .await
    }
}

impl Core {
    /// Baut den Kern mit frei wählbaren Quellen auf (für Tests); setzt
    /// verwaiste Läufe bewusst nicht zurück.
    pub fn with_sources(
        db: Database,
        catalog: Arc<dyn CatalogSource>,
        metadata: Arc<dyn MetadataSource>,
        store: ImageStore,
        fetcher: Arc<dyn ImageFetcher>,
        config: SyncConfig,
    ) -> Arc<Self> {
        Arc::new(Self {
            db: Arc::new(db),
            catalog,
            metadata,
            store,
            fetcher,
            config,
        })
    }
}

fn non_negative(value: i32) -> u32 {
    u32::try_from(value).unwrap_or(0)
}
