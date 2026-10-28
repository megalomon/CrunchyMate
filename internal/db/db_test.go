package db

import (
	"os"
	"path/filepath"
	"testing"
)

func TestStoreCRUD(t *testing.T) {
	tempDir, err := os.MkdirTemp("", "crunchymate-test-*")
	if err != nil {
		t.Fatalf("failed to create temp dir: %v", err)
	}
	defer os.RemoveAll(tempDir)

	dbPath := filepath.Join(tempDir, "test.db")
	store, err := NewStore(dbPath)
	if err != nil {
		t.Fatalf("failed to open store: %v", err)
	}
	defer store.Close()

	// 1. Insert/Upsert Anime
	anime := Anime{
		ID:           "CR123",
		Title:        "Frieren: Beyond Journey's End",
		Slug:         "frieren-beyond-journeys-end",
		Description:  "The adventure is over for the elf mage.",
		PosterTall:   "https://img.example.com/tall.png",
		PosterWide:   "https://img.example.com/wide.png",
		EpisodeCount: 28,
		SeasonCount:  1,
		LaunchYear:   2023,
		IsSubbed:     true,
		IsDubbed:     true,
		Genres:       []string{"Fantasy", "Adventure", "Drama"},
	}

	err = store.UpsertAnime(anime)
	if err != nil {
		t.Fatalf("failed to upsert anime: %v", err)
	}

	// 2. Query Anime by ID
	fetched, err := store.GetAnime("CR123")
	if err != nil {
		t.Fatalf("failed to get anime: %v", err)
	}
	if fetched == nil {
		t.Fatalf("expected anime CR123, got nil")
	}
	if fetched.Title != anime.Title {
		t.Errorf("expected title %q, got %q", anime.Title, fetched.Title)
	}
	if len(fetched.Genres) != 3 {
		t.Errorf("expected 3 genres, got %d", len(fetched.Genres))
	}
	if fetched.Watched {
		t.Errorf("expected watched to be false initially")
	}

	// 3. Mark as watched
	err = store.SetWatched("CR123", true)
	if err != nil {
		t.Fatalf("failed to set watched: %v", err)
	}
	fetched, err = store.GetAnime("CR123")
	if err != nil {
		t.Fatalf("failed to get anime: %v", err)
	}
	if !fetched.Watched {
		t.Errorf("expected watched to be true")
	}

	// 4. Update AniList data
	aniData := AniListData{
		AniListID:           154587,
		AniListTitleRomaji:  "Sousou no Frieren",
		AniListTitleEnglish: "Frieren: Beyond Journey's End",
		AniListDescription:  "The journey after...",
		AniListScore:        91,
		AniListStatus:       "FINISHED",
		AniListCoverImage:   "https://img.example.com/cover.jpg",
		AniListBannerImage:  "https://img.example.com/banner.jpg",
		AniListSiteURL:      "https://anilist.co/anime/154587",
	}
	err = store.UpdateAniList("CR123", aniData)
	if err != nil {
		t.Fatalf("failed to update anilist: %v", err)
	}

	fetched, err = store.GetAnime("CR123")
	if err != nil {
		t.Fatalf("failed to get anime: %v", err)
	}
	if fetched.AniListID == nil || *fetched.AniListID != 154587 {
		t.Errorf("expected AniListID 154587, got %v", fetched.AniListID)
	}
	if fetched.AniListScore != 91 {
		t.Errorf("expected score 91, got %d", fetched.AniListScore)
	}

	// 5. List with filters
	list, total, err := store.ListAnime(AnimeFilter{
		Query:   "Frieren",
		Genre:   "Fantasy",
		Watched: "watched",
	})
	if err != nil {
		t.Fatalf("failed to list anime: %v", err)
	}
	if total != 1 || len(list) != 1 {
		t.Errorf("expected 1 result, got %d (total %d)", len(list), total)
	}

	// Filter with unwatched should yield 0
	list, total, err = store.ListAnime(AnimeFilter{
		Watched: "unwatched",
	})
	if err != nil {
		t.Fatalf("failed to list anime: %v", err)
	}
	if total != 0 {
		t.Errorf("expected 0 unwatched, got %d", total)
	}

	// 6. List Genres
	genres, err := store.ListGenres()
	if err != nil {
		t.Fatalf("failed to list genres: %v", err)
	}
	if len(genres) < 3 {
		t.Errorf("expected at least 3 genres, got %d", len(genres))
	}
}

