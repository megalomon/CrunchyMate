#!/usr/bin/env python3
"""Erzeugt den Abschnitt „Abhängigkeiten (automatisch erzeugt)“ in docs/open-source-hinweise.md
und daraus das App-Asset app/src/main/assets/open_source_notices.json.

Quelle der Wahrheit bleibt docs/open-source-hinweise.md: Die Abschnitte Icons, Länderflaggen und
Lizenztexte pflegt man dort von Hand; das Skript liest sie beim Erzeugen des Assets wieder ein.
"""

import glob
import json
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DOC = ROOT / "docs" / "open-source-hinweise.md"
ASSET = ROOT / "app" / "src" / "main" / "assets" / "open_source_notices.json"
BEGIN, END = "<!-- oss:begin -->", "<!-- oss:end -->"
RUST_TARGET = "aarch64-linux-android"
RUST_ROOT_PACKAGE = "crunchymate-core"
GRADLE_CACHE = Path.home() / ".gradle/caches/modules-2/files-2.1"
NICHT_ERMITTELBAR = "nicht ermittelbar"
LICENSE_FILE_GLOBS = ("LICENSE*", "LICENCE*", "COPYING*", "UNLICENSE*")
COPYRIGHT_RE = re.compile(r"^\W*(copyright\b.*\d{4}.*|\(c\)\s.*\d{4}.*|©.*\d{4}.*)$", re.I)
APACHE_SPDX = "Apache-2.0"  # POMs nennen die Lizenz uneinheitlich
POM_NS = {"m": "http://maven.apache.org/POM/4.0.0"}
ICONS_NAME = "Material Design Icons (Pictogrammers)"
ICONS_HOLDER = "Pictogrammers"
ICONS_LICENSE_ID = "Apache-2.0"
FLAGS_NAME = "flag-icons"
FLAGS_LICENSE_ID = "MIT (flag-icons)"
FLAGS_SOURCE = "https://github.com/lipis/flag-icons"
APP_NAME = "CrunchyMate"
APP_SOURCE = "https://github.com/megalomon/CrunchyMate"
APP_LICENSE_ID = "Apache-2.0"
APP_LICENSE_FILE = ROOT / "LICENSE"


def run(cmd):
    return subprocess.run(cmd, cwd=ROOT, check=True, capture_output=True, text=True).stdout


def cell(text):
    return (text or "").replace("|", "/").replace("\n", " ").strip()


# ---------- Rust ----------

def rust_copyrights(pkg):
    """Copyright-Zeilen aus den Lizenzdateien des Crates; leer, wenn keine gefunden."""
    base = Path(pkg["manifest_path"]).parent
    found = []
    for pattern in LICENSE_FILE_GLOBS:
        for path in sorted(base.glob(pattern)):
            if not path.is_file():
                continue
            for line in path.read_text(errors="replace").splitlines():
                m = COPYRIGHT_RE.match(line.strip())
                if m and m.group(1).strip() not in found:
                    found.append(m.group(1).strip().rstrip("."))
    return found[:3]


def rust_packages():
    meta = json.loads(run(["cargo", "metadata", "--format-version", "1",
                           "--filter-platform", RUST_TARGET, "--offline"]))
    pkgs = {p["id"]: p for p in meta["packages"]}
    nodes = {n["id"]: n for n in meta["resolve"]["nodes"]}
    root = next(i for i, p in pkgs.items() if p["name"] == RUST_ROOT_PACKAGE)

    def proc_macro(pkg):
        return any("proc-macro" in t["kind"] for t in pkg["targets"])

    # Nur normale Kanten; Proc-Macros laufen zur Übersetzungszeit und landen nicht in der Bibliothek.
    seen, stack = set(), [root]
    while stack:
        cur = stack.pop()
        for dep in nodes[cur]["deps"]:
            if not any(k["kind"] is None for k in dep["dep_kinds"]):
                continue
            pid = dep["pkg"]
            if pid in seen or proc_macro(pkgs[pid]):
                continue
            seen.add(pid)
            stack.append(pid)
    return sorted((pkgs[i] for i in seen), key=lambda p: (p["name"], p["version"]))


# ---------- Gradle ----------

