package com.fantasyidler.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.fantasyidler.repository.BackupScheduler
import com.fantasyidler.repository.BuffNotificationScheduler
import com.fantasyidler.repository.PlayerRepository
import com.fantasyidler.repository.QueuedSessionStarter
import com.fantasyidler.repository.SessionRepository
import com.fantasyidler.repository.WorkerQueuedSessionStarter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var sessionRepository: SessionRepository
    @Inject lateinit var queuedSessionStarter: QueuedSessionStarter
    @Inject lateinit var workerStarter: WorkerQueuedSessionStarter
    @Inject lateinit var playerRepository: PlayerRepository
    @Inject lateinit var backupScheduler: BackupScheduler
    @Inject lateinit var buffNotifScheduler: BuffNotificationScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                sessionRepository.recoverActiveSession(queuedSessionStarter)
                sessionRepository.recoverActiveWorkerSession(1, workerStarter)
                sessionRepository.recoverActiveWorkerSession(2, workerStarter)
                val flags = playerRepository.getFlags()
                if (flags.backupFrequency.isNotEmpty()) backupScheduler.schedule(flags.backupFrequency)
                buffNotifScheduler.scheduleXpBoostExpiry(flags.xpBoostExpiresAt)
                buffNotifScheduler.scheduleBlessingExpiry(flags.activeBlessingExpiresAt)
            } finally {
                pending.finish()
            }
        }
    }
}
