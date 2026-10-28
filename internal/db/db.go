package db

import (
	"database/sql"
	"fmt"
	"strings"
	"sync"

	_ "modernc.org/sqlite"
)

type Store struct {
	db *sql.DB
	mu sync.RWMutex
}

func NewStore(dbPath string) (*Store, error) {
	db, err := sql.Open("sqlite", dbPath)
	if err != nil {
		return nil, fmt.Errorf("failed to open sqlite database: %w", err)
	}

	// Recommended SQLite settings for concurrency
	db.SetMaxOpenConns(1)

	s := &Store{db: db}
	if err := s.migrate(); err != nil {
		_ = db.Close()
		return nil, fmt.Errorf("migration failed: %w", err)
	}

	return s, nil
}

func (s *Store) Close() error {
	return s.db.Close()
}

func (s *Store) migrate() error {
	queries := []string{
		`PRAGMA foreign_keys = ON;`,
		`CREATE TABLE IF NOT EXISTS anime (
			id TEXT PRIMARY KEY,
			title TEXT NOT NULL,
			slug TEXT NOT NULL DEFAULT '',
			description TEXT NOT NULL DEFAULT '',
			poster_tall TEXT NOT NULL DEFAULT '',
			poster_wide TEXT NOT NULL DEFAULT '',
			episode_count INTEGER NOT NULL DEFAULT 0,
			season_count INTEGER NOT NULL DEFAULT 0,
			launch_year INTEGER NOT NULL DEFAULT 0,
			is_subbed BOOLEAN NOT NULL DEFAULT 0,
			is_dubbed BOOLEAN NOT NULL DEFAULT 0,
			has_gersub BOOLEAN NOT NULL DEFAULT 0,
			has_gerdub BOOLEAN NOT NULL DEFAULT 0,
			cr_updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
			anilist_id INTEGER,
			anilist_title_romaji TEXT NOT NULL DEFAULT '',
			anilist_title_english TEXT NOT NULL DEFAULT '',
			anilist_description TEXT NOT NULL DEFAULT '',
			anilist_score INTEGER NOT NULL DEFAULT 0,
			anilist_status TEXT NOT NULL DEFAULT '',
			anilist_cover_image TEXT NOT NULL DEFAULT '',
			anilist_banner_image TEXT NOT NULL DEFAULT '',
			anilist_site_url TEXT NOT NULL DEFAULT '',
			anilist_synced_at DATETIME,
			watched BOOLEAN NOT NULL DEFAULT 0,
			watched_at DATETIME,
			is_meh BOOLEAN NOT NULL DEFAULT 0,
			meh_at DATETIME,
			notes TEXT NOT NULL DEFAULT ''
		);`,
		`CREATE TABLE IF NOT EXISTS genres (
			id INTEGER PRIMARY KEY AUTOINCREMENT,
			name TEXT UNIQUE NOT NULL
		);`,
		`CREATE TABLE IF NOT EXISTS anime_genres (
			anime_id TEXT NOT NULL REFERENCES anime(id) ON DELETE CASCADE,
			genre_id INTEGER NOT NULL REFERENCES genres(id) ON DELETE CASCADE,
			PRIMARY KEY (anime_id, genre_id)
		);`,
		`CREATE TABLE IF NOT EXISTS tags (
			id INTEGER PRIMARY KEY AUTOINCREMENT,
			name TEXT UNIQUE NOT NULL
		);`,
		`CREATE TABLE IF NOT EXISTS anime_tags (
			anime_id TEXT NOT NULL REFERENCES anime(id) ON DELETE CASCADE,
			tag_id INTEGER NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
			rank INTEGER NOT NULL DEFAULT 0,
			PRIMARY KEY (anime_id, tag_id)
		);`,
		`CREATE INDEX IF NOT EXISTS idx_anime_title ON anime(title);`,
		`CREATE INDEX IF NOT EXISTS idx_anime_watched ON anime(watched);`,
		`CREATE INDEX IF NOT EXISTS idx_anime_is_meh ON anime(is_meh);`,
		`CREATE INDEX IF NOT EXISTS idx_tags_name ON tags(name);`,
		`CREATE INDEX IF NOT EXISTS idx_anime_tags_tag ON anime_tags(tag_id);`,
	}

	for _, q := range queries {
		if _, err := s.db.Exec(q); err != nil {
			return err
		}
	}
	// Safe column additions for existing databases
	_, _ = s.db.Exec("ALTER TABLE anime ADD COLUMN is_meh BOOLEAN NOT NULL DEFAULT 0;")
	_, _ = s.db.Exec("ALTER TABLE anime ADD COLUMN meh_at DATETIME;")
	_, _ = s.db.Exec("ALTER TABLE anime ADD COLUMN has_gersub BOOLEAN NOT NULL DEFAULT 0;")
	_, _ = s.db.Exec("ALTER TABLE anime ADD COLUMN has_gerdub BOOLEAN NOT NULL DEFAULT 0;")
	_, _ = s.db.Exec("CREATE INDEX IF NOT EXISTS idx_anime_gersub ON anime(has_gersub);")
	_, _ = s.db.Exec("CREATE INDEX IF NOT EXISTS idx_anime_gerdub ON anime(has_gerdub);")
	return nil
}

