# Vorprüfung: Toolchain, SDK 37.2 und crunchyroll-rs

Stand: 28.10.2026. Alle Versionen wurden am 28.10.2026 abgefragt (Quellen siehe Tabelle). Sie sind eine Momentaufnahme und vor dem Einchecken der Build-Dateien erneut zu prüfen, falls zwischen Notiz und Umsetzung Tage liegen.

## 1. Lokale Toolchain (WSL2 Ubuntu)

| Komponente | Version | Hinweis |
|---|---|---|
| rustup / Rust stable | rustc 1.99.1 (13.10.2026), cargo 1.99.1 | `~/.cargo/bin/cargo` ersetzt das Distributions-Rust (1.93.1) im PATH |
| Rust-Target | `aarch64-linux-android` | installiert |
| cargo-ndk | 4.1.2 | `cargo install cargo-ndk` |
| Android cmdline-tools | 22.0 (Paket `commandlinetools-linux-15859902`) | `~/Android/Sdk/cmdline-tools/latest` |
| platform-tools | 37.0.1 | Linux-`adb` sieht das am Windows-Host hängende Telefon nicht (erwartet) |
| Plattformen | `platforms;android-37.0` (Rev. 2) und `platforms;android-37.2` (Rev. 1) | Pfade: `platforms/android-37.0`, `platforms/android-37.2` |
| Build-Tools | `build-tools;37.0.0` | AGP 9.4 installiert zusätzlich selbst `36.0.0` (sein Standard) |
| NDK | `ndk;30.0.16248370` (r30, stabil) | `ANDROID_NDK_HOME=$ANDROID_HOME/ndk/30.0.16248370` |
| actionlint | 1.7.12 (30.03.2026) | `~/.local/bin/actionlint` |
| JDK | OpenJDK 25.0.5 | AGP 9.4 verlangt mindestens JDK 17 |

Umgebungsvariablen stehen in `~/.profile`: `ANDROID_HOME`, `ANDROID_NDK_HOME` sowie PATH-Ergänzungen für `~/.cargo/bin`, `cmdline-tools/latest/bin` und `platform-tools`.

Hinweis zur Plattform-ID: Die Plattform heißt im SDK-Manager `android-37.0` (nicht `android-37`). Der Plan-Befehl `platforms;android-37` ist entsprechend `platforms;android-37.0` bzw. `platforms;android-37.2`.

## 2. Entscheidung compileSdk: 37.2

Befund:

- `sdkmanager --list` führt `platforms;android-37.2` (Revision 1) als reguläres Paket. Die Vorabversionen `37.2-beta1` bis `-beta3` sind separate Einträge. Die installierte Plattform hat `AndroidVersion.CodeName` leer und `PreviewSdkInt=0`, ist also keine Vorschau.
- AGP-Release-Notes 9.4 nennen API 37 als höchstes unterstütztes Level; zur Minor-Syntax enthalten die Seiten nichts. Die Syntax wurde deshalb im AGP-Code (`gradle-api`/`gradle-common-api` 9.4.1: `CompileSdkSpec.release(int, Action)` und `CompileSdkReleaseSpec.minorApiLevel`) ermittelt und mit einem Wegwerf-Projekt (AGP 9.4.1, Gradle 9.8.2) praktisch geprüft.
- Praxistest: `./gradlew :app:assembleDebug` mit `minorApiLevel = 2` baut erfolgreich eine Debug-APK. Mit `minorApiLevel = 3` (nicht vorhandene Plattform) scheitert der Build. Der Test ist damit aussagekräftig.

Entscheidung: `compileSdk` 37.2, targetSdk bleibt 37.

Exakte AGP-Syntax (Kotlin-DSL, Modul `build.gradle.kts`):

```kotlin
android {
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }
    defaultConfig {
        minSdk = 26      // Wert aus dem Plan übernehmen, hier nur Platzhalter
        targetSdk = 37   // targetSdk kennt nur die Hauptversion
    }
}
```

