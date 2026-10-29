use std::time::Duration;

use reqwest::header::{ACCEPT, RETRY_AFTER, USER_AGENT};
use reqwest::StatusCode;
use serde::Deserialize;

use crate::error::{CoreError, CoreResult};
use crate::model::{UpdateChannel, UpdateInfo};
use crate::remote::update_source::{parse_version, UpdateSource};

/// Zeitlimit pro HTTP-Anfrage.
const REQUEST_TIMEOUT: Duration = Duration::from_secs(30);
const USER_AGENT_VALUE: &str = "CrunchyMate/2.0";
const ACCEPT_VALUE: &str = "application/vnd.github+json";
/// Einziger Ort von API-Host und Repository für die Update-Prüfung.
const API_BASE_URL: &str = "https://api.github.com";
const REPO_OWNER: &str = "megalomon";
const REPO_NAME: &str = "CrunchyMate";
/// Tag des fortlaufend ersetzten Nightly-Releases (siehe nightly.yml).
const NIGHTLY_TAG: &str = "nightly";
/// Die Build-Nummer (Commit-Zähler) steckt im Dateinamen der APK: crunchymate-nightly-<n>.apk.
const NIGHTLY_ASSET_PREFIX: &str = "crunchymate-nightly-";
const NIGHTLY_ASSET_SUFFIX: &str = ".apk";
const RELEASE_TAG_PREFIX: &str = "v";
/// Länge von „JJJJ-MM-TT“ am Anfang des ISO-Zeitstempels.
const DATE_LEN: usize = 10;

#[derive(Deserialize)]
struct ReleaseDto {
    #[serde(default)]
    tag_name: String,
    #[serde(default)]
    published_at: Option<String>,
    #[serde(default)]
    assets: Vec<AssetDto>,
}

#[derive(Deserialize)]
struct AssetDto {
    name: String,
}

/// Fragt die GitHub-REST-API des Projekt-Repositorys ab. Ohne Anmeldung; GitHub sieht dabei
/// nur die IP-Adresse, gesendet werden keine Nutzerdaten.
pub struct GitHubReleases {
    http: reqwest::Client,
    base_url: String,
}

impl GitHubReleases {
    /// Quelle mit der echten API; nur HTTPS.
    pub fn new() -> CoreResult<Self> {
        let http = super::http::http_client_builder(REQUEST_TIMEOUT)?
            .https_only(true)
            .build()?;
        Ok(Self {
            http,
            base_url: API_BASE_URL.to_string(),
        })
    }

    /// Wie `new`, aber mit frei wählbarer Basis-URL (für Tests; erlaubt auch http).
    pub fn with_base_url(base_url: String) -> CoreResult<Self> {
        let http = super::http::http_client_builder(REQUEST_TIMEOUT)?.build()?;
        Ok(Self { http, base_url })
    }

    fn url(&self, route: &str) -> String {
        format!(
            "{}/repos/{REPO_OWNER}/{REPO_NAME}/releases/{route}",
            self.base_url
        )
    }

    async fn fetch(&self, route: &str) -> CoreResult<Option<ReleaseDto>> {
        let response = self
            .http
            .get(self.url(route))
            .header(ACCEPT, ACCEPT_VALUE)
            .header(USER_AGENT, USER_AGENT_VALUE)
            .send()
            .await?;
        let status = response.status();
        match status {
            StatusCode::NOT_FOUND => Ok(None),
            // GitHub meldet das Kontingent der anonymen Anfragen mit 403 oder 429
            StatusCode::FORBIDDEN | StatusCode::TOO_MANY_REQUESTS => Err(CoreError::RateLimited {
                retry_after_secs: response
                    .headers()
                    .get(RETRY_AFTER)
                    .and_then(|value| value.to_str().ok())
                    .and_then(|value| value.trim().parse().ok())
                    .unwrap_or(0),
            }),
            StatusCode::OK => {
                let bytes = response.bytes().await?;
                Ok(Some(serde_json::from_slice(&bytes)?))
            }
            other => Err(CoreError::Network {
                detail: format!("GitHub antwortete mit Status {other}"),
            }),
        }
    }
}

#[async_trait::async_trait]
impl UpdateSource for GitHubReleases {
    async fn latest(&self, channel: UpdateChannel) -> CoreResult<Option<UpdateInfo>> {
        // /releases/latest liefert nur Releases ohne Pre-Release-Markierung; das Nightly
        // (prerelease) taucht dort nie auf.
        let route = match channel {
            UpdateChannel::Nightly => format!("tags/{NIGHTLY_TAG}"),
            UpdateChannel::Release => "latest".to_string(),
        };
        let Some(release) = self.fetch(&route).await? else {
            return Ok(None);
        };
        match channel {
            UpdateChannel::Nightly => nightly_info(&release).map(Some),
            UpdateChannel::Release => release_info(&release).map(Some),
        }
    }
}

fn nightly_info(release: &ReleaseDto) -> CoreResult<UpdateInfo> {
    let build = release
        .assets
        .iter()
        .find_map(|asset| {
            asset
                .name
                .strip_prefix(NIGHTLY_ASSET_PREFIX)?
                .strip_suffix(NIGHTLY_ASSET_SUFFIX)?
                .parse::<i64>()
                .ok()
        })
        .ok_or_else(|| parse_error("Nightly ohne APK mit Build-Nummer im Dateinamen"))?;
    let date = release
        .published_at
        .as_deref()
        .and_then(|stamp| stamp.get(..DATE_LEN))
        .filter(|date| is_iso_date(date))
        .ok_or_else(|| parse_error("Nightly ohne lesbares Veröffentlichungsdatum"))?;
    Ok(UpdateInfo::Nightly {
        build,
        date: date.to_string(),
    })
}

fn release_info(release: &ReleaseDto) -> CoreResult<UpdateInfo> {
    let version = release
        .tag_name
        .strip_prefix(RELEASE_TAG_PREFIX)
        .filter(|version| parse_version(version).is_some())
        .ok_or_else(|| parse_error("Release-Tag nicht im Schema v<MAJOR>.<MINOR>.<PATCH>"))?;
    Ok(UpdateInfo::Release {
        version: version.to_string(),
    })
}

fn is_iso_date(text: &str) -> bool {
    text.bytes().enumerate().all(|(index, byte)| match index {
        4 | 7 => byte == b'-',
        _ => byte.is_ascii_digit(),
    })
}

fn parse_error(detail: &str) -> CoreError {
    CoreError::Parse {
        detail: detail.to_string(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn recognizes_iso_dates() {
        assert!(is_iso_date("2026-10-30"));
        assert!(!is_iso_date("2026/10/30"));
        assert!(!is_iso_date("20261030xx"));
    }

    #[test]
    fn https_only_client_builds_without_network() {
        assert!(GitHubReleases::new().is_ok());
    }
}
