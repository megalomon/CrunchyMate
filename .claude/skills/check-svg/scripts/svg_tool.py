#!/usr/bin/env python3
"""Prüft, bereinigt und wandelt SVG-Icons für CrunchyMate in Android-VectorDrawables um.

Unterbefehle:
  names                         Icon-Namen aus docs/icons.md auflisten
  check   NAME SVG              Prüfbericht als JSON
  fix     NAME SVG OUT.svg      sichere Korrekturen anwenden (Metadaten, Farben, Größe)
  fit     NAME SVG OUT.svg      Motiv in die Safe Zone skalieren (nur Launcher-Vordergrund/-Monochrom)
  convert NAME SVG OUT.xml      VectorDrawable erzeugen
  verify  SVG XML               VectorDrawable zurückrendern und mit dem SVG vergleichen
  preview SVG OUT.png [SIZE]    PNG-Vorschau rendern (Standard 96 px)
  update-doc NAME FILE [SOURCE] Zeile in docs/icons.md ausfüllen
"""

import copy
import json
import math
import re
import shutil
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
from pathlib import Path

SVG_NS = "http://www.w3.org/2000/svg"
XLINK_NS = "http://www.w3.org/1999/xlink"
ANDROID_NS = "http://schemas.android.com/apk/res/android"
ET.register_namespace("", SVG_NS)
ET.register_namespace("xlink", XLINK_NS)
AAPT_NS = "http://schemas.android.com/aapt"

REPO_ROOT = Path(__file__).resolve().parents[4]
ICONS_DOC = REPO_ROOT / "docs" / "icons.md"

# Launcher-Icons: 108 dp Fläche, Motiv in der mittleren 66-dp-Safe-Zone
LAUNCHER_SIZE_DP = 108
SAFE_ZONE_DP = 66
STANDARD_SIZE_DP = 24
# Länderflaggen (4:3, farbig): 24 dp x 18 dp
FLAG_WIDTH_DP = 24
FLAG_HEIGHT_DP = 18
FLAG_NAME_PREFIX = "ic_flag_"
# Schutz vor zyklischen <use>-Verweisen
MAX_USE_DEPTH = 16
# Ab diesem Seitenverhältnis der Gradient-Transformation wird der Verlauf nur noch als Kreis angenähert
GRADIENT_MAX_ANISOTROPY = 1.1
NUMBER_PATTERN = r"[-+]?(?:\d*\.\d+|\d+\.?)(?:[eE][-+]?\d+)?"
# Ab diesem Anteil der Fläche gilt ein Rechteck als Hintergrund
BACKGROUND_AREA_RATIO = 0.95
# Ab dieser Pfadanzahl wird das Rendern auf Geräten spürbar teurer
MAX_RECOMMENDED_PATHS = 60
# Erlaubte mittlere Pixelabweichung beim Gegentest (0..255)
MAX_VERIFY_DIFF = 6.0
RENDER_SIZE = 96

SUPPORTED_SHAPES = {"path", "rect", "circle", "ellipse", "line", "polyline", "polygon"}
CONTAINERS = {"svg", "g"}
IGNORED = {"title", "desc", "metadata", "defs", "clipPath", "marker", "linearGradient", "radialGradient"}
UNSUPPORTED = {
    "text": "Text muss im Editor in Pfade umgewandelt werden",
    "image": "eingebettete Rastergrafik wird nicht unterstützt",
    "filter": "Filter (Schatten, Weichzeichner) werden nicht unterstützt",
    "mask": "Masken werden nicht unterstützt",
    "pattern": "Muster werden nicht unterstützt",
    "use": "<use>-Verweis nicht auflösbar (Ziel fehlt oder externer Verweis)",
    "symbol": "<symbol> muss im Editor aufgelöst werden",
    "style": "CSS-Stylesheets werden nicht unterstützt (nur style-Attribute)",
    "foreignObject": "foreignObject ist nicht erlaubt",
    "script": "Skripte sind nicht erlaubt",
}
STYLE_PROPS = (
    "fill", "stroke", "stroke-width", "stroke-linecap", "stroke-linejoin",
    "stroke-miterlimit", "fill-rule", "opacity", "fill-opacity", "stroke-opacity",
)
NAMED_COLORS = {"black": "#000000", "white": "#FFFFFF", "red": "#FF0000", "currentcolor": "#000000"}
# Attribute, die die Darstellung ändern, aber nicht umgewandelt werden; stillschweigend ignorieren wäre falsch
UNSUPPORTED_ATTRS = ("marker-start", "marker-mid", "marker-end", "mask", "filter")


def profile_for(name):
    if name == "ic_launcher_foreground":
        return "launcher_foreground"
    if name == "ic_launcher_background":
        return "launcher_background"
    if name == "ic_launcher_monochrome":
        return "launcher_monochrome"
    if name.startswith("ic_notification"):
        return "notification"
    if name.startswith(FLAG_NAME_PREFIX):
        return "flag"
    return "standard"


def target_color(profile):
    return "#FFFFFF" if profile == "notification" else "#000000"


def size_dp(profile):
    return LAUNCHER_SIZE_DP if profile.startswith("launcher") else STANDARD_SIZE_DP


def size_dp_wh(profile):
    """Breite und Höhe in dp; nur Flaggen sind nicht quadratisch."""
    if profile == "flag":
        return FLAG_WIDTH_DP, FLAG_HEIGHT_DP
    side = size_dp(profile)
    return side, side


def local(tag):
    return tag.split("}", 1)[-1]


def icon_names():
    names = []
    for line in ICONS_DOC.read_text(encoding="utf-8").splitlines():
        m = re.match(r"^\| `(ic_[a-z0-9_]+)` \|", line)
        if m:
            names.append(m.group(1))
    return names


def parse_style(value):
    result = {}
    for part in (value or "").split(";"):
        if ":" in part:
            k, v = part.split(":", 1)
            result[k.strip()] = v.strip()
    return result


