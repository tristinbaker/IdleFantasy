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
)
