# CrunchyMate Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a local Go web application that syncs Crunchyroll DE anime metadata, enriches it with AniList data into SQLite, and provides a responsive browser UI for searching, filtering, viewing details, and tracking watched series.

**Architecture:** A lightweight Go server utilizing `modernc.org/sqlite` for persistent storage, modular clients for Crunchyroll discover API and AniList GraphQL, a REST API for queries and watch status updates, and an embedded responsive Single Page Application UI with zero external runtime dependencies.

**Tech Stack:** Go 1.27+, `modernc.org/sqlite`, standard library `net/http` and `embed`, HTML5/CSS3/Vanilla JS.

**Spec:** `docs/superpowers/specs/2026-10-24-crunchyroll-anilist-tracker-spec.md`

## Global Constraints

- Language: Go (1.27+)
- Database: SQLite (pure Go via `modernc.org/sqlite`)
- Frontend: Embedded single-page application (HTML5/CSS3/JavaScript) served by Go binary.
- Network: Crunchyroll public endpoints (`https://beta-api.crunchyroll.com`) and AniList GraphQL (`https://graphql.anilist.co`).

## Review Focus

1. Crunchyroll token expiration or re-authentication: Token expires in 1 hour; client must automatically refresh expired access tokens.
2. Search query with special characters / SQL injection safety: Parameterized SQL queries for title and genre filters.
3. AniList rate limiting (HTTP 429): Background enrichment and detail-triggered fetches must handle 429 gracefully with backoff and caching.
4. Empty or partial anime metadata: Handle anime records where episode counts, categories, or AniList matches are missing without crashing or breaking the UI.
5. Watch status persistence: Toggling watched status must immediately update SQLite and return updated counts to the UI without requiring full catalog reloads.

---

### Task 1: Project Setup & SQLite Database Layer

**Files:**
- Create: `go.mod`
- Create: `internal/db/db.go`
- Create: `internal/db/models.go`
- Test: `internal/db/db_test.go`

**Interfaces:**
- Produces:
  - `type Store struct`
  - `func NewStore(dbPath string) (*Store, error)`
  - `func (s *Store) Close() error`
  - `func (s *Store) UpsertAnime(a Anime) error`
  - `func (s *Store) UpdateAniList(id string, data AniListData) error`
  - `func (s *Store) SetWatched(id string, watched bool) error`
  - `func (s *Store) GetAnime(id string) (*Anime, error)`
  - `func (s *Store) ListAnime(filter AnimeFilter) ([]Anime, int, error)`
  - `func (s *Store) ListGenres() ([]GenreCount, error)`

- [ ] **Step 1: Initialize go module and write failing database tests**

Define unit tests in `internal/db/db_test.go` testing schema initialization in an in-memory SQLite database, inserting an anime record with genres, updating watched status, updating AniList fields, and querying with text and genre filters.

- [ ] **Step 2: Run test to verify it fails**

Run: `go test ./internal/db -v`
Expected: FAIL (compilation error, missing package/types).

- [ ] **Step 3: Implement SQLite storage and migrations in `internal/db`**

Implement `models.go` with `Anime`, `AniListData`, `AnimeFilter`, `GenreCount` structs. Implement `db.go` using `modernc.org/sqlite` with tables: `anime`, `genres`, `anime_genres`, and sync metadata. Ensure Foreign Keys and indexes on `title`, `watched` are enabled.

- [ ] **Step 4: Run test to verify it passes**

