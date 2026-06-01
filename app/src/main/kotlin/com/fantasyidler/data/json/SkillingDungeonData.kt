package com.fantasyidler.data.json

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SkillingDungeonData(
    val name: String,
    @SerialName("display_name") val displayName: String,
    val description: String,
    /** "mining" | "woodcutting" | "fishing" */
    val skill: String,
    @SerialName("level_required") val levelRequired: Int,
    /** Combat dungeon key that must be in unlockedDungeons before this skilling dungeon is accessible. Null for tier-1 dungeons. */
    @SerialName("requires_previous_unlock") val requiresPreviousUnlock: String? = null,
    /** Keyed by minimum level threshold as String, e.g. "1", "40". */
    @SerialName("xp_ranges") val xpRanges: Map<String, XpRange>,
    /** Keyed by minimum level threshold; each value is a drop table. */
    @SerialName("drop_tables") val dropTables: Map<String, List<SkillDropEntry>>,
    @SerialName("note_chance_per_frame") val noteChancePerFrame: Double = 0.015,
    @SerialName("note_threshold") val noteThreshold: Int = 5,
    /** Ordered lore texts; length >= noteThreshold. Revealed one at a time as notes are found. */
    @SerialName("note_texts") val noteTexts: List<String>,
    /** Key of the combat dungeon that is unlocked when noteThreshold is reached. */
    @SerialName("unlock_dungeon") val unlockDungeon: String,
    @SerialName("unlock_message") val unlockMessage: String,
)
