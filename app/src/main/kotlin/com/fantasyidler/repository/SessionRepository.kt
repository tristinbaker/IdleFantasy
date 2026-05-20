package com.fantasyidler.repository

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.fantasyidler.data.db.dao.SkillSessionDao
import com.fantasyidler.data.model.SkillSession
import com.fantasyidler.worker.SessionCompletionWorker
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
    val completedCountFlow: Flow<Int> = sessionDao.observeCompletedCount()
    val completedSessionsFlow: Flow<List<SkillSession>> = sessionDao.observeCompletedSessions()

    suspend fun getActiveSession(): SkillSession? = sessionDao.getActiveSession()

    /**
     * Persist a new session and enqueue a [SessionCompletionWorker] to fire when
     * it ends. The worker survives reboot and Doze; replaces the legacy
     * AlarmManager + BroadcastReceiver path.
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
        alarmOffsetMs: Long? = null,
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
        val fireAt = if (alarmOffsetMs != null) now + alarmOffsetMs else session.endsAt
        enqueueCompletionWork(session.sessionId, fireAt, skillDisplayName)
        return session
    }

    suspend fun markCompleted(sessionId: String) = sessionDao.markCompleted(sessionId)

    suspend fun getSession(sessionId: String): SkillSession? = sessionDao.getSession(sessionId)

    suspend fun abandonSession(sessionId: String) {
        cancelCompletionWork(sessionId)
        sessionDao.delete(sessionId)
    }

    /** Delete a completed session after rewards have been applied. */
    suspend fun deleteSession(sessionId: String) = sessionDao.delete(sessionId)

    suspend fun deleteAllSessions() = sessionDao.deleteAll()

    suspend fun getRecentCompleted(limit: Int = 20): List<SkillSession> =
        sessionDao.getRecentCompleted(limit)

    suspend fun getAllCompletedSessions(): List<SkillSession> =
        sessionDao.getAllCompletedSessions()

    suspend fun getOldestCompletedSession(): SkillSession? =
        sessionDao.getOldestCompletedSession()

    /**
     * Re-enqueue completion work for an active, not-yet-completed session.
     * Called once on app startup so a session scheduled before the
     * AlarmManager → WorkManager migration doesn't lose its completion.
     * The session's display name is unknown at this point — the notification
     * falls back to an empty string, which is acceptable for the upgrade case.
     */
    suspend fun rescheduleActiveSessionIfAny() {
        val active = sessionDao.getActiveSession() ?: return
        if (active.completed) return
        enqueueCompletionWork(active.sessionId, active.endsAt, skillDisplayName = "")
    }

    // ------------------------------------------------------------------

    private fun enqueueCompletionWork(sessionId: String, fireAt: Long, skillDisplayName: String) {
        val delayMs = (fireAt - System.currentTimeMillis()).coerceAtLeast(0L)
        val data = Data.Builder()
            .putString(SessionCompletionWorker.KEY_SESSION_ID, sessionId)
            .putString(SessionCompletionWorker.KEY_SKILL_DISPLAY_NAME, skillDisplayName)
            .build()
        val request = OneTimeWorkRequestBuilder<SessionCompletionWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .addTag(WORK_TAG_SESSION)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            SessionCompletionWorker.uniqueWorkName(sessionId),
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    private fun cancelCompletionWork(sessionId: String) {
        WorkManager.getInstance(context)
            .cancelUniqueWork(SessionCompletionWorker.uniqueWorkName(sessionId))
    }

    companion object {
        const val SESSION_DURATION_MS = 60L * 60L * 1_000L  // 1 hour
        private const val WORK_TAG_SESSION = "session_completion"
    }
}
