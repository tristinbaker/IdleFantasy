package com.fantasyidler.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One minute of a skill session (60 frames per session).
 * Matches the frame schema emitted by the IdleApes Python simulators.
 */
@Serializable
data class SessionFrame(
    val minute: Int,
    @SerialName("xp_gain")    val xpGain: Int,
    @SerialName("xp_before")  val xpBefore: Long,
    @SerialName("xp_after")   val xpAfter: Long,
    @SerialName("level_before") val levelBefore: Int,
    @SerialName("level_after")  val levelAfter: Int,
    /** Item key → quantity gained this minute */
    val items: Map<String, Int> = emptyMap(),
    @SerialName("leveled_up") val leveledUp: Boolean = false,
    /** Each entry is [newLevel, minuteItOccurred] */
    @SerialName("level_ups")  val levelUps: List<List<Int>> = emptyList(),
    /** Agility only — false when a lap was failed */
    val success: Boolean = true,
    /**
     * Combat only — per-skill XP breakdown for this frame.
     * Empty for gathering/crafting frames (those use xpGain + skillName instead).
     */
    @SerialName("xp_by_skill") val xpBySkill: Map<String, Long> = emptyMap(),
    /** Combat only — total kills this minute (0 for non-combat frames). */
    val kills: Int = 0,
    /** Combat only — enemy key → kills this minute. */
    @SerialName("kills_by_enemy") val killsByEnemy: Map<String, Int> = emptyMap(),
    /** Combat only — true when the player died (underleveled dungeon or boss loss). */
    val died: Boolean = false,
    /** Combat only — food items consumed this minute (item key → quantity eaten). */
    @SerialName("food_consumed") val foodConsumed: Map<String, Int> = emptyMap(),
    /** Combat only — arrows consumed this minute (arrow key → quantity fired). */
    @SerialName("arrows_consumed") val arrowsConsumed: Map<String, Int> = emptyMap(),
    /** Combat only — runes consumed this minute (rune key → quantity cast). */
    @SerialName("runes_consumed") val runesConsumed: Map<String, Int> = emptyMap(),
    /** Raids only — total mercenary damage dealt per tick this minute. */
    @SerialName("ally_hits") val allyHits: List<Int> = emptyList(),
    /** Raids only — mercenaries fallen as of the end of this minute. */
    @SerialName("allies_down") val alliesDown: Int = 0,
    /** Raids only — each mercenary's HP at the end of this minute, in party order. */
    @SerialName("ally_hp_after") val allyHpAfter: List<Int> = emptyList(),
    /** Combat only — the enemy key fought this minute (empty for non-combat frames). */
    @SerialName("enemy_key") val enemyKey: String = "",
    /** Combat only — player HP at the end of this frame (0 = not recorded / non-combat). */
    @SerialName("hp_after") val hpAfter: Int = 0,
    /** Combat only — player's damage dealt each tick (0 = miss), in tick order. */
    @SerialName("player_hits") val playerHits: List<Int> = emptyList(),
    /** Combat only — tick indices where a prestige Double Hit landed, so the log can show it (issue #1567). */
    @SerialName("double_hit_ticks") val doubleHitTicks: List<Int> = emptyList(),
    /** Combat only — enemy's damage dealt each tick (0 = miss), in tick order. */
    @SerialName("enemy_hits") val enemyHits: List<Int> = emptyList(),
    /** Dungeon only — the enemy key spawned after each kill this minute, in kill order.
     *  With [enemyKey] (the minute's first enemy) this reconstructs the exact enemy chain,
     *  since each kill respawns a random type mid-minute; the live banner needs it to
     *  attribute kills and log lines to the right enemy (issue #1690). Empty on frames
     *  simulated before the field existed. */
    @SerialName("spawns_after_kills") val spawnsAfterKills: List<String> = emptyList(),
    /** Combat only — HP restored by eating each tick (0 = no eating), in tick order (issue #1431). */
    @SerialName("player_heals") val playerHeals: List<Int> = emptyList(),
    /** Boss only — combat style used during this session ("melee"|"strength"|"ranged"|"magic"). Empty for non-boss frames. */
    @SerialName("combat_style") val combatStyle: String = "",
    /** Combat only — player's max HP when the session was simulated (0 = not recorded;
     *  display falls back to live stats). Keeps the banner stable if HP levels mid-run (issue #1411). */
    @SerialName("max_hp") val maxHp: Int = 0,
    /** Combat only — food supply captured at simulation time, set on frame 0 only
     *  (empty = not recorded). Keeps the banner stable if gear food changes mid-run (issue #1411). */
    @SerialName("food_at_start") val foodAtStart: Map<String, Int> = emptyMap(),
    /** Combat only — effective "atk"/"str"/"def" the simulation fought with (levels, gear,
     *  potions, blessings, prestige), set on frame 0 only (empty = not recorded; the banner
     *  falls back to gear bonuses). Potions aren't recoverable from live state (issue #1569). */
    @SerialName("stats_at_start") val statsAtStart: Map<String, Int> = emptyMap(),
    /** Dungeon and boss only: combat gear worn when the session started (armor slots plus the
     *  active weapon under "weapon"), set on frame 0 only. Null = not recorded (sessions
     *  started before 2.1.0); empty = nothing equipped. Read by the hidden achievements. */
    @SerialName("gear_at_start") val gearAtStart: Map<String, String>? = null,
    /** Pet Arena only: pet XP earned this minute, keyed by owned pet id. */
    @SerialName("pet_xp") val petXp: Map<String, Long> = emptyMap(),
)
