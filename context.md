# IdleFantasy — Project Context

## Overview

**IdleFantasy** is a free, open-source, offline idle RPG for Android.
- No internet, no account, no ads
- Set hero to work, close app, come back to loot
- App ID: `com.tristinbaker.idlefantasy`
- Package: `com.fantasyidler`
- Current version: `1.8.8` (versionCode 69)
- Min SDK: 26 | Target SDK: 35
- Distributed via F-Droid and GitHub Releases

---

## Tech Stack

| Concern | Library |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Database | Room (SQLite) v3 |
| DI | Hilt |
| JSON | kotlinx.serialization |
| Architecture | MVVM + Repository |
| Background work | AlarmManager + BroadcastReceivers |
| Notifications | NotificationCompat |
| Localization | Android string resources (Weblate-compatible) |

---

## Repository Root Structure

```
IdleFantasy/
├── app/                        # Android application module
│   ├── build.gradle.kts        # Build config (SDK, deps, signing)
│   ├── proguard-rules.pro
│   ├── lint-baseline.xml
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/data/        # All static game JSON data
│       └── kotlin/com/fantasyidler/
├── wiki/                       # Python wiki site generator
├── docs/                       # F-Droid metadata
├── build.gradle.kts            # Root build
├── settings.gradle.kts
└── gradle/                     # Version catalog (libs.versions.toml)
```

---

## Kotlin Package Structure

```
com.fantasyidler/
├── FantasyIdlerApp.kt          # Hilt Application class
├── MainActivity.kt             # Single Activity; handles notification deep-links, theme, font scale
│
├── data/
│   ├── db/
│   │   ├── AppDatabase.kt      # Room database (v3); holds migrations 1→2, 2→3
│   │   └── dao/
│   │       ├── PlayerDao.kt
│   │       ├── SkillSessionDao.kt
│   │       ├── QuestProgressDao.kt
│   │       ├── FarmingPatchDao.kt
│   │       ├── GlobalStateDao.kt
│   │       └── ArenaRecordDao.kt
│   ├── json/                   # Kotlin data classes for assets/data JSON
│   │   ├── BlessingData.kt, BoneData.kt, BossData.kt, CropData.kt
│   │   ├── DailyQuestData.kt, DungeonData.kt, EnemyData.kt
│   │   ├── EquipmentData.kt, GatheringData.kt, GuildDailyData.kt
│   │   ├── GuildQuestData.kt, HerbloreData.kt, MarketplaceData.kt
│   │   ├── PetData.kt, QuestData.kt, RecipeData.kt, RuneData.kt
│   │   ├── SkillData.kt, SkillingDungeonData.kt, SlayerTaskData.kt
│   │   ├── SpellData.kt, ThievingNpcData.kt, TradeRouteData.kt
│   └── model/                  # Room entities + domain models
│       ├── Player.kt           # Room entity; JSON columns for complex fields
│       ├── PlayerModels.kt     # PlayerFlags, QueuedAction, HiredWorker, WorkerTier, OwnedPet, Skills, EquipSlot
│       ├── SkillSession.kt     # Room entity; 60-frame pre-simulated session
│       ├── SessionFrame.kt     # Single minute of a session (items, xp, events)
│       ├── QuestProgress.kt    # Room entity; quest completion tracking
│       ├── FarmingPatch.kt     # Room entity; crop patch state
│       ├── GlobalState.kt      # Room entity; app-wide state
│       ├── ArenaRecord.kt      # Room entity; game corner scores
│       └── TownBuildingDef.kt  # Data for Inn/GuildHall/Church upgrade tiers
│
├── di/
│   ├── AppModule.kt            # Hilt: provides Json, Context
│   └── DatabaseModule.kt      # Hilt: provides AppDatabase + all DAOs
│
├── notification/
│   └── SessionNotificationManager.kt   # Creates/cancels session-complete notifications; EXTRA_NAVIGATE_TO
│
├── receiver/                   # BroadcastReceivers
│   ├── SessionAlarmReceiver.kt # Fired when a session ends; triggers collection
│   ├── FarmPatchAlarmReceiver.kt
│   ├── BackupAlarmReceiver.kt
│   ├── BuffAlarmReceiver.kt    # XP boost / church blessing expiry
│   └── BootReceiver.kt         # Reschedules alarms on device reboot
│
├── repository/
│   ├── PlayerRepository.kt         # Core: XP, inventory, coins, flags, import/export
│   ├── SessionRepository.kt        # Active session CRUD, timer scheduling
│   ├── QueuedSessionStarter.kt     # Auto-starts next queued action after session ends
│   ├── WorkerQueuedSessionStarter.kt
│   ├── GameDataRepository.kt       # Lazy-loads all JSON assets; singleton cache
│   ├── QuestRepository.kt          # Quest progress update logic
│   ├── DailyQuestRepository.kt     # Daily quest roll + progress
│   ├── GuildRepository.kt          # Guild rep, rank, quest logic
│   ├── ChurchRepository.kt         # Blessing activation/expiry; xpMultiplier, coinMultiplier
│   ├── FarmingRepository.kt        # Crop plant/harvest scheduling
│   ├── SlayerRepository.kt         # Slayer task assignment
│   ├── TownRepository.kt           # Town building upgrades
│   ├── GlobalStateRepository.kt
│   ├── BackupScheduler.kt          # Auto-backup WorkManager scheduling
│   └── BuffNotificationScheduler.kt
│
├── simulator/
│   ├── SkillSimulator.kt           # Generates 60 frames for gathering/crafting sessions
│   ├── CombatSimulator.kt          # Generates 60 frames for dungeon/boss combat
│   ├── ThievingSimulator.kt        # Generates frames for thieving sessions
│   ├── MercantileSimulator.kt      # Generates frames for trade route sessions
│   ├── SkillingDungeonSimulator.kt # Generates frames for expedition sessions
│   └── XpTable.kt                  # levelForXp(), xpForLevel()
│
├── ui/
│   ├── navigation/
│   │   ├── Screen.kt               # Sealed class for all route definitions
│   │   └── AppNavigation.kt        # NavHost, bottom nav bar, composable registrations
│   ├── theme/
│   │   ├── Color.kt                # Material 3 color scheme
│   │   ├── Theme.kt                # FantasyIdlerTheme (dark/light/system)
│   │   └── Type.kt                 # Typography
│   ├── screen/                     # 24 composable screens
│   └── viewmodel/                  # 22 ViewModels (one per screen)
│
└── util/
    ├── Extensions.kt               # Kotlin extensions (formatting, etc.)
    └── GameStrings.kt              # Localizable display name helpers
```

