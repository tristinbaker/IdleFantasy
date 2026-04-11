package com.fantasyidler.notification

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.NotificationChannelCompat
import com.fantasyidler.MainActivity
import com.fantasyidler.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        const val CHANNEL_ID_SESSIONS = "fantasy_idler_sessions"
        const val CHANNEL_ID_FARMING  = "fantasy_idler_farming"

        private const val NOTIF_ID_SESSION_COMPLETE = 1001
        private const val NOTIF_ID_FARMING_READY    = 2001
    }

    /** Call once on app startup to register notification channels (idempotent). */
    fun createChannels() {
        val mgr = NotificationManagerCompat.from(context)
        mgr.createNotificationChannel(
            NotificationChannelCompat
                .Builder(CHANNEL_ID_SESSIONS, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.notif_channel_sessions_name))
                .setDescription(context.getString(R.string.notif_channel_sessions_desc))
                .build()
        )
        mgr.createNotificationChannel(
            NotificationChannelCompat
                .Builder(CHANNEL_ID_FARMING, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.notif_channel_farming_name))
                .setDescription(context.getString(R.string.notif_channel_farming_desc))
                .build()
        )
    }

    private fun launchIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Show "Your [skillName] session has finished" notification. */
    fun showSessionComplete(skillDisplayName: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_SESSIONS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.notif_session_complete_title))
            .setContentText(
                context.getString(R.string.notif_session_complete_body, skillDisplayName)
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(launchIntent())
            .setAutoCancel(true)
            .build()

        postIfPermitted(NOTIF_ID_SESSION_COMPLETE, notification)
    }

    /** Show "Your [cropName] is ready to harvest" notification. */
    fun showFarmingReady(cropDisplayName: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_FARMING)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.notif_farming_ready_title))
            .setContentText(
                context.getString(R.string.notif_farming_ready_body, cropDisplayName)
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        postIfPermitted(NOTIF_ID_FARMING_READY, notification)
    }

    private fun postIfPermitted(id: Int, notification: android.app.Notification) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context).notify(id, notification)
        }
    }
}