def own_props(el):
    props = {k: el.get(k) for k in STYLE_PROPS if el.get(k) is not None}
    props.update({k: v for k, v in parse_style(el.get("style")).items() if k in STYLE_PROPS})
    return props


def normalize_color(value):
    """Gibt (#RRGGBB, alpha) zurück, None für 'none', 'invalid' für Unbekanntes."""
    if value is None:
        return None
    v = value.strip().lower()
    if v in ("none", "transparent"):
        return None
    if v in NAMED_COLORS:
        return NAMED_COLORS[v], 1.0
    m = re.fullmatch(r"#([0-9a-f]{3})", v)
    if m:
        return "#" + "".join(c * 2 for c in m.group(1)).upper(), 1.0
    m = re.fullmatch(r"#([0-9a-f]{6})([0-9a-f]{2})?", v)
    if m:
        alpha = int(m.group(2), 16) / 255 if m.group(2) else 1.0
        return "#" + m.group(1).upper(), alpha
    m = re.fullmatch(r"rgba?\(([^)]*)\)", v)
    if m:
        parts = [p.strip() for p in m.group(1).split(",")]
        rgb = [int(float(p.rstrip("%")) * (2.55 if p.endswith("%") else 1)) for p in parts[:3]]
        alpha = float(parts[3]) if len(parts) > 3 else 1.0
        return "#%02X%02X%02X" % tuple(rgb), alpha
    return "invalid"


def parse_viewbox(root):
    vb = root.get("viewBox")
    if vb:
        nums = [float(x) for x in re.split(r"[\s,]+", vb.strip())]
        if len(nums) == 4:
            return nums
    w, h = root.get("width"), root.get("height")
    if w and h:
        return [0.0, 0.0, float(re.sub(r"[a-z%]+$", "", w)), float(re.sub(r"[a-z%]+$", "", h))]
    return None


def parse_transform(value):
    """Zerlegt ein transform-Attribut in [(art, [zahlen])]."""
    ops = []
    for kind, args in re.findall(r"([a-zA-Z]+)\s*\(([^)]*)\)", value or ""):
        ops.append((kind, [float(x) for x in re.split(r"[\s,]+", args.strip()) if x]))
    return ops


def num(el, attr, default=0.0):
    v = el.get(attr)
    return float(re.sub(r"[a-z%]+$", "", v)) if v else default


def shape_to_path(el):
    tag = local(el.tag)
    if tag == "path":
        return el.get("d", "")
    if tag == "rect":
        x, y, w, h = num(el, "x"), num(el, "y"), num(el, "width"), num(el, "height")
        rx = num(el, "rx", None) if el.get("rx") else None
        ry = num(el, "ry", None) if el.get("ry") else None
        rx = rx if rx is not None else (ry or 0.0)
        ry = ry if ry is not None else rx
        rx, ry = min(rx, w / 2), min(ry, h / 2)
        if rx == 0 and ry == 0:
            return f"M{x},{y}h{w}v{h}h{-w}z"
        return (f"M{x + rx},{y}h{w - 2 * rx}a{rx},{ry} 0 0 1 {rx},{ry}v{h - 2 * ry}"
                f"a{rx},{ry} 0 0 1 {-rx},{ry}h{-(w - 2 * rx)}a{rx},{ry} 0 0 1 {-rx},{-ry}"
                f"v{-(h - 2 * ry)}a{rx},{ry} 0 0 1 {rx},{-ry}z")
    if tag in ("circle", "ellipse"):
        cx, cy = num(el, "cx"), num(el, "cy")
        rx = num(el, "r") if tag == "circle" else num(el, "rx")
        ry = num(el, "r") if tag == "circle" else num(el, "ry")
        return (f"M{cx - rx},{cy}a{rx},{ry} 0 1 0 {2 * rx},0"
                f"a{rx},{ry} 0 1 0 {-2 * rx},0z")
    if tag == "line":
        return f"M{num(el, 'x1')},{num(el, 'y1')}L{num(el, 'x2')},{num(el, 'y2')}"
    if tag in ("polyline", "polygon"):
        pts = re.split(r"[\s,]+", el.get("points", "").strip())
        pairs = [f"{pts[i]},{pts[i + 1]}" for i in range(0, len(pts) - 1, 2)]
        if not pairs:
            return ""
        d = "M" + " L".join(pairs)
        return d + ("z" if tag == "polygon" else "")
    return ""


# ---------------------------------------------------------------- Verweise, Matrizen, Verläufe

def href_of(el):
    return el.get("href") or el.get(f"{{{XLINK_NS}}}href")


def element_ids(root):
    return {el.get("id"): el for el in root.iter() if isinstance(el.tag, str) and el.get("id")}


def url_target(value, ids):
    m = re.fullmatch(r"url\(\s*#([^)\s]+)\s*\)", (value or "").strip())
    return ids.get(m.group(1)) if m else None


def expand_uses(parent, ids, depth=0):
    """Ersetzt <use> durch eine Gruppe mit Kopie des Ziels; unauflösbare bleiben für check stehen."""
    for index, child in enumerate(list(parent)):
        if not isinstance(child.tag, str):
            continue
        if local(child.tag) != "use":
            expand_uses(child, ids, depth)
            continue
        ref = href_of(child)
        target = ids.get(ref[1:]) if ref and ref.startswith("#") else None
        if target is None or depth >= MAX_USE_DEPTH:
            continue
        group = ET.Element(f"{{{SVG_NS}}}g")
        for key, value in child.attrib.items():
            if local(key) not in ("x", "y", "width", "height", "href", "transform"):
                group.set(key, value)
        transform = child.get("transform", "")
        dx, dy = num(child, "x"), num(child, "y")
        if dx or dy:
            transform = f"{transform} translate({dx:g} {dy:g})".strip()
        if transform:
            group.set("transform", transform)
        group.append(copy.deepcopy(target))
        expand_uses(group, ids, depth + 1)
        parent.remove(child)
        parent.insert(index, group)


