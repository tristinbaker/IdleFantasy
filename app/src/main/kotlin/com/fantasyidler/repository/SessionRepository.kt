package com.fantasyidler.repository

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.fantasyidler.data.db.dao.SkillSessionDao
import com.fantasyidler.data.model.SkillSession
import com.fantasyidler.worker.SessionWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepository @Inject constructor(
    private val sessionDao: SkillSessionDao,
    @ApplicationContext private val context: Context,
) {
    val activeSessionFlow: Flow<SkillSession?> = sessionDao.observeActiveSession()

    suspend fun getActiveSession(): SkillSession? = sessionDao.getActiveSession()

    /**
     * Persist a new session and schedule its WorkManager completion task.
     *
     * @param skillName        canonical skill key, e.g. "mining"
     * @param activityKey      sub-activity key, e.g. "iron_ore" or "dark_cave"
     * @param frames           pre-serialised JSON of List<SessionFrame>
     * @param durationMs       wall-clock duration (already reduced by agility bonus)
     * @param skillDisplayName localised skill name forwarded to the notification
     */
    suspend fun startSession(
        skillName: String,
        activityKey: String,
        frames: String,
        durationMs: Long = SESSION_DURATION_MS,
        skillDisplayName: String,
    ): SkillSession {
        val now = System.currentTimeMillis()
        val session = SkillSession(
            sessionId   = UUID.randomUUID().toString(),
            skillName   = skillName,
            startedAt   = now,
            endsAt      = now + durationMs,
            frames      = frames,
            activityKey = activityKey,
        )
        sessionDao.insert(session)
        scheduleCompletionWorker(session.sessionId, durationMs, skillDisplayName)
        return session
    }

    suspend fun markCompleted(sessionId: String) = sessionDao.markCompleted(sessionId)

    suspend fun getSession(sessionId: String): SkillSession? = sessionDao.getSession(sessionId)

    suspend fun abandonSession(sessionId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(SessionWorker.workName(sessionId))
        sessionDao.delete(sessionId)
    }

    /** Delete a completed session after rewards have been applied. */
    suspend fun deleteSession(sessionId: String) = sessionDao.delete(sessionId)

    suspend fun getRecentCompleted(limit: Int = 20): List<SkillSession> =
        sessionDao.getRecentCompleted(limit)

    // ------------------------------------------------------------------

    private fun scheduleCompletionWorker(sessionId: String, durationMs: Long, skillDisplayName: String) {
        val data = workDataOf(
            SessionWorker.KEY_SESSION_ID        to sessionId,
            SessionWorker.KEY_SKILL_DISPLAY_NAME to skillDisplayName,
        )
        val request = OneTimeWorkRequestBuilder<SessionWorker>()
            .setInputData(data)
            .setInitialDelay(durationMs, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            SessionWorker.workName(sessionId),
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    companion object {
        const val SESSION_DURATION_MS = 60L * 60L * 1_000L  // 1 hour
    }
}
