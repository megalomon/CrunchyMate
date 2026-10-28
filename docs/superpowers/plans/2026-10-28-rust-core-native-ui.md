# Rust-Kern mit nativer Compose-UI – Implementierungsplan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** CrunchyMate wird zu einer reinen Android-App mit Fachlogik in Rust (UniFFI) und nativer Jetpack-Compose-UI, gebaut und als Nightly veröffentlicht über GitHub Actions.

**Architecture:** Crate `core/` (rusqlite, reqwest/rustls, tokio, crunchyroll-rs) kapselt Persistenz, Filterlogik, Remote-Quellen hinter Traits und Sync-Abläufe; exportiert wird ein UniFFI-Objekt `Core`. Das Android-Modul `app/` enthält nur Compose-Screens, ViewModels, WorkManager-Worker und Benachrichtigungen und spricht ausschließlich über einen `CoreGateway` mit dem Kern. Go-Server, Web-Frontend und die alte Kotlin-Implementierung entfallen.

**Tech Stack:** Rust (rusqlite `bundled`, reqwest `rustls-tls`, tokio, serde, uniffi, async-trait, thiserror, regex, crunchyroll-rs, wiremock), Kotlin (Compose Material 3, Navigation Compose typsicher, Lifecycle ViewModel, Paging 3, Coil, WorkManager, kotlinx-coroutines, kotlinx-serialization, JNA), Gradle mit Version Catalog, cargo-ndk, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-10-28-rust-core-native-ui-design.md`

## Global Constraints

- `minSdk` 34, `targetSdk` 37, `compileSdk` 37 (Minor 2 nur, falls Task 1 die Verfügbarkeit bestätigt).
- Einziges ABI: `arm64-v8a` (Rust-Target `aarch64-linux-android`).
- Ausgeliefert wird ein normales Einzel-APK (`assembleRelease`/`assembleDebug`); kein App-Bundle (AAB) und keine Split-APKs (xapk/apkm): `splits { abi/density/language }` bleiben deaktiviert, es gibt keinen `bundle`-Schritt in Workflows.
- Java-Toolchain 17; Gradle, AGP, Kotlin (K2) in aktueller stabiler Version laut Task-1-Notiz, zentral in `gradle/libs.versions.toml`.
- Application-ID und Paket: `de.crunchymate`; UniFFI-Kotlin-Paket: `de.crunchymate.core.ffi`.
- Keine Emojis in Code, Kommentaren, Ressourcen, Markdown, Commit-Nachrichten. Icons als VectorDrawable, Raster nur als WebP.
- Alle UI-Texte in `app/src/main/res/values/strings.xml` (Deutsch), Pluralformen über `plurals`; Lint `HardcodedText`, `MissingTranslation`, `UnusedResources` sind Fehler.
- Kotlin-Code kommentiert wie im Projekt üblich: wenig, auf Deutsch, nur das Warum. Rust ebenso.
- Kern liefert keine anzeigefertigen Texte, nur Daten und `ErrorCode`.
- UniFFI-Typabbildung: In allen über UniFFI exportierten Records und Methoden werden statt `u32`/`u64` die Typen `i32`/`i64` verwendet (Kotlin erhält `Int`/`Long` statt `UInt`/`ULong`); die im Plan genannten unsigned Typen gelten für exportierte Signaturen in dieser Form. Kotlin verwendet die generierten Namen: Felder in camelCase (`prefer_matches` -> `preferMatches`), Enum-Werte in UPPER_SNAKE_CASE (`WatchedFilter.UNWATCHED`, `ErrorCode.RATE_LIMITED`).
- Keine Magic Numbers: Werte als benannte Konstanten oder in `SyncConfig`/`RetryConfig`.
- Kein Umgehen von Geo- oder Cloudflare-Sperren; keine Tests gegen echte APIs in der CI.
- Kein toter oder auskommentierter Code; keine `TODO`s ohne Issue.
- Commits enden mit `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Gearbeitet wird auf Branch `modernize/rust-core-native-ui`; Pushen auf diesen Branch ist jederzeit erlaubt (nach jeder Task empfohlen, damit die CI läuft), auf `main` nicht.
- Ausführung subagent-gesteuert mit höchstens drei gleichzeitigen Subagents. Parallel laufen dürfen nur Task 6 und Task 7 (beide nach Task 5), jeweils in einem eigenen Git-Worktree; die Ergebnisse werden nacheinander in den Branch übernommen, Konflikte in `Cargo.toml`, `Cargo.lock` und `core/src/lib.rs` löst der Orchestrator. Task 8 folgt nach Task 7, weil es `core/src/remote/mod.rs` erweitert. Alle übrigen Tasks laufen nacheinander.
- Bisherige Implementierung als Referenz: `git show 1d5ca54:<pfad>` (z. B. `android/app/src/main/java/de/crunchymate/db/AnimeRepository.kt`, `web/static/app.js`, `web/static/index.html`). UI-Texte daraus übernehmen, ohne Emojis.
- `adb` ist das Windows-Programm `adb.exe` im `PATH`; Dateipfade werden mit `wslpath -w` übergeben.

## Review Focus

- Prozess wird während eines Syncs beendet: `sync_state` darf nicht dauerhaft „läuft“ zeigen; `Core::new` setzt verwaiste `Running`-Zustände auf `Cancelled` (Test in Task 6).
- Suchtext mit `%`, `_` oder `\`: wird wörtlich gesucht, nicht als Platzhalter (Test in Task 5).
- Suche mit Umlauten und Großschreibung („über“ findet „Über“): Groß-/Kleinschreibung wird auch jenseits von ASCII ignoriert (Test in Task 5).
- Gespeicherter Suchzustand enthält Tags, die es nach einem Sync nicht mehr gibt: Abfrage liefert ohne Fehler, Tag bleibt in der Leiste aktiver Tags sichtbar und entfernbar (Tests in Task 5 und Task 13).
- Sync wird doppelt gestartet (Doppeltippen, Start während Lauf): es läuft genau ein Worker je Art (Test in Task 15).

---

## Phase 0: Vorbereitung

### Task 1: Toolchain einrichten und Vorprüfungen

**Files:**
- Create: `docs/superpowers/notes/2026-10-28-vorpruefung.md`

**Interfaces:**
- Produces: die Notiz mit (a) allen Versionsnummern für `libs.versions.toml` und `Cargo.toml`, (b) NDK-Version, (c) Entscheidung `compileSdk` 37 oder 37.2 inklusive AGP-Syntax, (d) Entscheidung `crunchyroll-rs` ja/nein inklusive der konkreten API-Aufrufe (Login anonym, Browse mit Start/Anzahl/Locale/Sortierung, Felder für Audio-/Untertitel-Locales, Bilder, Kategorien, Land der Sitzung).

- [ ] **Step 1: rustup installieren und Distributions-Rust ersetzen**

Run: `curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y && . ~/.cargo/env && rustup target add aarch64-linux-android && cargo install cargo-ndk`
Expected: `rustup show` zeigt stable als Standard und `aarch64-linux-android` installiert; `which cargo` zeigt `~/.cargo/bin/cargo`.

- [ ] **Step 2: Android-SDK für Linux in WSL installieren**

Commandline-Tools nach `~/Android/Sdk/cmdline-tools/latest` entpacken; `ANDROID_HOME=~/Android/Sdk` und `ANDROID_NDK_HOME` in `~/.profile` setzen; `sdkmanager "platform-tools" "platforms;android-37" "build-tools;<aktuell>" "ndk;<aktuell>"` und Lizenzen akzeptieren. Zusätzlich `actionlint` installieren (für Task 17).
Expected: `sdkmanager --list_installed` listet Plattform 37 und NDK; `actionlint -version` läuft.

- [ ] **Step 3: SDK 37.2 prüfen**

`sdkmanager --list | grep -i "android-37"` und AGP-Release-Notes zur Minor-SDK-Konfiguration lesen.
Expected: Notiz hält fest, ob `platforms;android-37.2` (o. ä.) stabil verfügbar ist und welche AGP-Syntax die Minor-Version setzt; sonst Entscheidung `compileSdk = 37`.

- [ ] **Step 4: crunchyroll-rs prüfen**

In einem Wegwerf-Crate im Scratchpad die aktuelle Version mit rustls-Feature einbinden, Doku und Quellcode lesen, anonymen Login und eine Browse-Seite (`locale` de-DE, sortiert nach Popularität, 50 Einträge) abrufen und `cargo ndk -t arm64-v8a build` ausführen.
Expected: Notiz hält fest: Version, Lizenz, Datum des letzten Releases, ob alle in „Produces“ genannten Daten erreichbar sind und ob der Android-Build gelingt. Entscheidungsregel: Nur wenn alles zutrifft, wird `crunchyroll-rs` verwendet; sonst eigene reqwest-Implementierung nach dem Vorbild von `git show 1d5ca54:android/app/src/main/java/de/crunchymate/sync/CrunchyrollClient.kt`. Das Wegwerf-Crate wird nicht eingecheckt.

- [ ] **Step 5: Aktuelle Versionen ermitteln**

Für Gradle, AGP, Kotlin, Compose-BOM, Navigation, Lifecycle, Paging, Coil, WorkManager, Coroutines, Serialization, JNA, Robolectric, ktlint-Gradle-Plugin, uniffi, rusqlite, reqwest, tokio, wiremock jeweils die aktuelle stabile Version notieren (Quelle mit Datum).
Expected: vollständige Versionstabelle in der Notiz.

- [ ] **Step 6: Commit**

```bash
git add docs/superpowers/notes/2026-10-28-vorpruefung.md
git commit -m "docs: Vorprüfung Toolchain, SDK 37.2 und crunchyroll-rs"
```

### Task 2: Altbestand entfernen, Rust-Workspace und Projektregeln in CLAUDE.md

**Files:**
- Delete: `cmd/`, `internal/`, `web/`, `go.mod`, `go.sum`
- Create: `Cargo.toml` (Workspace, `members = ["core"]`, `resolver = "2"`), `rust-toolchain.toml` (channel stable, target `aarch64-linux-android`), `core/Cargo.toml`, `core/src/lib.rs`, `core/uniffi.toml`, `core/src/bin/uniffi-bindgen.rs`
- Create/Replace: `CLAUDE.md` (die bisher nicht eingecheckte Fassung beschreibt den alten Stand und wird ersetzt)
- Modify: `.gitignore` (Go-Einträge raus; `target/`, `*.jks`, `*.keystore`, `/build/`, `app/build/` rein)

**Interfaces:**
- Produces: Crate `crunchymate-core` (`crate-type = ["cdylib", "lib"]`, `lib.name = "crunchymate_core"`); `uniffi::setup_scaffolding!()` in `lib.rs`; `core/uniffi.toml` mit `[bindings.kotlin] package_name = "de.crunchymate.core.ffi"`; Binary `uniffi-bindgen` (ruft `uniffi::uniffi_bindgen_main()`, Feature `cli`).
- Produces: `CLAUDE.md` mit dem Präfix laut `/init` und den verbindlichen Projektregeln, damit alle folgenden Tasks sie automatisch sehen: keine Emojis in Code, Kommentaren, Ressourcen, Markdown und Commit-Nachrichten (einzige Ausnahme: Spalte „Emoji-Entsprechung“ in `docs/icons.md`); Icons als VectorDrawable aus SVG, die der Auftraggeber liefert (keine eigene Icon-Auswahl), Raster nur als WebP; Strings nur in `strings.xml`; Einzel-APK ohne Splits; Clean-Code-Leitlinien aus Spec 9.5; Verweis auf Spec und Plan als maßgebliche Dokumente; Hinweis, dass Go, Web und `android/` Altbestand sind. Befehle und Architektur ergänzt Task 18.

Das Verzeichnis `android/` bleibt bis Task 11 als Referenz bestehen.

- [ ] **Step 1: Go/Web löschen, Workspace anlegen, CLAUDE.md schreiben**

- [ ] **Step 2: Build prüfen**

Run: `cargo build -p crunchymate-core && cargo test`
Expected: Build erfolgreich, 0 Tests, keine Fehler.

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "chore: Go-Server und Web-Frontend entfernen, Rust-Workspace und Projektregeln anlegen"
```

