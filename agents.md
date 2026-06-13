# IdleFantasy — Agents

This file defines specialized agent roles for working on the IdleFantasy codebase.
Reference the [context.md](context.md) alongside these agents for full project awareness.

---

## 🎨 UI Agent — Compose & Navigation

**Role:** Responsible for all Jetpack Compose UI, screen layouts, theming, and navigation changes.

**Primary Files:**
- `app/src/main/kotlin/com/fantasyidler/ui/screen/` — 24 screen composables
- `app/src/main/kotlin/com/fantasyidler/ui/viewmodel/` — 22 ViewModels
- `app/src/main/kotlin/com/fantasyidler/ui/navigation/Screen.kt`
- `app/src/main/kotlin/com/fantasyidler/ui/navigation/AppNavigation.kt`
- `app/src/main/kotlin/com/fantasyidler/ui/theme/` — Color, Theme, Type

**Key Constraints:**
- Use Material 3 components (`MaterialTheme`, `Card`, `NavigationBar`, etc.)
- Respect the `LocalDensity` font-scale override set in `MainActivity`
- All strings must use `stringResource()` — never hardcode user-visible text
- New screens: register in `Screen.kt` + `AppNavigation.kt`, create ViewModel with `@HiltViewModel`
- Bottom nav always shows exactly 5 tabs (`Screen.bottomNavItems`)
- Sub-screens (accessible from a tab) must be in `tabSubScreens` map in `AppNavigation.kt` for correct back-press behavior
- Dark/light theme is driven by `SettingsViewModel.themePreference` → `FantasyIdlerTheme`

**Common Patterns:**
```kotlin
// ViewModel injection in a screen
@HiltViewModel
class MyViewModel @Inject constructor(
    private val playerRepo: PlayerRepository,
    private val gameData: GameDataRepository,
) : ViewModel()

// State collection in a composable
val state by viewModel.uiState.collectAsStateWithLifecycle()
```

---

## ⚔️ Game Mechanics Agent — Skills, Combat & Balance

**Role:** Responsible for game logic, balance, skill rules, quest conditions, item/equipment effects, and simulation output.

**Primary Files:**
- `app/src/main/kotlin/com/fantasyidler/simulator/` — all 6 simulators
- `app/src/main/assets/data/` — all JSON game data files
- `app/src/main/kotlin/com/fantasyidler/data/json/` — Kotlin data classes for JSON
- `app/src/main/kotlin/com/fantasyidler/data/model/PlayerModels.kt` — `Skills`, `EquipSlot`, `WorkerTier`

**Key Constraints:**
- Sessions are pre-simulated into 60 `SessionFrame` objects at start time — **not computed live**
- XP caps at level 99 per skill; prestige (0–3) adds +10% XP per tier
- Simulator output must be deterministic for the same inputs (no random seeds stored)
- Adding a new skill: add key to `Skills` object, update `Skills.ALL` and `DEFAULT_LEVELS`/`DEFAULT_XP`, add JSON asset
- Adding a new item: add to the relevant JSON file (e.g. `equipment.json`, `ores.json`), update `GameDataRepository.usefulItemKeys` if it's a "useful" item
- Multiplier chain: `efficiencyMultiplier` (worker) → XP boost (2×) → church blessing → prestige

**Simulator Contract:**
```kotlin
// Each simulator returns List<SessionFrame> (60 frames, one per minute)
// SessionFrame contains: xpGained, itemsGained, events, coinsGained, etc.
// Called once when session starts; result JSON stored in SkillSession.frames
```

---

## 💾 Data & Persistence Agent — Room, Repository & Save System

**Role:** Responsible for database schema, migrations, repository layer, save/import/export, and background alarm scheduling.

**Primary Files:**
- `app/src/main/kotlin/com/fantasyidler/data/db/AppDatabase.kt`
- `app/src/main/kotlin/com/fantasyidler/data/db/dao/`
- `app/src/main/kotlin/com/fantasyidler/repository/` — all 15 repositories
- `app/src/main/kotlin/com/fantasyidler/data/model/Player.kt`
- `app/src/main/kotlin/com/fantasyidler/data/model/PlayerModels.kt`

