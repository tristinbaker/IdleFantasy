# Fantasy Idler — Implementation Plan

> Working title: **Fantasy Idler**
> Source project: `../Python/IdleApes` (Python/Discord bot)
> Target: FOSS Android app, single-player, F-Droid compatible

---

## Overview

Fantasy Idler is a single-player idle RPG for Android, ported from the IdleApes Discord bot.
Players train skills in 1-hour sessions, fight in dungeons, craft gear, complete quests,
and progress through classic idle RPG skill trees — all offline, no accounts, no ads.

Multiplayer features (raids, player duels, auction house) are replaced with NPC/solo equivalents.

---

## Tech Stack

| Concern | Choice | Reason |
|---|---|---|
| Language | Kotlin | Android-native, concise, null-safe |
| UI | Jetpack Compose | Better for data-heavy list/card UIs than XML |
| Database | Room (SQLite) | Near-identical schema to existing SQLite |
| Background work | WorkManager | Session timers + notifications, survives process death |
| JSON parsing | kotlinx.serialization | Kotlin-native, fast, drop-in for existing JSON data files |
| Architecture | MVVM + Repository | Standard, testable, aligns well with Compose state |
| DI | Hilt | Reduces boilerplate for Repository/ViewModel wiring |
| Notifications | NotificationCompat | Session completion alerts |
| Localization | Android string resources (XML) | Native Weblate format, zero extra tooling |

**No Google Play Services dependency.** F-Droid compatible from day one.

---

## What's Included (Single-Player Scope)

### Kept from IdleApes
- All 6 gathering skills: Mining, Fishing, Woodcutting, Farming, Firemaking, Agility
- All 4 crafting skills: Smithing, Cooking, Fletching, Crafting (jewelry)
- All 7 combat skills: Attack, Strength, Defense, Ranged, Magic, HP, Prayer
- Runecrafting
- Dungeon system (12 dungeons, solo)
- Quest system (50+ quests)
- Farming patch system
- Equipment system (weapons, armor, food, arrows, runes)
- Pet system (XP boosts)
- NPC shop (fixed prices)
- Full XP/level progression (levels 1-99, classic idle RPG curve)
- 1-hour session system with frame-by-frame simulation

### Replaced/Adapted
| Multiplayer Feature | Single-Player Replacement |
|---|---|
| Raid system (multi-player bosses) | Solo boss encounters (same bosses, tuned for 1 player) |
| Player duels | NPC arena challengers at milestone levels |
| Auction house (player trading) | Expanded NPC shop + item recycling (sell to NPC) |
| Leaderboard | Personal best / achievement log |
| `/dig` collective hole | Solo curiosity dig (daily reward, kept as easter egg) |

### Dropped
- Discord-specific: slash commands, embeds, mentions, server management
- Admin commands (`/give`, `/applypermanentboost`)
- Multi-player raid lobby/join system

---

## Data Layer

### JSON Assets (direct port, zero modification needed)
All existing JSON files from `IdleApes/data/` copy into `assets/` as-is:

```
assets/
  data/
    xp_table.json
    equipment.json
    items.json
    enemies.json
    pets.json
    quests.json
    marketplace.json
    bones.json, crops.json, gems.json, logs.json, ores.json, runes.json
    spells.json, trees.json, agility_courses.json
    skills/          (8 skill definitions)
    dungeons/        (12 dungeon configs)
    recipes/         (smithing, cooking, fletching, crafting)
```

`raid_bosses.json` stays but is retargeted for solo encounters.

### Room Schema (port of existing SQLite)

```kotlin
// Core tables — direct port of IdleApes schema
@Entity data class Player(
    @PrimaryKey val id: Long = 1,           // single-player: always ID 1
    val skillLevels: Map<String, Int>,       // JSON column
    val skillXp: Map<String, Long>,
    val inventory: Map<String, Int>,
    val equipped: Map<String, String?>,
    val flags: PlayerFlags,                  // current_hp, equipped_food, etc.
    val pets: List<Pet>
)

@Entity data class SkillSession(
    @PrimaryKey val sessionId: String,       // UUID
    val skillName: String,
    val startedAt: Long,                     // epoch ms
    val endsAt: Long,
    val frames: List<SessionFrame>,          // 60 entries
    val completed: Boolean
)

@Entity data class QuestProgress(
    @PrimaryKey val questId: String,
    val progress: Int,
    val completed: Boolean,
    val completedAt: Long?
)

@Entity data class FarmingPatch(
    @PrimaryKey val patchNumber: Int,
    val cropType: String?,
    val plantedAt: Long?
)

@Entity data class GlobalState(
    @PrimaryKey val key: String,
    val value: String,
    val updatedAt: Long
)

// Duel tables replaced with arena records
@Entity data class ArenaRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val opponentName: String,
    val won: Boolean,
    val combatLog: List<CombatEvent>,
    val completedAt: Long
)
```

