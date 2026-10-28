//! Speicherformat der Bilder: WebP bleibt, JPEG/PNG werden zu WebP, wenn das kleiner ist.

use image::ImageFormat;

use crate::error::{CoreError, CoreResult};

/// Qualitätsstufe für verlustbehaftetes WebP; ohne sichtbaren Verlust gegenüber der Quelle.
pub const WEBP_QUALITY: f32 = 92.0;

pub const EXT_WEBP: &str = "webp";
pub const EXT_JPEG: &str = "jpg";
pub const EXT_PNG: &str = "png";

const JPEG_MAGIC: &[u8] = &[0xFF, 0xD8, 0xFF];
const PNG_MAGIC: &[u8] = &[0x89, b'P', b'N', b'G', 0x0D, 0x0A, 0x1A, 0x0A];
const RIFF_MAGIC: &[u8] = b"RIFF";
const WEBP_MAGIC: &[u8] = b"WEBP";
/// Position der Kennung „WEBP“ hinter „RIFF“ und der Chunk-Länge.
const WEBP_MAGIC_OFFSET: usize = 8;

/// Zu speichernder Bildinhalt samt Dateiendung.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct StoredImage {
    pub bytes: Vec<u8>,
    pub ext: &'static str,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum SourceFormat {
    WebP,
    Jpeg,
    Png,
}

// Erkennung über den Inhalt: URL-Endung und Content-Type der CDNs sind nicht verlässlich.
fn detect(bytes: &[u8]) -> Option<SourceFormat> {
    if bytes.starts_with(RIFF_MAGIC)
        && bytes.get(WEBP_MAGIC_OFFSET..WEBP_MAGIC_OFFSET + WEBP_MAGIC.len()) == Some(WEBP_MAGIC)
    {
        Some(SourceFormat::WebP)
    } else if bytes.starts_with(JPEG_MAGIC) {
        Some(SourceFormat::Jpeg)
    } else if bytes.starts_with(PNG_MAGIC) {
        Some(SourceFormat::Png)
    } else {
        None
    }
}

/// Bereitet einen Download für die Ablage vor. Rechenintensiv; gehört nicht auf einen Async-Thread.
pub fn encode_for_storage(bytes: &[u8]) -> CoreResult<StoredImage> {
    let (format, original_ext) = match detect(bytes) {
        Some(SourceFormat::WebP) => {
            return Ok(StoredImage {
                bytes: bytes.to_vec(),
                ext: EXT_WEBP,
            })
        }
        Some(SourceFormat::Jpeg) => (ImageFormat::Jpeg, EXT_JPEG),
        Some(SourceFormat::Png) => (ImageFormat::Png, EXT_PNG),
        None => {
            return Err(CoreError::Parse {
                detail: "Unbekanntes Bildformat".to_string(),
            })
        }
    };
    let decoded =
        image::load_from_memory_with_format(bytes, format).map_err(|e| CoreError::Parse {
            detail: format!("Bild nicht lesbar: {e}"),
        })?;
    // RGBA statt RGB, damit Transparenz aus PNGs erhalten bleibt
    let rgba = decoded.to_rgba8();
    let encoded = webp::Encoder::from_rgba(rgba.as_raw(), rgba.width(), rgba.height())
        .encode_simple(false, WEBP_QUALITY)
        .map_err(|e| CoreError::Parse {
            detail: format!("WebP-Kodierung fehlgeschlagen: {e:?}"),
        })?;
    Ok(pick_smaller(bytes, original_ext, encoded.to_vec()))
}

// Bei kleinen oder stark komprimierten Quellen kann WebP größer werden
fn pick_smaller(original: &[u8], original_ext: &'static str, webp: Vec<u8>) -> StoredImage {
    if webp.len() < original.len() {
        StoredImage {
            bytes: webp,
            ext: EXT_WEBP,
        }
    } else {
        StoredImage {
            bytes: original.to_vec(),
            ext: original_ext,
        }
    }
}

#[cfg(test)]
pub(crate) mod test_images {
    use std::io::Cursor;

    use image::{Rgb, RgbImage};

    /// Verlauf mit leichtem Rauschen in Posterhöhe, wie ein typisches Cover.
    pub fn photo_like(seed: u32) -> RgbImage {
        RgbImage::from_fn(480, 720, |x, y| {
            let noise = (x.wrapping_mul(7919) ^ y.wrapping_mul(104_729) ^ seed) % 5;
            Rgb([
                (x * 255 / 480 + noise) as u8,
                (y * 255 / 720 + noise) as u8,
                ((x + y) * 255 / 1200) as u8,
            ])
        })
    }

    pub fn jpeg(seed: u32) -> Vec<u8> {
        let mut out = Cursor::new(Vec::new());
        let encoder = image::codecs::jpeg::JpegEncoder::new_with_quality(&mut out, 90);
        photo_like(seed).write_with_encoder(encoder).unwrap();
        out.into_inner()
    }

    pub fn webp(seed: u32) -> Vec<u8> {
        let img = photo_like(seed);
        webp::Encoder::from_rgb(img.as_raw(), img.width(), img.height())
            .encode_simple(false, 80.0)
            .unwrap()
            .to_vec()
    }
}

#[cfg(test)]
mod tests {
    use super::test_images::{jpeg, webp};
    use super::*;

    #[test]
    fn encode_keeps_webp_unchanged() {
        let source = webp(1);
        let stored = encode_for_storage(&source).unwrap();
        assert_eq!(stored.ext, EXT_WEBP);
        assert_eq!(stored.bytes, source);
    }

    #[test]
    fn encode_jpeg_to_smaller_webp() {
        let source = jpeg(1);
        let stored = encode_for_storage(&source).unwrap();
        assert_eq!(stored.ext, EXT_WEBP);
        eprintln!(
            "JPEG {} Bytes -> WebP {} Bytes",
            source.len(),
            stored.bytes.len()
        );
        assert!(stored.bytes.len() < source.len());
        assert_eq!(detect(&stored.bytes), Some(SourceFormat::WebP));
    }

    #[test]
    fn encode_keeps_original_when_not_smaller() {
        let original = vec![1u8; 100];
        let larger = pick_smaller(&original, EXT_PNG, vec![2u8; 100]);
        assert_eq!((larger.ext, larger.bytes), (EXT_PNG, original.clone()));
        let smaller = pick_smaller(&original, EXT_JPEG, vec![2u8; 99]);
        assert_eq!(smaller.ext, EXT_WEBP);
    }

    #[test]
    fn encode_rejects_unknown_format() {
        let result = encode_for_storage(b"GIF89a\x01\x00\x01\x00");
        assert!(matches!(result, Err(CoreError::Parse { .. })));
        assert!(matches!(
            encode_for_storage(b""),
            Err(CoreError::Parse { .. })
        ));
    }

    #[test]
    fn encode_rejects_truncated_jpeg() {
        let result = encode_for_storage(&JPEG_MAGIC.repeat(2));
        assert!(matches!(result, Err(CoreError::Parse { .. })));
    }
}