def path_vertices(d):
    """Eckpunkte eines Pfads aus M/L/H/V/Z (absolut und relativ); sonst ValueError."""
    tokens = re.findall(r"[A-Za-z]|" + NUMBER_PATTERN, d)
    points, x, y, start, i, cmd = [], 0.0, 0.0, (0.0, 0.0), 0, None
    arity = {"M": 2, "L": 2, "H": 1, "V": 1, "Z": 0}
    while i < len(tokens):
        if tokens[i].isalpha():
            cmd = tokens[i]
            i += 1
            if cmd.upper() not in arity:
                raise ValueError(f"Pfadbefehl {cmd} wird für Marker nicht unterstützt")
            if cmd in "Zz":
                x, y = start
                points.append((x, y))
                continue
        elif cmd is None or cmd in "Zz":
            raise ValueError("Ungültige Pfaddaten")
        n = arity[cmd.upper()]
        args = [float(t) for t in tokens[i:i + n]]
        if len(args) < n:
            raise ValueError("Ungültige Pfaddaten")
        i += n
        rel = cmd.islower()
        kind = cmd.upper()
        if kind in "ML":
            x, y = (x + args[0], y + args[1]) if rel else (args[0], args[1])
        elif kind == "H":
            x = x + args[0] if rel else args[0]
        else:
            y = y + args[0] if rel else args[0]
        points.append((x, y))
        if kind == "M":
            start = (x, y)
            cmd = "l" if rel else "L"
    return points


def expand_markers(parent, ids):
    """Setzt marker-mid als Kopien des Marker-Inhalts an die Pfad-Eckpunkte (Standard-Marker ohne viewBox/Ref/Orient)."""
    for index, child in enumerate(list(parent)):
        if not isinstance(child.tag, str):
            continue
        expand_markers(child, ids)
        marker = url_target(child.get("marker-mid"), ids)
        if marker is None or local(marker.tag) != "marker" or local(child.tag) != "path":
            continue
        if any(marker.get(a) for a in ("viewBox", "refX", "refY", "orient")):
            continue
        try:
            vertices = path_vertices(child.get("d", ""))
        except ValueError:
            continue
        scale = 1.0 if marker.get("markerUnits") == "userSpaceOnUse" else float(child.get("stroke-width", 1))
        del child.attrib["marker-mid"]
        for offset, (vx, vy) in enumerate(vertices[1:-1], start=1):
            group = ET.Element(f"{{{SVG_NS}}}g", {"transform": f"translate({vx:g} {vy:g}) scale({scale:g})"})
            for part in marker:
                group.append(copy.deepcopy(part))
            parent.insert(index + offset, group)


def expand_references(root):
    """Löst <use> und marker-mid auf; danach braucht der Konverter diese Features nicht mehr."""
    ids = element_ids(root)
    expand_uses(root, ids)
    expand_markers(root, ids)
    return root


def load_svg(svg_path):
    return expand_references(ET.parse(svg_path).getroot())


def matrix_multiply(m1, m2):
    """Verkettet zwei Matrizen (a, b, c, d, e, f); m2 wird zuerst angewendet."""
    a1, b1, c1, d1, e1, f1 = m1
    a2, b2, c2, d2, e2, f2 = m2
    return (a1 * a2 + c1 * b2, b1 * a2 + d1 * b2, a1 * c2 + c1 * d2, b1 * c2 + d1 * d2,
            a1 * e2 + c1 * f2 + e1, b1 * e2 + d1 * f2 + f1)


def matrix_of(transform):
    result = (1.0, 0.0, 0.0, 1.0, 0.0, 0.0)
    for kind, a in parse_transform(transform):
        if kind == "matrix" and len(a) == 6:
            m = tuple(a)
        elif kind == "translate":
            m = (1.0, 0.0, 0.0, 1.0, a[0], a[1] if len(a) > 1 else 0.0)
        elif kind == "scale":
            m = (a[0], 0.0, 0.0, a[1] if len(a) > 1 else a[0], 0.0, 0.0)
        elif kind == "rotate":
            c, s_ = math.cos(math.radians(a[0])), math.sin(math.radians(a[0]))
            m = (c, s_, -s_, c, 0.0, 0.0)
            if len(a) == 3:
                m = matrix_multiply(matrix_multiply((1.0, 0.0, 0.0, 1.0, a[1], a[2]), m),
                                    (1.0, 0.0, 0.0, 1.0, -a[1], -a[2]))
        else:
            raise ValueError(f"transform {kind}() wird nicht unterstützt")
        result = matrix_multiply(result, m)
    return result


def matrix_group_attrs(args):
    """matrix() als Gruppenattribute (Skalierung, Drehung, Verschiebung); None bei Scherung/ungleicher Drehskalierung."""
    if len(args) != 6:
        return None
    a, b, c, d, e, f = args
    tolerance = 1e-6
    if abs(b) < tolerance and abs(c) < tolerance:
        return {"scaleX": a, "scaleY": d, "translateX": e, "translateY": f}
    if abs(a - d) < tolerance and abs(b + c) < tolerance:
        return {"scaleX": math.hypot(a, b), "scaleY": math.hypot(a, b),
                "rotation": math.degrees(math.atan2(b, a)), "translateX": e, "translateY": f}
    return None


def clip_path_data(value, ids):
    """Pfaddaten des Clip-Pfads (nur userSpaceOnUse); ValueError mit Begründung sonst."""
    target = url_target(value, ids)
    if target is None or local(target.tag) != "clipPath":
        raise ValueError("clip-path verweist auf kein <clipPath>")
    if target.get("clipPathUnits") == "objectBoundingBox":
        raise ValueError("clipPathUnits=objectBoundingBox wird nicht unterstützt (nur userSpaceOnUse)")
    if target.get("transform") or target.get("clip-path"):
        raise ValueError("clipPath mit transform oder eigenem clip-path wird nicht unterstützt")
    parts = []
    for shape in target:
        if not isinstance(shape.tag, str) or local(shape.tag) not in SUPPORTED_SHAPES or shape.get("transform"):
            raise ValueError("clipPath darf nur Formen ohne transform enthalten")
        parts.append(shape_to_path(shape).strip())
    if not parts:
        raise ValueError("clipPath ist leer")
    return " ".join(parts)


