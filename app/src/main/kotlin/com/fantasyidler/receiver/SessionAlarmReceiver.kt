package com.fantasyidler.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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
        val pending          = goAsync()
        val sessionId        = intent.getStringExtra(KEY_SESSION_ID) ?: run { pending.finish(); return }
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
            }
        }
    }

    companion object {
        const val KEY_SESSION_ID         = "session_id"
        const val KEY_SKILL_DISPLAY_NAME = "skill_display_name"
    }
}
