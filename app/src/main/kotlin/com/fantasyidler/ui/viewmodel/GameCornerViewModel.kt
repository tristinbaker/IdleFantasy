package com.fantasyidler.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fantasyidler.data.model.PlayerFlags
import com.fantasyidler.repository.PlayerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

enum class GameTab { SLOTS, HIGH_LOW, LOTTERY, CARD_FLIP, BLACKJACK, ROULETTE, SCRATCH_CARD, VIDEO_POKER }

// ── Slots ────────────────────────────────────────────────────────────────────

enum class SlotSymbol(val display: String, val weight: Int, val multiplier: Int) {
    CHERRY("🍒", 8, 2),
    LEMON("🍋", 6, 3),
    ORANGE("🍊", 5, 4),
    BELL("🔔", 4, 8),
    BAR("BAR", 3, 10),
    SEVEN("7️⃣", 2, 20),
    DIAMOND("💎", 1, 50),
}

data class SlotsState(
    val reels: List<SlotSymbol> = listOf(SlotSymbol.CHERRY, SlotSymbol.CHERRY, SlotSymbol.CHERRY),
    val betAmount: Long = 100L,
    val lastWon: Long? = null,
)

// ── Dice / High-Low ──────────────────────────────────────────────────────────

enum class DicePhase { BETTING, PLAYER_CHOICE, RESULT_WIN, RESULT_LOSE, RESULT_PUSH }

data class DiceState(
    val phase: DicePhase = DicePhase.BETTING,
    val currentRoll: Int = 0,
    val nextRoll: Int = 0,
    val betAmount: Long = 100L,
    val pendingPot: Long = 0L,
)

// ── Card Flip ────────────────────────────────────────────────────────────────

data class FlipCard(val isAce: Boolean, val revealed: Boolean = false)

data class CardFlipState(
    val betting: Boolean = true,
    val cards: List<FlipCard> = emptyList(),
    val betAmount: Long = 100L,
    val lastWon: Long? = null,
)

// ── Blackjack ─────────────────────────────────────────────────────────────────

data class PlayingCard(val suit: Int, val rank: Int) {
    val faceValue: Int get() = if (rank > 10) 10 else rank
    val display: String get() = when (rank) {
        1 -> "A"; 11 -> "J"; 12 -> "Q"; 13 -> "K"; else -> rank.toString()
    }
    val suitSymbol: String get() = when (suit) {
        0 -> "♠"; 1 -> "♥"; 2 -> "♦"; else -> "♣"
    }
}

fun handValue(hand: List<PlayingCard>): Int {
    var total = hand.sumOf { it.faceValue }
    var aces = hand.count { it.rank == 1 }
    while (aces > 0 && total + 10 <= 21) { total += 10; aces-- }
    return total
}

enum class BlackjackPhase { BETTING, PLAYER_TURN, DEALER_TURN, RESULT }
enum class BlackjackResult { WIN, BLACKJACK, LOSE, PUSH, BUST }

data class BlackjackState(
    val phase: BlackjackPhase = BlackjackPhase.BETTING,
    val playerHand: List<PlayingCard> = emptyList(),
    val dealerHand: List<PlayingCard> = emptyList(),
    val deck: List<PlayingCard> = emptyList(),
    val betAmount: Long = 100L,
    val result: BlackjackResult? = null,
    val payout: Long = 0L,
)

// ── Roulette ──────────────────────────────────────────────────────────────────

enum class RouletteColor { RED, BLACK, GREEN }
enum class RouletteBetType { RED, BLACK, ODD, EVEN, DOZEN_1, DOZEN_2, DOZEN_3, NUMBER }

private val ROULETTE_RED = setOf(1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36)

fun rouletteColor(n: Int): RouletteColor = when {
    n == 0 -> RouletteColor.GREEN
    n in ROULETTE_RED -> RouletteColor.RED
    else -> RouletteColor.BLACK
}

