package com.fantasyidler.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.fantasyidler.repository.BackupScheduler
import com.fantasyidler.repository.PlayerRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BackupAlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var playerRepo: PlayerRepository
    @Inject lateinit var backupScheduler: BackupScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val frequency = intent.getStringExtra(BackupScheduler.EXTRA_FREQUENCY) ?: ""
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                backupScheduler.performBackup(playerRepo, frequency)
            } finally {
                pending.finish()
            }
        }
    }
}
