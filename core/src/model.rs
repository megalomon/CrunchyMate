use serde::de::DeserializeOwned;
use serde::{Deserialize, Deserializer};

// Die Variantennamen sind zugleich das gespeicherte JSON der Einstellungen (siehe Golden-Test in
// db::settings); eine Umbenennung setzt sonst gespeicherte Werte still auf den Standard zurück.
#[derive(
    Debug, Clone, Copy, Default, PartialEq, Eq, serde::Serialize, serde::Deserialize, uniffi::Enum,
)]
pub enum WatchedFilter {
    #[default]
    All,
    Unwatched,
    Watched,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum MehFilter {
    Hide,
    Only,
    Include,
}

#[derive(
    Debug, Clone, Copy, Default, PartialEq, Eq, serde::Serialize, serde::Deserialize, uniffi::Enum,
)]
pub enum LanguageFilter {
    #[default]
    All,
    Dub,
    Sub,
    Both,
}

#[derive(
    Debug, Clone, Copy, Default, PartialEq, Eq, serde::Serialize, serde::Deserialize, uniffi::Enum,
)]
pub enum SortOrder {
    #[default]
    Title,
    Score,
    Episodes,
    Year,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, serde::Serialize, serde::Deserialize, uniffi::Enum)]
pub enum TagState {
    Prefer,
    Must,
    Exclude,
}

