use std::sync::{LazyLock, Mutex};
use std::time::{Duration, Instant, SystemTime, UNIX_EPOCH};

use regex::Regex;
use reqwest::header::{HeaderMap, ACCEPT, CONTENT_TYPE, USER_AGENT};
use reqwest::StatusCode;
use serde_json::{json, Map, Value};

use crate::error::{CoreError, CoreResult};
use crate::model::{MetadataItem, TagRank};
use crate::remote::metadata_source::MetadataSource;

/// Zeitlimit pro HTTP-Anfrage.
const REQUEST_TIMEOUT: Duration = Duration::from_secs(30);
/// Tags mit geringerem Rang werden verworfen.
const MIN_TAG_RANK: i32 = 40;
/// Obergrenze für jede berechnete Wartezeit.
const DEFAULT_MAX_WAIT: Duration = Duration::from_secs(300);
const USER_AGENT_VALUE: &str = "CrunchyMate/2.0";

static CLEAN_TITLE_REGEX: LazyLock<Regex> = LazyLock::new(|| {
    Regex::new(
        r"(?i)\s*(\(German\s*Dub\)|\[German\s*Dub\]|\(OmU\)|\(Dub\)|\(OV\)|\(Synchro\)|\(Staffel\s*\d+\))\s*",
    )
    .expect("gültiger regulärer Ausdruck")
});

const MEDIA_FRAGMENT: &str = "fragment MediaFields on Media {\n  id\n  title { romaji english native }\n  description\n  genres\n  tags { name rank isMediaSpoiler }\n  averageScore\n  status\n  bannerImage\n  siteUrl\n}\n";

#[derive(Debug, Clone)]
pub struct RetryConfig {
    pub max_retries: u32,
    pub default_wait: Duration,
    pub min_wait: Duration,
    pub max_wait: Duration,
}

impl Default for RetryConfig {
    fn default() -> Self {
        Self {
            max_retries: 3,
            default_wait: Duration::from_secs(60),
            min_wait: Duration::from_secs(1),
            max_wait: DEFAULT_MAX_WAIT,
        }
    }
}

pub struct AniListClient {
    endpoint: String,
    http: reqwest::Client,
    retry: RetryConfig,
    blocked_until: Mutex<Option<Instant>>,
}

impl AniListClient {
    pub const DEFAULT_ENDPOINT: &'static str = "https://graphql.anilist.co";

    pub fn new(endpoint: String, retry: RetryConfig) -> CoreResult<Self> {
        let http = reqwest::Client::builder()
            .timeout(REQUEST_TIMEOUT)
            .build()?;
        Ok(Self {
            endpoint,
            http,
            retry,
            blocked_until: Mutex::new(None),
        })
    }

    fn blocked_until(&self) -> Option<Instant> {
        *self.blocked_until.lock().unwrap_or_else(|e| e.into_inner())
    }

    fn set_blocked_until(&self, value: Option<Instant>) {
        *self.blocked_until.lock().unwrap_or_else(|e| e.into_inner()) = value;
    }

    /// Wartet ggf. bis zum gemerkten Ende der Sperre (kein Mutex über dem await).
    async fn wait_if_blocked(&self) {
        if let Some(until) = self.blocked_until() {
            let now = Instant::now();
            if until > now {
                tokio::time::sleep(until - now).await;
            }
            // Abgelaufene Sperren sind harmlos und werden bewusst nicht gelöscht.
        }
    }

    /// Merkt sich das Reset-Ende, wenn das Kontingent aufgebraucht ist.
    fn note_rate_limit(&self, headers: &HeaderMap) {
        if header_str(headers, "x-ratelimit-remaining") != Some("0") {
            return;
        }
        let Some(reset) =
            header_str(headers, "x-ratelimit-reset").and_then(|v| v.parse::<u64>().ok())
        else {
            return;
        };
        if reset == 0 {
            return;
        }
        let target = UNIX_EPOCH + Duration::from_secs(reset) + Duration::from_secs(1);
        if let Ok(delta) = target.duration_since(SystemTime::now()) {
            self.set_blocked_until(Some(Instant::now() + delta.min(self.retry.max_wait)));
        }
    }