Dropped tables: `auction_listings`, `auction_buy_orders`, `duel_challenges`,
`raid_instances`, `raid_participants`, `raid_completions` (replaced by solo equivalents).

---

## Architecture

```
app/
  ui/
    screens/
      SkillsScreen.kt          # All skills overview + session launcher
      CombatScreen.kt          # Dungeon selection + combat details
      CraftingScreen.kt        # Smithing/Cooking/Fletching/Crafting tabs
      QuestsScreen.kt          # Quest list + active quest tracker
      InventoryScreen.kt       # Items + gear + equip/unequip
      ShopScreen.kt            # NPC shop + item recycling
      ProgressScreen.kt        # Active session status + frame log
      ProfileScreen.kt         # Stats, pets, achievements
    components/
      SessionCard.kt           # Reusable session status widget
      SkillProgressBar.kt      # XP bar with level display
      ItemGrid.kt              # Inventory/loot grid
    navigation/
      AppNavigation.kt         # Bottom nav: Skills | Combat | Crafting | Quests | Profile
  viewmodel/
    SkillsViewModel.kt
    CombatViewModel.kt
    CraftingViewModel.kt
    QuestsViewModel.kt
    InventoryViewModel.kt
    ShopViewModel.kt
    SessionViewModel.kt
  repository/
    PlayerRepository.kt        # Player CRUD, inventory management
    SessionRepository.kt       # Session create/complete/abandon
    GameDataRepository.kt      # Loads + caches JSON assets
    QuestRepository.kt
  data/
    db/
      AppDatabase.kt           # Room database
      dao/                     # One DAO per entity
    model/                     # Data classes (Player, Session, Item, etc.)
    json/                      # kotlinx.serialization models for JSON assets
  simulator/
    SkillSimulator.kt          # Port of skill_simulator.py
    CombatSimulator.kt         # Port of combat_simulator.py
    DungeonSimulator.kt        # Port of dungeon_simulator.py
    CraftingSimulator.kt       # Port of smithing/cooking/fletching_utils.py
    AgilitySim.kt
    FarmingManager.kt
  worker/
    SessionWorker.kt           # WorkManager: fires notification when session ends
  notification/
    SessionNotificationManager.kt
```

---

## Localization Architecture

Localization is set up in Phase 1 and maintained as a hard rule throughout. The goal is
for the project to be Weblate-ready without any structural changes later.

### Why Android string XML

Weblate has first-class native support for Android string resource XML. No plugins, no
converters, no custom scripts — Weblate reads and writes `strings.xml` directly. Every
other format (gettext `.po`, JSON i18n, etc.) would require an adapter or export step.

### String resource file layout

Strings are split into per-domain files rather than one monolithic `strings.xml`.
Each file maps to one Weblate component, which keeps translator workload scoped and
allows domain experts (e.g., someone who knows the game lore) to focus on their section.

```
app/src/main/res/
  values/                      # Source language (English)
    strings.xml                # Core UI: nav labels, buttons, errors, settings, onboarding
    strings_notifications.xml  # All notification titles and body text
    strings_skills.xml         # Skill names and descriptions (13 skills)
    strings_items.xml          # Item names and descriptions (~100 items)
    strings_quests.xml         # Quest names, descriptions, objectives (50+ quests)
    strings_enemies.xml        # Enemy names, dungeon names, boss names
    strings_game.xml           # XP messages, level-up text, session result summaries
  values-fr/                   # French translation (same file structure)
    strings.xml
    strings_notifications.xml
    ...
  values-de/                   # German (same structure)
  values-es/                   # etc.
```

All translation directories mirror the `values/` structure exactly.
Weblate is pointed at the repo root with one component per file:

| Weblate Component | File mask | Base file |
|---|---|---|
| UI Strings | `app/src/main/res/values-*/strings.xml` | `values/strings.xml` |
| Notifications | `app/src/main/res/values-*/strings_notifications.xml` | `values/strings_notifications.xml` |
| Skills | `app/src/main/res/values-*/strings_skills.xml` | `values/strings_skills.xml` |
| Items | `app/src/main/res/values-*/strings_items.xml` | `values/strings_items.xml` |
| Quests | `app/src/main/res/values-*/strings_quests.xml` | `values/strings_quests.xml` |
| Enemies & Dungeons | `app/src/main/res/values-*/strings_enemies.xml` | `values/strings_enemies.xml` |
| Game Messages | `app/src/main/res/values-*/strings_game.xml` | `values/strings_game.xml` |

