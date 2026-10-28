# Open-Source-Hinweise

Diese Datei sammelt die Lizenzen und Quellen der in CrunchyMate verwendeten fremden Bestandteile. Sie ist die Grundlage für die Hinweise, die in der App angezeigt werden sollen. Bei jedem neuen fremden Bestandteil wird sie ergänzt.

## Icons

Die Icons stammen aus den Material Design Icons der Pictogrammers (https://pictogrammers.com/) und stehen unter der Pictogrammers Free License (https://pictogrammers.com/docs/general/license/, Stand 01.02.2023). Nach dieser Lizenz werden die Icons unter der Apache License 2.0 (https://www.apache.org/licenses/LICENSE-2.0) weitergegeben; einzelne Icons können stattdessen unter ihrer jeweiligen eigenen Lizenz stehen. Icons der Kategorie „Brand / Logos“ sind von der Lizenz nicht abgedeckt und werden nicht verwendet. Welche Datei woher kommt, steht in der Spalte „Quelle/Lizenz“ in `docs/icons.md`.

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
| `ic_badge_boost` | https://pictogrammers.com/library/mdi/icon/circle/ | Apache 2.0 |
| `ic_tag_prefer` | https://pictogrammers.com/library/mdi/icon/circle/ | Apache 2.0 |
| `ic_tag_must` | https://pictogrammers.com/library/mdi/icon/plus-circle/ | Apache 2.0 |
| `ic_tag_exclude` | https://pictogrammers.com/library/mdi/icon/alert-circle/ | Apache 2.0 |
| `ic_clear` | https://pictogrammers.com/library/mdi/icon/close/ | Apache 2.0 |
| `ic_tags_reset` | https://pictogrammers.com/library/mdi/icon/undo-variant/ | Apache 2.0 |
| `ic_sync_start` | https://pictogrammers.com/library/mdi/icon/timer-sync-outline/ | Apache 2.0 |
| `ic_sync_cancel` | https://pictogrammers.com/library/mdi/icon/timer-cancel-outline/ | Apache 2.0 |
| `ic_open_external` | https://pictogrammers.com/library/mdi/icon/open-in-new/ | Apache 2.0 |
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

- Offene Entscheidung: Ob und wo die Lizenzhinweise in der App angezeigt werden (z. B. als Unterpunkt in den Einstellungen), steht nicht in der Spec und ist vom Auftraggeber festzulegen.

## Länderflaggen

Die Flaggen für die Anzeige der Inhaltssprache stammen aus dem Paket flag-icons (Version 7.6.0, 4:3-Variante, https://github.com/lipis/flag-icons) und stehen unter der MIT-Lizenz. Copyright (c) 2013 Panayiotis Lipiridis. Welche Datei woher kommt, steht im Abschnitt „Flaggen (Inhaltssprache)“ in `docs/icons.md`; die unveränderten Originale liegen in `design/icons/flags/originals/`.

<!-- oss:begin -->
## Abhängigkeiten (automatisch erzeugt)

Neu erzeugen mit `python3 scripts/gen-oss-notices.py`. Nicht von Hand bearbeiten.

### Rust (Normal-Abhängigkeiten von `crunchymate-core`, Ziel `aarch64-linux-android`)

| Crate | Version | Lizenz | Quelle | Copyright |
|---|---|---|---|---|
| adler2 | 2.0.1 | 0BSD OR MIT OR Apache-2.0 | https://github.com/oyvindln/adler2 | adler2 (https://github.com/oyvindln/adler2) |
| aho-corasick | 1.1.5 | Unlicense OR MIT | https://github.com/BurntSushi/aho-corasick | Copyright (c) 2015 Andrew Gallant |
| anyhow | 1.0.104 | MIT OR Apache-2.0 | https://github.com/dtolnay/anyhow | anyhow (https://github.com/dtolnay/anyhow) |
| askama | 0.16.1 | MIT OR Apache-2.0 | https://github.com/askama-rs/askama | Copyright 2017-2020 Dirkjan Ochtman; Copyright (c) 2017-2020 Dirkjan Ochtman |
| async-compat | 0.2.6 | Apache-2.0 OR MIT | https://github.com/smol-rs/async-compat | async-compat (https://github.com/smol-rs/async-compat) |
| atomic-waker | 1.1.2 | Apache-2.0 OR MIT | https://github.com/smol-rs/atomic-waker | Copyright (c) 2016 Alex Crichton; Copyright (c) 2017 The Tokio Authors |
| aws-lc-rs | 1.18.2 | ISC AND (Apache-2.0 OR ISC) | https://github.com/aws/aws-lc-rs | aws-lc-rs (https://github.com/aws/aws-lc-rs) |
| aws-lc-sys | 0.46.0 | ISC AND (Apache-2.0 OR ISC) AND Apache-2.0 AND MIT AND BSD-3-Clause AND (Apache-2.0 OR ISC OR MIT) AND (Apache-2.0 OR ISC OR MIT-0) | https://github.com/aws/aws-lc-rs | Copyright (c) 2014-2024 Google Inc; Copyright (c) 2025-2026 Google Inc; Copyright (c) 1998-2011 The OpenSSL Project. All rights reserved |
| base64 | 0.23.1 | MIT OR Apache-2.0 | https://github.com/marshallpierce/rust-base64 | Copyright (c) 2025 Alice Maz, Marshall Pierce |
| bitflags | 2.13.2 | MIT OR Apache-2.0 | https://github.com/bitflags/bitflags | Copyright (c) 2014 The Rust Project Developers |
| block-buffer | 0.12.1 | MIT OR Apache-2.0 | https://github.com/RustCrypto/utils | Copyright (c) 2018-2025 The RustCrypto Project Developers |
| bytemuck | 1.25.2 | Zlib OR Apache-2.0 OR MIT | https://github.com/Lokathor/bytemuck | Copyright (c) 2019 Daniel "Lokathor" Gee; Copyright (c) 2019 Daniel "Lokathor" Gee |
| byteorder-lite | 0.1.0 | Unlicense OR MIT | https://github.com/image-rs/byteorder-lite | Copyright (c) 2015 Andrew Gallant |
| bytes | 1.12.1 | MIT | https://github.com/tokio-rs/bytes | Copyright (c) 2018 Carl Lerche |
| camino | 1.2.6 | MIT OR Apache-2.0 | https://github.com/camino-rs/camino | camino (https://github.com/camino-rs/camino) |
| cargo-platform | 0.3.3 | MIT OR Apache-2.0 | https://github.com/rust-lang/cargo | cargo-platform (https://github.com/rust-lang/cargo) |
| cargo_metadata | 0.23.1 | MIT | https://github.com/oli-obk/cargo_metadata | cargo_metadata (https://github.com/oli-obk/cargo_metadata) |
| cfg-if | 1.0.5 | MIT OR Apache-2.0 | https://github.com/rust-lang/cfg-if | Copyright (c) 2014 Alex Crichton |
| chacha20 | 0.10.2 | MIT OR Apache-2.0 | https://github.com/RustCrypto/stream-ciphers | Copyright (c) 2019-2026 The RustCrypto Project Developers |
| combine | 4.6.9 | MIT | https://github.com/Marwes/combine | Copyright (c) 2015 Markus Westerlind |
| const-oid | 0.10.2 | Apache-2.0 OR MIT | https://github.com/RustCrypto/formats | Copyright (c) 2020-2026 The RustCrypto Project Developers |
| cpufeatures | 0.3.1 | MIT OR Apache-2.0 | https://github.com/RustCrypto/utils | Copyright (c) 2020-2026 The RustCrypto Project Developers |
| crc32fast | 1.5.3 | MIT OR Apache-2.0 | https://github.com/srijs/rust-crc32fast | Copyright (c) 2018 Sam Rijs, Alex Crichton and contributors |
| crypto-common | 0.2.2 | MIT OR Apache-2.0 | https://github.com/RustCrypto/traits | Copyright (c) 2021-2026 RustCrypto Developers |
| digest | 0.11.3 | MIT OR Apache-2.0 | https://github.com/RustCrypto/traits | Copyright (c) 2017-2025 RustCrypto Developers; Copyright (c) 2017 Artyom Pavlov |
| equivalent | 1.0.3 | Apache-2.0 OR MIT | https://github.com/indexmap-rs/equivalent | Copyright (c) 2016--2023 |
| errno | 0.3.14 | MIT OR Apache-2.0 | https://github.com/lambda-fairy/rust-errno | Copyright (c) 2014 Chris Wong |
| fallible-iterator | 0.3.0 | MIT/Apache-2.0 | https://github.com/sfackler/rust-fallible-iterator | Copyright (c) 2015 The rust-openssl-verify Developers |
| fallible-streaming-iterator | 0.1.9 | MIT/Apache-2.0 | https://github.com/sfackler/fallible-streaming-iterator | Copyright (c) 2016 The fallible-streaming-iterator Developers |
| fastrand | 2.5.0 | Apache-2.0 OR MIT | https://github.com/smol-rs/fastrand | fastrand (https://github.com/smol-rs/fastrand) |
| fdeflate | 0.3.7 | MIT OR Apache-2.0 | https://github.com/image-rs/fdeflate | fdeflate (https://github.com/image-rs/fdeflate) |
| flate2 | 1.1.10 | MIT OR Apache-2.0 | https://github.com/rust-lang/flate2-rs | Copyright (c) 2014-2026 Alex Crichton |
| fnv | 1.0.7 | Apache-2.0 / MIT | https://github.com/servo/rust-fnv | Copyright (c) 2017 Contributors |
| foldhash | 0.2.0 | Zlib | https://github.com/orlp/foldhash | Copyright (c) 2024 Orson Peters |
| form_urlencoded | 1.2.2 | MIT OR Apache-2.0 | https://github.com/servo/rust-url | Copyright (c) 2013-2016 The rust-url developers |
| fs-err | 3.3.2 | MIT OR Apache-2.0 | https://github.com/andrewhickman/fs-err | fs-err (https://github.com/andrewhickman/fs-err) |
| futures-channel | 0.3.34 | MIT OR Apache-2.0 | https://github.com/rust-lang/futures-rs | Copyright (c) 2016 Alex Crichton; Copyright (c) 2017 The Tokio Authors |
| futures-core | 0.3.34 | MIT OR Apache-2.0 | https://github.com/rust-lang/futures-rs | Copyright (c) 2016 Alex Crichton; Copyright (c) 2017 The Tokio Authors |
| futures-io | 0.3.34 | MIT OR Apache-2.0 | https://github.com/rust-lang/futures-rs | Copyright (c) 2016 Alex Crichton; Copyright (c) 2017 The Tokio Authors |
| futures-sink | 0.3.34 | MIT OR Apache-2.0 | https://github.com/rust-lang/futures-rs | Copyright (c) 2016 Alex Crichton; Copyright (c) 2017 The Tokio Authors |
| futures-task | 0.3.34 | MIT OR Apache-2.0 | https://github.com/rust-lang/futures-rs | Copyright (c) 2016 Alex Crichton; Copyright (c) 2017 The Tokio Authors |
| futures-util | 0.3.34 | MIT OR Apache-2.0 | https://github.com/rust-lang/futures-rs | Copyright (c) 2016 Alex Crichton; Copyright (c) 2017 The Tokio Authors |
| getrandom | 0.2.17 | MIT OR Apache-2.0 | https://github.com/rust-random/getrandom | Copyright (c) 2018-2024 The rust-random Project Developers; Copyright (c) 2014 The Rust Project Developers |
| getrandom | 0.4.3 | MIT OR Apache-2.0 | https://github.com/rust-random/getrandom | Copyright (c) 2018-2026 The rust-random Project Developers; Copyright (c) 2014 The Rust Project Developers |
| glob | 0.3.4 | MIT OR Apache-2.0 | https://github.com/rust-lang/glob | Copyright (c) 2014 The Rust Project Developers |
| goblin | 0.8.2 | MIT | https://github.com/m4b/goblin | Copyright (c) m4b 2016-2024 |
| h2 | 0.4.20 | MIT | https://github.com/hyperium/h2 | Copyright (c) 2017 h2 authors |
| hashbrown | 0.17.1 | MIT OR Apache-2.0 | https://github.com/rust-lang/hashbrown | Copyright (c) 2016 Amanieu d'Antras |
| hashlink | 0.12.2 | MIT OR Apache-2.0 | https://github.com/djc/hashlink | hashlink (https://github.com/djc/hashlink) |
| heck | 0.5.0 | MIT OR Apache-2.0 | https://github.com/withoutboats/heck | Copyright (c) 2015 The Rust Project Developers |
| http | 1.5.0 | MIT OR Apache-2.0 | https://github.com/hyperium/http | Copyright 2017 http-rs authors; Copyright (c) 2017 http-rs authors |
| http-body | 1.1.0 | MIT | https://github.com/hyperium/http-body | Copyright (c) 2019-2026 Sean McArthur & Hyper Contributors |
| http-body-util | 0.1.5 | MIT | https://github.com/hyperium/http-body | Copyright (c) 2019-2026 Sean McArthur & Hyper Contributors |
| httparse | 1.10.1 | MIT OR Apache-2.0 | https://github.com/seanmonstar/httparse | Copyright (c) 2015-2025 Sean McArthur |
| httpdate | 1.0.3 | MIT OR Apache-2.0 | https://github.com/pyfisch/httpdate | Copyright (c) 2016 Pyfisch |
| hybrid-array | 0.4.15 | MIT OR Apache-2.0 | https://github.com/RustCrypto/hybrid-array | Copyright (c) 2022-2026 The RustCrypto Project Developers |
| hyper | 1.12.0 | MIT | https://github.com/hyperium/hyper | Copyright (c) 2014-2026 Sean McArthur |
| hyper-rustls | 0.27.10 | Apache-2.0 OR ISC OR MIT | https://github.com/rustls/hyper-rustls | Copyright (c) 2016, Joseph Birr-Pixton <jpixton@gmail.com>; Copyright (c) 2016 Joseph Birr-Pixton <jpixton@gmail.com> |
| hyper-util | 0.1.21 | MIT | https://github.com/hyperium/hyper-util | Copyright (c) 2023-2025 Sean McArthur |
| icu_collections | 2.4.0 | Unicode-3.0 | https://github.com/unicode-org/icu4x | Copyright © 2020-2024 Unicode, Inc |
| icu_locale_core | 2.4.0 | Unicode-3.0 | https://github.com/unicode-org/icu4x | Copyright © 2020-2024 Unicode, Inc |
| icu_normalizer | 2.4.0 | Unicode-3.0 | https://github.com/unicode-org/icu4x | Copyright © 2020-2024 Unicode, Inc |
| icu_normalizer_data | 2.4.0 | Unicode-3.0 | https://github.com/unicode-org/icu4x | Copyright © 2020-2024 Unicode, Inc |
| icu_properties | 2.4.0 | Unicode-3.0 | https://github.com/unicode-org/icu4x | Copyright © 2020-2024 Unicode, Inc |
| icu_properties_data | 2.4.0 | Unicode-3.0 | https://github.com/unicode-org/icu4x | Copyright © 2020-2024 Unicode, Inc |
| icu_provider | 2.4.0 | Unicode-3.0 | https://github.com/unicode-org/icu4x | Copyright © 2020-2024 Unicode, Inc |
| idna | 1.1.0 | MIT OR Apache-2.0 | https://github.com/servo/rust-url/ | Copyright (c) 2013-2025 The rust-url developers |
| idna_adapter | 1.2.2 | Apache-2.0 OR MIT | https://github.com/hsivonen/idna_adapter | idna_adapter (https://github.com/hsivonen/idna_adapter) |
| image | 0.25.10 | MIT OR Apache-2.0 | https://github.com/image-rs/image | image (https://github.com/image-rs/image) |
| indexmap | 2.14.2 | Apache-2.0 OR MIT | https://github.com/indexmap-rs/indexmap | Copyright (c) 2016--2017 |
| ipnet | 2.12.2 | MIT OR Apache-2.0 | https://github.com/krisprice/ipnet | Copyright 2017 Juniper Networks, Inc; Copyright 2017 Juniper Networks, Inc |
| itoa | 1.0.18 | MIT OR Apache-2.0 | https://github.com/dtolnay/itoa | itoa (https://github.com/dtolnay/itoa) |
| jni | 0.22.4 | MIT OR Apache-2.0 | https://github.com/jni-rs/jni-rs | jni (https://github.com/jni-rs/jni-rs) |
| jni-sys | 0.4.1 | MIT OR Apache-2.0 | https://github.com/jni-rs/jni-sys | Copyright (c) 2015 The rust-jni-sys Developers |
| libc | 0.2.191 | MIT OR Apache-2.0 | https://github.com/rust-lang/libc | libc (https://github.com/rust-lang/libc) |
| libsqlite3-sys | 0.39.1 | MIT | https://github.com/rusqlite/rusqlite | Copyright (c) 2014 The rusqlite developers |
| libwebp-sys | 0.9.6 | MIT | https://github.com/NoXF/libwebp-sys | libwebp-sys (https://github.com/NoXF/libwebp-sys) |
| linux-raw-sys | 0.12.1 | Apache-2.0 WITH LLVM-exception OR Apache-2.0 OR MIT | https://github.com/sunfishcode/linux-raw-sys | linux-raw-sys (https://github.com/sunfishcode/linux-raw-sys) |
| litemap | 0.8.3 | Unicode-3.0 | https://github.com/unicode-org/icu4x | Copyright © 2020-2024 Unicode, Inc |
| log | 0.4.34 | MIT OR Apache-2.0 | https://github.com/rust-lang/log | Copyright (c) 2014 The Rust Project Developers |
| lru-slab | 0.1.3 | MIT OR Apache-2.0 OR Zlib | https://github.com/Ralith/lru-slab | Copyright (c) 2024 The lru-slab Developers |
| memchr | 2.8.3 | Unlicense OR MIT | https://github.com/BurntSushi/memchr | Copyright (c) 2015 Andrew Gallant |
| minimal-lexical | 0.2.1 | MIT/Apache-2.0 | https://github.com/Alexhuszagh/minimal-lexical | Copyright (c) 2009 The Go Authors. All rights reserved |
| miniz_oxide | 0.8.9 | MIT OR Zlib OR Apache-2.0 | https://github.com/Frommi/miniz_oxide/tree/master/miniz_oxide | Copyright 2013-2014 RAD Game Tools and Valve Software; Copyright 2010-2014 Rich Geldreich and Tenacious Software LLC; Copyright (c) 2017 Frommi |
| miniz_oxide | 0.9.1 | MIT OR Zlib OR Apache-2.0 | https://github.com/Frommi/miniz_oxide/tree/master/miniz_oxide | Copyright 2013-2014 RAD Game Tools and Valve Software; Copyright 2010-2014 Rich Geldreich and Tenacious Software LLC; Copyright (c) 2017 Frommi |
| mio | 1.2.4 | MIT | https://github.com/tokio-rs/mio | Copyright (c) 2014 Carl Lerche and other MIO contributors |
| moxcms | 0.8.1 | BSD-3-Clause OR Apache-2.0 | https://github.com/awxkee/moxcms.git | Copyright 2024 Radzivon Bartoshyk |
| nom | 7.1.3 | MIT | https://github.com/Geal/nom | Copyright (c) 2014-2019 Geoffroy Couprie |
| num-traits | 0.2.19 | MIT OR Apache-2.0 | https://github.com/rust-num/num-traits | Copyright (c) 2014 The Rust Project Developers |
| once_cell | 1.21.4 | MIT OR Apache-2.0 | https://github.com/matklad/once_cell | once_cell (https://github.com/matklad/once_cell) |
| percent-encoding | 2.3.2 | MIT OR Apache-2.0 | https://github.com/servo/rust-url/ | Copyright (c) 2013-2025 The rust-url developers |
| pin-project-lite | 0.2.17 | Apache-2.0 OR MIT | https://github.com/taiki-e/pin-project-lite | pin-project-lite (https://github.com/taiki-e/pin-project-lite) |
| plain | 0.2.3 | MIT/Apache-2.0 | https://github.com/randomites/plain | Copyright (c) 2017 Plain contributors |
| png | 0.18.1 | MIT OR Apache-2.0 | https://github.com/image-rs/image-png | Copyright (c) 2015 nwin |
| potential_utf | 0.1.6 | Unicode-3.0 | https://github.com/unicode-org/icu4x | Copyright © 2020-2024 Unicode, Inc |
| pxfm | 0.1.30 | BSD-3-Clause OR Apache-2.0 | https://github.com/awxkee/pxfm | Copyright 2024 Radzivon Bartoshyk |
| quinn | 0.11.12 | MIT OR Apache-2.0 | https://github.com/quinn-rs/quinn | Copyright (c) 2018 The quinn Developers |
| quinn-proto | 0.11.19 | MIT OR Apache-2.0 | https://github.com/quinn-rs/quinn | Copyright (c) 2018 The quinn Developers |
| quinn-udp | 0.5.16 | MIT OR Apache-2.0 | https://github.com/quinn-rs/quinn | Copyright (c) 2018 The quinn Developers |
| rand | 0.10.3 | MIT OR Apache-2.0 | https://github.com/rust-random/rand | Copyright 2018 Developers of the Rand project; Copyright (c) 2014 The Rust Project Developers |
| rand_core | 0.10.1 | MIT OR Apache-2.0 | https://github.com/rust-random/rand_core | Copyright (c) 2018-2026 The Rand Project Developers |
| rand_pcg | 0.10.2 | MIT OR Apache-2.0 | https://github.com/rust-random/rngs | Copyright (c) 2014-2017 Melissa O'Neill and PCG Project contributors; Copyright 2018 Developers of the Rand project |
| regex | 1.13.1 | MIT OR Apache-2.0 | https://github.com/rust-lang/regex | Copyright (c) 2014 The Rust Project Developers |
| regex-automata | 0.4.18 | MIT OR Apache-2.0 | https://github.com/rust-lang/regex | Copyright (c) 2014 The Rust Project Developers |
| regex-syntax | 0.8.11 | MIT OR Apache-2.0 | https://github.com/rust-lang/regex | Copyright (c) 2014 The Rust Project Developers |
| reqwest | 0.13.5 | MIT OR Apache-2.0 | https://github.com/seanmonstar/reqwest | Copyright 2016 Sean McArthur; Copyright (c) 2016-2026 Sean McArthur |
| ring | 0.17.14 | Apache-2.0 AND ISC | https://github.com/briansmith/ring | Copyright (c) 2009 The Go Authors. All rights reserved; Copyright 2015 The Chromium Authors. All rights reserved; Copyright 2015-2025 Brian Smith |
| rusqlite | 0.41.0 | MIT | https://github.com/rusqlite/rusqlite | Copyright (c) 2014 The rusqlite developers |
| rustc-hash | 2.1.3 | Apache-2.0 OR MIT | https://github.com/rust-lang/rustc-hash | rustc-hash (https://github.com/rust-lang/rustc-hash) |
| rustix | 1.1.6 | Apache-2.0 WITH LLVM-exception OR Apache-2.0 OR MIT | https://github.com/bytecodealliance/rustix | rustix (https://github.com/bytecodealliance/rustix) |
| rustls | 0.23.46 | Apache-2.0 OR ISC OR MIT | https://github.com/rustls/rustls | Copyright (c) 2016, Joseph Birr-Pixton <jpixton@gmail.com>; Copyright (c) 2016 Joseph Birr-Pixton <jpixton@gmail.com> |
| rustls-pki-types | 1.15.1 | MIT OR Apache-2.0 | https://github.com/rustls/pki-types | Copyright 2023 Dirkjan Ochtman; Copyright (c) 2023 Dirkjan Ochtman <dirkjan@ochtman.nl> |
| rustls-platform-verifier | 0.7.1 | MIT OR Apache-2.0 | https://github.com/rustls/rustls-platform-verifier | Copyright (c) 2022 1Password |
| rustls-platform-verifier-android | 0.2.0 | MIT OR Apache-2.0 | https://github.com/rustls/rustls-platform-verifier | rustls-platform-verifier-android (https://github.com/rustls/rustls-platform-verifier) |
| rustls-webpki | 0.103.16 | ISC | https://github.com/rustls/webpki | Copyright 2015 Brian Smith |
| ryu | 1.0.23 | Apache-2.0 OR BSL-1.0 | https://github.com/dtolnay/ryu | ryu (https://github.com/dtolnay/ryu) |
| scroll | 0.12.0 | MIT | https://github.com/m4b/scroll | Copyright (c) m4b 2016 |
| semver | 1.0.28 | MIT OR Apache-2.0 | https://github.com/dtolnay/semver | semver (https://github.com/dtolnay/semver) |
| serde | 1.0.229 | MIT OR Apache-2.0 | https://github.com/serde-rs/serde | serde (https://github.com/serde-rs/serde) |
| serde_core | 1.0.229 | MIT OR Apache-2.0 | https://github.com/serde-rs/serde | serde_core (https://github.com/serde-rs/serde) |
| serde_json | 1.0.154 | MIT OR Apache-2.0 | https://github.com/serde-rs/json | serde_json (https://github.com/serde-rs/json) |
| serde_spanned | 1.1.2 | MIT OR Apache-2.0 | https://github.com/toml-rs/toml | serde_spanned (https://github.com/toml-rs/toml) |
| serde_urlencoded | 0.7.1 | MIT/Apache-2.0 | https://github.com/nox/serde_urlencoded | Copyright (c) 2016 Anthony Ramine |
| sha2 | 0.11.1 | MIT OR Apache-2.0 | https://github.com/RustCrypto/hashes | Copyright (c) 2016-2026 The RustCrypto Project Developers; Copyright (c) 2016 Artyom Pavlov; Copyright (c) 2009-2013 Mozilla Foundation |
| simd-adler32 | 0.3.10 | MIT | https://github.com/mcountryman/simd-adler32 | Copyright (c) [2021] [Marvin Countryman] |
| simd_cesu8 | 1.2.0 | Apache-2.0 OR MIT | https://github.com/seancroach/simd_cesu8 | simd_cesu8 (https://github.com/seancroach/simd_cesu8) |
| simdutf8 | 0.1.5 | MIT OR Apache-2.0 | https://github.com/rusticstuff/simdutf8 | simdutf8 (https://github.com/rusticstuff/simdutf8) |
| siphasher | 1.0.4 | MIT OR Apache-2.0 | https://github.com/jedisct1/rust-siphash | Copyright 2012-2016 The Rust Project Developers; Copyright 2016-2026 Frank Denis |
| slab | 0.4.12 | MIT | https://github.com/tokio-rs/slab | Copyright (c) 2019 Carl Lerche |
| smallvec | 1.16.4 | MIT OR Apache-2.0 | https://github.com/servo/rust-smallvec | Copyright (c) 2018 The Servo Project Developers |
| smawk | 0.3.3 | MIT | https://github.com/mgeisler/smawk | Copyright (c) 2017 Martin Geisler |
| socket2 | 0.6.5 | MIT OR Apache-2.0 | https://github.com/rust-lang/socket2 | Copyright (c) 2014 Alex Crichton |
| stable_deref_trait | 1.2.1 | MIT OR Apache-2.0 | https://github.com/storyyeller/stable_deref_trait | Copyright (c) 2017 Robert Grosse |
| static_assertions | 1.1.0 | MIT OR Apache-2.0 | https://github.com/nvzqz/static-assertions-rs | Copyright (c) 2017 Nikolai Vazquez |
| subtle | 2.6.1 | BSD-3-Clause | https://github.com/dalek-cryptography/subtle | Copyright (c) 2016-2017 Isis Agora Lovecruft, Henry de Valence. All rights reserved; Copyright (c) 2016-2024 Isis Agora Lovecruft. All rights reserved |
| sync_wrapper | 1.0.2 | Apache-2.0 | https://github.com/Actyx/sync_wrapper | sync_wrapper (https://github.com/Actyx/sync_wrapper) |
| tempfile | 3.27.0 | MIT OR Apache-2.0 | https://github.com/Stebalien/tempfile | Copyright (c) 2015 Steven Allen |
| textwrap | 0.16.4 | MIT | https://github.com/mgeisler/textwrap | Copyright (c) 2016 Martin Geisler |
| thiserror | 2.0.21 | MIT OR Apache-2.0 | https://github.com/dtolnay/thiserror | thiserror (https://github.com/dtolnay/thiserror) |
| tinystr | 0.8.5 | Unicode-3.0 | https://github.com/unicode-org/icu4x | Copyright © 2020-2024 Unicode, Inc |
| tinyvec | 1.13.3 | Zlib OR Apache-2.0 OR MIT | https://github.com/Lokathor/tinyvec | Copyright (c) 2019 Daniel "Lokathor" Gee |
| tokio | 1.54.1 | MIT | https://github.com/tokio-rs/tokio | tokio (https://github.com/tokio-rs/tokio) |
| tokio-rustls | 0.26.6 | MIT OR Apache-2.0 | https://github.com/rustls/tokio-rustls | Copyright 2017 quininer kel; Copyright (c) 2017 quininer kel |
| tokio-util | 0.7.20 | MIT | https://github.com/tokio-rs/tokio | tokio-util (https://github.com/tokio-rs/tokio) |
| toml | 1.1.8+spec-1.1.0 | MIT OR Apache-2.0 | https://github.com/toml-rs/toml | toml (https://github.com/toml-rs/toml) |
| toml_datetime | 1.1.2+spec-1.1.0 | MIT OR Apache-2.0 | https://github.com/toml-rs/toml | toml_datetime (https://github.com/toml-rs/toml) |
| toml_parser | 1.1.5+spec-1.1.0 | MIT OR Apache-2.0 | https://github.com/toml-rs/toml | toml_parser (https://github.com/toml-rs/toml) |
| toml_writer | 1.1.3+spec-1.1.0 | MIT OR Apache-2.0 | https://github.com/toml-rs/toml | toml_writer (https://github.com/toml-rs/toml) |
| tower | 0.5.3 | MIT | https://github.com/tower-rs/tower | Copyright (c) 2019 Tower Contributors |
| tower-http | 0.6.11 | MIT | https://github.com/tower-rs/tower-http | Copyright (c) 2019-2021 Tower Contributors |
| tower-layer | 0.3.3 | MIT | https://github.com/tower-rs/tower | Copyright (c) 2019 Tower Contributors |
| tower-service | 0.3.3 | MIT | https://github.com/tower-rs/tower | Copyright (c) 2019 Tower Contributors |
| tracing | 0.1.44 | MIT | https://github.com/tokio-rs/tracing | Copyright (c) 2019 Tokio Contributors |
| tracing-core | 0.1.36 | MIT | https://github.com/tokio-rs/tracing | Copyright (c) 2019 Tokio Contributors |
| try-lock | 0.2.5 | MIT | https://github.com/seanmonstar/try-lock | Copyright (c) 2018-2023 Sean McArthur; Copyright (c) 2016 Alex Crichton |
| typenum | 1.20.1 | MIT OR Apache-2.0 | https://github.com/paholg/typenum | Copyright 2014 Paho Lurie-Gregg; Copyright (c) 2014 Paho Lurie-Gregg |
| unicode-width | 0.2.3 | MIT OR Apache-2.0 | https://github.com/unicode-rs/unicode-width | Copyright (c) 2015 The Rust Project Developers |
| uniffi | 0.32.2 | MPL-2.0 | https://github.com/mozilla/uniffi-rs | uniffi (https://github.com/mozilla/uniffi-rs) |
| uniffi_bindgen | 0.32.2 | MPL-2.0 | https://github.com/mozilla/uniffi-rs | uniffi_bindgen (https://github.com/mozilla/uniffi-rs) |
| uniffi_core | 0.32.2 | MPL-2.0 | https://github.com/mozilla/uniffi-rs | uniffi_core (https://github.com/mozilla/uniffi-rs) |
| uniffi_meta | 0.32.2 | MPL-2.0 | https://github.com/mozilla/uniffi-rs | uniffi_meta (https://github.com/mozilla/uniffi-rs) |
| uniffi_pipeline | 0.32.2 | MPL-2.0 | https://github.com/mozilla/uniffi-rs | uniffi_pipeline (https://github.com/mozilla/uniffi-rs) |
| uniffi_udl | 0.32.2 | MPL-2.0 | https://github.com/mozilla/uniffi-rs | uniffi_udl (https://github.com/mozilla/uniffi-rs) |
| untrusted | 0.9.0 | ISC | https://github.com/briansmith/untrusted | Copyright 2015-2016 Brian Smith |
| url | 2.5.8 | MIT OR Apache-2.0 | https://github.com/servo/rust-url | Copyright (c) 2013-2025 The rust-url developers |
| utf8_iter | 1.0.4 | Apache-2.0 OR MIT | https://github.com/hsivonen/utf8_iter | utf8_iter (https://github.com/hsivonen/utf8_iter) |
| want | 0.3.2 | MIT | https://github.com/seanmonstar/want | Copyright (c) 2018-2019 Sean McArthur |
| webp | 0.3.1 | MIT OR Apache-2.0 | https://github.com/jaredforth/webp.git | Copyright (c) 2020 Jared Forth |
| webpki-roots | 1.0.11 | CDLA-Permissive-2.0 | https://github.com/rustls/webpki-roots | webpki-roots (https://github.com/rustls/webpki-roots) |
| weedle2 | 5.0.0 | MIT | https://github.com/mozilla/uniffi-rs | Copyright 2018-Present Sharad Chand; Copyright 2022 Jan-Erik Rediger |
| winnow | 1.0.4 | MIT | https://github.com/winnow-rs/winnow | winnow (https://github.com/winnow-rs/winnow) |
| writeable | 0.6.4 | Unicode-3.0 | https://github.com/unicode-org/icu4x | Copyright © 2020-2024 Unicode, Inc |
| yoke | 0.8.4 | Unicode-3.0 | https://github.com/unicode-org/icu4x | Copyright © 2020-2024 Unicode, Inc |
| zerofrom | 0.1.8 | Unicode-3.0 | https://github.com/unicode-org/icu4x | Copyright © 2020-2024 Unicode, Inc |
| zeroize | 1.9.1 | Apache-2.0 OR MIT | https://github.com/RustCrypto/utils | Copyright (c) 2018-2026 The RustCrypto Project Developers |
| zerotrie | 0.2.5 | Unicode-3.0 | https://github.com/unicode-org/icu4x | Copyright © 2020-2024 Unicode, Inc |
| zerovec | 0.11.9 | Unicode-3.0 | https://github.com/unicode-org/icu4x | Copyright © 2020-2024 Unicode, Inc |
| zlib-rs | 0.6.8 | Zlib | https://github.com/trifectatechfoundation/zlib-rs | (C) 2024 Trifecta Tech Foundation |
| zmij | 1.0.23 | MIT | https://github.com/dtolnay/zmij | zmij (https://github.com/dtolnay/zmij) |
| zune-core | 0.5.3 | MIT OR Apache-2.0 OR Zlib | https://github.com/etemesi254/zune-image | zune-core (https://github.com/etemesi254/zune-image) |
| zune-jpeg | 0.5.15 | MIT OR Apache-2.0 OR Zlib | https://github.com/etemesi254/zune-image/tree/dev/crates/zune-jpeg | zune-jpeg (https://github.com/etemesi254/zune-image/tree/dev/crates/zune-jpeg) |

### Android/Kotlin (`releaseRuntimeClasspath` von `:app`)

| Bibliothek | Version | Lizenz | Quelle | Urheber |
|---|---|---|---|---|
| androidx.activity:activity | 1.13.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/activity#1.13.0 | The Android Open Source Project |
| androidx.activity:activity-compose | 1.13.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/activity#1.13.0 | The Android Open Source Project |
| androidx.activity:activity-ktx | 1.13.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/activity#1.13.0 | The Android Open Source Project |
| androidx.annotation:annotation | 1.10.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/annotation#1.10.0 | The Android Open Source Project |
| androidx.annotation:annotation-experimental | 1.4.1 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/annotation#1.4.1 | The Android Open Source Project |
| androidx.annotation:annotation-jvm | 1.10.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/annotation#1.10.0 | The Android Open Source Project |
| androidx.appcompat:appcompat-resources | 1.7.1 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/appcompat#1.7.1 | The Android Open Source Project |
| androidx.arch.core:core-common | 2.2.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/arch-core#2.2.0 | The Android Open Source Project |
| androidx.arch.core:core-runtime | 2.2.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/arch-core#2.2.0 | The Android Open Source Project |
| androidx.autofill:autofill | 1.0.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx | The Android Open Source Project |
| androidx.collection:collection | 1.5.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/collection#1.5.0 | The Android Open Source Project |
| androidx.collection:collection-jvm | 1.5.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/collection#1.5.0 | The Android Open Source Project |
| androidx.collection:collection-ktx | 1.5.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/collection#1.5.0 | The Android Open Source Project |
| androidx.compose.animation:animation | 1.12.1 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/compose-animation#1.12.1 | The Android Open Source Project |
| androidx.compose.animation:animation-android | 1.12.1 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/compose-animation#1.12.1 | The Android Open Source Project |
| androidx.compose.animation:animation-core | 1.12.1 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/compose-animation#1.12.1 | The Android Open Source Project |
| androidx.compose.animation:animation-core-android | 1.12.1 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/compose-animation#1.12.1 | The Android Open Source Project |
| androidx.compose.foundation:foundation | 1.12.1 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/compose-foundation#1.12.1 | The Android Open Source Project |
| androidx.compose.foundation:foundation-android | 1.12.1 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/compose-foundation#1.12.1 | The Android Open Source Project |
| androidx.compose.foundation:foundation-layout | 1.12.1 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/compose-foundation#1.12.1 | The Android Open Source Project |
| androidx.compose.foundation:foundation-layout-android | 1.12.1 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/compose-foundation#1.12.1 | The Android Open Source Project |
| androidx.compose.material:material-ripple | 1.12.1 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/compose-material#1.12.1 | The Android Open Source Project |
| androidx.compose.material:material-ripple-android | 1.12.1 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/compose-material#1.12.1 | The Android Open Source Project |
| androidx.compose.material3:material3 | 1.4.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/compose-material3#1.4.0 | The Android Open Source Project |
| androidx.compose.material3:material3-android | 1.4.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/compose-material3#1.4.0 | The Android Open Source Project |
| androidx.compose.runtime:runtime | 1.12.1 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/compose-runtime#1.12.1 | The Android Open Source Project |
| androidx.compose.runtime:runtime-android | 1.12.1 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/compose-runtime#1.12.1 | The Android Open Source Project |
| androidx.compose.runtime:runtime-annotation | 1.12.1 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/compose-runtime#1.12.1 | The Android Open Source Project |
| androidx.compose.runtime:runtime-annotation-android | 1.12.1 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/compose-runtime#1.12.1 | The Android Open Source Project |
| androidx.compose.runtime:runtime-retain | 1.12.1 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/compose-runtime#1.12.1 | The Android Open Source Project |
| androidx.compose.runtime:runtime-retain-android | 1.12.1 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/compose-runtime#1.12.1 | The Android Open Source Project |
| androidx.compose.runtime:runtime-saveable | 1.12.1 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/compose-runtime#1.12.1 | The Android Open Source Project |
| androidx.compose.runtime:runtime-saveable-android | 1.12.1 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/compose-runtime#1.12.1 | The Android Open Source Project |
| androidx.compose.ui:ui | 1.12.1 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/compose-ui#1.12.1 | The Android Open Source Project |
| androidx.compose.ui:ui-android | 1.12.1 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/compose-ui#1.12.1 | The Android Open Source Project |
| androidx.compose.ui:ui-geometry | 1.12.1 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/compose-ui#1.12.1 | The Android Open Source Project |
| androidx.compose.ui:ui-geometry-android | 1.12.1 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/compose-ui#1.12.1 | The Android Open Source Project |
| androidx.compose.ui:ui-graphics | 1.12.1 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/compose-ui#1.12.1 | The Android Open Source Project |
| androidx.compose.ui:ui-graphics-android | 1.12.1 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/compose-ui#1.12.1 | The Android Open Source Project |
| androidx.compose.ui:ui-text | 1.12.1 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/compose-ui#1.12.1 | The Android Open Source Project |
| androidx.compose.ui:ui-text-android | 1.12.1 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/compose-ui#1.12.1 | The Android Open Source Project |
| androidx.compose.ui:ui-unit | 1.12.1 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/compose-ui#1.12.1 | The Android Open Source Project |
| androidx.compose.ui:ui-unit-android | 1.12.1 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/compose-ui#1.12.1 | The Android Open Source Project |
| androidx.compose.ui:ui-util | 1.12.1 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/compose-ui#1.12.1 | The Android Open Source Project |
| androidx.compose.ui:ui-util-android | 1.12.1 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/compose-ui#1.12.1 | The Android Open Source Project |
| androidx.concurrent:concurrent-futures | 1.1.0 | Apache-2.0 | https://developer.android.com/topic/libraries/architecture/index.html | The Android Open Source Project |
| androidx.concurrent:concurrent-futures-ktx | 1.1.0 | Apache-2.0 | https://developer.android.com/topic/libraries/architecture/index.html | The Android Open Source Project |
| androidx.core:core | 1.18.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/core#1.18.0 | The Android Open Source Project |
| androidx.core:core-ktx | 1.18.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/core#1.18.0 | The Android Open Source Project |
| androidx.core:core-viewtree | 1.0.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/core#1.0.0 | The Android Open Source Project |
| androidx.customview:customview-poolingcontainer | 1.0.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/customview#1.0.0 | The Android Open Source Project |
| androidx.emoji2:emoji2 | 1.4.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/emoji2#1.4.0 | The Android Open Source Project |
| androidx.exifinterface:exifinterface | 1.4.2 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/exifinterface#1.4.2 | The Android Open Source Project |
| androidx.graphics:graphics-path | 1.0.1 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/graphics#1.0.1 | The Android Open Source Project |
| androidx.interpolator:interpolator | 1.0.0 | Apache-2.0 | http://developer.android.com/tools/extras/support-library.html | The Android Open Source Project |
| androidx.lifecycle:lifecycle-common | 2.11.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/lifecycle#2.11.0 | The Android Open Source Project |
| androidx.lifecycle:lifecycle-common-java8 | 2.11.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/lifecycle#2.11.0 | The Android Open Source Project |
| androidx.lifecycle:lifecycle-common-jvm | 2.11.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/lifecycle#2.11.0 | The Android Open Source Project |
| androidx.lifecycle:lifecycle-livedata | 2.11.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/lifecycle#2.11.0 | The Android Open Source Project |
| androidx.lifecycle:lifecycle-livedata-core | 2.11.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/lifecycle#2.11.0 | The Android Open Source Project |
| androidx.lifecycle:lifecycle-livedata-core-ktx | 2.11.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/lifecycle#2.11.0 | The Android Open Source Project |
| androidx.lifecycle:lifecycle-process | 2.11.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/lifecycle#2.11.0 | The Android Open Source Project |
| androidx.lifecycle:lifecycle-runtime | 2.11.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/lifecycle#2.11.0 | The Android Open Source Project |
| androidx.lifecycle:lifecycle-runtime-android | 2.11.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/lifecycle#2.11.0 | The Android Open Source Project |
| androidx.lifecycle:lifecycle-runtime-compose | 2.11.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/lifecycle#2.11.0 | The Android Open Source Project |
| androidx.lifecycle:lifecycle-runtime-compose-android | 2.11.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/lifecycle#2.11.0 | The Android Open Source Project |
| androidx.lifecycle:lifecycle-runtime-ktx | 2.11.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/lifecycle#2.11.0 | The Android Open Source Project |
| androidx.lifecycle:lifecycle-runtime-ktx-android | 2.11.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/lifecycle#2.11.0 | The Android Open Source Project |
| androidx.lifecycle:lifecycle-service | 2.11.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/lifecycle#2.11.0 | The Android Open Source Project |
| androidx.lifecycle:lifecycle-viewmodel | 2.11.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/lifecycle#2.11.0 | The Android Open Source Project |
| androidx.lifecycle:lifecycle-viewmodel-android | 2.11.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/lifecycle#2.11.0 | The Android Open Source Project |
| androidx.lifecycle:lifecycle-viewmodel-compose | 2.11.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/lifecycle#2.11.0 | The Android Open Source Project |
| androidx.lifecycle:lifecycle-viewmodel-compose-android | 2.11.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/lifecycle#2.11.0 | The Android Open Source Project |
| androidx.lifecycle:lifecycle-viewmodel-ktx | 2.11.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/lifecycle#2.11.0 | The Android Open Source Project |
| androidx.lifecycle:lifecycle-viewmodel-savedstate | 2.11.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/lifecycle#2.11.0 | The Android Open Source Project |
| androidx.lifecycle:lifecycle-viewmodel-savedstate-android | 2.11.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/lifecycle#2.11.0 | The Android Open Source Project |
| androidx.navigation:navigation-common | 2.10.2 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/navigation#2.10.2 | The Android Open Source Project |
| androidx.navigation:navigation-common-android | 2.10.2 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/navigation#2.10.2 | The Android Open Source Project |
| androidx.navigation:navigation-compose | 2.10.2 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/navigation#2.10.2 | The Android Open Source Project |
| androidx.navigation:navigation-compose-android | 2.10.2 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/navigation#2.10.2 | The Android Open Source Project |
| androidx.navigation:navigation-runtime | 2.10.2 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/navigation#2.10.2 | The Android Open Source Project |
| androidx.navigation:navigation-runtime-android | 2.10.2 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/navigation#2.10.2 | The Android Open Source Project |
| androidx.navigationevent:navigationevent | 1.1.2 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/navigationevent#1.1.2 | The Android Open Source Project |
| androidx.navigationevent:navigationevent-android | 1.1.2 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/navigationevent#1.1.2 | The Android Open Source Project |
| androidx.navigationevent:navigationevent-compose | 1.1.2 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/navigationevent#1.1.2 | The Android Open Source Project |
| androidx.navigationevent:navigationevent-compose-android | 1.1.2 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/navigationevent#1.1.2 | The Android Open Source Project |
| androidx.paging:paging-common | 3.5.3 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/paging#3.5.3 | The Android Open Source Project |
| androidx.paging:paging-common-android | 3.5.3 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/paging#3.5.3 | The Android Open Source Project |
| androidx.paging:paging-compose | 3.5.3 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/paging#3.5.3 | The Android Open Source Project |
| androidx.paging:paging-compose-android | 3.5.3 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/paging#3.5.3 | The Android Open Source Project |
| androidx.profileinstaller:profileinstaller | 1.4.1 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/profileinstaller#1.4.1 | The Android Open Source Project |
| androidx.room:room-common | 2.7.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/room#2.7.0 | The Android Open Source Project |
| androidx.room:room-common-jvm | 2.7.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/room#2.7.0 | The Android Open Source Project |
| androidx.room:room-runtime | 2.7.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/room#2.7.0 | The Android Open Source Project |
| androidx.room:room-runtime-android | 2.7.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/room#2.7.0 | The Android Open Source Project |
| androidx.savedstate:savedstate | 1.5.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/savedstate#1.5.0 | The Android Open Source Project |
| androidx.savedstate:savedstate-android | 1.5.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/savedstate#1.5.0 | The Android Open Source Project |
| androidx.savedstate:savedstate-compose | 1.5.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/savedstate#1.5.0 | The Android Open Source Project |
| androidx.savedstate:savedstate-compose-android | 1.5.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/savedstate#1.5.0 | The Android Open Source Project |
| androidx.savedstate:savedstate-ktx | 1.5.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/savedstate#1.5.0 | The Android Open Source Project |
| androidx.sqlite:sqlite | 2.5.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/sqlite#2.5.0 | The Android Open Source Project |
| androidx.sqlite:sqlite-android | 2.5.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/sqlite#2.5.0 | The Android Open Source Project |
| androidx.sqlite:sqlite-framework | 2.5.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/sqlite#2.5.0 | The Android Open Source Project |
| androidx.sqlite:sqlite-framework-android | 2.5.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/sqlite#2.5.0 | The Android Open Source Project |
| androidx.startup:startup-runtime | 1.1.1 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/startup#1.1.1 | The Android Open Source Project |
| androidx.tracing:tracing | 1.2.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/tracing#1.2.0 | The Android Open Source Project |
| androidx.tracing:tracing-ktx | 1.2.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/tracing#1.2.0 | The Android Open Source Project |
| androidx.vectordrawable:vectordrawable | 1.1.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx | The Android Open Source Project |
| androidx.vectordrawable:vectordrawable-animated | 1.1.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx | The Android Open Source Project |
| androidx.versionedparcelable:versionedparcelable | 1.1.1 | Apache-2.0 | http://developer.android.com/tools/extras/support-library.html | The Android Open Source Project |
| androidx.window:window | 1.5.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/window#1.5.0 | The Android Open Source Project |
| androidx.window:window-core | 1.5.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/window#1.5.0 | The Android Open Source Project |
| androidx.window:window-core-android | 1.5.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/window#1.5.0 | The Android Open Source Project |
| androidx.work:work-runtime | 2.12.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/work#2.12.0 | The Android Open Source Project |
| androidx.work:work-runtime-ktx | 2.12.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/work#2.12.0 | The Android Open Source Project |
| com.google.accompanist:accompanist-drawablepainter | 0.37.3 | Apache-2.0 | https://github.com/google/accompanist/ | Google |
| com.google.guava:listenablefuture | 1.0 | Apache-2.0 | https://github.com/google/guava | Kevin Bourrillion |
| com.squareup.okhttp3:okhttp | 4.12.0 | Apache-2.0 | https://square.github.io/okhttp/ | Square, Inc. |
| com.squareup.okio:okio | 3.18.1 | Apache-2.0 | https://github.com/lysine-dev/okio/ | Square, Inc. |
| com.squareup.okio:okio-jvm | 3.18.1 | Apache-2.0 | https://github.com/lysine-dev/okio/ | Square, Inc. |
| io.coil-kt.coil3:coil | 3.6.3 | Apache-2.0 | https://github.com/coil-kt/coil | Coil Contributors |
| io.coil-kt.coil3:coil-android | 3.6.3 | Apache-2.0 | https://github.com/coil-kt/coil | Coil Contributors |
| io.coil-kt.coil3:coil-compose | 3.6.3 | Apache-2.0 | https://github.com/coil-kt/coil | Coil Contributors |
| io.coil-kt.coil3:coil-compose-android | 3.6.3 | Apache-2.0 | https://github.com/coil-kt/coil | Coil Contributors |
| io.coil-kt.coil3:coil-compose-core | 3.6.3 | Apache-2.0 | https://github.com/coil-kt/coil | Coil Contributors |
| io.coil-kt.coil3:coil-compose-core-android | 3.6.3 | Apache-2.0 | https://github.com/coil-kt/coil | Coil Contributors |
| io.coil-kt.coil3:coil-core | 3.6.3 | Apache-2.0 | https://github.com/coil-kt/coil | Coil Contributors |
| io.coil-kt.coil3:coil-core-android | 3.6.3 | Apache-2.0 | https://github.com/coil-kt/coil | Coil Contributors |
| io.coil-kt.coil3:coil-network-core | 3.6.3 | Apache-2.0 | https://github.com/coil-kt/coil | Coil Contributors |
| io.coil-kt.coil3:coil-network-core-android | 3.6.3 | Apache-2.0 | https://github.com/coil-kt/coil | Coil Contributors |
| io.coil-kt.coil3:coil-network-okhttp | 3.6.3 | Apache-2.0 | https://github.com/coil-kt/coil | Coil Contributors |
| io.coil-kt.coil3:coil-network-okhttp-android | 3.6.3 | Apache-2.0 | https://github.com/coil-kt/coil | Coil Contributors |
| net.java.dev.jna:jna | 5.19.1 | LGPL-2.1-or-later; Apache-2.0 | https://github.com/java-native-access/jna | Timothy Wall |
| org.jetbrains:annotations | 23.0.0 | Apache-2.0 | https://github.com/JetBrains/java-annotations | JetBrains Team |
| org.jetbrains.androidx.lifecycle:lifecycle-common | 2.9.6 | Apache-2.0 | https://github.com/JetBrains/compose-jb | Compose Multiplatform Team |
| org.jetbrains.androidx.lifecycle:lifecycle-runtime | 2.9.6 | Apache-2.0 | https://github.com/JetBrains/compose-jb | Compose Multiplatform Team |
| org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose | 2.9.6 | Apache-2.0 | https://github.com/JetBrains/compose-jb | Compose Multiplatform Team |
| org.jetbrains.androidx.savedstate:savedstate | 1.3.6 | Apache-2.0 | https://github.com/JetBrains/compose-jb | Compose Multiplatform Team |
| org.jetbrains.androidx.savedstate:savedstate-compose | 1.3.6 | Apache-2.0 | https://github.com/JetBrains/compose-jb | Compose Multiplatform Team |
| org.jetbrains.compose.animation:animation | 1.12.0 | Apache-2.0 | https://github.com/JetBrains/compose-multiplatform | Compose Multiplatform Team |
| org.jetbrains.compose.animation:animation-android | 1.12.0 | Apache-2.0 | https://github.com/JetBrains/compose-multiplatform | Compose Multiplatform Team |
| org.jetbrains.compose.animation:animation-core | 1.12.0 | Apache-2.0 | https://github.com/JetBrains/compose-multiplatform | Compose Multiplatform Team |
| org.jetbrains.compose.animation:animation-core-android | 1.12.0 | Apache-2.0 | https://github.com/JetBrains/compose-multiplatform | Compose Multiplatform Team |
| org.jetbrains.compose.foundation:foundation | 1.12.0 | Apache-2.0 | https://github.com/JetBrains/compose-multiplatform | Compose Multiplatform Team |
| org.jetbrains.compose.foundation:foundation-android | 1.12.0 | Apache-2.0 | https://github.com/JetBrains/compose-multiplatform | Compose Multiplatform Team |
| org.jetbrains.compose.foundation:foundation-layout | 1.12.0 | Apache-2.0 | https://github.com/JetBrains/compose-multiplatform | Compose Multiplatform Team |
| org.jetbrains.compose.foundation:foundation-layout-android | 1.12.0 | Apache-2.0 | https://github.com/JetBrains/compose-multiplatform | Compose Multiplatform Team |
| org.jetbrains.compose.runtime:runtime | 1.12.0 | Apache-2.0 | https://github.com/JetBrains/compose-multiplatform | Compose Multiplatform Team |
| org.jetbrains.compose.runtime:runtime-android | 1.12.0 | Apache-2.0 | https://github.com/JetBrains/compose-multiplatform | Compose Multiplatform Team |
| org.jetbrains.compose.runtime:runtime-saveable | 1.12.0 | Apache-2.0 | https://github.com/JetBrains/compose-multiplatform | Compose Multiplatform Team |
| org.jetbrains.compose.runtime:runtime-saveable-android | 1.12.0 | Apache-2.0 | https://github.com/JetBrains/compose-multiplatform | Compose Multiplatform Team |
| org.jetbrains.compose.ui:ui | 1.12.0 | Apache-2.0 | https://github.com/JetBrains/compose-multiplatform | Compose Multiplatform Team |
| org.jetbrains.compose.ui:ui-android | 1.12.0 | Apache-2.0 | https://github.com/JetBrains/compose-multiplatform | Compose Multiplatform Team |
| org.jetbrains.compose.ui:ui-geometry | 1.12.0 | Apache-2.0 | https://github.com/JetBrains/compose-multiplatform | Compose Multiplatform Team |
| org.jetbrains.compose.ui:ui-geometry-android | 1.12.0 | Apache-2.0 | https://github.com/JetBrains/compose-multiplatform | Compose Multiplatform Team |
| org.jetbrains.compose.ui:ui-graphics | 1.12.0 | Apache-2.0 | https://github.com/JetBrains/compose-multiplatform | Compose Multiplatform Team |
| org.jetbrains.compose.ui:ui-graphics-android | 1.12.0 | Apache-2.0 | https://github.com/JetBrains/compose-multiplatform | Compose Multiplatform Team |
| org.jetbrains.compose.ui:ui-text | 1.12.0 | Apache-2.0 | https://github.com/JetBrains/compose-multiplatform | Compose Multiplatform Team |
| org.jetbrains.compose.ui:ui-text-android | 1.12.0 | Apache-2.0 | https://github.com/JetBrains/compose-multiplatform | Compose Multiplatform Team |
| org.jetbrains.compose.ui:ui-unit | 1.12.0 | Apache-2.0 | https://github.com/JetBrains/compose-multiplatform | Compose Multiplatform Team |
| org.jetbrains.compose.ui:ui-unit-android | 1.12.0 | Apache-2.0 | https://github.com/JetBrains/compose-multiplatform | Compose Multiplatform Team |
| org.jetbrains.compose.ui:ui-util | 1.12.0 | Apache-2.0 | https://github.com/JetBrains/compose-multiplatform | Compose Multiplatform Team |
| org.jetbrains.compose.ui:ui-util-android | 1.12.0 | Apache-2.0 | https://github.com/JetBrains/compose-multiplatform | Compose Multiplatform Team |
| org.jetbrains.kotlin:kotlin-stdlib | 2.4.21 | Apache-2.0 | https://kotlinlang.org/ | Kotlin Team |
| org.jetbrains.kotlin:kotlin-stdlib-jdk7 | 1.8.21 | Apache-2.0 | https://kotlinlang.org/ | Kotlin Team |
| org.jetbrains.kotlin:kotlin-stdlib-jdk8 | 1.8.21 | Apache-2.0 | https://kotlinlang.org/ | Kotlin Team |
| org.jetbrains.kotlinx:kotlinx-coroutines-android | 1.11.0 | Apache-2.0 | https://github.com/Kotlin/kotlinx.coroutines | JetBrains Team |
| org.jetbrains.kotlinx:kotlinx-coroutines-core | 1.11.0 | Apache-2.0 | https://github.com/Kotlin/kotlinx.coroutines | JetBrains Team |
| org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm | 1.11.0 | Apache-2.0 | https://github.com/Kotlin/kotlinx.coroutines | JetBrains Team |
| org.jetbrains.kotlinx:kotlinx-serialization-core | 1.12.0 | Apache-2.0 | https://github.com/Kotlin/kotlinx.serialization | JetBrains Team |
| org.jetbrains.kotlinx:kotlinx-serialization-core-jvm | 1.12.0 | Apache-2.0 | https://github.com/Kotlin/kotlinx.serialization | JetBrains Team |
| org.jetbrains.kotlinx:kotlinx-serialization-json | 1.12.0 | Apache-2.0 | https://github.com/Kotlin/kotlinx.serialization | JetBrains Team |
| org.jetbrains.kotlinx:kotlinx-serialization-json-jvm | 1.12.0 | Apache-2.0 | https://github.com/Kotlin/kotlinx.serialization | JetBrains Team |
| org.jspecify:jspecify | 1.0.0 | Apache-2.0 | http://jspecify.org/ | Kevin Bourrillion |

### Lizenz nicht ermittelbar

Keine.

<!-- oss:end -->

## Lizenztexte

Vollständige, unveränderte Texte der oben genannten Lizenzen, je Lizenzkennung einmal. Bei Abhängigkeiten mit mehreren Lizenzen (`OR`) gilt jeweils eine davon; hier sind alle vorkommenden Texte aufgeführt. `net.java.dev.jna:jna` steht doppelt unter LGPL-2.1-or-later und Apache-2.0; CrunchyMate nutzt es unter Apache-2.0. Die Texte stammen von https://www.apache.org/licenses/LICENSE-2.0.txt, https://cdn.jsdelivr.net/npm/flag-icons@7.6.0/LICENSE und https://github.com/spdx/license-list-data.

### Apache-2.0

Apache License 2.0

```text

                                 Apache License
                           Version 2.0, January 2004
                        http://www.apache.org/licenses/

   TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION

   1. Definitions.

      "License" shall mean the terms and conditions for use, reproduction,
      and distribution as defined by Sections 1 through 9 of this document.

      "Licensor" shall mean the copyright owner or entity authorized by
      the copyright owner that is granting the License.

      "Legal Entity" shall mean the union of the acting entity and all
      other entities that control, are controlled by, or are under common
      control with that entity. For the purposes of this definition,
      "control" means (i) the power, direct or indirect, to cause the
      direction or management of such entity, whether by contract or
      otherwise, or (ii) ownership of fifty percent (50%) or more of the
      outstanding shares, or (iii) beneficial ownership of such entity.

      "You" (or "Your") shall mean an individual or Legal Entity
      exercising permissions granted by this License.

      "Source" form shall mean the preferred form for making modifications,
      including but not limited to software source code, documentation
      source, and configuration files.

      "Object" form shall mean any form resulting from mechanical
      transformation or translation of a Source form, including but
      not limited to compiled object code, generated documentation,
      and conversions to other media types.

      "Work" shall mean the work of authorship, whether in Source or
      Object form, made available under the License, as indicated by a
      copyright notice that is included in or attached to the work
      (an example is provided in the Appendix below).

      "Derivative Works" shall mean any work, whether in Source or Object
      form, that is based on (or derived from) the Work and for which the
      editorial revisions, annotations, elaborations, or other modifications
      represent, as a whole, an original work of authorship. For the purposes
      of this License, Derivative Works shall not include works that remain
      separable from, or merely link (or bind by name) to the interfaces of,
      the Work and Derivative Works thereof.

      "Contribution" shall mean any work of authorship, including
      the original version of the Work and any modifications or additions
      to that Work or Derivative Works thereof, that is intentionally
      submitted to Licensor for inclusion in the Work by the copyright owner
      or by an individual or Legal Entity authorized to submit on behalf of
      the copyright owner. For the purposes of this definition, "submitted"
      means any form of electronic, verbal, or written communication sent
      to the Licensor or its representatives, including but not limited to
      communication on electronic mailing lists, source code control systems,
      and issue tracking systems that are managed by, or on behalf of, the
      Licensor for the purpose of discussing and improving the Work, but
      excluding communication that is conspicuously marked or otherwise
      designated in writing by the copyright owner as "Not a Contribution."

      "Contributor" shall mean Licensor and any individual or Legal Entity
      on behalf of whom a Contribution has been received by Licensor and
      subsequently incorporated within the Work.

   2. Grant of Copyright License. Subject to the terms and conditions of
      this License, each Contributor hereby grants to You a perpetual,
      worldwide, non-exclusive, no-charge, royalty-free, irrevocable
      copyright license to reproduce, prepare Derivative Works of,
      publicly display, publicly perform, sublicense, and distribute the
      Work and such Derivative Works in Source or Object form.

   3. Grant of Patent License. Subject to the terms and conditions of
      this License, each Contributor hereby grants to You a perpetual,
      worldwide, non-exclusive, no-charge, royalty-free, irrevocable
      (except as stated in this section) patent license to make, have made,
      use, offer to sell, sell, import, and otherwise transfer the Work,
      where such license applies only to those patent claims licensable
      by such Contributor that are necessarily infringed by their
      Contribution(s) alone or by combination of their Contribution(s)
      with the Work to which such Contribution(s) was submitted. If You
      institute patent litigation against any entity (including a
      cross-claim or counterclaim in a lawsuit) alleging that the Work
      or a Contribution incorporated within the Work constitutes direct
      or contributory patent infringement, then any patent licenses
      granted to You under this License for that Work shall terminate
      as of the date such litigation is filed.

   4. Redistribution. You may reproduce and distribute copies of the
      Work or Derivative Works thereof in any medium, with or without
      modifications, and in Source or Object form, provided that You
      meet the following conditions:

      (a) You must give any other recipients of the Work or
          Derivative Works a copy of this License; and

      (b) You must cause any modified files to carry prominent notices
          stating that You changed the files; and

      (c) You must retain, in the Source form of any Derivative Works
          that You distribute, all copyright, patent, trademark, and
          attribution notices from the Source form of the Work,
          excluding those notices that do not pertain to any part of
          the Derivative Works; and

      (d) If the Work includes a "NOTICE" text file as part of its
          distribution, then any Derivative Works that You distribute must
          include a readable copy of the attribution notices contained
          within such NOTICE file, excluding those notices that do not
          pertain to any part of the Derivative Works, in at least one
          of the following places: within a NOTICE text file distributed
          as part of the Derivative Works; within the Source form or
          documentation, if provided along with the Derivative Works; or,
          within a display generated by the Derivative Works, if and
          wherever such third-party notices normally appear. The contents
          of the NOTICE file are for informational purposes only and
          do not modify the License. You may add Your own attribution
          notices within Derivative Works that You distribute, alongside
          or as an addendum to the NOTICE text from the Work, provided
          that such additional attribution notices cannot be construed
          as modifying the License.

      You may add Your own copyright statement to Your modifications and
      may provide additional or different license terms and conditions
      for use, reproduction, or distribution of Your modifications, or
      for any such Derivative Works as a whole, provided Your use,
      reproduction, and distribution of the Work otherwise complies with
      the conditions stated in this License.

   5. Submission of Contributions. Unless You explicitly state otherwise,
      any Contribution intentionally submitted for inclusion in the Work
      by You to the Licensor shall be under the terms and conditions of
      this License, without any additional terms or conditions.
      Notwithstanding the above, nothing herein shall supersede or modify
      the terms of any separate license agreement you may have executed
      with Licensor regarding such Contributions.

   6. Trademarks. This License does not grant permission to use the trade
      names, trademarks, service marks, or product names of the Licensor,
      except as required for reasonable and customary use in describing the
      origin of the Work and reproducing the content of the NOTICE file.

   7. Disclaimer of Warranty. Unless required by applicable law or
      agreed to in writing, Licensor provides the Work (and each
      Contributor provides its Contributions) on an "AS IS" BASIS,
      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
      implied, including, without limitation, any warranties or conditions
      of TITLE, NON-INFRINGEMENT, MERCHANTABILITY, or FITNESS FOR A
      PARTICULAR PURPOSE. You are solely responsible for determining the
      appropriateness of using or redistributing the Work and assume any
      risks associated with Your exercise of permissions under this License.

   8. Limitation of Liability. In no event and under no legal theory,
      whether in tort (including negligence), contract, or otherwise,
      unless required by applicable law (such as deliberate and grossly
      negligent acts) or agreed to in writing, shall any Contributor be
      liable to You for damages, including any direct, indirect, special,
      incidental, or consequential damages of any character arising as a
      result of this License or out of the use or inability to use the
      Work (including but not limited to damages for loss of goodwill,
      work stoppage, computer failure or malfunction, or any and all
      other commercial damages or losses), even if such Contributor
      has been advised of the possibility of such damages.

   9. Accepting Warranty or Additional Liability. While redistributing
      the Work or Derivative Works thereof, You may choose to offer,
      and charge a fee for, acceptance of support, warranty, indemnity,
      or other liability obligations and/or rights consistent with this
      License. However, in accepting such obligations, You may act only
      on Your own behalf and on Your sole responsibility, not on behalf
      of any other Contributor, and only if You agree to indemnify,
      defend, and hold each Contributor harmless for any liability
      incurred by, or claims asserted against, such Contributor by reason
      of your accepting any such warranty or additional liability.

   END OF TERMS AND CONDITIONS

   APPENDIX: How to apply the Apache License to your work.

      To apply the Apache License to your work, attach the following
      boilerplate notice, with the fields enclosed by brackets "[]"
      replaced with your own identifying information. (Don't include
      the brackets!)  The text should be enclosed in the appropriate
      comment syntax for the file format. We also recommend that a
      file or class name and description of purpose be included on the
      same "printed page" as the copyright notice for easier
      identification within third-party archives.

   Copyright [yyyy] [name of copyright owner]

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
```

### MIT

MIT-Lizenz (SPDX-Vorlage; die Copyright-Inhaber stehen je Abhängigkeit in den Tabellen oben)

```text
MIT License

Copyright (c) <year> <copyright holders>

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
associated documentation files (the "Software"), to deal in the Software without restriction, including
without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the
following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial
portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO
EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE
USE OR OTHER DEALINGS IN THE SOFTWARE.
```

### MIT (flag-icons)

MIT-Lizenz von flag-icons 7.6.0 (Länderflaggen)

```text
The MIT License (MIT)

Copyright (c) 2013 Panayiotis Lipiridis

Permission is hereby granted, free of charge, to any person obtaining a copy of
this software and associated documentation files (the "Software"), to deal in
the Software without restriction, including without limitation the rights to
use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
of the Software, and to permit persons to whom the Software is furnished to do
so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

### BSD-3-Clause

BSD-3-Clause (SPDX-Vorlage; Copyright-Inhaber siehe Tabellen)

```text
Copyright (c) <year> <owner>. 

Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.

3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
```

### ISC

ISC (SPDX-Vorlage; Copyright-Inhaber siehe Tabellen)

```text
ISC License

Copyright <year> <owner> 

Permission to use, copy, modify, and/or distribute this software for any purpose with or without fee is hereby granted, provided that the above copyright notice and this permission notice appear in all copies.

THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
```

### Unicode-3.0

Unicode-3.0

```text
UNICODE LICENSE V3

COPYRIGHT AND PERMISSION NOTICE

Copyright © 1991-2023 Unicode, Inc.

NOTICE TO USER: Carefully read the following legal agreement. BY
DOWNLOADING, INSTALLING, COPYING OR OTHERWISE USING DATA FILES, AND/OR
SOFTWARE, YOU UNEQUIVOCALLY ACCEPT, AND AGREE TO BE BOUND BY, ALL OF THE
TERMS AND CONDITIONS OF THIS AGREEMENT. IF YOU DO NOT AGREE, DO NOT
DOWNLOAD, INSTALL, COPY, DISTRIBUTE OR USE THE DATA FILES OR SOFTWARE.

Permission is hereby granted, free of charge, to any person obtaining a
copy of data files and any associated documentation (the "Data Files") or
software and any associated documentation (the "Software") to deal in the
Data Files or Software without restriction, including without limitation
the rights to use, copy, modify, merge, publish, distribute, and/or sell
copies of the Data Files or Software, and to permit persons to whom the
Data Files or Software are furnished to do so, provided that either (a)
this copyright and permission notice appear with all copies of the Data
Files or Software, or (b) this copyright and permission notice appear in
associated Documentation.

THE DATA FILES AND SOFTWARE ARE PROVIDED "AS IS", WITHOUT WARRANTY OF ANY
KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT OF
THIRD PARTY RIGHTS.

IN NO EVENT SHALL THE COPYRIGHT HOLDER OR HOLDERS INCLUDED IN THIS NOTICE
BE LIABLE FOR ANY CLAIM, OR ANY SPECIAL INDIRECT OR CONSEQUENTIAL DAMAGES,
OR ANY DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS,
WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION,
ARISING OUT OF OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THE DATA
FILES OR SOFTWARE.

Except as contained in this notice, the name of a copyright holder shall
not be used in advertising or otherwise to promote the sale, use or other
dealings in these Data Files or Software without prior written
authorization of the copyright holder.
```

### Zlib

Zlib (SPDX-Vorlage; Copyright-Inhaber siehe Tabellen)

```text
zlib License

This software is provided 'as-is', without any express or implied warranty.  In no event will the authors be held liable for any damages arising from the use of this software.

Permission is granted to anyone to use this software for any purpose, including commercial applications, and to alter it and redistribute it freely, subject to the following restrictions:

     1. The origin of this software must not be misrepresented; you must not claim that you wrote the original software. If you use this software in a product, an acknowledgment in the product documentation would be appreciated but is not required.

     2. Altered source versions must be plainly marked as such, and must not be misrepresented as being the original software.

     3. This notice may not be removed or altered from any source distribution.
```

### MPL-2.0

Mozilla Public License 2.0

```text
Mozilla Public License Version 2.0
==================================

1. Definitions
--------------

1.1. "Contributor"
    means each individual or legal entity that creates, contributes to
    the creation of, or owns Covered Software.

1.2. "Contributor Version"
    means the combination of the Contributions of others (if any) used
    by a Contributor and that particular Contributor's Contribution.

1.3. "Contribution"
    means Covered Software of a particular Contributor.

1.4. "Covered Software"
    means Source Code Form to which the initial Contributor has attached
    the notice in Exhibit A, the Executable Form of such Source Code
    Form, and Modifications of such Source Code Form, in each case
    including portions thereof.

1.5. "Incompatible With Secondary Licenses"
    means

    (a) that the initial Contributor has attached the notice described
        in Exhibit B to the Covered Software; or

    (b) that the Covered Software was made available under the terms of
        version 1.1 or earlier of the License, but not also under the
        terms of a Secondary License.

1.6. "Executable Form"
    means any form of the work other than Source Code Form.

1.7. "Larger Work"
    means a work that combines Covered Software with other material, in 
    a separate file or files, that is not Covered Software.

1.8. "License"
    means this document.

1.9. "Licensable"
    means having the right to grant, to the maximum extent possible,
    whether at the time of the initial grant or subsequently, any and
    all of the rights conveyed by this License.

1.10. "Modifications"
    means any of the following:

    (a) any file in Source Code Form that results from an addition to,
        deletion from, or modification of the contents of Covered
        Software; or

    (b) any new file in Source Code Form that contains any Covered
        Software.

1.11. "Patent Claims" of a Contributor
    means any patent claim(s), including without limitation, method,
    process, and apparatus claims, in any patent Licensable by such
    Contributor that would be infringed, but for the grant of the
    License, by the making, using, selling, offering for sale, having
    made, import, or transfer of either its Contributions or its
    Contributor Version.

1.12. "Secondary License"
    means either the GNU General Public License, Version 2.0, the GNU
    Lesser General Public License, Version 2.1, the GNU Affero General
    Public License, Version 3.0, or any later versions of those
    licenses.

1.13. "Source Code Form"
    means the form of the work preferred for making modifications.

1.14. "You" (or "Your")
    means an individual or a legal entity exercising rights under this
    License. For legal entities, "You" includes any entity that
    controls, is controlled by, or is under common control with You. For
    purposes of this definition, "control" means (a) the power, direct
    or indirect, to cause the direction or management of such entity,
    whether by contract or otherwise, or (b) ownership of more than
    fifty percent (50%) of the outstanding shares or beneficial
    ownership of such entity.

2. License Grants and Conditions
--------------------------------

2.1. Grants

Each Contributor hereby grants You a world-wide, royalty-free,
non-exclusive license:

(a) under intellectual property rights (other than patent or trademark)
    Licensable by such Contributor to use, reproduce, make available,
    modify, display, perform, distribute, and otherwise exploit its
    Contributions, either on an unmodified basis, with Modifications, or
    as part of a Larger Work; and

(b) under Patent Claims of such Contributor to make, use, sell, offer
    for sale, have made, import, and otherwise transfer either its
    Contributions or its Contributor Version.

2.2. Effective Date

The licenses granted in Section 2.1 with respect to any Contribution
become effective for each Contribution on the date the Contributor first
distributes such Contribution.

2.3. Limitations on Grant Scope

The licenses granted in this Section 2 are the only rights granted under
this License. No additional rights or licenses will be implied from the
distribution or licensing of Covered Software under this License.
Notwithstanding Section 2.1(b) above, no patent license is granted by a
Contributor:

(a) for any code that a Contributor has removed from Covered Software;
    or

(b) for infringements caused by: (i) Your and any other third party's
    modifications of Covered Software, or (ii) the combination of its
    Contributions with other software (except as part of its Contributor
    Version); or

(c) under Patent Claims infringed by Covered Software in the absence of
    its Contributions.

This License does not grant any rights in the trademarks, service marks,
or logos of any Contributor (except as may be necessary to comply with
the notice requirements in Section 3.4).

2.4. Subsequent Licenses

No Contributor makes additional grants as a result of Your choice to
distribute the Covered Software under a subsequent version of this
License (see Section 10.2) or under the terms of a Secondary License (if
permitted under the terms of Section 3.3).

2.5. Representation

Each Contributor represents that the Contributor believes its
Contributions are its original creation(s) or it has sufficient rights
to grant the rights to its Contributions conveyed by this License.

2.6. Fair Use

This License is not intended to limit any rights You have under
applicable copyright doctrines of fair use, fair dealing, or other
equivalents.

2.7. Conditions

Sections 3.1, 3.2, 3.3, and 3.4 are conditions of the licenses granted
in Section 2.1.

3. Responsibilities
-------------------

3.1. Distribution of Source Form

All distribution of Covered Software in Source Code Form, including any
Modifications that You create or to which You contribute, must be under
the terms of this License. You must inform recipients that the Source
Code Form of the Covered Software is governed by the terms of this
License, and how they can obtain a copy of this License. You may not
attempt to alter or restrict the recipients' rights in the Source Code
Form.

3.2. Distribution of Executable Form

If You distribute Covered Software in Executable Form then:

(a) such Covered Software must also be made available in Source Code
    Form, as described in Section 3.1, and You must inform recipients of
    the Executable Form how they can obtain a copy of such Source Code
    Form by reasonable means in a timely manner, at a charge no more
    than the cost of distribution to the recipient; and

(b) You may distribute such Executable Form under the terms of this
    License, or sublicense it under different terms, provided that the
    license for the Executable Form does not attempt to limit or alter
    the recipients' rights in the Source Code Form under this License.

3.3. Distribution of a Larger Work

You may create and distribute a Larger Work under terms of Your choice,
provided that You also comply with the requirements of this License for
the Covered Software. If the Larger Work is a combination of Covered
Software with a work governed by one or more Secondary Licenses, and the
Covered Software is not Incompatible With Secondary Licenses, this
License permits You to additionally distribute such Covered Software
under the terms of such Secondary License(s), so that the recipient of
the Larger Work may, at their option, further distribute the Covered
Software under the terms of either this License or such Secondary
License(s).

3.4. Notices

You may not remove or alter the substance of any license notices
(including copyright notices, patent notices, disclaimers of warranty,
or limitations of liability) contained within the Source Code Form of
the Covered Software, except that You may alter any license notices to
the extent required to remedy known factual inaccuracies.

3.5. Application of Additional Terms

You may choose to offer, and to charge a fee for, warranty, support,
indemnity or liability obligations to one or more recipients of Covered
Software. However, You may do so only on Your own behalf, and not on
behalf of any Contributor. You must make it absolutely clear that any
such warranty, support, indemnity, or liability obligation is offered by
You alone, and You hereby agree to indemnify every Contributor for any
liability incurred by such Contributor as a result of warranty, support,
indemnity or liability terms You offer. You may include additional
disclaimers of warranty and limitations of liability specific to any
jurisdiction.

4. Inability to Comply Due to Statute or Regulation
---------------------------------------------------

If it is impossible for You to comply with any of the terms of this
License with respect to some or all of the Covered Software due to
statute, judicial order, or regulation then You must: (a) comply with
the terms of this License to the maximum extent possible; and (b)
describe the limitations and the code they affect. Such description must
be placed in a text file included with all distributions of the Covered
Software under this License. Except to the extent prohibited by statute
or regulation, such description must be sufficiently detailed for a
recipient of ordinary skill to be able to understand it.

5. Termination
--------------

5.1. The rights granted under this License will terminate automatically
if You fail to comply with any of its terms. However, if You become
compliant, then the rights granted under this License from a particular
Contributor are reinstated (a) provisionally, unless and until such
Contributor explicitly and finally terminates Your grants, and (b) on an
ongoing basis, if such Contributor fails to notify You of the
non-compliance by some reasonable means prior to 60 days after You have
come back into compliance. Moreover, Your grants from a particular
Contributor are reinstated on an ongoing basis if such Contributor
notifies You of the non-compliance by some reasonable means, this is the
first time You have received notice of non-compliance with this License
from such Contributor, and You become compliant prior to 30 days after
Your receipt of the notice.

5.2. If You initiate litigation against any entity by asserting a patent
infringement claim (excluding declaratory judgment actions,
counter-claims, and cross-claims) alleging that a Contributor Version
directly or indirectly infringes any patent, then the rights granted to
You by any and all Contributors for the Covered Software under Section
2.1 of this License shall terminate.

5.3. In the event of termination under Sections 5.1 or 5.2 above, all
end user license agreements (excluding distributors and resellers) which
have been validly granted by You or Your distributors under this License
prior to termination shall survive termination.

************************************************************************
*                                                                      *
*  6. Disclaimer of Warranty                                           *
*  -------------------------                                           *
*                                                                      *
*  Covered Software is provided under this License on an "as is"       *
*  basis, without warranty of any kind, either expressed, implied, or  *
*  statutory, including, without limitation, warranties that the       *
*  Covered Software is free of defects, merchantable, fit for a        *
*  particular purpose or non-infringing. The entire risk as to the     *
*  quality and performance of the Covered Software is with You.        *
*  Should any Covered Software prove defective in any respect, You     *
*  (not any Contributor) assume the cost of any necessary servicing,   *
*  repair, or correction. This disclaimer of warranty constitutes an   *
*  essential part of this License. No use of any Covered Software is   *
*  authorized under this License except under this disclaimer.         *
*                                                                      *
************************************************************************

************************************************************************
*                                                                      *
*  7. Limitation of Liability                                          *
*  --------------------------                                          *
*                                                                      *
*  Under no circumstances and under no legal theory, whether tort      *
*  (including negligence), contract, or otherwise, shall any           *
*  Contributor, or anyone who distributes Covered Software as          *
*  permitted above, be liable to You for any direct, indirect,         *
*  special, incidental, or consequential damages of any character      *
*  including, without limitation, damages for lost profits, loss of    *
*  goodwill, work stoppage, computer failure or malfunction, or any    *
*  and all other commercial damages or losses, even if such party      *
*  shall have been informed of the possibility of such damages. This   *
*  limitation of liability shall not apply to liability for death or   *
*  personal injury resulting from such party's negligence to the       *
*  extent applicable law prohibits such limitation. Some               *
*  jurisdictions do not allow the exclusion or limitation of           *
*  incidental or consequential damages, so this exclusion and          *
*  limitation may not apply to You.                                    *
*                                                                      *
************************************************************************

8. Litigation
-------------

Any litigation relating to this License may be brought only in the
courts of a jurisdiction where the defendant maintains its principal
place of business and such litigation shall be governed by laws of that
jurisdiction, without reference to its conflict-of-law provisions.
Nothing in this Section shall prevent a party's ability to bring
cross-claims or counter-claims.

9. Miscellaneous
----------------

This License represents the complete agreement concerning the subject
matter hereof. If any provision of this License is held to be
unenforceable, such provision shall be reformed only to the extent
necessary to make it enforceable. Any law or regulation which provides
that the language of a contract shall be construed against the drafter
shall not be used to construe this License against a Contributor.

10. Versions of the License
---------------------------

10.1. New Versions

Mozilla Foundation is the license steward. Except as provided in Section
10.3, no one other than the license steward has the right to modify or
publish new versions of this License. Each version will be given a
distinguishing version number.

10.2. Effect of New Versions

You may distribute the Covered Software under the terms of the version
of the License under which You originally received the Covered Software,
or under the terms of any subsequent version published by the license
steward.

10.3. Modified Versions

If you create software not governed by this License, and you want to
create a new license for such software, you may create and use a
modified version of this License if you rename the license and remove
any references to the name of the license steward (except to note that
such modified license differs from this License).

10.4. Distributing Source Code Form that is Incompatible With Secondary
Licenses

If You choose to distribute Source Code Form that is Incompatible With
Secondary Licenses under the terms of this version of the License, the
notice described in Exhibit B of this License must be attached.

Exhibit A - Source Code Form License Notice
-------------------------------------------

  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at https://mozilla.org/MPL/2.0/.

If it is not possible or desirable to put the notice in a particular
file, then You may include the notice in a location (such as a LICENSE
file in a relevant directory) where a recipient would be likely to look
for such a notice.

You may add additional accurate notices of copyright ownership.

Exhibit B - "Incompatible With Secondary Licenses" Notice
---------------------------------------------------------

  This Source Code Form is "Incompatible With Secondary Licenses", as
  defined by the Mozilla Public License, v. 2.0.
```

### 0BSD

0BSD

```text
Copyright (C) YEAR by AUTHOR EMAIL

Permission to use, copy, modify, and/or distribute this software for any purpose with or without fee is hereby granted.

THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
```

### BSL-1.0

BSL-1.0 (Boost Software License)

```text
Boost Software License - Version 1.0 - August 17th, 2003

Permission is hereby granted, free of charge, to any person or organization obtaining a copy of the software and accompanying documentation covered by this license (the "Software") to use, reproduce, display, distribute, execute, and transmit the Software, and to prepare derivative works of the Software, and to permit third-parties to whom the Software is furnished to do so, all subject to the following:

The copyright notices in the Software and this entire statement, including the above license grant, this restriction and the following disclaimer, must be included in all copies of the Software, in whole or in part, and all derivative works of the Software, unless such copies or derivative works are solely in the form of machine-executable object code generated by a source language processor.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, TITLE AND NON-INFRINGEMENT. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR ANYONE DISTRIBUTING THE SOFTWARE BE LIABLE FOR ANY DAMAGES OR OTHER LIABILITY, WHETHER IN CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
```

### CDLA-Permissive-2.0

CDLA-Permissive-2.0

```text
Community Data License Agreement - Permissive - Version 2.0

This is the Community Data License Agreement - Permissive, Version 2.0 (the "agreement"). Data Provider(s) and Data Recipient(s) agree as follows:

1. Provision of the Data

1.1. A Data Recipient may use, modify, and share the Data made available by Data Provider(s) under this agreement if that Data Recipient follows the terms of this agreement.

1.2. This agreement does not impose any restriction on a Data Recipient's use, modification, or sharing of any portions of the Data that are in the public domain or that may be used, modified, or shared under any other legal exception or limitation.

2. Conditions for Sharing Data

2.1. A Data Recipient may share Data, with or without modifications, so long as the Data Recipient makes available the text of this agreement with the shared Data.

3. No Restrictions on Results

3.1. This agreement does not impose any restriction or obligations with respect to the use, modification, or sharing of Results.

4. No Warranty; Limitation of Liability

4.1. All Data Recipients receive the Data subject to the following terms:

THE DATA IS PROVIDED ON AN "AS IS" BASIS, WITHOUT REPRESENTATIONS, WARRANTIES OR CONDITIONS OF ANY KIND, EITHER EXPRESS OR IMPLIED INCLUDING, WITHOUT LIMITATION, ANY WARRANTIES OR CONDITIONS OF TITLE, NON-INFRINGEMENT, MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE.

NO DATA PROVIDER SHALL HAVE ANY LIABILITY FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING WITHOUT LIMITATION LOST PROFITS), HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE DATA OR RESULTS, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.

5. Definitions

5.1. "Data" means the material received by a Data Recipient under this agreement.

5.2. "Data Provider" means any person who is the source of Data provided under this agreement and in reliance on a Data Recipient's agreement to its terms.

5.3. "Data Recipient" means any person who receives Data directly or indirectly from a Data Provider and agrees to the terms of this agreement.

5.4. "Results" means any outcome obtained by computational analysis of Data, including for example machine learning models and models' insights.
```

### MIT-0

MIT-0

```text
MIT No Attribution

Copyright <YEAR> <COPYRIGHT HOLDER>

Permission is hereby granted, free of charge, to any person obtaining a copy of this
software and associated documentation files (the "Software"), to deal in the Software
without restriction, including without limitation the rights to use, copy, modify,
merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
permit persons to whom the Software is furnished to do so.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
```

### Unlicense

Unlicense

```text
This is free and unencumbered software released into the public domain.

Anyone is free to copy, modify, publish, use, compile, sell, or distribute this software, either in source code form or as a compiled binary, for any purpose, commercial or non-commercial, and by any means.

In jurisdictions that recognize copyright laws, the author or authors of this software dedicate any and all copyright interest in the software to the public domain. We make this dedication for the benefit of the public at large and to the detriment of our heirs and
successors. We intend this dedication to be an overt act of relinquishment in perpetuity of all present and future rights to this software under copyright law.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

For more information, please refer to <http://unlicense.org/>
```

### LLVM-exception

LLVM-exception (Ausnahme zur Apache License 2.0)

```text
---- LLVM Exceptions to the Apache 2.0 License ----

   As an exception, if, as a result of your compiling your source code, portions
   of this Software are embedded into an Object form of such source code, you
   may redistribute such embedded portions in such Object form without complying
   with the conditions of Sections 4(a), 4(b) and 4(d) of the License.

   In addition, if you combine or link compiled forms of this Software with
   software that is licensed under the GPLv2 ("Combined Software") and if a
   court of competent jurisdiction determines that the patent provision (Section
   3), the indemnity provision (Section 9) or other Section of the License
   conflicts with the conditions of the GPLv2, you may retroactively and
   prospectively choose to deem waived or otherwise exclude such Section(s) of
   the License, but only in their entirety and only with respect to the Combined
   Software.
```
