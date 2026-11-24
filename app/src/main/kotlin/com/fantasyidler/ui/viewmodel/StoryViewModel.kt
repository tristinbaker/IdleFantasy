package com.fantasyidler.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fantasyidler.data.model.PlayerFlags
import com.fantasyidler.repository.PlayerRepository
import com.fantasyidler.repository.StoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

data class StoryUiState(
    val loaded: Boolean = false,
    val started: Boolean = false,
    val promptPending: Boolean = false,
    val popupsEnabled: Boolean = true,
    val tabVisible: Boolean = true,
    val chapterIndex: Int = 0,
    val counters: Map<String, Int> = emptyMap(),
    val delivered: List<String> = emptyList(),
    val read: Set<String> = emptySet(),
    val popupQueue: List<String> = emptyList(),
    val inventory: Map<String, Int> = emptyMap(),
    val onElderIsle: Boolean = false,
    val characterSetupDone: Boolean = false,
    val islandName: String = "",
    val islandNameAsked: Boolean = false,
) {
    val unreadCount: Int get() = delivered.count { it !in read }
}

/** Shared by the Story tab, the app-wide note pop-up host, and Home's dialog gate. */
@HiltViewModel
class StoryViewModel @Inject constructor(
    private val storyRepo: StoryRepository,
    playerRepo: PlayerRepository,
    private val json: Json,
) : ViewModel() {

    val state: StateFlow<StoryUiState> = playerRepo.playerFlow
        .map { p ->
            if (p == null) return@map StoryUiState()
            val flags: PlayerFlags = try { json.decodeFromString(p.flags) } catch (_: Exception) { PlayerFlags() }
            val inv: Map<String, Int> = try { json.decodeFromString(p.inventory) } catch (_: Exception) { emptyMap() }
            StoryUiState(
                loaded             = true,
                started            = flags.storyStarted,
                promptPending      = flags.storyPromptPending,
                popupsEnabled      = flags.storyPopups,
                tabVisible         = flags.storyTabVisible,
                chapterIndex       = flags.storyChapter,
                counters           = flags.storyCounters,
                delivered          = flags.storyDelivered,
                read               = flags.storyRead,
                popupQueue         = flags.storyPopupQueue,
                inventory          = inv,
                onElderIsle        = flags.onElderIsle,
                characterSetupDone = flags.characterSetupDone,
                islandName         = flags.islandName,
                islandNameAsked    = flags.islandNameAsked,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StoryUiState())

    val homeDialogOpen: StateFlow<Boolean> = storyRepo.homeDialogOpen

    fun begin() { viewModelScope.launch { storyRepo.begin() } }
    fun declinePrompt() { viewModelScope.launch { storyRepo.declinePrompt() } }
    fun markRead(noteId: String) { viewModelScope.launch { storyRepo.markRead(noteId) } }
    fun readLater(noteId: String) { viewModelScope.launch { storyRepo.readLater(noteId) } }
    fun setHomeDialogOpen(open: Boolean) = storyRepo.setHomeDialogOpen(open)
    fun setIslandName(name: String) { viewModelScope.launch { storyRepo.setIslandName(name) } }
    fun skipIslandName() { viewModelScope.launch { storyRepo.skipIslandName() } }
}
