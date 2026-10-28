#!/usr/bin/env python3
"""Erzeugt den Abschnitt „Abhängigkeiten (automatisch erzeugt)“ in docs/open-source-hinweise.md."""

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
BEGIN, END = "<!-- oss:begin -->", "<!-- oss:end -->"
RUST_TARGET = "aarch64-linux-android"
RUST_ROOT_PACKAGE = "crunchymate-core"
GRADLE_CACHE = Path.home() / ".gradle/caches/modules-2/files-2.1"
NICHT_ERMITTELBAR = "nicht ermittelbar"
LICENSE_FILE_GLOBS = ("LICENSE*", "LICENCE*", "COPYING*", "UNLICENSE*")
COPYRIGHT_RE = re.compile(r"^\W*(copyright\b.*\d{4}.*|\(c\)\s.*\d{4}.*|©.*\d{4}.*)$", re.I)
APACHE_SPDX = "Apache-2.0"  # POMs nennen die Lizenz uneinheitlich
POM_NS = {"m": "http://maven.apache.org/POM/4.0.0"}


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
    rust, unknown, spdx_ids = [], [], set()
    for p in rust_packages():
        lic = p.get("license")
        if not lic:
            unknown.append(f"Rust: {p['name']} {p['version']}")
            lic = NICHT_ERMITTELBAR
        else:
            spdx_ids.update(re.findall(r"[A-Za-z0-9.\-+]+", re.sub(r"\b(OR|AND|WITH)\b", " ", lic)))
        holder = "; ".join(rust_copyrights(p)) or f"{p['name']} ({p.get('repository') or 'crates.io/crates/' + p['name']})"
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
    return "\n".join(lines), len(rust), len(kotlin), len(unknown), spdx_ids


def main():
    section, n_rust, n_kotlin, n_unknown, ids = build_section()
    text = DOC.read_text()
    if BEGIN in text and END in text:
        head, rest = text.split(BEGIN, 1)
        tail = rest.split(END, 1)[1]
        text = head + section + tail
    else:
        text = text.rstrip("\n") + "\n\n" + section + "\n"
    DOC.write_text(text)
    missing = sorted(i for i in ids if f"### {i}\n" not in text)
    print(f"Rust-Crates: {n_rust}, Android-Bibliotheken: {n_kotlin}, nicht ermittelbar: {n_unknown}")
    if missing:
        print("Lizenztext fehlt im Abschnitt „Lizenztexte“ für: " + ", ".join(missing), file=sys.stderr)


if __name__ == "__main__":
    main()
