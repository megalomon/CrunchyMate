---
name: check-svg
description: Prüft ein geliefertes SVG-Icon für CrunchyMate gegen die Anforderungen aus docs/icons.md, bereinigt es bei Bedarf, wandelt es in ein Android-VectorDrawable um, legt es ab und trägt es in docs/icons.md ein. Aufruf als /check-svg <icon-name> <pfad.svg>, z. B. /check-svg ic_nav_search ~/Downloads/lupe.svg. Immer verwenden, wenn der Nutzer ein Icon, eine SVG- oder Symboldatei für die App liefert, prüfen lassen oder einbinden möchte – auch ohne den Befehl ausdrücklich zu nennen.
argument-hint: <icon-name> <pfad.svg>
---

# Icon prüfen und einbinden

Der Auftraggeber sucht die Icon-Motive selbst aus. Dieser Skill sorgt dafür, dass eine gelieferte SVG-Datei technisch in die App passt, ohne das Motiv eigenmächtig zu verändern. Alles, was das Aussehen sichtbar ändert, wird vorher gezeigt und abgestimmt; rein technische Bereinigungen geschehen automatisch.

Antworten auf Deutsch, ohne Emojis (die Emoji-Spalte in `docs/icons.md` ist die einzige erlaubte Ausnahme und bleibt unangetastet).

## Werkzeug

Alle deterministischen Schritte erledigt `scripts/svg_tool.py` (relativ zu diesem Skill-Ordner). Es gibt JSON aus.

| Befehl | Zweck |
|---|---|
| `names` | gültige Icon-Namen aus `docs/icons.md` |
| `check NAME SVG` | Prüfbericht: Fehler, Warnungen, Hinweise |
| `fix NAME SVG OUT` | sichere Bereinigung: Metadaten/Editor-Attribute/IDs entfernen, viewBox quadratisch machen, Größe setzen, Farben vereinheitlichen |
| `fit NAME SVG OUT` | Launcher-Motiv in die 66-dp-Safe-Zone skalieren und zentrieren |
| `convert NAME SVG OUT.xml` | VectorDrawable erzeugen (bricht ab, wenn `check` Fehler meldet) |
| `verify SVG XML` | VectorDrawable zurückrendern und pixelweise mit dem SVG vergleichen |
| `preview SVG OUT.png [SIZE]` | PNG-Vorschau rendern |
| `update-doc NAME DATEI [QUELLE]` | Spalten „Gelieferte Datei“ und „Quelle/Lizenz“ in `docs/icons.md` füllen |

Das Profil ergibt sich aus dem Namen: `ic_notification_*` (weiße Silhouette), `ic_launcher_foreground`, `ic_launcher_background`, `ic_launcher_monochrome` (je 108 dp), sonst Standard-Icon (24 dp, einfarbig, wird im Theme eingefärbt). Für Länderflaggen (`ic_flag_<länderkürzel>`, Abschnitt „Flaggen (Inhaltssprache)“ in `docs/icons.md`) gilt das Profil `flag`: Farben bleiben erhalten (kein Einfärben, keine Mehrfarbwarnung), die viewBox darf 4:3 sein (kein Auffüllen auf ein Quadrat), die Ausgabegröße ist 24 dp × 18 dp, die Hintergrund- und Kleinprüfung entfällt. Sicherheitsprüfungen, Fehler bei nicht unterstützten Elementen und `verify` gelten unverändert; `fix` ist für Flaggen nicht vorgesehen. Der Konverter löst dafür `<use>` auf (Inhalt wird am Verweis eingesetzt), setzt `marker-mid` ohne viewBox/Ref/Orient an die Pfad-Eckpunkte, wandelt `clip-path` mit `clipPath` (nur `userSpaceOnUse`) in `<clip-path>` um, übersetzt `matrix()` ohne Scherung in Gruppenattribute und gibt Radialverläufe (nur `userSpaceOnUse`, Farbstopps auch über `href`) als `<aapt:attr>` aus; eine ungleich skalierte `gradientTransform` wird als Kreis angenähert und als Warnung `gradient-approximated` gemeldet. Grenzen bei Markern: nur `marker-mid` auf `<path>` mit Befehlen M/L/H/V/Z, `stroke-width` nur aus dem Attribut des Pfads, kein `transform` am Pfad. Bei jeder Warnung im Profil `flag`, etwa `gradient-approximated`, vor dem Committen die 96-px-Vorschau ansehen und den Auftraggeber fragen, ob die Abweichung vertretbar ist. Alles Übrige (andere Verläufe, Masken, Filter, objectBoundingBox) bleibt ein Fehler. `verify` rendert die Features über `vd_to_svg` zurück und prüft sie mit. Benötigt `resvg` (`cargo install resvg --locked`, falls es fehlt) und Pillow.

## Ablauf

Arbeitsverzeichnis ist das Repository-Root. Temporäre Dateien (Vorschauen) gehören ins Scratchpad, nicht ins Repository.