    /// Führt die GraphQL-Anfrage mit Wiederholung bei 429 aus.
    /// `None` bedeutet 404 oder leere Antwort (alle Ergebnisse ohne Treffer).
    async fn execute(&self, payload: &Value) -> CoreResult<Option<Map<String, Value>>> {
        let mut last_wait = Duration::ZERO;
        for attempt in 0..=self.retry.max_retries {
            self.wait_if_blocked().await;

            let resp = self
                .http
                .post(&self.endpoint)
                .header(CONTENT_TYPE, "application/json")
                .header(ACCEPT, "application/json")
                .header(USER_AGENT, USER_AGENT_VALUE)
                .body(payload.to_string())
                .send()
                .await?;
            let status = resp.status();
            let headers = resp.headers().clone();
            self.note_rate_limit(&headers);

            match status {
                StatusCode::TOO_MANY_REQUESTS => {
                    last_wait = parse_wait(&headers, SystemTime::now(), &self.retry);
                    if attempt < self.retry.max_retries {
                        tokio::time::sleep(last_wait).await;
                    }
                }
                StatusCode::NOT_FOUND => return Ok(None),
                StatusCode::OK => {
                    let bytes = resp.bytes().await?;
                    let root: Value = serde_json::from_slice(&bytes)?;
                    return parse_root(root);
                }
                other => {
                    return Err(CoreError::Network {
                        message: format!("AniList antwortete mit Status {other}"),
                    })
                }
            }
        }
        Err(CoreError::RateLimited {
            retry_after_secs: last_wait.as_secs() as i64 + i64::from(last_wait.subsec_nanos() > 0),
        })
    }
}

#[async_trait::async_trait]
impl MetadataSource for AniListClient {
    async fn search_batch(&self, titles: &[String]) -> CoreResult<Vec<Option<MetadataItem>>> {
        if titles.is_empty() {
            return Ok(Vec::new());
        }
        let payload = build_payload(titles);
        let Some(data) = self.execute(&payload).await? else {
            return Ok(vec![None; titles.len()]);
        };
        Ok((0..titles.len())
            .map(|i| {
                data.get(&format!("a{i}"))
                    .and_then(|page| page.get("media"))
                    .and_then(|media| media.get(0))
                    .and_then(parse_media)
            })
            .collect())
    }
}

fn header_str<'a>(headers: &'a HeaderMap, name: &str) -> Option<&'a str> {
    headers
        .get(name)
        .and_then(|v| v.to_str().ok())
        .map(str::trim)
        .filter(|v| !v.is_empty())
}

/// Entfernt Sprach- und Staffelmarker aus einem Titel; bei leerem Ergebnis gilt der Originaltitel.
pub fn clean_title(title: &str) -> String {
    let cleaned = CLEAN_TITLE_REGEX.replace_all(title, " ");
    let cleaned = cleaned.trim();
    if cleaned.is_empty() {
        title.to_string()
    } else {
        cleaned.to_string()
    }
}

/// Ermittelt die Wartezeit nach einem 429 aus den Antwort-Headern.
pub fn parse_wait(headers: &HeaderMap, now: SystemTime, retry: &RetryConfig) -> Duration {
    raw_wait(headers, now, retry).min(retry.max_wait)
}

fn raw_wait(headers: &HeaderMap, now: SystemTime, retry: &RetryConfig) -> Duration {
    let one = Duration::from_secs(1);
    if let Some(value) = header_str(headers, "retry-after") {
        if let Ok(secs) = value.parse::<i64>() {
            return if secs <= 0 {
                retry.min_wait
            } else {
                (Duration::from_secs(secs as u64) + one).max(retry.min_wait)
            };
        }
        if let Some(target) = parse_http_date(value) {
            if let Ok(diff) = target.duration_since(now) {
                return diff + one;
            }
        }
    }
    if let Some(reset) =
        header_str(headers, "x-ratelimit-reset").and_then(|v| v.parse::<u64>().ok())
    {
        if reset > 0 {
            if let Ok(diff) = (UNIX_EPOCH + Duration::from_secs(reset)).duration_since(now) {
                return diff + one;
            }
        }
    }
    retry.default_wait
}

/// Parst eine Zahl aus genau `digits` ASCII-Ziffern (kein Vorzeichen).
fn fixed_digits(text: &str, digits: usize) -> Option<i64> {
    if text.len() != digits || !text.bytes().all(|b| b.is_ascii_digit()) {
        return None;
    }
    text.parse().ok()
}

fn is_leap(year: i64) -> bool {
    (year % 4 == 0 && year % 100 != 0) || year % 400 == 0
}

