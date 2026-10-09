package com.fantasyidler.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fantasyidler.data.model.ElderQuests
import com.fantasyidler.data.model.PlayerFlags
import com.fantasyidler.repository.PlayerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

data class ElderQuestRow(
    val quest: ElderQuests.Quest,
    val progress: Int,
    val complete: Boolean,
    val unlocked: Boolean,
)

data class ElderQuestsState(
    val rows: List<ElderQuestRow> = emptyList(),
    val completedIds: Set<String> = emptySet(),
    /** Set when a quest just auto-completed, so the Quests tab can pop a snackbar. */
    val snackbarMessage: String? = null,
)

/**
 * Watches player state; whenever a quest's predicate is met for the first time, it stamps
 * the id into PlayerFlags.elderQuestsCompleted. Auto-completion means the player never
 * has to "claim" a quest — like the mainland expedition/note flow.
 */
@HiltViewModel
class ElderQuestsViewModel @Inject constructor(
    private val playerRepo: PlayerRepository,
    private val json: Json,
) : ViewModel() {

    private val _snack = MutableStateFlow<String?>(null)

    val state: StateFlow<ElderQuestsState> = combine(
        playerRepo.playerFlow.map { p ->
            if (p == null) return@map ElderQuestsState()
            val flags: PlayerFlags = try { json.decodeFromString(p.flags) } catch (_: Exception) { PlayerFlags() }
            val inv: Map<String, Int> = try { json.decodeFromString(p.inventory) } catch (_: Exception) { emptyMap() }
            val snap = ElderQuests.Snapshot(
                dungeonRuns = flags.dungeonRuns,
                enemyKills  = flags.enemyKills,
                inventory   = inv,
            )
            val completed = flags.elderQuestsCompleted
            val unlocked  = ElderQuests.unlockedIds(completed)
            val rows = ElderQuests.CHAIN.map { q ->
                ElderQuestRow(
                    quest    = q,
                    progress = ElderQuests.displayProgress(q, snap, completed),
                    complete = q.id in completed,
                    unlocked = q.id in unlocked,
                )
            }
            ElderQuestsState(rows = rows, completedIds = completed)
        },
        _snack,
    ) { base, snack -> base.copy(snackbarMessage = snack) }
        .onEach { s ->
            // Stamp any newly-met predicates into the persistent completed set. Re-reads
            // player state through the repo (playerFlow is cold, no `.value` on it).
            viewModelScope.launch {
                val current = playerRepo.getFlags()
                val invStr  = playerRepo.getOrCreatePlayer().inventory
                val inv: Map<String, Int> = try { json.decodeFromString(invStr) } catch (_: Exception) { emptyMap() }
                val snap = ElderQuests.Snapshot(
                    dungeonRuns = current.dungeonRuns,
                    enemyKills  = current.enemyKills,
                    inventory   = inv,
                )
                val newly = ElderQuests.newlyCompleted(current.elderQuestsCompleted, snap)
                if (newly.isNotEmpty()) {
                    val titles = current.unlockedTitles + (if ("act4_face_last_elder" in newly) setOf("isle_champion") else emptySet())
                    playerRepo.updateFlags(current.copy(
                        elderQuestsCompleted = current.elderQuestsCompleted + newly,
                        unlockedTitles       = titles,
                    ))
                    val questTitle = ElderQuests.CHAIN.firstOrNull { it.id in newly }?.title
                    if (questTitle != null) _snack.value = "Isle quest complete: $questTitle"
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ElderQuestsState())

    fun snackbarConsumed() { _snack.value = null }
}
