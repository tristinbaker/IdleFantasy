package com.fantasyidler.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fantasyidler.repository.PlayerRepository
import com.fantasyidler.repository.QuestRepository
import com.fantasyidler.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val playerRepo: PlayerRepository,
    private val sessionRepo: SessionRepository,
    private val questRepo: QuestRepository,
) : ViewModel() {

    fun resetProgression() {
        viewModelScope.launch {
            sessionRepo.deleteAllSessions()
            questRepo.resetAllProgress()
            playerRepo.resetProgression()
        }
    }
}
