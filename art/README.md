# Art pipeline

Pixel-art sprite pipeline for Idle Fantasy. See [`CLAUDE.md`](../CLAUDE.md) at the
repo root for the full context, decisions, and constraints.

## Quickstart — getting a sprite into the game

1. Generate (or refresh) the manifest of every entity that needs a sprite:

   ```
   python3 scripts/generate_art_manifest.py
   ```

   Writes `ART_MANIFEST.md` + `art_manifest.json` at the repo root.

2. Draw a sprite at 1× / mdpi base resolution (Aseprite, Pixly, etc.). Name the file
   exactly the entity id you want it to render for — e.g. `copper_ore.png` for
   the `copper_ore` entity. Snake-case alphanumerics only.

3. Export to all five Android densities at once:

   ```
   python3 -m art.pipeline.exporter path/to/copper_ore.png
   ```

   This writes `copper_ore.png` into each of
   `app/src/main/res/drawable-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/`, scaled with
   nearest-neighbor so the pixel art stays crisp.

4. Audit progress at any time:

   ```
   python3 scripts/audit_art.py                  # full report
   python3 scripts/audit_art.py --missing-only   # one id per line
   python3 scripts/audit_art.py --category Ores  # one category
   ```

5. Rebuild the app. The `EntityIcon(entityId = "copper_ore")` composable picks up
   the drawable automatically — no Kotlin changes.

## Layout

```
art/
├── README.md
└── pipeline/
    ├── core/
    │   └── palette.py     # mirror of TierBronze..TierDragon from Color.kt
    └── exporter.py        # 1× source → 5 density variants
```

The `pipeline/sprites/` source-art directory is **not** scaffolded yet — per
CLAUDE.md's "don't build infrastructure before there's content to push through
it". Add it the first time a hand-drawn source PNG needs a home.

## Style guarantees

- Pixel art only. `EntityIcon` in the app uses `FilterQuality.None` and
  `Image.NEAREST` is the resampler in `exporter.py`. Never substitute a smoother
  filter — pixel-art halftones look wrong with bilinear or bicubic.
- Palette stays in sync. `art/pipeline/core/palette.py` parses `Color.kt` on
  every run; if the Kotlin palette changes, update the Python mirror to match.
- Naming is law. The file's basename (without `.png`) **is** the
  `R.drawable.<id>` the app will resolve. If the audit reports an "orphan PNG",
  it's a typo.

## Until art lands

Every place that *will* render a sprite already renders a tier-colored
placeholder via the `EntityIcon` fallback. The app is fully functional with
zero sprites in `drawable-*dpi/`; each PNG that drops in fills one more slot.
