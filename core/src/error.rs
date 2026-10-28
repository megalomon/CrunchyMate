#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum CoreError {
    #[error("Netzwerkfehler: {message}")]
    Network { message: String },
    #[error("Zu viele Anfragen, erneut versuchen in {retry_after_secs} s")]
    RateLimited { retry_after_secs: i64 },
    #[error("Authentifizierung fehlgeschlagen: {message}")]
    Auth { message: String },
    #[error("Antwort nicht lesbar: {message}")]
    Parse { message: String },
    #[error("Datenbankfehler: {message}")]
    Database { message: String },
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
            message: error.to_string(),
        }
    }
}

impl From<reqwest::Error> for CoreError {
    fn from(error: reqwest::Error) -> Self {
        CoreError::Network {
            message: error.to_string(),
        }
    }
}

impl From<serde_json::Error> for CoreError {
    fn from(error: serde_json::Error) -> Self {
        CoreError::Parse {
            message: error.to_string(),
        }
    }
}

pub type CoreResult<T> = Result<T, CoreError>;