**Key Constraints:**
- **Never** write to `PlayerDao` directly from a ViewModel — always go through `PlayerRepository`
- All complex player state lives in JSON columns on the single `Player` row (id=1)
- Adding a new `PlayerFlags` field: add with a default value (backward-compatible JSON deserialization)
- Adding a new DB column/table: write a `MIGRATION_N_(N+1)` and bump `@Database(version = ...)`
- Schema is exported to `app/schemas/` — commit these files
- `PlayerRepository.exportSave()` / `importSave()` must include any new persistent state

**DB Migration Template:**
```kotlin
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE skill_sessions ADD COLUMN new_col TEXT NOT NULL DEFAULT ''")
    }
}
// Then add to AppDatabase: .addMigrations(MIGRATION_3_4)
```

**Repository Write Pattern:**
```kotlin
suspend fun updateSomething(value: SomeType) {
    val player = getOrCreatePlayer()
    val flags: PlayerFlags = json.decodeFromString(player.flags)
    val updated = flags.copy(someField = value)
    playerDao.upsert(player.copy(flags = json.encode<PlayerFlags>(updated)))
}
```

---

## 🔔 Notifications & Background Agent — Alarms, Receivers & Scheduling

**Role:** Responsible for session timers, farming patch timers, backup scheduling, buff expiry notifications, and boot-time rescheduling.

**Primary Files:**
- `app/src/main/kotlin/com/fantasyidler/receiver/` — 5 BroadcastReceivers
- `app/src/main/kotlin/com/fantasyidler/notification/SessionNotificationManager.kt`
- `app/src/main/kotlin/com/fantasyidler/repository/SessionRepository.kt`
- `app/src/main/kotlin/com/fantasyidler/repository/BackupScheduler.kt`
- `app/src/main/kotlin/com/fantasyidler/repository/BuffNotificationScheduler.kt`
- `app/src/main/AndroidManifest.xml`

**Key Constraints:**
- Uses `AlarmManager` with `USE_EXACT_ALARM` for precise session timers (not WorkManager)
- New receivers must be registered in `AndroidManifest.xml` with `android:exported="false"`
- `BootReceiver` must reschedule ALL active timers on `BOOT_COMPLETED`
- Notification channels must be created before sending (done in `SessionNotificationManager`)
- Deep-link navigation on notification tap: use `EXTRA_NAVIGATE_TO` in the pending intent; handled in `MainActivity.onNewIntent()`

**Notification Deep-Link Pattern:**
```kotlin
// In SessionNotificationManager
const val EXTRA_NAVIGATE_TO = "navigate_to"
const val NAVIGATE_FARMING  = "farming"

// MainActivity reads it and passes to AppNavigation via pendingNavigateTo state
```

---

## 🌍 Localization Agent — Strings & Translations

**Role:** Responsible for string resources, adding new translatable strings, and supporting existing translations.

**Primary Files:**
- `app/src/main/res/values/strings.xml` (English base)
- `app/src/main/res/values-de/`, `values-fr/`, `values-es/`, `values-tr/`
- `app/src/main/kotlin/com/fantasyidler/util/GameStrings.kt`

**Key Constraints:**
- All user-visible text MUST use `@StringRes` / `stringResource()` in Compose
- Add new keys to `values/strings.xml` first; other languages will get the English fallback until translated
- `GameStrings.kt` has helpers for converting skill/item keys to localized display names — use these instead of hardcoding
- Weblate-compatible: do not change existing key names without updating all language files

---

## 📋 Feature Change Checklist

When implementing any feature change, verify:

- [ ] **UI layer**: New/modified composable + ViewModel added/updated
- [ ] **State**: New flags added to `PlayerFlags` with default values (backward-compatible)
- [ ] **Persistence**: If new DB column needed, write migration + bump version
- [ ] **Game data**: If new JSON content needed, add asset file + update `GameDataRepository`
- [ ] **Export/Import**: If new persistent state, include in `exportSave()` / `importSave()`
- [ ] **Strings**: All user-facing text uses string resources
- [ ] **Navigation**: New screen registered in `Screen.kt` + `AppNavigation.kt`
- [ ] **Boot rescheduling**: If new alarm type, add to `BootReceiver`
- [ ] **Tests**: Run `./gradlew :app:testDebugUnitTest` to confirm nothing broken