## Phase 1: Rust-Kern

### Task 3: Fehler, Modelle, Datenbank und Schema

**Files:**
- Create: `core/src/error.rs`, `core/src/model.rs`, `core/src/db/mod.rs`, `core/src/db/schema.rs`
- Modify: `core/src/lib.rs` (Module einbinden)
- Test: `core/src/db/schema.rs` (`#[cfg(test)]`)

**Interfaces:**
- Produces (`error.rs`):
  ```rust
  #[derive(Debug, thiserror::Error, uniffi::Error)]
  pub enum CoreError {
      Network { message: String },
      RateLimited { retry_after_secs: u64 },
      Auth { message: String },
      Parse { message: String },
      Database { message: String },
      Cancelled,
  }
  #[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
  pub enum ErrorCode { Network, RateLimited, Auth, Parse, Database, Cancelled }
  impl CoreError { pub fn code(&self) -> ErrorCode }
  impl From<rusqlite::Error> for CoreError   // -> Database
  impl From<reqwest::Error> for CoreError    // -> Network
  impl From<serde_json::Error> for CoreError // -> Parse
  pub type CoreResult<T> = Result<T, CoreError>;
  ```
- Produces (`model.rs`, alle `uniffi::Record`/`uniffi::Enum`, sofern Kotlin sie sieht):
  ```rust
  pub enum WatchedFilter { All, Unwatched, Watched }
  pub enum MehFilter { Hide, Only, Include }
  pub enum LanguageFilter { All, Dub, Sub, Both, Any }
  pub enum SortOrder { Title, Score, Episodes, Year }
  pub enum TagState { Prefer, Must, Exclude }
  pub struct TagSelection { pub name: String, pub state: TagState }
  pub struct AnimeFilter { query: String, genre: Option<String>, language: LanguageFilter,
      watched: WatchedFilter, meh: MehFilter, sort: SortOrder,
      prefer_tags: Vec<String>, must_tags: Vec<String>, exclude_tags: Vec<String> }
  pub struct AnimeSummary { id: String, title: String, poster_url: String, anilist_score: u32,
      has_target_dub: bool, has_target_sub: bool, watched: bool, is_meh: bool, prefer_matches: u32 }
  pub struct AnimePage { items: Vec<AnimeSummary>, total: u64 }
  pub struct TagRank { name: String, rank: u32 }
  pub struct AniListInfo { score: u32, status: String, site_url: String, description: String }
  pub struct AnimeDetail { id, title, title_romaji, title_english, description, poster_url, banner_url: String,
      episode_count: u32, launch_year: u32, is_subbed: bool, is_dubbed: bool,
      has_target_dub: bool, has_target_sub: bool, watched: bool, is_meh: bool,
      genres: Vec<String>, tags: Vec<TagRank>, anilist: Option<AniListInfo> }
  pub struct NameCount { name: String, count: u64 }
  pub struct Stats { total: u64, watched: u64, meh: u64, enriched: u64, tags: u64 }
  pub struct SearchState { query: String, genre: Option<String>, language: LanguageFilter,
      watched: WatchedFilter, sort: SortOrder, tags: Vec<TagSelection> }
  pub struct Settings { content_locale: String, search: SearchState }
  pub enum SyncKind { Catalog, Metadata }
  pub enum SyncRunState { Idle, Running, Completed, Failed, Cancelled }
  pub struct SyncStatus { kind: SyncKind, state: SyncRunState, done: u64, total: u64,
      last_finished_at: Option<i64>, last_error: Option<ErrorCode>, catalog_country: Option<String> }
  pub struct SyncProgress { kind: SyncKind, done: u64, total: u64 }
  pub struct SyncSummary { processed: u64, failed_batches: u32 }
  // nur intern (kein uniffi):
  pub struct CatalogItem { id, title, description, poster_tall, poster_wide: String, episode_count: u32,
      launch_year: u32, is_subbed: bool, is_dubbed: bool, has_target_sub: bool, has_target_dub: bool, genres: Vec<String> }
  pub struct CatalogPage { total: u32, items: Vec<CatalogItem> }
  pub struct MetadataItem { anilist_id: i64, title_romaji, title_english, description: String, score: u32,
      status, banner_image, site_url: String, genres: Vec<String>, tags: Vec<TagRank> }
  ```
  Alle Felder `pub`. `Settings::default()`: `content_locale = "de-DE"`, `SearchState` leer mit `All`/`All`/`Title`.
- Produces (`db/mod.rs`):
  ```rust
  pub struct Database { conn: std::sync::Mutex<rusqlite::Connection> }
  impl Database {
      pub fn open(path: &str) -> CoreResult<Self>      // WAL, foreign_keys=ON, migrate
      pub fn open_in_memory() -> CoreResult<Self>
      pub fn with_conn<T>(&self, f: impl FnOnce(&rusqlite::Connection) -> CoreResult<T>) -> CoreResult<T>
      pub fn with_tx<T>(&self, f: impl FnOnce(&rusqlite::Transaction) -> CoreResult<T>) -> CoreResult<T>
  }
  ```
  Beim Öffnen wird die SQL-Funktion `fold(text) -> text` registriert (`str::to_lowercase`, deterministisch).
- Produces (`db/schema.rs`): `pub const SCHEMA_VERSION: i32 = 1;` `pub fn migrate(conn: &Connection) -> CoreResult<()>` – geordnete Liste von Migrationsschritten, Stand per `PRAGMA user_version`. Tabellen und Indizes laut Spec 5.1; `anime_tags(anime_id, tag_id, rank, PRIMARY KEY(anime_id, tag_id))`, `anime_genres(anime_id, genre_id, PRIMARY KEY(...))`, `sync_state(kind TEXT PRIMARY KEY, state TEXT, done INTEGER, total INTEGER, last_finished_at INTEGER, last_error TEXT, catalog_country TEXT)`, `settings(key TEXT PRIMARY KEY, value TEXT NOT NULL)`.

- [ ] **Step 1: Tests schreiben**

```rust
#[test] fn migrate_sets_user_version() { /* open_in_memory -> PRAGMA user_version == SCHEMA_VERSION */ }
#[test] fn migrate_is_idempotent() { /* migrate zweimal auf derselben Verbindung -> Ok, Version unverändert */ }
#[test] fn schema_contains_expected_tables() { /* sqlite_master enthält anime, genres, anime_genres, tags, anime_tags, sync_state, settings */ }
#[test] fn fold_lowercases_non_ascii() { /* SELECT fold('ÜBER Ärger') == "über ärger" */ }
#[test] fn error_code_matches_variant() { /* CoreError::RateLimited{retry_after_secs: 5}.code() == ErrorCode::RateLimited */ }
```

- [ ] **Step 2: Tests ausführen**

Run: `cargo test -p crunchymate-core db::schema`
Expected: FAIL (Typen fehlen)

- [ ] **Step 3: `error.rs`, `model.rs`, `db/mod.rs`, `db/schema.rs` implementieren**

- [ ] **Step 4: Tests ausführen**

Run: `cargo test -p crunchymate-core`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add core
git commit -m "feat(core): Fehlertypen, Modelle und SQLite-Schema"
```

### Task 4: Anime-Repository (Schreiben, Einzelabruf, Listen, Statistik)

**Files:**
- Create: `core/src/db/anime.rs`, `core/src/db/test_support.rs` (`#[cfg(test)]`, Fixture-Builder)
- Test: `core/src/db/anime.rs`

**Interfaces:**
- Consumes: `Database`, `CatalogItem`, `MetadataItem`, `AnimeDetail`, `NameCount`, `Stats` (Task 3).
- Produces:
  ```rust
  pub fn upsert_catalog_page(tx: &Transaction, items: &[CatalogItem]) -> CoreResult<()>
  pub fn apply_metadata(tx: &Transaction, anime_id: &str, item: &MetadataItem) -> CoreResult<()>
  pub fn set_watched(conn: &Connection, id: &str, watched: bool, now: i64) -> CoreResult<()>
  pub fn set_meh(conn: &Connection, id: &str, meh: bool, now: i64) -> CoreResult<()>
  pub fn get_anime(conn: &Connection, id: &str) -> CoreResult<Option<AnimeDetail>>
  pub fn list_genres(conn: &Connection) -> CoreResult<Vec<NameCount>>   // nach Name sortiert
  pub fn list_tags(conn: &Connection) -> CoreResult<Vec<NameCount>>     // nach Anzahl absteigend, dann Name
  pub fn stats(conn: &Connection) -> CoreResult<Stats>
  pub fn unenriched(conn: &Connection) -> CoreResult<Vec<(String, String)>> // (id, title), anilist_id IS NULL, nach Titel
  pub fn count_enriched(conn: &Connection) -> CoreResult<u64>
  ```
  Regeln: Upsert aktualisiert Crunchyroll-Felder, lässt `watched`, `is_meh`, Zeitstempel und AniList-Felder unberührt und ergänzt Genre-Zuordnungen. `apply_metadata` setzt AniList-Felder und `anilist_synced_at`, legt Tags (`INSERT OR IGNORE`) und Zuordnungen mit `rank` an (bei Konflikt `rank` aktualisieren) und ergänzt Genres. `set_watched(true)` setzt `watched_at = now`, `false` setzt `NULL`; ebenso `meh_at`. `poster_url` = `poster_tall`, sonst `poster_wide`; `banner_url` = `anilist_banner_image`, sonst `poster_wide`. `anilist` ist `Some`, wenn `anilist_id` gesetzt ist. Tags im Detail nach `rank` absteigend. `Stats.watched` zählt `watched = 1` unabhängig von Meh; `Stats.meh` zählt `is_meh = 1`; `enriched` zählt `anilist_id IS NOT NULL`; `tags` zählt Zeilen in `tags`. Eine unbekannte ID bei `set_watched`/`set_meh` ist kein Fehler.

- [ ] **Step 1: Tests schreiben**

```rust
#[test] fn upsert_is_idempotent() { /* gleiche Seite zweimal -> stats().total == 2 */ }
#[test] fn upsert_keeps_user_marks_and_anilist_data() { /* watched, meh, apply_metadata, erneuter Upsert mit neuem Titel -> Titel neu, watched/is_meh/anilist erhalten */ }
#[test] fn set_watched_sets_and_clears_timestamp() { /* true -> watched_at == now; false -> NULL */ }
#[test] fn set_meh_sets_and_clears_timestamp() { /* analog meh_at */ }
#[test] fn get_anime_returns_detail_with_sorted_tags_and_fallbacks() { /* Tags rank 90, 50 -> [90, 50]; ohne Banner -> banner_url == poster_wide; ohne poster_tall -> poster_url == poster_wide */ }
#[test] fn get_anime_unknown_id_is_none() {}
#[test] fn stats_count_watched_regardless_of_meh() { /* watched+meh -> watched 1, meh 1 */ }
#[test] fn unenriched_lists_only_items_without_anilist_id() {}
#[test] fn list_tags_sorted_by_count_then_name() {}
```

- [ ] **Step 2: Tests ausführen**

