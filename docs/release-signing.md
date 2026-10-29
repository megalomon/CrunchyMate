# Release-Signierung

Die Nightly-Builds und alle Release-APKs (`nightly.yml`, `release.yml`) werden mit einem eigenen Schlüssel signiert. Android akzeptiert Updates nur, wenn sie mit demselben Schlüssel signiert sind wie die installierte App. Geht der Schlüssel verloren, lässt sich die App nicht mehr aktualisieren.

## Keystore erzeugen

```bash
keytool -genkeypair -v \
  -keystore release.jks \
  -storetype PKCS12 \
  -alias crunchymate \
  -keyalg RSA -keysize 4096 \
  -validity 10000
```

`keytool` fragt nach dem Keystore-Passwort und den Angaben zum Zertifikat. Bei PKCS12 gelten Keystore- und Schlüsselpasswort als dasselbe; am einfachsten trägt man beide Secrets gleich ein.

## Prüfen

```bash
keytool -list -v -keystore release.jks
```

Kontrolliert werden Alias, Schlüsselalgorithmus (RSA 4096) und Gültigkeit (mindestens bis 2053). Den SHA-256-Fingerabdruck sollte man sich notieren, um spätere APKs gegen ihn zu prüfen.

## Sicherung

- Die Datei `release.jks` und die Passwörter getrennt voneinander an mindestens zwei sicheren Orten ablegen, zum Beispiel im Passwortmanager und auf einem verschlüsselten Datenträger.
- Den Keystore niemals ins Repository einchecken.
- Nach dem Hochladen der Secrets die lokale Kopie nicht löschen; GitHub-Secrets lassen sich nicht wieder auslesen.

## Mehrere Schlüssel in einem Keystore

Ein Keystore kann mehrere Schlüssel enthalten, einen Alias pro App. Einen weiteren Schlüssel legt man im selben Keystore an:

```bash
keytool -genkeypair -v \
  -keystore release.jks \
  -storetype PKCS12 \
  -alias andere-app \
  -keyalg RSA -keysize 4096 \
  -validity 10000
```

Welcher Alias verwendet wird, bestimmt das Secret `SIGNING_KEY_ALIAS` des jeweiligen Repositorys. So können mehrere Apps denselben Keystore und dieselben Secrets für Datei und Passwort nutzen.

## Secrets in GitHub setzen

Der Keystore wird als Base64 ohne Zeilenumbruch hinterlegt:

```bash
base64 -w0 release.jks | gh secret set SIGNING_KEYSTORE_B64 --repo megalomon/CrunchyMate
gh secret set SIGNING_STORE_PASSWORD --repo megalomon/CrunchyMate
gh secret set SIGNING_KEY_PASSWORD --repo megalomon/CrunchyMate
gh secret set SIGNING_KEY_ALIAS --repo megalomon/CrunchyMate --body crunchymate
```

Ohne `--body` fragt `gh` den Wert interaktiv ab, sodass Passwörter nicht in der Shell-Historie landen. Unter macOS heißt der Schalter `base64 -b 0` statt `-w0`.

Die Workflows `nightly.yml` und `release.yml` dekodieren den Keystore nach `$RUNNER_TEMP/release.jks`, setzen `SIGNING_KEYSTORE_PATH` und löschen die Datei am Ende in jedem Fall wieder. Gradle legt die Signing-Konfiguration `release` nur an, wenn `SIGNING_KEYSTORE_PATH` gesetzt ist; bei lokalen Gradle-Builds ohne diese Variable entsteht daher eine unsignierte APK. Beide Workflows brechen ohne `SIGNING_KEYSTORE_B64` bewusst ab und bauen nie unsigniert.

## Organisations- oder Repository-Secrets

Gehören mehrere Repositorys zu einer GitHub-Organisation, können Keystore-Datei und Passwörter als Organisations-Secrets hinterlegt und den gewünschten Repositorys freigegeben werden (`gh secret set NAME --org ORGANISATION --repos repo1,repo2`). Der Alias bleibt dann als Repository-Secret je App getrennt. Bei einem privaten Konto ohne Organisation gibt es nur Repository-Secrets; jedes Repository bekommt dann seine eigenen vier Secrets.

## Lokal signiert bauen

```bash
export SIGNING_KEYSTORE_PATH=/pfad/zu/release.jks
export SIGNING_STORE_PASSWORD=...
export SIGNING_KEY_PASSWORD=...
export SIGNING_KEY_ALIAS=crunchymate
./gradlew assembleRelease
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```
