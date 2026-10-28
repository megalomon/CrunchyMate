use rusqlite::{params, Connection, OptionalExtension};

use crate::error::{CoreResult, ErrorCode};
use crate::model::{SyncKind, SyncRunState, SyncStatus};

// Stabile Textwerte in der Datenbank; bewusst unabhängig von Debug und Variantennamen.
fn kind_str(kind: SyncKind) -> &'static str {
    match kind {
        SyncKind::Catalog => "catalog",
        SyncKind::Metadata => "metadata",
    }
}

fn state_str(state: SyncRunState) -> &'static str {
    match state {
        SyncRunState::Idle => "idle",
        SyncRunState::Running => "running",
        SyncRunState::Completed => "completed",
        SyncRunState::Failed => "failed",
        SyncRunState::Cancelled => "cancelled",
    }
}

fn state_from_str(text: &str) -> SyncRunState {
    match text {
        "running" => SyncRunState::Running,
        "completed" => SyncRunState::Completed,
        "failed" => SyncRunState::Failed,
        "cancelled" => SyncRunState::Cancelled,
        _ => SyncRunState::Idle,
    }
}

fn error_str(code: ErrorCode) -> &'static str {
    match code {
        ErrorCode::Network => "network",
        ErrorCode::RateLimited => "rate_limited",
        ErrorCode::Auth => "auth",
        ErrorCode::Parse => "parse",
        ErrorCode::Database => "database",
        ErrorCode::Cancelled => "cancelled",
    }
}

fn error_from_str(text: &str) -> Option<ErrorCode> {
    match text {
        "network" => Some(ErrorCode::Network),
        "rate_limited" => Some(ErrorCode::RateLimited),
        "auth" => Some(ErrorCode::Auth),
        "parse" => Some(ErrorCode::Parse),
        "database" => Some(ErrorCode::Database),
        "cancelled" => Some(ErrorCode::Cancelled),
        _ => None,
    }
}

pub fn status(conn: &Connection, kind: SyncKind) -> CoreResult<SyncStatus> {
    let row = conn
        .query_row(
            "SELECT state, done, total, last_finished_at, last_error, catalog_country
             FROM sync_state WHERE kind = ?1",
            params![kind_str(kind)],
            |row| {
                Ok((
                    row.get::<_, String>(0)?,
                    row.get::<_, i64>(1)?,
                    row.get::<_, i64>(2)?,
                    row.get::<_, Option<i64>>(3)?,
                    row.get::<_, Option<String>>(4)?,
                    row.get::<_, Option<String>>(5)?,
                ))
            },
        )
        .optional()?;
    Ok(match row {
        Some((state, done, total, last_finished_at, last_error, catalog_country)) => SyncStatus {
            kind,
            state: state_from_str(&state),
            done,
            total,
            last_finished_at,
            last_error: last_error.as_deref().and_then(error_from_str),
            catalog_country,
        },
        None => SyncStatus {
            kind,
            state: SyncRunState::Idle,
            done: 0,
            total: 0,
            last_finished_at: None,
            last_error: None,
            catalog_country: None,
        },
    })
}

/// Startet einen Lauf: done = 0, Fehler gelöscht, catalog_country bleibt erhalten.
pub fn mark_running(conn: &Connection, kind: SyncKind, total: i64) -> CoreResult<()> {
    conn.execute(
        "INSERT INTO sync_state(kind, state, done, total, last_error)
         VALUES (?1, 'running', 0, ?2, NULL)
         ON CONFLICT(kind) DO UPDATE SET
            state = 'running', done = 0, total = excluded.total, last_error = NULL",
        params![kind_str(kind), total],
    )?;
    Ok(())
}

pub fn update_progress(conn: &Connection, kind: SyncKind, done: i64, total: i64) -> CoreResult<()> {
    conn.execute(
        "INSERT INTO sync_state(kind, done, total) VALUES (?1, ?2, ?3)
         ON CONFLICT(kind) DO UPDATE SET done = excluded.done, total = excluded.total",
        params![kind_str(kind), done, total],
    )?;
    Ok(())
}

/// Beendet einen Lauf; done/total bleiben erhalten.
pub fn mark_finished(
    conn: &Connection,
    kind: SyncKind,
    state: SyncRunState,
    error: Option<ErrorCode>,
    now: i64,
) -> CoreResult<()> {
    conn.execute(
        "INSERT INTO sync_state(kind, state, last_finished_at, last_error)
         VALUES (?1, ?2, ?3, ?4)
         ON CONFLICT(kind) DO UPDATE SET
            state = excluded.state,
            last_finished_at = excluded.last_finished_at,
            last_error = excluded.last_error",
        params![kind_str(kind), state_str(state), now, error.map(error_str)],
    )?;
    Ok(())
}

