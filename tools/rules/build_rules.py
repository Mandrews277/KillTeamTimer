"""
Builds app/src/main/assets/rules.json from the official Kill Team downloads on Warhammer Community.

For every team-rules PDF it extracts the *names* of faction rules, ploys, equipment and operative
abilities, tags each one with the game situations it applies to, and then discards the rules text.
Only names, kinds and tags are written out.

    tools/rules/.venv/Scripts/python tools/rules/build_rules.py [--refresh] [--only kommandos,...]

PDFs are cached in tools/rules/cache and only re-downloaded when the site shows a newer date.
Hand corrections live in tools/rules/tag-overrides.json and are applied after automatic tagging.
"""

from __future__ import annotations

import argparse
import functools
import json
import re
import sys
import urllib.request
from dataclasses import dataclass, field
from pathlib import Path

import pymupdf

from tagging import TAGS, tag_text

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parent.parent
CACHE = ROOT / "cache"
OVERRIDES = ROOT / "tag-overrides.json"
OUTPUT = REPO / "app" / "src" / "main" / "assets" / "rules.json"
PREVIOUS_TEXT = CACHE / "text-hashes.json"

API = "https://www.warhammer-community.com/api/search/downloads/"
ASSETS = "https://assets.warhammer-community.com/"
UA = {"User-Agent": "KillTeamTimer rules builder (personal use)", "Content-Type": "application/json"}

CARD_KINDS = {
    "FACTION RULE": "FACTION_RULE",
    "STRATEGY PLOY": "STRATEGY_PLOY",
    "FIREFIGHT PLOY": "FIREFIGHT_PLOY",
    "FACTION EQUIPMENT": "EQUIPMENT",
    "UNIVERSAL EQUIPMENT": "EQUIPMENT",
}
ACTION_COST = re.compile(r"^\d+\s*AP$")


# ---------------------------------------------------------------- download


def fetch_index() -> list[dict]:
    body = json.dumps({"index": "downloads_v2", "searchTerm": "", "gameSystem": "kill-team", "language": "english"})
    req = urllib.request.Request(API, data=body.encode(), headers=UA, method="POST")
    with urllib.request.urlopen(req, timeout=60) as r:
        hits = json.load(r)["hits"]
    wanted = []
    for h in hits:
        cats = h.get("download_categories") or []
        if "team-rules" in cats or h["title"] == "Universal Equipment":
            entry = h["id"]
            wanted.append({
                "title": h["title"],
                "slug": entry["slug"],
                "file": entry["file"],
                "updated": entry.get("last_updated") or entry.get("created_at"),
                "universal": h["title"] == "Universal Equipment",
            })
    return sorted(wanted, key=lambda w: w["title"])


def cached_pdf(entry: dict, refresh: bool) -> Path:
    CACHE.mkdir(exist_ok=True)
    path = CACHE / f"{entry['slug']}.pdf"
    stamp = CACHE / f"{entry['slug']}.file"
    if refresh or not path.exists() or not stamp.exists() or stamp.read_text() != entry["file"]:
        print(f"  downloading {entry['title']}")
        req = urllib.request.Request(ASSETS + entry["file"], headers={"User-Agent": UA["User-Agent"]})
        with urllib.request.urlopen(req, timeout=120) as r:
            path.write_bytes(r.read())
        stamp.write_text(entry["file"])
    return path


# ---------------------------------------------------------------- PDF parsing


@dataclass
class Span:
    x: float
    y: float
    size: float
    font: str
    text: str


@dataclass(eq=False)
class Line:
    spans: list[Span]

    @property
    def x(self) -> float:
        return self.spans[0].x

    @property
    def y(self) -> float:
        return self.spans[0].y

    @functools.cached_property
    def text(self) -> str:
        return clean(" ".join(s.text for s in self.spans))


@dataclass
class Entry:
    kind: str
    heading: str
    sub: str | None
    text: str
    tags: list[str] = field(default_factory=list)


