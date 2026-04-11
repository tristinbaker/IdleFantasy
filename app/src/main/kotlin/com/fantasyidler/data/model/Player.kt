package com.fantasyidler.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for the single player profile.
 *
 * Complex fields are stored as JSON strings — identical to the IdleApes Python/SQLite schema.
 * Use [com.fantasyidler.repository.PlayerRepository] to read and write typed domain objects
 * rather than touching these raw columns directly.
 */
@Entity(tableName = "players")
data class Player(
    @PrimaryKey
    val id: Long = 1L,

    /** JSON: Map<String, Int> — skill key → level (1–99) */
    @ColumnInfo(name = "skill_levels")
    val skillLevels: String = "{}",

    /** JSON: Map<String, Long> — skill key → total XP */
    @ColumnInfo(name = "skill_xp")
    val skillXp: String = "{}",

    /** JSON: Map<String, Int> — item key → quantity */
    @ColumnInfo(name = "inventory")
    val inventory: String = "{}",

    /** JSON: Map<String, String?> — equipment slot → item key (null if empty) */
    @ColumnInfo(name = "equipped")
    val equipped: String = "{}",

    /** JSON: PlayerFlags — current HP, active food/arrows/runes, active spell */
    @ColumnInfo(name = "flags")
    val flags: String = "{}",

    /** JSON: List<OwnedPet> */
    @ColumnInfo(name = "pets")
    val pets: String = "[]",

    @ColumnInfo(name = "coins")
    val coins: Long = 0L,
)
