package com.fantasyidler.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents one 1-hour activity session (skill training, dungeon, crafting run, etc.).
 * The [frames] column holds a JSON array of 60 [SessionFrame] objects — simulated in full
 * when the session starts and stored immediately so the device never needs to be online.
 */
@Entity(tableName = "skill_sessions")
data class SkillSession(
    @PrimaryKey
    @ColumnInfo(name = "session_id")
    val sessionId: String,

    @ColumnInfo(name = "user_id")
    val playerId: Long = 1L,

    /** Canonical skill key, e.g. "mining", "dungeon", "smithing" */
    @ColumnInfo(name = "skill_name")
    val skillName: String,

    /** Epoch milliseconds when the session was started */
    @ColumnInfo(name = "started_at")
    val startedAt: Long,

    /** Epoch milliseconds when results become collectable (startedAt + 1 hour) */
    @ColumnInfo(name = "ends_at")
    val endsAt: Long,

    /** JSON: List<SessionFrame> — 60 pre-simulated frames */
    @ColumnInfo(name = "data")
    val frames: String = "[]",

    @ColumnInfo(name = "completed")
    val completed: Boolean = false,

    /** Sub-activity key, e.g. ore type "iron_ore" or dungeon "dark_cave" */
    @ColumnInfo(name = "activity_key")
    val activityKey: String = "",

    /** True when this session belongs to the hired worker (not the player). */
    @ColumnInfo(name = "is_worker_session", defaultValue = "0")
    val isWorkerSession: Boolean = false,

    /** Multiplier applied to XP and items at collect time (>1.0 for worker sessions). */
    @ColumnInfo(name = "efficiency_multiplier", defaultValue = "1.0")
    val efficiencyMultiplier: Float = 1.0f,

    /**
     * Which worker slot owns this session: 0 = player, 1 = long laborer slot, 2 = other worker slot.
     * Added in DB v3; replaces [isWorkerSession] for multi-slot routing.
     */
    @ColumnInfo(name = "worker_slot", defaultValue = "0")
    val workerSlot: Int = 0,

    /** Catalyst item consumed at start (e.g. rune ash, herblore enhancer), so it can be refunded on abandon. */
    @ColumnInfo(name = "catalyst_key")
    val catalystKey: String? = null,

    @ColumnInfo(name = "catalyst_qty", defaultValue = "0")
    val catalystQty: Int = 0,

    /**
     * The player's combat level (for boss/combat/tower) or the relevant skill's level
     * (otherwise) at the moment this session started. Compared against the current level
     * at collect time to detect a mid-session prestige reset — not a difficulty/unlock gate.
     */
    @ColumnInfo(name = "level_at_start", defaultValue = "0")
    val levelAtStart: Int = 0,
)
