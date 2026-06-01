package com.fantasyidler.repository

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.fantasyidler.data.db.dao.SkillSessionDao
import com.fantasyidler.data.model.SkillSession
import com.fantasyidler.receiver.SessionAlarmReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepository @Inject constructor(
    private val sessionDao: SkillSessionDao,
    @ApplicationContext private val context: Context,
) {
    val activeSessionFlow: Flow<SkillSession?> = sessionDao.observeActiveSession()
    val completedCountFlow: Flow<Int> = sessionDao.observeCompletedCount()
    val workerCompletedCountFlow: Flow<Int> = sessionDao.observeWorkerCompletedCount()

    fun activeWorkerSessionFlow(slot: Int): Flow<SkillSession?> =
        sessionDao.observeActiveWorkerSession(slot)

    suspend fun getActiveSession(): SkillSession? = sessionDao.getActiveSession()

    suspend fun getActiveWorkerSession(slot: Int): SkillSession? =
        sessionDao.getActiveWorkerSession(slot)

    suspend fun getAllCompletedWorkerSessions(slot: Int): List<SkillSession> =
        sessionDao.getAllCompletedWorkerSessions(slot)

    suspend fun deleteAllWorkerSessions(slot: Int) = sessionDao.deleteAllWorkerSessions(slot)
    suspend fun deleteAllWorkerSessions() = sessionDao.deleteAllWorkerSessions()

    /**
     * Persist a new session and schedule an AlarmManager alarm for completion.
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
        val alarmAt = if (alarmOffsetMs != null) now + alarmOffsetMs else session.endsAt
        scheduleAlarm(session.sessionId, alarmAt, skillDisplayName)
        return session
    }

    suspend fun startWorkerSession(
        workerSlot: Int,
        skillName: String,
        activityKey: String,
        frames: String,
        durationMs: Long,
        skillDisplayName: String,
        efficiencyMultiplier: Float,
    ): SkillSession {
        val now = System.currentTimeMillis()
        val session = SkillSession(
            sessionId            = UUID.randomUUID().toString(),
            skillName            = skillName,
            startedAt            = now,
            endsAt               = now + durationMs,
            frames               = frames,
            activityKey          = activityKey,
            isWorkerSession      = true,
            efficiencyMultiplier = efficiencyMultiplier,
            workerSlot           = workerSlot,
        )
        sessionDao.insert(session)
        scheduleAlarm(session.sessionId, session.endsAt, skillDisplayName)
        return session
    }

    suspend fun markCompleted(sessionId: String) {
        sessionDao.markCompleted(sessionId)
    }

    /**
     * Called on boot or app open to recover from a lost alarm.
     * - If the active session has already passed its end time, marks it complete and
     *   advances the queue via [starter].
     * - If it's still running, reschedules the alarm so it fires at the correct time.
     */
    suspend fun recoverActiveSession(starter: QueuedSessionStarter) {
        val session = getActiveSession() ?: run {
            // No session in DB at all — try to drain any leftover queue items.
            starter.startNextQueued()
            return
        }
        if (session.completed) {
            // Session already completed (alarm fired). Advance the queue in case the
            // alarm + app-open race left it stuck with remaining items.
            starter.startNextQueued()
            return
        }
        val now = System.currentTimeMillis()
        if (now >= session.endsAt) {
            markCompleted(session.sessionId)
            starter.startNextQueued()
        } else {
            scheduleAlarm(session.sessionId, session.endsAt, session.skillName)
        }
    }

    suspend fun recoverActiveWorkerSession(slot: Int, workerStarter: WorkerQueuedSessionStarter) {
        val session = getActiveWorkerSession(slot) ?: run {
            workerStarter.startNextQueued(slot)
            return
        }
        if (session.completed) {
            workerStarter.startNextQueued(slot)
            return
        }
        val now = System.currentTimeMillis()
        if (now >= session.endsAt) {
            markCompleted(session.sessionId)
            workerStarter.startNextQueued(slot)
        } else {
            scheduleAlarm(session.sessionId, session.endsAt, session.skillName)
        }
    }

    suspend fun getSession(sessionId: String): SkillSession? = sessionDao.getSession(sessionId)

    suspend fun abandonSession(sessionId: String) {
        cancelAlarm(sessionId)
        sessionDao.delete(sessionId)
    }

    /** Delete a completed session after rewards have been applied. */
    suspend fun deleteSession(sessionId: String) {
        sessionDao.delete(sessionId)
    }

    suspend fun deleteAllSessions() = sessionDao.deleteAll()

    suspend fun insertSession(session: SkillSession) = sessionDao.insert(session)

    suspend fun getRecentCompleted(limit: Int = 20): List<SkillSession> =
        sessionDao.getRecentCompleted(limit)

    suspend fun getAllCompletedSessions(): List<SkillSession> =
        sessionDao.getAllCompletedSessions()

    suspend fun getOldestCompletedSession(): SkillSession? =
        sessionDao.getOldestCompletedSession()

    // ------------------------------------------------------------------

    private fun alarmIntent(sessionId: String, skillDisplayName: String): PendingIntent {
        val intent = Intent(context, SessionAlarmReceiver::class.java).apply {
            putExtra(SessionAlarmReceiver.KEY_SESSION_ID, sessionId)
            putExtra(SessionAlarmReceiver.KEY_SKILL_DISPLAY_NAME, skillDisplayName)
        }
        return PendingIntent.getBroadcast(
            context,
            sessionId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun cancelIntent(sessionId: String): PendingIntent {
        val intent = Intent(context, SessionAlarmReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            sessionId.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun scheduleAlarm(sessionId: String, endsAt: Long, skillDisplayName: String) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = alarmIntent(sessionId, skillDisplayName)
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAt, pi)
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAt, pi)
        }
    }

    private fun cancelAlarm(sessionId: String) {
        val am      = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = cancelIntent(sessionId)
        am.cancel(pending)
    }

    companion object {
        const val SESSION_DURATION_MS = 60L * 60L * 1_000L  // 1 hour
    }
}
