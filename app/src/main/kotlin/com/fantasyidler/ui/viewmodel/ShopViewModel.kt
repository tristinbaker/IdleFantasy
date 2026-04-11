package com.fantasyidler.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fantasyidler.data.json.MarketplaceItem
import com.fantasyidler.repository.GameDataRepository
import com.fantasyidler.repository.PlayerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

// ---------------------------------------------------------------------------
// Models
// ---------------------------------------------------------------------------

/** A flat, display-ready buy entry derived from MarketplaceJson. */
data class ShopEntry(
    val key: String,
    val displayName: String,
    val description: String,
    val price: Int,
    val categoryName: String,
)

data class ShopTransaction(
    val key: String,
    val displayName: String,
    val priceEach: Int,
    val maxQty: Int,
    val qty: Int = 1,
    val isBuy: Boolean,
)

// ---------------------------------------------------------------------------
// UI state
// ---------------------------------------------------------------------------

data class ShopUiState(
    val coins: Long = 0L,
    val inventory: Map<String, Int> = emptyMap(),
    val transaction: ShopTransaction? = null,
    val snackbarMessage: String? = null,
    val isLoading: Boolean = true,
)

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

@HiltViewModel
class ShopViewModel @Inject constructor(
    private val playerRepo: PlayerRepository,
    private val gameData: GameDataRepository,
    private val json: Json,
) : ViewModel() {

    private val _extra = MutableStateFlow(ShopUiState())

    val uiState: StateFlow<ShopUiState> = combine(
        playerRepo.playerFlow,
        _extra,
    ) { player, extra ->
        if (player == null) extra
        else extra.copy(
            coins     = player.coins,
            inventory = json.decodeFromString(player.inventory),
            isLoading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShopUiState())

    // ------------------------------------------------------------------
    // Buy catalogue (all marketplace items except seeds)
    // ------------------------------------------------------------------

    val buyEntries: List<ShopEntry> by lazy {
        gameData.marketplace
            .filterKeys { it != "seeds" }     // skip farming for now
            .flatMap { (_, cat) ->
                cat.items.map { (key, item) ->
                    ShopEntry(
                        key          = key,
                        displayName  = item.displayName,
                        description  = item.description,
                        price        = item.price,
                        categoryName = cat.categoryName,
                    )
                }
            }
            .sortedWith(compareBy({ it.categoryName }, { it.price }))
    }

    // ------------------------------------------------------------------
    // Sell price lookup
    // ------------------------------------------------------------------

    fun sellPriceFor(itemKey: String): Int {
        // Prefer marketplace buy price / 3
        val marketPrice = gameData.marketplace.values
            .mapNotNull { it.items[itemKey]?.price }
            .firstOrNull()
        if (marketPrice != null) return maxOf(1, marketPrice / 3)

        // Category-based fallback for gathered/crafted items not in the shop
        return when {
            "gem"     in itemKey -> 80
            "bar"     in itemKey -> 30
            "log"     in itemKey -> 5
            "ore"     in itemKey -> 5
            "cooked"  in itemKey -> 10
            "raw_"    in itemKey -> 4
            itemKey in FISH_KEYS -> 8
            else                 -> 2
        }
    }

    // ------------------------------------------------------------------
    // Transactions
    // ------------------------------------------------------------------

    fun openBuy(entry: ShopEntry) {
        val maxAffordable = (uiState.value.coins / entry.price).toInt().coerceAtLeast(1)
        _extra.update {
            it.copy(
                transaction = ShopTransaction(
                    key         = entry.key,
                    displayName = entry.displayName,
                    priceEach   = entry.price,
                    maxQty      = maxAffordable,
                    qty         = 1,
                    isBuy       = true,
                )
            )
        }
    }

    fun openSell(itemKey: String, displayName: String) {
        val have      = uiState.value.inventory[itemKey] ?: 0
        val sellPrice = sellPriceFor(itemKey)
        _extra.update {
            it.copy(
                transaction = ShopTransaction(
                    key         = itemKey,
                    displayName = displayName,
                    priceEach   = sellPrice,
                    maxQty      = have,
                    qty         = have,
                    isBuy       = false,
                )
            )
        }
    }

    fun setTransactionQty(qty: Int) = _extra.update { state ->
        val t = state.transaction ?: return@update state
        state.copy(transaction = t.copy(qty = qty.coerceIn(1, t.maxQty)))
    }

    fun dismissTransaction() = _extra.update { it.copy(transaction = null) }

    fun confirmTransaction() {
        val t = _extra.value.transaction ?: return
        viewModelScope.launch {
            val success = if (t.isBuy) {
                playerRepo.buyItem(t.key, t.qty, t.priceEach)
            } else {
                playerRepo.sellItem(t.key, t.qty, t.priceEach)
            }
            _extra.update {
                it.copy(
                    transaction = null,
                    snackbarMessage = if (success) {
                        if (t.isBuy) "Bought ${t.qty}× ${t.displayName}"
                        else         "Sold ${t.qty}× ${t.displayName} for ${t.priceEach * t.qty} coins"
                    } else {
                        if (t.isBuy) "Not enough coins" else "Not enough in inventory"
                    },
                )
            }
        }
    }

    fun snackbarConsumed() = _extra.update { it.copy(snackbarMessage = null) }

    companion object {
        private val FISH_KEYS = setOf(
            "shrimp", "sardine", "herring", "trout", "salmon",
            "tuna", "lobster", "swordfish", "shark", "raw_shrimp",
            "raw_sardine", "raw_herring", "raw_trout", "raw_salmon",
            "raw_tuna", "raw_lobster", "raw_swordfish", "raw_shark",
        )
    }
}
