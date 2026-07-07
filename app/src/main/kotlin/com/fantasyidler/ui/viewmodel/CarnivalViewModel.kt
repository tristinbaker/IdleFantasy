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
import com.fantasyidler.repository.TownRepository
import com.fantasyidler.simulator.SkillSimulator
import com.fantasyidler.util.withAppLocale
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

enum class Difficulty { NORMAL, HARD }

sealed class ActiveGameState {
    object Ready : ActiveGameState()
    object TimingActive : ActiveGameState()
    data class SequenceShowing(val sequence: List<Int>, val currentIndex: Int) : ActiveGameState()
    data class SequenceInput(val sequence: List<Int>, val userInput: List<Int>) : ActiveGameState()
    data class AppraisalPlaying(val quadIndex: Int) : ActiveGameState()
    data class OnCooldown(val resumesAtMs: Long) : ActiveGameState()
    data class ShellGameShowing(val cupCount: Int, val gemPos: Int) : ActiveGameState()
    data class ShellGameSwapping(val cupCount: Int, val gemPos: Int, val swaps: List<Pair<Int,Int>>) : ActiveGameState()
    data class ShellGamePicking(val cupCount: Int, val gemPos: Int) : ActiveGameState()
    data class HigherOrLowerPlaying(val numbers: List<Int>, val currentIdx: Int, val correctCount: Int) : ActiveGameState()
    data class HigherOrLowerResult(val lastNumber: Int, val totalCorrect: Int, val totalRounds: Int, val tickets: Int, val resumesAtMs: Long) : ActiveGameState()
}

data class AppraisalPair(val itemA: String, val itemB: String, val correctIsA: Boolean)

data class AppraisalQuad(val items: List<String>, val correctIdx: Int)

data class CarnivalUiState(
    val isLoading: Boolean = true,
    val ticketBalance: Int = 0,
    val selectedTab: Int = 0,
    val tabInitialized: Boolean = false,
    val skillLevels: Map<String, Int> = emptyMap(),
    val tierBonus: Float = 0f,
    val queueSize: Int = 0,
    val maxQueueSize: Int = 3,
    val ownedPrizeKeys: Set<String> = emptySet(),
    val snackbarMessage: String? = null,
    val ringTossState: ActiveGameState = ActiveGameState.Ready,
    val hammerStrikeState: ActiveGameState = ActiveGameState.Ready,
    val potionSequenceState: ActiveGameState = ActiveGameState.Ready,
    val itemAppraisalState: ActiveGameState = ActiveGameState.Ready,
    val shellGameState: ActiveGameState = ActiveGameState.Ready,
    val higherLowerState: ActiveGameState = ActiveGameState.Ready,
    val currentAppraisalPair: AppraisalPair? = null,
    val currentAppraisalQuad: AppraisalQuad? = null,
    val pendingLampPrizeKey: String? = null,
    val ringTossDifficulty: Difficulty = Difficulty.NORMAL,
    val hammerStrikeDifficulty: Difficulty = Difficulty.NORMAL,
    val potionSequenceDifficulty: Difficulty = Difficulty.NORMAL,
    val itemAppraisalDifficulty: Difficulty = Difficulty.NORMAL,
    val shellGameDifficulty: Difficulty = Difficulty.NORMAL,
    val higherLowerDifficulty: Difficulty = Difficulty.NORMAL,
    val carnivalGameCount: Int = 4,
    val carnivalCooldownMs: Long = 10L * 60_000L,
)

