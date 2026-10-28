# Design: Modernisierung – Rust-Kern mit nativer Compose-UI

Stand: 28.10.2026

## 1. Ziel und Kontext

CrunchyMate wird komplett renoviert. Der Go-Webserver und das JS/HTML/CSS-Frontend entfallen ersatzlos. Übrig bleibt ausschließlich eine Android-App, deren Fachlogik in Rust implementiert ist und deren Oberfläche nativ mit Jetpack Compose erzeugt wird. Gebaut und veröffentlicht wird über GitHub Actions inklusive Nightly-Release.

### Vorgaben des Auftraggebers

- Go-Teil (`cmd/`, `internal/`, `go.mod`, `go.sum`) und Web-Frontend (`web/`) werden entfernt.
- Nur noch die Android-App; UI nativ, nicht mehr WebView/JS.
- Programmiersprache für die Logik ist Rust; Kotlin nur als dünne Schicht für UI und Android-Systemdienste.
- `minSdk` 34, `targetSdk` 37, Zielversion 37.2 (siehe 4.4).
- Einziges ABI: `arm64-v8a`.
- Ausgabe als normales Einzel-APK, kein App-Bundle und keine Split-APKs (xapk/apkm).
- Build über GitHub Actions; Nightly-Release, zusätzlich manuell auslösbar.
- Funktionsumfang 1:1 wie die heutige Web-UI.
- Mehrsprachigkeit von Anfang an vorbereitet; zum Start nur Deutsch.
- Crunchyroll-Inhaltssprache umstellbar (siehe 5.3).
- Code modular, wartbar, erweiterbar; Clean-Code-Prinzipien.
- Keine Emojis – weder in UI noch Code, Kommentaren oder Dokumentation. Icons als SVG (VectorDrawable), Rastergrafiken als WebP. Die konkreten Symbole wählt der Auftraggeber selbst aus (Liste in 7.6).
- Bestehende Installationen und Daten müssen nicht migriert werden (frischer Start).

### Erfolgskriterien

- Die App bietet alle heutigen Funktionen (Abschnitt 3) ohne WebView und ohne `AndroidBridge`.
- Das Repository enthält keinen Go- und keinen Web-Code mehr.
- `ci.yml` (Rust- und Android-Prüfungen) ist grün; `nightly.yml` erzeugt ein signiertes Pre-Release mit APK.
- Die App läuft auf dem Testgerät (Samsung SM-S938B, Android 17 / SDK 37.0, arm64-v8a).

## 2. Nicht-Ziele

- Kein echter Länderkatalog: Das Katalogland ist im signierten Crunchyroll-Token festgelegt und wird serverseitig bestimmt (Ergebnis des Spikes, siehe 5.3). Geo-Sperren werden nicht umgangen.
- Keine Datenmigration aus der bisherigen App.
- Keine Strings in einer Datenbank; Texte liegen in Android-Ressourcen.
- Kein Hilt/Dagger; Abhängigkeiten werden manuell verdrahtet.
- Kein x86/x86_64-Build, kein Emulator in der CI.

## 3. Funktionsumfang (1:1 aus der heutigen Web-UI)

