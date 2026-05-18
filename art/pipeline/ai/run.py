"""
run.py — Orchestrator + CLI. Stage-batched, sequential, resumable.

Stages in order:
    1. generate    SDXL+LoRA  → staging/raw/<id>_raw.png
    2. bg_remove   BiRefNet   → staging/cleaned/<id>_cleaned.png
    3. pixelize    Pillow     → staging/pixelized/<id>.png
    4. export      exporter   → app/src/main/res/drawable-*dpi/<id>.png

Each stage loads its model, runs every applicable job, then unloads before
the next stage begins — no two models in VRAM simultaneously. Each stage
is idempotent (skip if downstream artifact exists). --force ignores the skip.

Usage:
    python3 -m art.pipeline.ai.run --jobs art/pipeline/jobs/mining_ores.yaml
    python3 -m art.pipeline.ai.run --jobs ... --stages generate,bg_remove
    python3 -m art.pipeline.ai.run --jobs ... --force
    python3 -m art.pipeline.ai.run --jobs ... --dry-run
"""

from __future__ import annotations

import argparse
import re
from pathlib import Path

from .config import JobFile, load_job_file

ALL_STAGES = ("generate", "bg_remove", "pixelize", "export")
STAGING_ROOT = Path(__file__).resolve().parents[1] / "staging"

_SNAKE_CASE_RE = re.compile(r"^[a-z0-9_]+$")


def _parse_stages(value: str) -> tuple[str, ...]:
    parts = tuple(s.strip() for s in value.split(",") if s.strip())
    unknown = [p for p in parts if p not in ALL_STAGES]
    if unknown:
        raise argparse.ArgumentTypeError(
            f"Unknown stage(s): {unknown}. Valid: {list(ALL_STAGES)}"
        )
    return parts


def _print_plan(job_file: JobFile, stages: tuple[str, ...], staging_root: Path) -> None:
    print(f"[plan] job file: {job_file.path}")
    print(f"[plan] stages:   {','.join(stages)}")
    print(f"[plan] staging:  {staging_root}")
    print(f"[plan] {len(job_file.jobs)} job(s):")
    for j in job_file.jobs:
        print(
            f"  - {j.entity_id:24s} seed={j.seed:>6d}  ({j.steps} steps, cfg={j.cfg})"
        )


def _run_export(job_file: JobFile, staging_root: Path) -> list[Path]:
    """Stage 4: hand each pixelized PNG to the existing exporter."""
    from ..exporter import export  # reuse, don't reinvent

    outputs: list[Path] = []
    for job in job_file.jobs:
        src = staging_root / "pixelized" / f"{job.entity_id}.png"
        if not src.exists():
            print(f"[export] skip {job.entity_id} (no pixelized input)")
            continue
        if not _SNAKE_CASE_RE.match(job.entity_id):
            # Should already be caught by config.py, but defense in depth.
            raise SystemExit(f"entity_id must be snake_case alnum: {job.entity_id!r}")
        print(f"[export] {job.entity_id} → 5 densities")
        outputs.extend(export(src, job.entity_id))
    return outputs


def main() -> int:
    parser = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter
    )
    parser.add_argument(
        "--jobs", type=Path, required=True, help="Path to a YAML job file"
    )
    parser.add_argument(
        "--stages",
        type=_parse_stages,
        default=ALL_STAGES,
        help=f"Comma-separated subset of {','.join(ALL_STAGES)} (default: all)",
    )
    parser.add_argument(
        "--force", action="store_true", help="Re-run stages even if outputs exist"
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Validate + print plan, do not load models",
    )
    parser.add_argument(
        "--staging",
        type=Path,
        default=STAGING_ROOT,
        help=f"Staging dir for intermediates (default: {STAGING_ROOT})",
    )
    args = parser.parse_args()

    job_file = load_job_file(args.jobs)
    staging_root = args.staging
    _print_plan(job_file, args.stages, staging_root)

    if args.dry_run:
        print("[dry-run] not executing any stage")
        return 0

    if "generate" in args.stages:
        from . import generate

        generate.run(job_file.jobs, staging_root, force=args.force)

    if "bg_remove" in args.stages:
        from . import bg_remove

        bg_remove.run(job_file.jobs, staging_root, force=args.force)

    if "pixelize" in args.stages:
        from . import pixelize

        pixelize.run(job_file.jobs, staging_root, force=args.force)

    if "export" in args.stages:
        _run_export(job_file, staging_root)

    print("[done]")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