---

## Database Entities (Room v3)

| Entity | Table | Description |
|---|---|---|
| `Player` | `players` | Single row (id=1); all complex fields as JSON strings |
| `SkillSession` | `skill_sessions` | Active/past sessions with 60 pre-simulated frames |
| `QuestProgress` | `quest_progress` | Per-quest completion + counter tracking |
| `FarmingPatch` | `farming_patches` | Crop patches with timers |
| `GlobalState` | `global_state` | App-wide state flags |
| `ArenaRecord` | `arena_records` | Game corner high scores |

**DB Migrations:** 1→2 adds `is_worker_session` + `efficiency_multiplier`; 2→3 adds `worker_slot`.

---

## Key Data Models

### `Player` (Room entity)
All complex state stored as JSON strings inside a single row:
- `skillLevels: String` → `Map<String, Int>` (skill key → 1–99)
- `skillXp: String` → `Map<String, Long>`
- `inventory: String` → `Map<String, Int>` (item key → qty)
- `equipped: String` → `Map<String, String?>` (slot → item key)
- `flags: String` → `PlayerFlags` (HP, active food, queue, settings...)
- `pets: String` → `List<OwnedPet>`
- `coins: Long`

### `PlayerFlags` (serialized into `flags` column)
Contains: currentHp, equippedFood, equippedArrows, equippedRunes, activeSpell, activeWeaponSlot, xpBoostExpiresAt, characterName/gender/race, sessionQueue (up to 3), hiredWorker, hiredWorker2, guildReputation, dailyQuestIds/progress/claimed, skillingDungeonNotes, unlockedDungeons, activeBlessingKey, slayerTask, recentSessions, skillPrestige, farmingFertilizer, townBuildingTiers, lotteryTickets, etc.

### `SkillSession` (Room entity)
- `sessionId: String` (UUID)
- `skillName: String` (canonical skill key)
- `activityKey: String` (ore/dungeon/etc.)
- `startedAt / endsAt: Long` (epoch ms; session = up to 1 hour)
- `frames: String` → `List<SessionFrame>` (60 pre-simulated frames)
- `isWorkerSession: Boolean`
- `workerSlot: Int` (0=player, 1=long laborer, 2=second worker)

