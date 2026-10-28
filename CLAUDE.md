# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Stand des Projekts

CrunchyMate wird zu einer Android-App mit Rust-Kern (UniFFI) und Jetpack-Compose-Oberfläche umgebaut. Maßgeblich sind:

- Spec: `docs/superpowers/specs/2026-10-28-rust-core-native-ui-design.md`
- Plan: `docs/superpowers/plans/2026-10-28-rust-core-native-ui.md`

Altbestand: Der Go-Server und das Web-Frontend sind entfernt; das Verzeichnis `android/` ist Referenz und wird im Verlauf des Plans ersetzt. Neuer Code entsteht in `core/` (Rust) und später in `app/` (Kotlin).

## Verbindliche Regeln

- Keine Emojis in Code, Kommentaren, Ressourcen, Markdown und Commit-Nachrichten. Einzige Ausnahme: die Spalte „Emoji-Entsprechung“ in `docs/icons.md`.
- Icons sind VectorDrawables aus SVG-Dateien, die der Auftraggeber liefert; keine eigene Icon-Auswahl. Rohdateien liegen in `design/icons/ablage/` (nicht versioniert). Einbindung über den Projekt-Skill `/check-svg` (`.claude/skills/check-svg/`). Platzhalter dürfen gelieferte Icons nie überschreiben. Rastergrafiken nur als WebP.
- UI-Texte stehen ausschließlich auf Deutsch in `strings.xml`.
- Einzel-APK ohne Splits.
- Code-Kommentare sparsam, auf Deutsch, und sie erklären das Warum.

## Clean-Code-Leitlinien (Spec 9.5)

- Kleine Einheiten mit einem Zweck; Abhängigkeiten zeigen nach innen; Rust-Module kommunizieren über Traits, Kotlin über Interfaces.
- Aussagekräftige Namen.
- Keine Magic Numbers: Konfiguration in `SyncConfig` bzw. Konstanten mit Namen.
- Kein toter oder auskommentierter Code; keine `TODO`s ohne Issue.
