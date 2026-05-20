package com.fantasyidler.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fantasyidler.notification.SessionNotificationManager
import com.fantasyidler.repository.QueuedSessionStarter
import com.fantasyidler.repository.SessionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Marks a session complete at its scheduled end time, then either kicks off the
 * next queued session or fires the completion notification. Replaces the
 * previous AlarmManager + SessionAlarmReceiver path: WorkManager persists the
 * request across reboots and handles Doze without per-receiver boot wiring.
 *
 * Enqueued by [SessionRepository.startSession] as a one-shot delayed work
 * request, keyed by a `session:<sessionId>` unique-work name so a session
 * re-scheduled (or cancelled on abandon) supersedes the prior request.
 */
@HiltWorker
class SessionCompletionWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val sessionRepository: SessionRepository,
    private val queuedSessionStarter: QueuedSessionStarter,
    private val notificationManager: SessionNotificationManager,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val sessionId = inputData.getString(KEY_SESSION_ID) ?: return Result.success()
        val skillDisplayName = inputData.getString(KEY_SKILL_DISPLAY_NAME) ?: ""

        sessionRepository.markCompleted(sessionId)
        val started = queuedSessionStarter.startNextQueued()
        if (!started) {
            val hasRunning = sessionRepository.getActiveSession()?.completed == false
            if (!hasRunning) notificationManager.showSessionComplete(skillDisplayName)
        }
        return Result.success()
    }

    companion object {
        const val KEY_SESSION_ID         = "session_id"
        const val KEY_SKILL_DISPLAY_NAME = "skill_display_name"

        fun uniqueWorkName(sessionId: String): String = "session:$sessionId"
    }
}
