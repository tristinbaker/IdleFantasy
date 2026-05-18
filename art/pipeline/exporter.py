"""
exporter.py — Take a single source sprite (PNG or SVG) at 1× base size and
emit the five Android density variants in
`app/src/main/res/drawable-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/<id>.png`.

Per CLAUDE.md:
  - Visual style is pixel art → nearest-neighbor upscaling.
  - Output target: all five Android densities, every time.

Usage:
    python3 -m art.pipeline.exporter <source.png>           # auto-derive id from filename
    python3 -m art.pipeline.exporter <source.png> --id copper_ore

Densities (scaled from 1× mdpi base):
    mdpi    1.0×
    hdpi    1.5×
    xhdpi   2.0×
    xxhdpi  3.0×
    xxxhdpi 4.0×

Requires Pillow. SVG → PNG rasterization is not handled here; rasterize
upstream in the drawing app (Aseprite / etc.) and feed PNG.
"""

from __future__ import annotations

import argparse
from pathlib import Path

try:
    from PIL import Image
except ImportError:
    raise SystemExit(
        "Pillow is not installed. Run: pip install Pillow"
    )

REPO_ROOT     = Path(__file__).resolve().parents[2]
RES_DIR       = REPO_ROOT / "app/src/main/res"
DENSITIES: tuple[tuple[str, float], ...] = (
    ("mdpi",    1.0),
    ("hdpi",    1.5),
    ("xhdpi",   2.0),
    ("xxhdpi",  3.0),
    ("xxxhdpi", 4.0),
)


def export(source: Path, entity_id: str) -> list[Path]:
    if not source.is_file():
        raise FileNotFoundError(f"Source sprite not found: {source}")

    src = Image.open(source).convert("RGBA")
    out_paths: list[Path] = []

    for density, scale in DENSITIES:
        out_dir = RES_DIR / f"drawable-{density}"
        out_dir.mkdir(parents=True, exist_ok=True)
        target_size = (
            max(1, round(src.width  * scale)),
            max(1, round(src.height * scale)),
        )
        # NEAREST keeps pixel art crisp at every density — never use BILINEAR
        # or BICUBIC here.
        resized = src.resize(target_size, Image.NEAREST)
        out_path = out_dir / f"{entity_id}.png"
        resized.save(out_path, "PNG", optimize=True)
        out_paths.append(out_path)

    return out_paths


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("source", type=Path, help="Source PNG (1× / mdpi base resolution)")
    parser.add_argument("--id", help="Entity id (defaults to source filename without extension)")
    args = parser.parse_args()

    entity_id: str = args.id or args.source.stem
    if not entity_id.replace("_", "").isalnum():
        raise SystemExit(f"Entity id must be snake_case alphanumeric: {entity_id!r}")

    out_paths = export(args.source, entity_id)
    print(f"Exported '{entity_id}' to {len(out_paths)} densities:")
    for p in out_paths:
        print(f"  {p.relative_to(REPO_ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
