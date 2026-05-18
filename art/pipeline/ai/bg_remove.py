"""
bg_remove.py — Stage 2: rembg + BiRefNet → transparent-alpha 1024×1024 PNGs.

VRAM contract: this stage assumes SDXL has been fully unloaded by stage 1.
BiRefNet's onnxruntime session is created once, reused across all jobs,
then torn down before returning.

Input:  <staging>/raw/<entity_id>_raw.png         (RGB, from generate.py)
Output: <staging>/cleaned/<entity_id>_cleaned.png (RGBA, alpha-cut)
"""

from __future__ import annotations

import gc
import time
from pathlib import Path
from typing import Iterable

from .config import Job


def _raw_path(staging_root: Path, entity_id: str) -> Path:
    return staging_root / "raw" / f"{entity_id}_raw.png"


def _cleaned_path(staging_root: Path, entity_id: str) -> Path:
    return staging_root / "cleaned" / f"{entity_id}_cleaned.png"


def plan(jobs: Iterable[Job], staging_root: Path, *, force: bool) -> list[Job]:
    todo: list[Job] = []
    for j in jobs:
        raw = _raw_path(staging_root, j.entity_id)
        if not raw.exists():
            print(f"[bg_remove] skip {j.entity_id} (no raw input at {raw.name})")
            continue
        out = _cleaned_path(staging_root, j.entity_id)
        if out.exists() and not force:
            print(f"[bg_remove] skip {j.entity_id} (already cleaned)")
            continue
        todo.append(j)
    return todo


def run(jobs: Iterable[Job], staging_root: Path, *, force: bool = False) -> list[Path]:
    todo = plan(jobs, staging_root, force=force)
    if not todo:
        print("[bg_remove] nothing to do")
        return []

    # rembg pulls in onnxruntime. Local import keeps stage 1 cold-start light.
    from rembg import new_session, remove
    from PIL import Image

    cleaned_dir = staging_root / "cleaned"
    cleaned_dir.mkdir(parents=True, exist_ok=True)

    # All jobs should declare the same bg_model in practice; use the first.
    bg_model = todo[0].bg_model
    print(f"[bg_remove] loading rembg session ({bg_model})…")
    t0 = time.time()
    session = new_session(bg_model)
    print(f"[bg_remove] loaded in {time.time() - t0:.1f}s")

    outputs: list[Path] = []
    try:
        for i, job in enumerate(todo, 1):
            if job.bg_model != bg_model:
                # If a later job wants a different model, finish the current
                # batch first then swap. Cheaper than reloading per-job in the
                # common case where the whole file shares one model.
                raise RuntimeError(
                    f"Mixed bg_model in one job file is not supported "
                    f"(got {bg_model!r} and {job.bg_model!r}). Split into two files."
                )
            print(f"[bg_remove] ({i}/{len(todo)}) {job.entity_id}")
            with Image.open(_raw_path(staging_root, job.entity_id)) as src:
                cut = remove(src.convert("RGBA"), session=session)
            out = _cleaned_path(staging_root, job.entity_id)
            cut.save(out, "PNG")
            outputs.append(out)
    finally:
        print("[bg_remove] unloading rembg session…")
        del session
        gc.collect()
        try:
            import torch

            if torch.cuda.is_available():
                torch.cuda.empty_cache()
        except ImportError:
            pass

    return outputs
