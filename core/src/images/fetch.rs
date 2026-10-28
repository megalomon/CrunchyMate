//! Download der Bildquellen.

use std::time::Duration;

use crate::error::{CoreError, CoreResult};
use crate::remote::http::http_client_builder;

const HTTPS_PREFIX: &str = "https://";
const MAX_REDIRECTS: usize = 10;

/// Großzügig für große Banner auf langsamen Mobilfunkverbindungen.
const REQUEST_TIMEOUT: Duration = Duration::from_secs(60);

#[async_trait::async_trait]
pub trait ImageFetcher: Send + Sync {
    async fn fetch(&self, url: &str) -> CoreResult<Vec<u8>>;
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
    async fn fetch(&self, url: &str) -> CoreResult<Vec<u8>> {
        if self.https_only && !is_https(url) {
            return Err(CoreError::Network {
                detail: "Nur https-Bildquellen erlaubt".to_string(),
            });
        }
        let response = self.http.get(url).send().await?;
        let status = response.status();
        if status != reqwest::StatusCode::OK {
            return Err(CoreError::Network {
                detail: format!("HTTP {status}"),
            });
        }
        Ok(response.bytes().await?.to_vec())
    }
}

#[cfg(test)]
mod tests {
    use wiremock::matchers::method;
    use wiremock::{Mock, MockServer, ResponseTemplate};

    use super::*;

    #[tokio::test]
    async fn http_fetcher_non_200_is_network_error() {
        let server = MockServer::start().await;
        Mock::given(method("GET"))
            .respond_with(ResponseTemplate::new(404))
            .mount(&server)
            .await;
        let fetcher = HttpImageFetcher::new_allowing_http().unwrap();
        let result = fetcher.fetch(&format!("{}/x.jpg", server.uri())).await;
        match result {
            Err(CoreError::Network { detail }) => assert!(detail.contains("404"), "{detail}"),
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
        let bytes = fetcher.fetch(&server.uri()).await.unwrap();
        assert_eq!(bytes, b"daten");
    }

    #[tokio::test]
    async fn http_fetcher_rejects_non_https_urls() {
        let fetcher = HttpImageFetcher::new().unwrap();
        for url in [
            "http://example.invalid/x.jpg",
            "file:///etc/passwd",
            "ftp://h/x",
        ] {
            match fetcher.fetch(url).await {
                Err(CoreError::Network { detail }) => assert!(detail.contains("https"), "{detail}"),
                other => panic!("Netzwerkfehler erwartet: {other:?}"),
            }
        }
    }
}
