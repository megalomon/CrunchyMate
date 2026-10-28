package crunchyroll

import (
	"fmt"
	"log"
	"strings"
	"sync"
	"time"

	"crunchymate/internal/db"
)

type SyncStatus struct {
	IsRunning    bool       `json:"is_running"`
	TotalItems   int        `json:"total_items"`
	SyncedCount  int        `json:"synced_count"`
	LastSyncTime *time.Time `json:"last_sync_time"`
	LastError    string     `json:"last_error"`
}

type Syncer struct {
	client *Client
	store  *db.Store
	status SyncStatus
	mu     sync.RWMutex
}

func NewSyncer(client *Client, store *db.Store) *Syncer {
	return &Syncer{
		client: client,
		store:  store,
	}
}

func (s *Syncer) Status() SyncStatus {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return s.status
}

func (s *Syncer) StartSync(limit int) error {
	s.mu.Lock()
	if s.status.IsRunning {
		s.mu.Unlock()
		return fmt.Errorf("sync is already in progress")
	}
	s.status.IsRunning = true
	s.status.LastError = ""
	s.status.SyncedCount = 0
	s.mu.Unlock()

	go func() {
		err := s.executeSync(limit)
		s.mu.Lock()
		defer s.mu.Unlock()
		s.status.IsRunning = false
		now := time.Now()
		s.status.LastSyncTime = &now
		if err != nil {
			s.status.LastError = err.Error()
			log.Printf("[Crunchyroll Syncer] error: %v", err)
		} else {
			log.Printf("[Crunchyroll Syncer] successfully synced %d items", s.status.SyncedCount)
		}
	}()

	return nil
}

func (s *Syncer) SyncOnce(limit int) error {
	s.mu.Lock()
	if s.status.IsRunning {
		s.mu.Unlock()
		return fmt.Errorf("sync is already in progress")
	}
	s.status.IsRunning = true
	s.status.LastError = ""
	s.status.SyncedCount = 0
	s.mu.Unlock()

	defer func() {
		s.mu.Lock()
		s.status.IsRunning = false
		now := time.Now()
		s.status.LastSyncTime = &now
		s.mu.Unlock()
	}()

	return s.executeSync(limit)
}

func (s *Syncer) executeSync(limit int) error {
	pageSize := 50
	start := 0
	totalSynced := 0

	for {
		countToFetch := pageSize
		if limit > 0 && totalSynced+countToFetch > limit {
			countToFetch = limit - totalSynced
		}
		if countToFetch <= 0 {
			break
		}

		page, err := s.client.FetchCatalogPage(start, countToFetch)
		if err != nil {
			return fmt.Errorf("failed to fetch page at %d: %w", start, err)
		}

		s.mu.Lock()
		s.status.TotalItems = page.Total
		s.mu.Unlock()

		if len(page.Data) == 0 {
			break
		}

		for _, item := range page.Data {
			anime := convertItemToAnime(item)
			if err := s.store.UpsertAnime(anime); err != nil {
				log.Printf("[Crunchyroll Syncer] failed to store anime %s: %v", item.ID, err)
				continue
			}
			totalSynced++
			s.mu.Lock()
			s.status.SyncedCount = totalSynced
			s.mu.Unlock()

			if limit > 0 && totalSynced >= limit {
				return nil
			}
		}

		start += len(page.Data)
		if start >= page.Total {
			break
		}

		// Gentle throttle between pages
		time.Sleep(100 * time.Millisecond)
	}

	return nil
}

func convertItemToAnime(item SeriesItem) db.Anime {
	slug := item.Slug
	if slug == "" {
		slug = item.SlugTitle
	}

	posterTall := ""
	if len(item.Images.PosterTall) > 0 && len(item.Images.PosterTall[0]) > 0 {
		// Pick highest resolution
		tallList := item.Images.PosterTall[0]
		posterTall = tallList[len(tallList)-1].Source
		for _, img := range tallList {
			if img.Width >= 480 && img.Width <= 800 {
				posterTall = img.Source
				break
			}
		}
	}

	posterWide := ""
	if len(item.Images.PosterWide) > 0 && len(item.Images.PosterWide[0]) > 0 {
		wideList := item.Images.PosterWide[0]
		posterWide = wideList[len(wideList)-1].Source
		for _, img := range wideList {
			if img.Width >= 640 && img.Width <= 1280 {
				posterWide = img.Source
				break
			}
		}
	}

	hasGerDub := false
	for _, loc := range item.SeriesMetadata.AudioLocales {
		if strings.HasPrefix(strings.ToLower(loc), "de") {
			hasGerDub = true
			break
		}
	}
	if !hasGerDub {
		t := strings.ToLower(item.Title)
		if strings.Contains(t, "german dub") || strings.Contains(t, "synchro") {
			hasGerDub = true
		}
	}

	hasGerSub := false
	for _, loc := range item.SeriesMetadata.SubtitleLocales {
		if strings.HasPrefix(strings.ToLower(loc), "de") {
			hasGerSub = true
			break
		}
	}
	if !hasGerSub {
		t := strings.ToLower(item.Title)
		if strings.Contains(t, "omu") || strings.Contains(t, "(ov)") {
			hasGerSub = true
		}
	}

	return db.Anime{
		ID:           item.ID,
		Title:        item.Title,
		Slug:         slug,
		Description:  item.Description,
		PosterTall:   posterTall,
		PosterWide:   posterWide,
		EpisodeCount: item.SeriesMetadata.EpisodeCount,
		SeasonCount:  item.SeriesMetadata.SeasonCount,
		LaunchYear:   item.SeriesMetadata.SeriesLaunchYear,
		IsSubbed:     item.SeriesMetadata.IsSubbed,
		IsDubbed:     item.SeriesMetadata.IsDubbed,
		HasGerSub:    hasGerSub,
		HasGerDub:    hasGerDub,
		Genres:       item.SeriesMetadata.TenantCategories,
	}
}