Run: `cargo test -p crunchymate-core db::anime`
Expected: FAIL

- [ ] **Step 3: `db/anime.rs` implementieren**

- [ ] **Step 4: Tests ausführen**

Run: `cargo test -p crunchymate-core db::anime`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add core
git commit -m "feat(core): Anime-Repository mit Upsert, Markierungen und Statistik"
```

### Task 5: Filter- und Suchabfrage

**Files:**
- Create: `core/src/query.rs`
- Test: `core/src/query.rs` (tabellengetrieben, Fixture aus `db/test_support.rs`)

**Interfaces:**
- Consumes: `AnimeFilter`, `AnimePage`, `AnimeSummary` (Task 3); Fixture-Builder (Task 4).
- Produces: `pub fn query_anime(conn: &Connection, filter: &AnimeFilter, offset: u32, limit: u32) -> CoreResult<AnimePage>`; intern `fn build(filter: &AnimeFilter) -> BuiltQuery { where_sql: String, where_params: Vec<Value>, order_sql: String, prefer_params: Vec<Value> }`.

Semantik (Spec 5.2): Textsuche `fold(a.title) LIKE fold(?) ESCAPE '\'` (ebenso Romaji- und Englisch-Titel), Suchtext getrimmt, `\`, `%`, `_` mit `\` maskiert und in `%…%` eingeschlossen. Genre per `EXISTS`. Jeder `must_tag` ein eigenes `EXISTS`; `exclude_tags` als ein `NOT EXISTS … IN (…)`. Leere oder nur aus Leerzeichen bestehende Tag-Namen werden ignoriert. `WatchedFilter`: `All` ohne Bedingung, `Unwatched` `watched = 0`, `Watched` `watched = 1`. `MehFilter`: `Hide` `is_meh = 0`, `Only` `is_meh = 1`, `Include` ohne Bedingung. `LanguageFilter`: `Dub` `has_target_dub = 1`, `Sub` `has_target_sub = 1`, `Both` beide, `Any` mindestens eins. `prefer_matches` = Anzahl der `prefer_tags`, die der Eintrag hat (0, wenn keine angegeben). Sortierung: `prefer_matches DESC` immer zuerst (wenn `prefer_tags` nicht leer), dann `Score`: `anilist_score DESC`, `Episodes`: `episode_count DESC`, `Year`: `launch_year DESC`, jeweils gefolgt von `title COLLATE NOCASE ASC`; `Title`: nur `title COLLATE NOCASE ASC`. `total` ist die Anzahl ohne `LIMIT`. Genres und Tags werden für die Kachel nicht benötigt und daher nicht geladen; das erfüllt die Vorgabe „kein N+1“.

- [ ] **Step 1: Tests schreiben**

Fixture: mindestens sechs Einträge mit unterschiedlichen Titeln (darunter „Über die Welt“, „100% Liebe“, „A_B“), Scores, Episoden, Jahren, Fassungen, Gesehen/Meh, Genres und Tags. Tabellengetriebene Fälle:

```rust
#[test] fn default_filter_hides_meh_and_sorts_by_title() {}
#[test] fn text_search_matches_title_romaji_and_english() {}
#[test] fn text_search_is_case_insensitive_for_umlauts() { /* query "über" findet "Über die Welt" */ }
#[test] fn text_search_treats_wildcards_literally() { /* "100%" findet nur "100% Liebe"; "A_B" findet nicht "AxB" */ }
#[test] fn genre_filter() {}
#[test] fn must_tags_require_all() {}
#[test] fn exclude_tags_remove_any_match() {}
#[test] fn unknown_must_tag_returns_empty_page_without_error() { /* total == 0 */ }
#[test] fn prefer_tags_rank_first_then_sort_then_title() { /* Sortierung Score: Einträge mit 2 Treffern vor 1 vor 0, innerhalb gleich nach Score, Gleichstand nach Titel */ }
#[test] fn prefer_matches_reported_per_item() {}
#[test] fn watched_filters() { /* All, Unwatched, Watched */ }
#[test] fn meh_filters() { /* Hide, Only, Include */ }
#[test] fn language_filters() { /* All, Dub, Sub, Both, Any */ }
#[test] fn sort_orders_descending_with_title_tiebreak() { /* Score, Episodes, Year */ }
#[test] fn paging_returns_slice_and_full_total() { /* limit 2, offset 2 -> Einträge 3 und 4, total == Gesamtzahl */ }
#[test] fn tab_watched_includes_meh() { /* Watched + Include */ }
#[test] fn tab_meh_only_meh() { /* All + Only */ }
```

- [ ] **Step 2: Tests ausführen**

Run: `cargo test -p crunchymate-core query`
Expected: FAIL

- [ ] **Step 3: `query.rs` implementieren**

Werte ausschließlich als gebundene Parameter (`rusqlite::types::Value`); SQL-Fragmente nur aus `match` über die Enums.

- [ ] **Step 4: Tests ausführen**

Run: `cargo test -p crunchymate-core query`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add core
git commit -m "feat(core): Filter-, Such- und Sortierlogik mit Paging"
```

### Task 6: Einstellungen und Sync-Zustand

**Files:**
- Create: `core/src/db/settings.rs`, `core/src/db/sync_state.rs`
- Test: in beiden Dateien

**Interfaces:**
- Consumes: `Settings`, `SyncKind`, `SyncRunState`, `SyncStatus`, `ErrorCode` (Task 3).
- Produces:
  ```rust
  // settings.rs – Settings als JSON unter Schlüssel "settings" (serde)
  pub fn load(conn: &Connection) -> CoreResult<Settings>   // fehlt oder unlesbar -> Settings::default()
  pub fn save(conn: &Connection, settings: &Settings) -> CoreResult<()>
  // sync_state.rs
  pub fn status(conn: &Connection, kind: SyncKind) -> CoreResult<SyncStatus> // fehlt -> Idle, 0/0, None
  pub fn mark_running(conn: &Connection, kind: SyncKind, total: u64) -> CoreResult<()>
  pub fn update_progress(conn: &Connection, kind: SyncKind, done: u64, total: u64) -> CoreResult<()>
  pub fn mark_finished(conn: &Connection, kind: SyncKind, state: SyncRunState, error: Option<ErrorCode>, now: i64) -> CoreResult<()>
  pub fn set_catalog_country(conn: &Connection, country: &str) -> CoreResult<()>
  pub fn reset_orphaned_runs(conn: &Connection, now: i64) -> CoreResult<()> // Running -> Cancelled
  ```
  `mark_running` löscht `last_error`; `mark_finished` behält `done`/`total`. `catalog_country` steht nur in der Zeile `Catalog`.

- [ ] **Step 1: Tests schreiben**

```rust
#[test] fn settings_default_when_missing() { /* content_locale == "de-DE" */ }
#[test] fn settings_roundtrip_with_tags() {}
#[test] fn settings_default_when_corrupt_json() {}
#[test] fn status_idle_when_missing() {}
#[test] fn running_then_failed_keeps_progress_and_error() {}
#[test] fn reset_orphaned_runs_marks_running_as_cancelled() { /* Running -> Cancelled, last_finished_at == now; Completed bleibt */ }
#[test] fn catalog_country_persisted() {}
```

- [ ] **Step 2: Tests ausführen**

Run: `cargo test -p crunchymate-core db::settings db::sync_state`
Expected: FAIL

- [ ] **Step 3: Implementieren** (serde-Derives für `Settings`, `SearchState`, `TagSelection` und die Enums in `model.rs` ergänzen)

- [ ] **Step 4: Tests ausführen**

Run: `cargo test -p crunchymate-core`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add core
git commit -m "feat(core): Einstellungen und Sync-Zustand persistieren"
```

### Task 7: AniList-Metadatenquelle

**Files:**
- Create: `core/src/remote/mod.rs`, `core/src/remote/metadata_source.rs`, `core/src/remote/anilist.rs`
- Test: `core/tests/anilist.rs` (wiremock), Unit-Tests in `anilist.rs`

**Interfaces:**
- Consumes: `MetadataItem`, `TagRank`, `CoreError` (Task 3).
- Produces:
  ```rust
  #[async_trait::async_trait]
  pub trait MetadataSource: Send + Sync {
      async fn search_batch(&self, titles: &[String]) -> CoreResult<Vec<Option<MetadataItem>>>;
  }
  pub struct RetryConfig { pub max_retries: u32, pub default_wait: Duration, pub min_wait: Duration }
  // Default: max_retries 3, default_wait 60 s, min_wait 1 s
  pub struct AniListClient { /* endpoint, reqwest::Client, RetryConfig, blocked_until: Mutex<Option<Instant>> */ }
  impl AniListClient {
      pub const DEFAULT_ENDPOINT: &'static str = "https://graphql.anilist.co";
      pub fn new(endpoint: String, retry: RetryConfig) -> CoreResult<Self>
  }
  pub fn clean_title(title: &str) -> String
  pub fn parse_wait(headers: &reqwest::header::HeaderMap, now: SystemTime, retry: &RetryConfig) -> Duration
  ```
  Übernommen aus `git show 1d5ca54:android/app/src/main/java/de/crunchymate/sync/AniListClient.kt`:
  - `clean_title`: Regex `(?i)\s*(\(German\s*Dub\)|\[German\s*Dub\]|\(OmU\)|\(Dub\)|\(OV\)|\(Synchro\)|\(Staffel\s*\d+\))\s*` durch Leerzeichen ersetzen, trimmen; ist das Ergebnis leer, Originaltitel verwenden.
  - Batch-Query: Fragment `MediaFields { id title { romaji english native } description genres tags { name rank isMediaSpoiler } averageScore status bannerImage siteUrl }`; je Titel Variable `s<i>` und Alias `a<i>: Page(page: 1, perPage: 1) { media(search: $s<i>, type: ANIME) { ...MediaFields } }`; Ergebnisliste in Eingabereihenfolge, `None` ohne Treffer.
  - Tags: Spoiler und `rank < 40` verwerfen (Konstante `MIN_TAG_RANK = 40`); `null`-Felder werden zu leeren Strings bzw. 0.
  - `parse_wait`: `Retry-After` als Sekunden (Wert + 1 s, mindestens `min_wait`) oder HTTP-Datum (Differenz + 1 s); sonst `X-RateLimit-Reset` (Unix-Sekunden, Differenz + 1 s); sonst `default_wait`.
  - `X-RateLimit-Remaining: 0` setzt `blocked_until` auf Reset + 1 s; vor jeder Anfrage wird bis dahin gewartet (`tokio::time::sleep`).
  - 429: warten und wiederholen bis `max_retries`, danach `CoreError::RateLimited { retry_after_secs }`. 404: alle `None`. Andere Nicht-200: `Network`. `errors` ohne `data`: `Parse` mit erster Meldung.
  - Header `User-Agent: CrunchyMate/2.0`, `Content-Type`/`Accept: application/json`.

- [ ] **Step 1: Tests schreiben**

```rust
// Unit
#[test] fn clean_title_removes_markers() { /* "Frieren (German Dub)" -> "Frieren"; "X (Staffel 2)" -> "X"; "(OmU)" -> "(OmU)" (leer -> Original) */ }
#[test] fn parse_wait_seconds_plus_one() { /* "5" -> 6 s */ }
#[test] fn parse_wait_zero_uses_min_wait() {}
#[test] fn parse_wait_http_date() {}
#[test] fn parse_wait_ratelimit_reset() {}
#[test] fn parse_wait_default() { /* keine Header -> default_wait */ }
// wiremock (core/tests/anilist.rs), RetryConfig { max_retries: 3, default_wait: 0, min_wait: 0 }
#[tokio::test] async fn batch_maps_results_in_input_order_with_none_for_missing() {}
#[tokio::test] async fn batch_filters_spoiler_and_low_rank_tags() {}
#[tokio::test] async fn retries_after_429_then_succeeds() { /* erst 429 mit Retry-After 0, dann 200 -> Ok; 2 Requests */ }
#[tokio::test] async fn gives_up_after_max_retries() { /* immer 429 -> RateLimited; 4 Requests */ }
#[tokio::test] async fn graphql_errors_without_data_are_parse_errors() {}
#[tokio::test] async fn not_found_yields_all_none() {}
```

