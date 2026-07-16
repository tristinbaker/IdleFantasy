package com.fantasyidler.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ---------------------------------------------------------------------------
// Deserialised sub-models stored inside Player's JSON columns
// ---------------------------------------------------------------------------

@Serializable
data class PlayerFlags(
    @SerialName("current_hp") val currentHp: Int = 10,
    @SerialName("equipped_food") val equippedFood: Map<String, Int> = emptyMap(),
    @SerialName("equipped_arrows") val equippedArrows: String? = null,
    @SerialName("equipped_runes") val equippedRunes: String? = null,
    @SerialName("active_spell") val activeSpell: String? = null,
    @SerialName("active_weapon_slot") val activeWeaponSlot: String? = null,
    @SerialName("battery_prompt_shown") val batteryPromptShown: Boolean = false,
    /** Epoch ms when the 2× XP boost expires; 0 = not active. */
    @SerialName("xp_boost_expires_at") val xpBoostExpiresAt: Long = 0L,
    @SerialName("character_name") val characterName: String = "",
    @SerialName("character_gender") val characterGender: String = "",
    @SerialName("character_race") val characterRace: String = "",
    /** False until the player completes or skips the character setup prompt. */
    @SerialName("character_setup_done") val characterSetupDone: Boolean = false,
    /** Skin tone index (1-9; valid range depends on race). */
    @SerialName("character_skin_tone") val characterSkinTone: Int = 1,
    /** Hair style index (1-10; 0 = bald/no hair). */
    @SerialName("character_hair_style") val characterHairStyle: Int = 1,
    /** Hair colour letter (a-k). */
    @SerialName("character_hair_color") val characterHairColor: String = "a",
    /** Eye style index (1-12). */
    @SerialName("character_eye_style") val characterEyeStyle: Int = 1,
    /** Beard style index (1-3; 0 = none). */
    @SerialName("character_beard_style") val characterBeardStyle: Int = 0,
    /** Beard colour letter (a-k). */
    @SerialName("character_beard_color") val characterBeardColor: String = "a",
    /** Up to 3 queued sessions to auto-start after the current one completes. */
    @SerialName("session_queue") val sessionQueue: List<QueuedAction> = emptyList(),
    @SerialName("last_seen_version_code") val lastSeenVersionCode: Int = 0,
    /** "dark" | "light" | "system". Defaults to "dark" to preserve existing behaviour. */
    @SerialName("theme_preference") val themePreference: String = "dark",
    /** Number of completed runs per dungeon/boss key. */
    @SerialName("dungeon_runs") val dungeonRuns: Map<String, Int> = emptyMap(),
    /** App-wide font scale multiplier: 1.0 = Normal, 1.25 = Large, 1.5 = Huge. */
    @SerialName("font_scale") val fontScale: Float = 1.0f,
    /** IDs of the 3 active daily quest templates for today. */
    @SerialName("daily_quest_ids") val dailyQuestIds: List<String> = emptyList(),
    /** Progress map: templateId -> count accumulated today. */
    @SerialName("daily_quest_progress") val dailyQuestProgress: Map<String, Int> = emptyMap(),
    /** Template IDs whose reward has already been claimed today. */
    @SerialName("daily_quest_claimed") val dailyQuestClaimed: List<String> = emptyList(),
    /** Epoch ms when today's daily quests were generated (used to detect 6am rollover). */
    @SerialName("daily_quest_generated_at") val dailyQuestGeneratedAt: Long = 0L,

    /** IDs of the 5 active weekly challenge template IDs. */
    @SerialName("weekly_quest_ids") val weeklyQuestIds: List<String> = emptyList(),
    /** Progress map: templateId → count accumulated this week. */
    @SerialName("weekly_quest_progress") val weeklyQuestProgress: Map<String, Int> = emptyMap(),
    /** Template IDs whose individual reward has been claimed. */
    @SerialName("weekly_quest_claimed") val weeklyQuestClaimed: List<String> = emptyList(),
    /** Epoch ms when the current weekly set was generated (used to detect Monday 6am rollover). */
    @SerialName("weekly_quest_generated_at") val weeklyQuestGeneratedAt: Long = 0L,
    /** True if the full weekly bonus chest has been claimed this week. */
    @SerialName("weekly_bonus_claimed") val weeklyBonusClaimed: Boolean = false,

    /** Currently hired worker, or null if none. */
    @SerialName("hired_worker") val hiredWorker: HiredWorker? = null,
    /** Second worker slot (Apprentice / Journeyman / Master), or null if none. */
    @SerialName("hired_worker_2") val hiredWorker2: HiredWorker? = null,
    /** Persists the "hide completed quests" toggle across sessions. */
    @SerialName("hide_completed_quests") val hideCompletedQuests: Boolean = false,
    /** Last-visited Carnival tab index (0=Idle, 1=Active, 2=Prize Shop). */
    @SerialName("carnival_tab") val carnivalTab: Int = 0,
    /** Guild dailies completed this tier: "guild:tier" -> count (guild level is derived from this + step quests). */
    @SerialName("guild_daily_tier_counts") val guildDailyTierCounts: Map<String, Int> = emptyMap(),
    /** Legacy guild reputation totals, no longer written. Kept only so [GuildRepository.migrateLegacyGuildReputation] can back-fill [guildDailyTierCounts] once for existing saves without losing earned guild rank. */
    @SerialName("guild_reputation") val guildReputation: Map<String, Long> = emptyMap(),
    /** Whether [GuildRepository.migrateLegacyGuildReputation] has already run for this save. */
    @SerialName("guild_daily_migration_done") val guildDailyMigrationDone: Boolean = false,
    /** IDs of today's active guild daily request templates. */
    @SerialName("guild_daily_ids") val guildDailyIds: List<String> = emptyList(),
    /** Progress map: templateId → count accumulated today. */
    @SerialName("guild_daily_progress") val guildDailyProgress: Map<String, Int> = emptyMap(),
    /** Template IDs whose reward has already been claimed today. */
    @SerialName("guild_daily_claimed") val guildDailyClaimed: List<String> = emptyList(),
    /** Epoch ms when today's guild dailies were generated (used to detect 6am rollover). */
    @SerialName("guild_daily_generated_at") val guildDailyGeneratedAt: Long = 0L,
    /** Tracks the highest guild level whose quest-progress has been reset on tier-up. guild key → level. */
    @SerialName("guild_quest_reset_levels") val guildQuestResetLevels: Map<String, Int> = emptyMap(),
    /** Notes found per skilling dungeon key (e.g. "copper_caverns" -> 3). */
    @SerialName("skilling_dungeon_notes") val skillingDungeonNotes: Map<String, Int> = emptyMap(),
    /** Combat dungeon keys that have been unlocked via lore completion. */
    @SerialName("unlocked_dungeons") val unlockedDungeons: List<String> = emptyList(),
    /** Key of the active church blessing, or empty if none. */
    @SerialName("active_blessing_key") val activeBlessingKey: String = "",
    /** Epoch ms when the active blessing expires; 0 = not active. */
    @SerialName("active_blessing_expires_at") val activeBlessingExpiresAt: Long = 0L,
    /** Consecutive expedition runs with no note drop per skilling dungeon key; resets to 0 on any note. */
    @SerialName("expedition_pity_runs") val expeditionPityRuns: Map<String, Int> = emptyMap(),
    /** Tree URI string for the automatic backup destination folder; empty = disabled. */
    @SerialName("backup_folder_uri") val backupFolderUri: String = "",
    /** Automatic backup frequency: ""|"hourly"|"daily"|"weekly". */
    @SerialName("backup_frequency") val backupFrequency: String = "",
    /** Currently assigned Slayer task, or null if none. */
    @SerialName("active_slayer_task") val activeSlayerTask: SlayerTask? = null,
    /** Accumulated Slayer points, spent in the Slayer Master shop. */
    @SerialName("slayer_points") val slayerPoints: Int = 0,
    /** Up to 3 pre-assigned future Slayer tasks paid for with bones; first is assigned after the active task finishes. */
    @SerialName("foretelled_tasks") val foretelledTasks: List<SlayerTask> = emptyList(),
    /** Last 10 completed sessions, newest first. */
    @SerialName("recent_sessions") val recentSessions: List<RecentSession> = emptyList(),
    /** Whether to show the recent activity log FAB on the home screen. */
    @SerialName("show_recent_activity_log") val showRecentActivityLog: Boolean = true,
    /** Whether to show the Journal floating action button on the home screen. */
    @SerialName("show_journal_button") val showJournalButton: Boolean = true,
    /** Whether to show the active Seasonal Event banner/card on the home screen. */
    @SerialName("show_seasonal_events") val showSeasonalEvents: Boolean = true,
    /** Whether to show the character sprite viewer on the home screen. */
    @SerialName("show_character_viewer") val showCharacterViewer: Boolean = true,
    /** Whether to show the player stats card (combat level, total level, coins) on the home screen. */
    @SerialName("show_stats_card") val showStatsCard: Boolean = true,
    /** Whether the Town grid (Shop, Inn, Guild, etc.) on the home screen is shown as a collapsible card. */
    @SerialName("collapsible_town_grid") val collapsibleTownGrid: Boolean = true,
    /** Persisted expand/collapse state of the Town grid card, when collapsibleTownGrid is on. */
    @SerialName("town_grid_expanded") val townGridExpanded: Boolean = true,
    /** Profile screen layout: "rail" (sidebar) or "tabs" (horizontal tab bar). */
    @SerialName("profile_layout") val profileLayout: String = "rail",
    /** Prestige level per skill: skill key → 0–3. */
    @SerialName("skill_prestige") val skillPrestige: Map<String, Int> = emptyMap(),
    /** Ash fertilizer per farming patch: patchNumber.toString() → ash item key. */
    @SerialName("farming_fertilizer") val farmingFertilizer: Map<String, String> = emptyMap(),
    /** Last-used potion key for combat sessions; persisted across app restarts. */
    @SerialName("active_potion_key") val activePotionKey: String? = null,
    /** Town building upgrade tiers: building key ("inn"|"guild_hall"|"church") → tier (1-3). Absent = tier 0 (not upgraded). */
    @SerialName("town_building_tiers") val townBuildingTiers: Map<String, Int> = emptyMap(),
    /** Ash item key last used as fertilizer when planting crops; pre-selected in the plant sheet. */
    @SerialName("last_fertilizer_key") val lastFertilizerKey: String? = null,
    /** Lifetime kill count per enemy/boss key; absent = never encountered. */
    @SerialName("enemy_kills") val enemyKills: Map<String, Int> = emptyMap(),
    /** True once the magic bean has been planted; permanently hides it from the seed picker and stops the farming drop from occurring again. */
    @SerialName("magic_bean_planted") val magicBeanPlanted: Boolean = false,
    /** Epoch ms when each carnival active game cooldown expires; 0 = not on cooldown. */
    @SerialName("carnival_ring_toss_cooldown_at") val carnivalRingTossCooldownAt: Long = 0L,
    @SerialName("carnival_hammer_strike_cooldown_at") val carnivalHammerStrikeCooldownAt: Long = 0L,
    @SerialName("carnival_potion_sequence_cooldown_at") val carnivalPotionSequenceCooldownAt: Long = 0L,
    @SerialName("carnival_item_appraisal_cooldown_at") val carnivalItemAppraisalCooldownAt: Long = 0L,
    @SerialName("carnival_shell_game_cooldown_at") val carnivalShellGameCooldownAt: Long = 0L,
    @SerialName("carnival_higher_lower_cooldown_at") val carnivalHigherLowerCooldownAt: Long = 0L,
    /** Per-game carnival difficulty: game key ("ring_toss" etc.) → "normal" or "hard". */
    @SerialName("carnival_difficulties") val carnivalDifficulties: Map<String, String> = emptyMap(),
    /** Dungeon to queue for the active Slayer task when a queue slot next opens; null = nothing pending. */
    @SerialName("pending_slayer_dungeon_key") val pendingSlayerDungeonKey: String? = null,
    @SerialName("pending_slayer_dungeon_name") val pendingSlayerDungeonName: String? = null,
    /** All equipment item keys ever obtained; used by the Armory to show items even after they are sold. */
    @SerialName("seen_item_keys") val seenItemKeys: Set<String> = emptySet(),
    /** Last run stats per dungeon key (food consumed, kills, survived). */
    @SerialName("dungeon_last_run_stats") val dungeonLastRunStats: Map<String, DungeonRunStats> = emptyMap(),
    /** Infinite Tower: current floor of the active run (0 = not started). */
    @SerialName("tower_current_floor") val towerCurrentFloor: Int = 0,
    /** Infinite Tower: highest floor ever reached. */
    @SerialName("tower_best_floor") val towerBestFloor: Int = 0,
    /** Infinite Tower: list of milestone floor numbers already claimed. */
    @SerialName("tower_milestones") val towerMilestonesClaimed: List<Int> = emptyList(),
    /** Infinite Tower: cumulative XP bonus % from milestones. */
    @SerialName("tower_xp_bonus_pct") val towerXpBonusPct: Int = 0,
    /** Infinite Tower: cumulative max HP bonus from milestones. */
    @SerialName("tower_hp_bonus") val towerHpBonus: Int = 0,
    /** Infinite Tower: cumulative coin drop bonus % from milestones. */
    @SerialName("tower_coin_bonus_pct") val towerCoinBonusPct: Int = 0,
    /** Seasonal Events: tokens earned so far per event id, toward that event's token_goal. */
    @SerialName("seasonal_tokens_by_event") val seasonalTokensByEvent: Map<String, Int> = emptyMap(),
    /** Seasonal Events: progress map taskId -> count accumulated since that slot last rotated. */
    @SerialName("seasonal_bounty_progress") val seasonalBountyProgress: Map<String, Int> = emptyMap(),
    /** Seasonal Events: id of the event the current Bounty Board slots were seeded for; reseeded when this changes. */
    @SerialName("seasonal_bounty_event_id") val seasonalBountyEventId: String? = null,
    /** Seasonal Events: the 3 currently active Bounty Board task IDs, index-stable. */
    @SerialName("seasonal_bounty_slots") val seasonalBountySlots: List<String> = emptyList(),
    /** Seasonal Events: slot index (as String) -> epoch ms when a claimed slot rotates in a new task. */
    @SerialName("seasonal_bounty_slot_cooldown") val seasonalBountySlotCooldownUntil: Map<String, Long> = emptyMap(),
    /** Seasonal Events: epoch ms when the minigame cooldown expires; 0 = not on cooldown. */
    @SerialName("seasonal_minigame_cooldown_at") val seasonalMinigameCooldownAt: Long = 0L,
    /** Seasonal Events: persistent player choice — longer reaction window, longer cooldown. */
    @SerialName("seasonal_minigame_easy_mode") val seasonalMinigameEasyMode: Boolean = false,
    /** Seasonal Events: permanent record of every event completed, kept even after the event's data is removed. */
    @SerialName("seasonal_banners_earned") val seasonalBannersEarned: List<SeasonalBannerEarned> = emptyList(),
    /** Free-text notes the player jots down for themselves (e.g. what to queue next). */
    @SerialName("player_notes") val playerNotes: String = "",
    /** Titles: ids of every title ever earned. A title, once unlocked, is never revoked. */
    @SerialName("unlocked_titles") val unlockedTitles: Set<String> = emptySet(),
    /** Titles: id of the currently equipped title, or null for none. */
    @SerialName("equipped_title") val equippedTitle: String? = null,
)

