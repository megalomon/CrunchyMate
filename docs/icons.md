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
| `ic_nav_search` | Tab „Suche“: Katalog durchsuchen und filtern | 🔍 | `magnify.svg` | Pictogrammers Material Design Icons, https://pictogrammers.com/library/mdi/icon/magnify/; Apache 2.0 (Pictogrammers Free License) |
| `ic_nav_watched` | Tab „Gesehen“: Liste aller als gesehen markierten Anime | 👁️ | `eye-check-outline.svg` | Pictogrammers Material Design Icons, https://pictogrammers.com/library/mdi/icon/eye-check-outline/; Apache 2.0 (Pictogrammers Free License) |
| `ic_nav_meh` | Tab „Meh“: Liste der ausgeblendeten, unerwünschten Anime | 😒 | `emoticon-confused.svg` | Pictogrammers Material Design Icons, https://pictogrammers.com/library/mdi/icon/emoticon-confused/; Apache 2.0 (Pictogrammers Free License) |
| `ic_nav_settings` | Tab „Einstellungen“: Sync, Sprache, Statistik | ⚙️ | `cog-outline.svg` | Pictogrammers Material Design Icons, https://pictogrammers.com/library/mdi/icon/cog-outline/; Apache 2.0 (Pictogrammers Free License) |

## Kachel und Detailansicht

| Datei | Bedeutung | Emoji-Entsprechung | Gelieferte Datei | Quelle/Lizenz |
|---|---|---|---|---|
| `ic_watched_on` | Anime ist als gesehen markiert (Tippen entfernt die Markierung) | ✅ | `eye-check-outline.svg` | Pictogrammers Material Design Icons, https://pictogrammers.com/library/mdi/icon/eye-check-outline/; Apache 2.0 (Pictogrammers Free License) |
| `ic_watched_off` | Anime ist nicht gesehen (Tippen markiert als gesehen) | ⬜ | `eye-off-outline.svg` | Pictogrammers Material Design Icons, https://pictogrammers.com/library/mdi/icon/eye-off-outline/; Apache 2.0 (Pictogrammers Free License) |
| `ic_meh_on` | Anime ist als „Meh“ markiert und in der Suche ausgeblendet (Tippen stellt wieder her) | 😒 | `emoticon-confused.svg` | Pictogrammers Material Design Icons, https://pictogrammers.com/library/mdi/icon/emoticon-confused/; Apache 2.0 (Pictogrammers Free License) |
| `ic_meh_off` | Anime ist nicht als „Meh“ markiert (Tippen blendet ihn aus) | 😐 | `emoticon-confused-outline.svg` | Pictogrammers Material Design Icons, https://pictogrammers.com/library/mdi/icon/emoticon-confused-outline/; Apache 2.0 (Pictogrammers Free License) |

## Badges auf Kachel und Detailansicht

| Datei | Bedeutung | Emoji-Entsprechung | Gelieferte Datei | Quelle/Lizenz |
|---|---|---|---|---|
| `ic_badge_score` | AniList-Bewertung in Prozent | ⭐ | `star-face.svg` | Pictogrammers Material Design Icons, https://pictogrammers.com/library/mdi/icon/star-face/; Apache 2.0 (Pictogrammers Free License) |

## Tag-Zustände

Jeder Tag wird per Tippen durchgeschaltet: neutral → Boost → Muss → Ausgeschlossen → neutral. Die Zustände sind farbig und zusätzlich per Icon unterscheidbar (Barrierefreiheit).

| Datei | Bedeutung | Emoji-Entsprechung | Gelieferte Datei | Quelle/Lizenz |
|---|---|---|---|---|
| `ic_tag_prefer` | Boost: Anime mit diesem Tag werden weiter oben einsortiert | 🔵 | `circle.svg` | Pictogrammers Material Design Icons, https://pictogrammers.com/library/mdi/icon/circle/; Apache 2.0 (Pictogrammers Free License) |
| `ic_tag_must` | Muss: nur Anime mit diesem Tag werden angezeigt | 🟢 | `plus-circle.svg` | Pictogrammers Material Design Icons, https://pictogrammers.com/library/mdi/icon/plus-circle/; Apache 2.0 (Pictogrammers Free License) |
| `ic_tag_exclude` | Ausgeschlossen: Anime mit diesem Tag werden ausgeblendet | 🟠 | `alert-circle.svg` | Pictogrammers Material Design Icons, https://pictogrammers.com/library/mdi/icon/alert-circle/; Apache 2.0 (Pictogrammers Free License) |