### Naming conventions

All string keys follow a `{domain}_{identifier}_{role}` pattern:

```xml
<!-- Core UI -->
<string name="nav_skills">Skills</string>
<string name="nav_combat">Combat</string>
<string name="btn_start_session">Start Session</string>
<string name="btn_abandon_session">Abandon Session</string>
<string name="label_level">Level</string>
<string name="error_not_enough_items">Not enough %1$s to start.</string>

<!-- Skills -->
<string name="skill_mining_name">Mining</string>
<string name="skill_mining_desc">Extract ores from rock faces.</string>
<string name="skill_fishing_name">Fishing</string>

<!-- Items -->
<string name="item_iron_ore_name">Iron Ore</string>
<string name="item_iron_ore_desc">A common ore used for smithing.</string>
<string name="item_iron_bar_name">Iron Bar</string>

<!-- Quests -->
<string name="quest_first_ore_name">First Strike</string>
<string name="quest_first_ore_desc">Mine your first ore to begin your journey.</string>
<string name="quest_first_ore_objective">Mine 1 ore</string>

<!-- Enemies / Dungeons -->
<string name="enemy_goblin_name">Goblin</string>
<string name="dungeon_dark_cave_name">Dark Cave</string>
<string name="boss_king_black_dragon_name">King Black Dragon</string>

<!-- Notifications -->
<string name="notif_session_complete_title">Session Complete</string>
<string name="notif_session_complete_body">Your %1$s session has finished. Tap to collect rewards.</string>

<!-- Plurals -->
<plurals name="item_count">
    <item quantity="one">%1$d item</item>
    <item quantity="other">%1$d items</item>
</plurals>

<plurals name="xp_gained">
    <item quantity="one">%1$d XP gained</item>
    <item quantity="other">%1$d XP gained</item>
</plurals>
```

### Format argument rules

- **Always use positional args** (`%1$s`, `%1$d`, `%2$s`) — never bare `%s` or `%d`.
  Positional args let translators reorder arguments for their language's grammar.
- Mark non-translatable strings with `translatable="false"` (internal keys, URL schemes,
  format templates used only in code, etc.).
- Add `<!-- Translator note: ... -->` XML comments above any string where context is
  non-obvious (e.g., abbreviations, game-specific jargon).

### Game content string resolution

The JSON data files (`items.json`, `enemies.json`, etc.) use internal snake_case keys
(`"iron_ore"`, `"dark_cave"`) as identifiers — these are never shown to the user directly.
Display names are always resolved through string resources via a helper:

```kotlin
// Centralised in a single helper — all game content goes through here
object GameStrings {
    fun itemName(context: Context, key: String): String =
        context.stringByName("item_${key}_name") ?: key.toTitleCase()

    fun itemDesc(context: Context, key: String): String =
        context.stringByName("item_${key}_desc") ?: ""

    fun skillName(context: Context, key: String): String =
        context.stringByName("skill_${key}_name") ?: key.toTitleCase()

    fun dungeonName(context: Context, key: String): String =
        context.stringByName("dungeon_${key}_name") ?: key.toTitleCase()

    fun enemyName(context: Context, key: String): String =
        context.stringByName("enemy_${key}_name") ?: key.toTitleCase()

    fun questName(context: Context, key: String): String =
        context.stringByName("quest_${key}_name") ?: key.toTitleCase()

    // Fallback: title-cases the key so missing strings are readable, not broken
    private fun String.toTitleCase() =
        replace('_', ' ').split(' ').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
}

// Context extension used internally
private fun Context.stringByName(name: String): String? {
    val id = resources.getIdentifier(name, "string", packageName)
    return if (id != 0) getString(id) else null
}
```

In Compose screens, use `LocalContext.current` and `stringResource()` for static strings,
and `GameStrings.*` for dynamic game content keys resolved at runtime.

### Hard rules (enforced from Phase 1)

- **No hardcoded user-facing strings anywhere in the codebase.** Every label, button,
  message, notification, and tooltip goes through string resources.
- JSON data files contain only internal identifiers, never display text.
- New game content (item, skill, dungeon, quest) = new string resource entries added at
  the same time as the JSON entry.
