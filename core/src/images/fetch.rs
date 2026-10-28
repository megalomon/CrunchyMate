//! Download der Bildquellen.

use std::time::Duration;

use crate::error::{CoreError, CoreResult};
use crate::remote::http::http_client_builder;

const HTTPS_PREFIX: &str = "https://";
const MAX_REDIRECTS: usize = 10;

/// Großzügig für große Banner auf langsamen Mobilfunkverbindungen.
const REQUEST_TIMEOUT: Duration = Duration::from_secs(60);

#[derive(Debug)]
pub enum FetchError {
    /// Antwort größer als erlaubt; das Bild wird übersprungen.
    TooLarge,
    Failed(CoreError),
}

impl From<CoreError> for FetchError {
    fn from(error: CoreError) -> Self {
        FetchError::Failed(error)
    }
}

impl From<reqwest::Error> for FetchError {
    fn from(error: reqwest::Error) -> Self {
        FetchError::Failed(error.into())
    }
}

#[async_trait::async_trait]
pub trait ImageFetcher: Send + Sync {
    /// Lädt höchstens `max_bytes` Bytes; eine größere Antwort ergibt `TooLarge`.
    async fn fetch(&self, url: &str, max_bytes: u64) -> Result<Vec<u8>, FetchError>;
}

/// Lädt Bilder über einfache GET-Anfragen ohne besondere Header.
pub struct HttpImageFetcher {
    http: reqwest::Client,
    // Nur Tests dürfen gegen einen lokalen HTTP-Mockserver laden
    https_only: bool,
}

impl HttpImageFetcher {
    pub fn new() -> CoreResult<Self> {
        Ok(Self {
            http: http_client_builder(REQUEST_TIMEOUT)?
                .redirect(https_only_redirects())
                .build()?,
            https_only: true,
        })
    }

    #[cfg(test)]
    fn new_allowing_http() -> CoreResult<Self> {
        Ok(Self {
            http: http_client_builder(REQUEST_TIMEOUT)?.build()?,
            https_only: false,
        })
    }
}

/// Verhindert, dass eine Weiterleitung auf unverschlüsseltes HTTP zurückfällt.
fn https_only_redirects() -> reqwest::redirect::Policy {
    reqwest::redirect::Policy::custom(|attempt| {
        if attempt.url().scheme() != "https" {
            attempt.error("Weiterleitung auf Nicht-HTTPS")
        } else if attempt.previous().len() >= MAX_REDIRECTS {
            attempt.error("Zu viele Weiterleitungen")
        } else {
            attempt.follow()
        }
    })
}

fn is_https(url: &str) -> bool {
    url.get(..HTTPS_PREFIX.len())
        .is_some_and(|prefix| prefix.eq_ignore_ascii_case(HTTPS_PREFIX))
}

#[async_trait::async_trait]
impl ImageFetcher for HttpImageFetcher {
    async fn fetch(&self, url: &str, max_bytes: u64) -> Result<Vec<u8>, FetchError> {
        if self.https_only && !is_https(url) {
            return Err(FetchError::Failed(CoreError::Network {
                detail: "Nur https-Bildquellen erlaubt".to_string(),
            }));
        }
        let mut response = self.http.get(url).send().await?;
        let status = response.status();
        if status != reqwest::StatusCode::OK {
            return Err(FetchError::Failed(CoreError::Network {
                detail: format!("HTTP {status}"),
            }));
        }
        if response.content_length().is_some_and(|len| len > max_bytes) {
            return Err(FetchError::TooLarge);
        }
        // Die Längenangabe kann fehlen oder falsch sein; maßgeblich sind die tatsächlich gelesenen Bytes
        let mut body = Vec::new();
        while let Some(chunk) = response.chunk().await? {
            body.extend_from_slice(&chunk);
            if u64::try_from(body.len()).unwrap_or(u64::MAX) > max_bytes {
                return Err(FetchError::TooLarge);
            }
        }
        Ok(body)
    }
}

#[cfg(test)]
mod tests {
    use wiremock::matchers::method;
    use wiremock::{Mock, MockServer, ResponseTemplate};

    use super::*;

    const LIMIT: u64 = 1024;

    #[tokio::test]
    async fn http_fetcher_non_200_is_network_error() {
        let server = MockServer::start().await;
        Mock::given(method("GET"))
            .respond_with(ResponseTemplate::new(404))
            .mount(&server)
            .await;
        let fetcher = HttpImageFetcher::new_allowing_http().unwrap();
        let result = fetcher
            .fetch(&format!("{}/x.jpg", server.uri()), LIMIT)
            .await;
        match result {
            Err(FetchError::Failed(CoreError::Network { detail })) => {
                assert!(detail.contains("404"), "{detail}")
            }
            other => panic!("Netzwerkfehler erwartet: {other:?}"),
        }
    }

    #[tokio::test]
    async fn http_fetcher_returns_body_on_200() {
        let server = MockServer::start().await;
        Mock::given(method("GET"))
            .respond_with(ResponseTemplate::new(200).set_body_bytes(b"daten".to_vec()))
            .mount(&server)
            .await;
        let fetcher = HttpImageFetcher::new_allowing_http().unwrap();
        let bytes = fetcher.fetch(&server.uri(), LIMIT).await.unwrap();
        assert_eq!(bytes, b"daten");
    }

    #[tokio::test]
    async fn http_fetcher_rejects_body_over_limit() {
        let server = MockServer::start().await;
        Mock::given(method("GET"))
            .respond_with(ResponseTemplate::new(200).set_body_bytes(vec![0u8; 11]))
            .mount(&server)
            .await;
        let fetcher = HttpImageFetcher::new_allowing_http().unwrap();
        assert!(matches!(
            fetcher.fetch(&server.uri(), 10).await,
            Err(FetchError::TooLarge)
        ));
        assert_eq!(fetcher.fetch(&server.uri(), 11).await.unwrap().len(), 11);
    }

    #[tokio::test]
    async fn http_fetcher_rejects_non_https_urls() {
        let fetcher = HttpImageFetcher::new().unwrap();
        for url in [
            "http://example.invalid/x.jpg",
            "file:///etc/passwd",
            "ftp://h/x",
        ] {
            match fetcher.fetch(url, LIMIT).await {
                Err(FetchError::Failed(CoreError::Network { detail })) => {
                    assert!(detail.contains("https"), "{detail}")
                }
                other => panic!("Netzwerkfehler erwartet: {other:?}"),
            }
        }
    }
}