/** A permanent snapshot of a completed Seasonal Event, shown in the Profile Banners tab. */
@Serializable
data class SeasonalBannerEarned(
    @SerialName("event_id")       val eventId: String,
    @SerialName("display_text")   val displayText: String,
    @SerialName("completed_at_ms") val completedAtMs: Long,
    /** Drawable resource name captured at completion time, so the banner still renders after the event's data is removed. */
    @SerialName("banner_icon")    val bannerIcon: String? = null,
    /** Short event name (e.g. "Sunspire Solstice") captured at completion time, used to build this event's Title even after the event's data is removed. */
    @SerialName("event_display_name") val eventDisplayName: String = "",
)

/** Stats saved after each dungeon run; keyed by dungeon name in PlayerFlags. */
@Serializable
data class DungeonRunStats(
    @SerialName("food_consumed") val foodConsumed: Int = 0,
    @SerialName("kill_count") val killCount: Int = 0,
    @SerialName("survived") val survived: Boolean = true,
)

/** A single entry in the recent sessions log. */
@Serializable
data class RecentSession(
    @SerialName("skill_name") val skillName: String,
    @SerialName("activity_display_name") val activityDisplayName: String,
    @SerialName("activity_key") val activityKey: String = "",
)

/** An active Slayer task assigned by the Slayer Master. */
@Serializable
data class SlayerTask(
    @SerialName("enemy_key")       val enemyKey: String,
    @SerialName("display_name")    val displayName: String,
    @SerialName("target_kills")    val targetKills: Int,
    @SerialName("kills_completed") val killsCompleted: Int = 0,
    @SerialName("xp_per_kill")     val xpPerKill: Int,
    @SerialName("task_points")     val taskPoints: Int,
) {
    val isComplete get() = killsCompleted >= targetKills
}

