pub mod anime;
pub mod schema;
pub mod settings;
pub mod sync_state;
#[cfg(test)]
pub(crate) mod test_support;

use std::sync::Mutex;

use rusqlite::functions::FunctionFlags;
use rusqlite::{Connection, Transaction};

use crate::error::{CoreError, CoreResult};

pub struct Database {
    conn: Mutex<Connection>,
}

impl Database {
    pub fn open(path: &str) -> CoreResult<Self> {
        let conn = Connection::open(path)?;
        conn.pragma_update(None, "journal_mode", "WAL")?;
        Self::prepare(conn)
    }

    pub fn open_in_memory() -> CoreResult<Self> {
        Self::prepare(Connection::open_in_memory()?)
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

fn poisoned() -> CoreError {
    CoreError::Database {
        message: "Datenbankverbindung ist nicht mehr nutzbar".to_string(),
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
