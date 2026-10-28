use std::time::Duration;

use crate::images::encode::DecodeLimits;

const MIB: u64 = 1024 * 1024;

/// Interne Taktung und Grenzen der Sync-Läufe (nicht über UniFFI exportiert).
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct SyncConfig {
    pub catalog_page_size: u32,
    pub catalog_page_delay: Duration,
    pub metadata_batch_size: usize,
    pub metadata_batch_delay: Duration,
    /// Größte Bilddatei, die geladen wird; größere Antworten werden abgebrochen.
    pub image_max_download_bytes: u64,
    /// Grenzen beim Dekodieren von JPEG/PNG, damit ein manipuliertes Bild den Speicher nicht sprengt.
    pub image_decode_limits: DecodeLimits,
}

impl Default for SyncConfig {
    fn default() -> Self {
        Self {
            catalog_page_size: 50,
            catalog_page_delay: Duration::from_millis(100),
            metadata_batch_size: 10,
            // AniList erlaubt etwa 30 Anfragen pro Minute
            metadata_batch_delay: Duration::from_millis(2100),
            // Poster und Banner liegen typischerweise unter 1 MiB
            image_max_download_bytes: 16 * MIB,
            image_decode_limits: DecodeLimits {
                max_width: 4096,
                max_height: 4096,
                // 4096 x 4096 RGBA belegt 64 MiB; Spielraum für Zwischenpuffer des Dekoders
                max_alloc_bytes: 128 * MIB,
            },
        }
    }
}