/** A session to be started when the current one completes. */
@Serializable
data class QueuedAction(
    @SerialName("skill_name") val skillName: String,
    @SerialName("activity_key") val activityKey: String,
    @SerialName("skill_display_name") val skillDisplayName: String,
    /** Quantity — number of crafts/items to process. 0 = not applicable. */
    val qty: Int = 0,
    /** Total output items when a recipe yields more than 1 per craft (e.g. iron nails = 15 per craft). 0 = same as qty. */
    @SerialName("output_qty") val outputQty: Int = 0,
    /** Estimated XP this session will grant. 0 = unknown (combat, boss, expedition). */
    @SerialName("estimated_xp_gain") val estimatedXpGain: Long = 0L,
    /** Pre-computed session duration in ms, used to display accurate queue end time. */
    @SerialName("estimated_duration_ms") val estimatedDurationMs: Long = 0L,
    /** Coins to refund if this action is cancelled (mercantile trade route cost). */
    @SerialName("coin_refund") val coinRefund: Long = 0L,
    /** Ash item key used as a catalyst for herblore or runecrafting. Null = no catalyst. */
    @SerialName("catalyst_key") val catalystKey: String? = null,
    /** Quantity of [catalystKey] already consumed for this action, refunded if cancelled before it runs. */
    @SerialName("catalyst_qty") val catalystQty: Int = 0,
    /** Potion item key to consume and apply when this queued combat session starts. */
    @SerialName("potion_key") val potionKey: String? = null,
    /** JSON snapshot of Map<String,String?> (equipped gear) captured at queue time for combat/boss sessions. */
    @SerialName("equipped_snapshot") val equippedSnapshot: String? = null,
    /** Arrow item key captured at queue time. */
    @SerialName("arrows_key") val arrowsKey: String? = null,
    /** Rune item key captured at queue time (magic only). */
    @SerialName("runes_key") val runesKey: String? = null,
    /** Spell name captured at queue time. */
    @SerialName("spell_name") val spellName: String? = null,
    /** Weapon slot key captured at queue time for combat/boss sessions. */
    @SerialName("weapon_slot") val weaponSlot: String? = null,
)

