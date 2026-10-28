package api

import (
	"net/http"

	"crunchymate/internal/anilist"
	"crunchymate/internal/crunchyroll"
	"crunchymate/internal/db"
)

func NewRouter(store *db.Store, syncer *crunchyroll.Syncer, enricher *anilist.Enricher) http.Handler {
	api := &API{
		store:    store,
		syncer:   syncer,
		enricher: enricher,
	}

	mux := http.NewServeMux()

	mux.HandleFunc("GET /api/anime", api.HandleListAnime)
	mux.HandleFunc("GET /api/anime/{id}", api.HandleGetAnime)
	mux.HandleFunc("POST /api/anime/{id}/watched", api.HandleSetWatched)
	mux.HandleFunc("POST /api/anime/{id}/meh", api.HandleSetMeh)
	mux.HandleFunc("GET /api/genres", api.HandleListGenres)
	mux.HandleFunc("GET /api/tags", api.HandleListTags)
	mux.HandleFunc("POST /api/sync", api.HandleStartSync)
	mux.HandleFunc("GET /api/sync/status", api.HandleSyncStatus)
	mux.HandleFunc("POST /api/sync/anilist", api.HandleStartAniListSync)
	mux.HandleFunc("GET /api/sync/anilist/status", api.HandleAniListSyncStatus)
	mux.HandleFunc("GET /api/stats", api.HandleGetStats)

	return corsMiddleware(mux)
}
