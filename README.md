# CrunchyMate

CrunchyMate ist eine Android-App zum Durchsuchen, Filtern und Verfolgen des Crunchyroll-Anime-Katalogs. Der Katalog wird mit Bewertungen, Genres und Tags von AniList angereichert und lokal gespeichert. Die Fachlogik steckt in einem Rust-Kern (über UniFFI angebunden), die Oberfläche ist mit Jetpack Compose gebaut.

## Funktionsumfang

- Vier Bereiche mit unterer Navigation: Suche, Gesehen, Meh, Einstellungen.
- Volltextsuche über Titel und AniList-Titel; Filter nach Gesehen-Status, Genre und Fassung (Synchro, Untertitel); Sortierung nach Titel, AniList-Score, Episodenzahl und Erscheinungsjahr.
- Tag-Filter mit den Zuständen neutral, Boost, Muss und Ausgeschlossen.
- Kacheln im Raster mit Poster, Titel, Score und Flagge der Inhaltssprache; Detailansicht mit Banner, Beschreibung, Tags und AniList-Bereich.
- Anime als gesehen oder als Meh markieren; Meh-Einträge verschwinden aus der Suche.
- Crunchyroll-Sync und AniList-Anreicherung mit Fortschritt, Abbruch und Hintergrundausführung über WorkManager.
- Wählbare Inhaltssprache für Titel und Beschreibungen.
- Offline-first: Poster und Banner werden beim Sync als WebP lokal abgelegt, die App lädt keine Bilder aus dem Netz.

Der Katalog hängt am Land, das im anonymen Crunchyroll-Token festgelegt ist; die Inhaltssprache ändert nur die Texte.

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
- Android SDK mit Plattform 37.2, Build-Tools 37.0.0 und NDK 30.0.16248370

```bash
./gradlew assembleDebug
```

Die APK liegt danach unter `app/build/outputs/apk/debug/app-debug.apk`. Gradle baut den Rust-Kern dabei selbst und erzeugt die Kotlin-Bindings. Eine signierte Release-APK entsteht lokal wie in `docs/release-signing.md` beschrieben.

## Tests und Prüfungen

```bash
cargo fmt --check && cargo clippy --all-targets -- -D warnings
cargo test
./gradlew lint ktlintCheck testDebugUnitTest
bash scripts/run-device-tests.sh
```

`scripts/run-device-tests.sh` baut App und Test-APK, installiert beide mit `adb install -r` auf einem angeschlossenen Gerät und führt die Smoke-Tests aus. Sie nutzen eine eigene Datenbank und einen eigenen Bilderordner, vorhandene Nutzerdaten bleiben unberührt. Live-Tests gegen Crunchyroll (`core/tests/crunchyroll_live.rs`) sind mit `#[ignore]` markiert und laufen nur auf Anforderung.

## Weitere Dokumentation

- `docs/superpowers/specs/2026-10-28-rust-core-native-ui-design.md`: Entwurf und Architektur
- `docs/release-signing.md`: Release-Signierung und GitHub-Secrets
- `docs/icons.md`: Liste der Icons, Quellen und Lizenzen
- `docs/open-source-hinweise.md`: Lizenzen der verwendeten Bibliotheken, Icons und Flaggen
- `CLAUDE.md`: Arbeitsanleitung für Claude Code
