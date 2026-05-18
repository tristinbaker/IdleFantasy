"""
pixelize.py — Stage 3: Pillow downscale + optional palette quantize. CPU-only.

Input:  <staging>/cleaned/<entity_id>_cleaned.png  (RGBA, 1024×1024-ish)
Output: <staging>/pixelized/<entity_id>.png        (RGBA, target_size square)

The pixelize step trims the cleaned image to its alpha bbox, pads to a square,
then nearest-neighbor downscales to `target_size`. If `palette_size > 0`, the
RGB channels are quantized to N colors with PIL's median-cut quantizer while
preserving the alpha mask.
"""

from __future__ import annotations

from pathlib import Path
from typing import Iterable

from PIL import Image

from .config import Job


def _cleaned_path(staging_root: Path, entity_id: str) -> Path:
    return staging_root / "cleaned" / f"{entity_id}_cleaned.png"


def _pixelized_path(staging_root: Path, entity_id: str) -> Path:
    return staging_root / "pixelized" / f"{entity_id}.png"


def _square_crop_to_alpha(img: Image.Image) -> Image.Image:
    """Crop to alpha bbox, pad with transparent to a square, centered."""
    alpha = img.split()[-1]
    bbox = alpha.getbbox()
    if bbox is None:
        return img
    cropped = img.crop(bbox)
    side = max(cropped.size)
    canvas = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    canvas.paste(cropped, ((side - cropped.width) // 2, (side - cropped.height) // 2))
    return canvas


def _quantize_preserving_alpha(img: Image.Image, palette_size: int) -> Image.Image:
    if palette_size <= 0:
        return img
    rgb = (
        img.convert("RGB")
        .quantize(colors=palette_size, method=Image.Quantize.MEDIANCUT)
        .convert("RGB")
    )
    alpha = img.split()[-1]
    out = Image.new("RGBA", img.size)
    out.paste(rgb, mask=None)
    out.putalpha(alpha)
    return out


def _pixelize_one(src: Path, dst: Path, target_size: int, palette_size: int) -> None:
    with Image.open(src) as img:
        img = img.convert("RGBA")
        squared = _square_crop_to_alpha(img)
        small = squared.resize((target_size, target_size), Image.NEAREST)
        # Re-binarize alpha so anti-aliased edges from the bg remover become
        # crisp pixel-art edges.
        r, g, b, a = small.split()
        a = a.point(lambda v: 255 if v >= 128 else 0)
        small = Image.merge("RGBA", (r, g, b, a))
        small = _quantize_preserving_alpha(small, palette_size)
        dst.parent.mkdir(parents=True, exist_ok=True)
        small.save(dst, "PNG", optimize=True)


def plan(jobs: Iterable[Job], staging_root: Path, *, force: bool) -> list[Job]:
    todo: list[Job] = []
    for j in jobs:
        src = _cleaned_path(staging_root, j.entity_id)
        if not src.exists():
            print(f"[pixelize] skip {j.entity_id} (no cleaned input)")
            continue
        dst = _pixelized_path(staging_root, j.entity_id)
        if dst.exists() and not force:
            print(f"[pixelize] skip {j.entity_id} (already pixelized)")
            continue
        todo.append(j)
    return todo


def run(jobs: Iterable[Job], staging_root: Path, *, force: bool = False) -> list[Path]:
    todo = plan(jobs, staging_root, force=force)
    if not todo:
        print("[pixelize] nothing to do")
        return []
    outputs: list[Path] = []
    for i, job in enumerate(todo, 1):
        src = _cleaned_path(staging_root, job.entity_id)
        dst = _pixelized_path(staging_root, job.entity_id)
        print(
            f"[pixelize] ({i}/{len(todo)}) {job.entity_id} → {job.pixelize.target_size}px, palette={job.pixelize.palette_size}"
        )
        _pixelize_one(src, dst, job.pixelize.target_size, job.pixelize.palette_size)
        outputs.append(dst)
    return outputs