func (s *Store) UpsertAnime(a Anime) error {
	s.mu.Lock()
	defer s.mu.Unlock()

	tx, err := s.db.Begin()
	if err != nil {
		return err
	}
	defer tx.Rollback()

	query := `
	INSERT INTO anime (
		id, title, slug, description, poster_tall, poster_wide,
		episode_count, season_count, launch_year, is_subbed, is_dubbed,
		has_gersub, has_gerdub, cr_updated_at
	) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
	ON CONFLICT(id) DO UPDATE SET
		title = excluded.title,
		slug = excluded.slug,
		description = excluded.description,
		poster_tall = excluded.poster_tall,
		poster_wide = excluded.poster_wide,
		episode_count = excluded.episode_count,
		season_count = excluded.season_count,
		launch_year = excluded.launch_year,
		is_subbed = excluded.is_subbed,
		is_dubbed = excluded.is_dubbed,
		has_gersub = excluded.has_gersub,
		has_gerdub = excluded.has_gerdub,
		cr_updated_at = CURRENT_TIMESTAMP;
	`
	_, err = tx.Exec(query,
		a.ID, a.Title, a.Slug, a.Description, a.PosterTall, a.PosterWide,
		a.EpisodeCount, a.SeasonCount, a.LaunchYear, a.IsSubbed, a.IsDubbed,
		a.HasGerSub, a.HasGerDub,
	)
	if err != nil {
		return fmt.Errorf("failed to upsert anime: %w", err)
	}

	// Manage Genres
	if len(a.Genres) > 0 {
		for _, g := range a.Genres {
			g = strings.TrimSpace(g)
			if g == "" {
				continue
			}
			_, err = tx.Exec(`INSERT OR IGNORE INTO genres (name) VALUES (?)`, g)
			if err != nil {
				return fmt.Errorf("failed to insert genre: %w", err)
			}

			_, err = tx.Exec(`
				INSERT OR IGNORE INTO anime_genres (anime_id, genre_id)
				SELECT ?, id FROM genres WHERE name = ?
			`, a.ID, g)
			if err != nil {
				return fmt.Errorf("failed to link anime genre: %w", err)
			}
		}
	}

	return tx.Commit()
}

func (s *Store) UpdateAniList(id string, data AniListData) error {
	s.mu.Lock()
	defer s.mu.Unlock()

	query := `
	UPDATE anime SET
		anilist_id = ?,
		anilist_title_romaji = ?,
		anilist_title_english = ?,
		anilist_description = ?,
		anilist_score = ?,
		anilist_status = ?,
		anilist_cover_image = ?,
		anilist_banner_image = ?,
		anilist_site_url = ?,
		anilist_synced_at = CURRENT_TIMESTAMP
	WHERE id = ?;
	`
	_, err := s.db.Exec(query,
		data.AniListID,
		data.AniListTitleRomaji,
		data.AniListTitleEnglish,
		data.AniListDescription,
		data.AniListScore,
		data.AniListStatus,
		data.AniListCoverImage,
		data.AniListBannerImage,
		data.AniListSiteURL,
		id,
	)
	if err != nil {
		return err
	}

	// Manage Tags
	if len(data.Tags) > 0 {
		for _, t := range data.Tags {
			name := strings.TrimSpace(t.Name)
			if name == "" {
				continue
			}
			_, err = s.db.Exec(`INSERT OR IGNORE INTO tags (name) VALUES (?)`, name)
			if err != nil {
				return fmt.Errorf("failed to insert tag: %w", err)
			}

			_, err = s.db.Exec(`
				INSERT INTO anime_tags (anime_id, tag_id, rank)
				SELECT ?, id, ? FROM tags WHERE name = ?
				ON CONFLICT(anime_id, tag_id) DO UPDATE SET rank = excluded.rank
			`, id, t.Rank, name)
			if err != nil {
				return fmt.Errorf("failed to link anime tag: %w", err)
			}
		}
	}

	return nil
}

