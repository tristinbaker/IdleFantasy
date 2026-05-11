package com.fantasyidler

import android.app.Application
import com.fantasyidler.notification.SessionNotificationManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class FantasyIdlerApp : Application() {

    @Inject lateinit var notificationManager: SessionNotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager.createChannels()
    }
}
