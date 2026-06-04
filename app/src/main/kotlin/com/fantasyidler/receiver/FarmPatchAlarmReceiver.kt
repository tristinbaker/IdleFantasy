package com.fantasyidler.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.fantasyidler.notification.SessionNotificationManager
import com.fantasyidler.util.GameStrings
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class FarmPatchAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_PATCH_NUMBER = "patch_number"
        const val EXTRA_CROP_NAME   = "crop_display_name"
    }

    @Inject lateinit var notificationManager: SessionNotificationManager

    override fun onReceive(context: Context, intent: Intent) {
        val cropKey = intent.getStringExtra(EXTRA_CROP_NAME) ?: return
        notificationManager.showFarmingReady(GameStrings.cropName(context, cropKey))
    }
}
