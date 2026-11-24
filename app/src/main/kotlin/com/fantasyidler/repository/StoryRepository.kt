package com.fantasyidler.repository

import android.util.Log
import com.fantasyidler.data.model.MainStory
import com.fantasyidler.data.model.PlayerFlags
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists [MainStory] progression into PlayerFlags. Every entry point has a locked variant
 * plus an `Unlocked` one for callers already holding [PlayerRepository.playerMutex] (the
 * queue starters call [SessionRepository.startSession] that way).
 *
 * Story bookkeeping must never break gameplay, so failures are logged and swallowed.
 */
@Singleton
class StoryRepository @Inject constructor(
    private val playerRepo: PlayerRepository,
) {
    private val _homeDialogOpen = MutableStateFlow(false)
    /** True while Home shows a dialog of its own (session summary, What's New...); notes wait. */
    val homeDialogOpen: StateFlow<Boolean> = _homeDialogOpen.asStateFlow()

    fun setHomeDialogOpen(open: Boolean) { _homeDialogOpen.value = open }

    suspend fun begin() = playerRepo.withLock {
        runStep { state, inv -> MainStory.begin(state, inv) }
        playerRepo.updateFlagsUnlocked(playerRepo.getFlagsUnlocked().copy(storyPromptPending = false))
    }

    suspend fun declinePrompt() = playerRepo.updateFlagsAtomically { it.copy(storyPromptPending = false) }

    suspend fun onSessionStarted(skill: String, activityKey: String) =
        playerRepo.withLock { onSessionStartedUnlocked(skill, activityKey) }

    internal suspend fun onSessionStartedUnlocked(skill: String, activityKey: String) =
        runStep { state, inv -> MainStory.onEvent(state, MainStory.Event.SessionStarted(skill, activityKey), inv) }

    suspend fun onSessionCollected(skill: String, activityKey: String, items: Map<String, Int>) = playerRepo.withLock {
        runStep { state, inv -> MainStory.onEvent(state, MainStory.Event.SessionCollected(skill, activityKey, items), inv) }
    }

    suspend fun onBossDefeated(bossId: String) = playerRepo.withLock {
        runStep { state, inv -> MainStory.onEvent(state, MainStory.Event.BossDefeated(bossId), inv) }
    }

    suspend fun onDungeonCleared(dungeonKey: String) = playerRepo.withLock {
        runStep { state, inv -> MainStory.onEvent(state, MainStory.Event.DungeonCleared(dungeonKey), inv) }
    }

    /** Closes a note: read, and off the pop-up queue. */
    suspend fun markRead(noteId: String) = playerRepo.updateFlagsAtomically { f ->
        f.copy(storyRead = f.storyRead + noteId, storyPopupQueue = f.storyPopupQueue - noteId)
    }

    /** "Read later": off the pop-up queue but still unread in the Story tab. */
    suspend fun readLater(noteId: String) = playerRepo.updateFlagsAtomically { f ->
        f.copy(storyPopupQueue = f.storyPopupQueue - noteId)
    }

    suspend fun setIslandName(name: String) = playerRepo.updateFlagsAtomically { f ->
        f.copy(islandName = name.trim().take(ISLAND_NAME_MAX), islandNameAsked = true)
    }

    suspend fun skipIslandName() = playerRepo.updateFlagsAtomically { it.copy(islandNameAsked = true) }

    /** Turning pop-ups off also clears the queue; those notes stay unread in the tab. */
    suspend fun setPopups(enabled: Boolean) = playerRepo.updateFlagsAtomically { f ->
        f.copy(storyPopups = enabled, storyPopupQueue = if (enabled) f.storyPopupQueue else emptyList())
    }

    private suspend fun runStep(step: (MainStory.State, Map<String, Int>) -> MainStory.Outcome) {
        try {
            val flags = playerRepo.getFlagsUnlocked()
            if (flags.onElderIsle) return
            val out = step(flags.storyState(), playerRepo.getInventoryUnlocked())
            if (!out.changed && out.state == flags.storyState()) return
            playerRepo.updateFlagsUnlocked(flags.copy(
                storyStarted    = out.state.started,
                storyChapter    = out.state.chapter,
                storyCounters   = out.state.counters,
                storyDelivered  = out.state.delivered,
                storyPopupQueue = if (flags.storyPopups) flags.storyPopupQueue + out.newNotes else flags.storyPopupQueue,
                unlockedTitles  = flags.unlockedTitles + out.titles,
            ))
            // Coin and item writes re-read the row, so they run after the flags write.
            if (out.coins > 0) playerRepo.addCoinsUnlocked(out.coins)
            if (out.consumeItems.isNotEmpty()) playerRepo.consumeItemsUnlocked(out.consumeItems)
            if (out.grantItems.isNotEmpty()) playerRepo.addItemsUnlocked(out.grantItems)
        } catch (e: Exception) {
            Log.w("StoryRepository", "story step failed", e)
        }
    }

    companion object {
        const val ISLAND_NAME_MAX = 24
    }

    private fun PlayerFlags.storyState() = MainStory.State(
        started   = storyStarted,
        chapter   = storyChapter,
        counters  = storyCounters,
        delivered = storyDelivered,
    )
}