## Aktionen

| Datei | Bedeutung | Emoji-Entsprechung | Gelieferte Datei | Quelle/Lizenz |
|---|---|---|---|---|
| `ic_clear` | Eingabe im Suchfeld löschen | ✖️ | `close.svg` | Pictogrammers Material Design Icons, https://pictogrammers.com/library/mdi/icon/close/; Apache 2.0 (Pictogrammers Free License) |
| `ic_tags_reset` | Alle Tag-Filter zurücksetzen | 🔄 | `undo-variant.svg` | Pictogrammers Material Design Icons, https://pictogrammers.com/library/mdi/icon/undo-variant/; Apache 2.0 (Pictogrammers Free License) |
| `ic_sync_start` | Synchronisation starten | ▶️ | `timer-sync-outline.svg` | Pictogrammers Material Design Icons, https://pictogrammers.com/library/mdi/icon/timer-sync-outline/; Apache 2.0 (Pictogrammers Free License) |
| `ic_sync_cancel` | Laufende Synchronisation abbrechen | ⏹️ | `timer-cancel-outline.svg` | Pictogrammers Material Design Icons, https://pictogrammers.com/library/mdi/icon/timer-cancel-outline/; Apache 2.0 (Pictogrammers Free License) |
| `ic_open_external` | Seite auf AniList im Browser öffnen | ↗️ | `open-in-new.svg` | Pictogrammers Material Design Icons, https://pictogrammers.com/library/mdi/icon/open-in-new/; Apache 2.0 (Pictogrammers Free License) |
| `ic_back` | Zurück aus der Detailansicht | ⬅️ | `backburger.svg` | Pictogrammers Material Design Icons, https://pictogrammers.com/library/mdi/icon/backburger/; Apache 2.0 (Pictogrammers Free License) |

## Zustände

| Datei | Bedeutung | Emoji-Entsprechung | Gelieferte Datei | Quelle/Lizenz |
|---|---|---|---|---|
| `ic_state_empty` | Keine Ergebnisse oder noch keine Daten (Hinweis auf den Sync) | 📭 | `cloud-question-outline.svg` | Pictogrammers Material Design Icons, https://pictogrammers.com/library/mdi/icon/cloud-question-outline/; Apache 2.0 (Pictogrammers Free License) |
| `ic_state_error` | Laden fehlgeschlagen | ⚠️ | `timer-alert-outline.svg` | Pictogrammers Material Design Icons, https://pictogrammers.com/library/mdi/icon/timer-alert-outline/; Apache 2.0 (Pictogrammers Free License) |

## Statistik (Einstellungen)

| Datei | Bedeutung | Emoji-Entsprechung | Gelieferte Datei | Quelle/Lizenz |
|---|---|---|---|---|
| `ic_stat_total` | Anzahl aller Anime im Katalog | 📚 | `database-outline.svg` | Pictogrammers Material Design Icons, https://pictogrammers.com/library/mdi/icon/database-outline/; Apache 2.0 (Pictogrammers Free License) |
| `ic_stat_watched` | Anzahl gesehener Anime | 👁️ | `account-eye-outline.svg` | Pictogrammers Material Design Icons, https://pictogrammers.com/library/mdi/icon/account-eye-outline/; Apache 2.0 (Pictogrammers Free License) |
| `ic_stat_meh` | Anzahl als „Meh“ markierter Anime | 😒 | `emoticon-confused.svg` | Pictogrammers Material Design Icons, https://pictogrammers.com/library/mdi/icon/emoticon-confused/; Apache 2.0 (Pictogrammers Free License) |
| `ic_stat_enriched` | Anzahl der mit AniList-Daten angereicherten Anime | ✨ | `account-eye-outline.svg` | Pictogrammers Material Design Icons, https://pictogrammers.com/library/mdi/icon/account-eye-outline/; Apache 2.0 (Pictogrammers Free License) |
| `ic_stat_tags` | Anzahl bekannter Tags | 🏷️ | `book-arrow-down-outline.svg` | Pictogrammers Material Design Icons, https://pictogrammers.com/library/mdi/icon/book-arrow-down-outline/; Apache 2.0 (Pictogrammers Free License) |

## System

