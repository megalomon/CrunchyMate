use std::time::Duration;

/// Interne Taktung der Sync-Läufe (nicht über UniFFI exportiert).
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct SyncConfig {
    pub catalog_page_size: u32,
    pub catalog_page_delay: Duration,
    pub metadata_batch_size: usize,
    pub metadata_batch_delay: Duration,
}

impl Default for SyncConfig {
    fn default() -> Self {
        Self {
            catalog_page_size: 50,
            catalog_page_delay: Duration::from_millis(100),
            metadata_batch_size: 10,
            // AniList erlaubt etwa 30 Anfragen pro Minute
            metadata_batch_delay: Duration::from_millis(2100),
        }
    }
}
