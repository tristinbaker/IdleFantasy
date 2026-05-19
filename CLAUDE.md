# CLAUDE.md — Idle Fantasy GUI & Game Overhaul

> Context for Claude Code working on [Idle Fantasy](https://github.com/tristinbaker/IdleFantasy). Read this first.

---

## Project context

**Idle Fantasy** is a FOSS (GPL-3.0) Android idle RPG by Tristin Baker, built in Kotlin + Jetpack Compose. Game data and mechanics are OSRS-inspired: tier ladder runs bronze → iron → steel → mithril → adamant → rune → dragon; skills include Mining, Fishing, Woodcutting, Smithing, Firemaking, Cooking, Fletching, Crafting, Runecrafting, Agility, Farming, Prayer, Herblore, plus the combat skills.

**This repo** is the active overhaul of the game — Kotlin systems (combat, UI, motion, data), the Python art pipeline under `art/`, and the JSON data under `app/src/main/assets/data/` are all in scope. The art pipeline is still **Python-first** and follows its own conventions (see below), but Kotlin gameplay code, balance, and data are no longer off-limits.

## Mission

Two parallel workstreams:

1. **Gameplay & systems.** Fix bugs, rebalance, redesign combat / progression / UI as needed. The game's mechanics are being actively overhauled, not preserved.
2. **Art pipeline.** Produce **345 static sprites** and **92 animated entities (221 animation clips total)** as enumerated in `ART_MANIFEST.md`. Outputs land in `app/src/main/res/drawable-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/` so Kotlin code can reference them via `R.drawable.<name>`.

## Intended repository layout

```
.
├── app/src/main/
│   ├── assets/data/                       # SOURCE OF TRUTH for every named entity (read-only from art's POV)
│   ├── kotlin/.../ui/theme/Color.kt       # SOURCE OF TRUTH for palette
│   ├── kotlin/.../ui/components/EntityIcon.kt  # Kotlin consumer — R.drawable.<id> lookup + fallback
│   └── res/drawable-*dpi/                 # Output target (do not hand-edit)
├── art/                                   # Art pipeline (Python)
│   ├── pipeline/
│   │   ├── core/
│   │   │   └── palette.py                 ✅ exists — mirror of Color.kt
│   │   ├── exporter.py                    ✅ exists — 1× → 5 density Pillow exporter
│   │   ├── generators/                    ⬜ not yet (parametric sprites, tier templating)
│   │   ├── sprites/                       ⬜ not yet (source SVGs / authored PNGs)
│   │   └── animations/
│   │       ├── avd/                       ⬜ not yet (AnimatedVectorDrawable XML emitters)
│   │       └── sheets/                    ⬜ not yet (sprite-sheet packer + JSON metadata)
│   ├── tests/                             ⬜ not yet (golden-image regression)
│   └── README.md                          ✅ exists — drag-and-drop workflow
├── scripts/
│   ├── generate_art_manifest.py           ✅ exists — walks data/*.json → manifest
│   └── audit_art.py                       ✅ exists — punch-list missing sprites
├── ART_MANIFEST.md                        ✅ exists — 687 ids across 17 categories
└── art_manifest.json                      ✅ exists — machine-readable manifest
```

## Decisions already made

- **Pipeline language:** Python 3.10+.
- **Output target:** Android multi-density drawables. Pipeline always exports all five densities (mdpi 1×, hdpi 1.5×, xhdpi 2×, xxhdpi 3×, xxxhdpi 4×).
- **Palette source:** single-sourced from `app/src/main/kotlin/com/fantasyidler/ui/theme/Color.kt`. `art/pipeline/core/palette.py` mirrors it. If `Color.kt` changes, update `palette.py` to match — do not fork the palette.
- **Naming convention:** `snake_case`, matching the entity IDs in the JSON data files (e.g., `R.drawable.weapon_iron_sword` ← `iron_sword` in `items.json`).
- **Three-tier animation strategy:**
  1. **AnimatedVectorDrawable (XML)** for UI flourishes (XP sparkle, level-up burst, coin pickup). Tiny files, vector-scaled.
  2. **Sprite sheets + Coil** for combat creatures and skill-action loops. Coil 2.7.0 is wired up in `app/build.gradle.kts`.
  3. **Compose property animation over static sprites** for free idle motion (bobs, glows, tier-glow halos). The `FantasyMotion` vocabulary in `app/src/main/kotlin/com/fantasyidler/ui/motion/FantasyMotion.kt` is the canonical home for these — pick a named spec (Snappy / Smooth / Bouncy / Idle), don't inline tweens.
- **Tier templating:** one silhouette × 7 tier palettes (locked in `Color.kt` as `TierBronze`/`TierIron`/`TierSteel`/`TierMithril`/`TierAdamant`/`TierRune`/`TierDragon`). The pipeline auto-generates all 7 variants from one source — about half the equipment sprite workload disappears this way.
- **Visual style: pixel art.** Confirmed. Locks in nearest-neighbor scaling everywhere (`Image.NEAREST` in `art/pipeline/exporter.py`, `FilterQuality.None` on `EntityIcon`). Source authoring targets the 1× / mdpi base resolution.
- **Kotlin consumer pattern:** every list/row that renders a named entity goes through `EntityIcon(entityId = "<snake_case_id>")`. It resolves to `R.drawable.<id>` at runtime via `Resources.getIdentifier` and falls back to a tier-colored placeholder when no PNG exists yet. This is why the manifest can land before any sprite does — the placeholders fill the slots until each PNG drops in.

## Open decisions — do NOT pre-commit

- **Drawing app:** TBD. Pixel-side candidates: Aseprite, Pixly, Pixel Studio, Resprite.
- **AI animation tool:** TBD. PixelLab is the leading candidate.

## Conventions

- Python 3.10+, type-hinted, formatted with `ruff format`.
- **Pillow** is the primary image library. `numpy` is available where pixel-level operations help.
- Every generator that emits drawables also writes a small JSON sidecar in the source folder describing the generation params, so outputs are deterministically regeneratable.
- Vector source assets (SVG) live in `art/pipeline/sprites/`. Raster outputs go to `app/src/main/res/drawable-*dpi/`. **Don't commit raster source files**; raster sources must be regeneratable from SVG or from human-authored PNGs in `art/pipeline/sprites/`.
- AnimatedVectorDrawable XML emits to `app/src/main/res/drawable/` (density-independent).
- Sprite sheets land in `app/src/main/res/drawable-nodpi/<entity>_sheet.png` plus a JSON metadata file at `app/src/main/assets/sprite_sheets/<entity>.json` defining frame timing.

## What NOT to do

- **Treat Kotlin and art as one codebase.** Both are in scope. For Kotlin changes, mind the cross-cutting impact: `EntityIcon` consumers, the data files under `assets/data/`, and the `FantasyMotion` vocabulary in `ui/motion/`. For art changes, the rules below (palette, naming, density outputs) still apply.
- **Don't invent entity names.** Every sprite must correspond to an entry in `ART_MANIFEST.md` / `art_manifest.json`. If a sprite is needed for something not in the manifest, update the manifest first, then create the sprite.
- **Don't hand-edit anything in `app/src/main/res/drawable-*dpi/`.** Those are pipeline outputs. Source lives in `art/pipeline/sprites/`.
- **Don't fork the palette.** `palette.py` mirrors `Color.kt`. Single source of truth.
- **Don't commit AI-generated frames raw.** All AI output passes through human cleanup in the drawing app before entering the pipeline.
- **Don't scaffold infrastructure before there's content to push through it.** Prefer shipping a vertical slice (one skill end-to-end) over building all infrastructure first.

## Pipeline interface (commands)

✅ = exists today and works · ⬜ = planned, not yet implemented

```bash
# ✅ Regenerate the manifest from the game's JSON data
python3 scripts/generate_art_manifest.py

# ✅ Punch-list what's still missing (supports --missing-only, --category <name>)
python3 scripts/audit_art.py

# ✅ Process a single source PNG (1× / mdpi base) → all 5 Android densities
#    Nearest-neighbor resampling. Pillow required (pip install Pillow).
python3 -m art.pipeline.exporter art/pipeline/sprites/items/copper_ore.png

# ✅ Sanity check that art/pipeline/core/palette.py is still in sync with Color.kt
python3 art/pipeline/core/palette.py

# ⬜ Watch mode: auto-export when source files change (drag-and-drop workflow)
python3 -m art.pipeline.watch art/pipeline/sprites/

# ⬜ Tier-templating: generate all 7 tier variants from one silhouette
python3 -m art.pipeline.generators.weapons --silhouette art/pipeline/sprites/weapons/sword.svg

# ⬜ Golden-image regression tests
pytest art/tests/
```

## Key files to know about

| Path | Purpose |
|---|---|
| `ART_MANIFEST.md` | Every sprite that needs to exist, grouped by category. 687 entity IDs across 17 categories. Regenerate via `scripts/generate_art_manifest.py`. |
| `art_manifest.json` | Machine-readable manifest. `scripts/audit_art.py` ingests this to print the missing-sprite punch list. |
| `app/src/main/assets/data/*.json` | Game data; canonical source for entity IDs, display names, stats, drop rates. The manifest is generated *from* these. |
| `app/src/main/assets/data/dungeons/*.json` | Dungeon definitions (banner art targets). |
| `app/src/main/assets/data/skills/*.json` | Skill definitions. |
| `app/src/main/kotlin/com/fantasyidler/ui/theme/Color.kt` | Palette source of truth — `art/pipeline/core/palette.py` mirrors and self-verifies against it. |
| `app/src/main/kotlin/com/fantasyidler/ui/components/EntityIcon.kt` | Kotlin consumer. Renders `R.drawable.<entityId>` with `FilterQuality.None`; tier-colored fallback when the drawable doesn't exist yet. Touch this file if the lookup semantics ever change. |
| `app/src/main/kotlin/com/fantasyidler/ui/motion/FantasyMotion.kt` | The four canonical animation specs (Snappy / Smooth / Bouncy / Idle) plus the four nav-transition lambdas. Pick a named spec instead of writing raw `tween(...)`. |
| `art/pipeline/exporter.py` | Single-asset 1× → 5 density exporter. Pillow + nearest-neighbor. |
| `art/pipeline/core/palette.py` | Tier palette mirror with `verify_in_sync()` that fails loudly if `Color.kt` drifts. |
| `art/README.md` | Drag-and-drop workflow for getting a sprite into the game. |
| `scripts/generate_wiki.py` | Existing Python script in the repo; useful precedent for tooling conventions. |
| `.github/workflows/release.yml` | Auto-builds and publishes a signed APK on every push to `main` (tag = `v<versionName>.<run_number>`), and on `v*` tag pushes. |
| `README.md` (repo root) | Game-level documentation. |

## Working style notes

- **Vertical slices over horizontal infrastructure.** Mining is the planned first slice: 10 ores + 7 tier pickaxes + mining action animation loop. Prove the pipeline end-to-end before building generators for everything.
- **Tier templating is a force multiplier.** 36 weapons + 24 armor + 18 tools = 78 items, but real unique-silhouette count is closer to ~25 once you account for tier palette-swapping. Build for it from day one.
- **Mobile-friendly responses.** The author primarily reviews work on a mobile device. Favor concise summaries with file outputs over long inline content.
- **Style is pixel art.** Already chosen — no need to ask each task whether to assume pixel or painted.

## Current state at last update

**Infrastructure landed (PRs 4-9 of the GUI overhaul + the art manifest PR):**

- `ART_MANIFEST.md` + `art_manifest.json` generated from the JSON data files. 687 IDs, 17 categories. Counts include all derived per-tier entries from `equipment.json` and per-skill recipes.
- `art/` subtree scaffolded with the minimum-viable surface: `pipeline/core/palette.py` (palette mirror with sync check), `pipeline/exporter.py` (1× → 5 density Pillow exporter), `README.md` (workflow). No `sprites/`, `generators/`, or `animations/` subdirs yet — per the "don't scaffold infrastructure before there's content" rule.
- All five density drawable directories exist (`app/src/main/res/drawable-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/`), each tracked via `.gitkeep`. Empty of sprites — the placeholder fallback fills the slots until PNGs land.
- `EntityIcon` Kotlin primitive ready: every `ItemTile` / `DungeonCard` / `RecipeCard` in the app already consumes it. Drop a PNG named `<entityId>.png` into a density folder and it appears in-game with no Kotlin changes.
- Coil 2.7.0 wired (`io.coil-kt:coil-compose` in `app/build.gradle.kts`) — unused on `EntityIcon`'s static path but plumbed for the eventual sprite-sheet animation work.
- `scripts/audit_art.py` reports drawing progress. Today: **0/687 sprites landed** — all entities render via the tier-colored placeholder fallback.
- `.github/workflows/release.yml` auto-builds + signs + publishes a downloadable APK to the Releases page on every push to `main`. Use this as the build gate when the local environment can't reach Google Maven.

**Game's existing art assets (untouched):**

- `app/src/main/res/drawable/ic_launcher_foreground.xml` (generic gold sword launcher icon). The only pre-existing art asset; the placeholder fallback covers everything else for now.

**Next sensible step:** pick the drawing app (Aseprite / Pixly / Pixel Studio / Resprite) and ship the first vertical slice — Mining's 10 ores + 7 tier pickaxes — into `drawable-xhdpi/` (or just `drawable-xxhdpi/` if you want to start with the most common density). The audit script will track progress.

**Gameplay overhaul (now in scope):** combat balance, progression curves, UI feedback, and data tuning under `app/src/main/assets/data/` are all live work. The combat simulator lives at `app/src/main/kotlin/com/fantasyidler/simulator/CombatSimulator.kt`; the per-swing damage formula is `private fun maxHit(skillLevel, strengthBonus)` and is the canonical place to retune attacker output. Equipment bonus is intentionally weighted higher than skill level so gear progression feels meaningful from level 1.