- Vier Bereiche mit unterer Navigation: Suche, Gesehen, Meh, Einstellungen. Badges: Anzahl Gesehen, Anzahl Meh, Indikator „Sync läuft“.
- Suche: Volltext über Titel sowie AniList-Titel (Romaji/Englisch); Filter Gesehen-Status (mit anzeigen / ungesehene / nur gesehene), Genre, Fassung (alle / nur Synchro / nur Untertitel / beides / eins von beiden), Sortierung (Titel, AniList-Score, Episodenzahl, Erscheinungsjahr).
- Tag-Filter mit Zuständen neutral → Boost → Muss → Ausgeschlossen → neutral; filterbare Tag-Liste; Leiste aktiver Tags; „Alle zurücksetzen“.
- Kachel: Poster, Titel, Score, Anzahl Boost-Treffer, Fassungs-Badge, Gesehen- und Meh-Umschalter.
- Meh-markierte Einträge sind in der Suche ausgeblendet.
- Detailansicht: Banner, Poster, Titel und Alternativtitel, Jahr, Episoden, Fassung, Genres, Beschreibung, Tags mit Rang, AniList-Bereich (Score, Status, Link), Gesehen/Meh.
- Einstellungen: Crunchyroll-Sync und AniList-Anreicherung jeweils starten und abbrechen, mit Fortschritt, Status, Zähler, letztem Lauf und letztem Fehler; „Gesehene ausblenden“; Statistik (Gesamt, Gesehen, Meh, Angereichert, Tags).
- Suchzustand und „Gesehene ausblenden“ werden gespeichert und beim Start wiederhergestellt.

Änderungen gegenüber heute: Paging statt fester Grenze von 150 Einträgen; Detailansicht als eigenes Navigationsziel statt Modal; Fassungsfilter bezieht sich auf die eingestellte Inhaltssprache statt fest auf Deutsch; Icons statt Emojis.

## 4. Architektur

### 4.1. Aufgabenteilung

- **Rust-Kern (`core/`)**: gesamte Fachlogik und Persistenz – SQLite, Filter/Suche/Sortierung, Crunchyroll- und AniList-Anbindung, Sync-Abläufe, Einstellungen.
- **Kotlin (`app/`)**: Compose-Screens, ViewModels, Navigation, WorkManager-Worker, Benachrichtigungen, Übersetzung von Fehlercodes in Texte. Keine Fachlogik.
- **Schnittstelle**: UniFFI (Proc-Macros). Rust-`async fn` werden zu Kotlin-`suspend fun`; Objekte erhalten generierte Interfaces (z. B. `CoreInterface`), gegen die Kotlin programmiert und testet.

Abhängigkeiten zeigen nur nach innen: UI → ViewModel → Kern-API (UniFFI) → Domäne → Persistenz/Remote über Traits.

### 4.2. Repository-Layout

Das Android-Projekt liegt im Repository-Root (kein `android/`-Unterordner mehr).

```text
Cargo.toml                Workspace
rust-toolchain.toml
core/                     Crate "crunchymate-core" (cdylib + lib)
  src/
    lib.rs                UniFFI-Exporte: Objekt Core, Records, Enums, CoreError
    error.rs              CoreError
    db/                   Verbindung, Schema, Migrationen, Repositories (anime, tags, genres, settings, sync_state)
    query.rs              AnimeFilter -> parametrisiertes SQL
    model.rs              Domänentypen
    remote/
      catalog_source.rs   trait CatalogSource
      crunchyroll.rs      CatalogSource auf Basis von crunchyroll-rs
      metadata_source.rs  trait MetadataSource
      anilist.rs          MetadataSource, eigene GraphQL-Implementierung
    sync/
      catalog_sync.rs     Crunchyroll-Sync
      enrichment.rs       AniList-Anreicherung
      progress.rs         SyncKind, SyncProgress, trait SyncObserver
      config.rs           SyncConfig (Pausen, Batch-Größen, Retries)
  tests/                  Integrationstests
app/
  src/main/java/de/crunchymate/
    core/                 Wrapper um generierte Bindings, Änderungssignal
    sync/                 SyncWorker, SyncScheduler, Benachrichtigung
    ui/
      theme/
      components/         AnimeCard, TagChip, FilterDropdownChip, SyncCard
      search/             AnimeGridScreen, SearchViewModel
      detail/             DetailScreen, DetailViewModel
      settings/           SettingsScreen, SettingsViewModel
      CrunchyMateApp.kt   Scaffold, NavigationBar, NavHost
    AppContainer.kt       manuelle DI
    CrunchyMateApplication.kt
    MainActivity.kt
  src/main/res/           strings.xml (de), drawables (Vector), WebP
  src/test/               JVM-Unit-Tests
  src/androidTest/        lokale Smoke-Tests auf dem Gerät
gradle/libs.versions.toml
settings.gradle.kts, build.gradle.kts, gradlew
.github/workflows/        ci.yml, nightly.yml, checks.yml (wiederverwendbar)
docs/release-signing.md
```

