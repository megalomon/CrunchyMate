# Vorprüfung: Toolchain, SDK 37.2 und Drittanbieter-Crate

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
        minSdk = 34
        targetSdk = 37   // targetSdk kennt nur die Hauptversion
    }
}
```

Rückfallebene, falls 37.2 in CI nicht bereitsteht: `compileSdk = 37` (entspricht `platforms;android-37.0`). In CI muss die Paket-ID `platforms;android-37.2` per `sdkmanager` installiert werden (Lizenzen akzeptieren).

Optional: `buildToolsVersion = "37.0.0"` setzen, sonst nimmt AGP 9.4 Build-Tools 36.0.0.

## 2a. Java-Toolchain 17 und Gradle-Wrapper

- Vorgabe: Java-Toolchain 17 (`kotlin { jvmToolchain(17) }`), Gradle-Wrapper 9.8.2 (`gradle wrapper --gradle-version 9.8.2`; AGP 9.4 verlangt mindestens Gradle 9.6.0).
- In WSL ist ein JDK 17 bereits vorhanden: Eclipse Temurin 17.0.21+9 unter `/usr/lib/jvm/temurin-17-jdk-amd64` (daneben Temurin 8, 11, 21 und 25). Gradle erkennt es über die automatische Erkennung („Common Linux Locations“, geprüft mit `gradle javaToolchains`). Eine zusätzliche Installation war nicht nötig.
- Für CI und frische Rechner: Foojay-Resolver in `settings.gradle.kts` (`id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"`, Stand 28.10.2026 stabil) lässt Gradle ein fehlendes JDK 17 selbst laden. In GitHub Actions zusätzlich `actions/setup-java` mit Java 17 verwenden.
- Gradle selbst läuft unter JDK 25 (Standard-`java` in WSL); AGP 9.4 und der Probe-Build funktionierten damit.

## 2b. Kotlin mit AGP 9.4.1: integrierte Kotlin-Unterstützung (geprüft)

Ergebnis: Mit AGP 9.4.1 wird das Kotlin-Android-Plugin **nicht** separat deklariert (`org.jetbrains.kotlin.android` entfällt, AGP bringt die Kotlin-Unterstützung mit). Das Compose-Compiler-Plugin und `kotlinx-serialization` werden als Kotlin-Plugins mit Version 2.4.21 deklariert. KSP ist nicht nötig, solange kein Room/Hilt o. Ä. genutzt wird (Datenhaltung liegt in Rust).

Geprüft im Wegwerf-Projekt (Scratchpad, nicht eingecheckt) mit Gradle 9.8.2, Foojay 1.0.0: minimale Compose-Activity mit `@Serializable`-Klasse, compileSdk 37.2, minSdk 34, targetSdk 37; `gradle :app:assembleDebug` endete mit `BUILD SUCCESSFUL` und erzeugte `app-debug.apk`. Der Kotlin-Quelltext in `src/main/kotlin` wurde ohne `kotlin.android`-Plugin kompiliert.

Funktionierender Plugin-Block (Wurzel `build.gradle.kts`):

```kotlin
plugins {
    id("com.android.application") version "9.4.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.21" apply false
}
```

Modul `app/build.gradle.kts`:

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "probe.sdk"
    compileSdk { version = release(37) { minorApiLevel = 2 } }
    defaultConfig { minSdk = 34; targetSdk = 37 }
    buildFeatures { compose = true }
}

kotlin { jvmToolchain(17) }
```

Im Projekt werden die Plugin-IDs über `libs.versions.toml` bezogen (siehe 3a). Der Build lief mit einem Deprecation-Hinweis („incompatible with Gradle 10“) ohne Fehler; er ist für Gradle 9.8.2 unkritisch.

Nicht getestet: ktlint-Plugin und Robolectric im Probe-Projekt.

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
- Kotlin 2.4.21 mit AGP 9.4.1 und Compose wurde im Probe-Projekt geprüft, siehe Abschnitt 2b.

## 3a. Direkt übernehmbare Zeilen

`gradle/libs.versions.toml` (Koordinaten aus den abgefragten Repositories; Plugin-IDs und Artefaktnamen der Maven-Koordinaten wurden gegen die Metadaten geprüft, nicht jede einzelne Bibliothek wurde gebaut):

```toml
[versions]
agp = "9.4.1"
kotlin = "2.4.21"
composeBom = "2026.09.00"
activityCompose = "1.13.0"
navigationCompose = "2.10.2"
lifecycle = "2.11.0"
pagingCompose = "3.5.3"
coil = "3.6.3"
work = "2.12.0"
coroutines = "1.11.0"
serialization = "1.12.0"
jna = "5.19.1"
robolectric = "4.17"
ktlintGradle = "14.2.0"

