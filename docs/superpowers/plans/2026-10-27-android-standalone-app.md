# Standalone Android App Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a standalone Android application (`.apk`) under `android/` providing the exact same CrunchyMate web experience via an Android `WebView`, communicating with an internal SQLite database and native sync engine via a JavaScript bridge, with zero local HTTP servers.

**Architecture:** An Android project using Kotlin and Gradle embedding the existing web UI (`index.html`, `style.css`, `app.js`). Communication between JavaScript and the native app is handled via an `@JavascriptInterface` bridge (`AndroidBridge`) using a dual-mode adapter in `app.js`. The native layer uses Android's built-in SQLite framework for persistence and an Android `ForegroundService` with notification for long-running Crunchyroll and AniList catalog synchronizations.

**Tech Stack:** Kotlin, Android SDK (minSdk 26, targetSdk 34), SQLite (`android.database.sqlite`), Kotlin Coroutines, Java standard HTTP / `HttpURLConnection`, Vanilla JavaScript (ES6+).

**Spec:** `docs/superpowers/specs/2026-10-27-android-standalone-app-design.md`

## Global Constraints

- Android Target: Minimum SDK 26 (Android 8.0), Target SDK 34 (Android 14).
- Zero Local Server: No HTTP daemon or socket listener (`localhost`) may be started on Android.
- No Breaking Changes: Existing Go server and browser mode must continue to work without regressions.
- No DB Import: Database starts with an empty schema in app-private storage; catalog is populated via in-app sync.
- Persistence across updates: Database resides in `/data/data/<package>/databases/crunchymate.db` and is preserved across updates.

## Review Focus

1. **Android Bridge Async / JSON serialization**: Bridge methods invoked from WebView run on a background thread pool in Android; return values must be thread-safe JSON strings matching the Go API responses.
2. **AniList Rate Limit / 429 Handling**: AniList limits GraphQL requests to 30/min; the native client must catch HTTP 429 and exponential backoff to avoid sync failure.
3. **CORS / Asset Security in WebView**: Loading assets via `WebViewAssetLoader` with domain `https://appassets.androidplatform.net/` prevents file scheme restrictions while loading images and scripts.
4. **Foreground Service Android 14 Compliance**: Target SDK 34 requires `FOREGROUND_SERVICE_DATA_SYNC` permission and declaration in manifest.
5. **Dual-mode Fallback in `app.js`**: If `window.AndroidBridge` is absent, all network calls must seamlessly use `window.fetch()` without console errors.

---

### Task 1: Dual-Mode API Adapter in Frontend (`web/static/app.js`)

**Files:**
- Modify: `web/static/app.js:480-1140`
- Test: `web/embed_test.go` and `web/static/app_test.js` (or node verification script)

**Interfaces:**
- Consumes: `window.AndroidBridge` (when running inside Android WebView) or `window.fetch` (when running in browser).
- Produces: `apiClient.get(url, params)` and `apiClient.post(url, body)` functions resolving to parsed JSON data.

- [ ] **Step 1: Write test verifying API adapter fallback and bridge invocation**

Create a test script `web/adapter_test.js` using node or test suite to assert:
- When `window.AndroidBridge` is undefined: calls `fetch()`.
- When `window.AndroidBridge` is defined: routes to corresponding bridge method (e.g. `getGenres`, `queryAnime`).

- [ ] **Step 2: Run test to verify it fails**

Run: `node web/adapter_test.js`
Expected: FAIL (adapter not yet defined)

- [ ] **Step 3: Implement `apiClient` in `web/static/app.js`**

Implement `apiClient` object with methods:
- `get(endpoint, params)`
- `post(endpoint, body)`
Wrap existing calls (`/api/genres`, `/api/tags`, `/api/anime`, `/api/anime/:id/watched`, `/api/anime/:id/meh`, `/api/stats`, `/api/sync`, `/api/sync/status`, `/api/sync/anilist`, `/api/sync/anilist/status`) to use `apiClient`.

- [ ] **Step 4: Run test to verify it passes**

