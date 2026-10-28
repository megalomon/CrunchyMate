//! Phase „Bilder“: lädt fehlende oder geänderte Bilder und räumt nicht mehr benötigte ab.

use crate::db::images::{
    delete_image, known_hashes, pending, record, unreferenced, ImageSlot, PendingImage,
};
use crate::db::Database;
use crate::error::{CoreError, CoreResult};
use crate::images::encode::{encode_for_storage, DecodeLimits, EncodeError, StoredImage};
use crate::images::fetch::{FetchError, ImageFetcher};
use crate::images::store::ImageStore;
use crate::model::{SyncKind, SyncPhase, SyncProgress};

use super::config::SyncConfig;
use super::progress::SyncObserver;
use super::run_guard::RunOutcome;

#[derive(Debug, Clone, Default, PartialEq, Eq)]
pub struct ImageSyncSummary {
    pub downloaded: i64,
    pub failed: i32,
    /// Über den Größengrenzen; nicht gespeichert und beim nächsten Lauf erneut offen.
    pub skipped: i32,
}

/// Datei im Speicher samt Angaben für die Datenbank.
struct StoredFile {
    hash: String,
    ext: &'static str,
    bytes: i64,
}

/// Lädt die Bilder der angegebenen Arten nacheinander. Ein Verweis entsteht erst nach vollständig
/// geschriebener Datei und wird in einer Transaktion gesetzt; ein Abbruch behält daher alles
/// bereits Gespeicherte. Fehlgeschlagene Bilder bekommen keinen Verweis und sind beim nächsten
/// Lauf wieder offen.
pub async fn run_image_sync(
    db: &Database,
    store: &ImageStore,
    fetcher: &dyn ImageFetcher,
    slots: &[ImageSlot],
    kind: SyncKind,
    config: &SyncConfig,
    observer: &dyn SyncObserver,
) -> CoreResult<ImageSyncSummary> {
    // Eine Bild-Phase zur Zeit: Ablage und Aufräumen zweier Läufe würden sich sonst gegenseitig stören
    let _phase = store.phase_lock().lock().await;
    let jobs = db.with_conn(|conn| pending(conn, slots))?;
    let total = i64::try_from(jobs.len()).unwrap_or(i64::MAX);
    let mut summary = ImageSyncSummary::default();
    for (index, job) in jobs.iter().enumerate() {
        match download_one(db, store, fetcher, job, config).await {
            Ok(()) => summary.downloaded += 1,
            Err(DownloadError::Skipped) => summary.failed += 1,
            Err(DownloadError::TooLarge) => summary.skipped += 1,
            Err(DownloadError::Fatal(error)) => return Err(error),
        }
        // Auch fehlgeschlagene Bilder gelten als verarbeitet, damit der Fortschritt total erreicht
        let done = i64::try_from(index + 1).unwrap_or(i64::MAX);
        observer.on_progress(SyncProgress {
            kind,
            phase: SyncPhase::Images,
            done,
            total,
        });
    }
    if jobs.is_empty() {
        // Damit die Oberfläche den Abschnitt „Daten“ verlässt
        observer.on_progress(SyncProgress {
            kind,
            phase: SyncPhase::Images,
            done: 0,
            total: 0,
        });
    }
    remove_unreferenced(db, store)?;
    Ok(summary)
}

/// Ob nach der Daten-Phase noch Bilder geladen und aufgeräumt werden.
///
/// Auch nach einem Fehler der Daten-Phase laufen Bilder und Aufräumen für die bereits
/// gespeicherten Einträge: jede Seite bzw. jeder Batch ist zu dem Zeitpunkt vollständig
/// festgeschrieben, die Bild-Phase liest nur diesen Stand und setzt Verweise erst nach
/// vollständig geschriebener Datei; das Aufräumen entfernt nur Bilder ohne Verweis. Ausgenommen
/// sind Fehler, die die Bild-Phase genauso träfen (Datenbank, Speicher) oder auf einen
/// Programmfehler deuten.
pub fn images_may_follow(items: &RunOutcome) -> bool {
    !items.error.as_ref().is_some_and(|error| {
        matches!(
            error,
            CoreError::Database { .. }
                | CoreError::Storage { .. }
                | CoreError::UnsupportedSchema { .. }
                | CoreError::Internal { .. }
                | CoreError::Cancelled
        )
    })
}