def gradient_stops(gradient, ids, depth=0):
    stops = [c for c in gradient if isinstance(c.tag, str) and local(c.tag) == "stop"]
    if not stops:
        ref = href_of(gradient)
        target = ids.get(ref[1:]) if ref and ref.startswith("#") else None
        if target is None or depth >= MAX_USE_DEPTH:
            raise ValueError("Verlauf ohne Farbstopps")
        return gradient_stops(target, ids, depth + 1)
    result = []
    for stop in stops:
        raw = stop.get("offset", "0")
        offset = float(raw[:-1]) / 100 if raw.endswith("%") else float(raw)
        color = normalize_color(parse_style(stop.get("style")).get("stop-color", stop.get("stop-color", "#000000")))
        if color in (None, "invalid"):
            raise ValueError("Farbstopp mit nicht auswertbarer Farbe")
        result.append((min(max(offset, 0.0), 1.0), color[0]))
    return result


def radial_gradient(value, ids):
    """Radialverlauf in Nutzerkoordinaten; die Transformation wird als Kreis angenähert."""
    gradient = url_target(value, ids)
    if gradient is None or local(gradient.tag) != "radialGradient":
        raise ValueError("Nur radialGradient wird als Füllung unterstützt")
    if gradient.get("gradientUnits") != "userSpaceOnUse":
        raise ValueError("Nur gradientUnits=userSpaceOnUse wird unterstützt")
    if any(gradient.get(a) is None for a in ("cx", "cy", "r")):
        raise ValueError("Verlauf braucht cx, cy und r")
    a, b, c, d, e, f = matrix_of(gradient.get("gradientTransform"))
    cx, cy, r = num(gradient, "cx"), num(gradient, "cy"), num(gradient, "r")
    det = abs(a * d - b * c)
    trace = a * a + b * b + c * c + d * d
    root_term = math.sqrt(max(trace * trace - 4 * det * det, 0.0))
    largest, smallest = (trace + root_term) / 2, (trace - root_term) / 2
    anisotropy = math.sqrt(largest / smallest) if smallest > 0 else float("inf")
    return {"cx": a * cx + c * cy + e, "cy": b * cx + d * cy + f, "r": r * math.sqrt(det),
            "stops": gradient_stops(gradient, ids), "anisotropy": anisotropy}


# ---------------------------------------------------------------- Rendern

def resvg_binary():
    found = shutil.which("resvg") or str(Path.home() / ".cargo/bin/resvg")
    if not Path(found).exists():
        sys.exit("resvg fehlt: cargo install resvg --locked")
    return found


def render(svg_path, png_path, size=RENDER_SIZE):
    subprocess.run([resvg_binary(), "--width", str(size), "--height", str(size),
                    str(svg_path), str(png_path)], check=True, capture_output=True)


def alpha_bbox(png_path):
    from PIL import Image
    img = Image.open(png_path).convert("RGBA")
    return img.getchannel("A").getbbox(), img.size


# ---------------------------------------------------------------- Prüfen

def walk(el, inherited, findings, stats):
    if not isinstance(el.tag, str) or ("}" in el.tag and not el.tag.startswith("{" + SVG_NS)):
        return  # Editor-Elemente (Inkscape, Sodipodi) entfernt fix ohnehin
    tag = local(el.tag)
    if tag in IGNORED:
        if tag == "defs" and len(el):
            findings.append(("warning", "defs-content", "<defs> enthält Elemente; sie werden ignoriert"))
        return
    if tag in UNSUPPORTED:
        level = "error"
        findings.append((level, f"unsupported-{tag}", UNSUPPORTED[tag]))
        return
    for attr in el.attrib:
        a = local(attr).lower()
        if a.startswith("on"):
            findings.append(("error", "event-handler", f"Event-Attribut {a} ist nicht erlaubt"))
        if a == "href" and not el.attrib[attr].startswith("#"):
            findings.append(("error", "external-ref", "Externe Verweise sind nicht erlaubt"))
        if a in UNSUPPORTED_ATTRS and el.attrib[attr].strip() != "none":
            findings.append(("error", "unsupported-attr", f"Attribut {a} wird nicht unterstützt"))
        if a == "vector-effect":
            findings.append(("error", "vector-effect", "vector-effect wird nicht unterstützt"))
    props = dict(inherited)
    props.update(own_props(el))
    transform = el.get("transform")
    if transform:
        for kind, args in parse_transform(transform):
            if kind == "matrix" and matrix_group_attrs(args):
                continue
            if kind not in ("translate", "scale", "rotate"):
                findings.append(("error", "transform-" + kind,
                                 f"transform {kind}() wird nicht unterstützt; im Editor Transformationen auf Pfade anwenden"))
    clip = el.get("clip-path")
    if clip and clip.strip() != "none":
        try:
            clip_path_data(clip, stats["ids"])
        except ValueError as e:
            findings.append(("error", "unsupported-clip", str(e)))
    if tag in CONTAINERS:
        for child in el:
            walk(child, props, findings, stats)
        return
    if tag not in SUPPORTED_SHAPES:
        findings.append(("warning", "unknown-element", f"Unbekanntes Element <{tag}> wird ignoriert"))
        return
    stats["shapes"] += 1
    fill = props.get("fill", "#000000")
    for key in ("fill", "stroke"):
        raw = props.get(key, "#000000" if key == "fill" else None)
        if raw and raw.strip().startswith("url("):
            try:
                if key == "stroke":
                    raise ValueError("Verläufe als Kontur werden nicht unterstützt")
                gradient = radial_gradient(raw, stats["ids"])
                if gradient["anisotropy"] > GRADIENT_MAX_ANISOTROPY:
                    findings.append(("warning", "gradient-approximated",
                                     "Verlauf mit ungleicher Skalierung wird als Kreis angenähert"))
            except ValueError as e:
                findings.append(("error", "unsupported-gradient", str(e)))
            continue
        color = normalize_color(raw)
        if color == "invalid":
            findings.append(("error", "color-invalid", f"Farbe {raw!r} nicht auswertbar (Verlauf/Verweis?)"))
        elif color:
            stats["colors"].add(color[0])
            if color[1] < 1.0:
                stats["alpha"] = True
    for key in ("opacity", "fill-opacity", "stroke-opacity"):
        if key in props and float(props[key]) < 1.0:
            stats["alpha"] = True
    if tag == "rect" and normalize_color(fill) not in (None, "invalid"):
        stats["rects"].append((num(el, "width"), num(el, "height")))


