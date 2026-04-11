package com.fantasyidler.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fantasyidler.notification.SessionNotificationManager
import com.fantasyidler.repository.SessionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Fires when a 1-hour skill session completes.
 *
 * Responsibilities:
 * 1. Mark the session as complete in the DB (so the UI can apply rewards on next open).
 * 2. Fire the session-complete notification.
 *
 * Scheduled by [SessionRepository.startSession] with a 1-hour initial delay.
 * Uses [androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST] so it
 * degrades gracefully on devices that have consumed their expedited quota, rather
 * than failing entirely.
 *
 * Battery optimisation note: users are prompted (in Settings) to disable battery
 * optimisation for Fantasy Idler. Without that, Doze mode may delay this worker
 * by up to ~15 minutes on some OEM skins.
 */
@HiltWorker
class SessionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val sessionRepository: SessionRepository,
    private val notificationManager: SessionNotificationManager,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val sessionId = inputData.getString(KEY_SESSION_ID)
            ?: return Result.failure()
        val skillDisplayName = inputData.getString(KEY_SKILL_DISPLAY_NAME) ?: ""

        sessionRepository.markCompleted(sessionId)
        notificationManager.showSessionComplete(skillDisplayName)

        return Result.success()
    }

    companion object {
        const val KEY_SESSION_ID         = "session_id"
        const val KEY_SKILL_DISPLAY_NAME = "skill_display_name"

        private const val WORK_NAME_PREFIX = "session_complete_"

        fun workName(sessionId: String): String = "$WORK_NAME_PREFIX$sessionId"
    }
}