### `Skills` object — canonical skill keys
Gathering: `mining`, `fishing`, `woodcutting`, `farming`, `agility`, `thieving`
Crafting: `smithing`, `cooking`, `fletching`, `crafting`, `firemaking`, `runecrafting`, `herblore`, `construction`
Combat: `attack`, `strength`, `defense`, `ranged`, `magic`, `hitpoints`, `prayer`
Support: `mercantile`, `slayer`

### `EquipSlot` object — equipment slot keys
Weapons: `weapon_atk`, `weapon_str`, `weapon_ranged`, `weapon_magic`
Armor: `head`, `body`, `legs`, `boots`, `cape`, `ring`, `necklace`, `shield`
Tools: `pickaxe`, `axe`, `fishing_rod`, `hoe`

---

## Navigation & Screens

### Bottom Nav (5 tabs)
`Skills` | `Combat` | `Home` (center circle) | `Quests` | `Profile`

### All Screens (routes)
| Screen | Route | Entry Point |
|---|---|---|
| Home | `home` | Bottom nav |
| Skills | `skills` | Bottom nav |
| Combat | `combat` | Bottom nav |
| Quests | `quests` | Bottom nav |
| Profile | `profile` | Bottom nav |
| Settings | `settings` | From Home |
| Shop | `shop` | From Home |
| Inn | `inn` | From Home |
| WorkerSkills | `worker_skills?initialSlot={initialSlot}` | From Home/Inn |
| GuildHall | `guild_hall` | From Home |
| GuildDetail | `guild_detail/{guild}` | From GuildHall |
| Church | `church` | From Home |
| Slayer | `slayer` | From Home/Skills |
| Builder | `builder` | From Home |
| GameCorner | `game_corner` | From Home |
| Farming | `farming` | From Skills (also notification deep-link) |
| BoneAltar | `bone_altar` | From Skills |
| Combat Gear | `combat/gear` | From Profile |
| Onboarding | Full-screen overlay | First launch |

---

## Session Lifecycle

1. User picks skill + activity → VM calls `SessionRepository.startSession()`
2. Simulator pre-generates 60 `SessionFrame`s and stores full `SkillSession` in DB
3. `AlarmManager` schedules exact alarm at `endsAt`
4. `SessionAlarmReceiver` fires → marks session complete → sends notification
5. User opens app → ViewModel calls `collectSession()` → `PlayerRepository.applySessionResults()`
6. `QueuedSessionStarter` auto-starts next action in queue

---

## Worker System

- **Long Laborer** (slot 1): 8h, 0.5× efficiency, uncapped craft qty
- **Apprentice** (slot 2): 8h, 1.0× efficiency, max 480 crafts
- **Journeyman** (slot 2): 6h, 1.25× efficiency, max 360 crafts
- **Master** (slot 2): 4h, 2.0× efficiency, max 240 crafts
- Each worker has its own queue (1 queued item max)

---

## Boosts & Multipliers

- **XP Boost**: 2× XP for 48h, costs 250,000 coins (`PlayerRepository.XP_BOOST_COST`)
- **Church Blessings**: XP boost (up to 1.5×), Defense bonus, or Coin multiplier
- **Skill Prestige**: 0–3 tiers, each adds 10% XP bonus; requires level 99; resets XP/level to 1
- **Pets**: Passive XP bonus per pet

---

## Game Data Assets (`assets/data/`)

| File | Contents |
|---|---|
| `enemies.json` | Enemy stats (34 KB) |
| `equipment.json` | All equipment (88 KB) |
| `quests.json` | 170+ quests (68 KB) |
| `guild_daily_quests.json` | Guild daily pool (101 KB) |
| `guild_quests.json` | Guild progression quests (83 KB) |
| `marketplace.json` | Shop items (11 KB) |
| `dungeons/` | 20 dungeon JSON files |
| `skilling_dungeons/` | Expedition JSON files |
| `skills/` | Per-skill data |
| `trade_routes/` | Mercantile route data |
| `recipes/` | smithing, cooking, fletching, crafting, herblore, construction |
| `daily_quests.json` | Daily quest pool |
| `crops.json` | Farming crop data |
| `bones.json` | Prayer bones |
| `pets.json` | Collectible pets |
| `spells.json` | Magic spells |
| `agility_courses.json` | Agility courses |
| `slayer_tasks.json` | Slayer assignment pool |
| `raid_bosses.json` | Boss encounter data |

---

## Important Patterns

### Repository Writes
All writes go through `PlayerRepository`. Never touch `PlayerDao` directly from a ViewModel.
Pattern:
```kotlin
val player = getOrCreatePlayer()
val inventory: MutableMap<String, Int> = json.decodeFromString(player.inventory)
// ... mutate ...
playerDao.upsert(player.copy(inventory = json.encode<Map<String, Int>>(inventory)))
```