// ---------------------------------------------------------------------------
// Worker system
// ---------------------------------------------------------------------------

@Serializable
enum class WorkerTier {
    LONG_LABORER, APPRENTICE, JOURNEYMAN, MASTER;

    val durationMs: Long get() = when (this) {
        LONG_LABORER -> 8L * 60 * 60_000L
        APPRENTICE   -> 8L * 60 * 60_000L
        JOURNEYMAN   -> 6L * 60 * 60_000L
        MASTER       -> 4L * 60 * 60_000L
    }

    val efficiencyMultiplier: Float get() = when (this) {
        LONG_LABORER -> 0.5f
        APPRENTICE   -> 1.0f
        JOURNEYMAN   -> 1.5f
        MASTER       -> 2.0f
    }

    val hireCost: Long get() = when (this) {
        LONG_LABORER -> 5_000L
        APPRENTICE   -> 10_000L
        JOURNEYMAN   -> 20_000L
        MASTER       -> 50_000L
    }

    /** Per-item time for crafting/prayer/runecrafting sessions, scaled by efficiencyMultiplier
     *  (1 min/item at 1.0x, faster above, slower below) so higher tiers craft faster, not just longer. */
    val craftingPerItemMs: Long get() = (60_000L / efficiencyMultiplier).toLong()

