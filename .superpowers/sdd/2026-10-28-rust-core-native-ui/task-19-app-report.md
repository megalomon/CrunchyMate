# Task 19 (App): Bericht

Basis: modernize/rust-core-native-ui (9ad848c). Der Worktree-Branch stand zunächst auf dem alten Initial-Commit und wurde per `git reset --hard 9ad848c` auf die Basis gesetzt.

## Umgesetzt

1. Lizenzbildschirm: Einstellungen -> "Open-Source-Lizenzen" -> `ui/licenses/` (LazyColumn; Gruppen Rust, Android, Icons, Flaggen; danach antippbare Lizenztexte). Das Asset `app/src/main/assets/open_source_notices.json` erzeugt `scripts/gen-oss-notices.py`. Die Markdown-Datei bleibt einzige Quelle: Das Skript liest Icons-, Flaggen- und Lizenztextabschnitte aus ihr ein und bricht bei Abweichung laut ab. Dokumentiert in CLAUDE.md. Tests: OpenSourceNoticesTest (Asset gültig, nicht leer, Lizenztexte auflösbar, Zeilenzahl gleich den Tabellen im Dokument), LicensesViewModelTest, LicensesScreenTest. Beim Neuerzeugen fielen veraltete Zeilen (okhttp, coil-network, kotlin-stdlib-jdk7/8) aus dem Dokument; der Abschnitt "Offen" entfällt.
2. Benachrichtigung: `SyncNotifications.build` setzt `setContentIntent` (PendingIntent.getActivity auf MainActivity, FLAG_IMMUTABLE). SyncWorker unverändert. Test: SyncNotificationsTest.
3. Kern-Start: `AppContainer.initializeCore()` (IO), `AppViewModel` mit Zustand LOADING/READY/FAILED, Fehlerbildschirm mit EmptyState und ic_state_error. `MainActivity.onStop` sichert nur, wenn der Kern geöffnet ist (`flushSettings`). Test mit `FakeCore(failOnCreate=...)` über `coreFactory`.
4. Paging: Refresh-Key auf die bekannte Gesamtzahl geklemmt (`clampOffset`); ein Refresh hinter dem Ende lädt die letzte volle Seite nach; verworfene Duplikate werden zu `itemsAfter` addiert. Tests ergänzt.
5. Tests: `isReturnDefaultValues` entfernt; SettingsStoreTest, SearchViewModelTest, LicensesViewModelTest laufen dafür unter Robolectric. Zeitformat-Test mit fester Locale/Zeitzone. Test-Konstanten auf Dimens. fetchSemanticsNode-Warten ersetzt. Kachel: Tippflächen Gesehen/Meh überlappen nicht mehr (Lücke zwischen den Kreisen je zur Hälfte geteilt, Kreisgeometrie unverändert, Ripple nur auf dem Kreis); Test tippt auf den unteren Rand des Gesehen-Kreises und den oberen des Meh-Kreises.
6. DetailScreen: nichts getan.

## Gate

`./gradlew lint ktlintCheck testDebugUnitTest assembleDebug assembleRelease assembleDebugAndroidTest`: BUILD SUCCESSFUL. Kein Gerätelauf.