### JSON Encoding Helper
```kotlin
private inline fun <reified T> Json.encode(value: T): String =
    encodeToString(serializersModule.serializer<T>(), value)
```

### GameDataRepository (static data)
All JSON assets are `lazy`-loaded singletons. Access via `gameDataRepository.equipment`, `.dungeons`, etc.

### Adding a New Screen
1. Add `object MyScreen : Screen(route, labelRes, icon)` to `Screen.kt`
2. Add `composable(Screen.MyScreen.route)` in `AppNavigation.kt`
3. Create `MyScreen.kt` in `ui/screen/`
4. Create `MyScreenViewModel.kt` in `ui/viewmodel/`

### Adding a New DB Column / Migration
1. Modify entity data class
2. Add `MIGRATION_N_(N+1)` in `AppDatabase.kt`
3. Bump `version` in `@Database`
4. Regenerate schema: `./gradlew :app:kspDebugKotlin`

### Permissions (Manifest)
- `POST_NOTIFICATIONS` — session complete alerts
- `RECEIVE_BOOT_COMPLETED` — reschedule alarms on reboot
- `WAKE_LOCK` — keep CPU alive during alarm processing
- `USE_EXACT_ALARM` + `SCHEDULE_EXACT_ALARM` — precise session timers

---

## Build

```bash
# Debug APK
./gradlew :app:assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk

# Run tests
./gradlew :app:testDebugUnitTest

# Regenerate lint baseline
./gradlew lintDebug
```

Requirements: Android Studio Hedgehog+, JDK 17+, Android SDK 34/35

---

## Localization

Languages: English, German, French, Spanish, Turkish.
String resources in `app/src/main/res/values-*/strings.xml`.
Weblate-compatible.

---

## Key Files Quick-Reference

| File | Purpose |
|---|---|
| [Player.kt](file:///home/bixpurr/Desktop/Study/IdleFantasy/app/src/main/kotlin/com/fantasyidler/data/model/Player.kt) | Room entity — all player state |
| [PlayerModels.kt](file:///home/bixpurr/Desktop/Study/IdleFantasy/app/src/main/kotlin/com/fantasyidler/data/model/PlayerModels.kt) | PlayerFlags, Skills, EquipSlot, WorkerTier, QueuedAction |
| [PlayerRepository.kt](file:///home/bixpurr/Desktop/Study/IdleFantasy/app/src/main/kotlin/com/fantasyidler/repository/PlayerRepository.kt) | All player write operations |
| [GameDataRepository.kt](file:///home/bixpurr/Desktop/Study/IdleFantasy/app/src/main/kotlin/com/fantasyidler/repository/GameDataRepository.kt) | Lazy JSON asset cache |
| [AppDatabase.kt](file:///home/bixpurr/Desktop/Study/IdleFantasy/app/src/main/kotlin/com/fantasyidler/data/db/AppDatabase.kt) | Room DB + migrations |
| [Screen.kt](file:///home/bixpurr/Desktop/Study/IdleFantasy/app/src/main/kotlin/com/fantasyidler/ui/navigation/Screen.kt) | All route definitions |
| [AppNavigation.kt](file:///home/bixpurr/Desktop/Study/IdleFantasy/app/src/main/kotlin/com/fantasyidler/ui/navigation/AppNavigation.kt) | NavHost + bottom nav bar |
| [HomeScreen.kt](file:///home/bixpurr/Desktop/Study/IdleFantasy/app/src/main/kotlin/com/fantasyidler/ui/screen/HomeScreen.kt) | Town hub, session status |
| [HomeViewModel.kt](file:///home/bixpurr/Desktop/Study/IdleFantasy/app/src/main/kotlin/com/fantasyidler/ui/viewmodel/HomeViewModel.kt) | Core session + player state |
| [CombatSimulator.kt](file:///home/bixpurr/Desktop/Study/IdleFantasy/app/src/main/kotlin/com/fantasyidler/simulator/CombatSimulator.kt) | Dungeon session pre-simulation |
| [SkillSimulator.kt](file:///home/bixpurr/Desktop/Study/IdleFantasy/app/src/main/kotlin/com/fantasyidler/simulator/SkillSimulator.kt) | Gathering/crafting pre-simulation |
| [app/build.gradle.kts](file:///home/bixpurr/Desktop/Study/IdleFantasy/app/build.gradle.kts) | Dependencies + build config |
