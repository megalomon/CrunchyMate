# Icons für CrunchyMate

Diese Liste beschreibt alle Symbole, die die App benötigt. Die konkreten Motive wählt der Auftraggeber aus. Bis die Dateien vorliegen, verwendet die App neutrale Platzhalter mit denselben Dateinamen.

> Ausnahme: Die Spalte „Emoji-Entsprechung“ dient nur der Orientierung bei der Motivsuche. Es ist die einzige Stelle im Projekt, an der Emojis stehen dürfen. In der App, im Code und in der übrigen Dokumentation bleiben Emojis verboten.

## Anforderungen an die Dateien

- **Format:** SVG (wird in ein Android-VectorDrawable umgewandelt). Rastergrafiken nur, wenn es nicht anders geht, dann als WebP.
- **Normale Icons:** einfarbig, quadratisch, 24-px-Raster, ohne festen Hintergrund. Die Farbe setzt die App über das Theme.
- **Benachrichtigungs-Icon:** reine Silhouette, weiß auf transparentem Grund, ohne Details, die bei 24 px verschwinden.
- **App-Icon (Adaptive Icon):** Vordergrund und Hintergrund als getrennte Dateien, je 108 × 108 dp; das Motiv muss vollständig in der mittleren Fläche von 66 × 66 dp liegen (der Rand wird je nach Gerät abgeschnitten). Zusätzlich eine einfarbige Variante für „Themed Icons“.
- **Dateinamen:** Die Namen unten sind Vorschläge. Gelieferte Dateien dürfen beliebig heißen; trage den Namen in der Spalte „Gelieferte Datei“ ein.
- **Lizenz:** Bitte nur Icons mit einer Lizenz wählen, die die Verwendung in einer App erlaubt (z. B. Apache-2.0, MIT, CC0, CC BY mit Namensnennung), und die Quelle in der Spalte „Quelle/Lizenz“ notieren.

Tipp: Einige Motive können doppelt verwendet werden, etwa `ic_nav_watched` für `ic_stat_watched` und `ic_nav_meh` für `ic_stat_meh`. Für An/Aus-Paare reicht oft dieselbe Form gefüllt und umrandet.

## Navigation

| Datei | Bedeutung | Emoji-Entsprechung | Gelieferte Datei | Quelle/Lizenz |
|---|---|---|---|---|
| `ic_nav_search` | Tab „Suche“: Katalog durchsuchen und filtern | 🔍 | `magnify.svg` | Pictogrammers Material Design Icons, https://pictogrammers.com/library/mdi/icon/magnify/; Lizenz unbekannt, bitte nachtragen |
| `ic_nav_watched` | Tab „Gesehen“: Liste aller als gesehen markierten Anime | 👁️ | | |
| `ic_nav_meh` | Tab „Meh“: Liste der ausgeblendeten, unerwünschten Anime | 😒 | | |
| `ic_nav_settings` | Tab „Einstellungen“: Sync, Sprache, Statistik | ⚙️ | | |

## Kachel und Detailansicht

| Datei | Bedeutung | Emoji-Entsprechung | Gelieferte Datei | Quelle/Lizenz |
|---|---|---|---|---|
| `ic_watched_on` | Anime ist als gesehen markiert (Tippen entfernt die Markierung) | ✅ | | |
| `ic_watched_off` | Anime ist nicht gesehen (Tippen markiert als gesehen) | ⬜ | | |
| `ic_meh_on` | Anime ist als „Meh“ markiert und in der Suche ausgeblendet (Tippen stellt wieder her) | 😒 | | |
| `ic_meh_off` | Anime ist nicht als „Meh“ markiert (Tippen blendet ihn aus) | 😐 | | |

## Badges auf Kachel und Detailansicht