def check(name, svg_path):
    profile = profile_for(name)
    findings = []
    stats = {"shapes": 0, "colors": set(), "alpha": False, "rects": [], "ids": {}}
    try:
        root = load_svg(svg_path)
    except ET.ParseError as e:
        return {"name": name, "profile": profile, "ok": False,
                "findings": [{"level": "error", "code": "xml", "message": f"Kein gültiges XML: {e}"}]}
    if local(root.tag) != "svg":
        findings.append(("error", "not-svg", "Wurzelelement ist kein <svg>"))
    stats["ids"] = element_ids(root)
    known = name in icon_names()
    if not known:
        findings.append(("error", "unknown-name", f"{name} steht nicht in docs/icons.md"))
    vb = parse_viewbox(root)
    if not vb:
        findings.append(("error", "viewbox", "Weder viewBox noch width/height vorhanden"))
    else:
        if profile == "flag":
            if abs(vb[2] / vb[3] - FLAG_WIDTH_DP / FLAG_HEIGHT_DP) > 1e-3:
                findings.append(("warning", "flag-ratio",
                                 f"viewBox {vb[2]:g} x {vb[3]:g} ist nicht 4:3"))
        elif abs(vb[2] - vb[3]) > 1e-6:
            findings.append(("warning", "not-square",
                             f"viewBox nicht quadratisch ({vb[2]:g} x {vb[3]:g}); fix füllt auf ein Quadrat auf"))
        expected = size_dp(profile)
        if profile != "flag" and (abs(vb[2] - expected) > 1e-6 or abs(vb[3] - expected) > 1e-6):
            findings.append(("info", "viewbox-size",
                             f"viewBox {vb[2]:g} x {vb[3]:g} statt {expected} x {expected}; wird proportional skaliert"))
    for child in root:
        walk(child, {}, findings, stats)
    if stats["shapes"] == 0:
        findings.append(("error", "empty", "Keine darstellbaren Formen gefunden"))
    if stats["shapes"] > MAX_RECOMMENDED_PATHS:
        findings.append(("warning", "complex", f"{stats['shapes']} Formen; für ein Icon ungewöhnlich viele"))
    colors = sorted(stats["colors"])
    if profile not in ("launcher_foreground", "launcher_background", "flag"):
        want = target_color(profile)
        if len(colors) > 1:
            findings.append(("warning", "multicolor",
                             f"Mehrere Farben {colors}; fix macht alles {want}, Details können verschwinden"))
        elif colors and colors[0] != want:
            findings.append(("info", "color", f"Farbe {colors[0]} wird zu {want}"))
        if stats["alpha"]:
            findings.append(("warning", "alpha", "Teiltransparenz in einem einfarbigen Icon; bitte Vorschau prüfen"))
    if vb and profile in ("standard", "notification", "launcher_monochrome"):
        area = vb[2] * vb[3]
        if any(w * h >= BACKGROUND_AREA_RATIO * area for w, h in stats["rects"]):
            findings.append(("warning", "background",
                             "Flächendeckendes Rechteck erkannt; Icons brauchen einen transparenten Hintergrund"))
    if svg_path and Path(svg_path).exists() and not any(f[0] == "error" for f in findings):
        with tempfile.TemporaryDirectory() as tmp:
            png = Path(tmp) / "r.png"
            render(svg_path, png)
            bbox, (w, h) = alpha_bbox(png)
            if bbox:
                rel = [bbox[0] / w, bbox[1] / h, bbox[2] / w, bbox[3] / h]
                if profile in ("launcher_foreground", "launcher_monochrome"):
                    lo = (1 - SAFE_ZONE_DP / LAUNCHER_SIZE_DP) / 2
                    hi = 1 - lo
                    if rel[0] < lo - 0.01 or rel[1] < lo - 0.01 or rel[2] > hi + 0.01 or rel[3] > hi + 0.01:
                        findings.append(("error", "safe-zone",
                                         "Motiv ragt aus der 66-dp-Safe-Zone; mit fit verkleinern"))
                if profile == "launcher_background" and (rel[0] > 0.01 or rel[1] > 0.01 or rel[2] < 0.99 or rel[3] < 0.99):
                    findings.append(("error", "background-coverage", "Hintergrund deckt die Fläche nicht vollständig ab"))
                if profile in ("standard", "notification"):
                    if (rel[2] - rel[0]) < 0.5 and (rel[3] - rel[1]) < 0.5:
                        findings.append(("warning", "small", "Motiv füllt weniger als die Hälfte der Fläche"))
            else:
                findings.append(("error", "invisible", "Gerendertes Icon ist vollständig transparent"))
    return {
        "name": name,
        "profile": profile,
        "target_size_dp": list(size_dp_wh(profile)) if profile == "flag" else size_dp(profile),
        "colors": colors,
        "shapes": stats["shapes"],
        "ok": not any(f[0] == "error" for f in findings),
        "findings": [{"level": l, "code": c, "message": m} for l, c, m in findings],
    }