- All `@Composable` functions accept `String` parameters (already resolved), not raw keys,
  so resolution always happens at the ViewModel/Repository layer.

---

## Session System (Core Mechanic)

The 1-hour session mechanic maps cleanly to WorkManager:

1. Player taps "Start Mining (Iron Ore)"
2. App runs simulator immediately, stores 60-frame result in DB
3. `SessionWorker` is enqueued with a 1-hour delay
4. On completion: `SessionWorker` fires notification, marks session complete
5. Next time player opens the relevant screen: rewards are applied, UI updates

**Battery optimization note**: Prompt user on first session to exempt the app from
battery optimization (via `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`). This is
standard practice for idle games and is acceptable on F-Droid. WorkManager
`setExpedited()` used for the completion notification to minimize Doze delay.

---

## Navigation Structure

```
Bottom Nav (5 tabs):
  [Skills]     — Overview of all 13 skills, launch sessions, session progress
  [Combat]     — Dungeon list, boss arena, equipment loadout
  [Crafting]   — Smithing / Cooking / Fletching / Crafting (jewelry) sub-tabs
  [Quests]     — Quest log, active quest tracker
  [Profile]    — Stats, inventory, gear, pets, achievements, shop
```

Each tab uses a single Compose screen with nested navigation for detail views
(e.g., Skills → Mining Detail → Ore selection).

---

## Art Strategy

The game needs icons, not complex sprites. Art requirements are minimal for an idle game.

**Recommended free/CC0 sources:**
- **Kenney.nl** — RPG asset packs, item icons, UI elements (CC0)
- **OpenGameArt.org** — Filtered to CC0/CC-BY, pixel art RPG items
- **Game-icons.net** — SVG icons for skills/items (CC BY 3.0)

**Minimum viable art list:**
- 1 icon per item type (~80 items — ores, logs, fish, gems, bars, food, equipment)
- 1 icon per skill (13 skills)
- 1 icon per dungeon/boss (12 dungeons)
- Basic UI chrome (buttons, panels, progress bars — can use Material Design defaults)

**Strategy**: Start with placeholder colored rectangles + text labels. Add art
incrementally — the game is fully playable without polished icons.

---

## Implementation Phases

### Phase 1 — Foundation (Weeks 1-2)
- [ ] Android project setup (Kotlin, Compose, Room, Hilt, WorkManager)
- [ ] Copy all JSON data files into `assets/`
- [ ] Implement Room schema + DAOs (Player, SkillSession, QuestProgress, FarmingPatch)
- [ ] `GameDataRepository`: load + cache all JSON assets at startup
- [ ] `PlayerRepository`: create player profile, persist between sessions
- [ ] Basic bottom nav shell (5 empty screens)
- [ ] Port `game_utils.py` (XP table, level calculations) → `XpTable.kt`
- [ ] Create all string resource files (`strings.xml`, `strings_skills.xml`,
  `strings_items.xml`, `strings_quests.xml`, `strings_enemies.xml`,
  `strings_notifications.xml`, `strings_game.xml`) with English source strings
  — populate all skill names/descs, all item names/descs, all dungeon/enemy names
  up front so Weblate has a complete source to work from immediately
- [ ] Implement `GameStrings` helper and `Context.stringByName` extension
- [ ] Lint rule or code review checklist: no hardcoded user-facing strings

**Milestone**: App launches, player profile created, XP calculations work,
all source strings in XML and resolvable through `GameStrings`.

### Phase 2 — Core Skill Loop (Weeks 3-4)
- [ ] Port `skill_simulator.py` → `SkillSimulator.kt`
- [ ] Port `mining_utils.py`, `fishing_utils.py`, `woodcutting_utils.py` → Kotlin
- [ ] `SessionWorker` + `SessionNotificationManager`
- [ ] Skills screen: skill list, XP bars, session launcher, session progress
- [ ] Progress screen: real-time frame viewer, session results
- [ ] Inventory management (add/remove items from session loot)
- [ ] Session abandon flow

**Milestone**: Full Mining/Fishing/Woodcutting loop works end-to-end with notifications.

### Phase 3 — All Skills (Weeks 5-6)
- [ ] Farming: patch system, plant/harvest/clear screens
- [ ] Firemaking: log selection, quantity-based sessions
- [ ] Agility: course auto-selection, lap tracking
- [ ] Runecrafting: rune type selection
- [ ] Port `agility_simulator.py`, `firemaking_utils.py`, `runecrafting_utils.py`
- [ ] Burying bones for Prayer XP
- [ ] `/drops` equivalent: "View drops" sheet per skill

