#!/usr/bin/env bash
# Baut App und Test-APK, installiert beide auf dem angeschlossenen Gerät und führt die Smoke-Tests aus.
# Die Tests nutzen eigene Datenbank- und Bilderpfade; vorhandene Nutzerdaten bleiben unberührt.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

runner="de.crunchymate.test/de.crunchymate.CrunchyMateTestRunner"
apk_dir="app/build/outputs/apk"

./gradlew assembleDebug assembleDebugAndroidTest

# Nur -r: eine Deinstallation würde die echten Nutzerdaten löschen
adb.exe install -r "$(wslpath -w "$apk_dir/debug/app-debug.apk")"
adb.exe install -r "$(wslpath -w "$apk_dir/androidTest/debug/app-debug-androidTest.apk")"

output="$(adb.exe shell am instrument -w "$runner" | tr -d '\r')"
echo "$output"

if ! grep -q "OK (" <<<"$output"; then
    exit 1
fi
