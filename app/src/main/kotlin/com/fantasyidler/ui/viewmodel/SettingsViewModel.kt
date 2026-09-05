package com.fantasyidler.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fantasyidler.data.json.ThemeData
import com.fantasyidler.data.model.CustomTheme
import com.fantasyidler.data.model.PlayerFlags
import com.fantasyidler.repository.BackupScheduler
import com.fantasyidler.repository.FarmingRepository
import com.fantasyidler.repository.PlayerRepository
import com.fantasyidler.repository.QuestRepository
import com.fantasyidler.repository.SaveSlotRepository
import com.fantasyidler.repository.SessionRepository
import com.fantasyidler.repository.ThemeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import javax.inject.Inject

data class BackupStatus(
    val lastBackupAt: Long = 0L,
    val lastBackupOk: Boolean = true,
    val lastBackupError: String = "",
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playerRepo: PlayerRepository,
    private val sessionRepo: SessionRepository,
    private val questRepo: QuestRepository,
    private val backupScheduler: BackupScheduler,
    private val farmingRepo: FarmingRepository,
    private val saveSlotRepo: SaveSlotRepository,
    private val themeRepo: ThemeRepository,
    private val json: Json,
) : ViewModel() {

    val officialThemes: List<String> = themeRepo.getOfficialThemes()

    val customThemes: StateFlow<List<CustomTheme>> = themeRepo.observeCustomThemes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000),
            emptyList())

    val themePreference: StateFlow<String> = playerRepo.playerFlow
        .map { player ->
            if (player == null) return@map "dark"
            try { json.decodeFromString<PlayerFlags>(player.flags).themePreference }
            catch (_: Exception) { "dark" }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "dark")

    private val systemDark = MutableStateFlow(themeRepo.isSystemDarkNow())

    /** Keeps the "system" theme in sync with the device night-mode setting; fed from composition. */
    fun setSystemDark(dark: Boolean) { systemDark.value = dark }

    val colourScheme: StateFlow<ColorScheme> = combine(
        playerRepo.playerFlow,
        themeRepo.observeCustomThemes(),
        systemDark,
    ) { player, _, isSystemDark ->
        val preference = if (player == null) {
            "dark"
        } else {
            try { json.decodeFromString<PlayerFlags>(player.flags).themePreference }
            catch (_: Exception) { "dark" }
        }
        themeRepo.getColourScheme(preference, isSystemDark)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), darkColorScheme())

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

    val backupStatus: StateFlow<BackupStatus> = playerRepo.playerFlow
        .map { player ->
            if (player == null) return@map BackupStatus()
            try {
                val flags = json.decodeFromString<PlayerFlags>(player.flags)
                BackupStatus(flags.lastBackupAt, flags.lastBackupOk, flags.lastBackupError)
            } catch (_: Exception) { BackupStatus() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BackupStatus())

    val showRecentActivityLog: StateFlow<Boolean> = playerRepo.playerFlow
        .map { player ->
            if (player == null) return@map true
            try { json.decodeFromString<PlayerFlags>(player.flags).showRecentActivityLog }
            catch (_: Exception) { true }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val compactNumbers: StateFlow<Boolean> = playerRepo.playerFlow
        .map { player ->
            if (player == null) return@map false
            try { json.decodeFromString<PlayerFlags>(player.flags).compactNumbers }
            catch (_: Exception) { false }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val showPrestigeNotifications: StateFlow<Boolean> = playerRepo.playerFlow
        .map { player ->
            if (player == null) return@map true
            try { json.decodeFromString<PlayerFlags>(player.flags).showPrestigeNotifications }
            catch (_: Exception) { true }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val dailyResetHour: StateFlow<Int> = playerRepo.playerFlow
        .map { player ->
            if (player == null) return@map 6
            try { json.decodeFromString<PlayerFlags>(player.flags).dailyResetHour }
            catch (_: Exception) { 6 }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 6)

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

    val showStatsBar: StateFlow<Boolean> = playerRepo.playerFlow
        .map { player ->
            if (player == null) return@map true
            try { json.decodeFromString<PlayerFlags>(player.flags).showStatsBar }
            catch (_: Exception) { true }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val showSessionEndTime: StateFlow<Boolean> = playerRepo.playerFlow
        .map { player ->
            if (player == null) return@map true
            try { json.decodeFromString<PlayerFlags>(player.flags).showSessionEndTime }
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

    fun setCompactNumbers(enabled: Boolean) {
        viewModelScope.launch {
            val flags = playerRepo.getFlags()
            playerRepo.updateFlags(flags.copy(compactNumbers = enabled))
        }
    }

    fun setShowPrestigeNotifications(enabled: Boolean) {
        viewModelScope.launch {
            val flags = playerRepo.getFlags()
            playerRepo.updateFlags(flags.copy(showPrestigeNotifications = enabled))
        }
    }

    fun setDailyResetHour(hour: Int) {
        viewModelScope.launch {
            val flags = playerRepo.getFlags()
            if (hour == flags.dailyResetHour || hour !in 0..23) return@launch
            // Re-stamp active quest sets to "generated now" so moving the hour can
            // never land a boundary in the past and grant an instant extra reset;
            // the next reset is simply the new hour's next occurrence.
            val now = System.currentTimeMillis()
            playerRepo.updateFlags(flags.copy(
                dailyResetHour         = hour,
                dailyQuestGeneratedAt  = if (flags.dailyQuestGeneratedAt != 0L) now else 0L,
                weeklyQuestGeneratedAt = if (flags.weeklyQuestGeneratedAt != 0L) now else 0L,
                guildDailyGeneratedAt  = if (flags.guildDailyGeneratedAt != 0L) now else 0L,
            ))
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

    fun setShowStatsBar(enabled: Boolean) {
        viewModelScope.launch {
            val flags = playerRepo.getFlags()
            playerRepo.updateFlags(flags.copy(showStatsBar = enabled))
        }
    }

    fun setShowSessionEndTime(enabled: Boolean) {
        viewModelScope.launch {
            val flags = playerRepo.getFlags()
            playerRepo.updateFlags(flags.copy(showSessionEndTime = enabled))
        }
    }

    fun setTheme(preference: String) {
        viewModelScope.launch {
            val flags = playerRepo.getFlags()
            playerRepo.updateFlags(flags.copy(themePreference = preference))
        }
    }

    fun deleteTheme(theme: String) {
        viewModelScope.launch {
            if (!themeRepo.deleteTheme(theme)) return@launch
            val flags = playerRepo.getFlags()
            if (flags.themePreference == theme) {
                playerRepo.updateFlags(flags.copy(themePreference = "dark"))
            }
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
            onReady(saveSlotRepo.exportFullSave())
        }
    }

    /** Suggested per-character export file name, e.g. fantasyidler_save_2_IronDragon.json. */
    fun exportSuggestedName(onReady: (String) -> Unit) {
        viewModelScope.launch {
            val slot = saveSlotRepo.activeSlot()
            val name = playerRepo.getFlags().characterName
            onReady(BackupScheduler.exportFileName(slot, name))
        }
    }

    fun importSave(jsonString: String, onDone: (success: Boolean, ironmanDemoted: Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val ironmanDemoted = saveSlotRepo.importFullSave(jsonString)
                onDone(true, ironmanDemoted)
            } catch (_: Exception) {
                onDone(false, false)
            }
        }
    }

    fun importTheme(jsonString: String, onDone: (success: Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                themeRepo.importTheme(jsonString)
                onDone(true)
            } catch (_: Exception) {
                onDone(false)
            }
        }
    }

    /** Serializes [theme] in the shape importTheme reads and hands it to [onReady]. */
    fun exportTheme(theme: String, onReady: (jsonString: String) -> Unit) {
        viewModelScope.launch {
            val data = themeRepo.getThemeData(theme) ?: return@launch
            onReady(
                json.encodeToString(
                    json.serializersModule.serializer<Map<String, ThemeData>>(),
                    mapOf(theme to data),
                )
            )
        }
    }
}
