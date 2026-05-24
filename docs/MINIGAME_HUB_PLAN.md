# Minigame Hub — Market Center Plan

Locked plan for the Minigame Hub overhaul. Replaces the placeholder
`MinigameHubScreen` (and its old "Time Points / Time Vouchers,
bartender/kitchen/courier" TODO) with a walk-around medieval market
center where four crafting stations + a vendor stall offer skill-themed
minigames.

## Concept

A single full-screen pixel-art plaza. The player drives a generic
adventurer avatar with a virtual joystick (bottom-left). Walking
into a station's interact radius surfaces an "Enter" prompt; tapping
launches that station's minigame.

Five interactables in the plaza:

- **Smithy** — Hammer & Quench
- **Runic outcrop** — Stone Cracker
- **Herbalist cart / cauldron** — Cauldron Mixer
- **Cook fire** — Egg on the Fire
- **Vendor stall** — opens the existing `ShopScreen` in-place

## Reward model — locked

**Minigames subtract time from the active idle session.** Every second
of minigame play converts to a `timeSkipMs` credit that
`SessionRepository.fastForward(skill, ms)` applies to
`SkillSession.endsAt`.

- Base rate: per-second skip while the minigame is being played.
- **Relevance multiplier**: skip rate is multiplied (target 2×) when
  the station's skill matches the currently running session's skill.
  Smithing minigame during a smithing session = double speed.
- **No caps, no cooldowns.** Free-running active mode.
- **Fallback** when no session is active: the minigame still runs, but
  since there's nothing to fast-forward, the player gets the matching
  recipe's standard XP+item reward instead (so the action isn't
  wasted). UI shows a soft "no active session" hint.
- **Egg cooking and similar visuals are flavor only.** The egg is
  not a new recipe entry — it's just a visually instant-readable prop
  for the cooking minigame. Same logic for the other three: the
  minigame visuals are themed; the reward is the session time-skip.

## Locked open decisions

1. **Rewards**: free-running, uncapped.
2. **Vendor stall**: yes — 5th interactable in the plaza, reuses
   `ShopScreen` opened in-place (no nav away from the hub).
3. **Avatar**: **generic adventurer sprite for v1.** Character-specific
   gender×race sprites deferred. Avoids a 100+ sprite player budget on
   the first pass.
4. **Egg data**: no `egg` entry needed in `cooking.json` or
   `items.json`. Flavor only.

## Build order (vertical-slice first)

### Phase 2 — Assets & manifest (do first)

The "populate the market center" step.

- New `app/src/main/assets/data/minigame_hub.json` declaring every
  market-center entity: stations, decor, player frames, joystick
  parts, per-minigame props with all their visual states, vendor +
  vendor NPC.
- Extend `scripts/generate_art_manifest.py` to recognise
  `minigame_hub.json` and emit a new `minigame_hub` category in
  `ART_MANIFEST.md` + `art_manifest.json`. `scripts/audit_art.py`
  then tracks market-center progress automatically.
- Source PNGs drop into
  `art/pipeline/sprites/minigame_hub/{background,player,joystick,stations,props}/`.
  Existing `art/pipeline/exporter.py` fans them to 5 densities.
  `EntityIcon` already handles the snake_case lookup + tier-colored
  fallback, so the world renders with placeholders until sprites land.
- Rough asset budget (refined when JSON lands):
  - 1 plaza background, 5 station structures (smithy, runic
    outcrop, herbalist cart, cook fire, vendor stall), ~6 decor
    pieces (barrels, crates, lanterns, banner, cart, well).
  - Player: ~13 frames (4 dirs × 3 walk frames + idle).
  - Joystick: base + knob.
  - Per-minigame props: smithing 6, runecrafting 5, herblore 6,
    cooking 5.
  - Vendor NPC: idle frame.
  - ~50 new entity IDs total. Real count set by the JSON.

### Phase 1 — World

After Phase 2's manifest entries exist so the world has real IDs to
render (with fallbacks for any missing sprites).

- `MarketCenterStage` composable inside the refactored
  `MinigameHubScreen` — full-screen pixel scene, no scrolling at
  base resolution.
- New `VirtualJoystick` component in `ui/components/` — fixed
  bottom-left, emits a normalized `Offset` flow.
- New `WalkingAvatar` — consumes joystick offset, integrates position
  per-frame, clamps to plaza bounds, picks facing sprite, switches
  frames at a slow FPS (pixel feel, not smooth interpolation).
- 4-direction top-down walk. Sprite naming:
  `player_walk_<dir>_<frame>` (generic, no gender/race in v1).
- Per-frame proximity check: when the avatar enters a station's
  interact radius, an "Enter" prompt fades in using
  `FantasyMotion.Snappy`; tap → launch that station's minigame screen
  (or open the shop sheet for the vendor).

### Phase 3 — Smithing vertical slice

Proves the whole minigame architecture before fanning out.

