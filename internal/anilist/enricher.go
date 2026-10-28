package anilist

import (
	"fmt"
	"log"
	"sync"
	"time"

	"crunchymate/internal/db"
)

// AniListThrottleDelay paces requests to stay strictly below AniList's 30 req/min limit (~28.5 req/min).
const (
	AniListThrottleDelay = 2100 * time.Millisecond
	AniListBatchSize     = 10
)

type EnrichStatus struct {
	IsRunning     bool       `json:"is_running"`
	TotalToEnrich int        `json:"total_to_enrich"`
	EnrichedCount int        `json:"enriched_count"`
	TotalInDB     int        `json:"total_in_db"`
	AlreadyDone   int        `json:"already_done"`
	LastSyncTime  *time.Time `json:"last_sync_time"`
	LastError     string     `json:"last_error"`
	StatusMessage string     `json:"status_message,omitempty"`
}

type Enricher struct {
	client *Client
	store  *db.Store
	status EnrichStatus
	mu     sync.RWMutex
}

func NewEnricher(client *Client, store *db.Store) *Enricher {
	e := &Enricher{
		client: client,
		store:  store,
	}

	if client != nil {
		client.OnRateLimitWait = func(waitDuration time.Duration, attempt int) {
			e.mu.Lock()
			e.status.StatusMessage = fmt.Sprintf("AniList Rate-Limit erreicht (30 Req/min). Warte %ds...", int(waitDuration.Seconds()))
			e.mu.Unlock()
		}

		client.OnRateLimitDone = func() {
			e.mu.Lock()
			e.status.StatusMessage = ""
			e.mu.Unlock()
		}
	}

	return e
}

func (e *Enricher) Status() EnrichStatus {
	e.mu.RLock()
	defer e.mu.RUnlock()

	// Update live DB counts if not running
	st := e.status
	if !st.IsRunning {
		allItems, total, err := e.store.ListAnime(db.AnimeFilter{Limit: 10000, Meh: "all"})
		if err == nil {
			st.TotalInDB = total
			done := 0
			for _, item := range allItems {
				if item.AniListID != nil {
					done++
				}
			}
			st.AlreadyDone = done
			st.TotalToEnrich = total - done
		}
	}
	return st
}

func (e *Enricher) StartBatchSync() error {
	e.mu.Lock()
	if e.status.IsRunning {
		e.mu.Unlock()
		return fmt.Errorf("AniList sync is already in progress")
	}

	e.status.IsRunning = true
	e.status.LastError = ""
	e.status.StatusMessage = ""
	e.status.EnrichedCount = 0
	e.mu.Unlock()

	go func() {
		defer func() {
			e.mu.Lock()
			e.status.IsRunning = false
			e.status.StatusMessage = ""
			now := time.Now()
			e.status.LastSyncTime = &now
			e.mu.Unlock()
		}()

		// Fetch all anime that need enrichment
		allItems, total, err := e.store.ListAnime(db.AnimeFilter{Limit: 10000, Meh: "all"})
		if err != nil {
			e.mu.Lock()
			e.status.LastError = err.Error()
			e.mu.Unlock()
			return
		}

		var unenriched []db.Anime
		done := 0
		for _, item := range allItems {
			if item.AniListID == nil {
				unenriched = append(unenriched, item)
			} else {
				done++
			}
		}

		e.mu.Lock()
		e.status.TotalInDB = total
		e.status.AlreadyDone = done
		e.status.TotalToEnrich = len(unenriched)
		e.mu.Unlock()

		log.Printf("[AniList Batch Sync] Starting enrichment for %d unenriched anime...", len(unenriched))

		for i := 0; i < len(unenriched); i += AniListBatchSize {
			e.mu.RLock()
			running := e.status.IsRunning
			e.mu.RUnlock()
			if !running {
				break
			}

			end := i + AniListBatchSize
			if end > len(unenriched) {
				end = len(unenriched)
			}
			chunk := unenriched[i:end]

			titles := make([]string, len(chunk))
			for idx, item := range chunk {
				titles[idx] = item.Title
			}

			results, err := e.client.SearchAnimeBatch(titles)
			if err != nil {
				log.Printf("[AniList Batch Sync] Warning for batch starting at %d: %v", i, err)
			} else {
				newEnriched := 0
				for idx, media := range results {
					if media != nil {
						aniData := db.AniListData{
							AniListID:           media.ID,
							AniListTitleRomaji:  media.Title.Romaji,
							AniListTitleEnglish: media.Title.English,
							AniListDescription:  media.Description,
							AniListScore:        media.AverageScore,
							AniListStatus:       media.Status,
							AniListCoverImage:   media.CoverImage.Large,
							AniListBannerImage:  media.BannerImage,
							AniListSiteURL:      media.SiteURL,
							Tags:                extractTags(media.Tags),
						}
						if updateErr := e.store.UpdateAniList(chunk[idx].ID, aniData); updateErr == nil {
							newEnriched++
						}
					}
				}
				e.mu.Lock()
				e.status.EnrichedCount += newEnriched
				e.status.AlreadyDone += len(chunk)
				e.mu.Unlock()
			}

			// Respect AniList 30 req/min rate limit (2100ms pacing)
			time.Sleep(AniListThrottleDelay)
		}

		log.Printf("[AniList Batch Sync] Finished! Enriched %d anime.", e.Status().EnrichedCount)
	}()

	return nil
}