Run: `go test ./internal/db -v`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add go.mod go.sum internal/db/
git commit -m "feat(db): implement SQLite storage layer for anime metadata and watch status"
```

---

### Task 2: Crunchyroll API Client & Sync Engine

**Files:**
- Create: `internal/crunchyroll/client.go`
- Create: `internal/crunchyroll/sync.go`
- Test: `internal/crunchyroll/client_test.go`

**Interfaces:**
- Consumes: `*db.Store`, `db.Anime`
- Produces:
  - `type Client struct`
  - `func NewClient(httpClient *http.Client) *Client`
  - `func (c *Client) Authenticate() error`
  - `func (c *Client) FetchCatalogPage(start, count int) (*BrowseResponse, error)`
  - `type Syncer struct`
  - `func NewSyncer(client *Client, store *db.Store) *Syncer`
  - `func (s *Syncer) StartSync(limit int) error`
  - `func (s *Syncer) Status() SyncStatus`

- [ ] **Step 1: Write mock tests for Crunchyroll client and pagination**

Write tests in `internal/crunchyroll/client_test.go` using `httptest.Server` simulating Crunchyroll token grant (`/auth/v1/token`) and discover browse endpoint (`/content/v2/discover/browse`), checking token renewal when expired and correct parsing of German series metadata (titles, descriptions, categories, images).

- [ ] **Step 2: Run test to verify it fails**

Run: `go test ./internal/crunchyroll -v`
Expected: FAIL (package or types not found).

- [ ] **Step 3: Implement Crunchyroll client and sync service**

Implement OAuth2 client credential token acquisition with automatic token caching/expiry check in `internal/crunchyroll/client.go`. Implement catalog syncer in `internal/crunchyroll/sync.go` that pages through discover results, transforms items to `db.Anime`, and persists them in SQLite.

- [ ] **Step 4: Run test to verify it passes**

Run: `go test ./internal/crunchyroll -v`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add internal/crunchyroll/
git commit -m "feat(crunchyroll): add client and sync service for Crunchyroll DE catalog"
```

---

### Task 3: AniList GraphQL Client & Enrichment

**Files:**
- Create: `internal/anilist/client.go`
- Create: `internal/anilist/enricher.go`
- Test: `internal/anilist/client_test.go`

**Interfaces:**
- Consumes: `*db.Store`, `db.AniListData`
- Produces:
  - `type Client struct`
  - `func NewClient(httpClient *http.Client) *Client`
  - `func (c *Client) SearchAnime(title string) (*AniListMedia, error)`
  - `type Enricher struct`
  - `func NewEnricher(client *Client, store *db.Store) *Enricher`
  - `func (e *Enricher) EnrichOne(animeID, title string) (*db.Anime, error)`
  - `func (e *Enricher) EnrichBatch(limit int) (int, error)`

- [ ] **Step 1: Write mock tests for AniList GraphQL client**

Write unit tests in `internal/anilist/client_test.go` using `httptest.Server` returning sample AniList GraphQL responses (titles, score, status, cover image) and handling 404/no match cases cleanly.

- [ ] **Step 2: Run test to verify it fails**

Run: `go test ./internal/anilist -v`
Expected: FAIL

- [ ] **Step 3: Implement AniList GraphQL client and enricher**

Implement GraphQL request execution in `internal/anilist/client.go` targeting `https://graphql.anilist.co`. Implement title cleaning (stripping season suffixes like "Season 2" or German dub notes if present for higher hit-rates) and database update in `internal/anilist/enricher.go`.

- [ ] **Step 4: Run test to verify it passes**

