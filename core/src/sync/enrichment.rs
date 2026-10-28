use crate::db::anime::{apply_metadata, unenriched};
use crate::db::images::ImageSlot;
use crate::db::sync_state::update_progress;
use crate::db::Database;
use crate::error::{CoreError, CoreResult};
use crate::images::fetch::ImageFetcher;
use crate::images::store::ImageStore;
use crate::model::{SyncKind, SyncPhase, SyncProgress, SyncSummary};
use crate::remote::metadata_source::MetadataSource;

use super::config::SyncConfig;
use super::images::run_image_sync;
use super::pause;
use super::progress::SyncObserver;
use super::run_guard::RunGuard;

const KIND: SyncKind = SyncKind::Metadata;
const METADATA_IMAGE_SLOTS: [ImageSlot; 1] = [ImageSlot::Banner];

/// Reichert alle noch nicht angereicherten Einträge batchweise an.
/// Einzelne fehlgeschlagene Batches werden gezählt und übersprungen,
/// `RateLimited` bricht den Lauf ab. Danach folgen die Banner.
pub async fn run_enrichment(
    db: &Database,
    source: &dyn MetadataSource,
    store: &ImageStore,
    fetcher: &dyn ImageFetcher,
    config: &SyncConfig,
    observer: &dyn SyncObserver,
) -> CoreResult<SyncSummary> {
    let guard = RunGuard::start(db, KIND, 0)?;
    let result = enrich_all(db, source, store, fetcher, config, observer).await;
    guard.finish(result)
}

async fn enrich_all(
    db: &Database,
    source: &dyn MetadataSource,
    store: &ImageStore,
    fetcher: &dyn ImageFetcher,
    config: &SyncConfig,
    observer: &dyn SyncObserver,
) -> CoreResult<SyncSummary> {
    let mut summary = enrich(db, source, config, observer).await?;
    let images = run_image_sync(db, store, fetcher, &METADATA_IMAGE_SLOTS, KIND, observer).await?;
    summary.failed_images = images.failed;
    Ok(summary)
}

async fn enrich(
    db: &Database,
    source: &dyn MetadataSource,
    config: &SyncConfig,
    observer: &dyn SyncObserver,
) -> CoreResult<SyncSummary> {
    let pending = db.with_conn(unenriched)?;
    let total = len_i64(pending.len());
    db.with_conn(|conn| update_progress(conn, KIND, 0, total))?;

    let mut summary = SyncSummary {
        processed: 0,
        failed_batches: 0,
        failed_images: 0,
    };
    let mut done: i64 = 0;
    // Batchgröße 0 ist für chunks() unzulässig
    let batches: Vec<_> = pending.chunks(config.metadata_batch_size.max(1)).collect();
    for (index, batch) in batches.iter().enumerate() {
        let titles: Vec<String> = batch.iter().map(|(_, title)| title.clone()).collect();
        // Verarbeitete Titel zählen unabhängig vom Ergebnis, damit der Fortschritt total erreicht
        done += len_i64(batch.len());
        match source.search_batch(&titles).await {
            Ok(results) => {
                let applied = db.with_tx(|tx| {
                    let mut applied = 0;
                    for ((id, _), result) in batch.iter().zip(&results) {
                        if let Some(item) = result {
                            apply_metadata(tx, id, item)?;
                            applied += 1;
                        }
                    }
                    update_progress(tx, KIND, done, total)?;
                    Ok(applied)
                })?;
                summary.processed += applied;
            }
            Err(error @ CoreError::RateLimited { .. }) => return Err(error),
            Err(_) => {
                summary.failed_batches += 1;
                db.with_conn(|conn| update_progress(conn, KIND, done, total))?;
            }
        }
        observer.on_progress(SyncProgress {
            kind: KIND,
            phase: SyncPhase::Items,
            done,
            total,
        });
        if index + 1 < batches.len() {
            pause(config.metadata_batch_delay).await;
        }
    }
    Ok(summary)
}

fn len_i64(len: usize) -> i64 {
    i64::try_from(len).unwrap_or(i64::MAX)
}