data class RouletteState(
    val betType: RouletteBetType = RouletteBetType.RED,
    val selectedNumber: Int = 7,
    val betAmount: Long = 100L,
    val result: Int? = null,
    val lastWon: Long? = null,
)

// ── Scratch Card ──────────────────────────────────────────────────────────────

enum class ScratchSymbol(val display: String, val weight: Int, val multiplier: Int) {
    CHERRY("🍒", 30, 2),
    BAG("💰", 25, 5),
    STAR("⭐", 20, 10),
    BELL("🔔", 15, 20),
    DIAMOND("💎", 10, 50),
}

data class ScratchCell(val symbol: ScratchSymbol, val revealed: Boolean = false)

data class ScratchCardState(
    val cells: List<ScratchCell> = emptyList(),
    val price: Long = 500L,
    val won: Long? = null,
)

// ── Video Poker ───────────────────────────────────────────────────────────────

enum class VideoPokerPhase { BETTING, HOLDING, RESULT }

enum class PokerHand(val label: String, val multiplier: Int) {
    ROYAL_FLUSH("Royal Flush", 800),
    STRAIGHT_FLUSH("Straight Flush", 50),
    FOUR_OF_A_KIND("Four of a Kind", 25),
    FULL_HOUSE("Full House", 9),
    FLUSH("Flush", 6),
    STRAIGHT("Straight", 4),
    THREE_OF_A_KIND("Three of a Kind", 3),
    TWO_PAIR("Two Pair", 2),
    JACKS_OR_BETTER("Jacks or Better", 1),
}

data class VideoPokerState(
    val phase: VideoPokerPhase = VideoPokerPhase.BETTING,
    val hand: List<PlayingCard> = emptyList(),
    val held: List<Boolean> = List(5) { false },
    val deck: List<PlayingCard> = emptyList(),
    val betAmount: Long = 100L,
    val result: PokerHand? = null,
    val payout: Long = 0L,
)

fun evaluatePokerHand(hand: List<PlayingCard>): PokerHand? {
    val ranks = hand.map { it.rank }.sorted()
    val suits = hand.map { it.suit }
    val rankCounts = ranks.groupBy { it }.mapValues { it.value.size }
    val isFlush = suits.all { it == suits[0] }
    val normalized = ranks.map { if (it == 1) 14 else it }.sorted()
    val isStraight = normalized.zipWithNext().all { (a, b) -> b - a == 1 }
        || ranks == listOf(1, 2, 3, 4, 5)
    val counts = rankCounts.values
    return when {
        isFlush && normalized == listOf(10, 11, 12, 13, 14)  -> PokerHand.ROYAL_FLUSH
        isFlush && isStraight                                  -> PokerHand.STRAIGHT_FLUSH
        counts.maxOrNull() == 4                                -> PokerHand.FOUR_OF_A_KIND
        counts.containsAll(listOf(3, 2))                       -> PokerHand.FULL_HOUSE
        isFlush                                                -> PokerHand.FLUSH
        isStraight                                             -> PokerHand.STRAIGHT
        counts.maxOrNull() == 3                                -> PokerHand.THREE_OF_A_KIND
        counts.count { it == 2 } == 2                         -> PokerHand.TWO_PAIR
        rankCounts.any { (r, c) -> c == 2 && r in listOf(1, 11, 12, 13) } -> PokerHand.JACKS_OR_BETTER
        else                                                   -> null
    }
}

// ── Combined state ───────────────────────────────────────────────────────────

data class GameCornerExtra(
    val activeTab: GameTab = GameTab.SLOTS,
    val slotsState: SlotsState = SlotsState(),
    val diceState: DiceState = DiceState(),
    val lotteryBuyQty: Int = 1,
    val lotteryDrawResult: String? = null,
    val cardFlipState: CardFlipState = CardFlipState(),
    val blackjackState: BlackjackState = BlackjackState(),
    val rouletteState: RouletteState = RouletteState(),
    val scratchCardState: ScratchCardState = ScratchCardState(),
    val videoPokerState: VideoPokerState = VideoPokerState(),
    val snackbarMessage: String? = null,
    val rareDropGem: String? = null,
)