# ---------------------------------------------------------------- Korrigieren

def referenced_ids(root):
    """IDs, auf die noch verwiesen wird (clip-path, Verläufe); sie dürfen nicht entfernt werden."""
    found = set()
    for el in root.iter():
        if not isinstance(el.tag, str):
            continue
        for value in list(el.attrib.values()) + list(parse_style(el.get("style")).values()):
            found.update(re.findall(r"url\(\s*#([^)\s]+)\s*\)", value))
            if value.startswith("#"):
                found.add(value[1:])
    return found


def strip_foreign(el, keep_ids=frozenset()):
    """Entfernt Kommentare, Editor-Namensräume und Metadaten rekursiv."""
    for child in list(el):
        tag = child.tag if isinstance(child.tag, str) else ""
        if not tag or local(tag) in ("metadata", "title", "desc") or (
                "}" in tag and not tag.startswith("{" + SVG_NS)):
            el.remove(child)
            continue
        strip_foreign(child, keep_ids)
    for attr in list(el.attrib):
        if "}" in attr and not attr.startswith("{" + XLINK_NS):
            del el.attrib[attr]
        elif local(attr) == "id" and el.attrib[attr] in keep_ids:
            continue
        elif local(attr) in ("id", "class", "data-name") or local(attr).startswith("data-"):
            del el.attrib[attr]


def recolor(el, color):
    style = parse_style(el.get("style"))
    if style:
        for key in ("fill", "stroke"):
            if key in style and normalize_color(style[key]) not in (None,):
                style[key] = color
        el.set("style", ";".join(f"{k}:{v}" for k, v in style.items()))
    for key in ("fill", "stroke"):
        if el.get(key) is not None and normalize_color(el.get(key)) is not None:
            el.set(key, color)
    for child in el:
        recolor(child, color)


def fix(name, svg_path, out_path):
    profile = profile_for(name)
    if profile == "flag":
        sys.exit("fix gilt nicht für Flaggen (Farben und 4:3 bleiben erhalten); check, convert und verify genügen")
    tree = ET.parse(svg_path)
    root = expand_references(tree.getroot())
    changes = []
    before = ET.tostring(root)
    strip_foreign(root, referenced_ids(root))
    if ET.tostring(root) != before:
        changes.append("Metadaten, Editor-Attribute und IDs entfernt")
    vb = parse_viewbox(root)
    if vb and abs(vb[2] - vb[3]) > 1e-6:
        side = max(vb[2], vb[3])
        vb = [vb[0] - (side - vb[2]) / 2, vb[1] - (side - vb[3]) / 2, side, side]
        changes.append("viewBox mittig auf Quadrat erweitert")
    if vb:
        root.set("viewBox", " ".join(f"{v:g}" for v in vb))
    target = size_dp(profile)
    root.set("width", str(target))
    root.set("height", str(target))
    if profile not in ("launcher_foreground", "launcher_background"):
        color = target_color(profile)
        recolor(root, color)
        if root.get("fill") is None:
            root.set("fill", color)
        changes.append(f"alle Farben auf {color} gesetzt")
    tree.write(out_path, encoding="utf-8", xml_declaration=False)
    return {"out": str(out_path), "changes": changes}


def fit(name, svg_path, out_path):
    profile = profile_for(name)
    if profile not in ("launcher_foreground", "launcher_monochrome"):
        sys.exit("fit ist nur für ic_launcher_foreground und ic_launcher_monochrome gedacht")
    tree = ET.parse(svg_path)
    root = tree.getroot()
    vb = parse_viewbox(root)
    with tempfile.TemporaryDirectory() as tmp:
        png = Path(tmp) / "r.png"
        render(svg_path, png)
        bbox, (w, h) = alpha_bbox(png)
    if not bbox:
        sys.exit("Motiv ist leer")
    # Motivmittelpunkt und -größe in viewBox-Koordinaten
    bx0 = vb[0] + bbox[0] / w * vb[2]
    by0 = vb[1] + bbox[1] / h * vb[3]
    bx1 = vb[0] + bbox[2] / w * vb[2]
    by1 = vb[1] + bbox[3] / h * vb[3]
    side = max(bx1 - bx0, by1 - by0)
    zone = vb[2] * SAFE_ZONE_DP / LAUNCHER_SIZE_DP
    scale = zone / side
    cx, cy = (bx0 + bx1) / 2, (by0 + by1) / 2
    tx = vb[0] + vb[2] / 2 - cx * scale
    ty = vb[1] + vb[3] / 2 - cy * scale
    group = ET.Element(f"{{{SVG_NS}}}g", {"transform": f"translate({tx:g} {ty:g}) scale({scale:g})"})
    for child in list(root):
        if local(child.tag) not in IGNORED:
            root.remove(child)
            group.append(child)
    root.append(group)
    tree.write(out_path, encoding="utf-8", xml_declaration=False)
    return {"out": str(out_path), "scale": round(scale, 4)}


# ---------------------------------------------------------------- Umwandeln

def fmt(v):
    text = f"{v:.4f}".rstrip("0").rstrip(".")
    return "0" if text in ("-0", "") else text


