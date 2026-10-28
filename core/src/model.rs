#[derive(Debug, Clone, Copy, PartialEq, Eq, serde::Serialize, serde::Deserialize, uniffi::Enum)]
pub enum WatchedFilter {
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

#[derive(Debug, Clone, Copy, PartialEq, Eq, serde::Serialize, serde::Deserialize, uniffi::Enum)]
pub enum LanguageFilter {
    All,
    Dub,
    Sub,
    Both,
    Any,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, serde::Serialize, serde::Deserialize, uniffi::Enum)]
pub enum SortOrder {
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
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct AnimeSummary {
    pub id: String,
    pub title: String,
    pub poster_url: String,
    pub anilist_score: i32,
    pub has_target_dub: bool,
    pub has_target_sub: bool,
    pub watched: bool,
    pub is_meh: bool,
    pub prefer_matches: i32,
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
    pub poster_url: String,
    pub banner_url: String,
    pub episode_count: i32,
    pub launch_year: i32,
    pub is_subbed: bool,
    pub is_dubbed: bool,
    pub has_target_dub: bool,
    pub has_target_sub: bool,
    pub watched: bool,
    pub is_meh: bool,
    pub genres: Vec<String>,
    pub tags: Vec<TagRank>,
    pub anilist: Option<AniListInfo>,
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
}

#[derive(Debug, Clone, PartialEq, Eq, serde::Serialize, serde::Deserialize, uniffi::Record)]
#[serde(default)]
pub struct SearchState {
    pub query: String,
    pub genre: Option<String>,
    pub language: LanguageFilter,
    pub watched: WatchedFilter,
    pub sort: SortOrder,
    pub tags: Vec<TagSelection>,
}

impl Default for SearchState {
    fn default() -> Self {
        Self {
            query: String::new(),
            genre: None,
            language: LanguageFilter::All,
            watched: WatchedFilter::All,
            sort: SortOrder::Title,
            tags: Vec::new(),
        }
    }
}

pub const DEFAULT_CONTENT_LOCALE: &str = "de-DE";

#[derive(Debug, Clone, PartialEq, Eq, serde::Serialize, serde::Deserialize, uniffi::Record)]
#[serde(default)]
pub struct Settings {
    pub content_locale: String,
    pub search: SearchState,
}

impl Default for Settings {
    fn default() -> Self {
        Self {
            content_locale: DEFAULT_CONTENT_LOCALE.to_string(),
            search: SearchState::default(),
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum SyncKind {
    Catalog,
    Metadata,
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
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct SyncProgress {
    pub kind: SyncKind,
    pub done: i64,
    pub total: i64,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct SyncSummary {
    pub processed: i64,
    pub failed_batches: i32,
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
