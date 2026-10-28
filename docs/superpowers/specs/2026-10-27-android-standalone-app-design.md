# Design: Standalone Android App (Serverless WebView Architecture)

## 1. Intent & Context
CrunchyMate is a catalog and tracking web application for Crunchyroll Germany anime, enriched with AniList ratings, tags, and genres, with local SQLite persistence.
Currently, users must run a Go server binary (`./crunchyMate`) on their device (e.g. in Termux or on a host machine) and connect via a web browser to `http://localhost:8080`.

The goal is to provide a standalone Android application (`.apk`) providing the exact same user interface and behavior as the browser version, **without requiring an external or embedded local HTTP/Go server**.

## 2. Goals, Non-Goals, and Anti-Goals

### Goals
- **Native Android Project**: A standard Android project structured under `android/` using Kotlin and Gradle.
- **Embedded Web Frontend**: Reuse the existing frontend assets (`index.html`, `style.css`, `app.js`) inside an Android `WebView` without modifications to the UI presentation or layout.
- **Direct Local SQLite Storage**: Manage the SQLite database directly within the Android app's private data storage directory (`databases/crunchymate.db`) using native Android SQLite APIs (`android.database.sqlite.SQLiteDatabase`), with an identical table schema.
- **Zero Local Server**: No HTTP daemon or TCP socket listener (`localhost`) may be started on Android.
- **Native Bridge Layer**: Implement communication between the web frontend and Android using `@JavascriptInterface` (`AndroidBridge`).
- **Dual-Mode Frontend Adapter**: Provide a lightweight adapter layer in `app.js` (or an injected bridge adapter) so that `fetch('/api/...')` falls back to `window.AndroidBridge` when running inside the Android app, while retaining full compatibility with the existing Go HTTP server when running in standard web browsers.
- **Native Background Sync**: Implement the Crunchyroll catalog sync and AniList GraphQL enrichment natively in Kotlin using standard HTTP and coroutines.

### Non-Goals
- Re-implementing the user interface with Jetpack Compose or Flutter widgets. The user explicitly wants the exact same web UI and experience.
- Cloud account sync or external backend servers. All data remains strictly local on the smartphone.
- Starting a Go binary via gomobile or child process.
- Database file import/export: The app strictly starts with a clean database schema and populates its catalog directly via in-app sync. No file pickers or import mechanisms are included.

### Anti-Goals
- **No Embedded HTTP Server**: Under no circumstances should an HTTP server (Go, NanoHTTPD, Ktor, etc.) run in the background on Android.
- **No Breaking Changes to the Go Version**: The Go server and web interface must continue to function unchanged.

## 3. Architecture & Components

```
+--------------------------------------------------------------+
| Android App (crunchymate.apk)                                |
|                                                              |
|  +--------------------------------------------------------+  |
|  | Android WebView                                        |  |
|  | - Assets: index.html, style.css, app.js                |  |
|  | - Dual-mode API adapter:                               |  |
|  |   Checks if window.AndroidBridge exists:               |  |
|  |     Yes -> window.AndroidBridge.<call>()               |  |
|  |     No  -> window.fetch('/api/...') (Go server mode)   |  |
|  +---------------------------+----------------------------+  |
|                              | JavascriptInterface           |
|                              v                               |
|  +--------------------------------------------------------+  |
|  | Kotlin Native Bridge (AndroidBridge)                   |  |
|  |                                                        |  |
|  |  +---------------------+      +---------------------+  |  |
|  |  | AppDatabaseHelper   |      | SyncEngine          |  |  |
|  |  | - SQLiteOpenHelper  |      | - CrunchyrollClient |  |  |
|  |  | - anime, genres,    |      | - AniListClient     |  |  |
|  |  |   tags, anime_tags  |      | - Coroutine Scope   |  |  |
|  |  +----------+----------+      +----------+----------+  |  |
|  +-------------|----------------------------|-------------+  |
|                v                            v                |
|       Local SQLite File             External APIs            |
|       /data/data/.../               - Crunchyroll API        |
|       databases/crunchymate.db      - AniList GraphQL        |
+--------------------------------------------------------------+
```

