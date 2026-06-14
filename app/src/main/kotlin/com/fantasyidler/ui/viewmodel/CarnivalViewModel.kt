package com.fantasyidler.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fantasyidler.R
import com.fantasyidler.data.json.CarnivalPrize
import com.fantasyidler.data.model.PlayerFlags
import com.fantasyidler.data.model.QueuedAction
import com.fantasyidler.data.model.Skills
import com.fantasyidler.repository.CarnivalRepository
import com.fantasyidler.repository.GameDataRepository
import com.fantasyidler.repository.PlayerRepository
import com.fantasyidler.repository.QueuedSessionStarter
import com.fantasyidler.simulator.SkillSimulator
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject
import kotlin.random.Random

sealed class ActiveGameState {
    object Ready : ActiveGameState()
    object TimingActive : ActiveGameState()
    data class SequenceShowing(val sequence: List<Int>, val currentIndex: Int) : ActiveGameState()
    data class SequenceInput(val sequence: List<Int>, val userInput: List<Int>) : ActiveGameState()
    data class AppraisalPlaying(val pairIndex: Int) : ActiveGameState()
    data class OnCooldown(val resumesAtMs: Long) : ActiveGameState()
}

data class AppraisalPair(val itemA: String, val itemB: String, val correctIsA: Boolean)

data class CarnivalUiState(
    val isLoading: Boolean = true,
    val ticketBalance: Int = 0,
    val selectedTab: Int = 0,
    val skillLevels: Map<String, Int> = emptyMap(),
    val queueSize: Int = 0,
    val ownedPrizeKeys: Set<String> = emptySet(),
    val snackbarMessage: String? = null,
    val ringTossState: ActiveGameState = ActiveGameState.Ready,
    val hammerStrikeState: ActiveGameState = ActiveGameState.Ready,
    val potionSequenceState: ActiveGameState = ActiveGameState.Ready,
    val itemAppraisalState: ActiveGameState = ActiveGameState.Ready,
    val currentAppraisalPair: AppraisalPair? = null,
    val pendingLampPrizeKey: String? = null,
)

