package com.fantasyidler.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fantasyidler.data.model.PlayerFlags
import com.fantasyidler.data.model.toExport
import com.fantasyidler.data.model.toSkillSession
import com.fantasyidler.repository.BackupScheduler
import com.fantasyidler.repository.FarmingRepository
import com.fantasyidler.repository.GuildRepository
import com.fantasyidler.repository.PlayerRepository
import com.fantasyidler.repository.QueuedSessionStarter
import com.fantasyidler.repository.QuestRepository
import com.fantasyidler.repository.SessionRepository
import com.fantasyidler.repository.WorkerQueuedSessionStarter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playerRepo: PlayerRepository,
    private val sessionRepo: SessionRepository,
    private val questRepo: QuestRepository,
    private val queuedSessionStarter: QueuedSessionStarter,
    private val workerStarter: WorkerQueuedSessionStarter,
    private val backupScheduler: BackupScheduler,
    private val guildRepo: GuildRepository,
    private val farmingRepo: FarmingRepository,
    private val json: Json,
) : ViewModel() {

    val themePreference: StateFlow<String> = playerRepo.playerFlow
        .map { player ->
            if (player == null) return@map "dark"
            try { json.decodeFromString<PlayerFlags>(player.flags).themePreference }
            catch (_: Exception) { "dark" }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "dark")

    val fontScale: StateFlow<Float> = playerRepo.playerFlow
        .map { player ->
            if (player == null) return@map 1.0f
            try { json.decodeFromString<PlayerFlags>(player.flags).fontScale }
            catch (_: Exception) { 1.0f }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1.0f)

    val backupFolderUri: StateFlow<String> = playerRepo.playerFlow
        .map { player ->
            if (player == null) return@map ""
            try { json.decodeFromString<PlayerFlags>(player.flags).backupFolderUri }
            catch (_: Exception) { "" }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val backupFrequency: StateFlow<String> = playerRepo.playerFlow
        .map { player ->
            if (player == null) return@map ""
            try { json.decodeFromString<PlayerFlags>(player.flags).backupFrequency }
            catch (_: Exception) { "" }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val showRecentActivityLog: StateFlow<Boolean> = playerRepo.playerFlow
        .map { player ->
            if (player == null) return@map true
            try { json.decodeFromString<PlayerFlags>(player.flags).showRecentActivityLog }
            catch (_: Exception) { true }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val showJournalButton: StateFlow<Boolean> = playerRepo.playerFlow
        .map { player ->
            if (player == null) return@map true
            try { json.decodeFromString<PlayerFlags>(player.flags).showJournalButton }
            catch (_: Exception) { true }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val showSeasonalEvents: StateFlow<Boolean> = playerRepo.playerFlow
        .map { player ->
            if (player == null) return@map true
            try { json.decodeFromString<PlayerFlags>(player.flags).showSeasonalEvents }
            catch (_: Exception) { true }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val collapsibleTownGrid: StateFlow<Boolean> = playerRepo.playerFlow
        .map { player ->
            if (player == null) return@map true
            try { json.decodeFromString<PlayerFlags>(player.flags).collapsibleTownGrid }
            catch (_: Exception) { true }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val showCharacterViewer: StateFlow<Boolean> = playerRepo.playerFlow
        .map { player ->
            if (player == null) return@map true
            try { json.decodeFromString<PlayerFlags>(player.flags).showCharacterViewer }
            catch (_: Exception) { true }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val profileLayout: StateFlow<String> = playerRepo.playerFlow
        .map { player ->
            if (player == null) return@map "rail"
            try { json.decodeFromString<PlayerFlags>(player.flags).profileLayout }
            catch (_: Exception) { "rail" }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "rail")

    fun setProfileLayout(mode: String) {
        viewModelScope.launch {
            val flags = playerRepo.getFlags()
            playerRepo.updateFlags(flags.copy(profileLayout = mode))
        }
    }

    fun setShowRecentActivityLog(enabled: Boolean) {
        viewModelScope.launch {
            val flags = playerRepo.getFlags()
            playerRepo.updateFlags(flags.copy(showRecentActivityLog = enabled))
        }
    }

    fun setShowJournalButton(enabled: Boolean) {
        viewModelScope.launch {
            val flags = playerRepo.getFlags()
            playerRepo.updateFlags(flags.copy(showJournalButton = enabled))
        }
    }

    fun setShowSeasonalEvents(enabled: Boolean) {
        viewModelScope.launch {
            val flags = playerRepo.getFlags()
            playerRepo.updateFlags(flags.copy(showSeasonalEvents = enabled))
        }
    }

    fun setCollapsibleTownGrid(enabled: Boolean) {
        viewModelScope.launch {
            val flags = playerRepo.getFlags()
            playerRepo.updateFlags(flags.copy(collapsibleTownGrid = enabled))
        }
    }

    fun setShowCharacterViewer(enabled: Boolean) {
        viewModelScope.launch {
            val flags = playerRepo.getFlags()
            playerRepo.updateFlags(flags.copy(showCharacterViewer = enabled))
        }
    }

    fun setTheme(preference: String) {
        viewModelScope.launch {
            val flags = playerRepo.getFlags()
            playerRepo.updateFlags(flags.copy(themePreference = preference))
        }
    }

    fun setFontScale(scale: Float) {
        viewModelScope.launch {
            val flags = playerRepo.getFlags()
            playerRepo.updateFlags(flags.copy(fontScale = scale))
        }
    }

    fun setBackupFolder(uriString: String) {
        viewModelScope.launch {
            val flags = playerRepo.getFlags()
            val permFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            if (flags.backupFolderUri.isNotEmpty()) {
                try { context.contentResolver.releasePersistableUriPermission(Uri.parse(flags.backupFolderUri), permFlags) }
                catch (_: Exception) {}
            }
            context.contentResolver.takePersistableUriPermission(Uri.parse(uriString), permFlags)
            playerRepo.updateFlags(flags.copy(backupFolderUri = uriString))
            // Reschedule using the frequency already stored in flags. The folder URI
            // is now saved, so the next performBackup will find it correctly.
            // (Previously this used the stale pre-save flags object, same result here
            //  since only the URI changed, but being explicit avoids future confusion.)
            if (flags.backupFrequency.isNotEmpty()) backupScheduler.schedule(flags.backupFrequency)
        }
    }


    fun setBackupFrequency(frequency: String) {
        viewModelScope.launch {
            val flags = playerRepo.getFlags()
            playerRepo.updateFlags(flags.copy(backupFrequency = frequency))
            backupScheduler.schedule(frequency)
        }
    }

    fun backupNow(onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = backupScheduler.performBackup(playerRepo)
            onDone(success)
        }
    }

    fun resetProgression() {
        viewModelScope.launch {
            sessionRepo.deleteAllSessions()
            questRepo.resetAllProgress()
            farmingRepo.resetAllPatches()
            playerRepo.resetProgression()
        }
    }

    fun exportSave(onReady: (String) -> Unit) {
        viewModelScope.launch {
            val sessions = buildList {
                sessionRepo.getActiveSession()?.let { add(it.toExport()) }
                addAll(sessionRepo.getAllCompletedSessions().map { it.toExport() })
                for (slot in 1..2) {
                    sessionRepo.getActiveWorkerSession(slot)?.let { add(it.toExport()) }
                    addAll(sessionRepo.getAllCompletedWorkerSessions(slot).map { it.toExport() })
                }
            }.distinctBy { it.sessionId }
            onReady(playerRepo.exportSave(sessions))
        }
    }

    fun importSave(jsonString: String, onDone: (success: Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val export = playerRepo.importSave(jsonString)
                // Imported flags may predate the guild-leveling rework (reputation -> daily-count),
                // and importing overwrites the current save's migration state wholesale, so this
                // must re-run here rather than relying on the one-time app-startup call.
                guildRepo.migrateLegacyGuildReputation()
                sessionRepo.deleteAllSessions()
                sessionRepo.deleteAllWorkerSessions()
                val now = System.currentTimeMillis()
                val exportedAt = export.exportedAt.takeIf { it > 0L } ?: now
                export.sessions.forEach { s ->
                    val session = if (s.completed) {
                        s.toSkillSession()
                    } else {
                        val remainingMs = (s.endsAt - exportedAt).coerceAtLeast(0L)
                        s.toSkillSession().copy(endsAt = now + remainingMs)
                    }
                    try {
                        sessionRepo.insertSession(session)
                    } catch (_: Exception) {
                        // A duplicate/bad session in an old export file shouldn't abort the whole restore.
                    }
                }
                sessionRepo.recoverActiveSession(queuedSessionStarter)
                sessionRepo.recoverActiveWorkerSession(1, workerStarter)
                sessionRepo.recoverActiveWorkerSession(2, workerStarter)
                onDone(true)
            } catch (_: Exception) {
                onDone(false)
            }
        }
    }
}
