package com.fantasyidler.repository

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.provider.DocumentsContract
import android.net.Uri
import android.util.Log
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
    private val globalStateRepo: GlobalStateRepository,
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

    /** Reschedule the next alarm occurrence after a backup firing (successful or not). */
    fun reschedule(frequency: String) {
        if (frequency.isEmpty()) return
        val intent = Intent(context, BackupAlarmReceiver::class.java)
            .putExtra(EXTRA_FREQUENCY, frequency)
        val pi = PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // Anchor daily/weekly to the 5am slot rather than "now + interval": a manual
        // Back Up Now used to shift the next auto fire to the time of the tap, and a
        // Doze-delayed firing shifted it permanently, so the 5am backup quietly stopped
        // happening at 5am (playtester report after v1.14.3).
        val nextFire = if (frequency == "hourly") System.currentTimeMillis() + intervalMs(frequency)
                       else nextFiveAm(frequency)
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
        // Keep the periodic chain alive no matter how this run ends: a failed or skipped
        // firing used to end the sequence until the next cold app launch re-registered it.
        val effectiveFreq = frequency.ifEmpty { flags.backupFrequency }
        try {
            if (effectiveFreq.isNotEmpty()) reschedule(effectiveFreq)
        } catch (e: Exception) {
            Log.w(TAG, "Backup reschedule failed", e)
        }
        if (flags.backupFolderUri.isEmpty()) return false
        // Per-character file names: each save slot keeps its own backups, so switching
        // characters no longer overwrites another character's auto backup.
        val activeSlot = globalStateRepo.getActiveSaveSlot()
        val slotPrefix = autoBackupSlotPrefix(activeSlot)
        val backupCount = flags.backupCount.coerceAtLeast(1)
        var tempUri: Uri? = null
        var failureMsg = ""
        val ok = try {
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
            val existingDocs = childDocuments(cr, treeUri, treeDocId)
                .filter { (_, name) ->
                    name == slotPrefix || name.startsWith("${slotPrefix}_") ||
                        name == "$slotPrefix.json" || name == slotPrefix + TEMP_SUFFIX ||
                        name == "$slotPrefix$TEMP_SUFFIX.json"
                }
            val generation = maxOf(
                System.currentTimeMillis(),
                (existingDocs.maxOfOrNull { backupGeneration(it.second) } ?: 0L) + 1L,
            )
            val baseName = autoBackupFileName(activeSlot, flags.characterName)
            val finalName = "${baseName}_at_$generation"
            val tempName = finalName + TEMP_SUFFIX

            val created = DocumentsContract.createDocument(
                cr,
                DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId),
                "application/json",
                tempName,
            ) ?: throw IllegalStateException("backup provider refused to create temp document")
            tempUri = created

            cr.openOutputStream(created, "w")?.use { it.write(jsonBytes) }
                ?: throw IllegalStateException("backup provider would not open output stream")

            val verified = cr.openInputStream(created)?.use { input ->
                val cap = ByteArray(jsonBytes.size + 1)
                var filled = 0
                while (filled < cap.size) {
                    val read = input.read(cap, filled, cap.size - filled)
                    if (read < 0) break
                    filled += read
                }
                cap.copyOf(filled)
            } ?: throw IllegalStateException("backup provider would not reopen temp document for verification")
            if (!verified.contentEquals(jsonBytes)) {
                throw IllegalStateException("temp document bytes differ from exported save")
            }

            val renamed = DocumentsContract.renameDocument(cr, created, finalName)
                ?: throw IllegalStateException("backup provider failed to swap temp document to final name")
            tempUri = null

            val renamedId = DocumentsContract.getDocumentId(renamed)
            val swappedIn = childDocuments(cr, treeUri, treeDocId)
                .any { (id, name) -> id == renamedId && (name == finalName || name == "$finalName.json") }
            if (!swappedIn) {
                throw IllegalStateException("renamed backup document not found after swap")
            }

            // Only prune older backups after the new file is verified and finalized.
            val retainedIds = existingDocs
                .filterNot { it.second.removeSuffix(".json").endsWith(TEMP_SUFFIX) }
                .sortedByDescending { backupGeneration(it.second) }
                .take(backupCount - 1)
                .map { it.first }
                .toSet()
            val doomedIds = existingDocs
                .filterNot { it.first in retainedIds }
                .map { it.first }
            doomedIds.forEach { docId ->
                DocumentsContract.deleteDocument(cr, DocumentsContract.buildDocumentUriUsingTree(treeUri, docId))
            }

            true
        } catch (e: Exception) {
            Log.w(TAG, "Auto-backup failed", e)
            failureMsg = e.message ?: e.javaClass.simpleName
            false
        }

        if (!ok) {
            tempUri?.let { temp ->
                try { DocumentsContract.deleteDocument(context.contentResolver, temp) } catch (_: Exception) {}
            }
        }

        try {
            playerRepo.updateFlagsAtomically { f ->
                f.copy(
                    lastBackupAt    = System.currentTimeMillis(),
                    lastBackupOk    = ok,
                    lastBackupError = failureMsg,
                )
            }
        } catch (_: Exception) {}

        return ok
    }

    private fun childDocuments(cr: ContentResolver, treeUri: Uri, treeDocId: String): List<Pair<String, String>> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId)
        val docs = mutableListOf<Pair<String, String>>()
        cr.query(
            childrenUri,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val docId = cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID))
                val name  = cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME))
                docs += docId to name
            }
        }
        return docs
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
        private const val TAG = "BackupScheduler"
        private const val AUTO_BASE   = "fantasyidler_auto"
        private const val EXPORT_BASE = "fantasyidler_save"
        private const val TEMP_SUFFIX = ".tmp"
        private const val REQUEST_CODE = 9001
        const val EXTRA_FREQUENCY = "backup_frequency"

        /** Letters and digits only, so every storage provider accepts the display name. */
        private fun sanitizeCharacterName(name: String): String =
            name.filter { it.isLetterOrDigit() }.take(24)

        private fun backupGeneration(name: String): Long =
            name.removeSuffix(".json").removeSuffix(TEMP_SUFFIX)
                .substringAfterLast("_at_", "").toLongOrNull() ?: 0L

        /** Slot prefix scoping backup cleanup, leaving other slots' files untouched. */
        internal fun autoBackupSlotPrefix(slot: Int) = "${AUTO_BASE}_$slot"

        /** Per-character backup name, e.g. fantasyidler_auto_2_IronDragon (name part omitted when blank). */
        internal fun autoBackupFileName(slot: Int, characterName: String): String {
            val clean = sanitizeCharacterName(characterName)
            return if (clean.isEmpty()) autoBackupSlotPrefix(slot) else "${autoBackupSlotPrefix(slot)}_$clean"
        }

        /** Suggested export name, e.g. fantasyidler_save_2_IronDragon.json. */
        fun exportFileName(slot: Int, characterName: String): String {
            val clean = sanitizeCharacterName(characterName)
            val base = if (clean.isEmpty()) "${EXPORT_BASE}_$slot" else "${EXPORT_BASE}_${slot}_$clean"
            return "$base.json"
        }
    }
}