#[derive(Debug, Clone, PartialEq, Eq, serde::Serialize, serde::Deserialize, uniffi::Record)]
pub struct TagSelection {
    pub name: String,
    pub state: TagState,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct AnimeFilter {
    pub query: String,
    pub genre: Option<String>,
    pub language: LanguageFilter,
    pub watched: WatchedFilter,
    pub meh: MehFilter,
    pub sort: SortOrder,
    pub prefer_tags: Vec<String>,
    pub must_tags: Vec<String>,
    pub exclude_tags: Vec<String>,
    /// Blendet als 18+ gekennzeichnete Einträge aus (Liste und Trefferzahl).
    pub hide_adult: bool,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct AnimeSummary {
    pub id: String,
    pub title: String,
    /// Absoluter Pfad der lokalen Bilddatei; leer, solange sie nicht geladen ist.
    pub poster_path: String,
    pub anilist_score: i32,
    pub has_target_dub: bool,
    pub has_target_sub: bool,
    pub watched: bool,
    pub is_meh: bool,
    pub episode_count: i32,
    /// Altersstufe als Zahl; leer, solange der Katalog sie nicht geliefert hat.
    pub age_rating: Option<i32>,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct AnimePage {
    pub items: Vec<AnimeSummary>,
    pub total: i64,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct TagRank {
    pub name: String,
    pub rank: i32,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct AniListInfo {
    pub score: i32,
    pub status: String,
    pub site_url: String,
    pub description: String,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct AnimeDetail {
    pub id: String,
    pub title: String,
    pub title_romaji: String,
    pub title_english: String,
    pub description: String,
    pub poster_path: String,
    pub banner_path: String,
    pub episode_count: i32,
    pub launch_year: i32,
    pub has_target_dub: bool,
    pub has_target_sub: bool,
    pub watched: bool,
    pub is_meh: bool,
    pub genres: Vec<String>,
    pub tags: Vec<TagRank>,
    pub anilist: Option<AniListInfo>,
    pub age_rating: Option<i32>,
    /// Inhaltshinweise in der Sprache des letzten Katalog-Syncs.
    pub content_descriptors: Vec<String>,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct NameCount {
    pub name: String,
    pub count: i64,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct Stats {
    pub total: i64,
    pub watched: i64,
    pub meh: i64,
    pub enriched: i64,
    pub tags: i64,
    pub image_count: i64,
    pub image_bytes: i64,
}

/// Jedes Feld wird einzeln gelesen: ist ein Wert unlesbar (etwa eine entfallene Variante wie das
/// frühere `LanguageFilter::Any`), fällt nur dieses Feld auf den Standard zurück, nicht alles.
#[derive(
    Debug, Clone, Default, PartialEq, Eq, serde::Serialize, serde::Deserialize, uniffi::Record,
)]
#[serde(default)]
pub struct SearchState {
    #[serde(deserialize_with = "lenient")]
    pub query: String,
    #[serde(deserialize_with = "lenient")]
    pub genre: Option<String>,
    #[serde(deserialize_with = "lenient")]
    pub language: LanguageFilter,
    #[serde(deserialize_with = "lenient")]
    pub watched: WatchedFilter,
    #[serde(deserialize_with = "lenient")]
    pub sort: SortOrder,
    #[serde(deserialize_with = "lenient_items")]
    pub tags: Vec<TagSelection>,
}

pub const DEFAULT_CONTENT_LOCALE: &str = "de-DE";

#[derive(Debug, Clone, PartialEq, Eq, serde::Serialize, serde::Deserialize, uniffi::Record)]
#[serde(default)]
pub struct Settings {
    #[serde(deserialize_with = "lenient_locale")]
    pub content_locale: String,
    #[serde(deserialize_with = "lenient")]
    pub search: SearchState,
    /// Erststart-Info bestätigt; fehlt in älteren Ständen und gilt dann als nicht gesehen.
    #[serde(deserialize_with = "lenient")]
    pub intro_seen: bool,
    /// Dunkles Theme mit reinem Schwarz unabhängig von der Systemeinstellung; fehlt in älteren Ständen und gilt dann als aus.
    #[serde(deserialize_with = "lenient")]
    pub oled_mode: bool,
    /// Als 18+ gekennzeichnete Einträge anzeigen; fehlt in älteren Ständen und gilt dann als an.
    #[serde(deserialize_with = "lenient_true")]
    pub show_adult: bool,
    /// Altersstufe auf den Kacheln anzeigen; fehlt in älteren Ständen und gilt dann als an.
    #[serde(deserialize_with = "lenient_true")]
    pub show_age_rating: bool,
    /// Einmal täglich nach einem neueren Build des eigenen Kanals suchen; fehlt in älteren Ständen und gilt dann als aus.
    #[serde(deserialize_with = "lenient")]
    pub check_updates: bool,
    /// Unix-Sekunden der letzten erfolgreichen Update-Prüfung; 0 heißt „noch nie“.
    #[serde(deserialize_with = "lenient")]
    pub last_update_check: i64,
}

/// Unlesbarer Wert ergibt den Standard des Typs.
fn lenient<'de, D, T>(deserializer: D) -> Result<T, D::Error>
where
    D: Deserializer<'de>,
    T: DeserializeOwned + Default,
{
    let value = serde_json::Value::deserialize(deserializer)?;
    Ok(T::deserialize(value).unwrap_or_default())
}

/// Wie `lenient`, aber mit „an“ als Standard.
fn lenient_true<'de, D>(deserializer: D) -> Result<bool, D::Error>
where
    D: Deserializer<'de>,
{
    let value = serde_json::Value::deserialize(deserializer)?;
    Ok(bool::deserialize(value).unwrap_or(true))
}

/// Unlesbare Einträge entfallen einzeln; die übrigen bleiben erhalten.
fn lenient_items<'de, D, T>(deserializer: D) -> Result<Vec<T>, D::Error>
where
    D: Deserializer<'de>,
    T: DeserializeOwned,
{
    let value = serde_json::Value::deserialize(deserializer)?;
    let serde_json::Value::Array(items) = value else {
        return Ok(Vec::new());
    };
    Ok(items
        .into_iter()
        .filter_map(|item| T::deserialize(item).ok())
        .collect())
}

fn lenient_locale<'de, D>(deserializer: D) -> Result<String, D::Error>
where
    D: Deserializer<'de>,
{
    let value = serde_json::Value::deserialize(deserializer)?;
    Ok(match value {
        serde_json::Value::String(locale) => locale,
        _ => DEFAULT_CONTENT_LOCALE.to_string(),
    })
}

impl Default for Settings {
    fn default() -> Self {
        Self {
            content_locale: DEFAULT_CONTENT_LOCALE.to_string(),
            search: SearchState::default(),
            intro_seen: false,
            oled_mode: false,
            show_adult: true,
            show_age_rating: true,
            check_updates: false,
            last_update_check: 0,
        }
    }
}

/// Veröffentlichungskanal der installierten App.
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum UpdateChannel {
    Nightly,
    Release,
}

/// Neuester veröffentlichter Stand eines Kanals.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Enum)]
pub enum UpdateInfo {
    /// `build` ist der Commit-Zähler des Nightly-Builds, `date` das Veröffentlichungsdatum (JJJJ-MM-TT).
    Nightly { build: i64, date: String },
    /// `version` ohne führendes „v“, etwa „1.2.0“.
    Release { version: String },
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum SyncKind {
    Catalog,
    Metadata,
}

/// Abschnitt eines Laufs: erst die Daten, danach die Bilder.
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum SyncPhase {
    Items,
    Images,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum SyncRunState {
    Idle,
    Running,
    Completed,
    Failed,
    Cancelled,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct SyncStatus {
    pub kind: SyncKind,
    pub state: SyncRunState,
    pub done: i64,
    pub total: i64,
    pub last_finished_at: Option<i64>,
    pub last_error: Option<crate::error::ErrorCode>,
    pub catalog_country: Option<String>,
    /// Inhaltssprache des letzten abgeschlossenen Katalog-Syncs; danach richten sich die
    /// Sprachmerkmale der gespeicherten Einträge. Nur bei `SyncKind::Catalog` gesetzt.
    pub content_locale: Option<String>,
    /// Ergebnis des letzten beendeten Laufs; vor dem ersten Lauf und während eines Laufs leer.
    pub last_summary: Option<SyncSummary>,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct SyncProgress {
    pub kind: SyncKind,
    pub phase: SyncPhase,
    pub done: i64,
    pub total: i64,
}

#[derive(Debug, Clone, Default, PartialEq, Eq, uniffi::Record)]
pub struct SyncSummary {
    pub processed: i64,
    pub failed_batches: i32,
    pub failed_images: i32,
    /// Bilder über den Größengrenzen aus `SyncConfig`; sie werden nicht gespeichert.
    pub skipped_images: i32,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct CatalogItem {
    pub id: String,
    pub title: String,
    pub description: String,
    pub poster_tall: String,
    pub poster_wide: String,
    pub episode_count: u32,
    pub launch_year: u32,
    pub is_subbed: bool,
    pub is_dubbed: bool,
    pub has_target_sub: bool,
    pub has_target_dub: bool,
    pub genres: Vec<String>,
    /// Altersstufe als Zahl; `None`, wenn die Quelle keine eindeutige liefert.
    pub age_rating: Option<u32>,
    /// `is_mature` der Quelle oder Altersstufe ab `ADULT_MIN_AGE`.
    pub is_adult: bool,
    pub content_descriptors: Vec<String>,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct CatalogPage {
    pub total: u32,
    pub items: Vec<CatalogItem>,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct MetadataItem {
    pub anilist_id: i64,
    pub title_romaji: String,
    pub title_english: String,
    pub description: String,
    pub score: u32,
    pub status: String,
    pub banner_image: String,
    pub site_url: String,
    pub genres: Vec<String>,
    pub tags: Vec<TagRank>,
}