Entfernt werden: `cmd/`, `internal/`, `web/`, `go.mod`, `go.sum`, `android/` (Inhalt geht in das neue Layout über bzw. entfällt), insbesondere `assets/web/`, `bridge/`, `db/`, `sync/` (Kotlin), `MainActivity` mit WebView, `activity_main.xml`, `release.keystore`, die Abhängigkeiten `androidx.webkit` und `org.json` sowie der Termux-Override in `gradle.properties`.

### 4.3. Technologie

- Rust: `rusqlite` (Feature `bundled`), `reqwest` mit `rustls`, `serde`/`serde_json`, `tokio`, `uniffi`, `crunchyroll-rs` (TLS über rustls), `thiserror`. Tests: `wiremock`.
- Kotlin: Jetpack Compose mit Material 3, Navigation Compose, Lifecycle/ViewModel, Paging 3, Coil, WorkManager, kotlinx-coroutines, JNA (Laufzeit für UniFFI).
- Build: Gradle, AGP und Kotlin (K2, Compose-Compiler-Plugin) jeweils in aktueller stabiler Version, zentral in `gradle/libs.versions.toml`. Java-Toolchain 17. Die konkreten Versionen werden im Plan anhand der zum Umsetzungszeitpunkt aktuellen Releases festgelegt.
- Rust-Build: Gradle-Task vor `preBuild` ruft `cargo ndk -t arm64-v8a build --release` auf, kopiert die `.so` nach `jniLibs` und erzeugt die Kotlin-Bindings mit `uniffi-bindgen` in `build/generated`. Generierter Code wird nicht eingecheckt.

### 4.4. SDK-Stufen

- `minSdk` 34, `targetSdk` 37, `compileSdk` 37.
- Zielversion 37.2: `targetSdk` akzeptiert nach aktuellem Kenntnisstand nur ganze API-Stufen; eine Minor-Version lässt sich nur für `compileSdk` angeben. Im ersten Planschritt wird geprüft, ob SDK 37.2 als stabile Plattform verfügbar ist und wie AGP die Minor-Version konfiguriert. Ist sie verfügbar, wird `compileSdk` 37 mit Minor 2 gesetzt, sonst 37. Die App nutzt keine APIs, die erst ab 37.2 existieren, da das Testgerät 37.0 meldet.

## 5. Rust-Kern

### 5.1. Datenmodell

Frisches Schema, Versionierung über `PRAGMA user_version`, Migrationen als geordnete Liste im Code. WAL-Modus.

- `anime`: id, title, description, poster_tall, poster_wide, episode_count, launch_year, is_subbed, is_dubbed, has_target_sub, has_target_dub, cr_updated_at, anilist_id, anilist_title_romaji, anilist_title_english, anilist_description, anilist_score, anilist_status, anilist_banner_image, anilist_site_url, anilist_synced_at, watched, watched_at, is_meh, meh_at. Gegenüber heute entfallen `notes`, `slug`, `season_count`, `anilist_cover_image` (von der UI nicht genutzt); `has_gersub`/`has_gerdub` werden zu `has_target_sub`/`has_target_dub` (bezogen auf die eingestellte Inhaltssprache).
- `genres`, `anime_genres`, `tags`, `anime_tags` (mit `rank`) wie heute, inklusive Indizes auf Titel, Gesehen, Meh, Fassung, Tag-Name und Tag-Zuordnung.
- `sync_state`: je Sync-Art letzter Lauf, Fortschritt, letzter Fehler(-code), erkanntes Katalogland.
- `settings`: Schlüssel/Wert für „Gesehene ausblenden“, gespeicherten Suchzustand und Inhaltssprache.

