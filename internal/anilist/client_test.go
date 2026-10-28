package anilist

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"testing"
	"time"

	"crunchymate/internal/db"
)

func TestAniListClientAndEnricher(t *testing.T) {
	// Mock AniList GraphQL Server
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		var req struct {
			Query     string                 `json:"query"`
			Variables map[string]interface{} `json:"variables"`
		}
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
			http.Error(w, err.Error(), http.StatusBadRequest)
			return
		}

		search := ""
		if s, ok := req.Variables["s0"].(string); ok {
			search = s
		} else if s, ok := req.Variables["search"].(string); ok {
			search = s
		}

		w.Header().Set("Content-Type", "application/json")

		if search == "NonExistentAnime" {
			_ = json.NewEncoder(w).Encode(map[string]interface{}{
				"data": map[string]interface{}{
					"a0": map[string]interface{}{
						"media": []interface{}{},
					},
				},
			})
			return
		}

		resp := map[string]interface{}{
			"data": map[string]interface{}{
				"a0": map[string]interface{}{
					"media": []map[string]interface{}{
						{
							"id": 154587,
							"title": map[string]interface{}{
								"romaji":  "Sousou no Frieren",
								"english": "Frieren: Beyond Journey's End",
							},
							"description":  "The adventure is over...",
							"genres":       []string{"Adventure", "Drama", "Fantasy"},
							"averageScore": 91,
							"status":       "FINISHED",
							"coverImage": map[string]interface{}{
								"large": "https://img.example.com/frieren.jpg",
							},
							"bannerImage": "https://img.example.com/banner.jpg",
							"siteUrl":     "https://anilist.co/anime/154587",
							"tags": []map[string]interface{}{
								{"name": "Magic", "rank": 95, "isMediaSpoiler": false},
								{"name": "Elf", "rank": 91, "isMediaSpoiler": false},
								{"name": "Secret Ending", "rank": 80, "isMediaSpoiler": true},
							},
						},
					},
				},
			},
		}
		_ = json.NewEncoder(w).Encode(resp)
	}))
	defer server.Close()

	client := NewClient(server.Client())
	client.Endpoint = server.URL

	// 1. Test Search Anime
	media, err := client.SearchAnime("Frieren: Beyond Journey's End (German Dub)")
	if err != nil {
		t.Fatalf("search anime failed: %v", err)
	}
	if media == nil {
		t.Fatalf("expected media, got nil")
	}
	if media.ID != 154587 {
		t.Errorf("expected ID 154587, got %d", media.ID)
	}
	if media.AverageScore != 91 {
		t.Errorf("expected score 91, got %d", media.AverageScore)
	}

	// 2. Test Non-existent anime
	notFound, err := client.SearchAnime("NonExistentAnime")
	if err != nil {
		t.Fatalf("search returned error on non-existent anime: %v", err)
	}
	if notFound != nil {
		t.Errorf("expected nil for non-existent anime, got %+v", notFound)
	}

	// 3. Test EnrichOne in DB
	tempDir, err := os.MkdirTemp("", "anilist-test-*")
	if err != nil {
		t.Fatal(err)
	}
	defer os.RemoveAll(tempDir)

	store, err := db.NewStore(filepath.Join(tempDir, "test.db"))
	if err != nil {
		t.Fatal(err)
	}
	defer store.Close()

	err = store.UpsertAnime(db.Anime{
		ID:    "CR123",
		Title: "Frieren: Beyond Journey's End",
	})
	if err != nil {
		t.Fatal(err)
	}

	enricher := NewEnricher(client, store)
	updated, err := enricher.EnrichOne("CR123", "Frieren: Beyond Journey's End")
	if err != nil {
		t.Fatalf("enrich one failed: %v", err)
	}
	if updated.AniListID == nil || *updated.AniListID != 154587 {
		t.Errorf("expected enriched AniListID 154587, got %v", updated.AniListID)
	}
	if updated.AniListScore != 91 {
		t.Errorf("expected enriched score 91, got %d", updated.AniListScore)
	}
	if len(updated.Tags) != 2 {
		t.Errorf("expected 2 non-spoiler tags, got %d (%+v)", len(updated.Tags), updated.Tags)
	}
}