func TestTagFiltering(t *testing.T) {
	tempDir, err := os.MkdirTemp("", "crunchymate-tags-*")
	if err != nil {
		t.Fatal(err)
	}
	defer os.RemoveAll(tempDir)

	store, err := NewStore(filepath.Join(tempDir, "test.db"))
	if err != nil {
		t.Fatal(err)
	}
	defer store.Close()

	// Seed 3 Anime
	store.UpsertAnime(Anime{ID: "A1", Title: "Frieren"})
	store.UpsertAnime(Anime{ID: "A2", Title: "Slime Isekai"})
	store.UpsertAnime(Anime{ID: "A3", Title: "Yuru Camp"})

	// Update AniList with Tags
	store.UpdateAniList("A1", AniListData{
		AniListID: 1,
		Tags: []AniListTag{
			{Name: "Magic", Rank: 95},
			{Name: "Elf", Rank: 90},
			{Name: "Travel", Rank: 85},
		},
	})

	store.UpdateAniList("A2", AniListData{
		AniListID: 2,
		Tags: []AniListTag{
			{Name: "Magic", Rank: 92},
			{Name: "Isekai", Rank: 96},
			{Name: "Monsters", Rank: 80},
		},
	})

	store.UpdateAniList("A3", AniListData{
		AniListID: 3,
		Tags: []AniListTag{
			{Name: "Travel", Rank: 80},
			{Name: "Slice of Life", Rank: 90},
		},
	})

	// 1. MustTags (Green): "Magic" must appear -> should return A1 and A2
	res, total, err := store.ListAnime(AnimeFilter{
		MustTags: []string{"Magic"},
	})
	if err != nil {
		t.Fatalf("failed to query must tags: %v", err)
	}
	if total != 2 {
		t.Errorf("expected 2 anime with Magic, got %d", total)
	}

	// 2. ExcludeTags (Orange): "Isekai" must NOT appear -> excludes A2
	res, total, err = store.ListAnime(AnimeFilter{
		MustTags:    []string{"Magic"},
		ExcludeTags: []string{"Isekai"},
	})
	if err != nil {
		t.Fatalf("failed to query exclude tags: %v", err)
	}
	if total != 1 || res[0].ID != "A1" {
		t.Errorf("expected only A1 (A2 excluded), got total %d", total)
	}

	// 3. PreferTags (Blue): ["Elf", "Travel"]
	// A1 has Elf + Travel (score 2)
	// A3 has Travel (score 1)
	// A2 has neither (score 0)
	res, total, err = store.ListAnime(AnimeFilter{
		PreferTags: []string{"Elf", "Travel"},
	})
	if err != nil {
		t.Fatalf("failed to query prefer tags: %v", err)
	}
	if total != 3 {
		t.Fatalf("expected all 3 anime, got %d", total)
	}
	if res[0].ID != "A1" || res[1].ID != "A3" || res[2].ID != "A2" {
		t.Errorf("expected order A1, A3, A2, got %s, %s, %s", res[0].ID, res[1].ID, res[2].ID)
	}

	// 4. ListTags check
	tags, err := store.ListTags()
	if err != nil {
		t.Fatalf("failed to list tags: %v", err)
	}
	if len(tags) < 5 {
		t.Errorf("expected at least 5 distinct tags, got %d", len(tags))
	}
}

