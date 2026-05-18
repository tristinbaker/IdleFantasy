# Idle Fantasy — GUI Redesign Proposal

> **Status:** Approved execution roadmap. PRs 1–2 have landed (see §5 status table); PRs 3–10 are pending the §6 open questions.
> **Audience:** Tristin (game author) reviewing on mobile.
> **Working branch:** `claude/revise-implementation-plan-eKQ1T`
> **Original proposal branch:** `claude/redesign-casual-ui-IMf9a` (merged in PR #1)
> **Scope decision:** "Full redesign" — restructure the nav, add a persistent HUD, decompose monolithic screens, introduce a shared component layer.

---

## TL;DR

1. **Add a persistent top HUD** (coins + active-session pill + level chip) so the player never loses context when switching tabs.
2. **Re-cast the bottom nav** to *Skills · Combat · Adventure · Crafting · Profile* — promotes Crafting out of the Skills screen, replaces the dashboard-style Home with an "Adventure" hub, and stops hiding Settings/Shop in the back-stack.
3. **Break up the giant screens** (Skills is 1,734 lines, Combat is 1,497) into focused files plus a new `ui/components/` shared widget library.

Everything else flows from those three moves.

---

## 1. The problem in one screen

> "I get kinda lost sometimes on it."

After auditing the current GUI, the disorientation has clear causes:

| Symptom | Root cause |
|---|---|
| "Where do I craft?" | Crafting has no bottom-tab entry. It lives buried inside `SkillsScreen.kt` (1,734 lines). |
| "Where's the Shop?" | It's a back-stack child of the **Home** tab. Nobody looks for a shop under "Home". |
| "Where are Settings?" | Same problem — under Home. |
| "What am I doing right now?" | Active-session info is only on Home & Profile. The moment you leave those tabs, the timer disappears. |
| "How much gold do I have?" | Coins show on Home & Profile only. Not visible while picking a dungeon or starting a skill. |
| Screen-to-screen feel inconsistent | Every screen ships its own `TopAppBar`. There's no unified chrome. |
| Slow to scroll on big screens | Skills, Combat, Profile each pack many sections into one monolithic Composable. |

None of these are content problems — the game has the right *features*. They're **information-architecture** problems.

---

## 2. Audit — what exists today

### Bottom navigation (5 tabs)

```
[ Skills ] [ Combat ] [ ⭐ Home ] [ Quests ] [ Profile ]
```

- **Home** is `startDestination`. It's a dashboard: greeting, stats card, shop teaser, active-session card, queue.
- **Sub-screens** that exist but have no tab: `Shop` (under Home), `Settings` (under Home), `Farming` (under Skills), `Crafting` (only reachable by tapping a row inside Skills — not even routed).

### Screen sizes (lines of Kotlin in a single file)

| Screen | Lines | What's inside |
|---|---|---|
| `SkillsScreen.kt` | **1,734** | All gathering + crafting skill cards, category headers, active-session banner, efficiency hints, navigation to Farming |
| `CombatScreen.kt` | **1,497** | Dual-tab UI: combat log vs dungeon/boss picker, survival ratings, loadout, top app bar |
| `ProfileScreen.kt` | **863** | Identity header, coins banner, inventory tab, equipment tab, achievements tab, multiple bottom sheets |
| `HomeScreen.kt` | **547** | Dashboard cards |
| `ShopScreen.kt` | **471** | Item list, buy/sell sheets |
| `SettingsScreen.kt` | **426** | Preferences |
| `FarmingScreen.kt` | **434** | Patch-based farming UI |
| `QuestsScreen.kt` | **267** | Active / completed quest tabs |

Total of ~6,200 lines across 8 screens, with **zero** shared component files. Every `Card`, `Row`, `Sheet` and `Badge` is redefined inline. There's a `Color.kt` palette and `Type.kt` typography, but no `Shape.kt` and no `ui/components/` folder.

### What works well (don't touch)

- The MD3 dark/light/system theme switch is clean.
- The fantasy palette (gold-on-parchment) reads as a real RPG, not a clone of Material defaults.
- The tier palette is locked and reusable (`TierBronze` → `TierDragon`).
- `strings.xml` is already populated (354 strings), so a re-layout doesn't need a translation pass.
- `OnboardingScreen` exists and works as a pager — we'll extend it, not replace it.

---

## 3. Casual-game GUI principles applied here

Three principles dominate the casual / idle RPG genre. Sources at the bottom.

### A. 3–5 bottom tabs, never more — each is a *daily-driver loop*

> "The sweet spot is 3 to 5 main destinations. Each tab should map to a thing the player does every session, not a feature dump." — [UX Planet](https://uxplanet.org/bottom-tab-bar-navigation-design-best-practices-48d46a3b0c36)

Current count (5) is correct. The mix is wrong: Home eats a slot for things that belong in a persistent HUD, while Crafting (which the player does every session) has no slot at all.

### B. Persistent currency / status HUD

> "Currency indicates the player's in-game wealth and is a fundamental element of mobile game HUDs… HUD elements should be visible without obstructing the play area." — [Polydin: Game HUD Design](https://polydin.com/game-hud-design/)

Every successful idle/casual RPG — *AdVenture Capitalist*, *Egg Inc.*, *Melvor Idle*, *Idle Heroes* — keeps the player's wallet and active production visible at all times. Idle Fantasy currently does not. This is the single biggest fix.

### C. Hierarchy over density

> The reason a [Clean UI mod for Melvor Idle](https://mod.io/g/melvoridle/m/clean-ui) exists is that even the genre leader over-packs information. The community-built fix isn't *more* panels — it's grouping, hierarchy, and trimming chrome.

Implication for Idle Fantasy: don't add new widgets. Move existing ones into a hierarchy, and split monolithic screens so each one does one thing well.

---

## 4. Recommendation — five concrete changes

### 4.1 Persistent top HUD

A 56dp top strip in the **root** `Scaffold`. Survives every tab switch. Three regions:

```
┌──────────────────────────────────────────────────────────┐
│  Lv 87 ⚔        🪙 12,430   💎 4    ⏱ Mining · 23m ▸     │
└──────────────────────────────────────────────────────────┘
```

- **Left — Level chip.** Combat level + sword glyph. Tap → Profile.
- **Middle — Currency.** Coins always; gems if/when the concept is added. Tapping coins opens the **Shop bottom-sheet** (Shop stops being a routed screen).
- **Right — Active-session pill.** Skill icon + countdown. Tap expands a sheet showing progress, est. rewards, and a Claim/Cancel button. When no session is running, the pill collapses to a faint "Start a session ▸" prompt that deep-links to Skills.

Why this is the most important change: it solves three of the seven audit symptoms at once (coins location, session visibility, Shop discoverability).

### 4.2 Bottom nav restructure

```
Before:  [ Skills ] [ Combat ] [ ⭐ Home ] [ Quests ] [ Profile ]
After:   [ Skills ] [ Combat ] [ 🗺 Adventure ] [ ⚒ Crafting ] [ Profile ]
```

| Slot | Now | Proposed | Why |
|---|---|---|---|
| 1 | Skills | **Skills** | Keep — daily driver. Gathering + farming + agility live here. |
| 2 | Combat | **Combat** | Keep — daily driver. Dungeons + bosses + loadout. |
| 3 | Home | **Adventure** | New hub: quests, recommended dungeon, daily tasks, "what's next". The dashboard parts of Home dissolve into the HUD. |
| 4 | Quests | **Crafting** | Promotes the 4 crafting skills (Smithing / Cooking / Fletching / Crafting / Herblore / Runecrafting) out of `SkillsScreen`. |
| 5 | Profile | **Profile** | Inventory, equipment, achievements, pets, **Settings** (gear icon top-right of this screen). |

Quests merge into **Adventure** as the top section, where they already belong narratively. Farming stays a sub-screen of Skills. Onboarding/character-setup unchanged.

### 4.3 The new Adventure hub

Single scrolling column. Tells the player *what to do next* instead of *what they already have*:

```
┌─────────────────────────────────────┐
│  🗺  Adventure                       │
│                                     │
│  ▸ Continue your quest               │
│   ┌─────────────────────────────┐   │
│   │ Slay 5 Greater Goblins      │   │
│   │ ███████████░░░░  3 / 5      │   │
│   └─────────────────────────────┘   │
│                                     │
│  ▸ Recommended dungeon               │
│   ┌─────────────────────────────┐   │
│   │ 🕷 Goblin Cave · Lv 30      │   │
│   │ Survival: 76%   [ Enter ▸ ] │   │
│   └─────────────────────────────┘   │
│                                     │
│  ▸ Daily quests          [ 3 ▸ ]   │
│  ▸ Achievements          [ 12 ✨ ] │
│  ▸ Marketplace highlight [ New ✨ ] │
└─────────────────────────────────────┘
```

Three goals:

1. **One-tap re-entry** after a break ("what was I doing?").
2. **Surface neglected systems** (achievements, daily tasks) without giving each its own tab.
3. **Funnel into Combat** with a recommendation that respects current gear/level.

### 4.4 Screen decomposition

| Today | Tomorrow |
|---|---|
| `SkillsScreen.kt` (1,734) | `SkillsScreen.kt` (~300) + `SkillCategorySection.kt` + `SkillRow.kt` + `SessionStartSheet.kt` |
| `CombatScreen.kt` (1,497) | `CombatScreen.kt` (~250) + `DungeonList.kt` + `CombatLogPanel.kt` + `LoadoutBar.kt` + `BossPickerSheet.kt` |
| `ProfileScreen.kt` (863) | `ProfileScreen.kt` (~200) + `InventoryTab.kt` + `EquipmentTab.kt` + `AchievementsTab.kt` + `PetsSection.kt` |
| `HomeScreen.kt` (547) | **Deleted.** Replaced by `AdventureScreen.kt` (~250). |

New folder: **`app/src/main/kotlin/com/fantasyidler/ui/components/`** for shared widgets:

- `CoinsBadge` · `ActiveSessionPill` · `LevelChip` (the HUD trio)
- `TierChip` (tinted by `TierBronze`…`TierDragon`)
- `XpBar` · `SectionHeader` · `PrimaryButton` · `SecondaryButton`
- `ItemTile` · `RecipeCard` · `DungeonCard`
- `EmptyState` · `ConfirmDialog`

Each of these is currently inlined and re-implemented per screen. Extracting them collapses ~30–40% of duplicated code and makes future art-pipeline integration (drawables) a one-place change.

### 4.5 Unified root Scaffold

```kotlin
// MainActivity → FantasyIdlerApp → Scaffold {
//     topBar    = FantasyTopHud(...)        // persistent, 56dp
//     bottomBar = FantasyBottomNav(...)     // 5 tabs
//     content   = AppNavHost(...)           // each screen renders only its body
// }
```

Per-screen `TopAppBar`s removed. Screen titles pass up to the HUD through either a `LocalScreenTitle` composition local or the current `NavBackStackEntry.destination.label`. Sub-screens (Farming, individual skill detail) use a slim **back-pill** in the top-left of their content area, not a full TopAppBar.

### 4.6 Light theme additions (small, safe)

- Add `ui/theme/Shape.kt` exposing `small=8dp`, `medium=14dp`, `large=20dp` and pipe it into `MaterialTheme(shapes = ...)`. Standardises card radii.
- No palette changes. The existing gold/parchment scheme is good.
- Optional: pick a `FontFamily` for `displayLarge` (currently `FontFamily.Default`) — a single fantasy-feeling display face would lift the brand without touching body text legibility. Defer until after the structural pass.

### 4.7 Onboarding nudge

Add one extra page to `OnboardingScreen` titled "Find your way around". Three callouts:

1. The **HUD pill** — "tap here any time to check your timer or claim".
2. The **bottom nav** — five tabs, what each one is for.
3. The **gear icon** on Profile — "all your settings live here".

Three sentences total. Helps lapsed players too if you give them a "Show again" toggle in Settings (which already exists per the audit — `tutorial reopener` in `SettingsScreen`).

---

## 5. Suggested implementation order

Each row = one PR. Each PR is small enough to ship in a session.

Status reflects what has merged into the working branch; see git log for commits.

| # | PR | Status | Risk | What it touches |
|---|---|---|---|---|
| 1 | Extract `ui/components/` (no behaviour change) | ✅ Done | Low | Pure refactor of inlined widgets into shared files |
| 2 | Add `Shape.kt` + wire into theme | ✅ Done | Low | `ui/theme/` only |
| 3 | Build persistent top HUD in root Scaffold | ⬜ Pending | Medium | `MainActivity`, `FantasyIdlerApp`, new `FantasyTopHud` |
| 4 | Move Settings to Profile gear icon, dismantle Settings sub-route | ⬜ Pending | Low | `Screen.kt`, `AppNavigation.kt` |
| 5 | Open Shop as a bottom sheet from the HUD coin tap | ⬜ Pending | Medium | `Screen.kt`, new `ShopSheet` from existing `ShopScreen` content |
| 6 | Build `AdventureScreen` + delete `HomeScreen` | ⬜ Pending | Medium | New file, nav graph re-pointed, Home dashboard pieces moved to HUD |
| 7 | Bottom nav re-cast (Crafting promoted, Adventure replaces Home) | ⬜ Pending | Medium | `Screen.kt` items list + label strings |
| 8 | Split `SkillsScreen.kt` (gathering vs crafting handlers in different tabs) | ⬜ Pending | High | Largest single file in the repo |
| 9 | Split `CombatScreen.kt` and `ProfileScreen.kt` | ⬜ Pending | Medium | Per-file refactor, no behaviour change |
| 10 | Onboarding nudge page | ⬜ Pending | Low | One new pager page |

Ship 1–7 first — that's where the "I get lost" feeling lives. 8–10 are quality-of-life refactors.

---

## 6. Open questions for you

Things to decide before implementation. These remain open — resolution required before PRs 3, 5, 6, 7.

1. **Gems.** Does the game intend to have a premium-ish second currency (gems / cores / shards) or is it gold-only? The HUD has room for one slot today and could hold two — easier to design it for two and hide the second if unused.
2. **"Adventure" naming.** Tonally OK? Alternatives I considered: *Journey*, *Quests*, *Hub*, *World*, *Map*. "Adventure" reads broadest.
3. **Shop as sheet vs full screen.** I lean *sheet* (faster, doesn't break flow), but if you want to add more merchants later (one per town) a full screen with sub-tabs scales better.
4. **Settings on Profile.** Gear icon top-right is the universal pattern. Some players hunt for Settings as its own bottom tab — would you want a "More" tab instead, or trust the gear-icon convention?
5. **Combat bottom bar.** When the player is mid-dungeon, the bottom nav still shows — should we hide it during an active fight to focus attention, or leave it visible so they can pop out?
6. **Art pipeline alignment.** Once the art pipeline (see `CLAUDE.md`) starts producing drawables, do you want me to retrofit `ItemTile` / `DungeonCard` to consume `R.drawable.*` IDs from day one, or hold off until at least one sprite set lands?

---

## 7. What's deliberately *not* in this proposal

- **No palette change.** Gold/parchment is good.
- **No fonts swap.** Defer to after structural work — too easy to make legibility regressions while the bigger fish are still moving.
- **No animation work.** Belongs to the art pipeline, not the GUI restructure.
- **No data model change.** Items, skills, dungeons, recipes stay exactly as they are.
- **No Kotlin code in this PR.** This file is the entire deliverable for now. Implementation happens in follow-up PRs once you sign off.

---

## 8. Sources

- [Bottom Tab Bar Navigation Best Practices — UX Planet](https://uxplanet.org/bottom-tab-bar-navigation-design-best-practices-48d46a3b0c36)
- [Mobile Navigation UX Best Practices (2026) — Design Studio UI/UX](https://www.designstudiouiux.com/blog/mobile-navigation-ux/)
- [Mastering Game HUD Design — Polydin](https://polydin.com/game-hud-design/)
- [Video Game HUD & UI Design Guide — Sunstrike Studios](https://sunstrikestudios.com/en/blog/HUD_design_in_games/)
- [UX/UI in Game Design — Bruna Delfino, Medium](https://medium.com/@brdelfino.work/ux-and-ui-in-game-design-exploring-hud-inventory-and-menus-5d8c189deb65)
- [Melvor Idle — main site](https://melvoridle.com/) (reference for the genre)
- [Clean UI mod for Melvor Idle — mod.io](https://mod.io/g/melvoridle/m/clean-ui) (evidence even the genre leader needs UI cleanup)
- [Best Practices for Game UI/UX Design — Genieee](https://genieee.com/best-practices-for-game-ui-ux-design/)
