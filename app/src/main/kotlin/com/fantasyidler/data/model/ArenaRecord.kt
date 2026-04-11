package com.fantasyidler.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Result of a solo arena fight (replaces the multiplayer duel system). */
@Entity(tableName = "arena_records")
data class ArenaRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "opponent_name")
    val opponentName: String,

    val won: Boolean,

    /** JSON: List<CombatEvent> — full turn-by-turn combat log */
    @ColumnInfo(name = "combat_log")
    val combatLog: String = "[]",

    @ColumnInfo(name = "completed_at")
    val completedAt: Long,
)