func TestMehStatus(t *testing.T) {
	tempDir, err := os.MkdirTemp("", "crunchymate-meh-*")
	if err != nil {
		t.Fatal(err)
	}
	defer os.RemoveAll(tempDir)

	store, err := NewStore(filepath.Join(tempDir, "test.db"))
	if err != nil {
		t.Fatal(err)
	}
	defer store.Close()

	// Seed 2 Anime
	store.UpsertAnime(Anime{ID: "CR-MEH-1", Title: "Boring Anime"})
	store.UpsertAnime(Anime{ID: "CR-GOOD-2", Title: "Great Anime"})

	// Mark CR-MEH-1 as Meh
	err = store.SetMeh("CR-MEH-1", true)
	if err != nil {
		t.Fatalf("failed to set meh: %v", err)
	}

	// 1. GetAnime should reflect IsMeh
	a, err := store.GetAnime("CR-MEH-1")
	if err != nil {
		t.Fatal(err)
	}
	if !a.IsMeh || a.MehAt == nil {
		t.Errorf("expected is_meh true and meh_at set, got %v, %v", a.IsMeh, a.MehAt)
	}

	// 2. Default ListAnime should EXCLUDE Meh anime
	res, total, err := store.ListAnime(AnimeFilter{})
	if err != nil {
		t.Fatalf("failed to list anime: %v", err)
	}
	if total != 1 || res[0].ID != "CR-GOOD-2" {
		t.Errorf("expected 1 result (excluding meh), got total %d", total)
	}

	// 3. ListAnime with Meh="only" should return ONLY Meh anime
	mehRes, totalMeh, err := store.ListAnime(AnimeFilter{
		Meh: "only",
	})
	if err != nil {
		t.Fatalf("failed to list meh anime: %v", err)
	}
	if totalMeh != 1 || mehRes[0].ID != "CR-MEH-1" {
		t.Errorf("expected only CR-MEH-1 in meh list, got total %d", totalMeh)
	}

	// 4. Remove Meh status
	err = store.SetMeh("CR-MEH-1", false)
	if err != nil {
		t.Fatalf("failed to unmark meh: %v", err)
	}
	a, _ = store.GetAnime("CR-MEH-1")
	if a.IsMeh {
		t.Errorf("expected is_meh false after unmark")
	}

	// Now default list should show both
	res, total, err = store.ListAnime(AnimeFilter{})
	if err != nil {
		t.Fatal(err)
	}
	if total != 2 {
		t.Errorf("expected 2 anime after unmarking meh, got %d", total)
	}
}

func TestGerSubGerDubFilter(t *testing.T) {
	tempDir, err := os.MkdirTemp("", "crunchymate-lang-test-*")
	if err != nil {
		t.Fatal(err)
	}
	defer os.RemoveAll(tempDir)

	store, err := NewStore(filepath.Join(tempDir, "test.db"))
	if err != nil {
		t.Fatal(err)
	}
	defer store.Close()

	// 1. Anime with only GerSub
	_ = store.UpsertAnime(Anime{
		ID:        "A1",
		Title:     "Only Sub",
		HasGerSub: true,
		HasGerDub: false,
	})

	// 2. Anime with only GerDub
	_ = store.UpsertAnime(Anime{
		ID:        "A2",
		Title:     "Only Dub",
		HasGerSub: false,
		HasGerDub: true,
	})

	// 3. Anime with both
	_ = store.UpsertAnime(Anime{
		ID:        "A3",
		Title:     "Both Sub and Dub",
		HasGerSub: true,
		HasGerDub: true,
	})

	// 4. Anime with neither
	_ = store.UpsertAnime(Anime{
		ID:        "A4",
		Title:     "English Only",
		HasGerSub: false,
		HasGerDub: false,
	})

	// Test GerDub filter
	res, total, err := store.ListAnime(AnimeFilter{Language: "gerdub"})
	if err != nil || total != 2 {
		t.Errorf("expected 2 gerdub anime, got %d (err: %v)", total, err)
	}
	for _, a := range res {
		if !a.HasGerDub {
			t.Errorf("expected anime %s to have gerdub", a.ID)
		}
	}

	// Test GerSub filter
	res, total, err = store.ListAnime(AnimeFilter{Language: "gersub"})
	if err != nil || total != 2 {
		t.Errorf("expected 2 gersub anime, got %d (err: %v)", total, err)
	}
	for _, a := range res {
		if !a.HasGerSub {
			t.Errorf("expected anime %s to have gersub", a.ID)
		}
	}

	// Test Both filter
	res, total, err = store.ListAnime(AnimeFilter{Language: "both"})
	if err != nil || total != 1 || res[0].ID != "A3" {
		t.Errorf("expected 1 anime with both, got %d (err: %v)", total, err)
	}

	// Test Any filter
	_, total, err = store.ListAnime(AnimeFilter{Language: "any"})
	if err != nil || total != 3 {
		t.Errorf("expected 3 anime with any, got %d (err: %v)", total, err)
	}
}
