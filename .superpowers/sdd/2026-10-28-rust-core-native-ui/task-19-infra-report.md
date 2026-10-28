# Task 19: Infrastruktur-Nacharbeiten

Basis: 9ad848c (Worktree-Branch war initial auf dem Initial-Commit und wurde per reset --hard auf 9ad848c gesetzt).

## CI
- ci.yml: push nur auf main, pull_request fuer alle; concurrency je Ref (auf main ohne Abbruch).
- Composite Action .github/actions/setup-android-build (JDK, SDK, NDK, Rust, cargo-ndk, Gradle) in checks.yml und nightly.yml.
- Actions: upload-artifact v7, setup-gradle (gradle/actions) v6; alle node24, Releases aelter als zwei Wochen. Uebrige (checkout v7, setup-java v6, setup-android v4, rust-cache v2) bereits aktuell.
- Nightly baut bewusst neu (Signierung, versionCode/versionName im Manifest); als Kommentar begruendet.
- checks.yml fuehrt zusaetzlich assembleDebugAndroidTest aus.
- Validierung: yaml.safe_load und actionlint 1.7.12 ohne Befunde. Kein Workflow ausgefuehrt.

## Build
- rustJniLibsDir/uniffiOutDir einmal oben definiert, Source-Sets und cargo-ndk -o nutzen sie.
- Task checkRustToolchain (cargo, cargo-ndk, NDK) mit deutschen Meldungen; negativ getestet mit CARGO=/nonexistent/cargo.
- generateFixtureDb: @CacheableTask, cargo @Internal, toolchainSpec (rust-toolchain.toml) als @Input.

## Geraetetests
- run-device-tests.sh: ANDROID_SERIAL, Display/Keyguard-Pruefung, JobScheduler-Pruefung (awk gegen Beispieltext getestet, nicht gegen echtes Geraet), verankertes OK-Muster plus Fehlermarker. bash -n ok.
- TestStorage: Guard gegen crunchymate.db / crunchymate-user.db und images; Runner ruft ihn auf. Kommentar an CrunchyMateApplication (open).

## check-svg
- NAMED_COLORS: alle 148 CSS-Namen (aus PIL) plus currentcolor.
- Marker: stroke-width auch aus style und geerbt, markerUnits, viewBox (ohne Versatz, gleiches Seitenverhaeltnis), refX/refY; orient != 0 weiter nicht unterstuetzt. Gegenprobe convert+verify: mean_diff 0.01, ok.
- Es gibt keine automatisierte Testsuite fuer das Skript; Verifikation per check/convert/verify an Beispiel-SVG.

## Gate
./gradlew lint ktlintCheck testDebugUnitTest assembleDebug assembleRelease assembleDebugAndroidTest: BUILD SUCCESSFUL (5m 9s).

## Bedenken
- JobScheduler-Format variiert je Android-Version; Pruefung ist best effort.
- "stable" in rust-toolchain.toml ist beweglich; Cache-Schluessel erfasst nur die Datei, nicht die rustc-Version.
- checkRustToolchain haengt auch an generateFixtureDb (braucht eigentlich nur cargo).
