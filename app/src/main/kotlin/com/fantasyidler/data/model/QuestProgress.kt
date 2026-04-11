package com.fantasyidler.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "quest_progress")
data class QuestProgress(
    @PrimaryKey
    @ColumnInfo(name = "quest_id")
    val questId: String,

    val progress: Int = 0,
    val completed: Boolean = false,

    @ColumnInfo(name = "completed_at")
    val completedAt: Long? = null,
)
