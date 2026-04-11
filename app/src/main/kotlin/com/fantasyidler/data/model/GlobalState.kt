package com.fantasyidler.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Key/value store for miscellaneous game state (e.g. daily dig depth, cooldowns). */
@Entity(tableName = "global_state")
data class GlobalState(
    @PrimaryKey val key: String,
    val value: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** Well-known global state keys. */
object GlobalStateKey {
    const val HOLE_DEPTH          = "hole_depth"
    const val LAST_DIG_TIME       = "last_dig_time"
    const val ONBOARDING_COMPLETE = "onboarding_complete"
}