Ändert sich die Inhaltssprache, werden `has_target_sub`/`has_target_dub` erst beim nächsten Crunchyroll-Sync neu berechnet; die Einstellungen weisen darauf hin.

### 5.2. Kern-API

```rust
// Auszug der UniFFI-Schnittstelle
pub enum WatchedFilter { All, Unwatched, Watched }
pub enum MehFilter { Hide, Only, Include }
pub enum LanguageFilter { All, Dub, Sub, Both, Any }
pub enum SortOrder { Title, Score, Episodes, Year }

pub struct AnimeFilter {
    pub query: String,
    pub genre: Option<String>,
    pub language: LanguageFilter,
    pub watched: WatchedFilter,
    pub meh: MehFilter,
    pub sort: SortOrder,
    pub prefer_tags: Vec<String>,
    pub must_tags: Vec<String>,
    pub exclude_tags: Vec<String>,
}

// Methoden des Objekts Core
// query_anime(filter, offset, limit) -> AnimePage { items, total }
// get_anime(id) -> Option<AnimeDetail>
// set_watched(id, watched) / set_meh(id, meh)
// list_genres() / list_tags() -> Vec<NameCount>
// stats() -> Stats
// settings() / save_settings(settings)
// sync_status(kind) -> SyncStatus
// async sync_catalog(observer) / async enrich_metadata(observer)
```

Filtersemantik (unverändert gegenüber heute):

- Textsuche: `LIKE` auf Titel, AniList-Romaji- und AniList-Englisch-Titel.
- `must_tags`: alle müssen vorhanden sein (UND). `exclude_tags`: keiner darf vorhanden sein.
- `prefer_tags`: Anzahl Treffer als `prefer_score`, sortiert immer zuerst; danach die gewählte Sortierung (Score, Episoden, Jahr absteigend; Titel aufsteigend), Titel als Tiebreaker.
- Tabs: Suche = `MehFilter::Hide`; Gesehen = `WatchedFilter::Watched` + `MehFilter::Include`; Meh = `MehFilter::Only`.
- Alle Werte werden als gebundene Parameter übergeben; SQL-Fragmente stammen ausschließlich aus Enums.
- Genres und Tags einer Ergebnisseite werden mit einer zusätzlichen Abfrage für alle Einträge der Seite geladen (kein N+1).

Nebenläufigkeit: `Core` hält eine `Mutex<Connection>`; Sync und UI greifen auf dieselbe Instanz zu. Schreibvorgänge des Syncs laufen in einer Transaktion pro Katalogseite bzw. pro Batch.

Fehler: `CoreError` als UniFFI-Enum (`Network`, `RateLimited { retry_after_secs }`, `Auth`, `Parse`, `Database`, `Cancelled`). Der Kern liefert keine anzeigefertigen Texte.

### 5.3. Crunchyroll-Anbindung und Inhaltssprache

Spike-Ergebnis (28.10.2026): Das anonyme Token enthält `country` in den signierten JWT-Claims; der Katalog lieferte für `de-DE`, `en-US`, `fr-FR` und `ja-JP` jeweils `total = 1686`, nur die Texte unterschieden sich. Das Katalogland lässt sich über die API nicht legitim wählen.

Daraus folgt:

- Einstellung **Inhaltssprache** (Locale, Standard `de-DE`): steuert `locale`/`Accept-Language` beim Abruf und die Berechnung von `has_target_dub`/`has_target_sub` aus `audio_locales`/`subtitle_locales`.
- Das **Katalogland** wird beim Sync aus dem Token gelesen, in `sync_state` gespeichert und in den Einstellungen angezeigt („automatisch erkannt“).
- Die bisherige Heuristik, Dub/Sub zusätzlich aus dem Titel abzuleiten, wird im Plan anhand des Bestandscodes übernommen, sofern sie sprachunabhängig formulierbar ist; sonst gilt sie nur für Deutsch.

