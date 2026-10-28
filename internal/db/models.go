package db

import "time"

type AniListTag struct {
	Name string `json:"name"`
	Rank int    `json:"rank"`
}

type Anime struct {
	ID                  string       `json:"id"`
	Title               string       `json:"title"`
	Slug                string       `json:"slug"`
	Description         string       `json:"description"`
	PosterTall          string       `json:"poster_tall"`
	PosterWide          string       `json:"poster_wide"`
	EpisodeCount        int          `json:"episode_count"`
	SeasonCount         int          `json:"season_count"`
	LaunchYear          int          `json:"launch_year"`
	IsSubbed            bool         `json:"is_subbed"`
	IsDubbed            bool         `json:"is_dubbed"`
	HasGerSub           bool         `json:"has_gersub"`
	HasGerDub           bool         `json:"has_gerdub"`
	Genres              []string     `json:"genres"`
	Tags                []AniListTag `json:"tags"`
	CRUpdatedAt         time.Time    `json:"cr_updated_at"`
	AniListID           *int         `json:"anilist_id"`
	AniListTitleRomaji  string       `json:"anilist_title_romaji"`
	AniListTitleEnglish string       `json:"anilist_title_english"`
	AniListDescription  string       `json:"anilist_description"`
	AniListScore        int          `json:"anilist_score"`
	AniListStatus       string       `json:"anilist_status"`
	AniListCoverImage   string       `json:"anilist_cover_image"`
	AniListBannerImage  string       `json:"anilist_banner_image"`
	AniListSiteURL      string       `json:"anilist_site_url"`
	AniListSyncedAt     *time.Time   `json:"anilist_synced_at"`
	Watched             bool         `json:"watched"`
	WatchedAt           *time.Time   `json:"watched_at"`
	IsMeh               bool         `json:"is_meh"`
	MehAt               *time.Time   `json:"meh_at"`
	Notes               string       `json:"notes"`
}

type AniListData struct {
	AniListID           int          `json:"anilist_id"`
	AniListTitleRomaji  string       `json:"anilist_title_romaji"`
	AniListTitleEnglish string       `json:"anilist_title_english"`
	AniListDescription  string       `json:"anilist_description"`
	AniListScore        int          `json:"anilist_score"`
	AniListStatus       string       `json:"anilist_status"`
	AniListCoverImage   string       `json:"anilist_cover_image"`
	AniListBannerImage  string       `json:"anilist_banner_image"`
	AniListSiteURL      string       `json:"anilist_site_url"`
	Tags                []AniListTag `json:"tags"`
}

type AnimeFilter struct {
	Query       string   `json:"query"`
	Genre       string   `json:"genre"`
	MustTags    []string `json:"must_tags"`    // Green: Must match all
	ExcludeTags []string `json:"exclude_tags"` // Orange: Must NOT match any
	PreferTags  []string `json:"prefer_tags"`  // Blue: More matches = higher ranking
	Watched     string   `json:"watched"`      // "all", "watched", "unwatched"
	Meh         string   `json:"meh"`          // "exclude" (default), "only", "all"
	Language    string   `json:"language"`     // "all", "gersub", "gerdub", "both", "any"
	SortBy      string   `json:"sort_by"`      // "title", "score", "episodes", "year"
	Limit       int      `json:"limit"`
	Offset      int      `json:"offset"`
}

type GenreCount struct {
	Name  string `json:"name"`
	Count int    `json:"count"`
}

type TagCount struct {
	Name  string `json:"name"`
	Count int    `json:"count"`
}
