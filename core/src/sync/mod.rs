pub mod catalog_sync;
pub mod config;
pub mod enrichment;
pub mod images;
pub mod progress;
pub mod run_guard;

use std::future::{poll_fn, Future};
use std::panic::AssertUnwindSafe;
use std::pin::pin;
use std::task::Poll;
use std::time::{Duration, SystemTime, UNIX_EPOCH};

use crate::error::{CoreError, CoreResult};

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

/// Wandelt eine Panic beim Abfragen der Future in `CoreError::Internal`. Ohne das verwirft
/// UniFFI die Future samt RunGuard, und der Lauf erschiene als „Abgebrochen“ statt als Fehler.
pub(crate) async fn catch_panic<F: Future>(future: F) -> CoreResult<F::Output> {
    let mut future = pin!(future);
    poll_fn(move |cx| {
        match std::panic::catch_unwind(AssertUnwindSafe(|| future.as_mut().poll(cx))) {
            Ok(Poll::Ready(value)) => Poll::Ready(Ok(value)),
            Ok(Poll::Pending) => Poll::Pending,
            Err(payload) => Poll::Ready(Err(CoreError::Internal {
                detail: panic_message(payload.as_ref()),
            })),
        }
    })
    .await
}

fn panic_message(payload: &(dyn std::any::Any + Send)) -> String {
    payload
        .downcast_ref::<&str>()
        .map(|text| text.to_string())
        .or_else(|| payload.downcast_ref::<String>().cloned())
        .unwrap_or_else(|| "Panic ohne Text".to_string())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn catch_panic_turns_panic_into_internal_error() {
        let result = catch_panic(async {
            tokio::task::yield_now().await;
            panic!("kaputt");
        })
        .await;
        match result {
            Err(CoreError::Internal { detail }) => assert_eq!(detail, "kaputt"),
            other => panic!("Internal erwartet: {other:?}"),
        }
        assert_eq!(catch_panic(async { 5 }).await.unwrap(), 5);
    }
}
