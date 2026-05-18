# CLAUDE.md — Idle Fantasy Art Pipeline

> Context for Claude Code working on the art side of [Idle Fantasy](https://github.com/tristinbaker/IdleFantasy). Read this first.

---

## Project context

**Idle Fantasy** is a FOSS (GPL-3.0) Android idle RPG by Tristin Baker, built in Kotlin + Jetpack Compose. Game data and mechanics are OSRS-inspired: tier ladder runs bronze → iron → steel → mithril → adamant → rune → dragon; skills include Mining, Fishing, Woodcutting, Smithing, Firemaking, Cooking, Fletching, Crafting, Runecrafting, Agility, Farming, Prayer, Herblore, plus the combat skills.

**This work** (the `art/` subtree) is the art pipeline that produces sprites and animations for the game. Built as a side project alongside the main repo. The pipeline is **Python-first** (project author's language of preference; also precedent in the existing `scripts/generate_wiki.py`). Sprites are hand-drawn by the author; AI is used only to extend static keyframes into animation in-between frames, with human cleanup.

## Mission

Produce **345 static sprites** and **92 animated entities (221 animation clips total)** as enumerated in `ART_MANIFEST.md`. Outputs land in `app/src/main/res/drawable-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/` so existing Kotlin code can reference them via `R.drawable.<name>` with zero code changes.

## Intended repository layout

```
.
├── app/src/main/
│   ├── assets/data/                       # SOURCE OF TRUTH for every named entity (read-only from art's POV)
│   ├── kotlin/.../ui/theme/Color.kt       # SOURCE OF TRUTH for palette
│   └── res/drawable-*dpi/                 # Output target (do not hand-edit)
├── art/                                   # Art pipeline (Python) — NOT YET SCAFFOLDED
│   ├── pipeline/
│   │   ├── core/                          # palette.py, canvas.py, exporter.py
│   │   ├── generators/                    # parametric sprites (tier-template weapons, ores, bars…)
│   │   ├── sprites/                       # hand-authored source SVGs/PNGs
│   │   └── animations/
│   │       ├── avd/                       # AnimatedVectorDrawable XML emitters
│   │       └── sheets/                    # sprite-sheet packer + JSON metadata
│   ├── tests/                             # golden-image regression
│   └── README.md
├── ART_MANIFEST.md                        # complete enumeration of every needed sprite
└── art_manifest.json                      # machine-readable version of the manifest
```

The `art/` subtree does **not** exist yet at the time of writing — it will be scaffolded as part of the first work session.

## Decisions already made

- **Pipeline language:** Python 3.10+.
- **Output target:** Android multi-density drawables. Pipeline always exports all five densities (mdpi 1×, hdpi 1.5×, xhdpi 2×, xxhdpi 3×, xxxhdpi 4×).
- **Palette source:** single-sourced from `app/src/main/kotlin/com/fantasyidler/ui/theme/Color.kt`. `art/pipeline/core/palette.py` mirrors it. If `Color.kt` changes, update `palette.py` to match — do not fork the palette.
- **Naming convention:** `snake_case`, matching the entity IDs in the JSON data files (e.g., `R.drawable.weapon_iron_sword` ← `iron_sword` in `items.json`).
- **Three-tier animation strategy:**
  1. **AnimatedVectorDrawable (XML)** for UI flourishes (XP sparkle, level-up burst, coin pickup). Tiny files, vector-scaled.
  2. **Sprite sheets + Coil** for combat creatures and skill-action loops.
  3. **Compose property animation over static sprites** for free idle motion (bobs, glows, tier-glow halos).
- **Tier templating:** one silhouette × 7 tier palettes (already locked in `Color.kt` as `TierBronze`/`TierIron`/`TierSteel`/`TierMithril`/`TierAdamant`/`TierRune`/`TierDragon`). The pipeline auto-generates all 7 variants from one source — about half the equipment sprite workload disappears this way.

## Open decisions — do NOT pre-commit

- **Visual style: pixel art vs painted/illustrated.** Not chosen yet. Affects canvas dimensions, generator design, AI tool choice. Do not assume one or the other; ask if the answer matters for the task at hand.
- **Drawing app:** TBD. Candidates: Aseprite, Pixly, Pixel Studio, Resprite (pixel side); Clip Studio Paint, Krita, Procreate (painted side).
- **AI animation tool:** TBD. PixelLab is the leading candidate if pixel; less settled if painted.

## Conventions

- Python 3.10+, type-hinted, formatted with `ruff format`.
- **Pillow** is the primary image library. `numpy` is available where pixel-level operations help.
- Every generator that emits drawables also writes a small JSON sidecar in the source folder describing the generation params, so outputs are deterministically regeneratable.
- Vector source assets (SVG) live in `art/pipeline/sprites/`. Raster outputs go to `app/src/main/res/drawable-*dpi/`. **Don't commit raster source files**; raster sources must be regeneratable from SVG or from human-authored PNGs in `art/pipeline/sprites/`.
- AnimatedVectorDrawable XML emits to `app/src/main/res/drawable/` (density-independent).
- Sprite sheets land in `app/src/main/res/drawable-nodpi/<entity>_sheet.png` plus a JSON metadata file at `app/src/main/assets/sprite_sheets/<entity>.json` defining frame timing.

## What NOT to do

- **Don't modify Kotlin code** unless explicitly asked. The Kotlin app reads drawables by ID; new art just needs to land in the right folder with the right name. The Kotlin side is the game author's domain, not the art pipeline's.
- **Don't invent entity names.** Every sprite must correspond to an entry in `ART_MANIFEST.md` / `art_manifest.json`. If a sprite is needed for something not in the manifest, update the manifest first, then create the sprite.
- **Don't hand-edit anything in `app/src/main/res/drawable-*dpi/`.** Those are pipeline outputs. Source lives in `art/pipeline/sprites/`.
- **Don't fork the palette.** `palette.py` mirrors `Color.kt`. Single source of truth.
- **Don't commit AI-generated frames raw.** All AI output passes through human cleanup in the drawing app before entering the pipeline.
- **Don't scaffold infrastructure before there's content to push through it.** Prefer shipping a vertical slice (one skill end-to-end) over building all infrastructure first.

## Intended pipeline interface (commands)

These don't exist yet — they describe the target CLI surface.

```bash
# One-time setup
cd art && python -m venv .venv && source .venv/bin/activate && pip install -e .

# Process a single source asset → all 5 Android densities
python -m art.pipeline.exporter art/pipeline/sprites/items/copper_ore.svg

# Watch mode: auto-export when source files change (drag-and-drop workflow)
python -m art.pipeline.watch art/pipeline/sprites/

# Audit: verify every manifest entry has a corresponding output
python -m art.pipeline.audit

# Tier-templating: generate all 7 tier variants from one silhouette
python -m art.pipeline.generators.weapons --silhouette art/pipeline/sprites/weapons/sword.svg

# Golden-image regression tests
pytest art/tests/
```

## Key files to know about

| Path | Purpose |
|---|---|
| `ART_MANIFEST.md` | Every sprite that needs to exist, categorized static vs animated. Human-readable. |
| `art_manifest.json` | Machine-readable manifest. Ingest this when generating asset stubs or running audits. |
| `app/src/main/assets/data/*.json` | Game data; canonical source for entity IDs, display names, stats, drop rates. |
| `app/src/main/assets/data/dungeons/*.json` | Dungeon definitions (banner art targets). |
| `app/src/main/assets/data/skills/*.json` | Skill definitions. |
| `app/src/main/kotlin/com/fantasyidler/ui/theme/Color.kt` | Palette source of truth — mirror to `palette.py`. |
| `scripts/generate_wiki.py` | Existing Python script in the repo; useful precedent for tooling conventions. |
| `README.md` (repo root) | Game-level documentation. |

## Working style notes

- **Vertical slices over horizontal infrastructure.** Mining is the planned first slice: 10 ores + 7 tier pickaxes + mining action animation loop. Prove the pipeline end-to-end before building generators for everything.
- **Tier templating is a force multiplier.** 36 weapons + 24 armor + 18 tools = 78 items, but real unique-silhouette count is closer to ~25 once you account for tier palette-swapping. Build for it from day one.
- **Mobile-friendly responses.** The author primarily reviews work on a mobile device. Favor concise summaries with file outputs over long inline content.
- **No premature commitment to style choices.** When unsure whether a task is for pixel art or painted, ask. Both paths are still live.

## Current state at last update

- `ART_MANIFEST.md` and `art_manifest.json` exist at repo root. Generated from the game's JSON data files.
- `art/` subtree does not yet exist. Awaiting style decision and drawing app pick before scaffolding the first generator and exporter.
- The game's existing `app/src/main/res/drawable/` contains exactly one placeholder asset: `ic_launcher_foreground.xml` (a generic gold sword). All other art is yet to be created.
- No density-specific drawable folders (`drawable-mdpi/`, etc.) exist yet — pipeline will create them on first export.
