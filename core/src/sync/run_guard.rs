use crate::db::sync_state::{mark_finished, mark_running};
use crate::db::Database;
use crate::error::{CoreError, CoreResult, ErrorCode};
use crate::model::{SyncKind, SyncRunState, SyncSummary};

use super::now_unix;

/// Ergebnis eines Laufs; auch ein gescheiterter Lauf hat ein Teilergebnis, das gezählt wird.
#[derive(Debug)]
pub struct RunOutcome {
    pub summary: SyncSummary,
    pub error: Option<CoreError>,
}

impl RunOutcome {
    pub fn failed(summary: SyncSummary, error: CoreError) -> Self {
        Self {
            summary,
            error: Some(error),
        }
    }
}

/// Hält einen laufenden Sync. Wird der Guard ohne `finish` verworfen
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

    /// Schließt den Lauf passend zum Ergebnis ab: `Completed` oder `Failed` mit Fehlercode,
    /// jeweils mit Ergebnis. Scheitert das Schreiben nach einem Fehler, bleibt der ursprüngliche
    /// Fehler maßgeblich.
    pub fn finish(mut self, outcome: RunOutcome) -> CoreResult<SyncSummary> {
        self.finished = true;
        let RunOutcome { summary, error } = outcome;
        match error {
            None => {
                self.write(SyncRunState::Completed, None, &summary)?;
                Ok(summary)
            }
            Some(error) => {
                let _ = self.write(SyncRunState::Failed, Some(error.code()), &summary);
                Err(error)
            }
        }
    }

    fn write(
        &self,
        state: SyncRunState,
        error: Option<ErrorCode>,
        summary: &SyncSummary,
    ) -> CoreResult<()> {
        let kind = self.kind;
        self.db
            .with_conn(|conn| mark_finished(conn, kind, state, error, Some(summary), now_unix()))
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
                None,
                now_unix(),
            )
        });
    }
}