/// Trägt das Ergebnis der Bild-Phase ein; ein Fehler der Daten-Phase bleibt maßgeblich.
pub fn merge_image_result(outcome: &mut RunOutcome, images: CoreResult<ImageSyncSummary>) {
    match images {
        Ok(images) => {
            outcome.summary.failed_images = images.failed;
            outcome.summary.skipped_images = images.skipped;
        }
        Err(error) => {
            outcome.error.get_or_insert(error);
        }
    }
}

enum DownloadError {
    /// Nur dieses Bild scheitert; der Lauf geht weiter.
    Skipped,
    /// Bild über den Größengrenzen; wird gesondert gezählt.
    TooLarge,
    /// Datenbank oder Ablage sind betroffen; weitermachen hätte keinen Zweck.
    Fatal(CoreError),
}

async fn download_one(
    db: &Database,
    store: &ImageStore,
    fetcher: &dyn ImageFetcher,
    job: &PendingImage,
    config: &SyncConfig,
) -> Result<(), DownloadError> {
    let raw = fetcher
        .fetch(&job.source_url, config.image_max_download_bytes)
        .await
        .map_err(|error| match error {
            FetchError::TooLarge => DownloadError::TooLarge,
            FetchError::Failed(_) => DownloadError::Skipped,
        })?;
    // Schutz auch gegen eine Quelle, die die Grenze nicht selbst einhält
    if u64::try_from(raw.len()).unwrap_or(u64::MAX) > config.image_max_download_bytes {
        return Err(DownloadError::TooLarge);
    }
    let file = encode_and_store(store, raw, config.image_decode_limits).await?;
    db.with_tx(|tx| {
        record(
            tx,
            &job.anime_id,
            job.slot,
            &job.source_url,
            &file.hash,
            file.ext,
            file.bytes,
        )
    })
    .map_err(DownloadError::Fatal)
}

// Umwandeln und Schreiben sind rechenintensiv bzw. blockierend und laufen daher abseits der Async-Threads.
// Ein unlesbares Bild betrifft nur dieses Bild; scheitert die Ablage (z. B. Speicher voll), hat
// jedes weitere Bild dasselbe Problem und der Lauf bricht mit dem Fehler ab.
async fn encode_and_store(
    store: &ImageStore,
    raw: Vec<u8>,
    limits: DecodeLimits,
) -> Result<StoredFile, DownloadError> {
    let store = store.clone();
    tokio::task::spawn_blocking(move || {
        let image: StoredImage = encode_for_storage(&raw, limits).map_err(|e| match e {
            EncodeError::TooLarge => DownloadError::TooLarge,
            EncodeError::Unreadable(_) => DownloadError::Skipped,
        })?;
        let bytes = i64::try_from(image.bytes.len()).unwrap_or(i64::MAX);
        let hash = store.put(&image).map_err(DownloadError::Fatal)?;
        Ok(StoredFile {
            hash,
            ext: image.ext,
            bytes,
        })
    })
    .await
    .unwrap_or_else(|error| {
        // Panic oder Abbruch der Hintergrundaufgabe: ein Programmfehler, kein Speicherproblem
        Err(DownloadError::Fatal(CoreError::Internal {
            detail: format!("Bildverarbeitung abgebrochen: {error}"),
        }))
    })
}

/// Entfernt Bildzeilen ohne Verweis samt Datei sowie Dateien ohne Zeile (Reste eines Abbruchs
/// zwischen Schreiben und Speichern). Die Zeile wird nur bedingt gelöscht und zuerst: bleibt eine
/// Datei übrig, schadet das nicht, eine Zeile ohne Datei würde dagegen ein totes Bild melden.
/// Scheitert das Löschen einer Datei, läuft der Lauf weiter: die Zeile ist schon fort, die Datei
/// gilt beim Verzeichnisabgleich des nächsten Laufs als verwaist und wird dann erneut entfernt.
fn remove_unreferenced(db: &Database, store: &ImageStore) -> CoreResult<()> {
    for (hash, ext) in db.with_conn(unreferenced)? {
        if db.with_conn(|conn| delete_image(conn, &hash))? {
            let _ = store.remove(&hash, &ext);
        }
    }
    let known = db.with_conn(known_hashes)?;
    for (hash, ext) in store.list_files()? {
        if !known.contains(&hash) {
            let _ = store.remove(&hash, &ext);
        }
    }
    Ok(())
}