Rückfallebene, falls 37.2 in CI nicht bereitsteht: `compileSdk = 37` (entspricht `platforms;android-37.0`). In CI muss `platforms;android-37.2` im SDK installiert sein.

Optional: `buildToolsVersion = "37.0.0"` setzen, sonst nimmt AGP 9.4 Build-Tools 36.0.0.

## 3. Versionstabelle (aktuelle stabile Versionen)

Pre-Releases (alpha, beta, rc) sind ausgeschlossen. Quelle: jeweils `maven-metadata.xml` bzw. crates.io-API, abgefragt am 28.10.2026; Datum = Veröffentlichungsdatum laut Repository (Last-Modified der POM, bei Crates `updated_at`).

| Komponente | Version | Datum | Quelle |
|---|---|---|---|
| Gradle | 9.8.2 | 19.10.2026 | services.gradle.org/versions/current |
| Android Gradle Plugin (AGP) | 9.4.1 | 18.09.2026 | dl.google.com Maven (`com.android.tools.build:gradle`); 9.5.0 liegt nur als rc vor; Mindest-Gradle für 9.4: 9.6.0 |
| Kotlin | 2.4.21 | 08.10.2026 | Maven Central (`kotlin-gradle-plugin`); 2.5.0 nur Beta |
| Compose-BOM | 2026.09.00 | 09.09.2026 | Google Maven (enthält material3 1.4.0) |
| Navigation Compose | 2.10.2 | 23.09.2026 | Google Maven (`androidx.navigation:navigation-compose`) |
| Lifecycle (runtime-compose, viewmodel-compose) | 2.11.0 | 17.06.2026 | Google Maven; 2.12.0 nur alpha |
| Paging (paging-compose) | 3.5.3 | 12.10.2026 | Google Maven |
| Coil 3 (coil-compose) | 3.6.3 | 18.09.2026 | Maven Central (`io.coil-kt.coil3`); für Netzwerk zusätzlich `coil-network-okhttp` in gleicher Version |
| WorkManager (work-runtime) | 2.12.0 | 23.09.2026 | Google Maven (`work-runtime`; `work-runtime-ktx` ebenfalls 2.12.0 gelistet) |
| kotlinx-coroutines | 1.11.0 | 07.05.2026 | Maven Central |
| kotlinx-serialization-json | 1.12.0 | 19.10.2026 | Maven Central |
| JNA | 5.19.1 | 12.06.2026 | Maven Central (`net.java.dev.jna:jna`); für Android das AAR (`@aar`) verwenden |
| Robolectric | 4.17 | 10.09.2026 | Maven Central |
| ktlint-Gradle-Plugin (`org.jlleitschuh.gradle.ktlint`) | 14.2.0 | 12.03.2026 | Gradle Plugin Portal |
| uniffi (Rust) | 0.32.2 | 23.09.2026 | crates.io |
| rusqlite | 0.41.0 | 24.10.2026 | crates.io (Feature `bundled`) |
| reqwest | 0.13.5 | 08.09.2026 | crates.io |
| tokio | 1.54.1 | 21.10.2026 | crates.io |
| wiremock | 0.6.5 | 24.08.2025 | crates.io (seit über einem Jahr kein neues Release; Pflegestatus bei Verwendung beachten) |
| NDK | 30.0.16248370 (r30) | stabil gelistet am 28.10.2026 | `sdkmanager --list`; AGP 9.4 selbst nutzt standardmäßig 28.2.13676358, daher `ndkVersion` explizit setzen |

Anmerkungen:

- Alle Datumsangaben der Google- und Maven-Central-Artefakte stammen aus dem Last-Modified-Header der POM-Datei; das tatsächliche Release-Datum kann abweichen (Lifecycle 2.11.0 wirkt dadurch ungewöhnlich früh).
- Aufgrund der AGP-9-Neuerungen (integrierte Kotlin-Unterstützung, neue DSL) sollte beim Anlegen der Build-Dateien die jeweilige Migrationsdokumentation herangezogen werden. Das Kotlin-Gradle-Plugin ist nur dann separat zu deklarieren, wenn AGP es nicht selbst bereitstellt. Das wurde in dieser Vorprüfung nicht abschließend geklärt.
- Der Zustand der Kompatibilität Kotlin 2.4.21 mit AGP 9.4.1 wurde nicht getestet (der Probe-Build nutzte nur AGP ohne Kotlin-Code).