def gradle_coordinates():
    out = run(["./gradlew", "-q", ":app:dependencies", "--configuration", "releaseRuntimeClasspath"])
    result = {}
    for line in out.splitlines():
        if "(c)" in line or "project :" in line:
            continue
        body = re.sub(r"^[|+\\\- ]+", "", line).strip()
        if not re.match(r"^[\w.\-]+:[\w.\-]+(:[^\s]+)?( -> [^\s]+)?", body):
            continue
        parts = [t for t in body.split() if t != "(*)"]  # (*) markiert nur bereits gezeigte Teilbäume
        coord = parts[0].split(":")
        version = parts[-1] if "->" in parts else (coord[2] if len(coord) > 2 else None)
        if version is None or coord[1].endswith("-bom"):
            continue  # BOMs enthalten keinen Code
        result[(coord[0], coord[1])] = version
    return dict(sorted(result.items()))


def find_pom(group, artifact, version):
    hits = glob.glob(str(GRADLE_CACHE / group / artifact / version / "*" / f"{artifact}-{version}.pom"))
    return Path(hits[0]) if hits else None


def pom_text(root, path):
    node = root.find(path, POM_NS)
    return node.text.strip() if node is not None and node.text else None


def pom_info(group, artifact, version, depth=0):
    """Lizenzen, Projekt-URL und Urheber; erbt über <parent>."""
    pom = find_pom(group, artifact, version)
    if pom is None or depth > 6:
        return {"licenses": [], "url": None, "owner": None}
    root = ET.parse(pom).getroot()
    licenses = [(pom_text(l, "m:name"), pom_text(l, "m:url"))
                for l in root.findall("m:licenses/m:license", POM_NS)]
    info = {"licenses": licenses, "url": pom_text(root, "m:url"),
            "owner": pom_text(root, "m:organization/m:name")
            or pom_text(root, "m:developers/m:developer/m:name")}
    parent = root.find("m:parent", POM_NS)
    if parent is not None and not (info["licenses"] and info["url"] and info["owner"]):
        up = pom_info(pom_text(parent, "m:groupId"), pom_text(parent, "m:artifactId"),
                      pom_text(parent, "m:version"), depth + 1)
        info["licenses"] = info["licenses"] or up["licenses"]
        info["url"] = info["url"] or up["url"]
        info["owner"] = info["owner"] or up["owner"]
    return info


# ---------- Ausgabe ----------

def build_section():
    rust, unknown, spdx_ids, components = [], [], set(), []
    for p in rust_packages():
        lic = p.get("license")
        if not lic:
            unknown.append(f"Rust: {p['name']} {p['version']}")
            lic = NICHT_ERMITTELBAR
        else:
            spdx_ids.update(re.findall(r"[A-Za-z0-9.\-+]+", re.sub(r"\b(OR|AND|WITH)\b", " ", lic)))
        holder = "; ".join(rust_copyrights(p)) or f"{p['name']} ({p.get('repository') or 'crates.io/crates/' + p['name']})"
        components.append({"group": "rust", "name": p["name"], "version": p["version"], "license": lic,
                           "source": p.get("repository") or "", "holder": holder})
        rust.append(f"| {cell(p['name'])} | {p['version']} | {cell(lic)} | {cell(p.get('repository') or '-')} | {cell(holder)} |")

    kotlin = []
    for (group, artifact), version in gradle_coordinates().items():
        info = pom_info(group, artifact, version)
        names = [APACHE_SPDX if "apache" in (n or u).lower() else cell(n or u)
                 for n, u in info["licenses"] if n or u]
        lic = "; ".join(names) or NICHT_ERMITTELBAR
        if not names:
            unknown.append(f"Android: {group}:{artifact} {version}")
        owner = info["owner"] or "siehe Quelle"
        components.append({"group": "android", "name": f"{group}:{artifact}", "version": version, "license": lic,
                           "source": info["url"] or "", "holder": owner})
        kotlin.append(f"| {group}:{artifact} | {version} | {cell(lic)} | {cell(info['url'] or '-')} | {cell(owner)} |")

    lines = [BEGIN, "## Abhängigkeiten (automatisch erzeugt)", "",
             "Neu erzeugen mit `python3 scripts/gen-oss-notices.py`. Nicht von Hand bearbeiten.", "",
             f"### Rust (Normal-Abhängigkeiten von `{RUST_ROOT_PACKAGE}`, Ziel `{RUST_TARGET}`)", "",
             "| Crate | Version | Lizenz | Quelle | Copyright |", "|---|---|---|---|---|", *rust, "",
             "### Android/Kotlin (`releaseRuntimeClasspath` von `:app`)", "",
             "| Bibliothek | Version | Lizenz | Quelle | Urheber |", "|---|---|---|---|---|", *kotlin, "",
             "### Lizenz nicht ermittelbar", ""]
    lines += [f"- {u}" for u in unknown] or ["Keine."]
    lines += ["", END]
    return "\n".join(lines), components, unknown, spdx_ids


# ---------- Asset ----------

