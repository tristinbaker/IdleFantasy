package com.fantasyidler.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.fantasyidler.notification.SessionNotificationManager
import com.fantasyidler.repository.QueuedSessionStarter
import com.fantasyidler.repository.SessionRepository
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
                sessionRepository.markCompleted(sessionId)
                val started = queuedSessionStarter.startNextQueued()
                if (!started) {
                    val hasRunning = sessionRepository.getActiveSession()?.completed == false
                    if (!hasRunning) notificationManager.showSessionComplete(skillDisplayName)
                }
            } finally {
                pending.finish()
                if (wakeLock.isHeld) wakeLock.release()
            }
        }
    }

    companion object {
        const val KEY_SESSION_ID         = "session_id"
        const val KEY_SKILL_DISPLAY_NAME = "skill_display_name"
    }
}