/// Parst das HTTP-Datum im Format IMF-fixdate („Sun, 06 Nov 1994 08:49:37 GMT“).
/// Ungültige Eingaben ergeben `None`; der Wochentag wird nicht geprüft.
fn parse_http_date(value: &str) -> Option<SystemTime> {
    let (_, rest) = value.split_once(", ")?;
    let mut parts = rest.split_whitespace();
    let day = fixed_digits(parts.next()?, 2)?;
    let month_name = parts.next()?;
    let month = [
        "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
    ]
    .iter()
    .position(|m| m.eq_ignore_ascii_case(month_name))? as i64
        + 1;
    let year = fixed_digits(parts.next()?, 4)?;
    let mut time = parts.next()?.split(':');
    let hour = fixed_digits(time.next()?, 2)?;
    let minute = fixed_digits(time.next()?, 2)?;
    let second = fixed_digits(time.next()?, 2)?;
    if time.next().is_some() || parts.next()? != "GMT" || parts.next().is_some() {
        return None;
    }
    let days_in_month = match month {
        2 if is_leap(year) => 29,
        2 => 28,
        4 | 6 | 9 | 11 => 30,
        _ => 31,
    };
    if !(1..=days_in_month).contains(&day) || hour > 23 || minute > 59 || second > 60 {
        return None;
    }
    // Tage seit 1970-01-01 (Algorithmus von Howard Hinnant)
    let y = if month <= 2 { year - 1 } else { year };
    let era = y.div_euclid(400);
    let yoe = y - era * 400;
    let mp = (month + 9) % 12;
    let doy = (153 * mp + 2) / 5 + day - 1;
    let doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
    let days = era
        .checked_mul(146_097)?
        .checked_add(doe)?
        .checked_sub(719_468)?;
    let secs = days
        .checked_mul(86_400)?
        .checked_add(hour * 3600 + minute * 60 + second)?;
    u64::try_from(secs)
        .ok()
        .map(|s| UNIX_EPOCH + Duration::from_secs(s))
}

fn build_payload(titles: &[String]) -> Value {
    let mut query = String::from(MEDIA_FRAGMENT);
    query.push_str("query(");
    let mut variables = Map::new();
    for (i, raw) in titles.iter().enumerate() {
        if i > 0 {
            query.push_str(", ");
        }
        query.push_str(&format!("$s{i}: String"));
        variables.insert(format!("s{i}"), Value::String(clean_title(raw)));
    }
    query.push_str(") {\n");
    for i in 0..titles.len() {
        query.push_str(&format!(
            "  a{i}: Page(page: 1, perPage: 1) {{ media(search: $s{i}, type: ANIME) {{ ...MediaFields }} }}\n"
        ));
    }
    query.push('}');
    json!({ "query": query, "variables": variables })
}

fn parse_root(root: Value) -> CoreResult<Option<Map<String, Value>>> {
    match root.get("data") {
        Some(Value::Object(data)) => Ok(Some(data.clone())),
        _ => {
            let message = root
                .get("errors")
                .and_then(|e| e.get(0))
                .and_then(|e| e.get("message"))
                .and_then(Value::as_str)
                .filter(|m| !m.is_empty());
            match (message, root.get("errors").and_then(Value::as_array)) {
                (Some(m), _) => Err(CoreError::Parse {
                    message: format!("AniList-GraphQL-Fehler: {m}"),
                }),
                (None, Some(list)) if !list.is_empty() => Err(CoreError::Parse {
                    message: "AniList-GraphQL-Fehler: Unbekannter Fehler".to_string(),
                }),
                _ => Ok(None),
            }
        }
    }
}

fn str_field(obj: &Value, key: &str) -> String {
    obj.get(key)
        .and_then(Value::as_str)
        .unwrap_or_default()
        .to_string()
}

