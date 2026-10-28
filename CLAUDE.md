# CLAUDE.md

Arbeitsanleitung für Claude Code in diesem Repository. Details stehen in Spec und Plan; hier nur, was man zum Arbeiten braucht.

## Projekt

CrunchyMate ist eine Android-App (nur arm64-v8a, minSdk 34, targetSdk 37) zum Durchsuchen und Verfolgen des Crunchyroll-Katalogs, angereichert mit AniList-Daten. Fachlogik in Rust (`core/`, UniFFI), Oberfläche in Jetpack Compose (`app/`). Go-Server, Web-Frontend und das alte `android/` sind entfernt.

- Spec: `docs/superpowers/specs/2026-10-28-rust-core-native-ui-design.md`
- Plan: `docs/superpowers/plans/2026-10-28-rust-core-native-ui.md`

## Befehle

```bash
# Rust (Workspace-Root)
cargo fmt --check && cargo clippy --all-targets -- -D warnings
cargo test
cargo test -p crunchymate-core <testname>          # Einzeltest

# Android (Root)
./gradlew lint ktlintCheck testDebugUnitTest assembleDebug
./gradlew testDebugUnitTest --tests '<Klasse>'     # Einzeltest

# Gerätetests (baut, installiert mit -r, führt aus)
bash scripts/run-device-tests.sh
```

`assembleDebug` baut den Kern per cargo-ndk, erzeugt die UniFFI-Bindings und führt `checkNoNetworkClients` aus. Dafür brauchen Rust (rustup, Target `aarch64-linux-android`), cargo-ndk und das NDK aus `app/build.gradle.kts`.

## Architektur

- Rust-Kern hinter UniFFI. Module kommunizieren über Traits: `db/` (SQLite), `remote/` (Crunchyroll, AniList), `sync/`, `images/`, `query.rs`, Fassade `core_api.rs`. TLS über `webpki-roots`. Fehler: `CoreError` in Rust, `CoreException` mit Feld `detail` in Kotlin.
- Kotlin spricht den Kern ausschließlich über `CoreGateway` (`app/.../core/`); das Änderungssignal des Kerns löst Neuladen aus. Fehlertexte entstehen in `ErrorMessages.kt`, nicht im Kern.
- Sync läuft über WorkManager (`sync/`). Bilder werden beim Sync lokal als WebP mit SHA-256-Dateinamen abgelegt; die App lädt nur lokale Dateien. Netzwerkclients in Kotlin sind verboten (Gradle-Task `checkNoNetworkClients`).
- UI: `ui/` mit ViewModels je Bereich (Suche, Detail, Einstellungen), Grid mit drei festen Spalten, Inhaltssprache als Einstellung.
- Neue Kernfunktion = Rust-Export plus Gateway-Methode plus Fake in `FakeCore`.

## Verbindliche Regeln

- Keine Emojis in Code, Kommentaren, Ressourcen, Markdown und Commit-Nachrichten. Einzige Ausnahme: die Spalte „Emoji-Entsprechung“ in `docs/icons.md`.
- Icons sind VectorDrawables aus SVG-Dateien, die der Auftraggeber liefert; keine eigene Icon-Auswahl. Rohdateien liegen in `design/icons/ablage/` (nicht versioniert). Einbindung über den Projekt-Skill `/check-svg` (`.claude/skills/check-svg/`). Platzhalter dürfen gelieferte Icons nie überschreiben. Rastergrafiken nur als WebP.
- Ungenutzte Icons vollständig entfernen: Drawable, Eintrag in `docs/icons.md`, Eintrag in `docs/open-source-hinweise.md`, Quell-SVG in `design/icons/` und Original in `design/icons/originals`.
- Ändern sich Abhängigkeiten (Cargo oder Gradle), `docs/open-source-hinweise.md` mit `python3 scripts/gen-oss-notices.py` aktualisieren. Das Skript ersetzt nur den Abschnitt zwischen `<!-- oss:begin -->` und `<!-- oss:end -->` (aus `cargo metadata` und `./gradlew :app:dependencies`); Icon-, Flaggen- und Lizenztextabschnitte pflegt man von Hand. Das Skript liest sie zusammen mit den erzeugten Tabellen wieder ein und schreibt das App-Asset `app/src/main/assets/open_source_notices.json` (Lizenzbildschirm); die Markdown-Datei ist die einzige Quelle, das Asset wird nie von Hand bearbeitet. Das Asset gehört immer mit ins selbe Commit (`OpenSourceNoticesTest` prüft den Abgleich).
- UI-Texte stehen ausschließlich auf Deutsch in `strings.xml`.
- Einzel-APK ohne Splits.
- Code-Kommentare sparsam, auf Deutsch, und sie erklären das Warum.

## Gerät und Daten

- adb läuft aus WSL als `adb.exe`; Dateipfade mit `wslpath -w` umwandeln, z. B. `adb.exe install -r "$(wslpath -w app/build/outputs/apk/debug/app-debug.apk)"`. Auch per WLAN-Debugging: `adb.exe connect <ip:port>`.
- Echte App-Daten auf dem Telefon nie löschen: nur `install -r`, nie `uninstall` und nie `pm clear`. Die Gerätetests nutzen eine eigene Datenbank (`crunchymate-test.db`) und einen eigenen Bilderordner (`images-test`).

## Clean-Code-Leitlinien (Spec 9.5)

- Kleine Einheiten mit einem Zweck; Abhängigkeiten zeigen nach innen; Rust-Module kommunizieren über Traits, Kotlin über Interfaces.
- Aussagekräftige Namen.
- Keine Magic Numbers: Konfiguration in `SyncConfig` bzw. Konstanten mit Namen.
- Kein toter oder auskommentierter Code; keine `TODO`s ohne Issue.
