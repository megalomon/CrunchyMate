use crate::error::CoreResult;
use crate::model::MetadataItem;

/// Quelle für Anime-Metadaten. Das Ergebnis steht in Eingabereihenfolge,
/// `None` bedeutet „kein Treffer“.
#[async_trait::async_trait]
pub trait MetadataSource: Send + Sync {
    async fn search_batch(&self, titles: &[String]) -> CoreResult<Vec<Option<MetadataItem>>>;
}
