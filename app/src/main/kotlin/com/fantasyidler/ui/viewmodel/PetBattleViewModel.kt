package com.fantasyidler.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fantasyidler.data.model.SessionFrame
import com.fantasyidler.data.model.SkillSession
import com.fantasyidler.repository.GameDataRepository
import com.fantasyidler.repository.PetArenaCollectResult
import com.fantasyidler.repository.PetArenaRepository
import com.fantasyidler.repository.SessionRepository
import com.fantasyidler.simulator.PetBattle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

data class PetBattleUiState(
    val isLoading: Boolean = true,
    /** Null when the arena slot is empty. */
    val sessionId: String? = null,
    val zoneId: String? = null,
    val trainerId: String? = null,
    val ultimateId: String? = null,
    val startedAt: Long = 0L,
    val endsAt: Long = 0L,
    val completed: Boolean = false,
    /** Null for battles started before replays existed (no seed stored). */
    val replay: PetBattleReplay? = null,
    val result: PetArenaCollectResult? = null,
)

/** The Pokemon-style battle screen: replays the running arena session in sync with the clock. */
@HiltViewModel
class PetBattleViewModel @Inject constructor(
    sessionRepo: SessionRepository,
    private val arenaRepo: PetArenaRepository,
    private val gameData: GameDataRepository,
    private val json: Json,
) : ViewModel() {

    private val result = MutableStateFlow<PetArenaCollectResult?>(null)
    private var cachedId: String? = null
    private var cachedReplay: PetBattleReplay? = null

    val petEmoji: Map<String, String> = gameData.pets.mapValues { it.value.emoji }
    val petData: Map<String, com.fantasyidler.data.json.PetData> = gameData.pets
    fun creatureEmoji(id: String): String =
        gameData.petArena.creatures[id]?.emoji ?: gameData.petArena.ultimate?.takeIf { it.id == id }?.emoji ?: "❔"
    fun trainerEmoji(id: String): String = gameData.petArena.trainers.firstOrNull { it.id == id }?.emoji ?: "🧑"

    val uiState: StateFlow<PetBattleUiState> = combine(
        sessionRepo.activeWorkerSessionFlow(PetArenaRepository.PET_ARENA_SLOT),
        result,
    ) { session, r ->
        if (session == null) return@combine PetBattleUiState(isLoading = false, result = r)
        val replay = if (session.sessionId == cachedId) cachedReplay
            else buildReplay(session).also { cachedId = session.sessionId; cachedReplay = it }
        PetBattleUiState(
            isLoading = false,
            sessionId = session.sessionId,
            zoneId    = PetArenaRepository.zoneIdOf(session.activityKey),
            trainerId = PetArenaRepository.trainerIdOf(session.activityKey),
            ultimateId = PetArenaRepository.ultimateIdOf(session.activityKey),
            startedAt = session.startedAt,
            endsAt    = session.endsAt,
            completed = session.completed,
            replay    = replay,
            result    = r,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PetBattleUiState())

    private fun buildReplay(session: SkillSession): PetBattleReplay? = try {
        val frames: List<SessionFrame> = json.decodeFromString(session.frames)
        val first = frames.firstOrNull()
        val seed = first?.petSeed
        val team = first?.petTeamStart.orEmpty()
        val startHp = first?.petStartHp?.takeIf { it.size == team.size }
        if (seed == null || team.isEmpty()) null else {
            val arena = gameData.petArena
            val events = PetArenaRepository.ultimateIdOf(session.activityKey)
                ?.let { id -> arena.ultimate?.takeIf { it.id == id } }
                ?.let { PetBattle.replayUltimate(team, it, seed, startHp) }
                ?: PetArenaRepository.trainerIdOf(session.activityKey)
                ?.let { id -> arena.trainers.firstOrNull { it.id == id } }
                ?.let { PetBattle.replayTrainer(team, it, arena.creatures, seed, startHp) }
                ?: PetArenaRepository.zoneIdOf(session.activityKey)
                    ?.let { id -> arena.zones.firstOrNull { it.id == id } }
                    ?.let { PetBattle.replayZone(team, it, arena.creatures, seed, startHp) }
            events?.let { PetBattleReplay(team, it) }
        }
    } catch (_: Exception) { null }

    fun refreshCompletion() {
        viewModelScope.launch { arenaRepo.completeIfExpired() }
    }

    fun collect() {
        viewModelScope.launch { result.value = arenaRepo.collect() }
    }

    fun resultConsumed() { result.value = null }
}
