use crate::db::anime::upsert_catalog_page;
use crate::db::sync_state::{set_catalog_country, update_progress};
use crate::db::Database;
use crate::error::CoreResult;
use crate::model::{SyncKind, SyncProgress, SyncSummary};
use crate::remote::catalog_source::CatalogSource;

use super::config::SyncConfig;
use super::pause;
use super::progress::SyncObserver;
use super::run_guard::RunGuard;

const KIND: SyncKind = SyncKind::Catalog;

/// Lädt den ganzen Katalog seitenweise. Jede Seite wird sofort gespeichert,
/// damit ein Abbruch oder Fehler die bereits geladenen Seiten nicht verwirft.
pub async fn run_catalog_sync(
    db: &Database,
    source: &dyn CatalogSource,
    locale: &str,
    config: &SyncConfig,
    observer: &dyn SyncObserver,
) -> CoreResult<SyncSummary> {
    let guard = RunGuard::start(db, KIND, 0)?;
    let result = sync_pages(db, source, locale, config, observer).await;
    guard.finish(result)
}

async fn sync_pages(
    db: &Database,
    source: &dyn CatalogSource,
    locale: &str,
    config: &SyncConfig,
    observer: &dyn SyncObserver,
) -> CoreResult<SyncSummary> {
    let country = source.session_country().await?;
    db.with_conn(|conn| set_catalog_country(conn, &country))?;

    // Seitengröße 0 würde endlos dieselbe Seite abrufen
    let page_size = config.catalog_page_size.max(1);
    let mut start: u32 = 0;
    let mut done: i64 = 0;
    loop {
        let page = source.fetch_page(start, page_size, locale).await?;
        if page.items.is_empty() {
            break;
        }
        let total = i64::from(page.total);
        done += i64::try_from(page.items.len()).unwrap_or(i64::MAX);
        // Seite und Fortschritt gemeinsam festschreiben
        db.with_tx(|tx| {
            upsert_catalog_page(tx, &page.items)?;
            update_progress(tx, KIND, done, total)
        })?;
        observer.on_progress(SyncProgress {
            kind: KIND,
            done,
            total,
        });
        // Weiter um die angeforderte Größe, nicht um items.len()
        start = start.saturating_add(page_size);
        if start >= page.total {
            break;
        }
        pause(config.catalog_page_delay).await;
    }
    Ok(SyncSummary {
        processed: done,
        failed_batches: 0,
    })
}
