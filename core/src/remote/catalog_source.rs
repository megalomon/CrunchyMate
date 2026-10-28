use crate::error::CoreResult;
use crate::model::CatalogPage;

/// Quelle für den Streaming-Katalog.
#[async_trait::async_trait]
pub trait CatalogSource: Send + Sync {
    /// Land der aktuellen Sitzung in Großbuchstaben (z. B. „DE“).
    async fn session_country(&self) -> CoreResult<String>;
    /// Lädt eine Katalogseite ab `start` mit höchstens `count` Einträgen.
    async fn fetch_page(&self, start: u32, count: u32, locale: &str) -> CoreResult<CatalogPage>;
}