@HiltViewModel
class CarnivalViewModel @Inject constructor(
    private val playerRepo: PlayerRepository,
    private val carnivalRepo: CarnivalRepository,
    val gameData: GameDataRepository,
    private val townRepo: TownRepository,
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

    // Hard mode: 4-item quads — correctIdx = index of the most valuable item
    private val APPRAISAL_QUADS = listOf(
        AppraisalQuad(listOf("Dragon Sword", "Rune Sword", "Adamant Sword", "Iron Sword"), 0),
        AppraisalQuad(listOf("Magic Log", "Yew Log", "Maple Log", "Oak Log"), 0),
        AppraisalQuad(listOf("Raw Shark", "Raw Lobster", "Raw Trout", "Raw Herring"), 0),
        AppraisalQuad(listOf("Diamond", "Ruby", "Sapphire", "Opal"), 0),
        AppraisalQuad(listOf("Runite Ore", "Adamantite Ore", "Mithril Ore", "Iron Ore"), 0),
        AppraisalQuad(listOf("Dragon Bone", "Giant Bones", "Big Bones", "Bones"), 0),
        AppraisalQuad(listOf("Rune Bar", "Adamant Bar", "Steel Bar", "Bronze Bar"), 0),
        AppraisalQuad(listOf("Yew Log", "Maple Log", "Willow Log", "Oak Log"), 0),
        AppraisalQuad(listOf("Gold Bar", "Steel Bar", "Iron Bar", "Bronze Bar"), 0),
        AppraisalQuad(listOf("Rune Sword", "Adamant Sword", "Mithril Sword", "Iron Sword"), 0),
    )

    private val _extra = MutableStateFlow(CarnivalUiState())

    init {
        viewModelScope.launch {
            val diffs = playerRepo.getFlags().carnivalDifficulties
            if (diffs.isNotEmpty()) {
                fun diff(key: String) = diffs[key]?.uppercase()?.let { runCatching { Difficulty.valueOf(it) }.getOrNull() }
                _extra.update { s -> s.copy(
                    ringTossDifficulty       = diff("ring_toss")       ?: s.ringTossDifficulty,
                    hammerStrikeDifficulty   = diff("hammer_strike")   ?: s.hammerStrikeDifficulty,
                    potionSequenceDifficulty = diff("potion_sequence") ?: s.potionSequenceDifficulty,
                    itemAppraisalDifficulty  = diff("item_appraisal")  ?: s.itemAppraisalDifficulty,
                    shellGameDifficulty      = diff("shell_game")      ?: s.shellGameDifficulty,
                    higherLowerDifficulty    = diff("higher_lower")    ?: s.higherLowerDifficulty,
                ) }
            }
        }
    }

    val uiState: StateFlow<CarnivalUiState> = combine(
        playerRepo.playerFlow,
        _extra,
    ) { player, extra ->
        if (player == null) extra.copy(isLoading = true)
        else {
            val inventory: Map<String, Int> = json.decodeFromString(player.inventory)
            val flags: PlayerFlags = json.decodeFromString(player.flags)
            val levels: Map<String, Int> = json.decodeFromString(player.skillLevels)
            val pets: List<com.fantasyidler.data.model.OwnedPet> = try { json.decodeFromString(player.pets) } catch (_: Exception) { emptyList() }
            val ownedPetIds = pets.map { it.id }.toSet()
            val ownedPrizeKeys = prizes
                .filter { it.type == "equipment" || it.type == "pet" }
                .filter { (inventory[it.key] ?: 0) > 0 || it.key in ownedPetIds }
                .map { it.key }
                .toSet()
            val now = System.currentTimeMillis()
            fun resolveState(state: ActiveGameState, cooldownAt: Long): ActiveGameState =
                if (cooldownAt > now) ActiveGameState.OnCooldown(cooldownAt) else state
            extra.copy(
                isLoading           = false,
                ticketBalance       = inventory["carnival_ticket"] ?: 0,
                skillLevels         = levels,
                tierBonus           = townRepo.idleTicketBonusChance(flags),
                queueSize           = flags.sessionQueue.size,
                maxQueueSize        = townRepo.maxQueueSize(flags),
                ownedPrizeKeys      = ownedPrizeKeys,
                selectedTab         = if (!extra.tabInitialized) flags.carnivalTab else extra.selectedTab,
                tabInitialized      = true,
                ringTossState       = resolveState(extra.ringTossState, flags.carnivalRingTossCooldownAt),
                hammerStrikeState   = resolveState(extra.hammerStrikeState, flags.carnivalHammerStrikeCooldownAt),
                potionSequenceState = resolveState(extra.potionSequenceState, flags.carnivalPotionSequenceCooldownAt),
                itemAppraisalState  = resolveState(extra.itemAppraisalState, flags.carnivalItemAppraisalCooldownAt),
                shellGameState      = resolveState(extra.shellGameState, flags.carnivalShellGameCooldownAt),
                higherLowerState    = resolveState(extra.higherLowerState, flags.carnivalHigherLowerCooldownAt),
                carnivalGameCount   = townRepo.carnivalGameCount(flags),
                carnivalCooldownMs  = townRepo.carnivalCooldownMs(flags),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CarnivalUiState())

    fun selectTab(index: Int) {
        _extra.update { it.copy(selectedTab = index, tabInitialized = true) }
        viewModelScope.launch {
            val flags = playerRepo.getFlags()
            playerRepo.updateFlags(flags.copy(carnivalTab = index))
        }
    }

    fun snackbarConsumed() = _extra.update { it.copy(snackbarMessage = null) }

    // ── Difficulty selectors ───────────────────────────────────────────────────

    private fun persistDifficulty(key: String, d: Difficulty) {
        viewModelScope.launch {
            val flags = playerRepo.getFlags()
            playerRepo.updateFlags(flags.copy(carnivalDifficulties = flags.carnivalDifficulties + (key to d.name.lowercase())))
        }
    }

    fun setRingTossDifficulty(d: Difficulty)       { _extra.update { it.copy(ringTossDifficulty = d) };       persistDifficulty("ring_toss", d) }
    fun setHammerStrikeDifficulty(d: Difficulty)   { _extra.update { it.copy(hammerStrikeDifficulty = d) };   persistDifficulty("hammer_strike", d) }
    fun setPotionSequenceDifficulty(d: Difficulty) { _extra.update { it.copy(potionSequenceDifficulty = d) }; persistDifficulty("potion_sequence", d) }
    fun setItemAppraisalDifficulty(d: Difficulty)  { _extra.update { it.copy(itemAppraisalDifficulty = d) };  persistDifficulty("item_appraisal", d) }
    fun setShellGameDifficulty(d: Difficulty)      { _extra.update { it.copy(shellGameDifficulty = d) };      persistDifficulty("shell_game", d) }
    fun setHigherLowerDifficulty(d: Difficulty)    { _extra.update { it.copy(higherLowerDifficulty = d) };    persistDifficulty("higher_lower", d) }

    // ── Idle game queueing ─────────────────────────────────────────────────────

    fun queueIdleGame(activityKey: String, displayName: String) {
        viewModelScope.launch {
            val player = playerRepo.getOrCreatePlayer()
            val agility = (json.decodeFromString<Map<String, Int>>(player.skillLevels))[Skills.AGILITY] ?: 1
            val carnivalFlags: PlayerFlags = json.decodeFromString(player.flags)
            val enqueued = playerRepo.enqueueAction(
                QueuedAction(
                    skillName           = "carnival",
                    activityKey         = activityKey,
                    skillDisplayName    = displayName,
                    estimatedDurationMs = SkillSimulator.sessionDurationMs(agility, carnivalFlags.skillPrestige[Skills.AGILITY] ?: 0),
                )
            )
            if (enqueued) queuedSessionStarter.startNextQueued()
            _extra.update {
                it.copy(snackbarMessage = if (enqueued)
                    context.withAppLocale().getString(R.string.carnival_queue_added, displayName)
                else
                    context.withAppLocale().getString(R.string.carnival_queue_full))
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
        val diff = _extra.value.ringTossDifficulty
        val won = if (diff == Difficulty.HARD) position in 0.52f..0.57f else position in 0.45f..0.55f
        val tickets = if (won) (if (diff == Difficulty.HARD) 7 else 2) else 0
        viewModelScope.launch {
            if (tickets > 0) carnivalRepo.awardTickets(tickets)
            val cooldownMs = uiState.value.carnivalCooldownMs
            val resumesAt = System.currentTimeMillis() + cooldownMs
            playerRepo.updateFlags(playerRepo.getFlags().copy(carnivalRingTossCooldownAt = resumesAt))
            _extra.update {
                it.copy(
                    ringTossState   = ActiveGameState.OnCooldown(resumesAt),
                    snackbarMessage = if (won)
                        context.withAppLocale().getString(R.string.carnival_ring_won, tickets)
                    else
                        context.withAppLocale().getString(R.string.carnival_ring_missed),
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
        val diff = _extra.value.hammerStrikeDifficulty
        val tickets = if (diff == Difficulty.HARD) {
            when {
                position >= 0.87f -> 8
                position >= 0.60f -> 6
                else              -> 0
            }
        } else {
            when {
                position >= 0.80f -> 2
                position >= 0.50f -> 1
                else              -> 0
            }
        }
        viewModelScope.launch {
            if (tickets > 0) carnivalRepo.awardTickets(tickets)
            val cooldownMs = uiState.value.carnivalCooldownMs
            val resumesAt = System.currentTimeMillis() + cooldownMs
            playerRepo.updateFlags(playerRepo.getFlags().copy(carnivalHammerStrikeCooldownAt = resumesAt))
            _extra.update {
                it.copy(
                    hammerStrikeState = ActiveGameState.OnCooldown(resumesAt),
                    snackbarMessage   = when {
                        tickets >= 6 -> context.withAppLocale().getString(R.string.carnival_hammer_perfect, tickets)
                        tickets > 0  -> context.withAppLocale().getString(R.string.carnival_hammer_good, tickets)
                        else         -> context.withAppLocale().getString(R.string.carnival_hammer_miss)
                    },
                )
            }
        }
    }

    // ── Active game: Potion Sequence (memory) ──────────────────────────────────

    fun startPotionSequence() {
        if (_extra.value.potionSequenceState !is ActiveGameState.Ready) return
        val diff = _extra.value.potionSequenceDifficulty
        val colorCount = 6
        val seqLength  = if (diff == Difficulty.HARD) 8 else 6
        val seq = List(seqLength) { Random.nextInt(colorCount) }
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
                val cooldownMs = uiState.value.carnivalCooldownMs
                val resumesAt = System.currentTimeMillis() + cooldownMs
                playerRepo.updateFlags(playerRepo.getFlags().copy(carnivalPotionSequenceCooldownAt = resumesAt))
                _extra.update {
                    it.copy(
                        potionSequenceState = ActiveGameState.OnCooldown(resumesAt),
                        snackbarMessage     = context.withAppLocale().getString(R.string.carnival_sequence_wrong),
                    )
                }
            }
            return
        }
        if (newInput.size == state.sequence.size) {
            val diff = _extra.value.potionSequenceDifficulty
            val tickets = if (diff == Difficulty.HARD) 7 else 2
            viewModelScope.launch {
                carnivalRepo.awardTickets(tickets)
                val cooldownMs = uiState.value.carnivalCooldownMs
                val resumesAt = System.currentTimeMillis() + cooldownMs
                playerRepo.updateFlags(playerRepo.getFlags().copy(carnivalPotionSequenceCooldownAt = resumesAt))
                _extra.update {
                    it.copy(
                        potionSequenceState = ActiveGameState.OnCooldown(resumesAt),
                        snackbarMessage     = context.withAppLocale().getString(R.string.carnival_sequence_correct, tickets),
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
        val diff = _extra.value.itemAppraisalDifficulty
        if (diff == Difficulty.HARD) {
            val raw = APPRAISAL_QUADS[Random.nextInt(APPRAISAL_QUADS.size)]
            val shuffled = raw.items.shuffled()
            val quad = raw.copy(items = shuffled, correctIdx = shuffled.indexOf(raw.items[raw.correctIdx]))
            _extra.update {
                it.copy(
                    itemAppraisalState   = ActiveGameState.AppraisalPlaying(quad.correctIdx),
                    currentAppraisalQuad = quad,
                    currentAppraisalPair = null,
                )
            }
        } else {
            val raw = APPRAISAL_PAIRS[Random.nextInt(APPRAISAL_PAIRS.size)]
            val pair = if (Random.nextBoolean()) raw
                       else AppraisalPair(raw.itemB, raw.itemA, !raw.correctIsA)
            _extra.update {
                it.copy(
                    itemAppraisalState   = ActiveGameState.AppraisalPlaying(if (pair.correctIsA) 0 else 1),
                    currentAppraisalPair = pair,
                    currentAppraisalQuad = null,
                )
            }
        }
    }

    fun submitAppraisalAnswer(chosenIdx: Int) {
        val state = _extra.value.itemAppraisalState
        if (state !is ActiveGameState.AppraisalPlaying) return
        val diff = _extra.value.itemAppraisalDifficulty
        val won: Boolean
        val correctName: String
        if (diff == Difficulty.HARD) {
            val quad = _extra.value.currentAppraisalQuad ?: return
            won = chosenIdx == quad.correctIdx
            correctName = quad.items[quad.correctIdx]
        } else {
            val pair = _extra.value.currentAppraisalPair ?: return
            won = (chosenIdx == 0) == pair.correctIsA
            correctName = if (pair.correctIsA) pair.itemA else pair.itemB
        }
        val tickets = if (won) (if (diff == Difficulty.HARD) 7 else 2) else 0
        viewModelScope.launch {
            if (tickets > 0) carnivalRepo.awardTickets(tickets)
            val cooldownMs = uiState.value.carnivalCooldownMs
            val resumesAt = System.currentTimeMillis() + cooldownMs
            playerRepo.updateFlags(playerRepo.getFlags().copy(carnivalItemAppraisalCooldownAt = resumesAt))
            _extra.update {
                it.copy(
                    itemAppraisalState  = ActiveGameState.OnCooldown(resumesAt),
                    snackbarMessage     = if (won)
                        context.withAppLocale().getString(R.string.carnival_appraisal_correct, tickets)
                    else
                        context.withAppLocale().getString(R.string.carnival_appraisal_wrong, correctName),
                )
            }
        }
    }

    // ── Active game: Pick-a-Cup (shell game) ───────────────────────────────────

    fun startShellGame() {
        if (_extra.value.shellGameState !is ActiveGameState.Ready) return
        val diff = _extra.value.shellGameDifficulty
        val cupCount = if (diff == Difficulty.HARD) 4 else 3
        val gemPos   = Random.nextInt(cupCount)
        _extra.update { it.copy(shellGameState = ActiveGameState.ShellGameShowing(cupCount, gemPos)) }
    }

    fun advanceShellGame() {
        val s = _extra.value.shellGameState
        if (s !is ActiveGameState.ShellGameShowing) return
        val swapCount = s.cupCount * 2 + Random.nextInt(s.cupCount)
        val swapPairs = (0 until swapCount).map {
            val a = Random.nextInt(s.cupCount)
            val b = (a + 1 + Random.nextInt(s.cupCount - 1)) % s.cupCount
            Pair(a, b)
        }
        _extra.update { it.copy(shellGameState = ActiveGameState.ShellGameSwapping(s.cupCount, s.gemPos, swapPairs)) }
    }

    fun finishShellGame(newGemPos: Int) {
        val s = _extra.value.shellGameState
        if (s !is ActiveGameState.ShellGameSwapping) return
        _extra.update { it.copy(shellGameState = ActiveGameState.ShellGamePicking(s.cupCount, newGemPos)) }
    }

    fun submitShellGuess(pickedPos: Int) {
        val s = _extra.value.shellGameState
        if (s !is ActiveGameState.ShellGamePicking) return
        val diff = _extra.value.shellGameDifficulty
        val won = pickedPos == s.gemPos
        val tickets = if (won) (if (diff == Difficulty.HARD) 7 else 4) else 0
        viewModelScope.launch {
            if (tickets > 0) carnivalRepo.awardTickets(tickets)
            val cooldownMs = uiState.value.carnivalCooldownMs
            val resumesAt = System.currentTimeMillis() + cooldownMs
            playerRepo.updateFlags(playerRepo.getFlags().copy(carnivalShellGameCooldownAt = resumesAt))
            _extra.update {
                it.copy(
                    shellGameState  = ActiveGameState.OnCooldown(resumesAt),
                    snackbarMessage = if (won)
                        context.withAppLocale().getString(R.string.carnival_shell_won, tickets)
                    else
                        context.withAppLocale().getString(R.string.carnival_shell_missed, s.gemPos + 1),
                )
            }
        }
    }

    // ── Active game: Higher or Lower ───────────────────────────────────────────

    fun startHigherOrLower() {
        if (_extra.value.higherLowerState !is ActiveGameState.Ready) return
        val diff = _extra.value.higherLowerDifficulty
        val totalRounds = if (diff == Difficulty.HARD) 7 else 5
        val numbers = buildList {
            add(Random.nextInt(1, 11))
            repeat(totalRounds) {
                var n: Int
                do { n = Random.nextInt(1, 11) } while (n == last())
                add(n)
            }
        }
        _extra.update { it.copy(higherLowerState = ActiveGameState.HigherOrLowerPlaying(numbers, 0, 0)) }
    }

    fun submitHigherOrLower(guessHigher: Boolean) {
        val state = _extra.value.higherLowerState
        if (state !is ActiveGameState.HigherOrLowerPlaying) return
        val diff = _extra.value.higherLowerDifficulty
        val current = state.numbers[state.currentIdx]
        val next    = state.numbers[state.currentIdx + 1]
        val correct = when {
            next > current -> guessHigher
            next < current -> !guessHigher
            else           -> false // tie = wrong
        }
        val newCorrect = state.correctCount + if (correct) 1 else 0
        val newIdx     = state.currentIdx + 1
        val totalRounds = state.numbers.size - 1
        if (newIdx >= totalRounds) {
            val tickets = if (diff == Difficulty.HARD) {
                when {
                    newCorrect >= 6 -> 8
                    newCorrect >= 5 -> 4
                    newCorrect >= 4 -> 1
                    else            -> 0
                }
            } else {
                when {
                    newCorrect >= 4 -> 5
                    newCorrect >= 3 -> 2
                    else            -> 0
                }
            }
            viewModelScope.launch {
                if (tickets > 0) carnivalRepo.awardTickets(tickets)
                val cooldownMs = uiState.value.carnivalCooldownMs
                val resumesAt = System.currentTimeMillis() + cooldownMs
                playerRepo.updateFlags(playerRepo.getFlags().copy(carnivalHigherLowerCooldownAt = resumesAt))
                _extra.update {
                    it.copy(higherLowerState = ActiveGameState.HigherOrLowerResult(next, newCorrect, totalRounds, tickets, resumesAt))
                }
            }
        } else {
            _extra.update {
                it.copy(higherLowerState = ActiveGameState.HigherOrLowerPlaying(state.numbers, newIdx, newCorrect))
            }
        }
    }

    fun confirmHigherOrLowerResult() {
        val s = _extra.value.higherLowerState
        if (s !is ActiveGameState.HigherOrLowerResult) return
        _extra.update { it.copy(higherLowerState = ActiveGameState.OnCooldown(s.resumesAtMs)) }
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
                "shell_game" -> {
                    val gs = s.shellGameState
                    if (gs is ActiveGameState.OnCooldown && now >= gs.resumesAtMs) s.copy(shellGameState = ActiveGameState.Ready) else s
                }
                "higher_lower" -> {
                    val gs = s.higherLowerState
                    if (gs is ActiveGameState.OnCooldown && now >= gs.resumesAtMs) s.copy(higherLowerState = ActiveGameState.Ready) else s
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
                            context.withAppLocale().getString(R.string.carnival_redeemed, prize.displayName)
                        else
                            context.withAppLocale().getString(R.string.carnival_not_enough_tickets))
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
                    context.withAppLocale().getString(R.string.carnival_lamp_redeemed, prize.xpAmount, skillKey)
                else
                    context.withAppLocale().getString(R.string.carnival_not_enough_tickets))
            }
        }
    }

    fun dismissLampPicker() = _extra.update { it.copy(pendingLampPrizeKey = null) }
}