Umsetzung über `crunchyroll-rs` (MIT/Apache-2.0) hinter dem Trait `CatalogSource { token_country(), fetch_page(start, count, locale) }`. Erster Planschritt: prüfen, dass die Bibliothek anonymen Login, paginiertes Browsing sowie Audio- und Untertitel-Locales bietet und mit rustls für `aarch64-linux-android` baut. Fehlt etwas, wird `CatalogSource` mit eigener reqwest-Implementierung umgesetzt (wie heute). Hinweise der Bibliothek zum Umgehen von Cloudflare-Sperren werden nicht übernommen.

### 5.4. AniList-Anbindung

Eigene Implementierung hinter `MetadataSource { search_batch(titles) }`: GraphQL-Batch-Suche, Titelbereinigung, Retry mit `Retry-After` bzw. exponentiellem Backoff bei HTTP 429, Abbruch nach maximaler Versuchszahl (Werte aus `SyncConfig`).

### 5.5. Sync-Abläufe

- **Crunchyroll-Sync**: lädt den Katalog seitenweise mit Pause zwischen den Seiten, Upsert pro Seite in einer Transaktion. Kein Löschen verschwundener Serien. Bei Abbruch bleiben gespeicherte Seiten erhalten; ein neuer Lauf beginnt von vorn.
- **AniList-Anreicherung**: verarbeitet nur Einträge ohne AniList-Daten in Batches mit Pause; Fehler einzelner Batches werden toleriert und gezählt.
- Fortschritt über UniFFI-Callback-Interface `SyncObserver::on_progress(kind, done, total)`.
- Abbruch: Wird die Kotlin-Coroutine abgebrochen, verwirft Rust die Future; `Cancelled` wird in `sync_state` vermerkt.

### 5.6. Offline-Betrieb und Bilder (Ergänzung vom 28.10.2026)

Nach einem abgeschlossenen Sync läuft CrunchyMate vollständig offline. Netzzugriffe gibt es ausschließlich während eines manuell gestarteten Syncs; einzige Ausnahme ist der Link „Auf AniList öffnen“, der den Browser startet.

- Heruntergeladen werden: Hochformat-Poster und Querformat-Poster beim Katalog-Sync, AniList-Banner bei der Anreicherung.
- Ablauf: nach den Katalogseiten bzw. nach den AniList-Batches folgt im selben Lauf die Phase „Bilder“. Geladen werden nur fehlende Bilder und solche, deren Quell-URL sich geändert hat; Downloads nacheinander, ohne zusätzliche Wartezeit; Bild-Phasen von Katalog-Sync und Anreicherung laufen prozessweit nie gleichzeitig. Fehlgeschlagene Bilder werden gezählt und beim nächsten Sync erneut versucht; ein Abbruch behält bereits Gespeichertes.
- Format: Liefert die Quelle WebP, wird die Datei unverändert gespeichert. JPEG/PNG werden in WebP mit hoher Qualität (ohne sichtbaren Qualitätsverlust) umgewandelt; wird das Ergebnis nicht kleiner als das Original, bleibt das Original erhalten.
- Ablage: Dateien im App-Speicher (`files/images/`), Dateiname = SHA-256 des gespeicherten Inhalts (64 Hex-Zeichen) plus Endung des Formats. Die Datenbank speichert je Anime und Bildart Quell-URL und Hash; ein Eintrag wird erst geschrieben, wenn die Datei vollständig vorliegt. Nicht mehr referenzierte Dateien entfernt der Sync am Ende.
- Anzeige: Grid und Detail laden ausschließlich lokale Dateien; ohne Datei erscheint ein neutraler Platzhalter. Die UI hat keinen Netzwerk-Bildlader.
- Fortschritt zeigt die Phase an (Katalog bzw. Anreicherung, dann Bilder). Die Statistik zeigt den Speicherbedarf der Bilder.

