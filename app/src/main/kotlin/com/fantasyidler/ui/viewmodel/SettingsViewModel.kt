package com.fantasyidler.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fantasyidler.data.model.PlayerFlags
import com.fantasyidler.repository.PlayerRepository
import com.fantasyidler.repository.QuestRepository
import com.fantasyidler.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val playerRepo: PlayerRepository,
    private val sessionRepo: SessionRepository,
    private val questRepo: QuestRepository,
    private val json: Json,
) : ViewModel() {

    val themePreference: StateFlow<String> = playerRepo.playerFlow
        .map { player ->
            if (player == null) return@map "dark"
            try { json.decodeFromString<PlayerFlags>(player.flags).themePreference }
            catch (_: Exception) { "dark" }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "dark")

    fun setTheme(preference: String) {
        viewModelScope.launch {
            val flags = playerRepo.getFlags()
            playerRepo.updateFlags(flags.copy(themePreference = preference))
        }
    }

    fun resetProgression() {
        viewModelScope.launch {
            sessionRepo.deleteAllSessions()
            questRepo.resetAllProgress()
            playerRepo.resetProgression()
        }
    }

    fun exportSave(onReady: (String) -> Unit) {
        viewModelScope.launch {
            onReady(playerRepo.exportSave())
        }
    }

    fun importSave(jsonString: String, onDone: (success: Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                playerRepo.importSave(jsonString)
                onDone(true)
            } catch (_: Exception) {
                onDone(false)
            }
        }
    }
}
