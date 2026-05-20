package com.fantasyidler

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.fantasyidler.notification.SessionNotificationManager
import com.fantasyidler.repository.SessionRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class FantasyIdlerApp : Application(), Configuration.Provider {

    @Inject lateinit var notificationManager: SessionNotificationManager
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var sessionRepository: SessionRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        notificationManager.createChannels()
        // Re-enqueue completion work for any session that was started before
        // the AlarmManager → WorkManager migration. Safe on every cold start:
        // unique-work REPLACE makes the call idempotent.
        appScope.launch { sessionRepository.rescheduleActiveSessionIfAny() }
    }
}
