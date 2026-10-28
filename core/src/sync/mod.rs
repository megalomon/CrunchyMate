pub mod catalog_sync;
pub mod config;
pub mod enrichment;
pub mod images;
pub mod progress;
pub mod run_guard;

use std::time::{Duration, SystemTime, UNIX_EPOCH};

/// Aktuelle Unix-Zeit in Sekunden; vor 1970 (falsch gestellte Uhr) gilt 0.
pub fn now_unix() -> i64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| i64::try_from(d.as_secs()).unwrap_or(i64::MAX))
        .unwrap_or(0)
}

/// Pause zwischen Anfragen; bei null wird gar nicht erst geschlafen.
pub(crate) async fn pause(delay: Duration) {
    if !delay.is_zero() {
        tokio::time::sleep(delay).await;
    }
}
