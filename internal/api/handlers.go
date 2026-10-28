package api

import (
	"encoding/json"
	"net/http"
	"strconv"
	"strings"

	"crunchymate/internal/anilist"
	"crunchymate/internal/crunchyroll"
	"crunchymate/internal/db"
)

type API struct {
	store    *db.Store
	syncer   *crunchyroll.Syncer
	enricher *anilist.Enricher
}

func writeJSON(w http.ResponseWriter, status int, data interface{}) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(data)
}

func writeError(w http.ResponseWriter, status int, message string) {
	writeJSON(w, status, map[string]string{"error": message})
}

func (a *API) HandleListAnime(w http.ResponseWriter, r *http.Request) {
	q := r.URL.Query().Get("q")
	genre := r.URL.Query().Get("genre")
	watched := r.URL.Query().Get("watched")
	meh := r.URL.Query().Get("meh")
	sortBy := r.URL.Query().Get("sort")
	lang := r.URL.Query().Get("lang")
	if lang == "" {
		gersub := r.URL.Query().Get("gersub") == "true" || r.URL.Query().Get("gersub") == "1"
		gerdub := r.URL.Query().Get("gerdub") == "true" || r.URL.Query().Get("gerdub") == "1"
		if gersub && gerdub {
			lang = "both"
		} else if gerdub {
			lang = "gerdub"
		} else if gersub {
			lang = "gersub"
		}
	}

	limit := 50
	if l := r.URL.Query().Get("limit"); l != "" {
		if val, err := strconv.Atoi(l); err == nil && val > 0 && val <= 200 {
			limit = val
		}
	}

	offset := 0
	if o := r.URL.Query().Get("offset"); o != "" {
		if val, err := strconv.Atoi(o); err == nil && val >= 0 {
			offset = val
		}
	}

	parseTags := func(param string) []string {
		var result []string
		vals := r.URL.Query()[param]
		for _, v := range vals {
			for _, part := range strings.Split(v, ",") {
				part = strings.TrimSpace(part)
				if part != "" {
					result = append(result, part)
				}
			}
		}
		return result
	}

	filter := db.AnimeFilter{
		Query:       q,
		Genre:       genre,
		MustTags:    parseTags("must_tags"),
		ExcludeTags: parseTags("exclude_tags"),
		PreferTags:  parseTags("prefer_tags"),
		Watched:     watched,
		Meh:         meh,
		Language:    lang,
		SortBy:      sortBy,
		Limit:       limit,
		Offset:      offset,
	}

	items, total, err := a.store.ListAnime(filter)
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}

	if items == nil {
		items = []db.Anime{}
	}

	writeJSON(w, http.StatusOK, map[string]interface{}{
		"items":  items,
		"total":  total,
		"limit":  limit,
		"offset": offset,
	})
}

func (a *API) HandleGetAnime(w http.ResponseWriter, r *http.Request) {
	id := r.PathValue("id")
	if id == "" {
		writeError(w, http.StatusBadRequest, "missing anime id")
		return
	}

	anime, err := a.store.GetAnime(id)
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	if anime == nil {
		writeError(w, http.StatusNotFound, "anime not found")
		return
	}

	// On-demand AniList enrichment if not yet enriched
	if anime.AniListID == nil && a.enricher != nil {
		enriched, enrichErr := a.enricher.EnrichOne(anime.ID, anime.Title)
		if enrichErr == nil && enriched != nil {
			anime = enriched
		}
	}

	writeJSON(w, http.StatusOK, anime)
}

func (a *API) HandleSetWatched(w http.ResponseWriter, r *http.Request) {
	id := r.PathValue("id")
	if id == "" {
		writeError(w, http.StatusBadRequest, "missing anime id")
		return
	}

	var req struct {
		Watched bool `json:"watched"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid json body")
		return
	}

	if err := a.store.SetWatched(id, req.Watched); err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}

	updated, err := a.store.GetAnime(id)
	if err != nil || updated == nil {
		writeError(w, http.StatusInternalServerError, "failed to reload anime")
		return
	}

	writeJSON(w, http.StatusOK, map[string]interface{}{
		"success": true,
		"anime":   updated,
	})
}

func (a *API) HandleSetMeh(w http.ResponseWriter, r *http.Request) {
	id := r.PathValue("id")
	if id == "" {
		writeError(w, http.StatusBadRequest, "missing anime id")
		return
	}

	var req struct {
		Meh bool `json:"meh"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid json body")
		return
	}

	if err := a.store.SetMeh(id, req.Meh); err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}

	updated, err := a.store.GetAnime(id)
	if err != nil || updated == nil {
		writeError(w, http.StatusInternalServerError, "failed to reload anime")
		return
	}

	writeJSON(w, http.StatusOK, map[string]interface{}{
		"success": true,
		"anime":   updated,
	})
}