    /** Effective session duration for the crafting estimate display formula (perItemMs * 60). */
    val craftingSessionMs: Long get() = craftingPerItemMs * 60L

    /** Maximum qty for crafting/prayer/runecrafting sessions; LONG_LABORER is uncapped.
     *  = session hours × efficiencyMultiplier × 60, so a full-cap session takes about as long as [durationMs]. */
    val maxCraftQty: Int get() = if (this == LONG_LABORER) Int.MAX_VALUE else (combinedGatheringMultiplier * 60).toInt()

    /** Combined multiplier applied to gathering/combat loot and XP at collect time.
     *  = (session hours) × efficiencyMultiplier */
    val combinedGatheringMultiplier: Float get() =
        (durationMs / (60L * 60_000L)).toFloat() * efficiencyMultiplier
}

@Serializable
data class HiredWorker(
    @SerialName("tier") val tier: WorkerTier,
    @SerialName("daily_name") val dailyName: String,
    @SerialName("session_queue") val sessionQueue: List<QueuedAction> = emptyList(),
)

@Serializable
data class OwnedPet(
    val id: String,
    @SerialName("boost_percent") val boostPercent: Int = 0,
)

/** Portable save file written by export and read by import. */
@Serializable
data class SkillSessionExport(
    @SerialName("session_id")           val sessionId: String,
    @SerialName("skill_name")           val skillName: String,
    @SerialName("activity_key")         val activityKey: String,
    @SerialName("started_at")           val startedAt: Long,
    @SerialName("ends_at")             val endsAt: Long,
    @SerialName("frames")               val frames: String,
    @SerialName("completed")            val completed: Boolean,
    @SerialName("is_worker_session")    val isWorkerSession: Boolean,
    @SerialName("efficiency_multiplier") val efficiencyMultiplier: Float = 1.0f,
    @SerialName("worker_slot")          val workerSlot: Int = if (isWorkerSession) 1 else 0,
)

