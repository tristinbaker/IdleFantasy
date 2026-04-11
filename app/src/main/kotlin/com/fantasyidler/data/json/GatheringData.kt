package com.fantasyidler.data.json

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ---------------------------------------------------------------------------
// Ore data (ores.json)
// ---------------------------------------------------------------------------

@Serializable
data class OreData(
    @SerialName("display_name") val displayName: String,
    @SerialName("level_required") val levelRequired: Int,
    @SerialName("xp_per_ore") val xpPerOre: Int,
    @SerialName("time_per_ore") val timePerOre: Int,
)

// ---------------------------------------------------------------------------
// Tree data (trees.json)
// ---------------------------------------------------------------------------

@Serializable
data class TreeData(
    @SerialName("display_name") val displayName: String,
    @SerialName("log_name") val logName: String,
    @SerialName("log_display_name") val logDisplayName: String,
    @SerialName("level_required") val levelRequired: Int,
    @SerialName("xp_per_log") val xpPerLog: Int,
    @SerialName("time_per_log") val timePerLog: Int,
)

// ---------------------------------------------------------------------------
// Gem data (gems.json) — bonus drops while mining
// ---------------------------------------------------------------------------

@Serializable
data class GemData(
    @SerialName("display_name") val displayName: String,
    val description: String,
    @SerialName("drop_rate") val dropRate: Double,
    val rarity: String,
)

// ---------------------------------------------------------------------------
// Gathering skill data (skills/fishing.json, skills/mining.json, etc.)
// These have xp_ranges and drop_tables unlike simple skills (agility, etc.)
// ---------------------------------------------------------------------------

@Serializable
data class GatheringSkillData(
    val name: String,
    @SerialName("display_name") val displayName: String,
    val description: String,
    @SerialName("max_level") val maxLevel: Int = 99,
    /** Keyed by minimum level threshold (as String, e.g. "1", "15", "30") */
    @SerialName("xp_ranges") val xpRanges: Map<String, XpRange> = emptyMap(),
    /** Keyed by minimum level threshold; each value is a table of drop entries */
    @SerialName("drop_tables") val dropTables: Map<String, List<SkillDropEntry>> = emptyMap(),
)

@Serializable
data class XpRange(val min: Int, val max: Int)

/** Drop table entry used in skill JSON files (chance only, no quantity range). */
@Serializable
data class SkillDropEntry(
    val item: String,
    val chance: Double,
)

// ---------------------------------------------------------------------------
// Agility course data (agility_courses.json)
// ---------------------------------------------------------------------------

@Serializable
data class AgilityCourseData(
    val name: String,
    @SerialName("display_name")   val displayName: String,
    @SerialName("level_required") val levelRequired: Int,
    @SerialName("xp_per_success") val xpPerSuccess: Int,
    val description: String = "",
)

// ---------------------------------------------------------------------------
// Log data (logs.json) — used for Firemaking
// ---------------------------------------------------------------------------

@Serializable
data class LogData(
    @SerialName("display_name")   val displayName: String,
    @SerialName("level_required") val levelRequired: Int,
    @SerialName("xp_per_log")     val xpPerLog: Int,
    @SerialName("time_per_log")   val timePerLog: Int = 1,
)
