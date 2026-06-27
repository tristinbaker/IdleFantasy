package com.fantasyidler.repository

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.DocumentsContract
import android.net.Uri
import com.fantasyidler.data.model.toExport
import com.fantasyidler.receiver.BackupAlarmReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class BackupScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionRepo: SessionRepository,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun schedule(frequency: String) {
        cancel()
        if (frequency.isEmpty()) return
        // Build the PendingIntent with the frequency baked into the Intent extras so
        // BackupAlarmReceiver can reschedule the next occurrence after each firing.
        val intent = Intent(context, BackupAlarmReceiver::class.java)
            .putExtra(EXTRA_FREQUENCY, frequency)
        val pi = PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val firstFire = if (frequency == "hourly") System.currentTimeMillis() + intervalMs(frequency)
                        else nextFiveAm(frequency)
        // setInexactRepeating only honours Android's own built-in interval constants
        // (INTERVAL_HOUR, INTERVAL_DAY, etc.). Passing 7*INTERVAL_DAY is silently
        // ignored and the alarm fires once then stops. Use setExactAndAllowWhileIdle
        // instead and reschedule manually inside performBackup after each firing.
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, firstFire, pi)
    }

    /** Reschedule the next alarm occurrence after a successful backup firing. */
    fun reschedule(frequency: String) {
        if (frequency.isEmpty()) return
        val intent = Intent(context, BackupAlarmReceiver::class.java)
            .putExtra(EXTRA_FREQUENCY, frequency)
        val pi = PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val nextFire = System.currentTimeMillis() + intervalMs(frequency)
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextFire, pi)
    }

    private fun intervalMs(frequency: String): Long = when (frequency) {
        "hourly" -> AlarmManager.INTERVAL_HOUR
        "daily"  -> AlarmManager.INTERVAL_DAY
        "weekly" -> 7L * AlarmManager.INTERVAL_DAY
        else     -> AlarmManager.INTERVAL_DAY
    }

    fun cancel() {
        // FLAG_NO_CREATE returns null if no matching alarm is registered, so the
        // cancel() call is safely skipped when there is nothing to cancel.
        val intent = Intent(context, BackupAlarmReceiver::class.java)
        PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )?.let { alarmManager.cancel(it) }
    }

    suspend fun performBackup(playerRepo: PlayerRepository, frequency: String = ""): Boolean {
        val flags = playerRepo.getFlags()
        if (flags.backupFolderUri.isEmpty()) return false
        return try {
            val sessions = buildList {
                sessionRepo.getActiveSession()?.let { add(it.toExport()) }
                addAll(sessionRepo.getAllCompletedSessions().map { it.toExport() })
                for (slot in 1..2) {
                    sessionRepo.getActiveWorkerSession(slot)?.let { add(it.toExport()) }
                    addAll(sessionRepo.getAllCompletedWorkerSessions(slot).map { it.toExport() })
                }
            }
            val jsonBytes = playerRepo.exportSave(sessions).toByteArray()
            val treeUri   = Uri.parse(flags.backupFolderUri)
            val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val cr        = context.contentResolver

            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId)
            var existingDocId: String? = null
            cr.query(
                childrenUri,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null, null, null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val name = cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME))
                    if (name == "fantasyidler_auto.json" || name == "fantasyidler_auto") {
                        existingDocId = cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID))
                        break
                    }
                }
            }

            // Delete the existing file before creating a fresh one to avoid SAF truncation issues
            // that leave old bytes after the new JSON on some devices.
            existingDocId?.let { docId ->
                DocumentsContract.deleteDocument(cr, DocumentsContract.buildDocumentUriUsingTree(treeUri, docId))
            }
            val docUri  = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId)
            val fileUri = DocumentsContract.createDocument(cr, docUri, "application/json", "fantasyidler_auto")
            fileUri?.let { cr.openOutputStream(it, "w")?.use { s -> s.write(jsonBytes) } }

            // Schedule the next occurrence. setExactAndAllowWhileIdle is one-shot so
            // each firing must manually reschedule the next alarm.
            val effectiveFreq = frequency.ifEmpty { flags.backupFrequency }
            if (effectiveFreq.isNotEmpty()) reschedule(effectiveFreq)

            true
        } catch (_: Exception) { false }
    }

    /**
     * Returns the next 5am for daily/weekly (tomorrow if 5am today has already passed),
     * or for weekly, the next Sunday 5am.
     */
    private fun nextFiveAm(frequency: String): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 5)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        // For weekly, advance to the next Sunday
        if (frequency == "weekly") {
            while (get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) add(Calendar.DAY_OF_YEAR, 1)
        }
    }.timeInMillis

    companion object {
        private const val REQUEST_CODE = 9001
        const val EXTRA_FREQUENCY = "backup_frequency"
    }
}