| Datei | Bedeutung | Emoji-Entsprechung | Gelieferte Datei | Quelle/Lizenz |
|---|---|---|---|---|
| `ic_badge_score` | AniList-Bewertung in Prozent | ⭐ | | |
| `ic_badge_dub` | Synchronfassung in der gewählten Inhaltssprache vorhanden | 🎙️ | | |
| `ic_badge_sub` | Untertitel in der gewählten Inhaltssprache vorhanden | 💬 | | |
| `ic_badge_boost` | Anzahl der Treffer bei bevorzugten (Boost-)Tags | 🔵 | | |

## Tag-Zustände

Jeder Tag wird per Tippen durchgeschaltet: neutral → Boost → Muss → Ausgeschlossen → neutral. Die Zustände sind farbig und zusätzlich per Icon unterscheidbar (Barrierefreiheit).

| Datei | Bedeutung | Emoji-Entsprechung | Gelieferte Datei | Quelle/Lizenz |
|---|---|---|---|---|
| `ic_tag_prefer` | Boost: Anime mit diesem Tag werden weiter oben einsortiert | 🔵 | | |
| `ic_tag_must` | Muss: nur Anime mit diesem Tag werden angezeigt | 🟢 | | |
| `ic_tag_exclude` | Ausgeschlossen: Anime mit diesem Tag werden ausgeblendet | 🟠 | | |

## Aktionen

| Datei | Bedeutung | Emoji-Entsprechung | Gelieferte Datei | Quelle/Lizenz |
|---|---|---|---|---|
| `ic_clear` | Eingabe im Suchfeld löschen | ✖️ | | |
| `ic_tags_reset` | Alle Tag-Filter zurücksetzen | 🔄 | | |
| `ic_sync_start` | Synchronisation starten | ▶️ | | |
| `ic_sync_cancel` | Laufende Synchronisation abbrechen | ⏹️ | | |
| `ic_open_external` | Seite auf AniList im Browser öffnen | ↗️ | | |
| `ic_dropdown` | Auswahlliste eines Filter-Chips aufklappen | 🔽 | | |
| `ic_back` | Zurück aus der Detailansicht | ⬅️ | | |

## Zustände

| Datei | Bedeutung | Emoji-Entsprechung | Gelieferte Datei | Quelle/Lizenz |
|---|---|---|---|---|
| `ic_state_empty` | Keine Ergebnisse oder noch keine Daten (Hinweis auf den Sync) | 📭 | | |
| `ic_state_error` | Laden fehlgeschlagen | ⚠️ | | |

## Statistik (Einstellungen)

| Datei | Bedeutung | Emoji-Entsprechung | Gelieferte Datei | Quelle/Lizenz |
|---|---|---|---|---|
| `ic_stat_total` | Anzahl aller Anime im Katalog | 📚 | | |
| `ic_stat_watched` | Anzahl gesehener Anime | 👁️ | | |
| `ic_stat_meh` | Anzahl als „Meh“ markierter Anime | 😒 | | |
| `ic_stat_enriched` | Anzahl der mit AniList-Daten angereicherten Anime | ✨ | | |
| `ic_stat_tags` | Anzahl bekannter Tags | 🏷️ | | |

## System

| Datei | Bedeutung | Emoji-Entsprechung | Gelieferte Datei | Quelle/Lizenz |
|---|---|---|---|---|
| `ic_notification_sync` | Benachrichtigung während einer laufenden Synchronisation (Statusleiste) | 🔄 | | |
| `ic_launcher_foreground` | App-Icon, Vordergrund (Motiv) | 🍿 | | |
| `ic_launcher_background` | App-Icon, Hintergrund (Fläche oder Muster, z. B. Crunchyroll-Orange) | 🟧 | | |
| `ic_launcher_monochrome` | App-Icon, einfarbige Variante für Themed Icons | 🍿 | | |

## Umwandlung SVG in VectorDrawable

Die Umwandlung übernimmt die Entwicklung (Android Studio „Vector Asset“ bzw. `vd-tool`). Gelieferte SVGs können dafür einfach im Ordner `design/icons/ablage/` abgelegt werden (dessen Inhalt wird nicht versioniert) und mit `/check-svg <name> design/icons/ablage/<datei>.svg` eingebunden werden.
