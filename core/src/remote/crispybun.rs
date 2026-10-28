use std::time::{Duration, Instant};

use reqwest::header::{ACCEPT_LANGUAGE, AUTHORIZATION, CONTENT_TYPE, USER_AGENT};
use reqwest::StatusCode;
use serde_json::Value;
use tokio::sync::Mutex;

use crate::error::{CoreError, CoreResult};
use crate::model::{CatalogItem, CatalogPage};
use crate::remote::catalog_mapping::{to_catalog_item, ImageCandidate, RawSeries};
use crate::remote::catalog_source::CatalogSource;

/// Zeitlimit pro HTTP-Anfrage.
const REQUEST_TIMEOUT: Duration = Duration::from_secs(30);
/// Einziger Ort des API-Hosts der Katalogquelle.
const API_BASE_URL: &str = "https://beta-api.crunchyroll.com";
const AUTH_PATH: &str = "/auth/v1/token";
const BROWSE_PATH: &str = "/content/v2/discover/browse";
/// Zugangsdaten des anonymen Web-Clients (unverändert aus dem bisherigen Client).
const BASIC_AUTH_HEADER: &str = "Basic Y3Jfd2ViOg==";
const USER_AGENT_HEADER: &str =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
const FORM_CONTENT_TYPE: &str = "application/x-www-form-urlencoded";
const TOKEN_REQUEST_BODY: &str = "grant_type=client_id";
const SORT_BY: &str = "popularity";
/// Sicherheitsabstand, um den das Token früher als angegeben als abgelaufen gilt.
const TOKEN_SAFETY_MARGIN: Duration = Duration::from_secs(60);
/// Mindestlaufzeit, die ein frisches Token im Cache behält.
const TOKEN_MIN_LIFETIME: Duration = Duration::from_secs(10);
/// Laufzeit, falls die Antwort `expires_in` nicht enthält.
const DEFAULT_EXPIRES_IN: u64 = 3600;

#[derive(Clone)]
struct Session {
    access_token: String,
    country: String,
    expires_at: Instant,
}

pub struct CrispyBunSource {
    http: reqwest::Client,
    auth_url: String,
    browse_url: String,
    session: Mutex<Option<Session>>,
}

impl CrispyBunSource {
    /// Quelle mit den Standard-Endpunkten. Es findet kein Netzwerkzugriff statt;
    /// die Anmeldung erfolgt beim ersten Abruf.
    pub fn new() -> CoreResult<Self> {
        Self::with_endpoints(
            format!("{API_BASE_URL}{AUTH_PATH}"),
            format!("{API_BASE_URL}{BROWSE_PATH}"),
        )
    }

    /// Wie `new`, aber mit frei wählbaren Endpunkten (für Tests).
    pub fn with_endpoints(auth_url: String, browse_url: String) -> CoreResult<Self> {
        let http = super::http::http_client_builder(REQUEST_TIMEOUT)?.build()?;
        Ok(Self {
            http,
            auth_url,
            browse_url,
            session: Mutex::new(None),
        })
    }

    /// Liefert die gültige Sitzung. Ist `stale` gesetzt und entspricht dem
    /// gecachten Token, wird neu angemeldet (nach einem 401).
    async fn session(&self, stale: Option<&str>) -> CoreResult<Session> {
        let mut guard = self.session.lock().await;
        if let Some(current) = guard.as_ref() {
            let rejected = stale == Some(current.access_token.as_str());
            if !rejected && Instant::now() < current.expires_at {
                return Ok(current.clone());
            }
        }
        let fresh = self.authenticate().await?;
        *guard = Some(fresh.clone());
        Ok(fresh)
    }

    async fn authenticate(&self) -> CoreResult<Session> {
        let response = self
            .http
            .post(&self.auth_url)
            .header(AUTHORIZATION, BASIC_AUTH_HEADER)
            .header(CONTENT_TYPE, FORM_CONTENT_TYPE)
            .header(USER_AGENT, USER_AGENT_HEADER)
            .body(TOKEN_REQUEST_BODY)
            .send()
            .await?;
        let status = response.status();
        if status == StatusCode::UNAUTHORIZED {
            return Err(CoreError::Auth {
                detail: "Token-Anfrage abgelehnt (HTTP 401)".to_string(),
            });
        }
        let bytes = check_status(response, "Token-Anfrage").await?;
        let json: Value = serde_json::from_slice(&bytes)?;
        let access_token = json
            .get("access_token")
            .and_then(Value::as_str)
            .filter(|t| !t.is_empty())
            .ok_or_else(|| parse_error("Token-Antwort ohne access_token"))?
            .to_string();
        let country = json
            .get("country")
            .and_then(Value::as_str)
            .map(|c| c.trim().to_uppercase())
            .filter(|c| !c.is_empty())
            .ok_or_else(|| parse_error("Token-Antwort ohne country"))?;
        let expires_in = json
            .get("expires_in")
            .and_then(Value::as_u64)
            .unwrap_or(DEFAULT_EXPIRES_IN);
        let lifetime = Duration::from_secs(expires_in)
            .saturating_sub(TOKEN_SAFETY_MARGIN)
            .max(TOKEN_MIN_LIFETIME);
        Ok(Session {
            access_token,
            country,
            expires_at: Instant::now() + lifetime,
        })
    }

    async fn get_browse(
        &self,
        token: &str,
        start: u32,
        count: u32,
        locale: &str,
    ) -> CoreResult<reqwest::Response> {
        Ok(self
            .http
            .get(&self.browse_url)
            .query(&[
                ("locale", locale),
                ("start", &start.to_string()),
                ("n", &count.to_string()),
                ("sort_by", SORT_BY),
            ])
            .header(AUTHORIZATION, format!("Bearer {token}"))
            .header(USER_AGENT, USER_AGENT_HEADER)
            .header(ACCEPT_LANGUAGE, locale)
            .send()
            .await?)
    }
}

