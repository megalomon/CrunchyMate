package api

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"testing"

	"crunchymate/internal/anilist"
	"crunchymate/internal/crunchyroll"
	"crunchymate/internal/db"
)

func setupTestRouter(t *testing.T) (http.Handler, *db.Store) {
	tempDir, err := os.MkdirTemp("", "api-test-*")
	if err != nil {
		t.Fatal(err)
	}

	store, err := db.NewStore(filepath.Join(tempDir, "test.db"))
	if err != nil {
		t.Fatal(err)
	}

	crClient := crunchyroll.NewClient(nil)
	syncer := crunchyroll.NewSyncer(crClient, store)

	aniClient := anilist.NewClient(nil)
	enricher := anilist.NewEnricher(aniClient, store)

	// Seed one anime with tags
	err = store.UpsertAnime(db.Anime{
		ID:           "test-1",
		Title:        "Attack on Titan",
		Description:  "Titans attacking walls",
		EpisodeCount: 87,
		Genres:       []string{"Action", "Fantasy"},
	})
	if err != nil {
		t.Fatal(err)
	}
	_ = store.UpdateAniList("test-1", db.AniListData{
		AniListID: 16498,
		Tags: []db.AniListTag{
			{Name: "Military", Rank: 90},
			{Name: "Post-Apocalyptic", Rank: 85},
		},
	})

	router := NewRouter(store, syncer, enricher)
	return router, store
}

func TestAPIEndpoints(t *testing.T) {
	router, _ := setupTestRouter(t)

	// 1. GET /api/anime
	req := httptest.NewRequest("GET", "/api/anime?q=Titan&genre=Action", nil)
	rec := httptest.NewRecorder()
	router.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d", rec.Code)
	}

	var listResp struct {
		Items []db.Anime `json:"items"`
		Total int        `json:"total"`
	}
	if err := json.NewDecoder(rec.Body).Decode(&listResp); err != nil {
		t.Fatalf("failed to decode response: %v", err)
	}
	if listResp.Total != 1 || len(listResp.Items) != 1 {
		t.Errorf("expected 1 anime, got %d (total %d)", len(listResp.Items), listResp.Total)
	}

	// 2. GET /api/genres
	req = httptest.NewRequest("GET", "/api/genres", nil)
	rec = httptest.NewRecorder()
	router.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d", rec.Code)
	}
	var genres []db.GenreCount
	if err := json.NewDecoder(rec.Body).Decode(&genres); err != nil {
		t.Fatal(err)
	}
	if len(genres) < 2 {
		t.Errorf("expected at least 2 genres, got %d", len(genres))
	}

	// 3. POST /api/anime/test-1/watched
	body := bytes.NewBufferString(`{"watched": true}`)
	req = httptest.NewRequest("POST", "/api/anime/test-1/watched", body)
	rec = httptest.NewRecorder()
	router.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("expected 200 for watched toggle, got %d", rec.Code)
	}

	var watchResp struct {
		Success bool     `json:"success"`
		Anime   db.Anime `json:"anime"`
	}
	if err := json.NewDecoder(rec.Body).Decode(&watchResp); err != nil {
		t.Fatal(err)
	}
	if !watchResp.Success || !watchResp.Anime.Watched {
		t.Errorf("expected watched to be true, got %v", watchResp.Anime.Watched)
	}

	// 4. GET /api/sync/status
	req = httptest.NewRequest("GET", "/api/sync/status", nil)
	rec = httptest.NewRecorder()
	router.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("expected 200 for sync status, got %d", rec.Code)
	}

	// 5. GET /api/tags
	req = httptest.NewRequest("GET", "/api/tags", nil)
	rec = httptest.NewRecorder()
	router.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("expected 200 for tags, got %d", rec.Code)
	}
	var tags []db.TagCount
	if err := json.NewDecoder(rec.Body).Decode(&tags); err != nil {
		t.Fatal(err)
	}
	if len(tags) < 2 {
		t.Errorf("expected at least 2 tags, got %d", len(tags))
	}

	// 6. GET /api/anime with must_tags and exclude_tags
	req = httptest.NewRequest("GET", "/api/anime?must_tags=Military&exclude_tags=Romance", nil)
	rec = httptest.NewRecorder()
	router.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d", rec.Code)
	}
	var tagFilterResp struct {
		Items []db.Anime `json:"items"`
		Total int        `json:"total"`
	}
	if err := json.NewDecoder(rec.Body).Decode(&tagFilterResp); err != nil {
		t.Fatal(err)
	}
	if tagFilterResp.Total != 1 {
		t.Errorf("expected 1 result matching tags, got %d", tagFilterResp.Total)
	}

	// 7. POST /api/anime/test-1/meh
	mehBody := bytes.NewBufferString(`{"meh": true}`)
	req = httptest.NewRequest("POST", "/api/anime/test-1/meh", mehBody)
	rec = httptest.NewRecorder()
	router.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("expected 200 for meh toggle, got %d", rec.Code)
	}
	var mehResp struct {
		Success bool     `json:"success"`
		Anime   db.Anime `json:"anime"`
	}
	if err := json.NewDecoder(rec.Body).Decode(&mehResp); err != nil {
		t.Fatal(err)
	}
	if !mehResp.Success || !mehResp.Anime.IsMeh {
		t.Errorf("expected is_meh to be true, got %v", mehResp.Anime.IsMeh)
	}

	// 8. GET /api/anime (default) should now exclude test-1
	req = httptest.NewRequest("GET", "/api/anime", nil)
	rec = httptest.NewRecorder()
	router.ServeHTTP(rec, req)
	var defaultResp struct {
		Total int `json:"total"`
	}
	_ = json.NewDecoder(rec.Body).Decode(&defaultResp)
	if defaultResp.Total != 0 {
		t.Errorf("expected 0 anime in default list when meh, got %d", defaultResp.Total)
	}

	// 9. GET /api/anime?meh=only should return test-1
	req = httptest.NewRequest("GET", "/api/anime?meh=only", nil)
	rec = httptest.NewRecorder()
	router.ServeHTTP(rec, req)
	var mehListResp struct {
		Total int `json:"total"`
	}
	_ = json.NewDecoder(rec.Body).Decode(&mehListResp)
	if mehListResp.Total != 1 {
		t.Errorf("expected 1 anime in meh=only, got %d", mehListResp.Total)
	}

	// 10. GET /api/stats
	req = httptest.NewRequest("GET", "/api/stats", nil)
	rec = httptest.NewRecorder()
	router.ServeHTTP(rec, req)
	if rec.Code != http.StatusOK {
		t.Fatalf("expected 200 for stats, got %d", rec.Code)
	}

	// 11. GET /api/sync/anilist/status
	req = httptest.NewRequest("GET", "/api/sync/anilist/status", nil)
	rec = httptest.NewRecorder()
	router.ServeHTTP(rec, req)
	if rec.Code != http.StatusOK {
		t.Fatalf("expected 200 for anilist sync status, got %d", rec.Code)
	}
}