## 4. crunchyroll-rs: Entscheidung gegen die Bibliothek, eigene reqwest-Implementierung

Geprüft wurde ausschließlich der normale anonyme Login und Browse. Es wurden keine Optionen zum Umgehen von Cloudflare- oder Geo-Sperren verwendet (kein Proxy, kein Header-Spoofing, keine eigene Client-Konfiguration).

### 4.1 Eckdaten

- Version 0.19.1, veröffentlicht am 23.09.2026 (crates.io). Davor 0.19.0 (20.09.2026), 0.18.5 (15.09.2026): sehr aktive Pflege, aber mit schnellem Versionswechsel.
- Lizenz: MIT OR Apache-2.0. Mindest-Rust 1.88, Edition 2024.
- TLS: kein Feature `rustls-tls` nötig. Die Bibliothek bindet reqwest 0.13.5 mit Feature `rustls` immer ein. Features sind nur `parse` (Standard), `middleware`, `release-calendar`, `experimental-stabilizations`.
- Android-Build: `cargo ndk -t arm64-v8a build` im Wegwerf-Crate erfolgreich (NDK r30, Rust 1.99.1, ca. 1 Minute, Debug-Profil).

### 4.2 Konkrete API-Aufrufe (alle im Wegwerf-Crate ausgeführt)

```rust
use crunchyroll_rs::{Crunchyroll, Locale};
use crunchyroll_rs::auth::DeviceIdentifier;
use crunchyroll_rs::search::{BrowseOptions, BrowseSortType, SearchMediaCollection};

// Anonymer Login (wie Besuch ohne Konto)
let cr = Crunchyroll::builder()
    .locale(Locale::de_DE)
    .login_anonymously(DeviceIdentifier::default())
    .await?;

// Browse: Sortierung Popularität, Seitengröße 50
let mut page = cr.browse(BrowseOptions::default().sort(BrowseSortType::Popularity));
page.page_size(50);               // Anzahl pro Anfrage (Parameter n)
while let Some(item) = page.next().await { /* Stream<Item = Result<SearchMediaCollection>> */ }
```

Ergebnis des Laufs: Login erfolgreich, 50 Einträge geladen, `total` = 1686 (stimmt mit dem Spike-Ergebnis in Spec 5.3 überein).

Erreichbare Felder (Typ `Series`, per Deref aus `SearchSeries`):

| Bedarf | Feld / Aufruf | Befund |
|---|---|---|
| Audio-Locales | `series.audio_locales: Vec<Locale>` | vorhanden |
| Untertitel-Locales | `series.subtitle_locales: Vec<Locale>` | vorhanden |
| Bilder | `series.images.poster_tall` / `poster_wide: Vec<Image>` (`source`, `width`, `height`) | vorhanden (je 9 Größen) |
| Kategorien | `series.categories: Option<Vec<Category>>` (Quelle `tenant_categories`) | vorhanden |
| Locale der Texte | `CrunchyrollBuilder::locale(Locale)` bzw. intern `apply_locale_query()` | vorhanden |
| Sortierung | `BrowseOptions::sort(BrowseSortType::Popularity)` | vorhanden |
| Anzahl pro Seite | `Pagination::page_size(u32)` | vorhanden |
| Start-Offset | nicht setzbar | siehe 4.3 |
| Land der Sitzung | nicht als Methode; `AuthResponse.country` ist `pub(super)` und ungenutzt | nur über Umweg, siehe 4.3 |

### 4.3 Lücken und Entscheidungsgrund