def doc_section(text, heading):
    """Text eines Abschnitts mit Überschrift `## heading` bis zur nächsten `## `-Überschrift."""
    m = re.search(rf"^## {re.escape(heading)}\n(.*?)(?=^## |\Z)", text, re.S | re.M)
    if not m:
        sys.exit(f"Abschnitt „{heading}“ fehlt in {DOC.name}")
    return m.group(1)


def require(pattern, text, what):
    m = re.search(pattern, text)
    if not m:
        sys.exit(f"{what} nicht in {DOC.name} gefunden (Muster {pattern})")
    return m


def app_component():
    """Die eigene Lizenz der App; das Copyright steht in LICENSE."""
    holder = re.search(r"^\s*(Copyright \d{4}\b.*)$", APP_LICENSE_FILE.read_text(), re.M)
    if not holder:
        sys.exit(f"Copyright-Zeile in {APP_LICENSE_FILE.name} nicht gefunden")
    return {"group": "app", "name": APP_NAME, "version": "", "license": APP_LICENSE_ID,
            "source": APP_SOURCE, "holder": holder.group(1).strip()}


def hand_written_components(text):
    """Icons und Flaggen stehen von Hand im Dokument und werden hier eingelesen."""
    icons = doc_section(text, "Icons")
    icon_licenses = set(re.findall(r"^\| `ic_[^`]+` \| \S+ \| ([^|]+?) \|$", icons, re.M))
    if icon_licenses != {"Apache 2.0"}:
        sys.exit(f"Icon-Tabelle nennt unerwartete Lizenzen {sorted(icon_licenses)}; Skript und Dokument abgleichen")
    flags = doc_section(text, "Länderflaggen")
    return [
        {"group": "icons", "name": ICONS_NAME, "version": "", "license": ICONS_LICENSE_ID,
         "source": require(r"\((https://pictogrammers\.com/)\)", icons, "Icon-Quelle").group(1),
         "holder": ICONS_HOLDER},
        {"group": "flags", "name": FLAGS_NAME,
         "version": require(r"flag-icons \(Version ([\d.]+)", flags, "Flaggen-Version").group(1),
         "license": "MIT", "source": FLAGS_SOURCE,
         "holder": require(r"Copyright \(c\) [^.]+?(?=\. )", flags, "Flaggen-Copyright").group(0)},
    ]


def license_texts(text):
    """Volltexte aus dem Abschnitt „Lizenztexte“: je `### Kennung`, Titelzeile und ```text-Block."""
    body = doc_section(text, "Lizenztexte")
    texts = []
    for m in re.finditer(r"^### (.+?)\n\n(.+?)\n\n```text\n(.*?)\n```", body, re.S | re.M):
        texts.append({"id": m.group(1), "title": m.group(2), "text": m.group(3)})
    if not texts:
        sys.exit("Keine Lizenztexte gefunden")
    return texts


def license_ids(license_expression, known_ids):
    tokens = re.findall(r"[A-Za-z0-9.\-+]+", re.sub(r"\b(OR|AND|WITH)\b", " ", license_expression))
    return [t for t in tokens if t in known_ids]


def write_asset(components, text):
    texts = license_texts(text)
    known = {t["id"] for t in texts}
    all_components = [app_component()] + components + hand_written_components(text)
    for c in all_components:
        # Flaggen stehen unter dem eigenen MIT-Text mit Copyright von flag-icons
        c["licenseIds"] = [FLAGS_LICENSE_ID] if c["group"] == "flags" else license_ids(c["license"], known)
    ASSET.parent.mkdir(parents=True, exist_ok=True)
    ASSET.write_text(json.dumps({"components": all_components, "licenses": texts},
                                ensure_ascii=False, indent=1) + "\n")


def main():
    section, components, unknown, ids = build_section()
    text = DOC.read_text()
    if BEGIN in text and END in text:
        head, rest = text.split(BEGIN, 1)
        tail = rest.split(END, 1)[1]
        text = head + section + tail
    else:
        text = text.rstrip("\n") + "\n\n" + section + "\n"
    DOC.write_text(text)
    write_asset(components, text)
    missing = sorted(i for i in ids if f"### {i}\n" not in text)
    n_rust = sum(c["group"] == "rust" for c in components)
    print(f"Rust-Crates: {n_rust}, Android-Bibliotheken: {len(components) - n_rust}, nicht ermittelbar: {len(unknown)}")
    if missing:
        print("Lizenztext fehlt im Abschnitt „Lizenztexte“ für: " + ", ".join(missing), file=sys.stderr)


if __name__ == "__main__":
    main()
