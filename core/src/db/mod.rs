pub mod anime;
pub mod images;
pub mod schema;
pub mod settings;
pub mod sync_state;
#[cfg(test)]
pub(crate) mod test_support;

use std::sync::{Mutex, MutexGuard, PoisonError};

use rusqlite::functions::FunctionFlags;
use rusqlite::{Connection, Transaction};

use crate::error::CoreResult;

/// Name, unter dem die Nutzer-Datenbank an die Katalog-Verbindung angehängt ist (`userdata.marks`).
pub const USER_SCHEMA: &str = "userdata";

/// Eine Verbindung auf zwei Dateien: der Katalog ist ein jederzeit neu ladbarer Cache, die
/// Nutzer-Datenbank (Einstellungen, Markierungen) wird gesichert. Beide liegen auf derselben
/// Verbindung, damit Filter, Sortierung und Zähler über Markierungen in einer Abfrage laufen.
pub struct Database {
    conn: Mutex<Connection>,
}

impl Database {
    pub fn open(catalog_path: &str, user_path: &str) -> CoreResult<Self> {
        let conn = Connection::open(catalog_path)?;
        attach_user_db(&conn, user_path)?;
        // Vor jedem Schreibzugriff: eine Datei aus einer neueren App-Version bleibt unverändert
        schema::ensure_supported(&conn)?;
        conn.pragma_update(None, "journal_mode", "WAL")?;
        // Rollback-Journal statt WAL: nach jedem Commit steht alles in einer Datei, sodass das
        // Android-Backup keinen Stand aus Haupt- und WAL-Datei zusammensetzen muss.
        conn.pragma_update(Some(USER_SCHEMA), "journal_mode", "DELETE")?;
        Self::prepare(conn)
    }

    pub fn open_in_memory() -> CoreResult<Self> {
        let conn = Connection::open_in_memory()?;
        attach_user_db(&conn, ":memory:")?;
        Self::prepare(conn)
    }

    fn prepare(conn: Connection) -> CoreResult<Self> {
        conn.pragma_update(None, "foreign_keys", "ON")?;
        register_fold(&conn)?;
        schema::migrate(&conn)?;
        Ok(Self {
            conn: Mutex::new(conn),
        })
    }

    // Eine Panic in einem Aufrufer vergiftet den Mutex, lässt die Verbindung aber konsistent zurück:
    // eine offene Transaktion rollt beim Unwinding über ihr Drop zurück. Würde die Vergiftung zum
    // Dauerfehler, bliebe die App bis zum Prozessneustart ohne Datenzugriff.
    fn lock(&self) -> MutexGuard<'_, Connection> {
        self.conn.lock().unwrap_or_else(PoisonError::into_inner)
    }

    pub fn with_conn<T>(&self, f: impl FnOnce(&Connection) -> CoreResult<T>) -> CoreResult<T> {
        let guard = self.lock();
        f(&guard)
    }

    pub fn with_tx<T>(&self, f: impl FnOnce(&Transaction) -> CoreResult<T>) -> CoreResult<T> {
        let mut guard = self.lock();
        let tx = guard.transaction()?;
        let value = f(&tx)?;
        tx.commit()?;
        Ok(value)
    }
}

fn attach_user_db(conn: &Connection, path: &str) -> CoreResult<()> {
    conn.execute(&format!("ATTACH DATABASE ?1 AS {USER_SCHEMA}"), [path])?;
    Ok(())
}

// SQLite lowercased nur ASCII; fold sorgt für Umlaute bei der Suche.
fn register_fold(conn: &Connection) -> CoreResult<()> {
    conn.create_scalar_function(
        "fold",
        1,
        FunctionFlags::SQLITE_UTF8 | FunctionFlags::SQLITE_DETERMINISTIC,
        |ctx| {
            let text: Option<String> = ctx.get(0)?;
            Ok(text.map(|value| value.to_lowercase()))
        },
    )?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use std::panic::{catch_unwind, AssertUnwindSafe};

    use super::*;

    #[test]
    fn panic_in_with_conn_does_not_break_later_calls() {
        let db = Database::open_in_memory().unwrap();
        let result = catch_unwind(AssertUnwindSafe(|| {
            let _ = db.with_conn(|_| -> CoreResult<()> { panic!("absichtlich") });
        }));
        assert!(result.is_err());

        let value: i64 = db
            .with_conn(|c| Ok(c.query_row("SELECT 41 + 1", [], |r| r.get(0))?))
            .unwrap();
        assert_eq!(value, 42);
    }

    #[test]
    fn panic_in_with_tx_rolls_back_and_later_calls_work() {
        let db = Database::open_in_memory().unwrap();
        let result = catch_unwind(AssertUnwindSafe(|| {
            let _ = db.with_tx(|tx| -> CoreResult<()> {
                tx.execute("INSERT INTO anime (id, title) VALUES ('a', 'A')", [])?;
                panic!("absichtlich");
            });
        }));
        assert!(result.is_err());

        let rows: i64 = db
            .with_conn(|c| Ok(c.query_row("SELECT COUNT(*) FROM anime", [], |r| r.get(0))?))
            .unwrap();
        assert_eq!(rows, 0, "die halbe Transaktion darf nicht stehen bleiben");
    }
}