func (e *Enricher) EnrichOne(animeID, title string) (*db.Anime, error) {
	// Network request and rate-limit wait run outside mutex lock
	media, err := e.client.SearchAnime(title)
	if err != nil {
		return nil, fmt.Errorf("failed to search anilist for %q: %w", title, err)
	}

	if media == nil {
		// No match found on AniList
		return e.store.GetAnime(animeID)
	}

	aniData := db.AniListData{
		AniListID:           media.ID,
		AniListTitleRomaji:  media.Title.Romaji,
		AniListTitleEnglish: media.Title.English,
		AniListDescription:  media.Description,
		AniListScore:        media.AverageScore,
		AniListStatus:       media.Status,
		AniListCoverImage:   media.CoverImage.Large,
		AniListBannerImage:  media.BannerImage,
		AniListSiteURL:      media.SiteURL,
		Tags:                extractTags(media.Tags),
	}

	if err := e.store.UpdateAniList(animeID, aniData); err != nil {
		return nil, fmt.Errorf("failed to update db with anilist data: %w", err)
	}

	return e.store.GetAnime(animeID)
}

func (e *Enricher) EnrichBatch(limit int) (int, error) {
	items, _, err := e.store.ListAnime(db.AnimeFilter{
		Limit: limit,
		Meh:   "all",
	})
	if err != nil {
		return 0, err
	}

	enrichedCount := 0
	for _, item := range items {
		if item.AniListID != nil {
			continue
		}

		media, err := e.client.SearchAnime(item.Title)
		if err != nil {
			log.Printf("[AniList Enricher] error querying %q: %v", item.Title, err)
			continue
		}

		if media != nil {
			aniData := db.AniListData{
				AniListID:           media.ID,
				AniListTitleRomaji:  media.Title.Romaji,
				AniListTitleEnglish: media.Title.English,
				AniListDescription:  media.Description,
				AniListScore:        media.AverageScore,
				AniListStatus:       media.Status,
				AniListCoverImage:   media.CoverImage.Large,
				AniListBannerImage:  media.BannerImage,
				AniListSiteURL:      media.SiteURL,
				Tags:                extractTags(media.Tags),
			}
			if err := e.store.UpdateAniList(item.ID, aniData); err == nil {
				enrichedCount++
			}
		}

		time.Sleep(AniListThrottleDelay)
	}

	return enrichedCount, nil
}

func extractTags(mediaTags []AniListMediaTag) []db.AniListTag {
	var tags []db.AniListTag
	for _, mt := range mediaTags {
		if mt.IsMediaSpoiler {
			continue
		}
		if mt.Rank < 40 {
			continue
		}
		tags = append(tags, db.AniListTag{
			Name: mt.Name,
			Rank: mt.Rank,
		})
	}
	return tags
}