- Three-phase loop: HAMMER (tap-the-anvil rhythm, N random hits,
  good/perfect indicator) → HEAT (tap-and-hold gauge, release inside
  target band) → QUENCH (timed tap on the water bucket).
- Each successful interaction accumulates `timeSkipMs`. Perfect hits
  weight more than good hits.
- On exit, call
  `SessionRepository.fastForward(skill = Smithing, ms = total × relevance)`.
- Scene effects (anvil shake, sparks, fire flicker, quench steam)
  use the existing `scene/Effect.kt` vocabulary — same system that
  powers `SceneCatalog.MINING`. Each station gets its own
  `SceneConfig` entry.

### Phase 4 — Remaining stations in parallel

- **Runecrafting — Stone Cracker.** Tap the stone; each tap advances a
  crack overlay; on burst, a rune fragment pops. `timeSkipMs` accrues
  per tap.
- **Herblore — Cauldron Mixer.** Drag ingredients in, then a stirring
  puzzle: alternating CW/CCW arcs in a randomized sequence (e.g.,
  `3CW, 2CCW, 1CW`), tracked by a radial gesture detector. Wrong
  sequence → less `timeSkipMs` accrued, not a hard fail (idle-game
  forgiveness). Note: current `herblore.json` uses
  `potato`/`cabbage` as inputs — themed cosmetically as "herbs &
  vegetables" without changing data.
- **Cooking — Egg on the Fire.** Timing meter rises; tap FLIP at the
  sweet spot, then REMOVE before it burns. Outcomes
  (perfect/decent/burned) scale `timeSkipMs` earned. Burned still
  yields a small amount (no hard failure).
- **Vendor stall.** Tap → open `ShopScreen` as an in-place sheet.

### Phase 5 — State, persistence, polish

- One ViewModel per minigame.
  `MarketCenterViewModel` owns joystick state + nearby-station state.
- `SessionRepository.fastForward(skill, ms)` is the single
  integration point. Clamps to `endsAt - now`, persists, emits the
  updated session so the HUD pill ticks down live.
- Lifetime counters per station saved to `PlayerFlags` for future
  achievements (no UI yet).
- Strings: rewrite `minigame_hub_*` keys, add per-station and
  per-minigame strings in `values/strings.xml`.
- Motion: all timed animation picks from `FantasyMotion`
  (`Snappy` for taps, `Bouncy` for reward landings, `Idle` for
  cauldron bubbles / fire flicker).
- First-time tooltip for the joystick.

### Out of scope for v1

- SFX, haptics.
- Character-specific avatar sprites (gender×race matrix). Deferred
  until v1 is proven.
- New NPC vendors beyond the one stall.
- Camera scrolling (single-screen plaza only).

## Files & systems touched

| Area | Path |
|---|---|
| Hub screen (refactor) | `app/src/main/kotlin/com/fantasyidler/ui/screen/MinigameHubScreen.kt` |
| New: joystick | `app/src/main/kotlin/com/fantasyidler/ui/components/VirtualJoystick.kt` |
| New: walking avatar | `app/src/main/kotlin/com/fantasyidler/ui/components/WalkingAvatar.kt` |
| New: market stage | `app/src/main/kotlin/com/fantasyidler/ui/screen/minigame/MarketCenterStage.kt` |
| New: per-minigame screens | `app/src/main/kotlin/com/fantasyidler/ui/screen/minigame/{Smithing,Runecrafting,Herblore,Cooking}MinigameScreen.kt` |
| New: ViewModels | `app/src/main/kotlin/com/fantasyidler/ui/viewmodel/MarketCenter*ViewModel.kt` |
| Scene configs | `app/src/main/kotlin/com/fantasyidler/ui/scene/SceneCatalog.kt` |
| Session fast-forward | `app/src/main/kotlin/com/fantasyidler/repository/SessionRepository.kt` |
| New asset declarations | `app/src/main/assets/data/minigame_hub.json` |
| Manifest script | `scripts/generate_art_manifest.py` |
| Source sprites | `art/pipeline/sprites/minigame_hub/...` |
| Generated drawables | `app/src/main/res/drawable-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/` |
| Strings | `app/src/main/res/values/strings.xml` |
| Nav (already wired) | `app/src/main/kotlin/com/fantasyidler/ui/navigation/AppNavigation.kt` |

## Existing scaffolding to lean on

- `Screen.MinigameHub` route already wired under `Adventure`.
- `EntityIcon` resolves `R.drawable.<id>` with a tier-colored
  fallback when the PNG doesn't exist yet, so the world renders
  before any sprite lands.
- `scene/Effect.kt` + `SceneCatalog` declarative scene system
  already powers Mining; reuse pattern for each minigame.
- `FantasyMotion` vocabulary (Snappy / Smooth / Bouncy / Idle) is
  the canonical animation home.
- `scripts/audit_art.py` already tracks per-category progress —
  will surface market-center progress automatically once the manifest
  script knows about `minigame_hub.json`.