Run: `go test ./internal/anilist -v`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add internal/anilist/
git commit -m "feat(anilist): add GraphQL client and metadata enricher"
```

---

### Task 4: HTTP API Handlers & Router

**Files:**
- Create: `internal/api/router.go`
- Create: `internal/api/handlers.go`
- Test: `internal/api/handlers_test.go`

**Interfaces:**
- Consumes: `*db.Store`, `*crunchyroll.Syncer`, `*anilist.Enricher`
- Produces:
  - `func NewRouter(store *db.Store, syncer *crunchyroll.Syncer, enricher *anilist.Enricher) http.Handler`

- [ ] **Step 1: Write tests for HTTP REST endpoints**

Write tests in `internal/api/handlers_test.go` using `net/http/httptest` covering:
- `GET /api/anime` (search query, genre filter, watched filter, pagination)
- `GET /api/anime/{id}` (fetching single anime, enriching via AniList on demand)
- `POST /api/anime/{id}/watched` (toggling watched status)
- `GET /api/genres` (listing all genres)
- `POST /api/sync` and `GET /api/sync/status` (triggering sync and reporting progress)

- [ ] **Step 2: Run test to verify it fails**

Run: `go test ./internal/api -v`
Expected: FAIL

- [ ] **Step 3: Implement REST handlers and routing**

Implement standard library `net/http.ServeMux` routes and JSON handlers in `internal/api/router.go` and `internal/api/handlers.go`. Add CORS, error JSON formatting, and query parameter parsing.

- [ ] **Step 4: Run test to verify it passes**

Run: `go test ./internal/api -v`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add internal/api/
git commit -m "feat(api): implement REST API endpoints for catalog, filters, sync and watch tracking"
```

---

### Task 5: Web UI Frontend (Search, Filter, Details, Watched)

**Files:**
- Create: `web/static/index.html`
- Create: `web/static/style.css`
- Create: `web/static/app.js`
- Create: `web/embed.go`
- Test: `web/embed_test.go`

**Interfaces:**
- Produces:
  - `//go:embed static/*`
  - `var StaticFS embed.FS`
  - `func StaticHandler() http.Handler`

- [ ] **Step 1: Write test for embedded static file system**

Write `web/embed_test.go` verifying that `StaticHandler()` properly serves `index.html`, `style.css`, and `app.js` with correct MIME types.

- [ ] **Step 2: Run test to verify it fails**

Run: `go test ./web -v`
Expected: FAIL

- [ ] **Step 3: Implement Web UI and embed FS**

Implement:
1. `index.html`: Responsive layout with header, live search input, genre dropdown/badges, watched filter toggle buttons ("Alle", "Gesehen", "Ungesehen"), sync trigger button with status indicator, and anime grid container. Also includes anime detail modal.
2. `style.css`: Clean, dark-mode Crunchyroll-inspired theme (orange accents, clean typography, responsive card grid, badges for AniList score and CR sub/dub, smooth transitions).
3. `app.js`: Client-side logic for debounced live search, genre filter selection, watched filter, one-click watched toggle with instant UI feedback, detail modal opening with AniList metadata, and sync trigger.
4. `embed.go`: `embed.FS` handler wrapping `http.FileServer`.

- [ ] **Step 4: Run test to verify it passes**

Run: `go test ./web -v`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add web/
git commit -m "feat(ui): create responsive dark-mode web interface with search, filters, details and watched tracker"
```

---

### Task 6: CLI Entrypoint, Initial Sync & End-to-End Verification

**Files:**
- Create: `cmd/server/main.go`
- Test: Manual end-to-end verification via curl & headless check

**Interfaces:**
- Consumes: `internal/db`, `internal/crunchyroll`, `internal/anilist`, `internal/api`, `web`

- [ ] **Step 1: Implement `cmd/server/main.go`**

Setup flags (`-port` default 8080, `-db` default `crunchymate.db`, `-sync-on-start` default true with initial batch, `-limit` default 0 for all). Initialize DB, Syncer, Enricher, API router, and static file server. Graceful shutdown on SIGINT/SIGTERM.

- [ ] **Step 2: Build the Go binary**

Run: `go build -o crunchyMate ./cmd/server`
Expected: Binary compiles without errors or warnings.

- [ ] **Step 3: Run the server and test live sync & API operations**

Start the server, trigger a sync, fetch catalog via `/api/anime`, verify SQLite contents, toggle watched status, verify AniList enrichment, and confirm UI serves correctly on `http://localhost:8080`.

- [ ] **Step 4: Commit**

```bash
git add cmd/server/
git commit -m "feat(server): wire up server entrypoint with initial sync and embedded UI"
```