func (s *Store) SetWatched(id string, watched bool) error {
	s.mu.Lock()
	defer s.mu.Unlock()

	var query string
	if watched {
		query = `UPDATE anime SET watched = 1, watched_at = CURRENT_TIMESTAMP WHERE id = ?`
	} else {
		query = `UPDATE anime SET watched = 0, watched_at = NULL WHERE id = ?`
	}
	res, err := s.db.Exec(query, id)
	if err != nil {
		return err
	}
	n, _ := res.RowsAffected()
	if n == 0 {
		return fmt.Errorf("anime with id %s not found", id)
	}
	return nil
}

func (s *Store) SetMeh(id string, isMeh bool) error {
	s.mu.Lock()
	defer s.mu.Unlock()

	var query string
	if isMeh {
		query = `UPDATE anime SET is_meh = 1, meh_at = CURRENT_TIMESTAMP WHERE id = ?`
	} else {
		query = `UPDATE anime SET is_meh = 0, meh_at = NULL WHERE id = ?`
	}
	res, err := s.db.Exec(query, id)
	if err != nil {
		return err
	}
	n, _ := res.RowsAffected()
	if n == 0 {
		return fmt.Errorf("anime with id %s not found", id)
	}
	return nil
}

func (s *Store) GetAnime(id string) (*Anime, error) {
	s.mu.RLock()
	defer s.mu.RUnlock()

	query := `
	SELECT
		id, title, slug, description, poster_tall, poster_wide,
		episode_count, season_count, launch_year, is_subbed, is_dubbed,
		has_gersub, has_gerdub,
		cr_updated_at, anilist_id, anilist_title_romaji, anilist_title_english,
		anilist_description, anilist_score, anilist_status, anilist_cover_image,
		anilist_banner_image, anilist_site_url, anilist_synced_at,
		watched, watched_at, is_meh, meh_at, notes
	FROM anime
	WHERE id = ?;
	`
	row := s.db.QueryRow(query, id)

	var a Anime
	err := row.Scan(
		&a.ID, &a.Title, &a.Slug, &a.Description, &a.PosterTall, &a.PosterWide,
		&a.EpisodeCount, &a.SeasonCount, &a.LaunchYear, &a.IsSubbed, &a.IsDubbed,
		&a.HasGerSub, &a.HasGerDub,
		&a.CRUpdatedAt, &a.AniListID, &a.AniListTitleRomaji, &a.AniListTitleEnglish,
		&a.AniListDescription, &a.AniListScore, &a.AniListStatus, &a.AniListCoverImage,
		&a.AniListBannerImage, &a.AniListSiteURL, &a.AniListSyncedAt,
		&a.Watched, &a.WatchedAt, &a.IsMeh, &a.MehAt, &a.Notes,
	)
	if err == sql.ErrNoRows {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}

	// Fetch genres
	genres, err := s.getAnimeGenres(id)
	if err != nil {
		return nil, err
	}
	a.Genres = genres

	// Fetch tags
	tags, err := s.getAnimeTags(id)
	if err != nil {
		return nil, err
	}
	a.Tags = tags

	return &a, nil
}

