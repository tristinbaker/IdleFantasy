#!/usr/bin/env python3
"""
generate_art_manifest.py — Walk the game's JSON data files and emit a
machine-readable + human-readable manifest of every named entity that needs a
sprite. Output paths (at repo root):

    ART_MANIFEST.md       (human-friendly, grouped + counted)
    art_manifest.json     (machine-friendly, ingestable by the art pipeline)

Entity ids are the snake_case keys / "name" fields from the JSON files. They
match the R.drawable.<id> the app will request via EntityIcon, so once a sprite
lands at app/src/main/res/drawable-xxhdpi/<id>.png it is picked up with zero
Kotlin changes.

Usage:
    python3 scripts/generate_art_manifest.py
"""

from __future__ import annotations

import json
from pathlib import Path

REPO_ROOT  = Path(__file__).resolve().parent.parent
ASSETS     = REPO_ROOT / "app/src/main/assets/data"
MD_OUT     = REPO_ROOT / "ART_MANIFEST.md"
JSON_OUT   = REPO_ROOT / "art_manifest.json"


# ---------------------------------------------------------------------------
# Loaders — each returns a list of entity ids
# ---------------------------------------------------------------------------

def load_json(rel: str | Path) -> dict | list:
    return json.loads((ASSETS / rel).read_text())


def keys_of(path: str) -> list[str]:
    data = load_json(path)
    if isinstance(data, dict):
        return sorted(data.keys())
    raise ValueError(f"Expected dict at {path}, got {type(data).__name__}")


def flat_values(path: str) -> list[str]:
    """items.json shape: {category: [id, id, ...]}. Returns a flat sorted list."""
    data = load_json(path)
    if not isinstance(data, dict):
        raise ValueError(f"Expected dict at {path}, got {type(data).__name__}")
    out: set[str] = set()
    for v in data.values():
        if isinstance(v, list):
            out.update(v)
    return sorted(out)


def dungeon_ids() -> list[str]:
    out: list[str] = []
    dungeons_dir = ASSETS / "dungeons"
    if dungeons_dir.is_dir():
        for fp in sorted(dungeons_dir.glob("*.json")):
            data = json.loads(fp.read_text())
            name = data.get("name") or fp.stem
            out.append(name)
    return out


def skill_ids() -> list[str]:
    """Skills come from the directory listing under data/skills/."""
    out: list[str] = []
    skills_dir = ASSETS / "skills"
    if skills_dir.is_dir():
        for fp in sorted(skills_dir.glob("*.json")):
            out.append(fp.stem)
    return out


# ---------------------------------------------------------------------------
# Categories — ordered so the markdown reads in dependency order (resources →
# equipment → enemies → places → meta).
# ---------------------------------------------------------------------------

CATEGORIES: list[tuple[str, str, callable]] = [
    ("Skills",        "Skill icons (one per skill).",                    lambda: skill_ids()),
    ("Ores",          "Mineable ores.",                                   lambda: keys_of("ores.json")),
    ("Trees",         "Choppable trees.",                                 lambda: keys_of("trees.json")),
    ("Logs",          "Log items dropped by trees.",                      lambda: keys_of("logs.json")),
    ("Crops",         "Farmable crops.",                                  lambda: keys_of("crops.json")),
    ("Items",         "All inventory items, grouped flat from items.json.", lambda: flat_values("items.json")),
    ("Bones",         "Bone drops, used in Prayer.",                      lambda: keys_of("bones.json")),
    ("Gems",          "Mineable / cuttable gems.",                        lambda: keys_of("gems.json")),
    ("Runes",         "Runecrafting outputs.",                            lambda: keys_of("runes.json")),
    ("Spells",        "Castable spells.",                                 lambda: keys_of("spells.json")),
    ("Pets",          "Pet companions.",                                  lambda: keys_of("pets.json")),
    ("Equipment",     "Wearable equipment (weapons, armor, tools).",      lambda: keys_of("equipment.json")),
    ("Enemies",       "Combat enemies (per id).",                         lambda: keys_of("enemies.json")),
    ("Raid bosses",   "End-game raid bosses.",                            lambda: keys_of("raid_bosses.json")),
    ("Dungeons",      "Dungeon banner art (one per dungeon file).",       lambda: dungeon_ids()),
    ("Agility courses","Agility course banners.",                         lambda: keys_of("agility_courses.json")),
    ("Quests",        "Quest icons.",                                     lambda: keys_of("quests.json")),
]


# ---------------------------------------------------------------------------
# Output writers
# ---------------------------------------------------------------------------

def build_manifest() -> tuple[dict, str]:
    """Returns (json_payload, markdown_text)."""
    categories: list[dict] = []
    md_sections: list[str] = []
    grand_total = 0

    for title, blurb, loader in CATEGORIES:
        try:
            ids = loader()
        except FileNotFoundError:
            ids = []
        ids = sorted(set(ids))
        categories.append({"name": title, "description": blurb, "count": len(ids), "ids": ids})
        grand_total += len(ids)

        bullets = "\n".join(f"- `{i}`" for i in ids) or "_(no entries found)_"
        md_sections.append(f"## {title}\n\n_{blurb}_\n\nCount: **{len(ids)}**\n\n{bullets}\n")

    json_payload = {
        "schema_version": 1,
        "total": grand_total,
        "categories": categories,
    }

    md = (
        "# Art Manifest\n\n"
        "Auto-generated from `app/src/main/assets/data/`. Run `python3 scripts/generate_art_manifest.py` to regenerate.\n\n"
        "Each id below corresponds to the `R.drawable.<id>` the app's `EntityIcon` composable will resolve at runtime. "
        "Drop a PNG at `app/src/main/res/drawable-xxhdpi/<id>.png` (and lower densities for crisper rendering) to fill it in.\n\n"
        f"**Total entities:** {grand_total}\n\n"
        "---\n\n"
        + "\n".join(md_sections)
    )

    return json_payload, md


def main() -> int:
    if not ASSETS.is_dir():
        print(f"error: cannot find game data at {ASSETS}")
        return 1

    payload, md = build_manifest()

    JSON_OUT.write_text(json.dumps(payload, indent=2, sort_keys=False) + "\n")
    MD_OUT.write_text(md)

    print(f"Wrote {JSON_OUT.relative_to(REPO_ROOT)}: {payload['total']} entities across {len(payload['categories'])} categories.")
    print(f"Wrote {MD_OUT.relative_to(REPO_ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