func TestRateLimitRetryWithHeader(t *testing.T) {
	attempts := 0
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		attempts++
		if attempts == 1 {
			w.Header().Set("Retry-After", "0")
			w.WriteHeader(http.StatusTooManyRequests)
			_, _ = w.Write([]byte(`{"errors":[{"message":"Too Many Requests."}]}`))
			return
		}

		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(map[string]interface{}{
			"data": map[string]interface{}{
				"a0": map[string]interface{}{
					"media": []map[string]interface{}{
						{
							"id": 99999,
							"title": map[string]interface{}{
								"romaji": "Test Retry Success",
							},
						},
					},
				},
			},
		})
	}))
	defer server.Close()

	client := NewClient(server.Client())
	client.Endpoint = server.URL

	waitCalled := false
	doneCalled := false
	client.OnRateLimitWait = func(waitDuration time.Duration, attempt int) {
		waitCalled = true
		if attempt != 1 {
			t.Errorf("expected attempt 1, got %d", attempt)
		}
	}
	client.OnRateLimitDone = func() {
		doneCalled = true
	}

	media, err := client.SearchAnime("Test")
	if err != nil {
		t.Fatalf("expected successful search on retry, got err: %v", err)
	}
	if media == nil || media.ID != 99999 {
		t.Fatalf("expected media ID 99999, got %+v", media)
	}
	if attempts != 2 {
		t.Errorf("expected 2 attempts, got %d", attempts)
	}
	if !waitCalled || !doneCalled {
		t.Errorf("expected callbacks to be called, wait=%v, done=%v", waitCalled, doneCalled)
	}
}

func TestRateLimitExhaustion(t *testing.T) {
	attempts := 0
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		attempts++
		w.Header().Set("Retry-After", "0")
		w.WriteHeader(http.StatusTooManyRequests)
		_, _ = w.Write([]byte(`{"errors":[{"message":"Too Many Requests."}]}`))
	}))
	defer server.Close()

	client := NewClient(server.Client())
	client.Endpoint = server.URL

	_, err := client.SearchAnime("Test Limit")
	if err == nil {
		t.Fatalf("expected error after retry exhaustion, got nil")
	}
	if attempts != MaxRetries+1 {
		t.Errorf("expected %d attempts, got %d", MaxRetries+1, attempts)
	}
}

func TestParseWaitDuration(t *testing.T) {
	// 1. Integer seconds in Retry-After
	resp1 := &http.Response{Header: make(http.Header)}
	resp1.Header.Set("Retry-After", "30")
	if dur := parseWaitDuration(resp1); dur != 31*time.Second {
		t.Errorf("expected 31s, got %v", dur)
	}

	// 2. HTTP Date in Retry-After
	future := time.Now().Add(45 * time.Second).Truncate(time.Second)
	resp2 := &http.Response{Header: make(http.Header)}
	resp2.Header.Set("Retry-After", future.Format(http.TimeFormat))
	dur2 := parseWaitDuration(resp2)
	if dur2 < 40*time.Second || dur2 > 50*time.Second {
		t.Errorf("expected ~46s, got %v", dur2)
	}

	// 3. Fallback when no headers
	resp3 := &http.Response{Header: make(http.Header)}
	if dur := parseWaitDuration(resp3); dur != 60*time.Second {
		t.Errorf("expected 60s, got %v", dur)
	}
}

func TestSearchAnimeBatch(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(map[string]interface{}{
			"data": map[string]interface{}{
				"a0": map[string]interface{}{
					"media": []map[string]interface{}{
						{"id": 101, "title": map[string]interface{}{"romaji": "Anime One"}},
					},
				},
				"a1": map[string]interface{}{
					"media": []interface{}{}, // Not found
				},
				"a2": map[string]interface{}{
					"media": []map[string]interface{}{
						{"id": 103, "title": map[string]interface{}{"romaji": "Anime Three"}},
					},
				},
			},
		})
	}))
	defer server.Close()

	client := NewClient(server.Client())
	client.Endpoint = server.URL

	results, err := client.SearchAnimeBatch([]string{"Title 1", "NonExistent", "Title 3"})
	if err != nil {
		t.Fatalf("batch search failed: %v", err)
	}
	if len(results) != 3 {
		t.Fatalf("expected 3 results, got %d", len(results))
	}
	if results[0] == nil || results[0].ID != 101 {
		t.Errorf("expected result 0 to have ID 101, got %+v", results[0])
	}
	if results[1] != nil {
		t.Errorf("expected result 1 to be nil, got %+v", results[1])
	}
	if results[2] == nil || results[2].ID != 103 {
		t.Errorf("expected result 2 to have ID 103, got %+v", results[2])
	}
}