def clean(text: str) -> str:
    text = text.replace("’", "'").replace("‘", "'").replace("“", '"').replace("”", '"')
    text = re.sub(r"\s+([,.)!:;])", r"\1", text)
    return re.sub(r"\s+", " ", text).strip()


def nice(name: str) -> str:
    """'KRUMP 'EM!' -> "Krump 'Em!"; leaves mixed-case names alone."""
    name = clean(name).rstrip(":")
    if name != name.upper():
        return name
    def word(w: str) -> str:
        if w[:1].isalpha():
            return w[:1].upper() + w[1:].lower()
        if w[:1] in "'\"(":
            return w[:2].upper() + w[2:].lower()
        return w.lower()  # "1X" -> "1x"
    return " ".join(word(w) for w in name.split(" "))


def page_lines(page) -> list[Line]:
    lines = []
    flags = pymupdf.TEXTFLAGS_DICT & ~pymupdf.TEXT_PRESERVE_IMAGES
    for block in page.get_text("dict", flags=flags)["blocks"]:
        for line in block.get("lines", []):
            spans = [Span(s["bbox"][0], s["bbox"][1], round(s["size"], 1), s["font"], s["text"])
                     for s in line["spans"] if s["text"].strip()]
            if spans:
                lines.append(Line(spans))
    lines.sort(key=lambda l: (round(l.y), l.x))
    return lines


def is_bold_condensed(span: Span) -> bool:
    return "BdC" in span.font or "BoldCond" in span.font


def sub_rules(lines: list[Line]) -> list[tuple[str | None, str]]:
    """
    Splits card/datacard body lines into (name, text) parts. Names are either bold-condensed
    'Name:' lead-ins (abilities, weapon rules) or action headings followed by an AP cost.
    Text before the first name gets name None.
    """
    parts: list[tuple[str | None, list[str]]] = [(None, [])]
    for line in lines:
        spans = line.spans
        # Action heading: "GET IT DUN!   1AP" (cost may sit in its own line object at the same y).
        if len(spans) >= 2 and ACTION_COST.match(spans[-1].text.strip()) and spans[0].text.isupper():
            parts.append((" ".join(s.text for s in spans[:-1]), []))
            continue
        # Ability lead-in: optional '*' then a bold condensed span ending with ':'.
        lead = spans[1:] if spans[0].text.strip() == "*" and len(spans) > 1 else spans
        name_spans = []
        for s in lead:
            if not is_bold_condensed(s):
                break
            name_spans.append(s)
            if s.text.rstrip().endswith(":"):
                break
        if name_spans and name_spans[-1].text.rstrip().endswith(":"):
            rest = clean(" ".join(s.text for s in lead[len(name_spans):]))
            parts.append((" ".join(s.text for s in name_spans), [rest]))
            continue
        parts[-1][1].append(line.text)
    return [(n, " ".join(t)) for n, t in parts if n or " ".join(t).strip()]


def merge_action_costs(lines: list[Line]) -> list[Line]:
    """PyMuPDF often puts an action's '1AP' in a separate line at the same height; join them."""
    out: list[Line] = []
    for line in lines:
        if out and ACTION_COST.match(line.text) and abs(out[-1].y - line.y) < 2:
            out[-1] = Line(out[-1].spans + line.spans)
        else:
            out.append(line)
    return out


def parse_rule_cards(page, lines: list[Line]) -> list[tuple[str, str, list[Line]]]:
    """Rule pages hold up to four cards (2x2). Returns (kind, name, body lines) per card."""
    mid_x = page.rect.width / 2
    headers = [l for l in lines if l.text.upper() in CARD_KINDS and l.spans[0].size >= 16]
    cards = []
    for h in headers:
        left = h.x < mid_x
        below = [o.y for o in headers if (o.x < mid_x) == left and o.y > h.y + 2]
        bottom = min(below) - 20 if below else page.rect.height
        region = [l for l in lines if (l.x < mid_x) == left and h.y + 2 < l.y < bottom]
        if not region:
            continue
        # Name: the first large line(s) after the header, possibly wrapping.
        name_lines = []
        size = region[0].spans[0].size
        for l in region:
            if l.spans[0].size == size and size >= 10 and (not name_lines or l.y - name_lines[-1].y < size * 1.6):
                name_lines.append(l)
            else:
                break
        if not name_lines:
            continue
        body = [l for l in region if l not in name_lines and l.spans[0].size < 10]
        cards.append((CARD_KINDS[h.text.upper()], " ".join(l.text for l in name_lines), merge_action_costs(body)))
    return cards


