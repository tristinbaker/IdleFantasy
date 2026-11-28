package com.fantasyidler.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.fantasyidler.R
import com.fantasyidler.data.model.SkillSession
import com.fantasyidler.notification.SessionNotificationManager
import com.fantasyidler.util.GameStrings
import com.fantasyidler.repository.QueuedSessionStarter
import com.fantasyidler.repository.SessionRepository
import com.fantasyidler.repository.PetArenaRepository
import com.fantasyidler.repository.WorkerQueuedSessionStarter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SessionAlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var sessionRepository: SessionRepository
    @Inject lateinit var notificationManager: SessionNotificationManager
    @Inject lateinit var queuedSessionStarter: QueuedSessionStarter
    @Inject lateinit var workerQueuedSessionStarter: WorkerQueuedSessionStarter

    override fun onReceive(context: Context, intent: Intent) {
        // Acquire a partial wake lock so the CPU stays awake after onReceive() returns.
        // Without this, setAndAllowWhileIdle only holds the wake lock until onReceive()
        // returns, and the coroutine below can be suspended mid-execution on some devices.
        val wakeLock = (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "fantasyidler:session_alarm")
            .apply { acquire(30_000L) }

        val pending          = goAsync()
        val sessionId        = intent.getStringExtra(KEY_SESSION_ID) ?: run { pending.finish(); wakeLock.release(); return }
        val skillDisplayName = intent.getStringExtra(KEY_SKILL_DISPLAY_NAME) ?: ""

        CoroutineScope(Dispatchers.IO).launch {
            try {
                processSessionAlarm(sessionId, skillDisplayName)
            } finally {
                pending.finish()
                if (wakeLock.isHeld) wakeLock.release()
            }
        }
    }

    internal suspend fun processSessionAlarm(sessionId: String, skillDisplayName: String) {
        val session = sessionRepository.getSession(sessionId)
        if (session == null || session.completed) return
        if (!sessionRepository.hasTrustedClock(session)) return

        sessionRepository.markCompleted(sessionId)
        // The intent extra carries the English name baked in at scheduling time; resolve
        // the localised one from the session at fire time so notifications follow the
        // current app language (issue #1399).
        val notifName = localizedSessionName(session, skillDisplayName).ifBlank { skillDisplayName }
        // Compute how late the alarm fired so the next session can be backdated.
        val now = System.currentTimeMillis()
        val backdateMs = maxOf(0L, now - session.endsAt)
        if (session.workerSlot == PetArenaRepository.PET_ARENA_SLOT) {
            // Arena battles have no queue: just tell the player their pets are done.
            notificationManager.showSessionComplete(notifName)
        } else if (session.isWorkerSession) {
            val slot = session.workerSlot.coerceAtLeast(1)
            val workerStarted = workerQueuedSessionStarter.startNextQueued(slot)
            if (!workerStarted) notificationManager.showSessionComplete(notifName)
        } else {
            var catchUpMs = backdateMs
            while (catchUpMs > 0) {
                val used = try { queuedSessionStarter.insertNextQueuedAsOffline(catchUpMs) } catch (_: Exception) { 0L }
                if (used == 0L) break
                catchUpMs -= used
            }
            val started = queuedSessionStarter.startNextQueued(backdateMs = catchUpMs)
            if (!started) notificationManager.showSessionComplete(notifName)
        }
    }

    private fun localizedSessionName(session: SkillSession, fallback: String): String {
        val lc = notificationManager.localizedContext()
        return when (session.skillName) {
            "combat"     -> GameStrings.dungeonName(lc, session.activityKey)
            "boss"       -> GameStrings.bossName(lc, session.activityKey)
            "expedition" -> GameStrings.skillingDungeonName(lc, session.activityKey, fallback)
            "tower"      -> lc.getString(R.string.tower_title)
            "carnival"   -> lc.getString(R.string.carnival_title)
            PetArenaRepository.SKILL_NAME -> lc.getString(R.string.pet_arena_title)
            else         -> GameStrings.skillName(lc, session.skillName)
        }
    }

    companion object {
        const val KEY_SESSION_ID         = "session_id"
        const val KEY_SKILL_DISPLAY_NAME = "skill_display_name"
    }
}
