package crunchyroll

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"testing"

	"crunchymate/internal/db"
)

func TestCrunchyrollClientAndSync(t *testing.T) {
	// Mock Crunchyroll Server
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch r.URL.Path {
		case "/auth/v1/token":
			if r.Header.Get("Authorization") != "Basic Y3Jfd2ViOg==" {
				http.Error(w, `{"error":"invalid_client"}`, http.StatusUnauthorized)
				return
			}
			w.Header().Set("Content-Type", "application/json")
			_ = json.NewEncoder(w).Encode(map[string]interface{}{
				"access_token": "mock-token-xyz",
				"expires_in":   3600,
				"token_type":   "Bearer",
			})
		case "/content/v2/discover/browse":
			auth := r.Header.Get("Authorization")
			if auth != "Bearer mock-token-xyz" {
				http.Error(w, `{"error":"unauthorized"}`, http.StatusUnauthorized)
				return
			}
			w.Header().Set("Content-Type", "application/json")
			resp := BrowseResponse{
				Total: 2,
				Data: []SeriesItem{
					{
						ID:          "GEXH3W29Z",
						Title:       "#COMPASS2.0",
						Description: "Hero anime description in German",
						Images: ItemImages{
							PosterTall: [][]ImageDetail{
								{{Source: "https://example.com/tall.png", Width: 240, Height: 360}},
							},
							PosterWide: [][]ImageDetail{
								{{Source: "https://example.com/wide.png", Width: 640, Height: 360}},
							},
						},
						SeriesMetadata: SeriesMeta{
							EpisodeCount:     12,
							SeasonCount:      1,
							SeriesLaunchYear: 2025,
							IsSubbed:         true,
							IsDubbed:         false,
							TenantCategories: []string{"Action", "Sci-Fi"},
						},
					},
				},
			}
			_ = json.NewEncoder(w).Encode(resp)
		default:
			http.NotFound(w, r)
		}
	}))
	defer server.Close()

	client := NewClient(server.Client())
	client.BaseAuthURL = server.URL + "/auth/v1/token"
	client.BaseBrowseURL = server.URL + "/content/v2/discover/browse"

	// 1. Test Authenticate
	err := client.Authenticate()
	if err != nil {
		t.Fatalf("failed to authenticate: %v", err)
	}
	if client.token != "mock-token-xyz" {
		t.Errorf("expected token mock-token-xyz, got %q", client.token)
	}

	// 2. Test FetchCatalogPage
	page, err := client.FetchCatalogPage(0, 10)
	if err != nil {
		t.Fatalf("failed to fetch catalog page: %v", err)
	}
	if page.Total != 2 {
		t.Errorf("expected total 2, got %d", page.Total)
	}
	if len(page.Data) != 1 {
		t.Fatalf("expected 1 item in page, got %d", len(page.Data))
	}
	if page.Data[0].Title != "#COMPASS2.0" {
		t.Errorf("expected title #COMPASS2.0, got %s", page.Data[0].Title)
	}

	// 3. Test Syncer integration with DB
	tempDir, err := os.MkdirTemp("", "cr-sync-test-*")
	if err != nil {
		t.Fatal(err)
	}
	defer os.RemoveAll(tempDir)

	store, err := db.NewStore(filepath.Join(tempDir, "test.db"))
	if err != nil {
		t.Fatal(err)
	}
	defer store.Close()

	syncer := NewSyncer(client, store)
	err = syncer.SyncOnce(1)
	if err != nil {
		t.Fatalf("sync failed: %v", err)
	}

	status := syncer.Status()
	if status.SyncedCount != 1 {
		t.Errorf("expected 1 synced item, got %d", status.SyncedCount)
	}

	// Verify in DB
	a, err := store.GetAnime("GEXH3W29Z")
	if err != nil {
		t.Fatal(err)
	}
	if a == nil || a.Title != "#COMPASS2.0" {
		t.Fatalf("anime not stored properly in db")
	}
	if len(a.Genres) != 2 || a.Genres[0] != "Action" {
		t.Errorf("genres mismatch: %v", a.Genres)
	}
}