Run: `node web/adapter_test.js && go test ./...`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add web/static/app.js web/adapter_test.js
git commit -m "feat(web): add dual-mode apiClient supporting AndroidBridge and fetch"
```

---

### Task 2: Android Project Scaffolding & Gradle Setup

**Files:**
- Create: `android/settings.gradle.kts`
- Create: `android/build.gradle.kts`
- Create: `android/app/build.gradle.kts`
- Create: `android/app/src/main/AndroidManifest.xml`
- Create: `android/gradle/wrapper/gradle-wrapper.properties`
- Modify: `.gitignore`

**Interfaces:**
- Consumes: `web/static/*` assets.
- Produces: Configured Android application module `de.crunchymate` with compileSdk 34, minSdk 26, targetSdk 34, dependencies for coroutines, AndroidX Core, and Webkit.

- [ ] **Step 1: Write Gradle project configuration**

Configure `android/settings.gradle.kts` (include `:app`), root `build.gradle.kts`, and `app/build.gradle.kts`:
- Android Gradle Plugin & Kotlin plugins.
- Dependencies: `androidx.core:core-ktx:1.12.0`, `androidx.webkit:webkit:1.10.0`, `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3`, `org.json:json:20231013`.
- Task `copyWebAssets` copying `web/static/` to `app/src/main/assets/web/`.

- [ ] **Step 2: Create AndroidManifest.xml**

Declare:
- Permissions: `INTERNET`, `ACCESS_NETWORK_STATE`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `POST_NOTIFICATIONS`.
- Application with `MainActivity` and `SyncForegroundService` (foregroundServiceType="dataSync").

- [ ] **Step 3: Verify asset copy and project structure**

Verify directory layout and asset file existence in `android/app/src/main/assets/web/`.

- [ ] **Step 4: Commit**

```bash
git add android/ .gitignore
git commit -m "chore(android): scaffold Android project and Gradle build configuration"
```

---

### Task 3: Android SQLite Database Layer & Repository

**Files:**
- Create: `android/app/src/main/java/de/crunchymate/db/AppDatabaseHelper.kt`
- Create: `android/app/src/main/java/de/crunchymate/db/AnimeRepository.kt`
- Create: `android/app/src/main/java/de/crunchymate/db/Models.kt`
- Test: `android/app/src/test/java/de/crunchymate/db/AnimeRepositoryTest.kt`

**Interfaces:**
- Consumes: Android SQLite API (`android.database.sqlite.SQLiteOpenHelper`).
- Produces:
  - `AnimeRepository.queryAnime(query, genre, language, watched, meh, sort, limit, offset, blueTags, greenTags, orangeTags): JSONObject`
  - `AnimeRepository.getAnimeById(id: String): JSONObject?`
  - `AnimeRepository.setWatched(id: String, watched: Boolean): Boolean`
  - `AnimeRepository.setMeh(id: String, isMeh: Boolean): Boolean`
  - `AnimeRepository.getGenres(): JSONArray`
  - `AnimeRepository.getTags(): JSONArray`
  - `AnimeRepository.getStats(): JSONObject`
  - `AnimeRepository.upsertCrunchyrollSeries(items: List<CRSeriesItem>)`
  - `AnimeRepository.updateAniListMetadata(id: String, aniData: AniListMediaData)`

- [ ] **Step 1: Write Unit Test for Repository and SQL Query Generation**

Write unit tests verifying:
- Filtering by genre, search text, GerDub (`has_gerdub = 1`), GerSub (`has_gersub = 1`).
- Tag filtering (must include green tags, exclude orange tags, boost blue tags).
- Watched & Meh state toggling.

- [ ] **Step 2: Run test to verify it fails**

Run: `gradle test` (or CLI test execution)
Expected: FAIL (classes not yet defined)

- [ ] **Step 3: Implement Database Helper & Repository**

Implement:
- `AppDatabaseHelper.kt`: Creates `anime`, `genres`, `anime_genres`, `tags`, `anime_tags`, `sync_state` matching the Go SQLite schema.
- `AnimeRepository.kt`: Implements query building with ranking formula identical to `internal/db/db.go`.

- [ ] **Step 4: Run test to verify it passes**

Run: `gradle test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/de/crunchymate/db/ android/app/src/test/
git commit -m "feat(android): implement SQLite database helper and AnimeRepository"
```

---

### Task 4: Native Sync Engine (Crunchyroll & AniList Clients)

**Files:**
- Create: `android/app/src/main/java/de/crunchymate/sync/CrunchyrollClient.kt`
- Create: `android/app/src/main/java/de/crunchymate/sync/AniListClient.kt`
- Create: `android/app/src/main/java/de/crunchymate/sync/SyncManager.kt`
- Test: `android/app/src/test/java/de/crunchymate/sync/SyncClientTest.kt`

**Interfaces:**
- Consumes: Crunchyroll REST API (`https://beta-api.crunchyroll.com`), AniList GraphQL (`https://graphql.anilist.co`), `AnimeRepository`.
- Produces:
  - `SyncManager.startCrunchyrollSync(onProgress: (Int, Int) -> Unit)`
  - `SyncManager.startAniListSync(onProgress: (Int, Int) -> Unit)`
  - `SyncManager.getCrSyncStatus(): JSONObject`
  - `SyncManager.getAniListSyncStatus(): JSONObject`

- [ ] **Step 1: Write Unit Test for Clients and Rate Limiter**

Write unit tests verifying:
- Title cleanup regex in `AniListClient` (strips `(German Dub)`, `(OmU)`, etc.).
- Audio/Subtitle locale parsing (`de-DE`) in `CrunchyrollClient`.
- HTTP 429 retry-after backoff handling.

- [ ] **Step 2: Run test to verify it fails**

Run: `gradle test`
Expected: FAIL

- [ ] **Step 3: Implement CrunchyrollClient, AniListClient, and SyncManager**

Implement:
- `CrunchyrollClient`: Fetches anonymous token, iterates through `/content/v2/discover/browse?locale=de-DE`, batches upserts to `AnimeRepository`.
- `AniListClient`: GraphQL queries, media tag rank filtering, cover and banner retrieval.
- `SyncManager`: Coordinates background coroutine scopes, progress tracking, and sync state timestamps.

- [ ] **Step 4: Run test to verify it passes**

Run: `gradle test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/de/crunchymate/sync/
git commit -m "feat(android): implement Crunchyroll and AniList sync clients and SyncManager"
```

---

### Task 5: Background Sync Service (`SyncForegroundService`)

**Files:**
- Create: `android/app/src/main/java/de/crunchymate/sync/SyncForegroundService.kt`
- Modify: `android/app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `SyncManager`, Android `NotificationManager`.
- Produces: Foreground Service with notification channel `crunchymate_sync`, posting ongoing progress updates while syncing in background.

- [ ] **Step 1: Implement SyncForegroundService**

Implement:
- Notification channel creation with `IMPORTANCE_LOW`.
- Ongoing notification builder showing current sync phase (Crunchyroll or AniList) and progress count.
- Binding or static progress broadcast for UI sync.

- [ ] **Step 2: Verify Service registration in AndroidManifest.xml**

Ensure `<service android:name=".sync.SyncForegroundService" android:foregroundServiceType="dataSync" ... />` is present.

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/de/crunchymate/sync/SyncForegroundService.kt android/app/src/main/AndroidManifest.xml
git commit -m "feat(android): implement ForegroundService with progress notification for background sync"
```

---

### Task 6: WebView Bridge & Main Activity

**Files:**
- Create: `android/app/src/main/java/de/crunchymate/bridge/AndroidBridge.kt`
- Create: `android/app/src/main/java/de/crunchymate/MainActivity.kt`
- Create: `android/app/src/main/res/layout/activity_main.xml`

**Interfaces:**
- Consumes: `AnimeRepository`, `SyncManager`, `WebViewAssetLoader`.
- Produces: Fully interactive Android WebView running CrunchyMate frontend without local HTTP server.

- [ ] **Step 1: Implement AndroidBridge with `@JavascriptInterface`**

Expose:
- `queryAnime(filterJson: String): String`
- `getAnime(id: String): String`
- `setWatched(id: String, watched: Boolean): String`
- `setMeh(id: String, isMeh: Boolean): String`
- `getGenres(): String`
- `getTags(): String`
- `getStats(): String`
- `startCrSync(): String`
- `getCrSyncStatus(): String`
- `startAniSync(): String`
- `getAniSyncStatus(): String`

- [ ] **Step 2: Implement MainActivity**

Configure:
- `WebView` settings (JavaScript enabled, DOM storage enabled).
- `WebViewAssetLoader` serving `assets/web/` under `https://appassets.androidplatform.net/assets/`.
- Register `AndroidBridge` interface as `window.AndroidBridge`.
- Handle back-press navigation (closing modal detail view if open, else exiting app).
- Request `POST_NOTIFICATIONS` permission if on Android 13+.

- [ ] **Step 3: Verify web asset integration**

Ensure opening the app loads `index.html` and assets cleanly without CORS or mixed content warnings.

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/java/de/crunchymate/bridge/ android/app/src/main/java/de/crunchymate/MainActivity.kt android/app/src/main/res/
git commit -m "feat(android): implement AndroidBridge and MainActivity with WebViewAssetLoader"
```

---

### Task 7: Full System Verification & Regression Testing

**Files:**
- Test: All Go tests (`go test ./...`)
- Test: All Android unit tests (`android/gradlew test`)

- [ ] **Step 1: Run Go tests to verify no regressions on existing web server**

Run: `go test ./... -v`
Expected: All tests PASS.

- [ ] **Step 2: Run web adapter verification**

Verify `web/static/app.js` runs in standard browser without errors and `apiClient` works both with and without bridge.

- [ ] **Step 3: Build Android APK / assemble project**

Run Gradle assemble check to verify clean compilation.

- [ ] **Step 4: Final commit and documentation update**

Update `README.md` to document the new `android/` standalone app option and build instructions.

```bash
git add README.md
git commit -m "docs: add standalone Android app documentation"
```