- [ ] **Step 2: Tests ausführen**

Run: `cargo test -p crunchymate-core anilist`
Expected: FAIL

- [ ] **Step 3: Implementieren**

- [ ] **Step 4: Tests ausführen**

Run: `cargo test -p crunchymate-core anilist`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add core
git commit -m "feat(core): AniList-Metadatenquelle mit Batch-Suche und Backoff"
```

### Task 8: Crunchyroll-Katalogquelle

**Files:**
- Create: `core/src/remote/catalog_source.rs`, `core/src/remote/crunchyroll.rs`, `core/src/remote/catalog_mapping.rs`
- Modify: `core/src/remote/mod.rs` (aus Task 7)
- Test: `core/src/remote/catalog_mapping.rs`, `core/tests/crunchyroll.rs` (nur bei eigener reqwest-Implementierung, wiremock)

**Interfaces:**
- Consumes: `CatalogItem`, `CatalogPage` (Task 3); Entscheidung aus Task 1.
- Produces:
  ```rust
  #[async_trait::async_trait]
  pub trait CatalogSource: Send + Sync {
      async fn session_country(&self) -> CoreResult<String>;
      async fn fetch_page(&self, start: u32, count: u32, locale: &str) -> CoreResult<CatalogPage>;
  }
  pub struct CrunchyrollSource { /* … */ }
  impl CrunchyrollSource { pub async fn connect() -> CoreResult<Self> }  // anonymer Login
  // catalog_mapping.rs – reine Funktionen, unabhängig von der Bibliothek
  pub struct RawSeries { pub id: String, pub title: String, pub description: String,
      pub poster_tall: Vec<ImageCandidate>, pub poster_wide: Vec<ImageCandidate>,
      pub episode_count: u32, pub launch_year: u32, pub is_subbed: bool, pub is_dubbed: bool,
      pub audio_locales: Vec<String>, pub subtitle_locales: Vec<String>, pub categories: Vec<String> }
  pub struct ImageCandidate { pub source: String, pub width: u32 }
  pub struct LanguageFlags { pub dub: bool, pub sub: bool }
  pub fn language_flags(audio: &[String], subtitles: &[String], title: &str, content_locale: &str) -> LanguageFlags
  pub fn pick_image(candidates: &[ImageCandidate], min_width: u32, max_width: u32) -> String
  pub fn to_catalog_item(raw: RawSeries, content_locale: &str) -> CatalogItem
  pub const POSTER_TALL_WIDTH: (u32, u32) = (480, 800);
  pub const POSTER_WIDE_WIDTH: (u32, u32) = (640, 1280);
  pub const SUPPORTED_CONTENT_LOCALES: &[&str]; // Locales, die der Katalog laut Task 1 liefert; erstes Element "de-DE"
  ```
  Regeln (aus `git show 1d5ca54:android/app/src/main/java/de/crunchymate/sync/CrunchyrollClient.kt`): Sprachvergleich über das Primärsubtag (`de-DE` -> `de`), Groß-/Kleinschreibung egal. Titelheuristik nur, wenn das Primärsubtag `de` ist: Titel enthält „german dub“ oder „synchro“ -> Dub; „omu“ oder „(ov)“ -> Sub. `pick_image`: erster Kandidat mit Breite im Bereich, sonst der letzte, leer bei keiner Auswahl. Kategorien getrimmt, leere verworfen. `session_country` liefert das Land der Sitzung in Großbuchstaben. Sortierung beim Abruf: Popularität; `locale` steuert Locale-Parameter und `Accept-Language`. Bei eigener Implementierung: Token-Abruf mit `grant_type=client_id`, bei 401 einmal neu anmelden; Land aus dem Feld `country` der Token-Antwort; Zugangsdaten als benannte Konstante wie bisher.

- [ ] **Step 1: Tests schreiben**

```rust
#[test] fn flags_from_locales() { /* audio ["de-DE"], subs [] , locale "de-DE" -> dub true, sub false */ }
#[test] fn flags_use_primary_subtag() { /* locale "fr-FR", audio ["fr-FR"], subs ["fr-FR"] -> beides true */ }
#[test] fn title_heuristic_only_for_german() { /* "X (German Dub)" ohne Locales: de-DE -> dub; en-US -> kein dub */ }
#[test] fn title_heuristic_sub_markers() { /* "Y (OmU)" -> sub bei de-DE */ }
#[test] fn pick_image_prefers_width_range_else_last() {}
#[test] fn pick_image_empty() { /* [] -> "" */ }
#[test] fn to_catalog_item_trims_categories() {}
```
Bei eigener Implementierung zusätzlich in `core/tests/crunchyroll.rs`: `reauthenticates_once_on_401`, `reads_country_from_token`, `maps_browse_page`.

- [ ] **Step 2: Tests ausführen**

Run: `cargo test -p crunchymate-core catalog`
Expected: FAIL

- [ ] **Step 3: Implementieren** (`CrunchyrollSource` füllt `RawSeries` aus den Bibliothekstypen bzw. der JSON-Antwort und ruft `to_catalog_item`)

- [ ] **Step 4: Tests ausführen**

Run: `cargo test -p crunchymate-core`
Expected: PASS

- [ ] **Step 5: Manuelle Probe gegen die echte API (nicht in CI)**

Run: `cargo test -p crunchymate-core --test crunchyroll_live -- --ignored` (ein `#[ignore]`-Test, der eine Seite mit 5 Einträgen abruft und `total > 0` sowie ein gesetztes Land prüft)
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add core
git commit -m "feat(core): Crunchyroll-Katalogquelle hinter CatalogSource"
```

### Task 9: Sync-Abläufe

**Files:**
- Create: `core/src/sync/mod.rs`, `core/src/sync/config.rs`, `core/src/sync/progress.rs`, `core/src/sync/catalog_sync.rs`, `core/src/sync/enrichment.rs`, `core/src/sync/run_guard.rs`
- Test: `core/tests/sync.rs` (Fakes für `CatalogSource`/`MetadataSource`/`SyncObserver`)

**Interfaces:**
- Consumes: Tasks 4, 6, 7, 8.
- Produces:
  ```rust
  pub struct SyncConfig { pub catalog_page_size: u32, pub catalog_page_delay: Duration,
      pub metadata_batch_size: usize, pub metadata_batch_delay: Duration }
  // Default: 50, 100 ms, 10, 2100 ms
  #[uniffi::export(with_foreign)]
  pub trait SyncObserver: Send + Sync { fn on_progress(&self, progress: SyncProgress); }
  pub async fn run_catalog_sync(db: &Database, source: &dyn CatalogSource, locale: &str,
      config: &SyncConfig, observer: &dyn SyncObserver) -> CoreResult<SyncSummary>
  pub async fn run_enrichment(db: &Database, source: &dyn MetadataSource,
      config: &SyncConfig, observer: &dyn SyncObserver) -> CoreResult<SyncSummary>
  ```
  Ablauf Katalog: `mark_running`, Land über `session_country` speichern, Seiten ab 0 in `catalog_page_size` abrufen, jede Seite in einer eigenen Transaktion upserten, nach jeder Seite `update_progress` und `on_progress(done, total)`, Pause `catalog_page_delay`; Ende, wenn `start >= total` oder eine Seite leer ist; dann `mark_finished(Completed)`. Ablauf Anreicherung: Liste `unenriched`, `total` = deren Anzahl, Batches mit `metadata_batch_size`, Treffer per `apply_metadata` (eine Transaktion je Batch), Batch-Fehler zählen in `failed_batches` und brechen nicht ab, außer `RateLimited` (wird nach Ablauf der Retries an den Aufrufer gegeben); Pause zwischen Batches. Fehler: `mark_finished(Failed, Some(code))`, Fehler zurückgeben.
  `RunGuard` (in `run_guard.rs`): wird nach `mark_running` erzeugt; `complete(state, error)` schreibt den Abschluss; wird er ohne `complete` verworfen (Future abgebrochen), schreibt `Drop` `Cancelled`.

- [ ] **Step 1: Tests schreiben** (`SyncConfig` mit Pausen 0)

```rust
#[tokio::test] async fn catalog_sync_upserts_all_pages_and_reports_progress() { /* Fake: total 120, Seiten 50/50/20 -> stats.total 120; Observer: (50,120),(100,120),(120,120); Status Completed */ }
#[tokio::test] async fn catalog_sync_stores_country() { /* Fake-Land "DE" -> status.catalog_country == Some("DE") */ }
#[tokio::test] async fn catalog_sync_stops_on_empty_page() {}
#[tokio::test] async fn catalog_sync_failure_marks_failed_and_keeps_saved_pages() { /* Seite 2 -> Network-Fehler: 50 Einträge gespeichert, Failed, last_error Network */ }
#[tokio::test] async fn dropping_sync_future_marks_cancelled_and_keeps_pages() { /* Fake blockiert auf Seite 2 (Notify); Future nach Seite 1 droppen -> Cancelled, 50 gespeichert */ }
#[tokio::test] async fn enrichment_processes_only_unenriched() { /* 3 Einträge, 1 schon angereichert -> Fake erhält genau 2 Titel; total 2 */ }
#[tokio::test] async fn enrichment_tolerates_failed_batch() { /* Batch 1 Fehler Network, Batch 2 ok -> Ok(SyncSummary{failed_batches: 1}) */ }
#[tokio::test] async fn enrichment_aborts_on_rate_limit() { /* RateLimited -> Err, Failed, last_error RateLimited */ }
#[tokio::test] async fn enrichment_uses_clean_batches_of_configured_size() { /* 25 Einträge, Batch 10 -> Aufrufe mit 10, 10, 5 */ }
```

- [ ] **Step 2: Tests ausführen**

Run: `cargo test -p crunchymate-core --test sync`
Expected: FAIL

- [ ] **Step 3: Implementieren**

- [ ] **Step 4: Tests ausführen**

Run: `cargo test -p crunchymate-core`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add core
git commit -m "feat(core): Katalog-Sync und AniList-Anreicherung mit Fortschritt und Abbruch"
```

### Task 10: UniFFI-Fassade `Core`

**Files:**
- Modify: `core/src/lib.rs`
- Create: `core/src/core_api.rs`
- Test: `core/tests/core_api.rs`

