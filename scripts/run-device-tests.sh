#!/usr/bin/env bash
# Baut App und Test-APK, installiert beide auf dem angeschlossenen Gerät und führt die Smoke-Tests aus.
# Die Tests nutzen eigene Datenbank- und Bilderpfade; vorhandene Nutzerdaten bleiben unberührt.
#
# Achtung: Der Testprozess läuft unter derselben Paket-ID wie die echte App und teilt sich deshalb deren
# WorkManager-Datenbank. Ein dort geplanter Sync-Job würde im Testprozess ausgeführt und einen echten Sync
# gegen die Produktionsdaten starten. Das Skript bricht daher ab, solange für de.crunchymate ein Job aktiv ist.
#
# Mehrere Geräte: ANDROID_SERIAL setzen (adb.exe aus WSL reicht die Variable nicht weiter, daher -s).
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

app_package="de.crunchymate"
runner="$app_package.test/$app_package.CrunchyMateTestRunner"
apk_dir="app/build/outputs/apk"

adb_cmd=(adb.exe)
if [[ -n "${ANDROID_SERIAL:-}" ]]; then
    adb_cmd+=(-s "$ANDROID_SERIAL")
fi

adb_run() {
    "${adb_cmd[@]}" "$@" | tr -d '\r'
}

fail() {
    echo "Abbruch: $*" >&2
    exit 1
}

check_display_ready() {
    local wakefulness keyguard
    wakefulness="$(adb_run shell dumpsys power | grep -o 'mWakefulness=[A-Za-z]*' | head -n1 || true)"
    [[ "$wakefulness" == "mWakefulness=Awake" ]] ||
        fail "Das Display ist nicht an (${wakefulness:-mWakefulness unbekannt}). Gerät aufwecken und entsperren."
    keyguard="$(adb_run shell dumpsys window | grep -o 'isKeyguardShowing=[a-z]*' | head -n1 || true)"
    [[ "$keyguard" == "isKeyguardShowing=false" ]] ||
        fail "Das Gerät ist gesperrt (${keyguard:-isKeyguardShowing unbekannt}). Bitte entsperren; die UI-Tests brauchen den Bildschirm."
}

# Sucht die Abschnitte "Pending queue" und "Active jobs" (bzw. "Started jobs") und meldet Jobs des App-Pakets.
check_no_app_job_active() {
    local jobs
    jobs="$(adb_run shell dumpsys jobscheduler | awk -v pkg="$app_package" '
        function indent(line) { match(line, /^ */); return RLENGTH }
        /^ *(Pending queue|Active jobs|Started jobs)/ { in_section = 1; base = indent($0); next }
        in_section && NF && indent($0) <= base { in_section = 0 }
        in_section && index($0, pkg "/") { print }
    ')"
    if [[ -n "$jobs" ]]; then
        echo "$jobs" >&2
        fail "Für $app_package ist ein Job aktiv oder wartet (WorkManager-DB wird mit dem Testprozess geteilt). " \
            "Sync abwarten oder die App stoppen: adb shell am force-stop $app_package"
    fi
}

./gradlew assembleDebug assembleDebugAndroidTest

check_display_ready
check_no_app_job_active

# Nur -r: eine Deinstallation würde die echten Nutzerdaten löschen
"${adb_cmd[@]}" install -r "$(wslpath -w "$apk_dir/debug/app-debug.apk")"
"${adb_cmd[@]}" install -r "$(wslpath -w "$apk_dir/androidTest/debug/app-debug-androidTest.apk")"

output="$(adb_run shell am instrument -w "$runner")"
echo "$output"

# Erfolg nur bei verankertem Ergebnismuster und ohne Fehlermarker; am instrument liefert selbst bei Fehlern Exit 0
if grep -Eq 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed' <<<"$output" ||
    ! grep -Eq '^OK \([0-9]+ tests?\)' <<<"$output"; then
    fail "Geräte-Tests fehlgeschlagen oder ohne Ergebnis."
fi