/// Wandelt ein Media-Objekt um; ohne gültige ID gilt es als kein Treffer.
fn parse_media(media: &Value) -> Option<MetadataItem> {
    let anilist_id = media.get("id")?.as_i64()?;
    let title = media.get("title").unwrap_or(&Value::Null);
    let genres = media
        .get("genres")
        .and_then(Value::as_array)
        .map(|list| {
            list.iter()
                .filter_map(Value::as_str)
                .map(str::trim)
                .filter(|g| !g.is_empty())
                .map(str::to_string)
                .collect()
        })
        .unwrap_or_default();
    let tags = media
        .get("tags")
        .and_then(Value::as_array)
        .map(|list| {
            list.iter()
                .filter(|t| t.get("isMediaSpoiler").and_then(Value::as_bool) != Some(true))
                .filter_map(|t| {
                    let rank = t.get("rank").and_then(Value::as_i64).unwrap_or(0);
                    let rank = i32::try_from(rank).ok().filter(|r| *r >= MIN_TAG_RANK)?;
                    let name = str_field(t, "name").trim().to_string();
                    (!name.is_empty()).then_some(TagRank { name, rank })
                })
                .collect()
        })
        .unwrap_or_default();
    let score = media
        .get("averageScore")
        .and_then(Value::as_i64)
        .map(|s| s.clamp(0, i64::from(u32::MAX)) as u32)
        .unwrap_or(0);
    Some(MetadataItem {
        anilist_id,
        title_romaji: str_field(title, "romaji"),
        title_english: str_field(title, "english"),
        description: str_field(media, "description"),
        score,
        status: str_field(media, "status"),
        banner_image: str_field(media, "bannerImage"),
        site_url: str_field(media, "siteUrl"),
        genres,
        tags,
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use reqwest::header::{HeaderMap, HeaderValue};
    use std::time::{Duration, SystemTime, UNIX_EPOCH};

    fn headers(pairs: &[(&'static str, &str)]) -> HeaderMap {
        let mut map = HeaderMap::new();
        for (k, v) in pairs {
            map.insert(*k, HeaderValue::from_str(v).unwrap());
        }
        map
    }

    #[test]
    fn clean_title_removes_markers() {
        assert_eq!(clean_title("Frieren (German Dub)"), "Frieren");
        assert_eq!(clean_title("X (Staffel 2)"), "X");
        assert_eq!(clean_title("A [German Dub] B"), "A B");
        assert_eq!(clean_title("(OmU)"), "(OmU)");
    }

    #[test]
    fn parse_wait_seconds_plus_one() {
        let h = headers(&[("retry-after", "5")]);
        assert_eq!(
            parse_wait(&h, SystemTime::now(), &RetryConfig::default()),
            Duration::from_secs(6)
        );
    }

    #[test]
    fn parse_wait_zero_uses_min_wait() {
        let h = headers(&[("retry-after", "0")]);
        assert_eq!(
            parse_wait(&h, SystemTime::now(), &RetryConfig::default()),
            Duration::from_secs(1)
        );
    }

    #[test]
    fn parse_wait_http_date() {
        // 06.11.1994 08:49:37 GMT = 784111777
        let now = UNIX_EPOCH + Duration::from_secs(784111777 - 10);
        let h = headers(&[("retry-after", "Sun, 06 Nov 1994 08:49:37 GMT")]);
        assert_eq!(
            parse_wait(&h, now, &RetryConfig::default()),
            Duration::from_secs(11)
        );
    }

    #[test]
    fn parse_wait_ratelimit_reset() {
        let now = UNIX_EPOCH + Duration::from_secs(1000);
        let h = headers(&[("x-ratelimit-reset", "1030")]);
        assert_eq!(
            parse_wait(&h, now, &RetryConfig::default()),
            Duration::from_secs(31)
        );
    }

    #[test]
    fn parse_wait_default() {
        assert_eq!(
            parse_wait(
                &HeaderMap::new(),
                SystemTime::now(),
                &RetryConfig::default()
            ),
            Duration::from_secs(60)
        );
    }

    #[test]
    fn parse_wait_invalid_dates_fall_back_to_default() {
        for bad in [
            "Sun, 06 Nov 99999999999999 08:49:37 GMT",
            "Sun, 31 Feb 1994 08:49:37 GMT",
            "Sun, 06 Nov 1994 -1:49:37 GMT",
            "Sun, 06 Nov 1994 +5:49:37 GMT",
            "Sun, 06 Nov 1994 24:00:00 GMT",
        ] {
            let h = headers(&[("retry-after", bad)]);
            assert_eq!(
                parse_wait(&h, SystemTime::now(), &RetryConfig::default()),
                Duration::from_secs(60),
                "{bad}"
            );
        }
    }

    #[test]
    fn parse_wait_is_clamped_to_max_wait() {
        let h = headers(&[("retry-after", "9223372036854775807")]);
        let cfg = RetryConfig::default();
        assert_eq!(parse_wait(&h, SystemTime::now(), &cfg), cfg.max_wait);
    }

    #[test]
    fn leap_day_is_accepted() {
        assert!(parse_http_date("Thu, 29 Feb 2024 00:00:00 GMT").is_some());
        assert!(parse_http_date("Fri, 29 Feb 2023 00:00:00 GMT").is_none());
    }

    #[test]
    fn remaining_zero_blocks_until_reset_plus_one_second() {
        let client = AniListClient::new("http://localhost".into(), RetryConfig::default()).unwrap();
        let reset = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap()
            .as_secs()
            + 30;
        let h = headers(&[
            ("x-ratelimit-remaining", "0"),
            ("x-ratelimit-reset", &reset.to_string()),
        ]);
        client.note_rate_limit(&h);
        let left = client.blocked_until().unwrap() - Instant::now();
        assert!(left > Duration::from_secs(29) && left <= Duration::from_secs(31));

        // Ohne Remaining = 0 bleibt eine vorhandene Sperre unverändert
        let other = AniListClient::new("http://localhost".into(), RetryConfig::default()).unwrap();
        other.note_rate_limit(&headers(&[("x-ratelimit-reset", &reset.to_string())]));
        assert!(other.blocked_until().is_none());
    }
}
