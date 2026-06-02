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
    /** Currently hired worker, or null if none. */
    @SerialName("hired_worker") val hiredWorker: HiredWorker? = null,
    /** Second worker slot (Apprentice / Journeyman / Master), or null if none. */
    @SerialName("hired_worker_2") val hiredWorker2: HiredWorker? = null,
    /** Persists the "hide completed quests" toggle across sessions. */
    @SerialName("hide_completed_quests") val hideCompletedQuests: Boolean = false,
    /** Guild reputation totals: guild key → total rep earned (guild level is derived from this). */
    @SerialName("guild_reputation") val guildReputation: Map<String, Long> = emptyMap(),
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
    /** Last 10 completed sessions, newest first. */
    @SerialName("recent_sessions") val recentSessions: List<RecentSession> = emptyList(),
    /** Whether to show the recent activity log FAB on the home screen. */
    @SerialName("show_recent_activity_log") val showRecentActivityLog: Boolean = true,
    /** Prestige level per skill: skill key → 0–3. */
    @SerialName("skill_prestige") val skillPrestige: Map<String, Int> = emptyMap(),
)

/** A single entry in the recent sessions log. */
@Serializable
data class RecentSession(
    @SerialName("skill_name") val skillName: String,
    @SerialName("activity_display_name") val activityDisplayName: String,
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
    /** Quantity — used for prayer (bones) and runecrafting (essence). 0 = not applicable. */
    val qty: Int = 0,
    /** Pre-computed session duration in ms, used to display accurate queue end time. */
    @SerialName("estimated_duration_ms") val estimatedDurationMs: Long = 0L,
    /** Coins to refund if this action is cancelled (mercantile trade route cost). */
    @SerialName("coin_refund") val coinRefund: Long = 0L,
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
        JOURNEYMAN   -> 1.25f
        MASTER       -> 2.0f
    }

    val hireCost: Long get() = when (this) {
        LONG_LABORER -> 5_000L
        APPRENTICE   -> 10_000L
        JOURNEYMAN   -> 20_000L
        MASTER       -> 50_000L
    }

    /** Per-item time for crafting/prayer/runecrafting sessions.
     *  Long Laborer is 2x the player's base rate; all others match the player (1 min/item). */
    val craftingPerItemMs: Long get() = when (this) {
        LONG_LABORER -> 2L * 60_000L
        else         -> 60_000L
    }

    /** Effective session duration for the crafting estimate display formula (perItemMs * 60). */
    val craftingSessionMs: Long get() = craftingPerItemMs * 60L

    /** Maximum qty for crafting/prayer/runecrafting sessions; LONG_LABORER is uncapped. */
    val maxCraftQty: Int get() = when (this) {
        LONG_LABORER -> Int.MAX_VALUE
        APPRENTICE   -> 480
        JOURNEYMAN   -> 360
        MASTER       -> 240
    }

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

    val WEAPON_SLOTS = listOf(WEAPON_ATK, WEAPON_STR, WEAPON_RANGED, WEAPON_MAGIC)
    val ARMOR_SLOTS  = listOf(HEAD, BODY, LEGS, BOOTS, CAPE, RING, NECKLACE, SHIELD)
    val COMBAT_SLOTS = WEAPON_SLOTS + ARMOR_SLOTS
    val TOOL_SLOTS   = listOf(PICKAXE, AXE, FISHING_ROD, HOE)
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
    const val SMITHING     = "smithing"
    const val COOKING      = "cooking"
    const val FLETCHING    = "fletching"
    const val CRAFTING     = "crafting"
    const val RUNECRAFTING = "runecrafting"
    const val HERBLORE     = "herblore"

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

    val GATHERING = listOf(MINING, FISHING, WOODCUTTING, FARMING, FIREMAKING, AGILITY)
    val CRAFTING_SKILLS = listOf(SMITHING, COOKING, FLETCHING, CRAFTING, RUNECRAFTING, HERBLORE)
    val COMBAT = listOf(ATTACK, STRENGTH, DEFENSE, RANGED, MAGIC, HITPOINTS, PRAYER)
    val SUPPORT = listOf(PRAYER, MERCANTILE)
    val ALL = GATHERING + CRAFTING_SKILLS + COMBAT + listOf(MERCANTILE, SLAYER)

    val DEFAULT_LEVELS: Map<String, Int> = ALL.associateWith { 1 }
    val DEFAULT_XP: Map<String, Long> = ALL.associateWith { 0L }
}
