use crate::db::sync_state::{mark_finished, mark_running};
use crate::db::Database;
use crate::error::{CoreResult, ErrorCode};
use crate::model::{SyncKind, SyncRunState};

use super::now_unix;

/// Hält einen laufenden Sync. Wird der Guard ohne `complete` verworfen
/// (z. B. weil die Future abgebrochen wurde), gilt der Lauf als abgebrochen.
pub struct RunGuard<'a> {
    db: &'a Database,
    kind: SyncKind,
    finished: bool,
}

impl<'a> RunGuard<'a> {
    /// Setzt den Lauf auf `running` und liefert den Guard.
    pub fn start(db: &'a Database, kind: SyncKind, total: i64) -> CoreResult<Self> {
        db.with_conn(|conn| mark_running(conn, kind, total))?;
        Ok(Self {
            db,
            kind,
            finished: false,
        })
    }

    /// Schreibt den Abschluss des Laufs.
    pub fn complete(mut self, state: SyncRunState, error: Option<ErrorCode>) -> CoreResult<()> {
        self.finished = true;
        let kind = self.kind;
        self.db
            .with_conn(|conn| mark_finished(conn, kind, state, error, now_unix()))
    }
}

impl RunGuard<'_> {
    /// Schließt den Lauf passend zum Ergebnis ab: `Completed` oder `Failed` mit Fehlercode.
    /// Scheitert das Schreiben nach einem Fehler, bleibt der ursprüngliche Fehler maßgeblich.
    pub fn finish<T>(self, result: CoreResult<T>) -> CoreResult<T> {
        match result {
            Ok(value) => {
                self.complete(SyncRunState::Completed, None)?;
                Ok(value)
            }
            Err(error) => {
                let _ = self.complete(SyncRunState::Failed, Some(error.code()));
                Err(error)
            }
        }
    }
}

impl Drop for RunGuard<'_> {
    fn drop(&mut self) {
        if self.finished {
            return;
        }
        let kind = self.kind;
        // Fehler lassen sich aus Drop nicht weitergeben; der Start-Reset fängt das ab.
        let _ = self.db.with_conn(|conn| {
            mark_finished(
                conn,
                kind,
                SyncRunState::Cancelled,
                Some(ErrorCode::Cancelled),
                now_unix(),
            )
        });
    }
}
