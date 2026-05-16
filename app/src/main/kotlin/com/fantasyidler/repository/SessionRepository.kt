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

    suspend fun getActiveSession(): SkillSession? = sessionDao.getActiveSession()

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

    suspend fun markCompleted(sessionId: String) = sessionDao.markCompleted(sessionId)

    suspend fun getSession(sessionId: String): SkillSession? = sessionDao.getSession(sessionId)

    suspend fun abandonSession(sessionId: String) {
        cancelAlarm(sessionId)
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
        am.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            endsAt,
            alarmIntent(sessionId, skillDisplayName),
        )
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