pub fn set_catalog_country(conn: &Connection, country: &str) -> CoreResult<()> {
    conn.execute(
        "INSERT INTO sync_state(kind, catalog_country) VALUES ('catalog', ?1)
         ON CONFLICT(kind) DO UPDATE SET catalog_country = excluded.catalog_country",
        params![country],
    )?;
    Ok(())
}

/// Beim Start: Läufe, die mit dem Prozess beendet wurden, gelten als abgebrochen.
pub fn reset_orphaned_runs(conn: &Connection, now: i64) -> CoreResult<()> {
    conn.execute(
        "UPDATE sync_state SET state = 'cancelled', last_finished_at = ?1
         WHERE state = 'running'",
        params![now],
    )?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::db::Database;

    #[test]
    fn status_idle_when_missing() {
        let db = Database::open_in_memory().unwrap();
        let s = db.with_conn(|c| status(c, SyncKind::Catalog)).unwrap();
        assert_eq!(
            s,
            SyncStatus {
                kind: SyncKind::Catalog,
                state: SyncRunState::Idle,
                done: 0,
                total: 0,
                last_finished_at: None,
                last_error: None,
                catalog_country: None,
            }
        );
    }

    #[test]
    fn running_then_failed_keeps_progress_and_error() {
        let db = Database::open_in_memory().unwrap();
        db.with_conn(|c| {
            mark_running(c, SyncKind::Metadata, 10)?;
            update_progress(c, SyncKind::Metadata, 4, 12)?;
            let s = status(c, SyncKind::Metadata)?;
            assert_eq!((s.state, s.done, s.total), (SyncRunState::Running, 4, 12));
            mark_finished(
                c,
                SyncKind::Metadata,
                SyncRunState::Failed,
                Some(ErrorCode::RateLimited),
                500,
            )?;
            let s = status(c, SyncKind::Metadata)?;
            assert_eq!(s.state, SyncRunState::Failed);
            assert_eq!((s.done, s.total), (4, 12));
            assert_eq!(s.last_error, Some(ErrorCode::RateLimited));
            assert_eq!(s.last_finished_at, Some(500));
            // Neuer Lauf setzt zurück und löscht den Fehler
            mark_running(c, SyncKind::Metadata, 7)?;
            let s = status(c, SyncKind::Metadata)?;
            assert_eq!((s.done, s.total, s.last_error), (0, 7, None));
            Ok(())
        })
        .unwrap();
    }

    #[test]
    fn reset_orphaned_runs_marks_running_as_cancelled() {
        let db = Database::open_in_memory().unwrap();
        db.with_conn(|c| {
            mark_running(c, SyncKind::Catalog, 5)?;
            mark_running(c, SyncKind::Metadata, 5)?;
            mark_finished(c, SyncKind::Metadata, SyncRunState::Completed, None, 100)?;
            reset_orphaned_runs(c, 999)?;
            let cat = status(c, SyncKind::Catalog)?;
            assert_eq!(cat.state, SyncRunState::Cancelled);
            assert_eq!(cat.last_finished_at, Some(999));
            let meta = status(c, SyncKind::Metadata)?;
            assert_eq!(meta.state, SyncRunState::Completed);
            assert_eq!(meta.last_finished_at, Some(100));
            Ok(())
        })
        .unwrap();
    }

    #[test]
    fn catalog_country_persisted() {
        let db = Database::open_in_memory().unwrap();
        db.with_conn(|c| {
            set_catalog_country(c, "DE")?;
            mark_running(c, SyncKind::Catalog, 3)?;
            assert_eq!(
                status(c, SyncKind::Catalog)?.catalog_country.as_deref(),
                Some("DE")
            );
            assert_eq!(status(c, SyncKind::Metadata)?.catalog_country, None);
            Ok(())
        })
        .unwrap();
    }

    #[test]
    fn unknown_stored_text_is_tolerated() {
        let db = Database::open_in_memory().unwrap();
        db.with_conn(|c| {
            c.execute(
                "INSERT INTO sync_state(kind, state, last_error) VALUES ('catalog', 'zzz', 'qqq')",
                [],
            )?;
            let s = status(c, SyncKind::Catalog)?;
            assert_eq!((s.state, s.last_error), (SyncRunState::Idle, None));
            Ok(())
        })
        .unwrap();
    }
}