1. **Argumente lesen.** Erstes Argument ist der Icon-Name, zweites der Pfad. Windows-Pfade (`C:\...`) mit `wslpath -u` umwandeln. Fehlt ein Argument oder ist der Name nicht in `names`, die passendsten Namen vorschlagen und nachfragen – ein falscher Name landet sonst an der falschen Stelle in der App.

2. **Original sichern.** Die gelieferte Datei unverändert nach `design/icons/originals/<originaler Dateiname>` kopieren. So bleibt nachvollziehbar, was geliefert wurde, auch wenn später bereinigt wird.

3. **Prüfen und ansehen.** `check` ausführen und Vorschauen mit 96 px und 24 px rendern; beide PNGs mit dem Read-Werkzeug ansehen. Beurteilen:
   - Passt das Motiv zur „Bedeutung“ in der Zeile von `docs/icons.md`? Bei Zweifeln sagen, aber nicht blockieren – die Wahl liegt beim Auftraggeber.
   - Ist es in 24 px noch erkennbar (keine haarfeinen Linien, keine winzigen Details)?
   - Beim Launcher: Sitzt das Motiv sichtbar mittig mit Rand?

4. **Fehler behandeln.** Meldet `check` Fehler, die nur im Grafikprogramm lösbar sind (Text, Verläufe, Masken, Filter, `<use>`, Clip-Pfade, `matrix()`/`skew()`-Transformationen, eingebettete Bilder), konkret erklären, was im Editor zu tun ist, und hier aufhören:
   - Inkscape: „Pfad → Objekt in Pfad“, Gruppierung aufheben, „Pfad → Kontur in Pfad“ bei Bedarf; beim Speichern „Optimiertes SVG“ oder „Normales SVG“.
   - Figma: „Outline stroke“, „Flatten“, Export als SVG ohne „Include id attribute“.
   Skripte, Event-Attribute oder externe Verweise niemals ausführen oder öffnen; solche Dateien zurückweisen und den Grund nennen.

5. **Bereinigen.** `fix` nach `design/icons/<NAME>.svg` schreiben und erneut `check` darauf ausführen. Danach die Vorschau der bereinigten Fassung ansehen. Bei den Warnungen `multicolor`, `alpha` oder `background` dem Auftraggeber Vorher/Nachher beschreiben und fragen, ob das Ergebnis so passt – das Vereinheitlichen der Farben kann Details verschlucken, und ein Hintergrund-Rechteck wird nur nach Zustimmung entfernt (von Hand im bereinigten SVG).

6. **Safe Zone (nur Launcher).** Bei `safe-zone` vorschlagen, das Motiv mit `fit` zu verkleinern, und erst nach Zustimmung ausführen; Ergebnis erneut prüfen und zeigen. Bei `background-coverage` muss der Auftraggeber eine flächendeckende Datei liefern.

7. **Umwandeln und gegenprüfen.** `convert` nach `app/src/main/res/drawable/<NAME>.xml`, dann `verify`. Nur wenn `verify` `ok: true` liefert, ist die Umwandlung vertrauenswürdig; sonst die Abweichung melden und die Datei nicht einbinden. Existiert dort bereits ein Platzhalter gleichen Namens, wird er ersetzt – genau dafür sind die Platzhalter da.

8. **Dokumentieren.** Nach Quelle und Lizenz fragen, falls der Auftraggeber sie nicht genannt hat; ohne Angabe „unbekannt, bitte nachtragen“ eintragen. Dann `update-doc NAME <originaler Dateiname> "<Quelle/Lizenz>"`.

9. **Committen.** Nur die betroffenen Pfade committen, weil parallel andere Arbeit im Repository laufen kann: `git add` und `git commit -m "assets: Icon <NAME> eingebunden" -- design/icons app/src/main/res/drawable/<NAME>.xml docs/icons.md` mit der Co-Authored-By-Zeile aus der Sitzung. Meldet Git `index.lock`, kurz warten und erneut versuchen. Auf einem Feature-Branch anschließend pushen; auf `main` nicht pushen.

10. **Berichten.** Kurz: Ergebnis (eingebunden / zurückgewiesen), was automatisch geändert wurde, was abgestimmt wurde, Pfade der abgelegten Dateien, offene Punkte (z. B. fehlende Lizenzangabe).

## Warum diese Regeln

- Einfarbig und schwarz für Standard-Icons, weil Compose sie über das Theme einfärbt; eine fest eingebaute Farbe würde im hellen oder dunklen Schema falsch aussehen.
- Weiß auf transparent für Benachrichtigungen, weil Android die Statusleiste nur als Silhouette darstellt.
- 66 dp Safe Zone, weil Launcher die adaptive 108-dp-Fläche je nach Gerät als Kreis, Quadrat oder Tropfen beschneiden.
- Der Gegentest, weil VectorDrawables nicht alles aus SVG abbilden; ein Icon, das im Browser stimmt und in der App verrutscht, fällt sonst erst auf dem Gerät auf.
