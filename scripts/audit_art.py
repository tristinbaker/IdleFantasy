#!/usr/bin/env python3
"""
audit_art.py — Compare the art manifest against the actual drawable files
landed in `app/src/main/res/drawable-xxhdpi/` and print a punch list:

  - Present: ids that have a PNG.
  - Missing: ids in the manifest with no PNG.
  - Orphans: PNGs that don't correspond to any manifest id.

Use the `--missing-only` flag to print only the missing list (handy for
piping into the drawing queue).

Usage:
    python3 scripts/audit_art.py
    python3 scripts/audit_art.py --missing-only
    python3 scripts/audit_art.py --category Ores
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path

REPO_ROOT     = Path(__file__).resolve().parent.parent
MANIFEST_JSON = REPO_ROOT / "art_manifest.json"
DRAWABLE_DIR  = REPO_ROOT / "app/src/main/res/drawable-xxhdpi"


def load_manifest() -> dict:
    if not MANIFEST_JSON.is_file():
        raise SystemExit(
            f"Manifest not found at {MANIFEST_JSON}. "
            "Run scripts/generate_art_manifest.py first."
        )
    return json.loads(MANIFEST_JSON.read_text())


def present_drawables() -> set[str]:
    if not DRAWABLE_DIR.is_dir():
        return set()
    return {p.stem for p in DRAWABLE_DIR.glob("*.png")}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--missing-only", action="store_true", help="Print only the missing ids, one per line.")
    parser.add_argument("--category", help="Restrict the report to a single category from the manifest.")
    args = parser.parse_args()

    manifest = load_manifest()
    present = present_drawables()

    total_missing = 0
    total_present = 0
    manifest_ids: set[str] = set()

    for cat in manifest["categories"]:
        if args.category and cat["name"].lower() != args.category.lower():
            continue
        cat_ids = cat["ids"]
        manifest_ids.update(cat_ids)
        cat_missing = [i for i in cat_ids if i not in present]
        cat_present = [i for i in cat_ids if i in present]
        total_missing += len(cat_missing)
        total_present += len(cat_present)

        if args.missing_only:
            for i in cat_missing:
                print(i)
            continue

        if not cat_ids:
            continue
        print(f"=== {cat['name']} — {len(cat_present)}/{len(cat_ids)} done ===")
        if cat_missing:
            sample = cat_missing[:6]
            extra = f" (+{len(cat_missing) - len(sample)} more)" if len(cat_missing) > len(sample) else ""
            print(f"  missing: {', '.join(sample)}{extra}")
        print()

    if not args.missing_only:
        orphans = sorted(present - manifest_ids)
        if orphans:
            print(f"=== Orphan PNGs (not in manifest) ===")
            for o in orphans:
                print(f"  {o}")
            print()

        total_ids = total_present + total_missing
        pct = (100.0 * total_present / total_ids) if total_ids else 0.0
        print(f"--- Total: {total_present}/{total_ids} sprites ({pct:.1f}%) ---")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