**Interfaces:**
- Consumes: alle vorherigen Kern-Tasks.
- Produces (Kotlin sieht `CoreInterface`, `CoreException`, die Records/Enums aus Task 3, `SyncObserver`):
  ```rust
  #[derive(uniffi::Object)]
  pub struct Core { db: Arc<Database>, catalog: Arc<dyn CatalogSource>, metadata: Arc<dyn MetadataSource>, config: SyncConfig }
  #[uniffi::export(async_runtime = "tokio")]
  impl Core {
      #[uniffi::constructor] pub fn new(db_path: String) -> CoreResult<Arc<Self>>  // öffnet DB, reset_orphaned_runs, echte Quellen (CrunchyrollSource lazy beim ersten Sync)
      pub fn query_anime(&self, filter: AnimeFilter, offset: u32, limit: u32) -> CoreResult<AnimePage>
      pub fn get_anime(&self, id: String) -> CoreResult<Option<AnimeDetail>>
      pub fn set_watched(&self, id: String, watched: bool) -> CoreResult<()>
      pub fn set_meh(&self, id: String, meh: bool) -> CoreResult<()>
      pub fn list_genres(&self) -> CoreResult<Vec<NameCount>>
      pub fn list_tags(&self) -> CoreResult<Vec<NameCount>>
      pub fn stats(&self) -> CoreResult<Stats>
      pub fn settings(&self) -> CoreResult<Settings>
      pub fn save_settings(&self, settings: Settings) -> CoreResult<()>
      pub fn supported_content_locales(&self) -> Vec<String>
      pub fn sync_status(&self, kind: SyncKind) -> CoreResult<SyncStatus>
      pub async fn sync_catalog(&self, observer: Arc<dyn SyncObserver>) -> CoreResult<SyncSummary> // Locale aus settings
      pub async fn enrich_metadata(&self, observer: Arc<dyn SyncObserver>) -> CoreResult<SyncSummary>
  }
  impl Core { pub fn with_sources(db: Database, catalog: Arc<dyn CatalogSource>, metadata: Arc<dyn MetadataSource>, config: SyncConfig) -> Arc<Self> } // nicht exportiert, für Tests
  ```
  `now` für Zeitstempel aus `SystemTime::now()` als Unix-Sekunden.

- [ ] **Step 1: Tests schreiben**

```rust
#[tokio::test] async fn new_resets_orphaned_running_state() { /* Datei-DB mit Running anlegen, Core::new -> sync_status == Cancelled */ }
#[tokio::test] async fn facade_roundtrip_with_fake_sources() { /* with_sources, sync_catalog, query_anime total > 0, set_watched -> stats.watched 1 */ }
#[tokio::test] async fn sync_catalog_uses_content_locale_from_settings() { /* save_settings(fr-FR) -> Fake erhält "fr-FR" */ }
#[test] fn supported_locales_start_with_german() {}
```

- [ ] **Step 2: Tests ausführen**

Run: `cargo test -p crunchymate-core --test core_api`
Expected: FAIL

- [ ] **Step 3: Implementieren**

- [ ] **Step 4: Tests, Lints und Bindings prüfen**

Run: `cargo fmt --check && cargo clippy --all-targets -- -D warnings && cargo test && cargo ndk -t arm64-v8a build --release -p crunchymate-core && cargo run -p crunchymate-core --bin uniffi-bindgen -- generate --library target/aarch64-linux-android/release/libcrunchymate_core.so --language kotlin --config core/uniffi.toml --out-dir target/uniffi-check`
Expected: alles grün; die generierte Datei liegt unter `de/crunchymate/core/ffi/` und enthält `interface CoreInterface`, `sealed class CoreException`, `interface SyncObserver` und `suspend fun syncCatalog`.

- [ ] **Step 5: Commit**

```bash
git add core
git commit -m "feat(core): UniFFI-Fassade Core"
```

## Phase 2: Android-App

### Task 11: Gradle-Projekt im Root, Rust-Integration, alte App entfernen

