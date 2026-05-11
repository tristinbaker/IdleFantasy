package com.fantasyidler.data.db.dao

import androidx.room.*
import com.fantasyidler.data.model.SkillSession
import kotlinx.coroutines.flow.Flow

@Dao
interface SkillSessionDao {
    // Returns the most recent session that hasn't been collected yet.
    // Includes sessions marked completed (= ready to collect) by the Worker.
    @Query("SELECT * FROM skill_sessions WHERE user_id = 1 ORDER BY started_at DESC LIMIT 1")
    suspend fun getActiveSession(): SkillSession?

    @Query("SELECT * FROM skill_sessions WHERE user_id = 1 ORDER BY started_at DESC LIMIT 1")
    fun observeActiveSession(): Flow<SkillSession?>

    @Query("SELECT * FROM skill_sessions WHERE session_id = :sessionId")
    suspend fun getSession(sessionId: String): SkillSession?

    @Insert
    suspend fun insert(session: SkillSession)

    @Update
    suspend fun update(session: SkillSession)

    @Query("UPDATE skill_sessions SET completed = 1 WHERE session_id = :sessionId")
    suspend fun markCompleted(sessionId: String)

    @Query("DELETE FROM skill_sessions WHERE session_id = :sessionId")
    suspend fun delete(sessionId: String)

    @Query("DELETE FROM skill_sessions")
    suspend fun deleteAll()

    @Query("SELECT * FROM skill_sessions WHERE completed = 1 AND user_id = 1 ORDER BY started_at DESC LIMIT :limit")
    suspend fun getRecentCompleted(limit: Int = 20): List<SkillSession>

    @Query("SELECT * FROM skill_sessions WHERE completed = 1 AND user_id = 1 ORDER BY started_at ASC")
    suspend fun getAllCompletedSessions(): List<SkillSession>
}