@HiltViewModel
class CarnivalViewModel @Inject constructor(
    private val playerRepo: PlayerRepository,
    private val carnivalRepo: CarnivalRepository,
    val gameData: GameDataRepository,
    private val queuedSessionStarter: QueuedSessionStarter,
    @ApplicationContext private val context: Context,
    private val json: Json,
) : ViewModel() {

    val prizes: List<CarnivalPrize> by lazy { carnivalRepo.prizes.values.toList() }
    val prizesMap: Map<String, CarnivalPrize> by lazy { carnivalRepo.prizes }

    private val APPRAISAL_PAIRS = listOf(
        AppraisalPair("Dragon Sword", "Iron Sword", true),
        AppraisalPair("Magic Log", "Oak Log", true),
        AppraisalPair("Raw Shark", "Raw Trout", true),
        AppraisalPair("Diamond", "Sapphire", true),
        AppraisalPair("Runite Ore", "Iron Ore", true),
        AppraisalPair("Yew Log", "Willow Log", true),
        AppraisalPair("Gold Bar", "Bronze Bar", true),
        AppraisalPair("Raw Lobster", "Raw Herring", true),
        AppraisalPair("Mithril Ore", "Copper Ore", true),
        AppraisalPair("Iron Sword", "Dragon Sword", false),
        AppraisalPair("Oak Log", "Magic Log", false),
        AppraisalPair("Raw Trout", "Raw Shark", false),
        AppraisalPair("Copper Ore", "Mithril Ore", false),
        AppraisalPair("Willow Log", "Yew Log", false),
        AppraisalPair("Sapphire", "Diamond", false),
    )

    private val _extra = MutableStateFlow(CarnivalUiState())

    val uiState: StateFlow<CarnivalUiState> = combine(
        playerRepo.playerFlow,
        _extra,
    ) { player, extra ->
        if (player == null) extra.copy(isLoading = true)
        else {
            val inventory: Map<String, Int> = json.decodeFromString(player.inventory)
            val flags: PlayerFlags = json.decodeFromString(player.flags)
            val levels: Map<String, Int> = json.decodeFromString(player.skillLevels)
            val ownedPrizeKeys = prizes
                .filter { it.type == "equipment" || it.type == "pet" }
                .filter { (inventory[it.key] ?: 0) > 0 }
                .map { it.key }
                .toSet()
            extra.copy(
                isLoading      = false,
                ticketBalance  = inventory["carnival_ticket"] ?: 0,
                skillLevels    = levels,
                queueSize      = flags.sessionQueue.size,
                ownedPrizeKeys = ownedPrizeKeys,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CarnivalUiState())

    fun selectTab(index: Int) = _extra.update { it.copy(selectedTab = index) }

    fun snackbarConsumed() = _extra.update { it.copy(snackbarMessage = null) }

    // ── Idle game queueing ─────────────────────────────────────────────────────

    fun queueIdleGame(activityKey: String, displayName: String) {
        viewModelScope.launch {
            val player = playerRepo.getOrCreatePlayer()
            val agility = (json.decodeFromString<Map<String, Int>>(player.skillLevels))[Skills.AGILITY] ?: 1
            val enqueued = playerRepo.enqueueAction(
                QueuedAction(
                    skillName           = "carnival",
                    activityKey         = activityKey,
                    skillDisplayName    = displayName,
                    estimatedDurationMs = SkillSimulator.sessionDurationMs(agility),
                )
            )
            if (enqueued) queuedSessionStarter.startNextQueued()
            _extra.update {
                it.copy(snackbarMessage = if (enqueued)
                    context.getString(R.string.carnival_queue_added, displayName)
                else
                    context.getString(R.string.carnival_queue_full))
            }
        }
    }

    // ── Active game: Ring Toss (timing) ────────────────────────────────────────

    fun startRingToss() {
        if (_extra.value.ringTossState !is ActiveGameState.Ready) return
        _extra.update { it.copy(ringTossState = ActiveGameState.TimingActive) }
    }

    fun submitRingToss(position: Float) {
        if (_extra.value.ringTossState !is ActiveGameState.TimingActive) return
        val won = position in 0.45f..0.55f
        viewModelScope.launch {
            if (won) carnivalRepo.awardTickets(2)
            val resumesAt = System.currentTimeMillis() + 10 * 60_000L
            _extra.update {
                it.copy(
                    ringTossState   = ActiveGameState.OnCooldown(resumesAt),
                    snackbarMessage = if (won)
                        context.getString(R.string.carnival_ring_won, 2)
                    else
                        context.getString(R.string.carnival_ring_missed),
                )
            }
        }
    }

    // ── Active game: Hammer Strike (timing) ────────────────────────────────────

    fun startHammerStrike() {
        if (_extra.value.hammerStrikeState !is ActiveGameState.Ready) return
        _extra.update { it.copy(hammerStrikeState = ActiveGameState.TimingActive) }
    }

    fun submitHammerStrike(position: Float) {
        if (_extra.value.hammerStrikeState !is ActiveGameState.TimingActive) return
        val tickets = when {
            position >= 0.80f -> 2
            position >= 0.50f -> 1
            else              -> 0
        }
        viewModelScope.launch {
            if (tickets > 0) carnivalRepo.awardTickets(tickets)
            val resumesAt = System.currentTimeMillis() + 10 * 60_000L
            _extra.update {
                it.copy(
                    hammerStrikeState = ActiveGameState.OnCooldown(resumesAt),
                    snackbarMessage   = when (tickets) {
                        2    -> context.getString(R.string.carnival_hammer_perfect, 2)
                        1    -> context.getString(R.string.carnival_hammer_good, 1)
                        else -> context.getString(R.string.carnival_hammer_miss)
                    },
                )
            }
        }
    }

    // ── Active game: Potion Sequence (memory) ──────────────────────────────────

    fun startPotionSequence() {
        if (_extra.value.potionSequenceState !is ActiveGameState.Ready) return
        val seq = List(3) { Random.nextInt(4) }
        _extra.update { it.copy(potionSequenceState = ActiveGameState.SequenceShowing(seq, 0)) }
    }

    fun advancePotionSequence() {
        val state = _extra.value.potionSequenceState
        if (state !is ActiveGameState.SequenceShowing) return
        val nextIdx = state.currentIndex + 1
        _extra.update {
            it.copy(potionSequenceState = if (nextIdx < state.sequence.size)
                ActiveGameState.SequenceShowing(state.sequence, nextIdx)
            else
                ActiveGameState.SequenceInput(state.sequence, emptyList()))
        }
    }

    fun submitPotionInput(colorIndex: Int) {
        val state = _extra.value.potionSequenceState
        if (state !is ActiveGameState.SequenceInput) return
        val newInput = state.userInput + colorIndex
        val expectedSoFar = state.sequence.take(newInput.size)
        if (newInput != expectedSoFar) {
            viewModelScope.launch {
                val resumesAt = System.currentTimeMillis() + 10 * 60_000L
                _extra.update {
                    it.copy(
                        potionSequenceState = ActiveGameState.OnCooldown(resumesAt),
                        snackbarMessage     = context.getString(R.string.carnival_sequence_wrong),
                    )
                }
            }
            return
        }
        if (newInput.size == state.sequence.size) {
            viewModelScope.launch {
                carnivalRepo.awardTickets(2)
                val resumesAt = System.currentTimeMillis() + 10 * 60_000L
                _extra.update {
                    it.copy(
                        potionSequenceState = ActiveGameState.OnCooldown(resumesAt),
                        snackbarMessage     = context.getString(R.string.carnival_sequence_correct, 2),
                    )
                }
            }
        } else {
            _extra.update { it.copy(potionSequenceState = state.copy(userInput = newInput)) }
        }
    }

    // ── Active game: Item Appraisal (choice) ───────────────────────────────────

    fun startItemAppraisal() {
        if (_extra.value.itemAppraisalState !is ActiveGameState.Ready) return
        val pairIndex = Random.nextInt(APPRAISAL_PAIRS.size)
        _extra.update {
            it.copy(
                itemAppraisalState  = ActiveGameState.AppraisalPlaying(pairIndex),
                currentAppraisalPair = APPRAISAL_PAIRS[pairIndex],
            )
        }
    }

    fun submitAppraisalAnswer(chooseA: Boolean) {
        val state = _extra.value.itemAppraisalState
        val pair  = _extra.value.currentAppraisalPair
        if (state !is ActiveGameState.AppraisalPlaying || pair == null) return
        val won = (chooseA == pair.correctIsA)
        viewModelScope.launch {
            if (won) carnivalRepo.awardTickets(2)
            val resumesAt = System.currentTimeMillis() + 10 * 60_000L
            _extra.update {
                it.copy(
                    itemAppraisalState  = ActiveGameState.OnCooldown(resumesAt),
                    snackbarMessage     = if (won)
                        context.getString(R.string.carnival_appraisal_correct, 2)
                    else
                        context.getString(R.string.carnival_appraisal_wrong,
                            if (pair.correctIsA) pair.itemA else pair.itemB),
                )
            }
        }
    }

    // Called by the Screen's periodic ticker when a cooldown has expired
    fun clearCooldownIfExpired(gameKey: String) {
        val now = System.currentTimeMillis()
        _extra.update { s ->
            when (gameKey) {
                "ring_toss" -> {
                    val gs = s.ringTossState
                    if (gs is ActiveGameState.OnCooldown && now >= gs.resumesAtMs) s.copy(ringTossState = ActiveGameState.Ready) else s
                }
                "hammer_strike" -> {
                    val gs = s.hammerStrikeState
                    if (gs is ActiveGameState.OnCooldown && now >= gs.resumesAtMs) s.copy(hammerStrikeState = ActiveGameState.Ready) else s
                }
                "potion_sequence" -> {
                    val gs = s.potionSequenceState
                    if (gs is ActiveGameState.OnCooldown && now >= gs.resumesAtMs) s.copy(potionSequenceState = ActiveGameState.Ready) else s
                }
                "item_appraisal" -> {
                    val gs = s.itemAppraisalState
                    if (gs is ActiveGameState.OnCooldown && now >= gs.resumesAtMs) s.copy(itemAppraisalState = ActiveGameState.Ready) else s
                }
                else -> s
            }
        }
    }

    // ── Prize shop ─────────────────────────────────────────────────────────────

    fun redeem(prizeKey: String) {
        viewModelScope.launch {
            val prize = prizesMap[prizeKey] ?: return@launch
            when (prize.type) {
                "equipment", "pet" -> {
                    val success = carnivalRepo.redeemForItem(prizeKey, prize.ticketCost)
                    _extra.update {
                        it.copy(snackbarMessage = if (success)
                            context.getString(R.string.carnival_redeemed, prize.displayName)
                        else
                            context.getString(R.string.carnival_not_enough_tickets))
                    }
                }
                "xp_lamp" -> _extra.update { it.copy(pendingLampPrizeKey = prizeKey) }
            }
        }
    }

    fun redeemLamp(skillKey: String) {
        val prizeKey = _extra.value.pendingLampPrizeKey ?: return
        val prize    = prizesMap[prizeKey] ?: return
        _extra.update { it.copy(pendingLampPrizeKey = null) }
        viewModelScope.launch {
            val success = carnivalRepo.redeemForXp(skillKey, prize.xpAmount, prize.ticketCost)
            _extra.update {
                it.copy(snackbarMessage = if (success)
                    context.getString(R.string.carnival_lamp_redeemed, prize.xpAmount, skillKey)
                else
                    context.getString(R.string.carnival_not_enough_tickets))
            }
        }
    }

    fun dismissLampPicker() = _extra.update { it.copy(pendingLampPrizeKey = null) }
}