[libraries]
androidx-compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
androidx-compose-material3 = { module = "androidx.compose.material3:material3" }
androidx-compose-ui = { module = "androidx.compose.ui:ui" }
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
androidx-navigation-compose = { module = "androidx.navigation:navigation-compose", version.ref = "navigationCompose" }
androidx-lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-paging-compose = { module = "androidx.paging:paging-compose", version.ref = "pagingCompose" }
androidx-work-runtime = { module = "androidx.work:work-runtime", version.ref = "work" }
coil-compose = { module = "io.coil-kt.coil3:coil-compose", version.ref = "coil" }
coil-network-okhttp = { module = "io.coil-kt.coil3:coil-network-okhttp", version.ref = "coil" }
kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "serialization" }
jna = { module = "net.java.dev.jna:jna", version.ref = "jna" }
robolectric = { module = "org.robolectric:robolectric", version.ref = "robolectric" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ktlint = { id = "org.jlleitschuh.gradle.ktlint", version.ref = "ktlintGradle" }
```

Für JNA unter Android wird das AAR gebraucht: `implementation("net.java.dev.jna:jna:5.19.1@aar")` bzw. `libs.jna` mit `artifact`-Angabe in der Abhängigkeitszeile (`implementation(variantOf(libs.jna) { artifactType("aar") })`). Die Existenz der AAR-Variante bei Maven Central wurde hier nicht einzeln geprüft. Die Coil-Artefakte `coil-network-okhttp` und die Lifecycle-Artefakte wurden nur über Modulnamen und die gemeinsame Versionsnummer abgeleitet.

`Cargo.toml` des Rust-Kerns (Abschnitte `[dependencies]` und `[dev-dependencies]`; erzeugt mit `cargo add` und geprüft):

```toml
[dependencies]
async-trait = "0.1.92"
regex = "1.13.1"
reqwest = { version = "0.13.5", default-features = false, features = ["rustls", "json"] }
rusqlite = { version = "0.41.0", features = ["bundled"] }
serde = { version = "1.0.229", features = ["derive"] }
serde_json = "1.0.154"
thiserror = "2.0.21"
tokio = { version = "1.54.1", features = ["rt-multi-thread", "macros", "time", "sync"] }
uniffi = { version = "0.32.2", features = ["tokio", "cli"] }

[dev-dependencies]
wiremock = "0.6.5"
```

Prüfung: Ein Wegwerf-Crate mit genau diesen Abhängigkeiten baut mit `cargo ndk -t arm64-v8a build` (NDK r30, Rust 1.99.1) erfolgreich, mit und ohne das uniffi-Feature `cli`. Hinweise:

- reqwest 0.13 zieht mit Feature `rustls` den Krypto-Anbieter aws-lc-rs; er baut mit dem NDK. `default-features = false` vermeidet native-tls/OpenSSL (`openssl-sys` kommt im Baum nicht vor).
- uniffi: `tokio` aktiviert die async-Unterstützung, `cli` das Bindgen (`uniffi-bindgen`-Binary). Für die Bibliothek selbst reicht `tokio`; `cli` kann auf ein Feature des Host-Bindgen-Binaries beschränkt werden, damit die Android-Bibliothek kleiner bleibt. Das Binary ist ein eigenes `[[bin]]` mit `uniffi::uniffi_bindgen_main()` (Vorgehen laut UniFFI-Dokumentation, in dieser Vorprüfung nicht ausgeführt).
- Für Build-Skripte `uniffi` ggf. zusätzlich mit Feature `build` als `[build-dependencies]` einbinden, falls UDL statt Proc-Makros verwendet wird (Spec nutzt Proc-Makros, daher voraussichtlich nicht nötig).
- Die tatsächlichen Tokio-Features sind für den Kern ausreichend gewählt (`rt-multi-thread`, `macros`, `time`, `sync`); `net` zieht reqwest selbst nach.

## 4. Drittanbieter-Rust-Crate für die KnusperBrötchen-API: Entscheidung gegen die Bibliothek, eigene reqwest-Implementierung

Geprüft wurde ein frei verfügbares Rust-Crate eines Drittanbieters (im Folgenden „das Crate“); sein Name steht hier bewusst nicht.

Geprüft wurde ausschließlich der normale anonyme Login und Browse. Es wurden keine Optionen zum Umgehen von Cloudflare- oder Geo-Sperren verwendet (kein Proxy, kein Header-Spoofing, keine eigene Client-Konfiguration).

### 4.1 Eckdaten

- Version 0.19.1, veröffentlicht am 23.09.2026 (crates.io). Davor 0.19.0 (20.09.2026), 0.18.5 (15.09.2026): sehr aktive Pflege, aber mit schnellem Versionswechsel.
- Lizenz: MIT OR Apache-2.0. Mindest-Rust 1.88, Edition 2024.
- TLS: kein Feature `rustls-tls` nötig. Die Bibliothek bindet reqwest 0.13.5 mit Feature `rustls` immer ein. Features sind nur `parse` (Standard), `middleware`, `release-calendar`, `experimental-stabilizations`.
- Android-Build: `cargo ndk -t arm64-v8a build` im Wegwerf-Crate erfolgreich (NDK r30, Rust 1.99.1, ca. 1 Minute, Debug-Profil).

### 4.2 Konkrete API-Aufrufe (alle im Wegwerf-Crate ausgeführt)

```rust
use drittanbieter_crate::{Client, Locale};
use drittanbieter_crate::auth::DeviceIdentifier;
use drittanbieter_crate::search::{BrowseOptions, BrowseSortType, SearchMediaCollection};

// Anonymer Login (wie Besuch ohne Konto)
let cr = Client::builder()
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
| Locale der Texte | `ClientBuilder::locale(Locale)` bzw. intern `apply_locale_query()` | vorhanden |
| Sortierung | `BrowseOptions::sort(BrowseSortType::Popularity)` | vorhanden |
| Anzahl pro Seite | `Pagination::page_size(u32)` | vorhanden |
| Start-Offset | nicht setzbar | siehe 4.3 |
| Land der Sitzung | nicht als Methode; `AuthResponse.country` ist `pub(super)` und ungenutzt | nur über Umweg, siehe 4.3 |

### 4.3 Lücken und Entscheidungsgrund

1. **Kein wahlfreier Start-Offset.** `BrowseOptions` kennt kein `start`. `Pagination` beginnt immer bei 0 und zählt intern hoch (`options.start = this.count`). Ein Wiederaufsetzen an einer gespeicherten Position (Trait `CatalogSource::fetch_page(start, count, locale)` aus Spec 5.3, Fortsetzen nach Abbruch) ist damit nur möglich, indem alle vorherigen Seiten erneut abgerufen werden. Das widerspricht der geforderten Schnittstelle.
2. **Land der Sitzung nicht öffentlich.** Der Wert steckt im Login-Ergebnis, die Bibliothek gibt ihn aber nicht heraus. Als Umweg lässt sich der JWT aus `cr.access_token().await` selbst dekodieren (Claim `country`, im Test: `DE`). Das funktioniert, ist aber ein Zugriff auf das Token-Format ohne Zusicherung der Bibliothek.
3. **Zugangsdaten in der Bibliothek.** Die Bibliothek bringt ein fest einkompiliertes Basic-Auth-Token samt User-Agent einer Fremd-App (Android TV) mit; im Quelltext steht, dass KnusperBrötchen es regelmäßig rotiert und die Bibliothek dann kurzzeitig ungültig wird. Der vorgeschriebene Weg für den anonymen Login läuft über die Web-Plattform mit fest verdrahteten Werten. Der Kern wäre damit an die Release-Frequenz einer Drittbibliothek gekoppelt.
4. **Client-Konfiguration.** `predefined_client_builder()` setzt ein angepasstes TLS-Profil (feste Cipher-Suites, nur X25519). Das ist laut Dokumentation auf Cloudflare-Erkennung ausgerichtet. Die Spec schließt das Übernehmen solcher Maßnahmen aus (Spec 5.3: Hinweise zum Umgehen von Cloudflare-Sperren werden nicht übernommen). Eine eigene Implementierung vermeidet das von vornherein.
5. **Größe und Build-Zeit.** Die Bibliothek zieht u. a. `jsonwebtoken`, `dash-mpd`, `chrono`, `regex` mit; für Katalog-Browse wird nur ein Bruchteil benötigt.

Entscheidungsregel aus dem Plan: Nur wenn alles zutrifft, wird das Crate verwendet. Punkt 1 (Start-Offset) trifft nicht zu. Entscheidung: **eigene reqwest-Implementierung** hinter dem Trait `CatalogSource`, nach dem Vorbild des Katalog-Clients im Verzeichnis `sync/` von `git show 1d5ca54:android/app/src/main/java/de/crunchymate/sync/` (Verzeichnisliste; der Client ist dort die Datei mit dem Dienstnamen im Dateinamen).

### 4.4 Vorgaben für die eigene Implementierung (aus Bibliothek und Altcode abgeleitet)

- Auth-Endpunkt wie im Altcode: `<API-Basis-URL>/auth/v1/token`; Browse: `<API-Basis-URL>/content/v2/discover/browse` mit `locale=de-DE&start={start}&n={count}&sort_by=popularity`. Die Bibliothek nutzt für Browse `<API-Basis-URL>/content/v2/discover/browse`; beide Hosts sind vor der Umsetzung gegen die Live-API zu prüfen (nicht verbindlich zugesagt).
- Land: Claim `country` aus dem JWT des anonymen Tokens lesen (Payload Base64-URL-dekodieren, JSON-Feld `country`); Wert in `sync_state` speichern.
- Felder der Browse-Antwort, die zu mappen sind: `id`, `title`, `description`, `audio_locales`, `subtitle_locales`, `images.poster_tall` / `poster_wide`, `tenant_categories`, `is_dubbed`, `is_subbed`, `series_launch_year`, `episode_count`, `season_count`.
- Bei Cloudflare-Block (HTTP 403 mit Block-Seite) nur Fehler melden, nicht umgehen.
- Der anonyme Login wurde nur mit der Bibliothek getestet; die Zugangsdaten des Altcodes sind unverändert zu prüfen.

## 5. Offene Punkte

- Sudo wurde nicht benötigt; alle Installationen liegen im Home-Verzeichnis.