def convert_node(el, inherited, out, indent, ids):
    tag = local(el.tag)
    if tag in IGNORED or tag in UNSUPPORTED:
        return
    props = dict(inherited)
    props.update(own_props(el))
    # Deckkraft von Gruppen multipliziert sich in die Kinder
    if tag in CONTAINERS and "opacity" in own_props(el):
        props["_group_opacity"] = str(float(inherited.get("_group_opacity", 1)) * float(own_props(el)["opacity"]))
        props.pop("opacity", None)
    opened = 0
    for kind, args in parse_transform(el.get("transform")):
        attrs = {}
        if kind == "translate":
            attrs = {"translateX": args[0], "translateY": args[1] if len(args) > 1 else 0.0}
        elif kind == "scale":
            attrs = {"scaleX": args[0], "scaleY": args[1] if len(args) > 1 else args[0]}
        elif kind == "rotate":
            attrs = {"rotation": args[0]}
            if len(args) == 3:
                attrs.update({"pivotX": args[1], "pivotY": args[2]})
        elif kind == "matrix" and matrix_group_attrs(args):
            attrs = matrix_group_attrs(args)
        else:
            raise ValueError(f"transform {kind}() nicht unterstützt")
        out.append("  " * (indent + opened) + "<group " + " ".join(
            f'android:{k}="{fmt(v)}"' for k, v in attrs.items()) + ">")
        opened += 1
    clip = el.get("clip-path")
    if clip and clip.strip() != "none":
        if opened == 0:
            out.append("  " * indent + "<group>")
            opened = 1
        # Der Clip-Pfad gilt im Koordinatensystem nach der Transformation des Elements
        out.append("  " * (indent + opened) + f'<clip-path android:pathData="{clip_path_data(clip, ids)}" />')
    depth = indent + opened
    if tag in CONTAINERS:
        for child in el:
            convert_node(child, props, out, depth, ids)
    elif tag in SUPPORTED_SHAPES:
        d = shape_to_path(el)
        if d:
            out.append(path_xml(d, props, depth, ids))
    for i in reversed(range(opened)):
        out.append("  " * (indent + i) + "</group>")


def path_xml(d, props, indent, ids):
    pad = "  " * indent
    attrs = [f'android:pathData="{d.strip()}"']
    group_alpha = float(props.get("_group_opacity", 1))
    opacity = float(props.get("opacity", 1)) * group_alpha
    raw_fill = props.get("fill", "#000000")
    gradient = radial_gradient(raw_fill, ids) if raw_fill.strip().startswith("url(") else None
    fill = None if gradient else normalize_color(raw_fill)
    if fill and fill != "invalid":
        attrs.append(f'android:fillColor="{fill[0]}"')
        alpha = fill[1] * opacity * float(props.get("fill-opacity", 1))
        if alpha < 1:
            attrs.append(f'android:fillAlpha="{fmt(alpha)}"')
        if props.get("fill-rule") == "evenodd":
            attrs.append('android:fillType="evenOdd"')
    stroke = normalize_color(props.get("stroke"))
    if stroke and stroke != "invalid":
        attrs.append(f'android:strokeColor="{stroke[0]}"')
        attrs.append(f'android:strokeWidth="{fmt(float(re.sub(r"[a-z%]+$", "", props.get("stroke-width", "1"))))}"')
        alpha = stroke[1] * opacity * float(props.get("stroke-opacity", 1))
        if alpha < 1:
            attrs.append(f'android:strokeAlpha="{fmt(alpha)}"')
        cap = props.get("stroke-linecap")
        if cap in ("round", "square", "butt"):
            attrs.append(f'android:strokeLineCap="{cap}"')
        join = props.get("stroke-linejoin")
        if join in ("round", "bevel", "miter"):
            attrs.append(f'android:strokeLineJoin="{join}"')
        if props.get("stroke-miterlimit"):
            attrs.append(f'android:strokeMiterLimit="{fmt(float(props["stroke-miterlimit"]))}"')
    head = pad + "<path\n" + "\n".join(pad + "    " + a for a in attrs)
    if not gradient:
        return head + " />"
    items = [f'{pad}        <item android:offset="{fmt(offset)}" android:color="{color}" />'
             for offset, color in gradient["stops"]]
    return "\n".join([
        head + ">",
        f'{pad}  <aapt:attr name="android:fillColor">',
        f'{pad}    <gradient',
        f'{pad}        android:type="radial"',
        f'{pad}        android:centerX="{fmt(gradient["cx"])}"',
        f'{pad}        android:centerY="{fmt(gradient["cy"])}"',
        f'{pad}        android:gradientRadius="{fmt(gradient["r"])}">',
        *items,
        f'{pad}    </gradient>',
        f'{pad}  </aapt:attr>',
        f'{pad}</path>'])


def convert(name, svg_path, out_path):
    result = check(name, svg_path)
    if not result["ok"]:
        sys.exit("Prüfung nicht bestanden; erst Fehler beheben:\n" + json.dumps(result, ensure_ascii=False, indent=2))
    profile = profile_for(name)
    root = load_svg(svg_path)
    ids = element_ids(root)
    vb = parse_viewbox(root)
    dp_w, dp_h = size_dp_wh(profile)
    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        f'<vector xmlns:android="{ANDROID_NS}"',
        f'    xmlns:aapt="{AAPT_NS}"',
        f'    android:width="{dp_w}dp"',
        f'    android:height="{dp_h}dp"',
        f'    android:viewportWidth="{fmt(vb[2])}"',
        f'    android:viewportHeight="{fmt(vb[3])}">',
    ]
    body = []
    root_props = own_props(root)
    if vb[0] or vb[1]:
        body.append(f'  <group android:translateX="{fmt(-vb[0])}" android:translateY="{fmt(-vb[1])}">')
        for child in root:
            convert_node(child, root_props, body, 2, ids)
        body.append("  </group>")
    else:
        for child in root:
            convert_node(child, root_props, body, 1, ids)
    lines += body + ["</vector>", ""]
    Path(out_path).parent.mkdir(parents=True, exist_ok=True)
    Path(out_path).write_text("\n".join(lines), encoding="utf-8")
    return {"out": str(out_path), "paths": sum(1 for l in body if l.strip().startswith("<path"))}


# ---------------------------------------------------------------- Gegentest