### 3.1. Directory Structure
```
crunchyMate/
  android/
    app/
      build.gradle.kts
      src/
        main/
          AndroidManifest.xml
          assets/web/            <- synced or symlinked from ../web/static/
          java/de/crunchymate/
            MainActivity.kt
            bridge/
              AndroidBridge.kt
            db/
              AppDatabaseHelper.kt
              AnimeRepository.kt
            sync/
              CrunchyrollClient.kt
              AniListClient.kt
              SyncManager.kt
    build.gradle.kts
    settings.gradle.kts
  cmd/
  internal/
  web/
    static/
      index.html
      style.css
      app.js
```

### 3.2. Database Schema (Mirroring Go SQLite)
The Android `AppDatabaseHelper` creates and executes the exact schema as `internal/db/db.go`:
- `anime`: metadata fields, timestamps, `watched`, `is_meh`, etc.
- `genres` & `anime_genres`
- `tags` & `anime_tags`
- `sync_state`: tracks Crunchyroll and AniList sync timestamps, total count, progress.

### 3.3. Bridge Contract (`AndroidBridge`)
Methods exposed to JavaScript via `@JavascriptInterface`:
1. `queryAnime(filterJson: String): String` -> returns JSON array and counts identical to `/api/anime?...`.
2. `getAnime(id: String): String` -> returns detailed JSON identical to `/api/anime/{id}`.
3. `setWatched(id: String, watched: Boolean): String` -> updates watched flag and timestamp.
4. `setMeh(id: String, isMeh: Boolean): String` -> updates meh flag and timestamp.
5. `getGenres(): String` -> returns JSON array of genres.
6. `getTags(): String` -> returns JSON array of tags with counts.
7. `getStats(): String` -> returns JSON with total, watched, meh counts.
8. `startCrSync(): String` -> triggers Crunchyroll background synchronization.
9. `getCrSyncStatus(): String` -> returns current CR sync status and progress.
10. `startAniListSync(): String` -> triggers AniList background synchronization.
11. `getAniListSyncStatus(): String` -> returns current AniList sync status and progress.

### 3.4. Dual-Mode Frontend Client
In `web/static/app.js`, API interactions will be wrapped through a helper `apiClient`:
```javascript
const apiClient = {
  async get(endpoint, params) { ... },
  async post(endpoint, body) { ... }
};
```
If `window.AndroidBridge` exists, `apiClient` parses parameters and calls the corresponding `window.AndroidBridge` method, deserializing the returned JSON string into a Promise.
If `window.AndroidBridge` does not exist, `apiClient` uses standard `window.fetch()`.

### 3.5. Native Sync Engine & Background Execution
- **Foreground Service Lifecycle**:
  - Because AniList enrichment requires ~35-45 minutes due to AniList's 30 req/min rate limit across ~1,000 series, synchronization is executed within a dedicated Android `ForegroundService` (`SyncForegroundService`) using `FOREGROUND_SERVICE_DATA_SYNC`.
  - A persistent progress notification informs the user of active sync and current progress (e.g., "CrunchyMate Sync: 124 / 980 Anime angereichert").
  - This prevents Android's Doze mode and background process termination from interrupting or killing the sync when the user switches apps or turns off the screen.
- **Crunchyroll Sync**:
  - Implements anonymous OAuth token retrieval (`/auth/v1/token` with basic auth `c3Jfd2ViOg==`).
  - Iterates over `/content/v2/discover/browse` with locale `de-DE`.
  - Parses audio locales (`de-DE` for GerDub) and subtitle locales (`de-DE` for GerSub).
  - Upserts series and relations into the local SQLite database.
- **AniList Sync**:
  - GraphQL queries to `https://graphql.anilist.co`.
  - Title cleanup (stripping "(German Dub)", "(OmU)", etc.).
  - Handles AniList rate limits (HTTP 429) gracefully.
  - Updates AniList scores, romaji/english titles, banners, genres, and tags in SQLite.

## 4. Constraints & Compatibility
- **Android Target**: Minimum SDK 26 (Android 8.0+), Target SDK 34+.
- **Zero Local Server**: No socket listening on 127.0.0.1.
- **Network Permissions**: `INTERNET` and `ACCESS_NETWORK_STATE` in `AndroidManifest.xml` for reaching Crunchyroll and AniList APIs directly.
- **Asset Packaging**: The web assets are copied or linked during build to `app/src/main/assets/web/`.
