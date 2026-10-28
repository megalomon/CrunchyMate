pub mod anime;
pub mod images;
pub mod schema;
pub mod settings;
pub mod sync_state;
#[cfg(test)]
pub(crate) mod test_support;

use std::sync::Mutex;

use rusqlite::functions::FunctionFlags;
use rusqlite::{Connection, Transaction};

use crate::error::{CoreError, CoreResult};

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

    pub fn with_conn<T>(&self, f: impl FnOnce(&Connection) -> CoreResult<T>) -> CoreResult<T> {
        let guard = self.conn.lock().map_err(|_| poisoned())?;
        f(&guard)
    }

    pub fn with_tx<T>(&self, f: impl FnOnce(&Transaction) -> CoreResult<T>) -> CoreResult<T> {
        let mut guard = self.conn.lock().map_err(|_| poisoned())?;
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

fn poisoned() -> CoreError {
    CoreError::Database {
        detail: "Datenbankverbindung ist nicht mehr nutzbar".to_string(),
    }
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