def parse_datacards(page, lines: list[Line]) -> list[tuple[str, list[Line], str]]:
    """Datacard pages stack several operatives. Returns (name, body lines, keyword line)."""
    apl_ys = [o.y for o in lines if o.spans[0].text.startswith("APL")]
    names = sorted((l for l in lines if 12 <= l.spans[0].size <= 14 and not l.spans[0].font.startswith("TradeGothic")
                    and any(abs(y - l.y) < 12 for y in apl_ys)), key=lambda l: l.y)
    cards = []
    for i, n in enumerate(names):
        bottom = names[i + 1].y - 2 if i + 1 < len(names) else page.rect.height
        region = merge_action_costs([l for l in lines if n.y + 2 < l.y < bottom])
        region = by_columns(region)
        keywords = next((l.text for l in region if l.spans[0].size <= 6.5 and l.text.isupper()), "")
        # Skip stats and the weapon table: body text starts at the first 7pt prose or action heading.
        body = [l for l in region if 6.9 <= l.spans[0].size < 9
                and (l.spans[0].font != "DIN2014-Demi" or l.spans[0].text.strip() == "*") and not re.fullmatch(r"[\d\"+ /-]+|APL.*|NAME.*|ATK.*|RULES CONTINUE.*", l.text)]
        cards.append((n.text, body, keywords))
    return cards


def by_columns(region: list[Line]) -> list[Line]:
    """
    Abilities and action text can sit in two columns. Within each band between full-width action
    headings, read the left column top to bottom, then the right.
    """
    left_edge = min((l.x for l in region), default=0)
    out: list[Line] = []
    band: list[Line] = []
    for line in region + [None]:
        if line is None or (ACTION_COST.match(line.spans[-1].text.strip()) and line.x < left_edge + 100):
            out += sorted(band, key=lambda l: (l.x > left_edge + 100, l.y, l.x))
            band = []
            if line is not None:
                out.append(line)
        else:
            band.append(line)
    return out


def operative_name(raw: str, faction: str) -> str:
    name = clean(raw)
    if faction and name.upper().startswith(faction.upper() + " ") and len(name) > len(faction) + 1:
        name = name[len(faction) + 1:]
    return nice(name)


def parse_team(pdf: Path) -> list[Entry]:
    entries: list[Entry] = []
    seen: set[tuple[str, str | None]] = set()

    def add(kind: str, heading: str, sub: str | None, text: str):
        key = (heading, sub)
        if key in seen:  # rules that continue onto the back of a card
            for e in entries:
                if (e.heading, e.sub) == key:
                    e.text += " " + text
            return
        seen.add(key)
        entries.append(Entry(kind, heading, sub, text))

    doc = pymupdf.open(pdf)
    pages = [(page, page_lines(page)) for page in doc]
    # The faction keyword (e.g. KOMMANDO) prefixes operative names; drop it for display.
    keyword_lines = [k for page, lines in pages for _, _, k in parse_datacards(page, lines) if k]
    firsts = [k.split(",")[0].strip() for k in keyword_lines]
    faction = max(set(firsts), key=firsts.count) if firsts else ""
    for page, lines in pages:
        for kind, name, body in parse_rule_cards(page, lines):
            parts = sub_rules(body)
            named = [(n, t) for n, t in parts if n]
            intro = " ".join(t for n, t in parts if not n)
            if named and kind in ("FACTION_RULE", "EQUIPMENT"):
                # A faction rule or equipment with several named parts: heading = card, sub = part.
                if intro.strip():
                    add(kind, nice(name), None, intro)
                for n, t in named:
                    add(kind, nice(name), nice(n), t)
            else:
                add(kind, nice(name), None, " ".join(t for _, t in parts))
        for raw, body, keywords in parse_datacards(page, lines):
            op = operative_name(raw, faction)
            for n, t in sub_rules(body):
                if n:
                    add("OPERATIVE", op, nice(n), t)
    return entries