## 6. Android-Systemintegration

- `SyncWorker` (`CoroutineWorker`) mit `setForeground`, Typ `dataSync`; ruft den passenden Kern-Sync auf und aktualisiert die Benachrichtigung (Fortschritt, Aktion „Abbrechen“) aus dem `SyncObserver`.
- `SyncScheduler`: startet Worker als `UniqueWork` je Sync-Art mit `KEEP`, bricht ab, stellt `Flow<SyncUiState>` aus `WorkInfo` und Fortschrittsdaten bereit.
- Beide Syncs sind einzeln startbar, keine automatische Verkettung.
- `POST_NOTIFICATIONS` wird beim ersten Sync-Start angefragt; ohne Berechtigung läuft der Sync ohne sichtbare Benachrichtigung.
- Der eigene `SyncForegroundService` entfällt; WorkManagers `SystemForegroundService` wird per Manifest-Merge mit `foregroundServiceType="dataSync"` deklariert.
- Änderungssignal: Der Kotlin-Wrapper um `Core` emittiert nach jeder schreibenden Operation und bei Sync-Fortschritt ein Ereignis (`SharedFlow`); ViewModels und `PagingSource` laden daraufhin neu.

## 7. UI

### 7.1. Navigation

`Scaffold` mit `NavigationBar` (Suche, Gesehen, Meh, Einstellungen) und Badges; Detailansicht als Ziel `detail/{id}`. Systemzurück funktioniert ohne Sonderbehandlung.

### 7.2. Screens

- **Suche**: Suchfeld mit Löschen; Filter-Chips mit Dropdown für Gesehen-Status, Genre, Fassung, Sortierung; Tag-Bereich mit Filterfeld, `FlowRow` aus Tag-Chips (Zustand per Farbe **und** Icon), Leiste aktiver Tags, Zurücksetzen; Trefferzahl; `LazyVerticalGrid` (adaptive Spalten) mit Paging; Leer- und Fehlerzustand mit Verweis auf den Sync. Suchzustand wird verzögert gespeichert und beim Start wiederhergestellt.
- **Gesehen / Meh**: derselbe Grid-Screen mit festem Filter und Titel-Banner, ohne Filterleiste.
- **Detail**: Banner, Poster, Titel, Alternativtitel, Badges (Jahr, Episoden, Fassung), Genres, Beschreibung (AniList-HTML via `AnnotatedString.fromHtml`), Tags mit Rang und aktuellem Filterzustand, AniList-Bereich mit externem Link, Gesehen/Meh.
- **Einstellungen**: zwei Sync-Karten (Fortschritt, Status, Zähler, letzter Lauf, letzter Fehler, Start/Abbrechen), Katalogland, Inhaltssprache, „Gesehene ausblenden“, Statistik, Link zur App-Spracheinstellung des Systems.

### 7.3. Zustand

Ein ViewModel je Screen mit `StateFlow<UiState>` (unveränderliche Data Classes), Ereignisse als Methoden, unidirektionaler Datenfluss. Composables sind zustandslos (State + Lambdas) und dadurch in Previews und Tests isoliert nutzbar.

### 7.4. Theme

Material 3, dunkles Schema angelehnt an die heutigen Farben (Crunchyroll-Orange als Primärfarbe), zusätzlich helles Schema; keine Dynamic Colors. Edge-to-Edge.

### 7.5. Texte und Mehrsprachigkeit

- Alle Texte in `res/values/strings.xml` (Deutsch), Pluralformen über `plurals`. Keine fest kodierten Texte (Lint `HardcodedText` als Fehler).
- Fehlercodes des Kerns werden in Kotlin auf String-Ressourcen abgebildet.
- App-Sprache über die systemeigene App-Spracheinstellung (`localeConfig`); zum Start nur `de`.

