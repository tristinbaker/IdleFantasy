# `art/pipeline/ai/` — Local AI sprite-generation pipeline

Local SDXL + Pixel Art XL LoRA + BiRefNet background remover, designed for an
RTX 3080 12 GB. **One model in VRAM at a time** — stages load, run all jobs,
then unload before the next stage begins. The final stage chains into the
existing density exporter so all five Android density variants land in
`app/src/main/res/drawable-*dpi/` in one command.

## Install

```bash
# 1. Install torch + cuda wheels FIRST (PyPI's torch package is CPU-only):
pip install --index-url https://download.pytorch.org/whl/cu121 \
    "torch>=2.2,<2.5" "torchvision>=0.17,<0.20"

# 2. Install the rest:
pip install -r art/pipeline/ai/requirements.txt
```

First run downloads:
- SDXL 1.0 base, fp16 variant → `~/.cache/huggingface/hub/`, ~7 GB
- `nerijs/pixel-art-xl` LoRA → same cache, ~150 MB
- BiRefNet ONNX weights → `~/.u2net/`, ~880 MB

Total cold-start disk: ~10 GB. Subsequent runs reuse the cache.

## Run

```bash
# Full pipeline for the Mining ores:
python3 -m art.pipeline.ai.run --jobs art/pipeline/jobs/mining_ores.yaml

# Subsets (resumable — skip jobs whose downstream output exists):
python3 -m art.pipeline.ai.run --jobs ... --stages generate
python3 -m art.pipeline.ai.run --jobs ... --stages bg_remove,pixelize,export

# Force re-run, ignore caches:
python3 -m art.pipeline.ai.run --jobs ... --force

# Dry run — validate YAML + entity IDs against art_manifest.json, do not
# load any model. Safe on machines without a GPU.
python3 -m art.pipeline.ai.run --jobs ... --dry-run
```

## Stages

| # | Stage | Model | VRAM | Output |
|---|---|---|---|---|
| 1 | `generate` | SDXL 1.0 base + Pixel Art XL LoRA (fp16) | ~8 GB | `art/pipeline/staging/raw/<id>_raw.png` |
| 2 | `bg_remove` | rembg + `birefnet-general` | ~1.5 GB | `art/pipeline/staging/cleaned/<id>_cleaned.png` |
| 3 | `pixelize` | Pillow downscale + quantize | CPU | `art/pipeline/staging/pixelized/<id>.png` |
| 4 | `export` | Existing `art/pipeline/exporter.py` | CPU | `app/src/main/res/drawable-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/<id>.png` |

Each stage is idempotent. If a stage's output exists, that job is skipped
(unless `--force`). Workflow:

1. Run all stages. Glance at `art/pipeline/staging/raw/` — anything ugly?
2. Edit the prompt/seed for the bad ones in the YAML, run again with
   `--stages generate --force` (only) for those entity IDs (or simply
   delete their `_raw.png` and re-run without `--force`).
3. Once all raws look good, let bg_remove + pixelize + export run to land
   the density-export PNGs.

## VRAM tips for the 3080 12 GB

The defaults already fit comfortably. If you want headroom (e.g. screen
sharing while it runs):

- **Lower steps**: 20 instead of 25. ~20% faster, marginal quality cost
  with the Pixel Art XL LoRA.
- **Lower resolution**: 768×768 instead of 1024×1024 in `defaults.width/height`.
  Halves VRAM for the generation step but reduces detail. The pixelize step
  doesn't care — it's downscaling anyway.
- **`enable_model_cpu_offload()`**: not enabled by default because the 3080
  has the VRAM for the full pipeline; flip it on in `generate.py` if you
  want to multitask with another GPU workload.

## Job file schema

See `art/pipeline/jobs/mining_ores.yaml` for a working example. Per-job keys
override the file-level `defaults`. Every `entity_id` is validated against
`art_manifest.json` at load time — typos fail immediately rather than after
a 30 s model load.

## What this is NOT

- **Not a substitute for hand-drawing.** CLAUDE.md is clear that AI output
  passes through human review. The staging dir is intentionally gitignored
  so the author can prune before any sprite is exported to the drawables.
- **Not animation.** Animation in-betweening is a separate pipeline.
- **Not committed weights.** Models live in the HF / u2net caches; never
  commit them to the repo.

## Troubleshooting

- `CUDA out of memory` on stage 1 → lower `width/height` to 768 in the job
  file's `defaults`, or enable CPU offload.
- `onnxruntime-gpu` import error → make sure the CUDA toolkit version your
  driver supports matches the wheel (`onnxruntime-gpu` ≥ 1.17 wants CUDA 12).
- "Background still visible" after bg_remove → try a different rembg model
  (`bg_model: u2net` or `bg_model: isnet-general-use` in the YAML defaults).