# ---------------------------------------------------------------- build


def slug(s: str) -> str:
    return re.sub(r"[^a-z0-9]+", "-", s.lower()).strip("-")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--refresh", action="store_true", help="re-download every PDF")
    ap.add_argument("--only", help="comma-separated team slugs to build (for testing)")
    ap.add_argument("--dump", action="store_true", help="print every entry with its tags")
    args = ap.parse_args()

    overrides = json.loads(OVERRIDES.read_text(encoding="utf-8")) if OVERRIDES.exists() else {}
    overrides = {k: v for k, v in overrides.items() if not k.startswith("_")}
    previous = json.loads(PREVIOUS_TEXT.read_text()) if PREVIOUS_TEXT.exists() else {}
    hashes: dict[str, int] = {}

    print("Fetching download list…")
    index = fetch_index()
    if args.only:
        only = set(args.only.split(","))
        index = [e for e in index if e["slug"] in only]

    teams, universal, untagged, changed = [], [], [], []
    for entry in index:
        pdf = cached_pdf(entry, args.refresh)
        items = []
        for e in parse_team(pdf):
            item_id = "/".join(slug(p) for p in (entry["slug"], e.heading, e.sub or "") if p)
            tags = tag_text(e.kind, e.text)
            o = overrides.get(item_id, {})
            tags = sorted((set(tags) | set(o.get("add", []))) - set(o.get("remove", [])))
            if o.get("hide"):
                continue
            hashes[item_id] = hash_text(e.text)
            if item_id in previous and previous[item_id] != hashes[item_id]:
                changed.append(item_id)
            if not tags:
                untagged.append(item_id)
                tags = ["general"]
            if args.dump:
                print(f"    {e.kind:15} {e.heading}{' / ' + e.sub if e.sub else ''}  {tags}")
            items.append({"id": item_id, "kind": e.kind, "heading": e.heading, "sub": e.sub, "tags": tags})
        counts = {k: sum(1 for i in items if i["kind"] == k) for k in
                  ("FACTION_RULE", "STRATEGY_PLOY", "FIREFIGHT_PLOY", "EQUIPMENT", "OPERATIVE")}
        print(f"  {entry['title']:28} " + "  ".join(f"{k.split('_')[0][:5].lower()}={v}" for k, v in counts.items()))
        if entry["universal"]:
            universal = items
        else:
            teams.append({"id": entry["slug"], "name": entry["title"], "updated": entry["updated"], "items": items})

    new = [i for i in hashes if previous and i not in previous]
    if not args.only:
        OUTPUT.parent.mkdir(parents=True, exist_ok=True)
        OUTPUT.write_text(json.dumps({
            "source": "https://www.warhammer-community.com/en-gb/downloads/kill-team/",
            "tags": TAGS,
            "universal": universal,
            "teams": teams,
        }, ensure_ascii=False, indent=1), encoding="utf-8")
        PREVIOUS_TEXT.write_text(json.dumps(hashes))
        print(f"\nWrote {OUTPUT.relative_to(REPO)} ({OUTPUT.stat().st_size // 1024} KB, {len(teams)} teams)")

    print(f"\nUntagged ({len(untagged)}):")
    for i in untagged:
        print("  " + i)
    if previous:
        print(f"\nNew since last build ({len(new)}):")
        for i in new:
            print("  " + i)
        print(f"\nWording changed since last build — check their tags ({len(changed)}):")
        for i in changed:
            print("  " + i)
    return 0


def hash_text(text: str) -> int:
    import zlib
    return zlib.crc32(clean(text).lower().encode())


if __name__ == "__main__":
    sys.exit(main())
