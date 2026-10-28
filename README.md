# CrunchyMate

> **Disclaimer:** CrunchyMate steht in keiner Verbindung zu Crunchyroll und wird von Crunchyroll weder unterstützt, gesponsert noch geprüft. Alle Markennamen gehören ihren jeweiligen Inhabern.

> **Vibe-coded:** Diese App ist mit KI-Assistenz entstanden, ohne klassische Code-Prüfung durch Menschen. Die Nutzung erfolgt auf eigenes Risiko.

CrunchyMate ist eine Android-App zum Durchsuchen, Filtern und Verfolgen des KnusperBrötchen-Anime-Katalogs. Der Katalog wird mit Bewertungen, Genres und Tags von AniList angereichert und lokal gespeichert. Die Fachlogik steckt in einem Rust-Kern (über UniFFI angebunden), die Oberfläche ist mit Jetpack Compose gebaut.

## Funktionsumfang

- Vier Bereiche mit unterer Navigation: Suche, Gesehen, Meh, Einstellungen.
- Volltextsuche über Titel und AniList-Titel; Filter nach Gesehen-Status, Genre und Fassung (Synchro, Untertitel); Sortierung nach Titel, AniList-Score, Episodenzahl und Erscheinungsjahr.
- Tag-Filter mit den Zuständen neutral, Boost, Muss und Ausgeschlossen.
- Kacheln im Raster mit Poster, Titel, Score und Flagge der Inhaltssprache; Detailansicht mit Banner, Beschreibung, Tags und AniList-Bereich.
- Anime als gesehen oder als Meh markieren; Meh-Einträge verschwinden aus der Suche.
- KnusperBrötchen-Sync und AniList-Anreicherung mit Fortschritt, Abbruch und Hintergrundausführung über WorkManager.
- Wählbare Inhaltssprache für Titel und Beschreibungen.
- App-Sprachen Deutsch, Englisch und Boykisser (englisch, im Stil von Discord-Femboys); Auswahl in den Einstellungen, Standard ist die Systemsprache.
- Offline-first: Poster und Banner werden beim Sync als WebP lokal abgelegt, die App lädt keine Bilder aus dem Netz.

Der Katalog hängt am Land, das im anonymen KnusperBrötchen-Token festgelegt ist; die Inhaltssprache ändert nur die Texte.

## Installation

Das Nightly-Release auf GitHub enthält die signierte APK (`app-release.apk`). Zum Installieren die Datei auf das Gerät laden und öffnen oder per adb installieren:

```bash
adb install -r app-release.apk
```

Voraussetzung ist ein Gerät mit Android 14 (SDK 34) oder neuer und arm64-v8a. Mit `-r` bleiben vorhandene App-Daten erhalten. Updates funktionieren nur, wenn sie mit demselben Schlüssel signiert sind (siehe `docs/release-signing.md`).

## Lokal bauen

Voraussetzungen:

- Rust über rustup; `rust-toolchain.toml` wählt die stabile Toolchain und das Target `aarch64-linux-android`
- cargo-ndk: `cargo install cargo-ndk --locked`
- JDK 17
- Android SDK mit Plattform 37.2, Build-Tools 37.0.0 und dem NDK aus `gradle.properties` (`crunchymate.ndkVersion`)

```bash
./gradlew assembleDebug
```

Die APK liegt danach unter `app/build/outputs/apk/debug/app-debug.apk`. Gradle baut den Rust-Kern dabei selbst und erzeugt die Kotlin-Bindings.

## Release-Builds

Gradle legt die Signing-Konfiguration `release` nur an, wenn die Umgebungsvariable `SIGNING_KEYSTORE_PATH` gesetzt ist (siehe `app/build.gradle.kts`); ohne sie entsteht eine unsignierte APK. Gradle-Properties sind dafür nicht vorgesehen, es zählen ausschließlich diese Umgebungsvariablen:

```bash
export SIGNING_KEYSTORE_PATH=/pfad/zu/release.jks
export SIGNING_STORE_PASSWORD=...
export SIGNING_KEY_PASSWORD=...
export SIGNING_KEY_ALIAS=crunchymate
./gradlew assembleRelease
```

Die APK liegt danach unter `app/build/outputs/apk/release/app-release.apk`. Schlüsselerzeugung, Secrets und Prüfung mit `apksigner` stehen in `docs/release-signing.md`.

Der Workflow `nightly.yml` baut täglich um 03:00 UTC (nur bei neuen Commits) eine signierte APK und veröffentlicht sie als Pre-Release `nightly` auf GitHub.

## Tests und Prüfungen

```bash
cargo fmt --check && cargo clippy --all-targets -- -D warnings
cargo test
./gradlew lint ktlintCheck testDebugUnitTest
bash scripts/run-device-tests.sh
```

`scripts/run-device-tests.sh` baut App und Test-APK, installiert beide mit `adb install -r` auf einem angeschlossenen Gerät und führt die Smoke-Tests aus. Sie nutzen eine eigene Datenbank und einen eigenen Bilderordner, vorhandene Nutzerdaten bleiben unberührt. Live-Tests gegen KnusperBrötchen (`core/tests/crispybun_live.rs`) sind mit `#[ignore]` markiert und laufen nur auf Anforderung.

## Lizenz

CrunchyMate steht unter der Apache License 2.0, siehe `LICENSE`. Die Lizenzen der verwendeten Bibliotheken, Icons und Flaggen stehen in `docs/open-source-hinweise.md` und in der App unter Einstellungen.

## Weitere Dokumentation

- `docs/superpowers/specs/2026-10-28-rust-core-native-ui-design.md`: Entwurf und Architektur
- `docs/release-signing.md`: Release-Signierung und GitHub-Secrets
- `docs/icons.md`: Liste der Icons, Quellen und Lizenzen
- `docs/open-source-hinweise.md`: Lizenzen der verwendeten Bibliotheken, Icons und Flaggen
- `CLAUDE.md`: Arbeitsanleitung für Claude Code
