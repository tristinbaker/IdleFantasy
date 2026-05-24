"""
generate.py — Stage 1: SDXL 1.0 base + Pixel Art XL LoRA → raw 1024×1024 PNGs.

VRAM contract: SDXL is the ONLY model loaded during this stage. The pipeline
object is built once, all jobs run sequentially, then torn down with an
explicit cuda.empty_cache() before this function returns. The next stage
(bg_remove) must not be invoked until this returns.

Skip rule: if `<staging>/raw/<entity_id>_raw.png` already exists and --force
was not passed, that job is skipped — supports resumable runs.
"""

from __future__ import annotations

import gc
import time
from pathlib import Path
from typing import Iterable

from .config import Job

SDXL_BASE = "stabilityai/stable-diffusion-xl-base-1.0"
PIXEL_ART_LORA = "nerijs/pixel-art-xl"
PIXEL_ART_LORA_WEIGHT = "pixel-art-xl.safetensors"


def _raw_path(staging_root: Path, entity_id: str) -> Path:
    return staging_root / "raw" / f"{entity_id}_raw.png"


def plan(jobs: Iterable[Job], staging_root: Path, *, force: bool) -> list[Job]:
    todo: list[Job] = []
    for j in jobs:
        out = _raw_path(staging_root, j.entity_id)
        if out.exists() and not force:
            print(
                f"[generate] skip {j.entity_id} (already at {out.relative_to(staging_root.parent)})"
            )
            continue
        todo.append(j)
    return todo


def run(jobs: Iterable[Job], staging_root: Path, *, force: bool = False) -> list[Path]:
    todo = plan(jobs, staging_root, force=force)
    if not todo:
        print("[generate] nothing to do")
        return []

    # Local imports — heavy deps, only needed when we actually run a job.
    import torch
    from diffusers import StableDiffusionXLPipeline, DPMSolverMultistepScheduler

    if not torch.cuda.is_available():
        raise RuntimeError(
            "CUDA is not available. This stage requires an NVIDIA GPU. "
            "Use --dry-run to validate the job file without running models."
        )

    raw_dir = staging_root / "raw"
    raw_dir.mkdir(parents=True, exist_ok=True)

    print(f"[generate] loading SDXL ({SDXL_BASE}) in fp16…")
    t0 = time.time()
    pipe = StableDiffusionXLPipeline.from_pretrained(
        SDXL_BASE,
        torch_dtype=torch.float16,
        variant="fp16",
        use_safetensors=True,
        add_watermarker=False,
    )
    pipe.scheduler = DPMSolverMultistepScheduler.from_config(
        pipe.scheduler.config, use_karras_sigmas=True
    )
    pipe.load_lora_weights(PIXEL_ART_LORA, weight_name=PIXEL_ART_LORA_WEIGHT)
    pipe = pipe.to("cuda")
    # VAE tiling decodes the output in tiles instead of one tensor —
    # peak decode VRAM drops from ~1 GB to ~150 MB. Required for stable
    # 1024² SDXL on 12 GB cards across long batch runs (fragmentation
    # eats headroom and the VAE upsampler is the first thing to OOM).
    pipe.enable_vae_tiling()
    pipe.enable_vae_slicing()
    pipe.set_progress_bar_config(disable=False)
    print(
        f"[generate] loaded in {time.time() - t0:.1f}s; VRAM {torch.cuda.memory_allocated() / 1e9:.2f} GB"
    )

    outputs: list[Path] = []
    try:
        for i, job in enumerate(todo, 1):
            print(
                f"[generate] ({i}/{len(todo)}) {job.entity_id}  seed={job.seed}  steps={job.steps}"
            )
            generator = torch.Generator(device="cuda").manual_seed(job.seed)
            image = pipe(
                prompt=job.prompt,
                negative_prompt=job.negative,
                width=job.width,
                height=job.height,
                num_inference_steps=job.steps,
                guidance_scale=job.cfg,
                cross_attention_kwargs={"scale": job.lora_scale},
                generator=generator,
            ).images[0]
            out = _raw_path(staging_root, job.entity_id)
            image.save(out, "PNG")
            outputs.append(out)
    finally:
        print("[generate] unloading SDXL…")
        del pipe
        gc.collect()
        if torch.cuda.is_available():
            torch.cuda.empty_cache()
            torch.cuda.synchronize()
            print(
                f"[generate] VRAM after unload: {torch.cuda.memory_allocated() / 1e9:.2f} GB"
            )

    return outputs
