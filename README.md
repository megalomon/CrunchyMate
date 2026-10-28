# CrunchyMate 🍿

Eine lokale Go-Webapp zum Durchsuchen, Filtern und Verfolgen von Anime-Serien, die auf **Crunchyroll Deutschland** verfügbar sind, angereichert mit Bewertungen und Metadaten von **AniList** und gespeichert in einer lokalen **SQLite**-Datenbank.

## Features

- **Crunchyroll DE Sync & AniList Sync**: Über das dedizierte **Einstellungen-Menü** (⚙️) kann sowohl der Crunchyroll DE Katalog als auch die AniList-Anreicherung gezielt gestartet und der Live-Fortschritt verfolgt werden.
- **SQLite Persistenz**: Alle Daten werden lokal in einer SQLite-Datei (`crunchymate.db`) gespeichert (pure Go über `modernc.org/sqlite`, keine CGO-Abhängigkeit).
- **Direkter Multi-State Tag-Filter**:
  - Tags sind in der Suche **direkt sichtbar und antippbar** (ohne Drawer oder umständliches Suchen).
  - 1x Tap = 🔵 Blau (Ranking-Boost)
  - 2x Tap = 🟢 Grün (Muss vorkommen)
  - 3x Tap = 🟠 Orange (Ausgeschlossen)
  - 4x Tap = Neutral (Deaktiviert)
- **Watchlist & Meh-Verwaltung**:
  - **Gesehen (✓)**: Als gesehen markieren und in der dedizierten Gesehen-Ansicht verwalten.
  - **Meh (😒)**: Unerwünschte Anime als Meh markieren – sie werden automatisch aus der normalen Suche ausgeblendet. Über den "Meh"-Reiter in der Navigationsleiste können sie eingesehen und wiederhergestellt werden.
- **Untere Navigationsleiste (Bottom Nav)**: Schneller Wechsel zwischen 🔍 Suche, 👁️ Gesehen, 😒 Meh und ⚙️ Einstellungen.
- **Suche & Filter**:
  - Live-Volltextsuche nach Wörtern und Namen.
  - Filterung nach Genres & Kategorien (z. B. Action, Fantasy, Sci-Fi, Shonen).
  - Filterung nach Status: *Alle*, *Gesehen* (✓) oder *Ungesehen*.
  - Sortierung nach Titel, AniList-Score, Episodenzahl oder Erscheinungsjahr.
- **Detailansicht**: Klick auf eine Kachel öffnet eine Übersicht mit Banner, Postern, deutscher Synopsis, Metadaten und AniList-Details.
- **Watchlist-Tracker**: Direktes Antippen des Häkchens auf der Kachel oder in der Detailansicht zum Umschalten ("schon gesehen").

## Starten

### 1. Direkt ausführen:
```bash
./crunchyMate
```

Standardmäßig startet der Server auf Port `8080` und synchronisiert den Katalog im Hintergrund.

Im Browser aufrufen:
👉 **[http://localhost:8080](http://localhost:8080)**

### Optionale Flags:
```bash
# Anderen Port wählen (z. B. 3000)
./crunchyMate -port 3000

# Eigene SQLite-Datei angeben
./crunchyMate -db /pfad/zu/meine_animes.db

# Ohne automatischen Startup-Sync starten
./crunchyMate -sync-on-start=false
```

### 2. Neu kompilieren (falls Code angepasst wird):
```bash
go build -o crunchyMate ./cmd/server
```

## Tests ausführen

### Go Backend Tests
```bash
go test ./... -v
```

### Web Adapter Tests
```bash
node web/adapter_test.js
```

### Android Unit Tests
```bash
./android/gradlew -p android testDebugUnitTest
```

## Standalone Android App (APK) 📱

Neben dem Go-Webserver bietet CrunchyMate eine vollständige, eigenständige Android-App im Verzeichnis `android/`. Sie benötigt **keinen lokalen Server** und führt Datenbank, Synchronisation und Web-UI nativ auf dem Smartphone aus.

### Architektur

- **Dual-Mode Web Frontend (`web/static/app.js`)**: Erkennt automatisch, ob `window.AndroidBridge` vorhanden ist. Falls ja, werden Aufrufe über die native Brücke geleitet; andernfalls via Standard-`fetch()` an den Go-Server.
- **Native SQLite-Datenbank (`AnimeRepository`)**: Verwendet Android SQLite mit identischem Schema und Indizes wie das Go-Backend für Filterung, Tags, Suche, Gesehen- und Meh-Status.
- **Native Synchronisation (`SyncManager`, `CrunchyrollClient`, `AniListClient`)**: Lädt den Crunchyroll DE Katalog und reichert Einträge über die AniList GraphQL API an (inklusive Exponential Backoff bei HTTP 429 Rate-Limits).
- **Foreground Service (`SyncForegroundService`)**: Führt langlaufende Synchronisationen stabil im Hintergrund mit Android-Fortschrittsbenachrichtigung aus (`dataSync`).
- **WebView & Asset Loader (`MainActivity`)**: Nutzt AndroidX `WebViewAssetLoader`, um Web-Assets isoliert und performant unter `https://appassets.androidplatform.net/assets/` darzustellen.

### Bauen & Installieren

Das Projekt kann direkt in **Android Studio** geöffnet oder per Kommandozeile gebaut werden:

> [!NOTE]
> Die Datei `android/gradle.properties` definiert standardmäßig `android.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2` für Builds direkt in der Termux-Umgebung auf Android. Bei Builds auf Desktop-Systemen (Linux/macOS/Windows) oder in Android Studio kann diese Zeile auskommentiert oder weggelassen werden.

```bash
# Debug-APK kompilieren
./android/gradlew -p android assembleDebug
```

Die kompilierte APK befindet sich anschließend unter:
`android/app/build/outputs/apk/debug/app-debug.apk`

Installation auf einem verbundenen Android-Gerät oder Emulator:
```bash
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