1. **Kein wahlfreier Start-Offset.** `BrowseOptions` kennt kein `start`. `Pagination` beginnt immer bei 0 und zählt intern hoch (`options.start = this.count`). Ein Wiederaufsetzen an einer gespeicherten Position (Trait `CatalogSource::fetch_page(start, count, locale)` aus Spec 5.3, Fortsetzen nach Abbruch) ist damit nur möglich, indem alle vorherigen Seiten erneut abgerufen werden. Das widerspricht der geforderten Schnittstelle.
2. **Land der Sitzung nicht öffentlich.** Der Wert steckt im Login-Ergebnis, die Bibliothek gibt ihn aber nicht heraus. Als Umweg lässt sich der JWT aus `cr.access_token().await` selbst dekodieren (Claim `country`, im Test: `DE`). Das funktioniert, ist aber ein Zugriff auf das Token-Format ohne Zusicherung der Bibliothek.
3. **Zugangsdaten in der Bibliothek.** Die Bibliothek bringt ein fest einkompiliertes Basic-Auth-Token samt User-Agent einer Fremd-App (Android TV) mit; im Quelltext steht, dass Crunchyroll es regelmäßig rotiert und die Bibliothek dann kurzzeitig ungültig wird. Der vorgeschriebene Weg für den anonymen Login läuft über die Web-Plattform mit fest verdrahteten Werten. Der Kern wäre damit an die Release-Frequenz einer Drittbibliothek gekoppelt.
4. **Client-Konfiguration.** `predefined_client_builder()` setzt ein angepasstes TLS-Profil (feste Cipher-Suites, nur X25519). Das ist laut Dokumentation auf Cloudflare-Erkennung ausgerichtet. Die Spec schließt das Übernehmen solcher Maßnahmen aus (Spec 5.3: Hinweise zum Umgehen von Cloudflare-Sperren werden nicht übernommen). Eine eigene Implementierung vermeidet das von vornherein.
5. **Größe und Build-Zeit.** Die Bibliothek zieht u. a. `jsonwebtoken`, `dash-mpd`, `chrono`, `regex` mit; für Katalog-Browse wird nur ein Bruchteil benötigt.

Entscheidungsregel aus dem Plan: Nur wenn alles zutrifft, wird `crunchyroll-rs` verwendet. Punkt 1 (Start-Offset) trifft nicht zu. Entscheidung: **eigene reqwest-Implementierung** hinter dem Trait `CatalogSource`, nach dem Vorbild von `git show 1d5ca54:android/app/src/main/java/de/crunchymate/sync/CrunchyrollClient.kt`.

### 4.4 Vorgaben für die eigene Implementierung (aus Bibliothek und Altcode abgeleitet)

- Auth-Endpunkt wie im Altcode: `https://beta-api.crunchyroll.com/auth/v1/token`; Browse: `https://beta-api.crunchyroll.com/content/v2/discover/browse` mit `locale=de-DE&start={start}&n={count}&sort_by=popularity`. Die Bibliothek nutzt für Browse `https://www.crunchyroll.com/content/v2/discover/browse`; beide Hosts sind vor der Umsetzung gegen die Live-API zu prüfen (nicht verbindlich zugesagt).
- Land: Claim `country` aus dem JWT des anonymen Tokens lesen (Payload Base64-URL-dekodieren, JSON-Feld `country`); Wert in `sync_state` speichern.
- Felder der Browse-Antwort, die zu mappen sind: `id`, `title`, `description`, `audio_locales`, `subtitle_locales`, `images.poster_tall` / `poster_wide`, `tenant_categories`, `is_dubbed`, `is_subbed`, `series_launch_year`, `episode_count`, `season_count`.
- Bei Cloudflare-Block (HTTP 403 mit Block-Seite) nur Fehler melden, nicht umgehen.
- Der anonyme Login wurde nur mit der Bibliothek getestet; die Zugangsdaten des Altcodes sind unverändert zu prüfen.

## 5. Offene Punkte

- Kotlin 2.4.21 und AGP 9.4.1 zusammen mit Compose sind erst im Task zur Projektanlage zu verifizieren.
- Sudo wurde nicht benötigt; alle Installationen liegen im Home-Verzeichnis.