data class GameCornerUiState(
    val coins: Long = 0L,
    val activeTab: GameTab = GameTab.SLOTS,
    val slotsState: SlotsState = SlotsState(),
    val diceState: DiceState = DiceState(),
    val lotteryTickets: Int = 0,
    val lotteryLastDrawAt: Long = 0L,
    val lotteryBuyQty: Int = 1,
    val lotteryDrawResult: String? = null,
    val cardFlipState: CardFlipState = CardFlipState(),
    val blackjackState: BlackjackState = BlackjackState(),
    val rouletteState: RouletteState = RouletteState(),
    val scratchCardState: ScratchCardState = ScratchCardState(),
    val videoPokerState: VideoPokerState = VideoPokerState(),
    val snackbarMessage: String? = null,
    val rareDropGem: String? = null,
)

@HiltViewModel
class GameCornerViewModel @Inject constructor(
    private val playerRepo: PlayerRepository,
    private val json: kotlinx.serialization.json.Json,
) : ViewModel() {

    private val _extra = MutableStateFlow(GameCornerExtra())

    val uiState: StateFlow<GameCornerUiState> = combine(playerRepo.playerFlow, _extra) { player, extra ->
        if (player == null) return@combine GameCornerUiState()
        val flags: PlayerFlags = json.decodeFromString(player.flags)
        GameCornerUiState(
            coins               = player.coins,
            activeTab           = extra.activeTab,
            slotsState          = extra.slotsState,
            diceState           = extra.diceState,
            lotteryTickets      = flags.lotteryTickets,
            lotteryLastDrawAt   = flags.lotteryLastDrawAt,
            lotteryBuyQty       = extra.lotteryBuyQty,
            lotteryDrawResult   = extra.lotteryDrawResult,
            cardFlipState       = extra.cardFlipState,
            blackjackState      = extra.blackjackState,
            rouletteState       = extra.rouletteState,
            scratchCardState    = extra.scratchCardState,
            videoPokerState     = extra.videoPokerState,
            snackbarMessage     = extra.snackbarMessage,
            rareDropGem         = extra.rareDropGem,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GameCornerUiState())

    fun selectTab(tab: GameTab) = _extra.update { it.copy(activeTab = tab) }

    fun clearSnackbar() = _extra.update { it.copy(snackbarMessage = null, rareDropGem = null) }

    // ── Slots ────────────────────────────────────────────────────────────────

    fun slotsSetBet(amount: Long) = _extra.update { it.copy(slotsState = it.slotsState.copy(betAmount = amount.coerceAtLeast(100L))) }

    fun spinSlots() {
        val bet = _extra.value.slotsState.betAmount
        viewModelScope.launch {
            if (!playerRepo.spendCoins(bet)) {
                _extra.update { it.copy(snackbarMessage = "Not enough coins") }
                return@launch
            }
            val r1 = weightedSlotSymbol()
            val r2 = weightedSlotSymbol()
            val r3 = weightedSlotSymbol()
            val reels = listOf(r1, r2, r3)
            val won: Long = if (r1 == r2 && r2 == r3) {
                val payout = bet * r1.multiplier
                playerRepo.addCoins(payout)
                payout
            } else 0L
            val gem = if (won > 0) maybeGrantRareDrop() else null
            _extra.update { it.copy(
                slotsState    = it.slotsState.copy(reels = reels, lastWon = if (won > 0) won else null),
                snackbarMessage = when {
                    won > 0 -> "You won $won coins!"
                    else    -> "No match — better luck next spin!"
                },
                rareDropGem = gem,
            ) }
        }
    }

    // ── Dice / High-Low ──────────────────────────────────────────────────────

    fun diceSetBet(amount: Long) = _extra.update { it.copy(diceState = it.diceState.copy(betAmount = amount.coerceAtLeast(100L))) }

    fun diceRoll() {
        val bet = _extra.value.diceState.betAmount
        viewModelScope.launch {
            if (!playerRepo.spendCoins(bet)) {
                _extra.update { it.copy(snackbarMessage = "Not enough coins") }
                return@launch
            }
            val roll = Random.nextInt(1, 7)
            _extra.update { it.copy(diceState = it.diceState.copy(
                phase      = DicePhase.PLAYER_CHOICE,
                currentRoll = roll,
                pendingPot = bet,
            )) }
        }
    }

    fun diceChoose(higher: Boolean) {
        val state = _extra.value.diceState
        val next = Random.nextInt(1, 7)
        val won = if (higher) next > state.currentRoll else next < state.currentRoll
        val push = next == state.currentRoll
        viewModelScope.launch {
            when {
                won -> {
                    val newPot = (state.pendingPot * 1.8).toLong()
                    val gem = maybeGrantRareDrop()
                    _extra.update { it.copy(diceState = it.diceState.copy(
                        phase      = DicePhase.RESULT_WIN,
                        nextRoll   = next,
                        pendingPot = newPot,
                    ), rareDropGem = gem) }
                }
                push -> {
                    playerRepo.addCoins(state.pendingPot)
                    _extra.update { it.copy(diceState = it.diceState.copy(
                        phase    = DicePhase.RESULT_PUSH,
                        nextRoll = next,
                    )) }
                }
                else -> {
                    _extra.update { it.copy(diceState = it.diceState.copy(
                        phase    = DicePhase.RESULT_LOSE,
                        nextRoll = next,
                    )) }
                }
            }
        }
    }

    fun diceCashOut() {
        val pot = _extra.value.diceState.pendingPot
        viewModelScope.launch {
            playerRepo.addCoins(pot)
            _extra.update { it.copy(
                diceState       = DiceState(betAmount = _extra.value.diceState.betAmount),
                snackbarMessage = "Cashed out $pot coins!",
            ) }
        }
    }

    fun diceRollAgain() {
        val state = _extra.value.diceState
        val newRoll = state.nextRoll.takeIf { it > 0 } ?: Random.nextInt(1, 7)
        _extra.update { it.copy(diceState = state.copy(
            phase       = DicePhase.PLAYER_CHOICE,
            currentRoll = newRoll,
            nextRoll    = 0,
        )) }
    }

    fun diceReset() = _extra.update { it.copy(diceState = DiceState(betAmount = it.diceState.betAmount)) }

    // ── Lottery ──────────────────────────────────────────────────────────────

    fun lotterySetBuyQty(qty: Int) = _extra.update { it.copy(lotteryBuyQty = qty.coerceIn(1, 10)) }

    fun lotteryBuyTickets() {
        val qty = _extra.value.lotteryBuyQty
        val cost = qty * 1_000L
        viewModelScope.launch {
            if (!playerRepo.spendCoins(cost)) {
                _extra.update { it.copy(snackbarMessage = "Not enough coins") }
                return@launch
            }
            val flags = playerRepo.getFlags()
            val newTotal = (flags.lotteryTickets + qty).coerceAtMost(20)
            playerRepo.updateFlags(flags.copy(lotteryTickets = newTotal))
            _extra.update { it.copy(snackbarMessage = "Bought $qty ticket${if (qty == 1) "" else "s"}!") }
        }
    }

    fun lotteryDraw() {
        viewModelScope.launch {
            val flags = playerRepo.getFlags()
            val held = flags.lotteryTickets
            if (held == 0) {
                _extra.update { it.copy(lotteryDrawResult = "No tickets to draw.") }
                return@launch
            }
            var totalWon = 0L
            repeat(held) {
                val roll = Random.nextInt(100)
                totalWon += when {
                    roll < 2  -> 20_000L
                    roll < 10 -> 5_000L
                    roll < 25 -> 1_500L
                    else      -> 0L
                }
            }
            if (totalWon > 0) playerRepo.addCoins(totalWon)
            playerRepo.updateFlags(flags.copy(lotteryTickets = 0, lotteryLastDrawAt = System.currentTimeMillis()))
            val result = if (totalWon > 0)
                "Drew $held ticket${if (held == 1) "" else "s"} — won $totalWon coins!"
            else
                "Drew $held ticket${if (held == 1) "" else "s"} — no winners this time."
            _extra.update { it.copy(lotteryDrawResult = result) }
        }
    }

    fun lotteryClearResult() = _extra.update { it.copy(lotteryDrawResult = null) }

    // ── Card Flip ────────────────────────────────────────────────────────────

    fun cardFlipSetBet(amount: Long) = _extra.update { it.copy(cardFlipState = it.cardFlipState.copy(betAmount = amount.coerceAtLeast(100L))) }

    fun cardFlipStart() {
        val bet = _extra.value.cardFlipState.betAmount
        viewModelScope.launch {
            if (!playerRepo.spendCoins(bet)) {
                _extra.update { it.copy(snackbarMessage = "Not enough coins") }
                return@launch
            }
            val acePos = Random.nextInt(3)
            val cards = List(3) { i -> FlipCard(isAce = i == acePos) }
            _extra.update { it.copy(cardFlipState = it.cardFlipState.copy(betting = false, cards = cards, lastWon = null)) }
        }
    }

    fun cardFlipPick(index: Int) {
        val state = _extra.value.cardFlipState
        if (!state.betting && state.cards.any { it.revealed }) return
        val bet = state.betAmount
        val won = state.cards[index].isAce
        viewModelScope.launch {
            val payout = if (won) (bet * 2.5).toLong() else 0L
            if (won) {
                playerRepo.addCoins(payout)
            }
            val gem = if (won) maybeGrantRareDrop() else null
            val revealed = state.cards.mapIndexed { i, c -> c.copy(revealed = true) }
            _extra.update { it.copy(
                cardFlipState  = state.copy(cards = revealed, betting = true, lastWon = if (won) payout else null),
                snackbarMessage = if (won) "You found the Ace! Won $payout coins!" else "Not the Ace! Better luck next time.",
                rareDropGem    = gem,
            ) }
        }
    }

    // ── Blackjack ─────────────────────────────────────────────────────────────

    fun blackjackSetBet(amount: Long) = _extra.update { it.copy(blackjackState = it.blackjackState.copy(betAmount = amount.coerceAtLeast(100L))) }

    fun blackjackDeal() {
        val bet = _extra.value.blackjackState.betAmount
        viewModelScope.launch {
            if (!playerRepo.spendCoins(bet)) {
                _extra.update { it.copy(snackbarMessage = "Not enough coins") }
                return@launch
            }
            val deck = buildShuffledDeck().toMutableList()
            val playerHand = listOf(deck.removeFirst(), deck.removeFirst())
            val dealerHand = listOf(deck.removeFirst(), deck.removeFirst())
            val playerVal  = handValue(playerHand)
            if (playerVal == 21) {
                val payout = (bet * 2.5).toLong()
                playerRepo.addCoins(payout)
                val gem = maybeGrantRareDrop()
                _extra.update { it.copy(blackjackState = it.blackjackState.copy(
                    phase      = BlackjackPhase.RESULT,
                    playerHand = playerHand,
                    dealerHand = dealerHand,
                    deck       = deck,
                    result     = BlackjackResult.BLACKJACK,
                    payout     = payout,
                ), rareDropGem = gem) }
            } else {
                _extra.update { it.copy(blackjackState = it.blackjackState.copy(
                    phase      = BlackjackPhase.PLAYER_TURN,
                    playerHand = playerHand,
                    dealerHand = dealerHand,
                    deck       = deck,
                    result     = null,
                    payout     = 0L,
                )) }
            }
        }
    }

    fun blackjackHit() {
        val bj = _extra.value.blackjackState
        val deck = bj.deck.toMutableList()
        val newHand = bj.playerHand + deck.removeFirst()
        val value = handValue(newHand)
        if (value > 21) {
            _extra.update { it.copy(blackjackState = bj.copy(
                phase      = BlackjackPhase.RESULT,
                playerHand = newHand,
                deck       = deck,
                result     = BlackjackResult.BUST,
                payout     = 0L,
            )) }
        } else {
            _extra.update { it.copy(blackjackState = bj.copy(playerHand = newHand, deck = deck)) }
        }
    }

    fun blackjackStand() {
        val bj = _extra.value.blackjackState
        viewModelScope.launch { resolveDealer(bj) }
    }

    fun blackjackDoubleDown() {
        val bj = _extra.value.blackjackState
        viewModelScope.launch {
            if (!playerRepo.spendCoins(bj.betAmount)) {
                _extra.update { it.copy(snackbarMessage = "Not enough coins to double down") }
                return@launch
            }
            val deck = bj.deck.toMutableList()
            val newHand = bj.playerHand + deck.removeFirst()
            val updated = bj.copy(playerHand = newHand, deck = deck, betAmount = bj.betAmount * 2)
            if (handValue(newHand) > 21) {
                _extra.update { it.copy(blackjackState = updated.copy(
                    phase  = BlackjackPhase.RESULT,
                    result = BlackjackResult.BUST,
                    payout = 0L,
                )) }
            } else {
                resolveDealer(updated)
            }
        }
    }

    private suspend fun resolveDealer(bj: BlackjackState) {
        val deck = bj.deck.toMutableList()
        var dealer = bj.dealerHand.toMutableList()
        while (handValue(dealer) <= 16) dealer.add(deck.removeFirst())
        val playerVal = handValue(bj.playerHand)
        val dealerVal = handValue(dealer)
        val (result, payout) = when {
            dealerVal > 21 || playerVal > dealerVal -> BlackjackResult.WIN to bj.betAmount * 2
            playerVal == dealerVal                  -> BlackjackResult.PUSH to bj.betAmount
            else                                    -> BlackjackResult.LOSE to 0L
        }
        if (payout > 0) playerRepo.addCoins(payout)
        val gem = if (result == BlackjackResult.WIN || result == BlackjackResult.PUSH) maybeGrantRareDrop() else null
        _extra.update { it.copy(blackjackState = bj.copy(
            phase      = BlackjackPhase.RESULT,
            dealerHand = dealer,
            deck       = deck,
            result     = result,
            payout     = payout,
        ), rareDropGem = gem) }
    }

    fun blackjackReset() = _extra.update { it.copy(blackjackState = BlackjackState(betAmount = _extra.value.blackjackState.betAmount)) }

    // ── Roulette ─────────────────────────────────────────────────────────────

    fun rouletteSetBetType(type: RouletteBetType) = _extra.update { it.copy(rouletteState = it.rouletteState.copy(betType = type)) }
    fun rouletteSetNumber(n: Int) = _extra.update { it.copy(rouletteState = it.rouletteState.copy(selectedNumber = n.coerceIn(0, 36))) }
    fun rouletteSetBet(amount: Long) = _extra.update { it.copy(rouletteState = it.rouletteState.copy(betAmount = amount.coerceAtLeast(100L))) }

    fun rouletteSpin() {
        val rs = _extra.value.rouletteState
        viewModelScope.launch {
            if (!playerRepo.spendCoins(rs.betAmount)) {
                _extra.update { it.copy(snackbarMessage = "Not enough coins") }
                return@launch
            }
            val result = Random.nextInt(37) // 0-36
            val color = rouletteColor(result)
            val won: Long = when (rs.betType) {
                RouletteBetType.RED    -> if (color == RouletteColor.RED)   rs.betAmount * 2 else 0L
                RouletteBetType.BLACK  -> if (color == RouletteColor.BLACK) rs.betAmount * 2 else 0L
                RouletteBetType.ODD    -> if (result != 0 && result % 2 == 1) rs.betAmount * 2 else 0L
                RouletteBetType.EVEN   -> if (result != 0 && result % 2 == 0) rs.betAmount * 2 else 0L
                RouletteBetType.DOZEN_1 -> if (result in 1..12)  rs.betAmount * 3 else 0L
                RouletteBetType.DOZEN_2 -> if (result in 13..24) rs.betAmount * 3 else 0L
                RouletteBetType.DOZEN_3 -> if (result in 25..36) rs.betAmount * 3 else 0L
                RouletteBetType.NUMBER  -> if (result == rs.selectedNumber) rs.betAmount * 36 else 0L
            }
            if (won > 0) playerRepo.addCoins(won)
            val gem = if (won > 0) maybeGrantRareDrop() else null
            _extra.update { it.copy(
                rouletteState   = rs.copy(result = result, lastWon = if (won > 0) won else null),
                snackbarMessage = if (won > 0) "Landed on $result — won $won coins!" else "Landed on $result — no win.",
                rareDropGem     = gem,
            ) }
        }
    }

    // ── Scratch Card ─────────────────────────────────────────────────────────

    fun scratchBuyCard() {
        val price = _extra.value.scratchCardState.price
        viewModelScope.launch {
            if (!playerRepo.spendCoins(price)) {
                _extra.update { it.copy(snackbarMessage = "Not enough coins") }
                return@launch
            }
            val cells = List(9) { ScratchCell(symbol = weightedScratchSymbol()) }
            _extra.update { it.copy(scratchCardState = ScratchCardState(cells = cells, price = price)) }
        }
    }

    fun scratchRevealCell(index: Int) {
        val sc = _extra.value.scratchCardState
        if (sc.cells.isEmpty() || sc.cells[index].revealed) return
        val updated = sc.cells.toMutableList().also { it[index] = it[index].copy(revealed = true) }
        val allRevealed = updated.all { it.revealed }
        _extra.update { it.copy(scratchCardState = sc.copy(
            cells = updated,
            won   = if (allRevealed) sc.won ?: calculateScratchWin(updated, sc.price) else sc.won,
        )) }
        if (allRevealed) viewModelScope.launch { finalizeScratch() }
    }

    fun scratchRevealAll() {
        val sc = _extra.value.scratchCardState
        if (sc.cells.isEmpty()) return
        val updated = sc.cells.map { it.copy(revealed = true) }
        val won = calculateScratchWin(updated, sc.price)
        _extra.update { it.copy(scratchCardState = sc.copy(cells = updated, won = won)) }
        viewModelScope.launch { finalizeScratch() }
    }

    private fun calculateScratchWin(cells: List<ScratchCell>, price: Long): Long {
        var total = 0L
        for (row in 0..2) {
            val a = cells[row * 3]; val b = cells[row * 3 + 1]; val c = cells[row * 3 + 2]
            if (a.symbol == b.symbol && b.symbol == c.symbol) total += price * a.symbol.multiplier
        }
        for (col in 0..2) {
            val a = cells[col]; val b = cells[col + 3]; val c = cells[col + 6]
            if (a.symbol == b.symbol && b.symbol == c.symbol) total += price * a.symbol.multiplier
        }
        val d1a = cells[0]; val d1b = cells[4]; val d1c = cells[8]
        if (d1a.symbol == d1b.symbol && d1b.symbol == d1c.symbol) total += price * d1a.symbol.multiplier
        val d2a = cells[2]; val d2b = cells[4]; val d2c = cells[6]
        if (d2a.symbol == d2b.symbol && d2b.symbol == d2c.symbol) total += price * d2a.symbol.multiplier
        return total
    }

    private suspend fun finalizeScratch() {
        val won = _extra.value.scratchCardState.won ?: 0L
        if (won > 0) {
            playerRepo.addCoins(won)
            val gem = maybeGrantRareDrop()
            _extra.update { it.copy(
                snackbarMessage = "You won $won coins!",
                rareDropGem     = gem,
            ) }
        }
    }

    fun scratchReset() = _extra.update { it.copy(scratchCardState = ScratchCardState(price = it.scratchCardState.price)) }

    // ── Video Poker ───────────────────────────────────────────────────────────

    fun videoPokerSetBet(amount: Long) = _extra.update { it.copy(videoPokerState = it.videoPokerState.copy(betAmount = amount.coerceAtLeast(100L))) }

    fun videoPokerDeal() {
        val bet = _extra.value.videoPokerState.betAmount
        viewModelScope.launch {
            if (!playerRepo.spendCoins(bet)) {
                _extra.update { it.copy(snackbarMessage = "Not enough coins") }
                return@launch
            }
            val deck = buildShuffledDeck().toMutableList()
            val hand = List(5) { deck.removeFirst() }
            _extra.update { it.copy(videoPokerState = it.videoPokerState.copy(
                phase = VideoPokerPhase.HOLDING,
                hand  = hand,
                held  = List(5) { false },
                deck  = deck,
                result = null,
                payout = 0L,
            )) }
        }
    }

    fun videoPokerToggleHold(index: Int) {
        val vp = _extra.value.videoPokerState
        if (vp.phase != VideoPokerPhase.HOLDING) return
        val newHeld = vp.held.toMutableList().also { it[index] = !it[index] }
        _extra.update { it.copy(videoPokerState = vp.copy(held = newHeld)) }
    }

    fun videoPokerDraw() {
        val vp = _extra.value.videoPokerState
        viewModelScope.launch {
            val deck = vp.deck.toMutableList()
            val newHand = vp.hand.mapIndexed { i, card ->
                if (vp.held[i]) card else deck.removeFirst()
            }
            val pokerHand = evaluatePokerHand(newHand)
            val payout = if (pokerHand != null) vp.betAmount * pokerHand.multiplier else 0L
            if (payout > 0) playerRepo.addCoins(payout)
            val gem = if (payout > 0) maybeGrantRareDrop() else null
            _extra.update { it.copy(
                videoPokerState = vp.copy(
                    phase  = VideoPokerPhase.RESULT,
                    hand   = newHand,
                    deck   = deck,
                    result = pokerHand,
                    payout = payout,
                ),
                rareDropGem = gem,
            ) }
        }
    }

    fun videoPokerReset() = _extra.update { it.copy(videoPokerState = VideoPokerState(betAmount = _extra.value.videoPokerState.betAmount)) }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun weightedSlotSymbol(): SlotSymbol {
        val total = SlotSymbol.values().sumOf { it.weight }
        var roll = Random.nextInt(total)
        for (sym in SlotSymbol.values()) {
            roll -= sym.weight
            if (roll < 0) return sym
        }
        return SlotSymbol.CHERRY
    }

    private fun weightedScratchSymbol(): ScratchSymbol {
        val total = ScratchSymbol.values().sumOf { it.weight }
        var roll = Random.nextInt(total)
        for (sym in ScratchSymbol.values()) { roll -= sym.weight; if (roll < 0) return sym }
        return ScratchSymbol.CHERRY
    }

    private fun buildShuffledDeck(): List<PlayingCard> =
        (0..3).flatMap { suit -> (1..13).map { rank -> PlayingCard(suit, rank) } }.shuffled()

    private suspend fun maybeGrantRareDrop(): String? {
        if (Random.nextInt(1000) != 0) return null
        val item = if (Random.nextBoolean()) "ring_of_fortune" else "amulet_of_fortune"
        playerRepo.grantItem(item)
        return item
    }
}
