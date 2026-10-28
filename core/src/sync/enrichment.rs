use crate::db::anime::{apply_metadata, mark_metadata_miss, unenriched};
use crate::db::images::ImageSlot;
use crate::db::sync_state::update_progress;
use crate::db::Database;
use crate::error::{CoreError, CoreResult};
use crate::images::fetch::ImageFetcher;
use crate::images::store::ImageStore;
use crate::model::{SyncKind, SyncPhase, SyncProgress, SyncSummary};
use crate::remote::metadata_source::MetadataSource;

use super::config::SyncConfig;
use super::images::{images_may_follow, merge_image_result, run_image_sync};
use super::progress::SyncObserver;
use super::run_guard::{RunGuard, RunOutcome};
use super::{catch_panic, now_unix, pause};

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
    let outcome = catch_panic(enrich_all(db, source, store, fetcher, config, observer))
        .await
        .unwrap_or_else(|error| RunOutcome::failed(SyncSummary::default(), error));
    guard.finish(outcome)
}

async fn enrich_all(
    db: &Database,
    source: &dyn MetadataSource,
    store: &ImageStore,
    fetcher: &dyn ImageFetcher,
    config: &SyncConfig,
    observer: &dyn SyncObserver,
) -> RunOutcome {
    let mut summary = SyncSummary::default();
    let error = enrich(db, source, config, observer, &mut summary)
        .await
        .err();
    let mut outcome = RunOutcome { summary, error };
    if images_may_follow(&outcome) {
        let images = run_image_sync(
            db,
            store,
            fetcher,
            &METADATA_IMAGE_SLOTS,
            KIND,
            config,
            observer,
        )
        .await;
        merge_image_result(&mut outcome, images);
    }
    outcome
}

async fn enrich(
    db: &Database,
    source: &dyn MetadataSource,
    config: &SyncConfig,
    observer: &dyn SyncObserver,
    summary: &mut SyncSummary,
) -> CoreResult<()> {
    let retry_secs = i64::try_from(config.metadata_miss_retry.as_secs()).unwrap_or(i64::MAX);
    let pending = db.with_conn(|conn| unenriched(conn, now_unix().saturating_sub(retry_secs)))?;
    let total = len_i64(pending.len());
    db.with_conn(|conn| update_progress(conn, KIND, 0, total))?;

    let mut done: i64 = 0;
    // Batchgröße 0 ist für chunks() unzulässig
    let batches: Vec<_> = pending.chunks(config.metadata_batch_size.max(1)).collect();
    for (index, batch) in batches.iter().enumerate() {
        let titles: Vec<String> = batch.iter().map(|(_, title)| title.clone()).collect();
        // Verarbeitete Titel zählen unabhängig vom Ergebnis, damit der Fortschritt total erreicht
        done += len_i64(batch.len());
        match source.search_batch(&titles).await {
            Ok(results) => {
                let now = now_unix();
                let applied = db.with_tx(|tx| {
                    let mut applied = 0;
                    for ((id, _), result) in batch.iter().zip(&results) {
                        match result {
                            Some(item) => {
                                apply_metadata(tx, id, item)?;
                                applied += 1;
                            }
                            None => mark_metadata_miss(tx, id, now)?,
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
    Ok(())
}

fn len_i64(len: usize) -> i64 {
    i64::try_from(len).unwrap_or(i64::MAX)
}