fun SkillSession.toExport() = SkillSessionExport(
    sessionId            = sessionId,
    skillName            = skillName,
    activityKey          = activityKey,
    startedAt            = startedAt,
    endsAt               = endsAt,
    frames               = frames,
    completed            = completed,
    isWorkerSession      = isWorkerSession,
    efficiencyMultiplier = efficiencyMultiplier,
    workerSlot           = workerSlot,
)

fun SkillSessionExport.toSkillSession() = SkillSession(
    sessionId            = sessionId,
    skillName            = skillName,
    activityKey          = activityKey,
    startedAt            = startedAt,
    endsAt               = endsAt,
    frames               = frames,
    completed            = completed,
    isWorkerSession      = isWorkerSession,
    efficiencyMultiplier = efficiencyMultiplier,
    workerSlot           = workerSlot,
)

@Serializable
data class PlayerExport(
    val skillLevels: String,
    val skillXp: String,
    val inventory: String,
    val equipped: String,
    val flags: String,
    val pets: String,
    val coins: Long,
    val questProgress: List<QuestProgress> = emptyList(),
    val farmingPatches: List<FarmingPatch> = emptyList(),
    val sessions: List<SkillSessionExport> = emptyList(),
    @SerialName("exported_at") val exportedAt: Long = 0L,
)

// ---------------------------------------------------------------------------
// Equipment slot keys — match the Python equipped dict keys exactly
// ---------------------------------------------------------------------------

object EquipSlot {
    // One weapon slot per combat style
    const val WEAPON_ATK    = "weapon_atk"
    const val WEAPON_STR    = "weapon_str"
    const val WEAPON_RANGED = "weapon_ranged"
    const val WEAPON_MAGIC  = "weapon_magic"

