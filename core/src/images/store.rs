//! Inhaltsadressierte Ablage der Bilddateien im App-Speicher.

use std::fs;
use std::io::Write;
use std::path::PathBuf;
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::Arc;

use sha2::{Digest, Sha256};

use super::encode::StoredImage;
use crate::error::{CoreError, CoreResult};

/// Unterverzeichnis für halb geschriebene Dateien; nur dort können Reste eines Abbruchs liegen.
const TMP_DIR: &str = "tmp";
const TMP_SUFFIX: &str = ".part";

#[derive(Debug, Clone)]
pub struct ImageStore {
    dir: PathBuf,
    // Eindeutige Temp-Namen auch bei gleichzeitigen Schreibvorgängen desselben Inhalts
    next_tmp: Arc<AtomicU64>,
}

impl ImageStore {
    /// Legt Verzeichnis und Temp-Unterverzeichnis an und räumt Reste abgebrochener Schreibvorgänge.
    pub fn new(dir: impl Into<PathBuf>) -> CoreResult<Self> {
        let dir = dir.into();
        let tmp = dir.join(TMP_DIR);
        fs::create_dir_all(&tmp).map_err(io_error)?;
        for entry in fs::read_dir(&tmp).map_err(io_error)? {
            let path = entry.map_err(io_error)?.path();
            if path.is_file() {
                fs::remove_file(path).map_err(io_error)?;
            }
        }
        Ok(Self {
            dir,
            next_tmp: Arc::new(AtomicU64::new(0)),
        })
    }

    pub fn path(&self, hash: &str, ext: &str) -> PathBuf {
        self.dir.join(format!("{hash}.{ext}"))
    }

    /// Schreibt das Bild unter seinem SHA-256 und liefert den Hash. Der endgültige Name entsteht
    /// erst per Rename, damit nie eine halbe Datei unter ihm liegt; vorhandene Dateien bleiben.
    pub fn put(&self, image: &StoredImage) -> CoreResult<String> {
        let hash = hex(&Sha256::digest(&image.bytes));
        let target = self.path(&hash, image.ext);
        if target.exists() {
            return Ok(hash);
        }
        let n = self.next_tmp.fetch_add(1, Ordering::Relaxed);
        let tmp = self
            .dir
            .join(TMP_DIR)
            .join(format!("{hash}-{n}{TMP_SUFFIX}"));
        let mut file = fs::File::create(&tmp).map_err(io_error)?;
        file.write_all(&image.bytes).map_err(io_error)?;
        file.sync_all().map_err(io_error)?;
        fs::rename(&tmp, &target).map_err(io_error)?;
        Ok(hash)
    }

    /// Entfernt eine Datei; fehlt sie bereits, ist das kein Fehler.
    pub fn remove(&self, hash: &str, ext: &str) -> CoreResult<()> {
        match fs::remove_file(self.path(hash, ext)) {
            Ok(()) => Ok(()),
            Err(e) if e.kind() == std::io::ErrorKind::NotFound => Ok(()),
            Err(e) => Err(io_error(e)),
        }
    }
}

fn hex(digest: &[u8]) -> String {
    digest.iter().map(|b| format!("{b:02x}")).collect()
}

fn io_error(error: std::io::Error) -> CoreError {
    CoreError::Database {
        detail: format!("Bildspeicher: {error}"),
    }
}

#[cfg(test)]
pub(crate) mod test_dir {
    use std::path::PathBuf;
    use std::sync::atomic::{AtomicU64, Ordering};

    static COUNTER: AtomicU64 = AtomicU64::new(0);

    /// Temporäres Verzeichnis, das beim Verlassen gelöscht wird.
    pub struct TempDir(PathBuf);

    impl TempDir {
        pub fn new(name: &str) -> Self {
            let n = COUNTER.fetch_add(1, Ordering::Relaxed);
            let dir =
                std::env::temp_dir().join(format!("crunchymate-{name}-{}-{n}", std::process::id()));
            let _ = std::fs::remove_dir_all(&dir);
            Self(dir)
        }

        pub fn path(&self) -> &std::path::Path {
            &self.0
        }
    }

    impl Drop for TempDir {
        fn drop(&mut self) {
            let _ = std::fs::remove_dir_all(&self.0);
        }
    }
}

#[cfg(test)]
mod tests {
    use super::test_dir::TempDir;
    use super::*;

    fn image(bytes: &[u8]) -> StoredImage {
        StoredImage {
            bytes: bytes.to_vec(),
            ext: "webp",
        }
    }

    #[test]
    fn store_names_file_by_sha256_and_is_idempotent() {
        let dir = TempDir::new("store");
        let store = ImageStore::new(dir.path()).unwrap();
        let hash = store.put(&image(b"abc")).unwrap();
        // SHA-256 von "abc"
        assert_eq!(
            hash,
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        );
        let path = store.path(&hash, "webp");
        assert_eq!(fs::read(&path).unwrap(), b"abc");
        assert_eq!(store.put(&image(b"abc")).unwrap(), hash);
        assert_eq!(fs::read_dir(dir.path().join(TMP_DIR)).unwrap().count(), 0);

        store.remove(&hash, "webp").unwrap();
        assert!(!path.exists());
        store.remove(&hash, "webp").unwrap();
    }

    #[test]
    fn store_new_clears_stale_tmp() {
        let dir = TempDir::new("store-tmp");
        fs::create_dir_all(dir.path().join(TMP_DIR)).unwrap();
        fs::write(dir.path().join(TMP_DIR).join("x.part"), b"halb").unwrap();
        ImageStore::new(dir.path()).unwrap();
        assert_eq!(fs::read_dir(dir.path().join(TMP_DIR)).unwrap().count(), 0);
    }
}