func (s *Store) getAnimeGenres(animeID string) ([]string, error) {
	rows, err := s.db.Query(`
		SELECT g.name
		FROM genres g
		JOIN anime_genres ag ON ag.genre_id = g.id
		WHERE ag.anime_id = ?
		ORDER BY g.name ASC;
	`, animeID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var genres []string
	for rows.Next() {
		var g string
		if err := rows.Scan(&g); err != nil {
			return nil, err
		}
		genres = append(genres, g)
	}
	return genres, nil
}

func (s *Store) getAnimeTags(animeID string) ([]AniListTag, error) {
	rows, err := s.db.Query(`
		SELECT t.name, at.rank
		FROM tags t
		JOIN anime_tags at ON at.tag_id = t.id
		WHERE at.anime_id = ?
		ORDER BY at.rank DESC, t.name ASC;
	`, animeID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var tags []AniListTag
	for rows.Next() {
		var t AniListTag
		if err := rows.Scan(&t.Name, &t.Rank); err != nil {
			return nil, err
		}
		tags = append(tags, t)
	}
	return tags, nil
}

func (s *Store) ListAnime(filter AnimeFilter) ([]Anime, int, error) {
	s.mu.RLock()
	defer s.mu.RUnlock()

	var conditions []string
	var args []interface{}

	if strings.TrimSpace(filter.Query) != "" {
		conditions = append(conditions, "(a.title LIKE ? OR a.anilist_title_romaji LIKE ? OR a.anilist_title_english LIKE ?)")
		q := "%" + strings.TrimSpace(filter.Query) + "%"
		args = append(args, q, q, q)
	}

	if strings.TrimSpace(filter.Genre) != "" {
		conditions = append(conditions, "EXISTS (SELECT 1 FROM anime_genres ag JOIN genres g ON ag.genre_id = g.id WHERE ag.anime_id = a.id AND g.name = ?)")
		args = append(args, strings.TrimSpace(filter.Genre))
	}

	// MustTags (Green): All must be present
	for _, mt := range filter.MustTags {
		mt = strings.TrimSpace(mt)
		if mt == "" {
			continue
		}
		conditions = append(conditions, "EXISTS (SELECT 1 FROM anime_tags at JOIN tags t ON at.tag_id = t.id WHERE at.anime_id = a.id AND t.name = ?)")
		args = append(args, mt)
	}

	// ExcludeTags (Orange): None may be present
	var cleanExclude []string
	for _, et := range filter.ExcludeTags {
		et = strings.TrimSpace(et)
		if et != "" {
			cleanExclude = append(cleanExclude, et)
		}
	}
	if len(cleanExclude) > 0 {
		placeholders := make([]string, len(cleanExclude))
		for i, name := range cleanExclude {
			placeholders[i] = "?"
			args = append(args, name)
		}
		conditions = append(conditions, fmt.Sprintf("NOT EXISTS (SELECT 1 FROM anime_tags at JOIN tags t ON at.tag_id = t.id WHERE at.anime_id = a.id AND t.name IN (%s))", strings.Join(placeholders, ", ")))
	}

	if filter.Watched == "watched" {
		conditions = append(conditions, "a.watched = 1")
	} else if filter.Watched == "unwatched" {
		conditions = append(conditions, "a.watched = 0")
	}

	if filter.Meh == "only" {
		conditions = append(conditions, "a.is_meh = 1")
	} else if filter.Meh == "all" {
		// do not filter on is_meh
	} else {
		// default: hide meh animes
		conditions = append(conditions, "a.is_meh = 0")
	}

	switch strings.ToLower(filter.Language) {
	case "gerdub":
		conditions = append(conditions, "a.has_gerdub = 1")
	case "gersub":
		conditions = append(conditions, "a.has_gersub = 1")
	case "both":
		conditions = append(conditions, "a.has_gersub = 1 AND a.has_gerdub = 1")
	case "any", "either":
		conditions = append(conditions, "(a.has_gersub = 1 OR a.has_gerdub = 1)")
	}

	whereClause := ""
	if len(conditions) > 0 {
		whereClause = "WHERE " + strings.Join(conditions, " AND ")
	}

	// Count total
	countQuery := fmt.Sprintf("SELECT COUNT(*) FROM anime a %s", whereClause)
	var total int
	err := s.db.QueryRow(countQuery, args...).Scan(&total)
	if err != nil {
		return nil, 0, fmt.Errorf("failed to count anime: %w", err)
	}

	// PreferTags (Blue): Calculate prefer_score for sorting boost
	var cleanPrefer []string
	for _, pt := range filter.PreferTags {
		pt = strings.TrimSpace(pt)
		if pt != "" {
			cleanPrefer = append(cleanPrefer, pt)
		}
	}

	preferSelect := ""
	var selectArgs []interface{}
	preferOrderPrefix := ""

	if len(cleanPrefer) > 0 {
		placeholders := make([]string, len(cleanPrefer))
		for i, name := range cleanPrefer {
			placeholders[i] = "?"
			selectArgs = append(selectArgs, name)
		}
		preferSelect = fmt.Sprintf(", COALESCE((SELECT COUNT(*) FROM anime_tags at JOIN tags t ON at.tag_id = t.id WHERE at.anime_id = a.id AND t.name IN (%s)), 0) AS prefer_score", strings.Join(placeholders, ", "))
		preferOrderPrefix = "prefer_score DESC, "
	}

	// Combine args for query
	selectArgs = append(selectArgs, args...)

	// Ordering
	orderBy := fmt.Sprintf("ORDER BY %sa.title ASC", preferOrderPrefix)
	switch filter.SortBy {
	case "score":
		orderBy = fmt.Sprintf("ORDER BY %sa.anilist_score DESC, a.title ASC", preferOrderPrefix)
	case "episodes":
		orderBy = fmt.Sprintf("ORDER BY %sa.episode_count DESC, a.title ASC", preferOrderPrefix)
	case "year":
		orderBy = fmt.Sprintf("ORDER BY %sa.launch_year DESC, a.title ASC", preferOrderPrefix)
	}

	limitClause := ""
	if filter.Limit > 0 {
		limitClause = fmt.Sprintf("LIMIT %d OFFSET %d", filter.Limit, filter.Offset)
	}

	selectQuery := fmt.Sprintf(`
	SELECT
		a.id, a.title, a.slug, a.description, a.poster_tall, a.poster_wide,
		a.episode_count, a.season_count, a.launch_year, a.is_subbed, a.is_dubbed,
		a.has_gersub, a.has_gerdub,
		a.cr_updated_at, a.anilist_id, a.anilist_title_romaji, a.anilist_title_english,
		a.anilist_description, a.anilist_score, a.anilist_status, a.anilist_cover_image,
		a.anilist_banner_image, a.anilist_site_url, a.anilist_synced_at,
		a.watched, a.watched_at, a.is_meh, a.meh_at, a.notes
		%s
	FROM anime a
	%s
	%s
	%s;
	`, preferSelect, whereClause, orderBy, limitClause)

	rows, err := s.db.Query(selectQuery, selectArgs...)
	if err != nil {
		return nil, 0, fmt.Errorf("failed to query anime: %w", err)
	}
	defer rows.Close()

	var result []Anime
	for rows.Next() {
		var a Anime
		var dummyPreferScore int
		var scanTargets []interface{}
		scanTargets = append(scanTargets,
			&a.ID, &a.Title, &a.Slug, &a.Description, &a.PosterTall, &a.PosterWide,
			&a.EpisodeCount, &a.SeasonCount, &a.LaunchYear, &a.IsSubbed, &a.IsDubbed,
			&a.HasGerSub, &a.HasGerDub,
			&a.CRUpdatedAt, &a.AniListID, &a.AniListTitleRomaji, &a.AniListTitleEnglish,
			&a.AniListDescription, &a.AniListScore, &a.AniListStatus, &a.AniListCoverImage,
			&a.AniListBannerImage, &a.AniListSiteURL, &a.AniListSyncedAt,
			&a.Watched, &a.WatchedAt, &a.IsMeh, &a.MehAt, &a.Notes,
		)
		if len(cleanPrefer) > 0 {
			scanTargets = append(scanTargets, &dummyPreferScore)
		}

		if err := rows.Scan(scanTargets...); err != nil {
			return nil, 0, err
		}
		result = append(result, a)
	}

	// Populate genres & tags for each result
	for i := range result {
		g, err := s.getAnimeGenres(result[i].ID)
		if err == nil {
			result[i].Genres = g
		}
		t, err := s.getAnimeTags(result[i].ID)
		if err == nil {
			result[i].Tags = t
		}
	}

	return result, total, nil
}

func (s *Store) ListGenres() ([]GenreCount, error) {
	s.mu.RLock()
	defer s.mu.RUnlock()

	query := `
	SELECT g.name, COUNT(ag.anime_id) as count
	FROM genres g
	LEFT JOIN anime_genres ag ON ag.genre_id = g.id
	GROUP BY g.id, g.name
	HAVING count > 0
	ORDER BY g.name ASC;
	`
	rows, err := s.db.Query(query)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var genres []GenreCount
	for rows.Next() {
		var gc GenreCount
		if err := rows.Scan(&gc.Name, &gc.Count); err != nil {
			return nil, err
		}
		genres = append(genres, gc)
	}
	return genres, nil
}

func (s *Store) ListTags() ([]TagCount, error) {
	s.mu.RLock()
	defer s.mu.RUnlock()

	query := `
	SELECT t.name, COUNT(at.anime_id) as count
	FROM tags t
	JOIN anime_tags at ON at.tag_id = t.id
	GROUP BY t.id, t.name
	HAVING count > 0
	ORDER BY count DESC, t.name ASC;
	`
	rows, err := s.db.Query(query)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var tags []TagCount
	for rows.Next() {
		var tc TagCount
		if err := rows.Scan(&tc.Name, &tc.Count); err != nil {
			return nil, err
		}
		tags = append(tags, tc)
	}
	return tags, nil
}
