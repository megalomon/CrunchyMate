use crate::db::anime::upsert_catalog_page;
use crate::db::images::ImageSlot;
use crate::db::sync_state::{set_catalog_country, set_synced_content_locale, update_progress};
use crate::db::Database;
use crate::error::{CoreError, CoreResult};
use crate::images::fetch::ImageFetcher;
use crate::images::store::ImageStore;
use crate::model::{SyncKind, SyncPhase, SyncProgress, SyncSummary};
use crate::remote::catalog_source::CatalogSource;

use super::config::SyncConfig;
use super::images::{images_may_follow, merge_image_result, run_image_sync};
use super::progress::SyncObserver;
use super::run_guard::{RunGuard, RunOutcome};
use super::{catch_panic, pause};

const KIND: SyncKind = SyncKind::Catalog;
/// Der Katalog liefert beide Poster; das Banner kommt erst mit den Metadaten.
const CATALOG_IMAGE_SLOTS: [ImageSlot; 2] = [ImageSlot::PosterTall, ImageSlot::PosterWide];

/// Lädt den ganzen Katalog seitenweise und danach die Poster. Jede Seite wird sofort
/// gespeichert, damit ein Abbruch oder Fehler die bereits geladenen Seiten nicht verwirft.
pub async fn run_catalog_sync(
    db: &Database,
    source: &dyn CatalogSource,
    store: &ImageStore,
    fetcher: &dyn ImageFetcher,
    locale: &str,
    config: &SyncConfig,
    observer: &dyn SyncObserver,
) -> CoreResult<SyncSummary> {
    let guard = RunGuard::start(db, KIND, 0)?;
    let outcome = catch_panic(sync_all(
        db, source, store, fetcher, locale, config, observer,
    ))
    .await
    .unwrap_or_else(|error| RunOutcome::failed(SyncSummary::default(), error));
    guard.finish(outcome)
}

async fn sync_all(
    db: &Database,
    source: &dyn CatalogSource,
    store: &ImageStore,
    fetcher: &dyn ImageFetcher,
    locale: &str,
    config: &SyncConfig,
    observer: &dyn SyncObserver,
) -> RunOutcome {
    let mut done: i64 = 0;
    let pages = sync_pages(db, source, locale, config, observer, &mut done).await;
    // Erst ein vollständiger Durchlauf bestimmt die Sprache der gespeicherten Einträge
    let error = pages
        .and_then(|()| db.with_conn(|conn| set_synced_content_locale(conn, locale)))
        .err();
    let mut outcome = RunOutcome {
        summary: SyncSummary {
            processed: done,
            ..SyncSummary::default()
        },
        error,
    };
    if images_may_follow(&outcome) {
        let images = run_image_sync(
            db,
            store,
            fetcher,
            &CATALOG_IMAGE_SLOTS,
            KIND,
            config,
            observer,
        )
        .await;
        merge_image_result(&mut outcome, images);
    }
    outcome
}

/// Zählt gespeicherte Einträge in `done` mit, damit auch ein abgebrochener Durchlauf sein
/// Teilergebnis meldet.
async fn sync_pages(
    db: &Database,
    source: &dyn CatalogSource,
    locale: &str,
    config: &SyncConfig,
    observer: &dyn SyncObserver,
    done: &mut i64,
) -> CoreResult<()> {
    let country = source.session_country().await?;
    db.with_conn(|conn| set_catalog_country(conn, &country))?;

    // Seitengröße 0 würde endlos dieselbe Seite abrufen
    let page_size = config.catalog_page_size.max(1);
    let mut start: u32 = 0;
    loop {
        let page = source.fetch_page(start, page_size, locale).await?;
        let total = i64::from(page.total);
        if page.items.is_empty() {
            // Vor der angekündigten Gesamtzahl ist eine leere Seite kein Ende, sondern ein Fehler
            if start < page.total {
                return Err(CoreError::Incomplete { done: *done, total });
            }
            break;
        }
        let done_now = done.saturating_add(i64::try_from(page.items.len()).unwrap_or(i64::MAX));
        // Seite und Fortschritt gemeinsam festschreiben
        db.with_tx(|tx| {
            upsert_catalog_page(tx, &page.items)?;
            update_progress(tx, KIND, done_now, total)
        })?;
        *done = done_now;
        observer.on_progress(SyncProgress {
            kind: KIND,
            phase: SyncPhase::Items,
            done: done_now,
            total,
        });
        // Weiter um die angeforderte Größe, nicht um items.len()
        start = start.saturating_add(page_size);
        if start >= page.total {
            break;
        }
        pause(config.catalog_page_delay).await;
    }
    Ok(())
}