| Datei | Bedeutung | Emoji-Entsprechung | Gelieferte Datei | Quelle/Lizenz |
|---|---|---|---|---|
| `ic_notification_sync` | Benachrichtigung während einer laufenden Synchronisation (Statusleiste) | 🔄 | `timer-sync-outline.svg` | Pictogrammers Material Design Icons, https://pictogrammers.com/library/mdi/icon/timer-sync-outline/; Apache 2.0 (Pictogrammers Free License) |
| `ic_launcher_foreground` | App-Icon, Vordergrund (Motiv) | 🍿 | `movie-roll.svg` | Pictogrammers Material Design Icons, https://pictogrammers.com/library/mdi/icon/movie-roll/; Apache 2.0 (Pictogrammers Free License) |
| `ic_launcher_background` | App-Icon, Hintergrund (Fläche oder Muster, z. B. Crunchyroll-Orange) | 🟧 | `square-rounded.svg` | Pictogrammers Material Design Icons, https://pictogrammers.com/library/mdi/icon/square-rounded/; Apache 2.0 (Pictogrammers Free License); Form durch vollflächiges Quadrat ersetzt (Launcher-Hintergrund muss die Fläche decken) |
| `ic_launcher_monochrome` | App-Icon, einfarbige Variante für Themed Icons | 🍿 | `movie-roll.svg` | Pictogrammers Material Design Icons, https://pictogrammers.com/library/mdi/icon/movie-roll/; Apache 2.0 (Pictogrammers Free License) |

## Flaggen (Inhaltssprache)

Farbige Flaggen im Format 4:3 (24 × 18 dp) für die Anzeige der Inhaltssprache auf der Kachel. Sie behalten ihre Farben und werden nicht im Theme eingefärbt. Alle Flaggen sind VectorDrawables, erzeugt und geprüft über das Profil `flag` des Skills `/check-svg`.

| Datei | Bedeutung | Emoji-Entsprechung | Gelieferte Datei | Quelle/Lizenz |
|---|---|---|---|---|
| `ic_flag_de` | Flagge für Inhaltssprache de-DE | 🇩🇪 | `flag-icons de.svg` | flag-icons (https://github.com/lipis/flag-icons), MIT |
| `ic_flag_us` | Flagge für Inhaltssprache en-US | 🇺🇸 | `flag-icons us.svg` | flag-icons (https://github.com/lipis/flag-icons), MIT |
| `ic_flag_mx` | Flagge für Inhaltssprache es-419 | 🇲🇽 | `flag-icons mx.svg` | flag-icons (https://github.com/lipis/flag-icons), MIT |
| `ic_flag_es` | Flagge für Inhaltssprache es-ES | 🇪🇸 | `flag-icons es.svg` | flag-icons (https://github.com/lipis/flag-icons), MIT |
| `ic_flag_fr` | Flagge für Inhaltssprache fr-FR | 🇫🇷 | `flag-icons fr.svg` | flag-icons (https://github.com/lipis/flag-icons), MIT |
| `ic_flag_it` | Flagge für Inhaltssprache it-IT | 🇮🇹 | `flag-icons it.svg` | flag-icons (https://github.com/lipis/flag-icons), MIT |
| `ic_flag_br` | Flagge für Inhaltssprache pt-BR | 🇧🇷 | `flag-icons br.svg` | flag-icons (https://github.com/lipis/flag-icons), MIT |
| `ic_flag_ru` | Flagge für Inhaltssprache ru-RU | 🇷🇺 | `flag-icons ru.svg` | flag-icons (https://github.com/lipis/flag-icons), MIT |
| `ic_flag_sa` | Flagge für Inhaltssprache ar-SA | 🇸🇦 | `flag-icons sa.svg` | flag-icons (https://github.com/lipis/flag-icons), MIT |
| `ic_flag_in` | Flagge für Inhaltssprache hi-IN | 🇮🇳 | `flag-icons in.svg` | flag-icons (https://github.com/lipis/flag-icons), MIT |

## Umwandlung SVG in VectorDrawable

Die Umwandlung übernimmt die Entwicklung (Android Studio „Vector Asset“ bzw. `vd-tool`). Gelieferte SVGs können dafür einfach im Ordner `design/icons/ablage/` abgelegt werden (dessen Inhalt wird nicht versioniert) und mit `/check-svg <name> design/icons/ablage/<datei>.svg` eingebunden werden.
