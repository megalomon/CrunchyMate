# CrunchyMate: Crunchyroll DE & AniList Tracker Spec

## Intent & Why
A local Go-based web application to browse, search, and track anime series currently available on Crunchyroll Germany (`de-DE`), enriched with additional metadata and scores from AniList, backed by a persistent SQLite database. The user can mark series as "watched" directly from the browser UI.

## Goals
1. **Crunchyroll DE Catalog Sync:** Authenticate anonymously using Crunchyroll's public web client token and fetch series metadata for the German region (`de-DE`). Store metadata (titles, descriptions, poster/banner images, episode count, categories/genres) in SQLite.
2. **AniList Enrichment:** Fetch rich AniList data (scores, romaji/english titles, status, banner images) via AniList GraphQL API and persist in SQLite.
3. **SQLite Persistence:** Clean schema for anime items, categories/genres, AniList cache, and user watch states.
4. **Web UI & Search:**
   - Search by title/keywords (case-insensitive, partial matching).
   - Filter by categories/genres and watch status ("All", "Watched", "Unwatched").
   - Detailed anime view with full synopsis, image gallery, metadata, and AniList score.
   - Quick "Watched" toggle button directly on cards and in detail view.
   - Manual & background sync trigger with live status indicators.
5. **Single Self-Contained Binary:** Go standard library `http.Server` with embedded HTML/CSS/JS (`embed.FS`) and SQLite (`modernc.org/sqlite` for pure Go, zero CGO dependencies).

## Non-Goals
- Video streaming or playback (this is a metadata browser & tracker).
- User multi-tenancy / cloud authentication (intended for local personal use).

## Technical Constraints
- Language: Go (1.27+)
- Database: SQLite (pure Go via `modernc.org/sqlite`)
- Frontend: Embedded single-page application (HTML5/CSS3/JavaScript) served by Go binary.
- Network: Crunchyroll public endpoints (`https://beta-api.crunchyroll.com`) and AniList GraphQL (`https://graphql.anilist.co`).
