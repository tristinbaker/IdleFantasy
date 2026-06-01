package com.fantasyidler.data.db.dao

import androidx.room.*
import com.fantasyidler.data.model.SkillSession
import kotlinx.coroutines.flow.Flow

@Dao
interface SkillSessionDao {
    // ── Player sessions (worker_slot = 0) ───────────────────────────────────

    @Query("SELECT * FROM skill_sessions WHERE user_id = 1 AND worker_slot = 0 ORDER BY started_at DESC LIMIT 1")
    suspend fun getActiveSession(): SkillSession?

    @Query("SELECT * FROM skill_sessions WHERE user_id = 1 AND worker_slot = 0 ORDER BY started_at DESC LIMIT 1")
    fun observeActiveSession(): Flow<SkillSession?>

    @Query("SELECT * FROM skill_sessions WHERE completed = 1 AND user_id = 1 AND worker_slot = 0 ORDER BY started_at DESC LIMIT :limit")
    suspend fun getRecentCompleted(limit: Int = 20): List<SkillSession>

    @Query("SELECT * FROM skill_sessions WHERE completed = 1 AND user_id = 1 AND worker_slot = 0 ORDER BY started_at ASC")
    suspend fun getAllCompletedSessions(): List<SkillSession>

    @Query("SELECT * FROM skill_sessions WHERE completed = 1 AND user_id = 1 AND worker_slot = 0 ORDER BY started_at ASC LIMIT 1")
    suspend fun getOldestCompletedSession(): SkillSession?

    @Query("SELECT COUNT(*) FROM skill_sessions WHERE completed = 1 AND user_id = 1 AND worker_slot = 0")
    fun observeCompletedCount(): Flow<Int>

    // ── Worker sessions — slot-parameterized ────────────────────────────────

    @Query("SELECT * FROM skill_sessions WHERE user_id = 1 AND worker_slot = :slot ORDER BY started_at DESC LIMIT 1")
    suspend fun getActiveWorkerSession(slot: Int): SkillSession?

    @Query("SELECT * FROM skill_sessions WHERE user_id = 1 AND worker_slot = :slot ORDER BY started_at DESC LIMIT 1")
    fun observeActiveWorkerSession(slot: Int): Flow<SkillSession?>

    @Query("SELECT * FROM skill_sessions WHERE completed = 1 AND user_id = 1 AND worker_slot = :slot ORDER BY started_at ASC")
    suspend fun getAllCompletedWorkerSessions(slot: Int): List<SkillSession>

    @Query("SELECT COUNT(*) FROM skill_sessions WHERE completed = 1 AND user_id = 1 AND worker_slot > 0")
    fun observeWorkerCompletedCount(): Flow<Int>

    @Query("DELETE FROM skill_sessions WHERE user_id = 1 AND worker_slot = :slot")
    suspend fun deleteAllWorkerSessions(slot: Int)

    @Query("DELETE FROM skill_sessions WHERE user_id = 1 AND worker_slot > 0")
    suspend fun deleteAllWorkerSessions()

    // ── Shared ───────────────────────────────────────────────────────────────

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
}
