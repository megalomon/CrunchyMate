//! Gemeinsame Hilfen der Integrationstests: Fake-Download und temporäre Bildablage.
#![allow(dead_code)]

use std::collections::{HashMap, HashSet};
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::Mutex;

use crunchymate_core::error::CoreError;
use crunchymate_core::images::fetch::{FetchError, ImageFetcher};
use crunchymate_core::images::store::ImageStore;

static COUNTER: AtomicU64 = AtomicU64::new(0);

/// Temporäres Verzeichnis, das beim Verlassen gelöscht wird.
pub struct TempDir(PathBuf);

impl TempDir {
    pub fn new(name: &str) -> Self {
        let n = COUNTER.fetch_add(1, Ordering::Relaxed);
        let dir =
            std::env::temp_dir().join(format!("crunchymate-it-{name}-{}-{n}", std::process::id()));
        let _ = std::fs::remove_dir_all(&dir);
        Self(dir)
    }

    pub fn path(&self) -> &Path {
        &self.0
    }

    pub fn store(&self) -> ImageStore {
        ImageStore::new(&self.0).unwrap()
    }
}

impl Drop for TempDir {
    fn drop(&mut self) {
        let _ = std::fs::remove_dir_all(&self.0);
    }
}

/// Minimales WebP-Gerüst; wird unverändert gespeichert, `tag` macht den Inhalt eindeutig.
pub fn webp_bytes(tag: &str) -> Vec<u8> {
    let mut bytes = b"RIFF\0\0\0\0WEBP".to_vec();
    bytes.extend_from_slice(tag.as_bytes());
    bytes
}

/// Download-Fake: bekannte URLs liefern Bytes, `fail` scheitert, `block` hängt für immer.
#[derive(Default)]
pub struct FakeFetcher {
    bodies: Mutex<HashMap<String, Vec<u8>>>,
    failing: Mutex<HashSet<String>>,
    blocking: Mutex<HashSet<String>>,
    calls: Mutex<Vec<String>>,
}

impl FakeFetcher {
    pub fn serve(&self, url: &str, bytes: Vec<u8>) {
        self.bodies.lock().unwrap().insert(url.to_string(), bytes);
    }

    pub fn fail(&self, url: &str) {
        self.failing.lock().unwrap().insert(url.to_string());
    }

    pub fn heal(&self, url: &str) {
        self.failing.lock().unwrap().remove(url);
    }

    pub fn block(&self, url: &str) {
        self.blocking.lock().unwrap().insert(url.to_string());
    }

    pub fn calls(&self) -> Vec<String> {
        self.calls.lock().unwrap().clone()
    }
}

#[async_trait::async_trait]
impl ImageFetcher for FakeFetcher {
    async fn fetch(&self, url: &str, max_bytes: u64) -> Result<Vec<u8>, FetchError> {
        self.calls.lock().unwrap().push(url.to_string());
        if self.blocking.lock().unwrap().contains(url) {
            let never = tokio::sync::Notify::new();
            never.notified().await;
        }
        if self.failing.lock().unwrap().contains(url) {
            return Err(FetchError::Failed(CoreError::Network {
                detail: "weg".to_string(),
            }));
        }
        let body = self
            .bodies
            .lock()
            .unwrap()
            .get(url)
            .cloned()
            .ok_or_else(|| {
                FetchError::Failed(CoreError::Network {
                    detail: format!("unbekannte URL {url}"),
                })
            })?;
        if body.len() as u64 > max_bytes {
            return Err(FetchError::TooLarge);
        }
        Ok(body)
    }
}