def vd_to_svg(xml_path):
    """Baut aus einem VectorDrawable ein SVG, um es mit resvg zu rendern."""
    a = "{" + ANDROID_NS + "}"
    root = ET.parse(xml_path).getroot()
    vw, vh = root.get(a + "viewportWidth"), root.get(a + "viewportHeight")
    svg = ET.Element(f"{{{SVG_NS}}}svg", {"viewBox": f"0 0 {vw} {vh}", "width": vw, "height": vh})
    defs = ET.SubElement(svg, f"{{{SVG_NS}}}defs")

    def add_gradient(path_el):
        """Gibt die url(#id) des Verlaufs in <aapt:attr> zurück, sonst None."""
        for attr in path_el:
            for grad in attr:
                if local(grad.tag) != "gradient":
                    continue
                gid = f"grad{len(defs)}"
                node = ET.SubElement(defs, f"{{{SVG_NS}}}radialGradient", {
                    "id": gid, "gradientUnits": "userSpaceOnUse",
                    "cx": grad.get(a + "centerX"), "cy": grad.get(a + "centerY"), "r": grad.get(a + "gradientRadius")})
                for item in grad:
                    ET.SubElement(node, f"{{{SVG_NS}}}stop",
                                  {"offset": item.get(a + "offset"), "stop-color": item.get(a + "color")})
                return f"url(#{gid})"
        return None

    def build(src, dst):
        # clip-path gilt für alle folgenden Geschwister der Gruppe
        for clip in [c for c in src if local(c.tag) == "clip-path"]:
            cid = f"clip{len(defs)}"
            node = ET.SubElement(defs, f"{{{SVG_NS}}}clipPath", {"id": cid})
            ET.SubElement(node, f"{{{SVG_NS}}}path", {"d": clip.get(a + "pathData")})
            dst = ET.SubElement(dst, f"{{{SVG_NS}}}g", {"clip-path": f"url(#{cid})"})
        for child in src:
            tag = local(child.tag)
            if tag == "group":
                tf = []
                tx, ty = child.get(a + "translateX"), child.get(a + "translateY")
                if tx or ty:
                    tf.append(f"translate({tx or 0} {ty or 0})")
                rot = child.get(a + "rotation")
                if rot:
                    tf.append(f"rotate({rot} {child.get(a + 'pivotX') or 0} {child.get(a + 'pivotY') or 0})")
                sx, sy = child.get(a + "scaleX"), child.get(a + "scaleY")
                if sx or sy:
                    tf.append(f"scale({sx or 1} {sy or 1})")
                g = ET.SubElement(dst, f"{{{SVG_NS}}}g", {"transform": " ".join(tf)} if tf else {})
                build(child, g)
            elif tag == "path":
                attrs = {"d": child.get(a + "pathData"), "fill": child.get(a + "fillColor", "none")}
                gradient_ref = add_gradient(child)
                if gradient_ref:
                    attrs["fill"] = gradient_ref
                if child.get(a + "fillAlpha"):
                    attrs["fill-opacity"] = child.get(a + "fillAlpha")
                if child.get(a + "fillType") == "evenOdd":
                    attrs["fill-rule"] = "evenodd"
                if child.get(a + "strokeColor"):
                    attrs["stroke"] = child.get(a + "strokeColor")
                    attrs["stroke-width"] = child.get(a + "strokeWidth", "0")
                    for src_attr, svg_attr in (("strokeAlpha", "stroke-opacity"), ("strokeLineCap", "stroke-linecap"),
                                               ("strokeLineJoin", "stroke-linejoin"), ("strokeMiterLimit", "stroke-miterlimit")):
                        if child.get(a + src_attr):
                            attrs[svg_attr] = child.get(a + src_attr)
                ET.SubElement(dst, f"{{{SVG_NS}}}path", attrs)

    build(root, svg)
    return ET.tostring(svg, encoding="unicode")


def verify(svg_path, xml_path):
    from PIL import Image, ImageChops, ImageStat
    with tempfile.TemporaryDirectory() as tmp:
        tmp = Path(tmp)
        (tmp / "vd.svg").write_text(vd_to_svg(xml_path), encoding="utf-8")
        render(svg_path, tmp / "a.png")
        render(tmp / "vd.svg", tmp / "b.png")
        img_a = Image.open(tmp / "a.png").convert("RGBA")
        img_b = Image.open(tmp / "b.png").convert("RGBA")
        diff = ImageStat.Stat(ImageChops.difference(img_a, img_b)).mean
    score = max(diff)
    return {"mean_diff": round(score, 2), "ok": score <= MAX_VERIFY_DIFF}


# ---------------------------------------------------------------- Doku

def update_doc(name, delivered, source=None):
    lines = ICONS_DOC.read_text(encoding="utf-8").splitlines()
    for i, line in enumerate(lines):
        if line.startswith(f"| `{name}` |"):
            cells = line.split("|")
            # cells: ['', name, Bedeutung, Emoji, Gelieferte Datei, Quelle, '']
            cells[4] = f" `{delivered}` "
            if source:
                cells[5] = f" {source} "
            lines[i] = "|".join(cells)
            ICONS_DOC.write_text("\n".join(lines) + "\n", encoding="utf-8")
            return {"updated": name}
    sys.exit(f"{name} nicht in docs/icons.md gefunden")


def main(argv):
    if len(argv) < 2:
        sys.exit(__doc__)
    cmd, args = argv[1], argv[2:]
    if cmd == "names":
        result = icon_names()
    elif cmd == "check":
        result = check(args[0], args[1])
    elif cmd == "fix":
        result = fix(args[0], args[1], args[2])
    elif cmd == "fit":
        result = fit(args[0], args[1], args[2])
    elif cmd == "convert":
        result = convert(args[0], args[1], args[2])
    elif cmd == "verify":
        result = verify(args[0], args[1])
    elif cmd == "preview":
        render(args[0], args[1], int(args[2]) if len(args) > 2 else RENDER_SIZE)
        result = {"out": args[1]}
    elif cmd == "update-doc":
        result = update_doc(args[0], args[1], args[2] if len(args) > 2 else None)
    else:
        sys.exit(__doc__)
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main(sys.argv)
