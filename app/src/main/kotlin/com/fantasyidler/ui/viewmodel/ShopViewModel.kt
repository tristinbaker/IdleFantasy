package com.fantasyidler.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fantasyidler.data.json.MarketplaceItem
import com.fantasyidler.data.model.EquipSlot
import com.fantasyidler.data.model.PlayerFlags
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
    val equipped: Map<String, String?> = emptyMap(),
    val xpBoostExpiresAt: Long = 0L,
    val transaction: ShopTransaction? = null,
    val snackbarMessage: String? = null,
    val isLoading: Boolean = true,
) {
    val xpBoostActive: Boolean get() = xpBoostExpiresAt > System.currentTimeMillis()
}

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
        else {
            val flags: PlayerFlags = json.decodeFromString(player.flags)
            extra.copy(
                coins            = player.coins,
                inventory        = json.decodeFromString(player.inventory),
                equipped         = json.decodeFromString(player.equipped),
                xpBoostExpiresAt = flags.xpBoostExpiresAt,
                isLoading        = false,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShopUiState())

    // ------------------------------------------------------------------
    // Buy catalogue (all marketplace items except seeds + XP boost)
    // ------------------------------------------------------------------

    val buyEntries: List<ShopEntry> by lazy {
        val regular = gameData.marketplace
            .filterKeys { it != "seeds" }
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

        val boost = ShopEntry(
            key          = XP_BOOST_KEY,
            displayName  = "2× XP Boost (48h)",
            description  = "Double all XP gained for 48 hours",
            price        = PlayerRepository.XP_BOOST_COST.toInt(),
            categoryName = "Special",
        )
        listOf(boost) + regular
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
    // Bulk sell helpers
    // ------------------------------------------------------------------

    /** Sell all items that have no gameplay use (not equipment, food, materials, etc.). */
    fun sellJunk() {
        viewModelScope.launch {
            val inventory = uiState.value.inventory
            val useful    = gameData.usefulItemKeys
            val junk      = inventory.filterKeys { it != "coins" && it !in useful }
            if (junk.isEmpty()) {
                _extra.update { it.copy(snackbarMessage = "No junk to sell") }
                return@launch
            }
            var total = 0L
            for ((key, qty) in junk) {
                val price = sellPriceFor(key)
                playerRepo.sellItem(key, qty, price)
                total += price.toLong() * qty
            }
            _extra.update { it.copy(snackbarMessage = "Sold junk for ${total.toCoinsString()} coins") }
        }
    }

    /**
     * Sell equipment in inventory that is strictly weaker than what's currently
     * equipped in the same slot. Weapons are excluded per user request.
     */
    fun sellOldEquipment() {
        viewModelScope.launch {
            val state     = uiState.value
            val equipped  = state.equipped
            val inventory = state.inventory
            val allEquip  = gameData.equipment

            val toSell = mutableMapOf<String, Int>()
            for (slot in EquipSlot.ALL) {
                if (slot == EquipSlot.WEAPON) continue    // user: don't include weapons
                val equippedKey  = equipped[slot] ?: continue
                val equippedItem = allEquip[equippedKey] ?: continue
                val equippedScore = scoreFor(equippedItem, slot)

                for ((itemKey, qty) in inventory) {
                    if (itemKey == equippedKey) continue  // keep the currently equipped copy
                    val item = allEquip[itemKey] ?: continue
                    if (item.slot != slot) continue
                    if (scoreFor(item, slot) < equippedScore) {
                        toSell[itemKey] = (toSell[itemKey] ?: 0) + qty
                    }
                }
            }

            if (toSell.isEmpty()) {
                _extra.update { it.copy(snackbarMessage = "No old equipment to sell") }
                return@launch
            }
            var total = 0L
            for ((key, qty) in toSell) {
                val price = sellPriceFor(key)
                playerRepo.sellItem(key, qty, price)
                total += price.toLong() * qty
            }
            _extra.update { it.copy(snackbarMessage = "Sold old equipment for ${total.toCoinsString()} coins") }
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
        val state        = uiState.value
        val have         = state.inventory[itemKey] ?: 0
        val equippedCount = state.equipped.values.count { it == itemKey }
        val sellable     = (have - equippedCount).coerceAtLeast(0)
        if (sellable == 0) {
            _extra.update { it.copy(snackbarMessage = "$displayName is equipped — unequip it first to sell.") }
            return
        }
        val sellPrice = sellPriceFor(itemKey)
        _extra.update {
            it.copy(
                transaction = ShopTransaction(
                    key         = itemKey,
                    displayName = displayName,
                    priceEach   = sellPrice,
                    maxQty      = sellable,
                    qty         = sellable,
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
            // Special handling for XP boost
            if (t.key == XP_BOOST_KEY) {
                val activated = playerRepo.activateXpBoost(PlayerRepository.XP_BOOST_DURATION_MS, t.qty)
                _extra.update {
                    it.copy(
                        transaction     = null,
                        snackbarMessage = if (activated) "2× XP boost activated for ${t.qty * 48}h!"
                                          else           "Not enough coins",
                    )
                }
                return@launch
            }

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

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    fun sellCategoryFor(itemKey: String): String {
        val equip = gameData.equipment[itemKey]
        if (equip != null) {
            return when (equip.slot) {
                EquipSlot.WEAPON                                      -> "Weapons"
                EquipSlot.PICKAXE, EquipSlot.AXE, EquipSlot.FISHING_ROD -> "Tools"
                else                                                  -> "Armor"
            }
        }
        if (itemKey in gameData.foodHealValues) return "Food"
        return when {
            "ore"     in itemKey || "bar"     in itemKey ||
            "gem"     in itemKey || "log"     in itemKey ||
            "bone"    in itemKey || "essence" in itemKey ||
            "arrow"   in itemKey || "raw_"    in itemKey ||
            "cooked"  in itemKey              -> "Materials"
            else                              -> "Misc"
        }
    }

    private fun scoreFor(item: com.fantasyidler.data.json.EquipmentData, slot: String): Float = when (slot) {
        EquipSlot.PICKAXE     -> item.miningEfficiency ?: 0f
        EquipSlot.AXE         -> item.woodcuttingEfficiency ?: 0f
        EquipSlot.FISHING_ROD -> item.fishingEfficiency ?: 0f
        else                  -> (item.attackBonus + item.strengthBonus + item.defenseBonus).toFloat()
    }

    private fun Long.toCoinsString(): String =
        if (this >= 1_000_000) "${"%.1f".format(this / 1_000_000.0)}M"
        else if (this >= 1_000) "${"%.1f".format(this / 1_000.0)}k"
        else this.toString()

    companion object {
        const val XP_BOOST_KEY = "xp_boost_48h"

        private val FISH_KEYS = setOf(
            "shrimp", "sardine", "herring", "trout", "salmon",
            "tuna", "lobster", "swordfish", "shark", "raw_shrimp",
            "raw_sardine", "raw_herring", "raw_trout", "raw_salmon",
            "raw_tuna", "raw_lobster", "raw_swordfish", "raw_shark",
        )
    }
}