### 7.6. Icons und Bilder

- Keine Emojis. Icons als SVG, umgewandelt in VectorDrawables, einfarbig im 24-px-Raster (im Theme einfärbbar). Rastergrafiken, falls nötig, als WebP.
- App-Icon als Adaptive Icon aus Vektoren (Vorder- und Hintergrund, 108 dp, Motiv in der mittleren 66-dp-Safe-Zone) plus einfarbige Variante für Themed Icons. Benachrichtigungs-Icon als weiße Silhouette auf transparentem Grund.
- Bis der Auftraggeber die Dateien liefert, werden neutrale Platzhalter verwendet; vor dem ersten Nightly-Release werden sie ersetzt.

Benötigte Symbole (Auswahl durch den Auftraggeber):

| Bereich | Symbole |
|---|---|
| Navigation | Suche, Gesehen, Meh, Einstellungen |
| Kachel/Detail | Gesehen (an/aus), Meh (an/aus) |
| Badges | Score, Synchro, Untertitel, Boost-Treffer |
| Tag-Zustände | Boost, Muss, Ausgeschlossen |
| Aktionen | Eingabe löschen, Tags zurücksetzen, Sync starten, Sync abbrechen, externer Link, Dropdown-Pfeil, Zurück |
| Zustände | leeres Ergebnis, Fehler |
| Statistik | Gesamt, Gesehen, Meh, Angereichert, Tags |
| System | Benachrichtigung (Sync), App-Icon |

## 8. CI, Nightly und Signierung

### 8.1. Workflows

- `checks.yml` (wiederverwendbar): Rust (`cargo fmt --check`, `cargo clippy -- -D warnings`, `cargo test`), Android (JDK 17, SDK/NDK, Rust-Target `aarch64-linux-android`, `cargo-ndk`, `./gradlew lint ktlintCheck testDebugUnitTest assembleDebug`). Caching über `Swatinem/rust-cache` und `gradle/actions/setup-gradle`; alle Actions auf feste Versionen gepinnt.
- `ci.yml`: bei Push und Pull Request, ruft `checks.yml` auf.
- `nightly.yml`: Cron täglich 03:00 UTC und `workflow_dispatch`. Der Cron-Lauf überspringt den Build, wenn es seit dem letzten Nightly keinen neuen Commit auf `main` gibt; ein manueller Lauf baut immer. Ruft `checks.yml` auf, dann `assembleRelease` signiert, und ersetzt das GitHub-Pre-Release mit festem Tag `nightly` (APK als Asset, Commit-Log seit dem vorigen Nightly als Beschreibung).
- Versionierung: `versionCode` = `github.run_number`; `versionName` = `<Basisversion>-nightly.<JJJJMMTT>+<Kurz-SHA>`.

### 8.2. Signierung

- Secrets (bereits angelegt): `SIGNING_KEYSTORE_B64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_PASSWORD`, `SIGNING_KEY_ALIAS`.
- Der Workflow dekodiert den Keystore in eine temporäre Datei; Gradle liest Pfad, Passwörter und Alias aus Umgebungsvariablen. Fehlen sie, bleibt der Release-Build unsigniert.
- `release.keystore` und die Klartext-Passwörter werden entfernt; `*.jks` und `*.keystore` kommen in `.gitignore`. Der alte Schlüssel wird nicht mehr verwendet.
- `docs/release-signing.md` beschreibt Anlage des Keystores mit `keytool`, Sicherung, mehrere Schlüssel pro Keystore (ein Alias je App) und das Setzen der Secrets mit `gh`.

### 8.3. Release-Build

`isMinifyEnabled = true` mit R8 und Keep-Regeln für UniFFI/JNA; Rust-Bibliothek mit `--release` gebaut und gestrippt.

## 9. Tests und Qualität

### 9.1. Rust (`cargo test`, Host)