**Files:**
- Delete: `android/` (vollständig, inklusive `release.keystore`)
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties` (ohne Termux-Override; `crunchymate.baseVersion=2.0.0`), `gradle/libs.versions.toml`, `gradle/wrapper/*`, `gradlew`, `gradlew.bat`
- Create: `app/build.gradle.kts`, `app/proguard-rules.pro`, `app/src/main/AndroidManifest.xml`, `app/src/main/java/de/crunchymate/MainActivity.kt`, `app/src/main/java/de/crunchymate/CrunchyMateApplication.kt`, `app/src/main/java/de/crunchymate/AppContainer.kt`, `app/src/main/res/values/strings.xml`, `app/src/main/res/xml/locales_config.xml`, `app/src/main/res/resources.properties` (`unqualifiedResLocale=de`)

**Interfaces:**
- Produces (Gradle-Tasks in `app/build.gradle.kts`): `cargoBuildArm64` (Exec: `cargo ndk -t arm64-v8a -o app/build/rustJniLibs build --release -p crunchymate-core` im Root), `generateUniffiBindings` (Exec, siehe Task 10 Step 4, `--out-dir app/build/generated/uniffi`), beide vor `preBuild`; `sourceSets.main.java.srcDir("build/generated/uniffi")`, `jniLibs.srcDir("build/rustJniLibs")`; `ndk.abiFilters += "arm64-v8a"`; Abhängigkeit `jna` als `@aar`.
- Produces: `versionCode` aus Property `crunchymate.versionCode` (Standard 1), `versionName` aus `crunchymate.versionName` (Standard `<baseVersion>-dev`).
- Produces: `class AppContainer(context: Context) { val core: CoreInterface by lazy { Core(context.getDatabasePath(DB_NAME).path) } }` mit `DB_NAME = "crunchymate.db"`; `CrunchyMateApplication.container: AppContainer`.
- Produces: Lint-Block `abortOnError = true`, `error += listOf("HardcodedText", "MissingTranslation", "UnusedResources")`; ktlint-Plugin.
- `MainActivity` (vorläufig): `enableEdgeToEdge()`, `setContent { Text(stringResource(R.string.app_name)) }` plus Laden von `container.core.stats()` im Hintergrund mit Log-Ausgabe der Gesamtzahl (wird in Task 14 ersetzt).

- [ ] **Step 1: Projekt anlegen, `android/` löschen**

- [ ] **Step 2: Build prüfen**

Run: `./gradlew lint ktlintCheck assembleDebug`
Expected: BUILD SUCCESSFUL; `unzip -l app/build/outputs/apk/debug/app-debug.apk | grep lib/arm64-v8a/libcrunchymate_core.so` findet die Bibliothek.

- [ ] **Step 3: Auf dem Gerät prüfen**

Run: `adb.exe install -r "$(wslpath -w app/build/outputs/apk/debug/app-debug.apk)" && adb.exe shell am start -n de.crunchymate/.MainActivity && sleep 3 && adb.exe logcat -d -s CrunchyMate`
Expected: App startet ohne Absturz; Log enthält die Gesamtzahl 0 aus `stats()`.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "build: Android-Projekt im Root mit Rust-Kern über UniFFI, alte App entfernt"
```

### Task 12: CoreGateway, Einstellungsspeicher und Fehlertexte

**Files:**
- Create: `app/src/main/java/de/crunchymate/core/CoreGateway.kt`, `app/src/main/java/de/crunchymate/core/SettingsStore.kt`, `app/src/main/java/de/crunchymate/core/ErrorMessages.kt`, `app/src/main/java/de/crunchymate/core/AnimePagingSource.kt`
- Modify: `AppContainer.kt`
- Test: `app/src/test/java/de/crunchymate/core/FakeCore.kt`, `CoreGatewayTest.kt`, `SettingsStoreTest.kt`, `AnimePagingSourceTest.kt`, `ErrorMessagesTest.kt`

**Interfaces:**
- Consumes: generierte `CoreInterface`, Records, `CoreException`, `ErrorCode` (Task 10).
- Produces:
  ```kotlin
  class CoreGateway(private val core: CoreInterface, private val io: CoroutineDispatcher) {
      val changes: SharedFlow<Unit>
      suspend fun queryAnime(filter: AnimeFilter, offset: Int, limit: Int): AnimePage
      suspend fun getAnime(id: String): AnimeDetail?
      suspend fun setWatched(id: String, watched: Boolean)   // emittiert changes
      suspend fun setMeh(id: String, meh: Boolean)           // emittiert changes
      suspend fun genres(): List<NameCount>
      suspend fun tags(): List<NameCount>
      suspend fun stats(): Stats
      suspend fun syncStatus(kind: SyncKind): SyncStatus
      fun supportedContentLocales(): List<String>
      fun notifyChanged()
      val core: CoreInterface   // für SyncWorker
  }
  class SettingsStore(private val gateway: CoreGateway, private val scope: CoroutineScope, private val io: CoroutineDispatcher) {
      val settings: StateFlow<Settings>          // initial Settings-Default, nach load() der gespeicherte Stand
      suspend fun load()
      fun update(transform: (Settings) -> Settings)  // sofort im Flow, Speicherung verzögert um SAVE_DEBOUNCE = 500 ms
  }
  class AnimePagingSource(private val gateway: CoreGateway, private val filter: AnimeFilter) : PagingSource<Int, AnimeSummary>
  const val PAGE_SIZE = 50
  @StringRes fun ErrorCode.messageRes(): Int
  @StringRes fun Throwable.messageRes(): Int   // CoreException -> Code, sonst R.string.error_unknown
  ```
  `SettingsStore` bekommt Lese-/Schreibzugriff auf `core.settings()`/`core.saveSettings()` über den Gateway (zwei zusätzliche Methoden `loadSettings()`/`saveSettings(settings)` im Gateway). `AppContainer` stellt `gateway`, `settingsStore` und `applicationScope` bereit.

Falls die JVM-Tests beim Laden generierter Typen die native Bibliothek anfordern: die Kern-Bibliothek zusätzlich für den Host bauen (`cargo build -p crunchymate-core`) und in `testOptions` `systemProperty("jna.library.path", "<root>/target/debug")` setzen.

- [ ] **Step 1: Tests schreiben** (`FakeCore` implementiert `CoreInterface` mit In-Memory-Listen)

```kotlin
@Test fun setWatched_emitsChange()
@Test fun queryAnime_runsOnIoDispatcher()
@Test fun settingsStore_updateIsVisibleImmediately_andSavedOnceAfterDebounce() // zwei Updates in 100 ms -> ein saveSettings-Aufruf mit dem letzten Stand nach 500 ms
@Test fun pagingSource_returnsNextKeyUntilTotalReached() // total 120, PAGE_SIZE 50 -> Keys 0, 50, 100, dann null
@Test fun pagingSource_errorBecomesLoadResultError()
@Test fun errorMessages_mapEveryErrorCode() // jeder ErrorCode hat eine eigene Ressource
```

- [ ] **Step 2: Tests ausführen**

Run: `./gradlew testDebugUnitTest --tests 'de.crunchymate.core.*'`
Expected: FAIL

- [ ] **Step 3: Implementieren** (Fehlertexte in `strings.xml`: `error_network`, `error_rate_limited`, `error_auth`, `error_parse`, `error_database`, `error_cancelled`, `error_unknown`)

- [ ] **Step 4: Tests ausführen**

Run: `./gradlew testDebugUnitTest --tests 'de.crunchymate.core.*'`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app
git commit -m "feat(app): CoreGateway, Einstellungsspeicher, Paging und Fehlertexte"
```

### Task 13: Theme, Icon-Platzhalter und Komponenten

**Files:**
- Create: `app/src/main/java/de/crunchymate/ui/theme/{Color.kt,Theme.kt,Type.kt}`, `app/src/main/java/de/crunchymate/ui/components/{AnimeCard.kt,TagChip.kt,FilterDropdownChip.kt,SyncCard.kt,EmptyState.kt}`, `app/src/main/java/de/crunchymate/ui/tags/TagCycle.kt`
- Create: Vector-Platzhalter `app/src/main/res/drawable/ic_*.xml` (Namen siehe unten), `app/src/main/res/mipmap-anydpi/ic_launcher.xml` (adaptive mit `monochrome`)
- Modify: `docs/icons.md` (existiert bereits, vom Auftraggeber genutzt; nur ergänzen, nichts entfernen. Die Spalte „Emoji-Entsprechung“ ist eine ausdrücklich erlaubte Ausnahme vom Emoji-Verbot und bleibt stehen.)
- Test: `app/src/test/java/de/crunchymate/ui/tags/TagCycleTest.kt`, `app/src/test/java/de/crunchymate/ui/components/ComponentsTest.kt` (Robolectric + Compose)

**Interfaces:**
- Produces:
  ```kotlin
  fun nextTagState(current: TagState?): TagState?   // null -> Prefer -> Must -> Exclude -> null
  fun List<TagSelection>.cycle(name: String): List<TagSelection>
  fun List<TagSelection>.stateOf(name: String): TagState?
  @Composable fun CrunchyMateTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit)
  @Composable fun AnimeCard(anime: AnimeSummary, onOpen: () -> Unit, onToggleWatched: () -> Unit, onToggleMeh: () -> Unit, modifier: Modifier = Modifier)
  @Composable fun TagChip(name: String, count: Long?, state: TagState?, onClick: () -> Unit, modifier: Modifier = Modifier)
  @Composable fun <T> FilterDropdownChip(label: String, options: List<T>, selected: T, optionLabel: @Composable (T) -> String, onSelect: (T) -> Unit)
  @Composable fun SyncCard(title: String, state: SyncUiState, onStart: () -> Unit, onCancel: () -> Unit)
  @Composable fun EmptyState(@DrawableRes icon: Int, title: String, subtitle: String)
  ```
  `SyncUiState` wird in Task 15 definiert; für diesen Task wird es bereits in `app/src/main/java/de/crunchymate/sync/SyncUiState.kt` angelegt: `data class SyncUiState(val running: Boolean, val done: Long, val total: Long, val lastFinishedAt: Long?, val lastError: ErrorCode?)` mit `companion object { val Idle }`.
  Farben: Primär Crunchyroll-Orange `#F47521`; Tag-Zustände Boost `#3DB4F2`, Muss `#4CAF50`, Ausgeschlossen `#FF9800` (Werte aus `git show 1d5ca54:web/static/style.css` übernehmen, falls dort abweichend). Dunkles und helles Schema, keine Dynamic Colors.
  Bereits vorhandene Dateien in `app/src/main/res/drawable/`, die per `/check-svg` aus gelieferten SVGs entstanden sind (Quelle in `design/icons/`), werden nicht durch Platzhalter überschrieben.
  Icon-Dateien (Platzhalter, 24 dp, einfarbig): `ic_nav_search`, `ic_nav_watched`, `ic_nav_meh`, `ic_nav_settings`, `ic_watched_on`, `ic_watched_off`, `ic_meh_on`, `ic_meh_off`, `ic_badge_score`, `ic_badge_dub`, `ic_badge_sub`, `ic_badge_boost`, `ic_tag_prefer`, `ic_tag_must`, `ic_tag_exclude`, `ic_clear`, `ic_tags_reset`, `ic_sync_start`, `ic_sync_cancel`, `ic_open_external`, `ic_dropdown`, `ic_back`, `ic_state_empty`, `ic_state_error`, `ic_stat_total`, `ic_stat_watched`, `ic_stat_meh`, `ic_stat_enriched`, `ic_stat_tags`, `ic_notification_sync`, `ic_launcher_foreground`, `ic_launcher_background`, `ic_launcher_monochrome`. `docs/icons.md` enthält bereits jede Datei mit Bedeutung, Anforderungen und Emoji-Entsprechung; liegen in `design/icons/` gelieferte SVGs vor, werden sie umgewandelt und eingetragen, sonst bleibt die Datei unverändert.
  Jede Komponente setzt `contentDescription` aus String-Ressourcen; `TagChip` zeigt Zustand per Farbe und Icon und setzt `stateDescription`.

- [ ] **Step 1: Tests schreiben**

```kotlin
@Test fun nextTagState_cyclesThroughAllStates()
@Test fun cycle_addsThenAdvancesThenRemoves()
@Test fun tagChip_exposesStateDescriptionPerState()   // Robolectric: semantics stateDescription für null/Prefer/Must/Exclude unterschiedlich
@Test fun animeCard_showsBoostBadgeOnlyWhenMatches()  // prefer_matches 0 -> kein Badge; 2 -> Text aus plurals
@Test fun animeCard_togglesInvokeCallbacks()
@Test fun syncCard_showsCancelWhileRunningAndStartOtherwise()
@Test fun syncCard_showsLastErrorText()
```

- [ ] **Step 2: Tests ausführen**

Run: `./gradlew testDebugUnitTest --tests 'de.crunchymate.ui.*'`
Expected: FAIL

- [ ] **Step 3: Implementieren**

- [ ] **Step 4: Tests und Lint**

Run: `./gradlew testDebugUnitTest lint ktlintCheck`
Expected: PASS, keine Lint-Fehler (Platzhalter-Icons werden alle referenziert oder sind in `docs/icons.md` aufgeführt und über `tools:keep` in `res/raw/keep.xml` geschützt).

- [ ] **Step 5: Commit**

```bash
git add app docs/icons.md
git commit -m "feat(app): Theme, Komponenten und Icon-Platzhalter"
```

### Task 14: Suche, Gesehen, Meh, Detail und Navigation

**Files:**
- Create: `app/src/main/java/de/crunchymate/ui/search/{GridMode.kt,SearchUiState.kt,SearchViewModel.kt,AnimeGridScreen.kt,SearchControls.kt}`, `app/src/main/java/de/crunchymate/ui/detail/{DetailViewModel.kt,DetailScreen.kt}`, `app/src/main/java/de/crunchymate/ui/CrunchyMateApp.kt`, `app/src/main/java/de/crunchymate/ui/Routes.kt`
- Modify: `MainActivity.kt` (`setContent { CrunchyMateTheme { CrunchyMateApp(container) } }`)
- Test: `app/src/test/java/de/crunchymate/ui/search/{FilterMappingTest.kt,SearchViewModelTest.kt}`, `app/src/test/java/de/crunchymate/ui/detail/DetailViewModelTest.kt`

**Interfaces:**
- Consumes: Tasks 12, 13.
- Produces:
  ```kotlin
  enum class GridMode { Search, Watched, Meh }
  fun SearchState.toFilter(mode: GridMode): AnimeFilter
  data class SearchUiState(val search: SearchState, val genres: List<NameCount>, val tags: List<NameCount>,
      val tagQuery: String, val visibleTags: List<NameCount>, val activeTags: List<TagSelection>, val total: Long?)
  class SearchViewModel(mode: GridMode, gateway: CoreGateway, settingsStore: SettingsStore) : ViewModel() {
      val uiState: StateFlow<SearchUiState>
      val items: Flow<PagingData<AnimeSummary>>
      fun onQueryChange(q: String); fun onGenreSelected(genre: String?); fun onLanguageSelected(l: LanguageFilter)
      fun onWatchedSelected(w: WatchedFilter); fun onSortSelected(s: SortOrder)
      fun onTagTapped(name: String); fun onTagQueryChange(q: String); fun onClearTags()
      fun onToggleWatched(anime: AnimeSummary); fun onToggleMeh(anime: AnimeSummary)
  }
  data class DetailUiState(val anime: AnimeDetail?, val loading: Boolean, @StringRes val error: Int?, val tagStates: List<TagSelection>)
  class DetailViewModel(id: String, gateway: CoreGateway, settingsStore: SettingsStore) : ViewModel() {
      val uiState: StateFlow<DetailUiState>; fun onToggleWatched(); fun onToggleMeh()
  }
  @Serializable data object SearchRoute; @Serializable data object WatchedRoute; @Serializable data object MehRoute
  @Serializable data object SettingsRoute; @Serializable data class DetailRoute(val id: String)
  @Composable fun CrunchyMateApp(container: AppContainer)
  ```
  `toFilter`: `Search` übernimmt alles und setzt `MehFilter.Hide`, Tags nach Zustand auf `prefer_tags`/`must_tags`/`exclude_tags`; `Watched`: Suchtext leer, Genre `null`, Sprache `All`, `WatchedFilter.Watched`, `MehFilter.Include`, Sortierung aus dem Suchzustand, keine Tags; `Meh`: wie `Watched`, aber `WatchedFilter.All`, `MehFilter.Only`. Der Paging-Flow wird bei jeder Filteränderung und bei jedem `gateway.changes` neu erzeugt bzw. invalidiert (`InvalidatingPagingSourceFactory`). `activeTags` enthält alle Tags aus dem Suchzustand, auch wenn sie in `tags` nicht mehr vorkommen. `visibleTags` = `tags` gefiltert nach `tagQuery` (Groß-/Kleinschreibung egal). `total` aus einer separaten `queryAnime(filter, 0, 1)`-Abfrage bei jeder Änderung. Gesehen-/Meh-Ansicht: gleicher Screen ohne Steuerelemente, mit Banner-Titel und eigenem Leerzustand (Texte aus der bisherigen UI). Navigation: `NavigationBar` mit vier Zielen und Badges (Anzahl aus `stats()` bei jedem `changes`, Punkt bei laufendem Sync aus `SyncScheduler` – bis Task 15 immer aus); Detail per `navController.navigate(DetailRoute(id))`. Externer AniList-Link über `LocalUriHandler`. Beschreibung über `AnnotatedString.fromHtml`.

- [ ] **Step 1: Tests schreiben**

```kotlin
@Test fun toFilter_search_mapsTagsAndHidesMeh()
@Test fun toFilter_watched_ignoresSearchControlsAndIncludesMeh()
@Test fun toFilter_meh_onlyMeh()
@Test fun tagTap_cyclesAndPersistsViaSettingsStore()
@Test fun staleTagInSearchState_staysInActiveTags()   // Tag "Alt" nicht in tags -> activeTags enthält "Alt"; onTagTapped("Alt") dreimal entfernt ihn
@Test fun tagQuery_filtersVisibleTagsCaseInsensitive()
@Test fun toggleWatched_callsGatewayAndInvalidates()
@Test fun watchedSelection_updatesHideWatchedCoupling() // onWatchedSelected(Unwatched) -> settings.search.watched == Unwatched
@Test fun detail_loadsAnimeAndExposesTagStates()
@Test fun detail_unknownId_showsError()
```

- [ ] **Step 2: Tests ausführen**

Run: `./gradlew testDebugUnitTest --tests 'de.crunchymate.ui.search.*' --tests 'de.crunchymate.ui.detail.*'`
Expected: FAIL

- [ ] **Step 3: Implementieren**

- [ ] **Step 4: Tests und Build**

Run: `./gradlew testDebugUnitTest lint ktlintCheck assembleDebug`
Expected: PASS

- [ ] **Step 5: Auf dem Gerät prüfen**

APK installieren und starten (Befehl wie Task 11 Step 3).
Expected: Navigation zwischen den vier Tabs funktioniert; Suche zeigt den Leerzustand mit Hinweis auf den Sync; Zurück-Geste aus der (noch leeren) App beendet sie ohne Absturz.

- [ ] **Step 6: Commit**

```bash
git add app
git commit -m "feat(app): Suche, Gesehen, Meh, Detail und Navigation"
```

### Task 15: Sync im Hintergrund und Einstellungen

**Files:**
- Create: `app/src/main/java/de/crunchymate/sync/{SyncWorker.kt,SyncScheduler.kt,SyncNotifications.kt}`, `app/src/main/java/de/crunchymate/ui/settings/{SettingsUiState.kt,SettingsViewModel.kt,SettingsScreen.kt}`
- Modify: `AndroidManifest.xml` (`POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `INTERNET`; `androidx.work.impl.foreground.SystemForegroundService` mit `foregroundServiceType="dataSync"` und `tools:node="merge"`), `AppContainer.kt`, `CrunchyMateApp.kt` (Sync-Badge)
- Test: `app/src/test/java/de/crunchymate/sync/{SyncSchedulerTest.kt,SyncWorkerTest.kt}`, `app/src/test/java/de/crunchymate/ui/settings/SettingsViewModelTest.kt`

**Interfaces:**
- Consumes: Tasks 12–14; `SyncObserver`, `SyncKind`, `SyncStatus`.
- Produces:
  ```kotlin
  class SyncScheduler(private val workManager: WorkManager, private val gateway: CoreGateway) {
      fun start(kind: SyncKind)    // enqueueUniqueWork("sync-${kind.name}", ExistingWorkPolicy.KEEP, …) mit setExpedited(RUN_AS_NON_EXPEDITED_WORK_REQUEST)
      fun cancel(kind: SyncKind)
      fun observe(kind: SyncKind): Flow<SyncUiState>  // WorkInfo-Progress + gateway.syncStatus
      val anyRunning: Flow<Boolean>
  }
  class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
      companion object { const val KEY_KIND = "kind"; const val KEY_DONE = "done"; const val KEY_TOTAL = "total" }
  }
  object SyncNotifications { const val CHANNEL_ID = "sync"; fun ensureChannel(context: Context); fun build(context: Context, kind: SyncKind, done: Long, total: Long, cancelIntent: PendingIntent): Notification }
  data class SettingsUiState(val catalog: SyncUiState, val metadata: SyncUiState, val stats: Stats?,
      val catalogCountry: String?, val contentLocale: String, val availableLocales: List<String>, val hideWatched: Boolean)
  class SettingsViewModel(gateway: CoreGateway, settingsStore: SettingsStore, scheduler: SyncScheduler) : ViewModel() {
      val uiState: StateFlow<SettingsUiState>
      fun onStartSync(kind: SyncKind); fun onCancelSync(kind: SyncKind)
      fun onHideWatchedChange(hide: Boolean)        // setzt search.watched auf Unwatched bzw. All
      fun onContentLocaleChange(locale: String)
  }
  ```
  Worker: `setForeground(ForegroundInfo(id, notification, FOREGROUND_SERVICE_TYPE_DATA_SYNC))`; ruft `gateway.core.syncCatalog(observer)` bzw. `enrichMetadata(observer)`; der Observer ruft `setProgress`, aktualisiert die Benachrichtigung und `gateway.notifyChanged()` höchstens alle `PROGRESS_THROTTLE = 1 s`; Ergebnis `Result.success()`, bei `CoreException` `Result.failure(workDataOf("error" to code.name))`; Abbruch der Coroutine bricht den Rust-Sync ab. Abbrechen-Aktion der Benachrichtigung über `WorkManager.createCancelPendingIntent(id)`. `hideWatched` = `settings.search.watched == WatchedFilter.UNWATCHED`. Locale-Anzeige über `Locale.forLanguageTag(tag).getDisplayName(Locale.getDefault())`; Hinweis, dass die Inhaltssprache beim nächsten Katalog-Sync wirkt. Benachrichtigungsberechtigung im `SettingsScreen` vor dem ersten Start per `rememberLauncherForActivityResult(RequestPermission())`; Start erfolgt unabhängig vom Ergebnis. Link zur App-Spracheinstellung: `Settings.ACTION_APP_LOCALE_SETTINGS` mit `package:`-URI.

- [ ] **Step 1: Tests schreiben** (WorkManager über `WorkManagerTestInitHelper`, Worker über `TestListenableWorkerBuilder`, `FakeCore` aus Task 12)

```kotlin
@Test fun start_twice_enqueuesSingleWork()           // zweimal start(Catalog) -> genau eine WorkInfo RUNNING/ENQUEUED
@Test fun catalogAndMetadata_canRunInParallel()
@Test fun observe_mapsProgressAndLastError()
@Test fun worker_success_returnsSuccess()
@Test fun worker_coreException_returnsFailureWithCode() // RateLimited -> outputData error == "RATE_LIMITED"
@Test fun settings_hideWatched_togglesSearchWatched()
@Test fun settings_contentLocale_savedViaStore()
@Test fun settings_showsCatalogCountry()
```

- [ ] **Step 2: Tests ausführen**

Run: `./gradlew testDebugUnitTest --tests 'de.crunchymate.sync.*' --tests 'de.crunchymate.ui.settings.*'`
Expected: FAIL

- [ ] **Step 3: Implementieren**

- [ ] **Step 4: Tests und Build**

Run: `./gradlew testDebugUnitTest lint ktlintCheck assembleDebug`
Expected: PASS

- [ ] **Step 5: Auf dem Gerät prüfen**

APK installieren, in den Einstellungen den Katalog-Sync starten, Benachrichtigung zulassen; nach Abschluss AniList-Anreicherung starten und nach einigen Batches über die Benachrichtigung abbrechen.
Expected: Fortschritt in Karte und Benachrichtigung, erkanntes Land „DE“, Suche zeigt Einträge mit Postern, Tag-Filter und Detail funktionieren; Abbruch setzt den Status auf „abgebrochen“, bereits angereicherte Einträge bleiben erhalten; App beenden während eines Laufs und neu öffnen zeigt keinen hängenden „läuft“-Zustand.

- [ ] **Step 6: Commit**

```bash
git add app
git commit -m "feat(app): Sync über WorkManager und Einstellungsseite"
```

### Task 16: Lokale Smoke-Tests auf dem Gerät

**Files:**
- Create: `core/examples/make_fixture_db.rs`, `app/src/androidTest/java/de/crunchymate/SmokeTest.kt`, `scripts/run-device-tests.sh`
- Modify: `app/build.gradle.kts` (Task `generateFixtureDb`: `cargo run -p crunchymate-core --example make_fixture_db -- app/build/fixture/fixture.db`, Ausgabe als `androidTest`-Asset; `testInstrumentationRunner`), `AppContainer.kt` nur falls nötig, damit der Kern erst beim ersten Zugriff geöffnet wird (bereits `lazy` seit Task 11)

**Interfaces:**
- Consumes: `Database`, `upsert_catalog_page`, `apply_metadata` (Task 4) im Beispielprogramm; UI aus Tasks 14–15.
- Produces: `scripts/run-device-tests.sh` baut `assembleDebug assembleDebugAndroidTest`, installiert beide APKs mit `adb.exe install -r "$(wslpath -w …)"` und führt `adb.exe shell am instrument -w de.crunchymate.test/androidx.test.runner.AndroidJUnitRunner` aus; Exit-Code 1, wenn die Ausgabe nicht `OK (` enthält.

Fixture: 5 Einträge mit Tags „Action“ und „Isekai“, einer angereichert. `SmokeTest` kopiert die Fixture in `@BeforeClass` an `context.getDatabasePath("crunchymate.db")`, bevor die Activity per `createAndroidComposeRule<MainActivity>()` startet.

- [ ] **Step 1: Tests schreiben**

```kotlin
@Test fun appStarts_andShowsFixtureItems()
@Test fun bottomNavigation_switchesAllTabs()
@Test fun tagChip_cyclesThroughStates()        // stateDescription wechselt dreimal und dann zurück
@Test fun openDetail_andNavigateBack()
```

- [ ] **Step 2: Tests auf dem Gerät ausführen**

Run: `bash scripts/run-device-tests.sh`
Expected: zunächst FAIL, solange Fixture/Tests fehlen; nach Step 3 `OK (4 tests)`.

- [ ] **Step 3: Fixture-Programm, Gradle-Task und Tests implementieren**

- [ ] **Step 4: Erneut ausführen**

Run: `bash scripts/run-device-tests.sh`
Expected: `OK (4 tests)`

- [ ] **Step 5: Commit**

```bash
git add core/examples app scripts/run-device-tests.sh
git commit -m "test: Smoke-Tests auf dem Gerät mit Fixture-Datenbank"
```

### Task 15c: Bilder offline (Ergänzung, Spec 5.6)

**Files:**
- Create: `core/src/images/mod.rs`, `core/src/images/encode.rs`, `core/src/images/store.rs`, `core/src/images/fetch.rs`, `core/src/sync/images.rs`
- Modify: `core/src/db/schema.rs` (Migration auf Version 2), `core/src/db/anime.rs`, `core/src/query.rs`, `core/src/model.rs`, `core/src/core_api.rs`, `core/src/sync/{catalog_sync,enrichment,config,progress}.rs`, `core/Cargo.toml`
- Modify: `app/src/main/java/de/crunchymate/AppContainer.kt`, `ui/components/AnimeCard.kt`, `ui/components/SyncCard.kt`, `ui/detail/DetailScreen.kt`, `ui/settings/SettingsScreen.kt`, `sync/SyncWorker.kt`, `sync/SyncScheduler.kt`, `sync/SyncNotifications.kt`, `app/build.gradle.kts`, `gradle/libs.versions.toml`, `strings.xml`
- Test: `core/tests/images.rs`, Unit-Tests in `core/src/images/*.rs`, angepasste Kotlin-Tests

**Interfaces:**
- Schema v2: Tabelle `images(hash TEXT PRIMARY KEY, ext TEXT NOT NULL, bytes INTEGER NOT NULL)`; Tabelle `image_refs(anime_id TEXT NOT NULL REFERENCES anime(id) ON DELETE CASCADE, slot TEXT NOT NULL, source_url TEXT NOT NULL, hash TEXT REFERENCES images(hash), PRIMARY KEY(anime_id, slot))`; `slot` stabil als TEXT: `poster_tall`, `poster_wide`, `banner`.
- `pub enum ImageSlot { PosterTall, PosterWide, Banner }` (intern) mit `as_str()`.
- `pub fn encode_for_storage(bytes: &[u8]) -> CoreResult<StoredImage>` mit `StoredImage { bytes: Vec<u8>, ext: &'static str }`: WebP-Quelle unverändert (`webp`); JPEG/PNG -> WebP mit `WEBP_QUALITY` (Konstante, 92); ist das Ergebnis nicht kleiner, Original mit `jpg`/`png`; unbekanntes Format -> `CoreError::Parse`.
- `pub struct ImageStore { dir: PathBuf }` mit `put(&self, image: &StoredImage) -> CoreResult<String>` (SHA-256 hex, schreibt atomar per Temp-Datei + Rename, existierende Datei bleibt), `path(&self, hash, ext) -> PathBuf`, `remove(&self, hash, ext)`.
- `#[async_trait] pub trait ImageFetcher: Send + Sync { async fn fetch(&self, url: &str) -> CoreResult<Vec<u8>>; }` mit `HttpImageFetcher` über den gemeinsamen `http_client_builder`.
- `pub async fn run_image_sync(db: &Database, store: &ImageStore, fetcher: &dyn ImageFetcher, slots: &[ImageSlot], config: &SyncConfig, observer: &dyn SyncObserver, kind: SyncKind) -> CoreResult<ImageSyncSummary>`: ermittelt Soll (`anime.poster_tall`/`poster_wide`/`anilist_banner_image`, nicht leer) gegen `image_refs` (fehlend oder `source_url` geändert), lädt mit `SyncConfig.image_concurrency` (Standard 4) parallel, schreibt Datei, dann `images`-Zeile und `image_refs` in einer Transaktion, meldet Fortschritt mit Phase `Images`, zählt Fehler; am Ende Garbage Collection (nicht referenzierte `images` und Dateien löschen).
- `SyncProgress` erhält `phase: SyncPhase { Items, Images }`; `SyncSummary` erhält `failed_images: i32`; `Stats` erhält `image_count: i64`, `image_bytes: i64`.
- `run_catalog_sync` ruft nach den Seiten `run_image_sync(.., [PosterTall, PosterWide], ..)`; `run_enrichment` nach den Batches `run_image_sync(.., [Banner], ..)`. `RunGuard` umschließt beide Phasen.
- `Core::new(db_path: String, image_dir: String)`; exportierte Records: `AnimeSummary.poster_path: String` (absoluter Pfad oder leer) statt `poster_url`; `AnimeDetail.poster_path`, `banner_path` (Banner, sonst Querformat-Poster) statt URLs.
- Kotlin: `AppContainer` übergibt `File(context.filesDir, "images")`; Coil lädt `File(path)`; `coil-network-okhttp` entfällt; `SyncUiState` erhält `phase`; SyncCard und Benachrichtigung zeigen „Katalog“/„Anreicherung“ bzw. „Bilder“; Statistik-Kachel „Bilder“ mit `Formatter.formatShortFileSize`.

- [ ] **Step 1: Encoder für arm64 prüfen** – passenden WebP-Encoder (z. B. Crate `webp` mit libwebp) und Decoder (`image` mit `jpeg`,`png`) wählen, `cargo ndk -t arm64-v8a build` muss gelingen; Entscheidung im Report.
- [ ] **Step 2: Tests schreiben (RED)** – `encode_keeps_webp_unchanged`, `encode_jpeg_to_smaller_webp`, `encode_keeps_original_when_not_smaller`, `encode_rejects_unknown_format`, `store_names_file_by_sha256_and_is_idempotent`, `image_sync_downloads_only_missing_or_changed`, `image_sync_counts_failures_and_retries_next_run`, `image_sync_cancel_keeps_completed_files`, `image_sync_gc_removes_unreferenced`, `migration_v1_to_v2`, `query_returns_local_poster_path`; Kotlin: SyncCard zeigt Phase, Karte zeigt Platzhalter ohne Pfad.
- [ ] **Step 3: Implementieren**
- [ ] **Step 4: Prüfen** – `cargo fmt --check`, `cargo clippy --all-targets -D warnings`, `cargo test`, `./gradlew testDebugUnitTest lint ktlintCheck assembleDebug`.
- [ ] **Step 5: Gerät** – Katalog-Sync inkl. Bilder, Gesamtgröße messen; danach Flugmodus (`adb.exe shell cmd connectivity airplane-mode enable`), App neu starten, Grid und Detail zeigen Bilder; Flugmodus wieder aus.
- [ ] **Step 6: Commit**

### Task 15b: Filterbereich und Texte kompakt (Nutzer-Feedback vom 28.10.2026)

**Files:** `ui/search/SearchControls.kt`, `ui/search/AnimeGridScreen.kt`, `ui/components/FilterDropdownChip.kt` (ersetzt durch Exposed Dropdown), `ui/components/TagChip.kt`, `ui/settings/SettingsScreen.kt`, `ui/detail/DetailScreen.kt`, `strings.xml`, Tests.

- Suchfeld einzeilig, Standardhöhe, Platzhalter „Suchen“, Löschen-Symbol.
- Filter als M3-Exposed-Dropdowns (Label im Rahmen, Standard-Pfeil der Komponente, kein `ic_dropdown`), zwei pro Zeile: Gesehene | Genre (mit Anzahl), Fassung | Sortierung.
- Tags: Zeile mit Filterfeld und „Filter löschen“ (nur bei aktiven Tags); aktive Tags als farbige Chips mit Entfernen; übrige Tags als kompakte Chips in einem höhenbegrenzten, intern scrollenden Bereich.
- Trefferzahl „N Anime gefunden“ direkt über dem Grid; engere Abstände.
- Texte kürzen: keine Erklär- und Beispieltexte; Kopfzeilen ohne Untertitel; Leerzustände ein kurzer Satz; Einstellungen „Einstellungen“, „Katalog: <Land>“, „Statistik“; Inhaltssprache-Hinweis nur nach Änderung.
- Aus dem Task-15-Review: Sync-Karte zeigt vor der ersten Fortschrittsmeldung 0/0 (unbestimmt) statt alter Werte; Benachrichtigungs-Updates über `NotificationManager.notify` statt wiederholtem `setForeground`.
- Prüfung: Robolectric-Test für Dropdown-Label/-Wert; Gate wie üblich; Screenshot vom Gerät per `adb.exe exec-out screencap -p` für den Auftraggeber.

## Phase 3: CI, Release und Dokumentation

### Task 17: GitHub-Actions-Workflows, Signierung, Release-Build

**Files:**
- Create: `.github/workflows/checks.yml`, `.github/workflows/ci.yml`, `.github/workflows/nightly.yml`, `docs/release-signing.md`
- Modify: `app/build.gradle.kts` (Signing aus Umgebungsvariablen, Release mit `isMinifyEnabled = true`), `app/proguard-rules.pro`

**Interfaces:**
- Consumes: Gradle-Properties `crunchymate.versionCode`/`crunchymate.versionName` (Task 11); Secrets `SIGNING_KEYSTORE_B64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_PASSWORD`, `SIGNING_KEY_ALIAS`.
- Produces: Umgebungsvariablen für Gradle `SIGNING_KEYSTORE_PATH`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_PASSWORD`, `SIGNING_KEY_ALIAS`; Signing-Config `release` wird nur angelegt, wenn `SIGNING_KEYSTORE_PATH` gesetzt ist.

Inhalte:
- `checks.yml` (`on: workflow_call`), Jobs: `rust` (`dtolnay/rust-toolchain` stable mit `rustfmt, clippy`, `Swatinem/rust-cache`, fmt/clippy/test), `android` (JDK 17 per `actions/setup-java`, `android-actions/setup-android`, NDK-Version aus Task 1 per `sdkmanager`, Rust-Target, `cargo install cargo-ndk --locked`, `gradle/actions/setup-gradle`, `./gradlew lint ktlintCheck testDebugUnitTest assembleDebug`, APK als Artefakt).
- `ci.yml`: `on: push` (alle Branches) und `pull_request`; `uses: ./.github/workflows/checks.yml`.
- `nightly.yml`: `on: schedule: cron "0 3 * * *"` und `workflow_dispatch`; `permissions: contents: write`; Job `decide` (Checkout mit `fetch-depth: 0`; `should_build=true` bei `workflow_dispatch`, sonst nur wenn `git rev-parse HEAD` von `git rev-list -n1 nightly` abweicht oder der Tag fehlt); Job `checks` (`needs: decide`, `if: should_build`, `uses: checks.yml`); Job `release` (`needs: [decide, checks]`): Keystore nach `$RUNNER_TEMP/release.jks` dekodieren, `./gradlew assembleRelease -Pcrunchymate.versionCode=${{ github.run_number }} -Pcrunchymate.versionName=<baseVersion>-nightly.<JJJJMMTT>+<Kurz-SHA>`, Release-Notes aus `git log --oneline <alter nightly-Tag>..HEAD` (ohne Tag: letzte 20 Commits), `gh release delete nightly --cleanup-tag --yes` (Fehler ignorieren), `gh release create nightly --prerelease --target $GITHUB_SHA --title "Nightly <JJJJ-MM-TT>" --notes-file notes.md app/build/outputs/apk/release/app-release.apk`, Keystore-Datei im `always()`-Schritt löschen.
- Alle Actions mit fester Version (Major-Tag laut Task-1-Notiz oder Commit-SHA).
- `proguard-rules.pro`: `-keep class com.sun.jna.** { *; }`, `-keep class * implements com.sun.jna.** { *; }`, `-keep class de.crunchymate.core.ffi.** { *; }`, `-dontwarn java.awt.**`.
- `docs/release-signing.md`: Inhalt laut Spec 8.2 (keytool-Befehl mit PKCS12/RSA 4096/Validity 10000, Prüfung mit `keytool -list -v`, Sicherung, weiterer Alias im selben Keystore, Secrets per `gh secret set` inklusive Base64 ohne Zeilenumbruch, Hinweis zu Organisations- vs. Repository-Secrets).

- [ ] **Step 1: Workflows und Signing implementieren**

- [ ] **Step 2: Workflows statisch prüfen**

Run: `actionlint`
Expected: keine Meldungen.

- [ ] **Step 3: Release-Build lokal ohne Secrets**

Run: `./gradlew assembleRelease && ls app/build/outputs/apk/release/`
Expected: BUILD SUCCESSFUL; genau eine APK (`app-release-unsigned.apk`), keine ABI-/Dichte-/Sprach-Splits; Installation dieser APK ist nicht nötig.

- [ ] **Step 4: Release-Build lokal mit Test-Keystore auf dem Gerät**

Wegwerf-Keystore im Scratchpad erzeugen, Umgebungsvariablen setzen, `./gradlew assembleRelease`, Debug-App deinstallieren (`adb.exe uninstall de.crunchymate`), Release-APK installieren und starten.
Expected: App startet, Suche und Detail funktionieren (R8 hat keine UniFFI/JNA-Klassen entfernt). Wegwerf-Keystore danach löschen.

- [ ] **Step 5: Commit**

```bash
git add .github app/build.gradle.kts app/proguard-rules.pro docs/release-signing.md
git commit -m "ci: Prüf-, CI- und Nightly-Workflows mit Signierung aus Secrets"
```

- [ ] **Step 6: CI auf GitHub prüfen**

Run: `git push -u origin modernize/rust-core-native-ui && gh run watch`
Expected: `ci.yml` grün. Den Nightly lösen wir nach dem Merge auf `main` mit dem Auftraggeber per `gh workflow run nightly.yml` manuell aus; Expected: Pre-Release `nightly` mit APK.

### Task 18: README, CLAUDE.md und Abschlussprüfung

**Files:**
- Modify: `README.md` (neu schreiben), `CLAUDE.md` (aus Task 2 um Befehle und Architektur ergänzen)

**Interfaces:**
- Consumes: alle Tasks.

Inhalt README (Deutsch, ohne Emojis): Zweck, Funktionsumfang (Spec 3), Installation über das Nightly-Release, lokaler Build (rustup, Target, cargo-ndk, Android-SDK/NDK, `./gradlew assembleDebug`), Tests (`cargo test`, `./gradlew testDebugUnitTest`, `scripts/run-device-tests.sh`), Hinweis auf `docs/release-signing.md` und `docs/icons.md`. Ergänzungen in CLAUDE.md: Befehle inklusive Einzeltest (`cargo test -p crunchymate-core <name>`, `./gradlew testDebugUnitTest --tests '<Klasse>'`), Architektur (Rust-Kern hinter UniFFI, `CoreGateway` als einzige Kotlin-Schnittstelle, Änderungssignal, Sync über WorkManager), Regeln (keine Emojis, Icons liefert der Auftraggeber, WebP statt PNG, Strings nur in `strings.xml`, neue Kernfunktion = Rust-Export plus Gateway-Methode plus Fake in `FakeCore`), Clean-Code-Leitlinien aus Spec 9.5, `adb.exe` mit `wslpath -w`.

- [ ] **Step 1: Dokumente schreiben**

- [ ] **Step 2: Gesamtprüfung**

Run: `cargo fmt --check && cargo clippy --all-targets -- -D warnings && cargo test && ./gradlew lint ktlintCheck testDebugUnitTest assembleDebug && bash scripts/run-device-tests.sh && git ls-files | grep -E '^(cmd|internal|web|android)/|go\.mod|\.keystore$|\.jks$' ; test $? -eq 1`
Expected: alles grün; die letzte Prüfung findet keine Go-, Web-, Alt-Android- oder Keystore-Dateien.

- [ ] **Step 3: Commit**

```bash
git add README.md CLAUDE.md docs
git commit -m "docs: README und CLAUDE.md für Rust-Kern und Compose-App"
```