**Milestone**: All 6 gathering skills + Runecrafting + Prayer playable.

### Phase 4 — Combat System (Weeks 7-8)
- [ ] Port `combat_simulator.py` → `CombatSimulator.kt`
- [ ] Port `dungeon_simulator.py` → `DungeonSimulator.kt`
- [ ] Equipment system: equip/unequip weapons, armor, food, arrows, runes
- [ ] Magic: spell selection, rune consumption
- [ ] Combat screen: dungeon list, enemy previews, combat log viewer
- [ ] Solo boss arena (replaces raids): tougher encounters, rare drops
- [ ] NPC arena challengers (replaces duels): milestone unlock fights

**Milestone**: Full dungeon combat loop works, all 12 dungeons accessible at appropriate levels.

### Phase 5 — Crafting (Week 9)
- [ ] Port `smithing_utils.py` → `SmithingSimulator.kt`
- [ ] Port `cooking_utils.py` → `CookingSimulator.kt`
- [ ] Port `fletching_utils.py` → `FletchingSimulator.kt`
- [ ] Port crafting (jewelry) logic → `CraftingSimulator.kt`
- [ ] Crafting screen: recipe browser, ingredient checks, session launcher
- [ ] Recipe detail view: requirements, outputs, XP per item

**Milestone**: All 4 crafting skills work, items craftable and usable in combat.

### Phase 6 — Quests & Farming Polish (Week 10)
- [ ] Port `quest_utils.py` → `QuestManager.kt`
- [ ] Quest screen: available/active/completed lists, requirement checks
- [ ] Quest detail view: objectives, rewards, prerequisite chain
- [ ] Farming screen polish: patch grid, growth timers, harvest notifications
- [ ] Pet system: manage pets, XP boost activation, pet display

**Milestone**: Quest system complete, all 50+ quests accessible.

### Phase 7 — Economy & Shop (Week 10-11)
- [ ] NPC shop screen: browse categories, buy items
- [ ] Item recycling: sell items to NPC for coins (replaces auction house)
- [ ] Port `marketplace.json` shop categories
- [ ] Inspect item sheet: stats, description, source

**Milestone**: Full economy loop — earn coins from drops, spend at shop.

### Phase 8 — Polish & Release Prep (Week 12)
- [ ] Profile screen: full stats view, achievement log, personal bests
- [ ] Achievements system (milestone-based: first level 99, first dungeon clear, etc.)
- [ ] Art pass: replace placeholders with Kenney/OpenGameArt assets
- [ ] Onboarding flow (first-launch tutorial)
- [ ] Settings screen: notifications on/off, battery optimization prompt, language override
- [ ] Set up Weblate instance (self-hosted or hosted) linked to the repo:
  - Create one component per strings XML file (7 components total)
  - Configure file mask and base file for each component (see Localization Architecture)
  - Enable "Add missing languages" and "Manage strings" in Weblate settings
  - Add `TRANSLATING.md` with contribution guide for translators
- [ ] Audit all strings for translator-friendliness: add `<!-- Translator note -->` comments
  where context is ambiguous, verify all format args are positional
- [ ] F-Droid metadata (`fastlane/` directory, screenshots, description)
- [ ] Final QA pass (balance check, edge cases, RTL layout sanity check)

**Milestone**: Shippable 1.0 on F-Droid, Weblate open for community translations.

---

## What NOT to Build (Scope Guard)

- No online features, no accounts, no sync
- No ads, no IAP, no analytics
- No Cloud Save (local SQLite only; players can back up via standard Android backup)
- No social features
- No push notifications beyond session completion (no engagement spam)

---

## File Reference (Source Project)

Key IdleApes files to reference during port:

| Source File | What to Port |
|---|---|
| `src/database.py` | Room schema |
| `src/game_utils.py` | XP table, level calcs, equipment loader |
| `src/skill_simulator.py` | Core session simulation |
| `src/combat_simulator.py` | Turn-based combat engine |
| `src/dungeon_simulator.py` | Dungeon run simulation |
| `src/smithing_utils.py` | Smithing recipe logic |
| `src/cooking_utils.py` | Cooking recipe logic |
| `src/fletching_utils.py` | Fletching recipe logic |
| `src/commands.py` | Command handlers → ViewModel logic |
| `src/quest_utils.py` | Quest state machine |
| `src/auction_house.py` | Reference only (NPC shop replaces this) |
| `src/raid_utils.py` | Reference for solo boss tuning |
| `data/` | All JSON files → assets/ (direct copy) |