func (a *API) HandleListGenres(w http.ResponseWriter, r *http.Request) {
	genres, err := a.store.ListGenres()
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	if genres == nil {
		genres = []db.GenreCount{}
	}
	writeJSON(w, http.StatusOK, genres)
}

func (a *API) HandleListTags(w http.ResponseWriter, r *http.Request) {
	tags, err := a.store.ListTags()
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	if tags == nil {
		tags = []db.TagCount{}
	}
	writeJSON(w, http.StatusOK, tags)
}

func (a *API) HandleStartSync(w http.ResponseWriter, r *http.Request) {
	limit := 0 // default 0 = all
	if r.Body != nil {
		var req struct {
			Limit int `json:"limit"`
		}
		_ = json.NewDecoder(r.Body).Decode(&req)
		if req.Limit > 0 {
			limit = req.Limit
		}
	}

	if err := a.syncer.StartSync(limit); err != nil {
		writeError(w, http.StatusConflict, err.Error())
		return
	}

	writeJSON(w, http.StatusOK, map[string]interface{}{
		"success": true,
		"message": "sync started",
	})
}

func (a *API) HandleSyncStatus(w http.ResponseWriter, r *http.Request) {
	status := a.syncer.Status()
	writeJSON(w, http.StatusOK, status)
}

func (a *API) HandleStartAniListSync(w http.ResponseWriter, r *http.Request) {
	if a.enricher == nil {
		writeError(w, http.StatusServiceUnavailable, "enricher not configured")
		return
	}
	if err := a.enricher.StartBatchSync(); err != nil {
		writeError(w, http.StatusConflict, err.Error())
		return
	}
	writeJSON(w, http.StatusOK, map[string]interface{}{
		"success": true,
		"message": "anilist sync started",
	})
}

func (a *API) HandleAniListSyncStatus(w http.ResponseWriter, r *http.Request) {
	if a.enricher == nil {
		writeJSON(w, http.StatusOK, anilist.EnrichStatus{})
		return
	}
	status := a.enricher.Status()
	writeJSON(w, http.StatusOK, status)
}

func (a *API) HandleGetStats(w http.ResponseWriter, r *http.Request) {
	_, totalAnime, _ := a.store.ListAnime(db.AnimeFilter{Limit: 1, Meh: "all"})
	_, watchedAnime, _ := a.store.ListAnime(db.AnimeFilter{Limit: 1, Watched: "watched", Meh: "all"})
	_, mehAnime, _ := a.store.ListAnime(db.AnimeFilter{Limit: 1, Meh: "only"})
	genres, _ := a.store.ListGenres()
	tags, _ := a.store.ListTags()

	var anilistDone int
	if a.enricher != nil {
		anilistDone = a.enricher.Status().AlreadyDone
	}

	writeJSON(w, http.StatusOK, map[string]interface{}{
		"total_anime":      totalAnime,
		"watched_anime":    watchedAnime,
		"meh_anime":        mehAnime,
		"genres_count":     len(genres),
		"tags_count":       len(tags),
		"anilist_enriched": anilistDone,
	})
}

func corsMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Access-Control-Allow-Origin", "*")
		w.Header().Set("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
		w.Header().Set("Access-Control-Allow-Headers", "Content-Type, Authorization")
		if r.Method == "OPTIONS" {
			w.WriteHeader(http.StatusOK)
			return
		}
		// Basic path normalization
		if strings.HasSuffix(r.URL.Path, "/") && len(r.URL.Path) > 1 {
			r.URL.Path = strings.TrimSuffix(r.URL.Path, "/")
		}
		next.ServeHTTP(w, r)
	})
}