- `query`: tabellengetriebene Tests gegen In-Memory-SQLite für alle Filter, Tag-Semantik, Sortierung inklusive Tiebreaker, Paging und Tab-Abbildung. Die Fälle orientieren sich an den bisherigen `AnimeRepositoryTest` und `db_test.go`.
- `db`: idempotenter Upsert, Gesehen/Meh inklusive Zeitstempel, Migrationen.
- `sync`: Fakes für `CatalogSource`/`MetadataSource`; Fortschritt, Abbruch nach Seite N, tolerierte Batch-Fehler, nur nicht angereicherte Einträge.
- `remote/anilist`: `wiremock` für Mapping, `Retry-After`, Aufgeben nach maximaler Versuchszahl.
- `remote/crunchyroll`: Mapping der Bibliothekstypen (u. a. Dub/Sub-Erkennung je Inhaltssprache). Keine Tests gegen die echte API in der CI.

### 9.2. Kotlin (JVM)

- ViewModels gegen Fakes der generierten Interfaces mit `kotlinx-coroutines-test`: State-Übergänge, Tag-Zyklus, Speichern/Wiederherstellen des Suchzustands, Neuladen nach Änderungssignal.
- `SyncScheduler` mit `work-testing`: Abbildung `WorkInfo` → `SyncUiState`, Fehlercodes.
- Zustandslose Composables mit Robolectric (Semantik-Tests, z. B. Tag-Chip-Zustand, Leerzustand).

### 9.3. Lokal auf dem Gerät

Instrumentierte Smoke-Tests (Compose UI Test): App-Start, Tab-Wechsel, Tag-Zyklus, Detail öffnen. Nicht Teil der CI. Ablauf: Build in WSL2, Installation und Ausführung über das Windows-`adb.exe` (`adb.exe install`, `adb.exe shell am instrument`), da das Gerät an Windows angeschlossen ist.

Die lokale Toolchain in WSL2 (rustup statt Distributions-cargo, Android-Target, Linux-Android-SDK und NDK, cargo-ndk) wird im ersten Planschritt eingerichtet. Der Auftraggeber hat Installationen innerhalb von WSL2 freigegeben; am Windows-System wird nichts verändert.

### 9.4. Statische Prüfungen

`rustfmt`, `clippy -D warnings`, `ktlint`, Android Lint mit `warningsAsErrors` für `HardcodedText`, `MissingTranslation`, `UnusedResources`. Das Emoji-Verbot ist als Projektregel in `CLAUDE.md` verankert; eine automatische Prüfung gibt es nicht.

### 9.5. Leitlinien (Clean Code, Modularität)

- Kleine Einheiten mit einem Zweck; Abhängigkeiten zeigen nach innen; Rust-Module kommunizieren über Traits, Kotlin über Interfaces.
- Aussagekräftige Namen; Kommentare erklären das Warum.
- Keine Magic Numbers (Konfiguration in `SyncConfig` bzw. Konstanten mit Namen).
- Kein toter oder auskommentierter Code; keine `TODO`s ohne Issue.
- Die Leitlinien werden in `CLAUDE.md` übernommen; `README.md` und `CLAUDE.md` werden auf den neuen Stand gebracht (ohne Emojis).

## 10. Offene Prüfpunkte für den ersten Planschritt

1. Verfügbarkeit von SDK 37.2 und Konfiguration der Minor-Version in AGP.
2. `crunchyroll-rs`: anonymer Login, paginiertes Browsing, Audio-/Untertitel-Locales, Build für `aarch64-linux-android` mit rustls; Lizenz- und Pflegestatus bestätigen.
3. Einrichtung der lokalen Toolchain in WSL2.

## 11. Mögliche zukünftige Features

- Periodischer automatischer Sync.
- Entfernen von Serien, die aus dem Katalog verschwunden sind (unter Erhalt von Gesehen-/Meh-Markierungen).
