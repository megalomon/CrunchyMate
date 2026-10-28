# Open-Source-Hinweise

Diese Datei sammelt die Lizenzen und Quellen der in CrunchyMate verwendeten fremden Bestandteile. Sie ist die Grundlage für die Hinweise, die in der App angezeigt werden sollen. Bei jedem neuen fremden Bestandteil wird sie ergänzt.

## Icons

Die Icons stammen aus den Material Design Icons der Pictogrammers (https://pictogrammers.com/) und stehen unter der Pictogrammers Free License. Für die Icons gilt Apache License 2.0 (https://www.apache.org/licenses/LICENSE-2.0), soweit ein Icon nicht unter seiner eigenen Lizenz steht. Welche Datei woher kommt, steht in der Spalte „Quelle/Lizenz“ in `docs/icons.md`.

| Datei | Quelle | Lizenz |
|---|---|---|
| `ic_nav_search` | https://pictogrammers.com/library/mdi/icon/magnify/ | Apache 2.0 |
| `ic_nav_watched` | https://pictogrammers.com/library/mdi/icon/eye-check-outline/ | Apache 2.0 |
| `ic_nav_meh` | https://pictogrammers.com/library/mdi/icon/emoticon-confused/ | Apache 2.0 |
| `ic_nav_settings` | https://pictogrammers.com/library/mdi/icon/cog-outline/ | Apache 2.0 |
| `ic_watched_on` | https://pictogrammers.com/library/mdi/icon/eye-check-outline/ | Apache 2.0 |
| `ic_watched_off` | https://pictogrammers.com/library/mdi/icon/eye-off-outline/ | Apache 2.0 |
| `ic_meh_on` | https://pictogrammers.com/library/mdi/icon/emoticon-confused/ | Apache 2.0 |
| `ic_meh_off` | https://pictogrammers.com/library/mdi/icon/emoticon-confused-outline/ | Apache 2.0 |
| `ic_badge_score` | https://pictogrammers.com/library/mdi/icon/star-face/ | Apache 2.0 |
| `ic_badge_dub` | https://pictogrammers.com/library/mdi/icon/microphone/ | Apache 2.0 |
| `ic_badge_sub` | https://pictogrammers.com/library/mdi/icon/chat-processing/ | Apache 2.0 |
| `ic_badge_boost` | https://pictogrammers.com/library/mdi/icon/circle/ | Apache 2.0 |
| `ic_tag_prefer` | https://pictogrammers.com/library/mdi/icon/circle/ | Apache 2.0 |
| `ic_tag_must` | https://pictogrammers.com/library/mdi/icon/plus-circle/ | Apache 2.0 |
| `ic_tag_exclude` | https://pictogrammers.com/library/mdi/icon/alert-circle/ | Apache 2.0 |
| `ic_clear` | https://pictogrammers.com/library/mdi/icon/close/ | Apache 2.0 |
| `ic_tags_reset` | https://pictogrammers.com/library/mdi/icon/undo-variant/ | Apache 2.0 |
| `ic_sync_start` | https://pictogrammers.com/library/mdi/icon/timer-sync-outline/ | Apache 2.0 |
| `ic_sync_cancel` | https://pictogrammers.com/library/mdi/icon/timer-cancel-outline/ | Apache 2.0 |
| `ic_open_external` | https://pictogrammers.com/library/mdi/icon/open-in-new/ | Apache 2.0 |
| `ic_dropdown` | https://pictogrammers.com/library/mdi/icon/form-dropdown/ | Apache 2.0 |
| `ic_back` | https://pictogrammers.com/library/mdi/icon/backburger/ | Apache 2.0 |
| `ic_state_empty` | https://pictogrammers.com/library/mdi/icon/cloud-question-outline/ | Apache 2.0 |
| `ic_state_error` | https://pictogrammers.com/library/mdi/icon/timer-alert-outline/ | Apache 2.0 |
| `ic_stat_total` | https://pictogrammers.com/library/mdi/icon/database-outline/ | Apache 2.0 |
| `ic_stat_watched` | https://pictogrammers.com/library/mdi/icon/account-eye-outline/ | Apache 2.0 |
| `ic_stat_meh` | https://pictogrammers.com/library/mdi/icon/emoticon-confused/ | Apache 2.0 |
| `ic_stat_tags` | https://pictogrammers.com/library/mdi/icon/book-arrow-down-outline/ | Apache 2.0 |
| `ic_notification_sync` | https://pictogrammers.com/library/mdi/icon/timer-sync-outline/ | Apache 2.0 |
| `ic_stat_enriched` | https://pictogrammers.com/library/mdi/icon/account-eye-outline/ | Apache 2.0 |
| `ic_launcher_foreground` | https://pictogrammers.com/library/mdi/icon/movie-roll/ | Apache 2.0 |
| `ic_launcher_monochrome` | https://pictogrammers.com/library/mdi/icon/movie-roll/ | Apache 2.0 |
| `ic_launcher_background` | https://pictogrammers.com/library/mdi/icon/square-rounded/ | Apache 2.0 |

## Offen

- Rust-Abhängigkeiten und Kotlin-Bibliotheken sind hier noch nicht erfasst. Die Liste sollte später aus `Cargo.lock` und den Gradle-Abhängigkeiten erzeugt werden, nicht von Hand gepflegt.
- Eine Anzeige in der App (z. B. Unterpunkt in den Einstellungen) steht nicht in der Spec. Ob und wo sie entsteht, ist zu entscheiden.

## Länderflaggen

Die Flaggen für die Anzeige der Inhaltssprache stammen aus dem Paket flag-icons (Version 7.6.0, 4:3-Variante, https://github.com/lipis/flag-icons) und stehen unter der MIT-Lizenz. Copyright (c) 2013 Panayiotis Lipiridis. Welche Datei woher kommt, steht im Abschnitt „Flaggen (Inhaltssprache)“ in `docs/icons.md`; die unveränderten Originale liegen in `design/icons/flags/originals/`.