#[async_trait::async_trait]
impl CatalogSource for CrispyBunSource {
    async fn session_country(&self) -> CoreResult<String> {
        Ok(self.session(None).await?.country)
    }

    async fn fetch_page(&self, start: u32, count: u32, locale: &str) -> CoreResult<CatalogPage> {
        let session = self.session(None).await?;
        let mut response = self
            .get_browse(&session.access_token, start, count, locale)
            .await?;
        if response.status() == StatusCode::UNAUTHORIZED {
            // Einmal neu anmelden und genau einmal wiederholen.
            let fresh = self.session(Some(&session.access_token)).await?;
            response = self
                .get_browse(&fresh.access_token, start, count, locale)
                .await?;
            if response.status() == StatusCode::UNAUTHORIZED {
                return Err(CoreError::Auth {
                    detail: "Katalogabruf nach erneuter Anmeldung abgelehnt (HTTP 401)".to_string(),
                });
            }
        }
        let bytes = check_status(response, "Katalogabruf").await?;
        let json: Value = serde_json::from_slice(&bytes)?;
        parse_page(&json, locale)
    }
}

/// Prüft den Status und liefert den Body; 403 und andere Fehler werden zu `Network`.
async fn check_status(response: reqwest::Response, what: &str) -> CoreResult<Vec<u8>> {
    let status = response.status();
    if status == StatusCode::FORBIDDEN {
        // Kein Umgehungsversuch, z. B. bei einer Cloudflare-Sperrseite.
        return Err(CoreError::Network {
            detail: format!("{what} abgelehnt (HTTP 403)"),
        });
    }
    if !status.is_success() {
        return Err(CoreError::Network {
            detail: format!("{what} fehlgeschlagen (HTTP {})", status.as_u16()),
        });
    }
    Ok(response.bytes().await?.to_vec())
}

fn saturating_u32(value: u64) -> u32 {
    u32::try_from(value).unwrap_or(u32::MAX)
}

fn parse_error(message: &str) -> CoreError {
    CoreError::Parse {
        detail: message.to_string(),
    }
}

fn parse_page(json: &Value, locale: &str) -> CoreResult<CatalogPage> {
    let data = json
        .get("data")
        .and_then(Value::as_array)
        .ok_or_else(|| parse_error("Katalogantwort ohne data"))?;
    let total = json
        .get("total")
        .and_then(Value::as_u64)
        .map(saturating_u32)
        .ok_or_else(|| parse_error("Katalogantwort ohne total"))?;
    let items: Vec<CatalogItem> = data
        .iter()
        .filter_map(parse_series)
        .map(|raw| to_catalog_item(raw, locale))
        .collect();
    Ok(CatalogPage { total, items })
}

fn str_field(value: &Value, key: &str) -> String {
    value
        .get(key)
        .and_then(Value::as_str)
        .unwrap_or_default()
        .to_string()
}

fn u32_field(value: &Value, key: &str) -> u32 {
    value
        .get(key)
        .and_then(Value::as_u64)
        .map(saturating_u32)
        .unwrap_or(0)
}

fn string_list(value: &Value, key: &str) -> Vec<String> {
    value
        .get(key)
        .and_then(Value::as_array)
        .map(|list| {
            list.iter()
                .filter_map(Value::as_str)
                .map(str::to_string)
                .collect()
        })
        .unwrap_or_default()
}

/// Bilder liegen als Array von Arrays vor; verwendet wird die erste Liste.
fn image_list(images: Option<&Value>, key: &str) -> Vec<ImageCandidate> {
    images
        .and_then(|i| i.get(key))
        .and_then(Value::as_array)
        .and_then(|outer| outer.first())
        .and_then(Value::as_array)
        .map(|list| {
            list.iter()
                .map(|img| ImageCandidate {
                    source: str_field(img, "source"),
                    width: u32_field(img, "width"),
                })
                .collect()
        })
        .unwrap_or_default()
}

/// Einträge ohne ID werden verworfen, da sie nicht gespeichert werden können.
fn parse_series(item: &Value) -> Option<RawSeries> {
    let id = str_field(item, "id");
    if id.is_empty() {
        return None;
    }
    let meta = item.get("series_metadata").unwrap_or(&Value::Null);
    let images = item.get("images");
    Some(RawSeries {
        id,
        title: str_field(item, "title"),
        description: str_field(item, "description"),
        poster_tall: image_list(images, "poster_tall"),
        poster_wide: image_list(images, "poster_wide"),
        episode_count: u32_field(meta, "episode_count"),
        launch_year: u32_field(meta, "series_launch_year"),
        is_subbed: meta
            .get("is_subbed")
            .and_then(Value::as_bool)
            .unwrap_or(false),
        is_dubbed: meta
            .get("is_dubbed")
            .and_then(Value::as_bool)
            .unwrap_or(false),
        audio_locales: string_list(meta, "audio_locales"),
        subtitle_locales: string_list(meta, "subtitle_locales"),
        categories: string_list(meta, "tenant_categories"),
    })
}

#[cfg(test)]
mod tests {
    use serde_json::json;

    use super::*;

    #[test]
    fn parse_page_without_total_is_parse_error() {
        let json = json!({ "data": [] });
        assert!(matches!(
            parse_page(&json, "de-DE"),
            Err(CoreError::Parse { .. })
        ));
    }

    #[test]
    fn parse_page_with_total_keeps_it() {
        let json = json!({ "data": [], "total": 7 });
        assert_eq!(parse_page(&json, "de-DE").unwrap().total, 7);
    }
}
