"""
config.py — Job-file schema, YAML loader, and entity_id validation against
art_manifest.json.

A job file looks like:

    defaults:
      width: 1024
      height: 1024
      steps: 25
      cfg: 7.0
      lora_scale: 1.0
      sampler: dpmpp_2m_karras
      negative: "blurry, photo, ..."
      pixelize:
        target_size: 64
        palette_size: 16
      bg_model: birefnet-general

    jobs:
      - entity_id: copper_ore
        prompt: "pixel art, 16-bit RPG sprite of ..."
        seed: 1001
        # any defaults key can be overridden per-job

Per-job keys override the file-level defaults.
"""

from __future__ import annotations

import json
import re
from dataclasses import dataclass, field, replace
from pathlib import Path
from typing import Any

import yaml

REPO_ROOT = Path(__file__).resolve().parents[3]
MANIFEST_JSON = REPO_ROOT / "art_manifest.json"

# Must match the validation that exporter.main() applies. snake_case alnum.
_SNAKE_CASE_RE = re.compile(r"^[a-z0-9_]+$")


@dataclass(frozen=True)
class PixelizeConfig:
    target_size: int = 64  # mdpi 1× base size, square
    palette_size: int = 16  # 0 = no quantization


@dataclass(frozen=True)
class Defaults:
    width: int = 1024
    height: int = 1024
    steps: int = 25
    cfg: float = 7.0
    lora_scale: float = 1.0
    sampler: str = "dpmpp_2m_karras"
    negative: str = (
        "blurry, photo, smooth, anti-aliased, gradient, 3d render, "
        "low quality, signature, watermark, text"
    )
    pixelize: PixelizeConfig = field(default_factory=PixelizeConfig)
    bg_model: str = "birefnet-general"


@dataclass(frozen=True)
class Job:
    entity_id: str
    prompt: str
    seed: int
    # Resolved per-job after merging defaults.
    width: int
    height: int
    steps: int
    cfg: float
    lora_scale: float
    sampler: str
    negative: str
    pixelize: PixelizeConfig
    bg_model: str


@dataclass(frozen=True)
class JobFile:
    path: Path
    defaults: Defaults
    jobs: tuple[Job, ...]


def _load_manifest_ids() -> set[str]:
    data = json.loads(MANIFEST_JSON.read_text())
    ids: set[str] = set()
    for cat in data["categories"]:
        ids.update(cat["ids"])
    return ids


def _merge_pixelize(
    base: PixelizeConfig, override: dict[str, Any] | None
) -> PixelizeConfig:
    if not override:
        return base
    return replace(base, **override)


def _merge_defaults(base: Defaults, override: dict[str, Any]) -> Defaults:
    pix = _merge_pixelize(base.pixelize, override.pop("pixelize", None))
    return replace(base, pixelize=pix, **override)


def _job_from_dict(d: dict[str, Any], defaults: Defaults) -> Job:
    if "entity_id" not in d:
        raise ValueError(f"Job missing 'entity_id': {d!r}")
    if "prompt" not in d:
        raise ValueError(f"Job {d.get('entity_id')!r} missing 'prompt'")
    if "seed" not in d:
        raise ValueError(
            f"Job {d['entity_id']!r} missing 'seed' (set explicitly for reproducibility)"
        )

    entity_id = d["entity_id"]
    if not _SNAKE_CASE_RE.match(entity_id):
        raise ValueError(f"entity_id must be snake_case alnum: {entity_id!r}")

    overrides = {k: v for k, v in d.items() if k not in ("entity_id", "prompt", "seed")}
    pix = _merge_pixelize(defaults.pixelize, overrides.pop("pixelize", None))
    merged = replace(defaults, pixelize=pix, **overrides)

    return Job(
        entity_id=entity_id,
        prompt=d["prompt"],
        seed=int(d["seed"]),
        width=merged.width,
        height=merged.height,
        steps=merged.steps,
        cfg=merged.cfg,
        lora_scale=merged.lora_scale,
        sampler=merged.sampler,
        negative=merged.negative,
        pixelize=merged.pixelize,
        bg_model=merged.bg_model,
    )


def load_job_file(path: Path, *, validate_against_manifest: bool = True) -> JobFile:
    raw = yaml.safe_load(path.read_text()) or {}
    if not isinstance(raw, dict):
        raise ValueError(f"{path}: expected a YAML mapping at the top level")

    defaults_override = raw.get("defaults") or {}
    if not isinstance(defaults_override, dict):
        raise ValueError(f"{path}: 'defaults' must be a mapping")
    defaults = _merge_defaults(Defaults(), dict(defaults_override))

    jobs_raw = raw.get("jobs") or []
    if not isinstance(jobs_raw, list) or not jobs_raw:
        raise ValueError(f"{path}: 'jobs' must be a non-empty list")

    jobs = tuple(_job_from_dict(j, defaults) for j in jobs_raw)

    seen: set[str] = set()
    for j in jobs:
        if j.entity_id in seen:
            raise ValueError(f"{path}: duplicate entity_id {j.entity_id!r}")
        seen.add(j.entity_id)

    if validate_against_manifest:
        manifest_ids = _load_manifest_ids()
        unknown = [j.entity_id for j in jobs if j.entity_id not in manifest_ids]
        if unknown:
            raise ValueError(
                f"{path}: entity_ids not in art_manifest.json: {unknown!r}. "
                f"Regenerate the manifest or fix the typo."
            )

    return JobFile(path=path, defaults=defaults, jobs=jobs)
