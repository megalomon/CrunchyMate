// Feldname "detail" statt "message": UniFFI erzeugt daraus Kotlin-Exceptions, deren
// Property "message" mit Throwable.message kollidieren würde.
#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum CoreError {
    #[error("Netzwerkfehler: {detail}")]
    Network { detail: String },
    #[error("Zu viele Anfragen, erneut versuchen in {retry_after_secs} s")]
    RateLimited { retry_after_secs: i64 },
    #[error("Authentifizierung fehlgeschlagen: {detail}")]
    Auth { detail: String },
    #[error("Antwort nicht lesbar: {detail}")]
    Parse { detail: String },
    #[error("Datenbankfehler: {detail}")]
    Database { detail: String },
    #[error("Abgebrochen")]
    Cancelled,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum ErrorCode {
    Network,
    RateLimited,
    Auth,
    Parse,
    Database,
    Cancelled,
}

impl CoreError {
    pub fn code(&self) -> ErrorCode {
        match self {
            CoreError::Network { .. } => ErrorCode::Network,
            CoreError::RateLimited { .. } => ErrorCode::RateLimited,
            CoreError::Auth { .. } => ErrorCode::Auth,
            CoreError::Parse { .. } => ErrorCode::Parse,
            CoreError::Database { .. } => ErrorCode::Database,
            CoreError::Cancelled => ErrorCode::Cancelled,
        }
    }
}

impl From<rusqlite::Error> for CoreError {
    fn from(error: rusqlite::Error) -> Self {
        CoreError::Database {
            detail: error.to_string(),
        }
    }
}

impl From<reqwest::Error> for CoreError {
    fn from(error: reqwest::Error) -> Self {
        CoreError::Network {
            detail: error.to_string(),
        }
    }
}

impl From<serde_json::Error> for CoreError {
    fn from(error: serde_json::Error) -> Self {
        CoreError::Parse {
            detail: error.to_string(),
        }
    }
}

pub type CoreResult<T> = Result<T, CoreError>;