    // Legacy single weapon slot — kept for save-game migration only
    const val WEAPON   = "weapon"

    const val HEAD     = "head"
    const val BODY     = "body"
    const val LEGS     = "legs"
    const val BOOTS    = "boots"
    const val CAPE     = "cape"
    const val RING     = "ring"
    const val NECKLACE = "necklace"
    const val SHIELD   = "shield"

    // Gathering tools
    const val PICKAXE     = "pickaxe"
    const val AXE         = "axe"
    const val FISHING_ROD = "fishing_rod"
    const val HOE         = "hoe"

    // Crafting/skilling tools
    const val HAMMER         = "hammer"
    const val TINDERBOX      = "tinderbox"
    const val GRAPPLING_HOOK = "grappling_hook"
    const val FRYING_PAN     = "frying_pan"
    const val LOCKPICK       = "lockpick"

    val WEAPON_SLOTS = listOf(WEAPON_ATK, WEAPON_STR, WEAPON_RANGED, WEAPON_MAGIC)
    val ARMOR_SLOTS  = listOf(HEAD, BODY, LEGS, BOOTS, CAPE, RING, NECKLACE, SHIELD)
    val COMBAT_SLOTS = WEAPON_SLOTS + ARMOR_SLOTS
    val TOOL_SLOTS   = listOf(PICKAXE, AXE, FISHING_ROD, HOE, HAMMER, TINDERBOX, GRAPPLING_HOOK, FRYING_PAN, LOCKPICK)
    val ALL          = COMBAT_SLOTS + TOOL_SLOTS

    /** Returns the combat style string that belongs in a given weapon slot, or null for non-weapon slots. */
    fun combatStyleForSlot(slot: String): String? = when (slot) {
        WEAPON_ATK    -> "attack"
        WEAPON_STR    -> "strength"
        WEAPON_RANGED -> "ranged"
        WEAPON_MAGIC  -> "magic"
        else          -> null
    }
}

// ---------------------------------------------------------------------------
// Canonical skill keys — must match keys in skill_levels / skill_xp JSON
// ---------------------------------------------------------------------------

object Skills {
    // Gathering
    const val MINING      = "mining"
    const val FISHING     = "fishing"
    const val WOODCUTTING = "woodcutting"
    const val FARMING     = "farming"
    const val FIREMAKING  = "firemaking"
    const val AGILITY     = "agility"

    // Crafting
    const val SMITHING      = "smithing"
    const val COOKING       = "cooking"
    const val FLETCHING     = "fletching"
    const val CRAFTING      = "crafting"
    const val RUNECRAFTING  = "runecrafting"
    const val HERBLORE      = "herblore"
    const val CONSTRUCTION  = "construction"

    // Gathering / Stealth
    const val THIEVING      = "thieving"

    // Combat
    const val ATTACK    = "attack"
    const val STRENGTH  = "strength"
    const val DEFENSE   = "defense"
    const val RANGED    = "ranged"
    const val MAGIC     = "magic"
    const val HITPOINTS = "hitpoints"
    const val PRAYER      = "prayer"
    const val MERCANTILE  = "mercantile"
    const val SLAYER      = "slayer"

    val GATHERING = listOf(MINING, FISHING, WOODCUTTING, FARMING, AGILITY, THIEVING)
    val CRAFTING_SKILLS = listOf(SMITHING, COOKING, FLETCHING, CRAFTING, FIREMAKING, RUNECRAFTING, HERBLORE, CONSTRUCTION)
    val COMBAT = listOf(ATTACK, STRENGTH, DEFENSE, RANGED, MAGIC, HITPOINTS, PRAYER)
    val SUPPORT = listOf(PRAYER, MERCANTILE)
    val ALL = GATHERING + CRAFTING_SKILLS + COMBAT + listOf(MERCANTILE, SLAYER)

    val DEFAULT_LEVELS: Map<String, Int> = ALL.associateWith { 1 }
    val DEFAULT_XP: Map<String, Long> = ALL.associateWith { 0L }
}
