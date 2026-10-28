use crate::model::SyncProgress;

/// Wird von der UI-Seite implementiert und nach jeder Seite bzw. jedem Batch aufgerufen.
#[uniffi::export(with_foreign)]
pub trait SyncObserver: Send + Sync {
    fn on_progress(&self, progress: SyncProgress);
}
