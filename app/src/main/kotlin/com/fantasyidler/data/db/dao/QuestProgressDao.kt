package com.fantasyidler.data.db.dao

import androidx.room.*
import com.fantasyidler.data.model.QuestProgress
import kotlinx.coroutines.flow.Flow

@Dao
interface QuestProgressDao {
    @Query("SELECT * FROM quest_progress WHERE quest_id = :questId")
    suspend fun getQuestProgress(questId: String): QuestProgress?

    @Query("SELECT * FROM quest_progress")
    suspend fun getAllProgress(): List<QuestProgress>

    @Query("SELECT * FROM quest_progress")
    fun observeAllProgress(): Flow<List<QuestProgress>>

    @Query("SELECT * FROM quest_progress WHERE completed = 1")
    suspend fun getCompletedQuests(): List<QuestProgress>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: QuestProgress)
}
