package com.fantasyidler.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fantasyidler.data.json.MarketplaceItem
import com.fantasyidler.data.model.BulkSellReceipt
import com.fantasyidler.data.model.EquipSlot
import com.fantasyidler.data.model.PlayerFlags
import com.fantasyidler.data.model.QueuedAction
import com.fantasyidler.data.model.Skills
import com.fantasyidler.repository.BoostRepository
import com.fantasyidler.repository.GameDataRepository
import com.fantasyidler.repository.PlayerRepository
import com.fantasyidler.repository.WeeklyQuestRepository
import com.fantasyidler.repository.XpBoostPurchaseResult
import com.fantasyidler.repository.resolveCapeMultiplier
import com.fantasyidler.simulator.MercantilePerks
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
import android.content.Context
import com.fantasyidler.R
import com.fantasyidler.data.json.EquipmentData
import com.fantasyidler.util.GameStrings
import com.fantasyidler.util.withAppLocale
import dagger.hilt.android.qualifiers.ApplicationContext

// ---------------------------------------------------------------------------
// Models
// ---------------------------------------------------------------------------

data class BulkSellItem(
    val key: String,
    val displayName: String,
    val qty: Int,
    val priceEach: Int,
) {
    val total: Long get() = priceEach.toLong() * qty
}

data class BulkSellPreview(
    val titleRes: Int,
    val soldMsgRes: Int,
    val items: List<BulkSellItem>,
) {
    val totalCoins: Long get() = items.sumOf { it.total }
}

/** A flat, display-ready buy entry derived from MarketplaceJson. */
data class ShopEntry(
    val key: String,
    val displayName: String,
    val description: String,
    val price: Int,
    val categoryName: String,
    val mercantileLevelRequired: Int = 0,
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
    val xpBoostLastPurchaseAt: Long = 0L,
    val dailyResetHour: Int = 6,
    val transaction: ShopTransaction? = null,
    val pendingBulkSell: BulkSellPreview? = null,
    val snackbarMessage: String? = null,
    val isLoading: Boolean = true,
    /** Items reserved by queued actions — cannot be sold. */
    val reservedItems: Map<String, Int> = emptyMap(),
    /** Per-style remembered armor loadouts; their gear counts as equipped for selling. */
    val armorLoadouts: Map<String, Map<String, String?>> = emptyMap(),
    /** Items the player locked against selling. */
    val lockedItems: Set<String> = emptySet(),
    val mercantileLevel: Int = 0,
    val townBuildingTiers: Map<String, Int> = emptyMap(),
    val skillPrestige: Map<String, Int> = emptyMap(),
    val capeScalingBySkill: Map<String, Int> = emptyMap(),
    val sellPriceMult: Double = 1.0,
    /** Ironman characters can only sell — every buy path is blocked. */
    val ironman: Boolean = false,
    val compactNumbers: Boolean = false,
    /** Bulk and manual sells always leave one of each item (collector safety). */
    val keepOneOfEach: Boolean = false,
    val bulkSellReceipts: List<BulkSellReceipt> = emptyList(),
    val seenItemKeys: Set<String> = emptySet(),
) {
    val xpBoostActive: Boolean get() = xpBoostExpiresAt > System.currentTimeMillis()
}

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

@HiltViewModel
class ShopViewModel @Inject constructor(
    private val boostRepo: BoostRepository,
    @ApplicationContext private val context: Context,
    private val playerRepo: PlayerRepository,
    private val gameData: GameDataRepository,
    private val weeklyQuestRepo: WeeklyQuestRepository,
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
            val levels: Map<String, Int> = json.decodeFromString(player.skillLevels)
            extra.copy(
                coins            = player.coins,
                inventory        = json.decodeFromString(player.inventory),
                equipped         = json.decodeFromString(player.equipped),
                xpBoostExpiresAt = flags.xpBoostExpiresAt,
                xpBoostLastPurchaseAt = flags.xpBoostLastPurchaseAt,
                dailyResetHour   = flags.dailyResetHour,
                isLoading        = false,
                reservedItems    = computeReserved(flags.sessionQueue),
                armorLoadouts    = flags.armorLoadouts,
                lockedItems      = flags.lockedItems.toSet(),
                mercantileLevel  = levels[Skills.MERCANTILE] ?: 0,
                townBuildingTiers = flags.townBuildingTiers,
                skillPrestige     = flags.skillPrestige,
                capeScalingBySkill = boostRepo.capeScalingBySkill(flags),
                sellPriceMult     = boostRepo.sellPriceMultiplier(flags),
                ironman           = flags.ironman,
                compactNumbers    = flags.compactNumbers,
                keepOneOfEach     = flags.shopKeepOneOfEach,
                bulkSellReceipts  = flags.bulkSellReceipts,
                seenItemKeys      = flags.seenItemKeys,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShopUiState())

    // ------------------------------------------------------------------
    // Buy catalogue (all marketplace items except XP boost)
    // ------------------------------------------------------------------

    val buyEntries: List<ShopEntry> by lazy {
        val regular = gameData.marketplace
            .flatMap { (_, cat) ->
                cat.items.map { (key, item) ->
                    ShopEntry(
                        key                    = key,
                        displayName            = item.displayName,
                        description            = item.description,
                        price                  = item.price,
                        categoryName           = cat.categoryName,
                        mercantileLevelRequired = item.mercantileLevelRequired,
                    )
                }
            }
            .sortedWith(compareBy({ it.categoryName }, { it.price }))

        val boost = ShopEntry(
            key          = XP_BOOST_KEY,
            displayName  = context.withAppLocale().getString(R.string.shop_xp_boost_name),
            description  = context.withAppLocale().getString(R.string.item_xp_boost_48h_desc),
            price        = PlayerRepository.XP_BOOST_COST.toInt(),
            categoryName = "Special",
        )
        listOf(boost) + regular
    }

    // ------------------------------------------------------------------
    // Queue reservation helpers
    // ------------------------------------------------------------------

    private fun computeReserved(queue: List<QueuedAction>): Map<String, Int> {
        val reserved = mutableMapOf<String, Int>()
        for (action in queue) {
            when (action.skillName) {
                Skills.SMITHING, Skills.FLETCHING, Skills.CRAFTING -> {
                    val mats = when (action.skillName) {
                        Skills.SMITHING  -> gameData.smithingRecipes[action.activityKey]?.materials
                        Skills.FLETCHING -> gameData.fletchingRecipes[action.activityKey]?.materials
                        Skills.CRAFTING  -> gameData.craftingRecipes[action.activityKey]?.materials
                        else             -> null
                    } ?: continue
                    for ((item, needed) in mats) {
                        reserved[item] = (reserved[item] ?: 0) + needed * action.qty
                    }
                }
                Skills.COOKING -> {
                    val rawItem = gameData.cookingRecipes[action.activityKey]?.rawItem ?: continue
                    reserved[rawItem] = (reserved[rawItem] ?: 0) + action.qty
                }
                Skills.HERBLORE -> {
                    val mats = gameData.herbloreRecipes[action.activityKey]?.materials ?: continue
                    for ((item, needed) in mats) {
                        reserved[item] = (reserved[item] ?: 0) + needed * action.qty
                    }
                }
                Skills.PRAYER -> {
                    val key = action.activityKey
                    reserved[key] = (reserved[key] ?: 0) + action.qty
                }
                Skills.RUNECRAFTING -> {
                    val cost = gameData.runes[action.activityKey]?.essenceCost ?: continue
                    reserved["rune_essence"] = (reserved["rune_essence"] ?: 0) + cost * action.qty
                }
            }
        }
        return reserved
    }

    // ------------------------------------------------------------------
    // Sell price lookup
    // ------------------------------------------------------------------

    fun sellPriceFor(itemKey: String): Int {
        val marketPrice = gameData.marketplace.values
            .mapNotNull { it.items[itemKey]?.price }
            .firstOrNull()

        val equipData = gameData.equipment[itemKey]
        val basePrice: Int = if (equipData != null) {
            when {
                itemKey.endsWith("_cape") -> {
                    val maxReq = equipData.requirements.values.maxOrNull() ?: 1
                    maxOf(5, maxReq * 3)
                }
                else -> {
                    val slotMult  = SLOT_MULTIPLIER[equipData.slot] ?: 2.0f
                    val metalBase = METAL_BASE[itemKey.substringBefore("_")]
                    val bowBase   = BOW_BASE.entries.firstOrNull { itemKey.startsWith(it.key) }?.value
                    val jewelBase = if (equipData.slot == EquipSlot.RING || equipData.slot == EquipSlot.NECKLACE)
                        JEWEL_BASE.entries.firstOrNull { itemKey.startsWith(it.key) }?.value else null
                    when {
                        metalBase != null -> maxOf(5, (metalBase * slotMult).toInt())
                        bowBase   != null -> maxOf(5, (bowBase   * slotMult).toInt())
                        jewelBase != null -> {
                            val gemBonus = GEM_BONUS.entries.firstOrNull { it.key in itemKey }?.value ?: 0
                            maxOf(5, (jewelBase * slotMult + gemBonus).toInt())
                        }
                        else -> maxOf(5, (equipData.requirements.values.maxOrNull() ?: 1) * 4)
                    }
                }
            }
        } else {
            val gem  = gameData.gems[itemKey]
            val crop = gameData.crops[itemKey]
            when {
                itemKey in CONSTRUCTION_SELL -> CONSTRUCTION_SELL.getValue(itemKey)
                "arrow_shaft" in itemKey -> 1
                "_arrow_tip" in itemKey -> when {
                    "runite"     in itemKey -> 8
                    "adamantite" in itemKey -> 5
                    "mithril"    in itemKey -> 3
                    "steel"      in itemKey -> 2
                    else                   -> 1
                }
                "_arrow" in itemKey -> when {
                    "runite"     in itemKey -> 20
                    "adamantite" in itemKey -> 14
                    "mithril"    in itemKey -> 9
                    "steel"      in itemKey -> 6
                    "iron"       in itemKey -> 4
                    else                   -> 3
                }
                "bar" in itemKey -> when {
                    "runite"     in itemKey -> 230
                    "adamantite" in itemKey -> 130
                    "platinum"   in itemKey -> 140
                    "mithril"    in itemKey -> 65
                    "gold"       in itemKey -> 27
                    "steel"      in itemKey -> 22
                    "silver"     in itemKey -> 20
                    "iron"       in itemKey -> 10
                    else                   -> 15
                }
                gem != null -> when (gem.rarity) {
                    "very_rare" -> 150
                    "rare"      -> 100
                    "uncommon"  -> 70
                    else        -> 50
                }
                "potion" in itemKey          -> 25
                "pearl"  in itemKey          -> 15
                "log"    in itemKey          -> 5
                "ore"    in itemKey          -> 5
                "cooked" in itemKey          -> 10
                "raw_"   in itemKey          -> 4
                itemKey in FISH_KEYS         -> 8
                crop != null                 -> maxOf(5, crop.seedCost / 4)
                itemKey.endsWith("_bone") || itemKey == "bone" -> 8
                "_scale"   in itemKey        -> 20
                "_horn"    in itemKey || "_fang"     in itemKey -> 15
                "_hide"    in itemKey || "_leather"  in itemKey -> 8
                "_silk"    in itemKey        -> 10
                "_feather" in itemKey        -> 3
                "_wool"    in itemKey        -> 5
                itemKey.endsWith("_rune")    -> 8
                else                         -> 5
            }
        }

        val base = if (marketPrice != null) maxOf(basePrice, maxOf(1, marketPrice / 3)) else basePrice
        val result = (base * mercantileSellBonus(uiState.value.mercantileLevel) * uiState.value.sellPriceMult).toInt().coerceAtLeast(1)
        // Clamp to the actual current buy price (not the undiscounted list price), so selling
        // can never turn a profit once Mercantile discounts push the buy price down.
        val buyPrice = marketPrice?.let { (it * mercantileBuyDiscount()).toInt().coerceAtLeast(1) }
        return if (buyPrice != null) minOf(result, buyPrice - 1).coerceAtLeast(1) else result
    }

    fun discountedPrice(entry: ShopEntry): Int =
        (entry.price * mercantileBuyDiscount()).toInt().coerceAtLeast(1)

    fun mercantileBuyDiscount(mercantileLevel: Int = uiState.value.mercantileLevel): Float {
        val base = 1f - MercantilePerks.tradePct(mercantileLevel) / 100f
        val capeBonus = mercantileCapeBonusFromState()
        return (base - capeBonus).coerceAtLeast(0.50f)
    }

    private fun mercantileSellBonus(level: Int): Float =
        1f + MercantilePerks.tradePct(level) / 100f + mercantileCapeBonusFromState()

    private fun mercantileCapeBonusFromState(): Float {
        val equippedCape = uiState.value.equipped[EquipSlot.CAPE]?.let { gameData.equipment[it] }
        val mult = resolveCapeMultiplier(
            skillName = "mercantile",
            equippedCape = equippedCape,
            inventoryKeys = uiState.value.inventory.keys,
            townBuildingTiers = uiState.value.townBuildingTiers,
            capeScaling = uiState.value.capeScalingBySkill,
            allEquipment = gameData.equipment,
            ironman = uiState.value.ironman,
        )
        return mult - 1.0f
    }

    // ------------------------------------------------------------------
    // Bulk sell helpers
    // ------------------------------------------------------------------

    fun previewSellJunk() {
        viewModelScope.launch {
            val inventory = uiState.value.inventory
            val useful    = gameData.usefulItemKeys
            val locked    = uiState.value.lockedItems
            val junk      = inventory.filterKeys { it != "coins" && it !in useful && it !in locked }
                .let { if (uiState.value.keepOneOfEach) it.mapValues { (_, qty) -> qty - 1 }.filterValues { qty -> qty > 0 } else it }
            if (junk.isEmpty()) {
                _extra.update { it.copy(snackbarMessage = context.withAppLocale().getString(R.string.shop_no_junk)) }
                return@launch
            }
            val items = junk.map { (key, qty) ->
                BulkSellItem(key, GameStrings.itemName(context.withAppLocale(), key), qty, sellPriceFor(key))
            }.sortedBy { it.displayName }
            _extra.update { it.copy(pendingBulkSell = BulkSellPreview(R.string.shop_sell_junk, R.string.shop_sold_junk, items)) }
        }
    }

    fun previewSellOldEquipment() {
        viewModelScope.launch {
            val state     = uiState.value
            val equipped  = state.equipped
            val inventory = state.inventory
            val allEquip  = gameData.equipment

            // Gear referenced only by a queued action's start-of-session snapshot is still
            // in use once that session fires; never sell its last copy (issue #1630).
            val queuedGearKeys = playerRepo.getFlags().sessionQueue.flatMapTo(mutableSetOf()) { action ->
                action.equippedSnapshot?.let {
                    try { json.decodeFromString<Map<String, String?>>(it).values.filterNotNull() }
                    catch (_: Exception) { emptyList() }
                } ?: emptyList()
            }
            val toSell = computeOldEquipmentToSell(
                equipped, inventory, allEquip, state.keepOneOfEach, state.reservedItems, state.armorLoadouts, queuedGearKeys)
                .filterKeys { it !in state.lockedItems }
                .filterKeys { allEquip[it]?.heirloomSkill == null }

            if (toSell.isEmpty()) {
                _extra.update { it.copy(snackbarMessage = context.withAppLocale().getString(R.string.shop_no_old_equipment)) }
                return@launch
            }
            val items = toSell.map { (key, qty) ->
                BulkSellItem(key, GameStrings.itemName(context.withAppLocale(), key), qty, sellPriceFor(key))
            }.sortedBy { it.displayName }
            _extra.update { it.copy(pendingBulkSell = BulkSellPreview(R.string.shop_sell_old_gear, R.string.shop_sold_old_equipment, items)) }
        }
    }

    fun setKeepOneOfEach(enabled: Boolean) {
        viewModelScope.launch {
            val flags = playerRepo.getFlags()
            playerRepo.updateFlags(flags.copy(shopKeepOneOfEach = enabled))
        }
    }

    fun toggleItemLock(itemKey: String) {
        viewModelScope.launch {
            val nowLocked = playerRepo.toggleItemLock(itemKey)
            val name = GameStrings.itemName(context.withAppLocale(), itemKey)
            _extra.update {
                it.copy(snackbarMessage = context.withAppLocale().getString(
                    if (nowLocked) R.string.shop_item_lock_on else R.string.shop_item_lock_off, name))
            }
        }
    }

    fun confirmBulkSell() {
        val preview = _extra.value.pendingBulkSell ?: return
        _extra.update { it.copy(pendingBulkSell = null) }
        viewModelScope.launch {
            val player = playerRepo.getOrCreatePlayer()
            val flags: PlayerFlags = json.decodeFromString(player.flags)
            val inventory: Map<String, Int> = json.decodeFromString(player.inventory)
            val equipped: Map<String, String?> = json.decodeFromString(player.equipped)
            val reserved = computeReserved(flags.sessionQueue)
            val queuedGearKeys = flags.sessionQueue.flatMapTo(mutableSetOf()) { action ->
                action.equippedSnapshot?.let {
                    try { json.decodeFromString<Map<String, String?>>(it).values.filterNotNull() }
                    catch (_: Exception) { emptyList() }
                } ?: emptyList()
            }
            val loadoutKeys = flags.armorLoadouts.values.flatMapTo(mutableSetOf()) { it.values.filterNotNull() } + queuedGearKeys

            val toSell = mutableMapOf<String, Int>()
            val prices = mutableMapOf<String, Int>()
            for (item in preview.items) {
                val key = item.key
                val equipData = gameData.equipment[key]
                if (key in flags.lockedItems) continue
                if (equipData?.heirloomSkill != null) continue
                if (equipData?.capeSkill != null) continue
                val have = inventory[key] ?: 0
                val equippedCount = if (equipData != null) {
                    maxOf(equipped.values.count { it == key }, if (key in loadoutKeys) 1 else 0)
                } else 0
                val reservedQty = reserved[key] ?: 0
                val keeper = if (flags.shopKeepOneOfEach && equippedCount == 0) 1 else 0
                val cap = (have - equippedCount - reservedQty - keeper).coerceAtLeast(0)
                val qty = minOf(item.qty, cap)
                if (qty <= 0) continue
                toSell[key] = qty
                prices[key] = item.priceEach
            }

            val (sold, coins) = playerRepo.sellItemsBulk(toSell, prices)
            if (sold.isNotEmpty()) {
                val updatedFlags = playerRepo.getFlags()
                val receipt = BulkSellReceipt(atMs = System.currentTimeMillis(), items = sold, coins = coins)
                playerRepo.updateFlags(updatedFlags.copy(
                    bulkSellReceipts = (listOf(receipt) + updatedFlags.bulkSellReceipts).take(MAX_BULK_SELL_RECEIPTS)))
            }
            _extra.update { it.copy(
                snackbarMessage = context.withAppLocale().getString(preview.soldMsgRes, coins.toCoinsString()),
            )}
        }
    }

    /**
     * How many copies of [itemKey] may be sold RIGHT NOW, applying every protection the
     * previews apply (locks, heirlooms, capes, equipped and loadout copies, queue
     * reservations and snapshots, the keep-one keeper) against the live player state.
     */
    private suspend fun currentSellableCap(itemKey: String): Int {
        val player = playerRepo.getOrCreatePlayer()
        val flags: PlayerFlags = json.decodeFromString(player.flags)
        if (itemKey in flags.lockedItems) return 0
        val equipData = gameData.equipment[itemKey]
        if (equipData?.heirloomSkill != null) return 0
        if (equipData?.capeSkill != null) return 0
        val inventory: Map<String, Int> = json.decodeFromString(player.inventory)
        val have = inventory[itemKey] ?: 0
        val equippedCount = if (equipData != null) {
            val equipped: Map<String, String?> = json.decodeFromString(player.equipped)
            val queuedGearKeys = flags.sessionQueue.flatMapTo(mutableSetOf()) { action ->
                action.equippedSnapshot?.let {
                    try { json.decodeFromString<Map<String, String?>>(it).values.filterNotNull() }
                    catch (_: Exception) { emptyList() }
                } ?: emptyList()
            }
            val loadoutKeys = flags.armorLoadouts.values.flatMapTo(mutableSetOf()) { it.values.filterNotNull() } + queuedGearKeys
            maxOf(equipped.values.count { it == itemKey }, if (itemKey in loadoutKeys) 1 else 0)
        } else 0
        val reserved = computeReserved(flags.sessionQueue)[itemKey] ?: 0
        val keeper = if (flags.shopKeepOneOfEach && equippedCount == 0) 1 else 0
        return (have - equippedCount - reserved - keeper).coerceAtLeast(0)
    }

    fun dismissBulkSell() = _extra.update { it.copy(pendingBulkSell = null) }

    // ------------------------------------------------------------------
    // Transactions
    // ------------------------------------------------------------------

    fun openBuy(entry: ShopEntry) {
        if (uiState.value.ironman) {
            _extra.update { it.copy(snackbarMessage = context.withAppLocale().getString(R.string.ironman_shop_buy_blocked)) }
            return
        }
        val isXpBoost = entry.key == XP_BOOST_KEY
        if (isXpBoost) {
            val state = uiState.value
            if (state.xpBoostActive) {
                _extra.update { it.copy(snackbarMessage = context.withAppLocale().getString(R.string.shop_xp_boost_already_active)) }
                return
            }
            if (state.xpBoostLastPurchaseAt > 0 &&
                System.currentTimeMillis() < weeklyQuestRepo.nextResetMs(state.xpBoostLastPurchaseAt, state.dailyResetHour)
            ) {
                _extra.update { it.copy(snackbarMessage = context.withAppLocale().getString(R.string.shop_xp_boost_weekly_limit)) }
                return
            }
        }
        val discount  = mercantileBuyDiscount()
        val discPrice = (entry.price * discount).toInt().coerceAtLeast(1)
        val maxAffordable = (uiState.value.coins / discPrice).coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
        _extra.update {
            it.copy(
                transaction = ShopTransaction(
                    key         = entry.key,
                    displayName = entry.displayName,
                    priceEach   = discPrice,
                    maxQty      = if (isXpBoost) 1 else maxAffordable,
                    qty         = 1,
                    isBuy       = true,
                )
            )
        }
    }

    fun openSell(itemKey: String, displayName: String) {
        val state         = uiState.value
        if (itemKey in state.lockedItems) {
            _extra.update { it.copy(snackbarMessage = context.withAppLocale().getString(R.string.shop_item_locked, displayName)) }
            return
        }
        if (gameData.equipment[itemKey]?.heirloomSkill != null) {
            _extra.update { it.copy(snackbarMessage = context.withAppLocale().getString(R.string.shop_sell_blocked_heirloom, displayName)) }
            return
        }
        val have          = state.inventory[itemKey] ?: 0
        // Gear remembered in a non-active style's loadout counts as equipped (issue #1597).
        val equippedCount = maxOf(
            state.equipped.values.count { it == itemKey },
            if (state.armorLoadouts.values.any { itemKey in it.values }) 1 else 0,
        )
        val reserved      = state.reservedItems[itemKey] ?: 0
        // The equipped copy already serves as the collection keeper (issue #1419)
        val keptForCollection = if (state.keepOneOfEach && equippedCount == 0) 1 else 0
        val sellable      = (have - equippedCount - reserved - keptForCollection).coerceAtLeast(0)
        if (sellable == 0) {
            val reason = when {
                equippedCount > 0     -> context.withAppLocale().getString(R.string.shop_sell_blocked_equipped, displayName)
                reserved > 0          -> context.withAppLocale().getString(R.string.shop_sell_blocked_reserved, displayName)
                keptForCollection > 0 && have > 0 -> context.withAppLocale().getString(R.string.shop_sell_blocked_keep_one, displayName)
                else                  -> context.withAppLocale().getString(R.string.shop_sell_blocked_none)
            }
            _extra.update { it.copy(snackbarMessage = reason) }
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
        if (t.isBuy && uiState.value.ironman) {
            _extra.update { it.copy(transaction = null, snackbarMessage = context.withAppLocale().getString(R.string.ironman_shop_buy_blocked)) }
            return
        }
        viewModelScope.launch {
            // Special handling for XP boost
            if (t.key == XP_BOOST_KEY) {
                val result = playerRepo.activateXpBoost(PlayerRepository.XP_BOOST_DURATION_MS, t.priceEach.toLong())
                _extra.update {
                    it.copy(
                        transaction     = null,
                        snackbarMessage = when (result) {
                            XpBoostPurchaseResult.SUCCESS              -> context.withAppLocale().getString(R.string.shop_xp_boost_activated, 48)
                            XpBoostPurchaseResult.NOT_ENOUGH_COINS     -> context.withAppLocale().getString(R.string.error_not_enough_coins)
                            XpBoostPurchaseResult.ALREADY_ACTIVE       -> context.withAppLocale().getString(R.string.shop_xp_boost_already_active)
                            XpBoostPurchaseResult.WEEKLY_LIMIT_REACHED -> context.withAppLocale().getString(R.string.shop_xp_boost_weekly_limit)
                        },
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
                        if (t.isBuy) context.withAppLocale().getString(R.string.shop_bought_item, t.qty, GameStrings.itemName(context, t.key))
                        else         context.withAppLocale().getString(R.string.shop_sold_item, t.qty, GameStrings.itemName(context, t.key), t.priceEach * t.qty)
                    } else {
                        if (t.isBuy) context.withAppLocale().getString(R.string.error_not_enough_coins) else context.withAppLocale().getString(R.string.shop_not_enough_in_inventory)
                    },
                )
            }
        }
    }

    fun snackbarConsumed() = _extra.update { it.copy(snackbarMessage = null) }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    /** Capes entries only appear once earned (ever seen) and no longer owned/equipped — everything else is always eligible. */
    fun isBuyEntryEligible(entry: ShopEntry, state: ShopUiState): Boolean {
        if (entry.categoryName != CAPES_CATEGORY) return true
        val owned = (state.inventory[entry.key] ?: 0) > 0 || entry.key in state.equipped.values
        return entry.key in state.seenItemKeys && !owned
    }

    fun sellCategoryFor(itemKey: String): String {
        val equip = gameData.equipment[itemKey]
        if (equip != null) {
            return when (equip.slot) {
                EquipSlot.WEAPON                                      -> "Weapons"
                EquipSlot.PICKAXE, EquipSlot.AXE, EquipSlot.FISHING_ROD, EquipSlot.HOE,
                EquipSlot.HAMMER, EquipSlot.TINDERBOX, EquipSlot.GRAPPLING_HOOK,
                EquipSlot.FRYING_PAN, EquipSlot.LOCKPICK                               -> "Tools"
                else                                                  -> "Armor"
            }
        }
        if (itemKey in gameData.foodHealValues) return "Food"
        return when {
            itemKey.endsWith("_ore") || itemKey == "ore" || itemKey.endsWith("_bar") || itemKey == "bar" ||
            itemKey.endsWith("_gem") || itemKey == "gem" || itemKey.endsWith("_log") || itemKey == "log" ||
            itemKey.endsWith("_bone") || itemKey == "bone" || "essence" in itemKey ||
            "arrow"   in itemKey ||
            "raw_"    in itemKey || "cooked"  in itemKey -> "Materials"
            else                              -> "Misc"
        }
    }

    private fun Long.toCoinsString(): String =
        if (this >= 1_000_000) "${"%.1f".format(this / 1_000_000.0)}M"
        else if (this >= 1_000) "${"%.1f".format(this / 1_000.0)}k"
        else this.toString()

    companion object {
        const val XP_BOOST_KEY = "xp_boost_48h"
        const val CAPES_CATEGORY = "Capes"
        const val MAX_BULK_SELL_RECEIPTS = 5

        /**
         * Construction furniture otherwise falls to the generic 5-coin fallback (issue #1337).
         * Values are the salvage sum of each recipe's materials at their own sell prices,
         * matching how bars price against ore+coal. Kept below buy-material cost so max
         * Mercantile sell bonuses can't turn buy-craft-sell into a coin loop.
         */
        private val CONSTRUCTION_SELL = mapOf(
            "wooden_rack"    to 10,
            "wooden_shelf"   to 15,
            "carved_stone"   to 4,
            "oak_table"      to 30,
            "oak_bookshelf"  to 40,
            "stone_block"    to 10,
            "willow_cabinet" to 75,
            "maple_dresser"  to 160,
            "yew_wardrobe"   to 340,
            "magic_throne"   to 850,
            "redwood_bench"  to 1050,
            "redwood_bookcase" to 1400,
            "redwood_grand_bed" to 1800,
        )

        /**
         * Pure logic for computing which old equipment items to suggest selling.
         * Extracted from [previewSellOldEquipment] for testability.
         *
         * @param equipped  current equipped items: slot → item key
         * @param inventory player's inventory: item key → quantity
         * @param allEquip  all equipment data: item key → [EquipmentData]
         * @return map of item key → quantity to sell
         */
        /**
         * Every equippable in the inventory is sellable except: copies currently equipped,
         * copies remembered in any combat style's armor loadout (only one style's gear is in
         * [equipped] at a time, so loadout gear would otherwise be sold, issue #1597),
         * copies reserved by queued actions, skill capes (rare level-99 rewards, issue #821),
         * and one keeper per item when [keepOneOfEach] is on and none is equipped (the
         * equipped copy already serves as the collection keeper, issue #1419). Selling gear
         * that is not strictly outclassed is intentional since the keep-one toggle exists.
         */
        fun computeOldEquipmentToSell(
            equipped: Map<String, String?>,
            inventory: Map<String, Int>,
            allEquip: Map<String, EquipmentData>,
            keepOneOfEach: Boolean = false,
            reserved: Map<String, Int> = emptyMap(),
            armorLoadouts: Map<String, Map<String, String?>> = emptyMap(),
            queuedGearKeys: Set<String> = emptySet(),
        ): Map<String, Int> {
            val loadoutKeys = armorLoadouts.values.flatMapTo(mutableSetOf()) { it.values.filterNotNull() } + queuedGearKeys
            val toSell = mutableMapOf<String, Int>()
            for ((itemKey, qty) in inventory) {
                val item = allEquip[itemKey] ?: continue
                if (item.capeSkill != null) continue
                // A loadout reference protects at most one copy: styles swap over the same
                // physical item, so one kept copy serves every loadout that remembers it.
                val equippedCount = maxOf(
                    equipped.values.count { it == itemKey },
                    if (itemKey in loadoutKeys) 1 else 0,
                )
                val keeper = if (keepOneOfEach && equippedCount == 0) 1 else 0
                val extras = qty - equippedCount - (reserved[itemKey] ?: 0) - keeper
                if (extras > 0) toSell[itemKey] = extras
            }
            return toSell
        }

        private val FISH_KEYS = setOf(
            "shrimp", "sardine", "herring", "mackerel", "trout", "salmon",
            "tuna", "lobster", "swordfish", "monkfish", "shark", "sea_turtle", "manta_ray",
            "raw_shrimp", "raw_sardine", "raw_herring", "raw_mackerel", "raw_trout", "raw_salmon",
            "raw_tuna", "raw_lobster", "raw_swordfish", "raw_monkfish", "raw_shark",
            "raw_sea_turtle", "raw_manta_ray",
        )

        // Base sell value by material tier; multiply by SLOT_MULTIPLIER to get final price.
        // Calibrated so runite_platebody (200 × 5.0) = 1000 coins.
        private val METAL_BASE = mapOf(
            "bronze"     to 8,
            "iron"       to 20,
            "steel"      to 50,
            "mithril"    to 100,
            "adamantite" to 150,
            "runite"     to 200,
            "dwarven"    to 250,
            "dragon"     to 200,
            "balrog"     to 350,
        )

        private val BOW_BASE = mapOf(
            "wooden"  to 8,
            "oak"     to 20,
            "willow"  to 40,
            "maple"   to 80,
            "yew"     to 150,
            "magic"   to 250,
        )

        private val JEWEL_BASE = mapOf(
            "silver"   to 15,
            "gold"     to 30,
            "platinum" to 80,
        )

        private val GEM_BONUS = mapOf(
            "sapphire" to 30,
            "emerald"  to 60,
            "ruby"     to 110,
            "diamond"  to 190,
        )

        private val SLOT_MULTIPLIER = mapOf(
            EquipSlot.WEAPON      to 2.5f,
            EquipSlot.BODY        to 5.0f,
            EquipSlot.HEAD        to 3.5f,
            EquipSlot.LEGS        to 3.5f,
            EquipSlot.SHIELD      to 3.5f,
            EquipSlot.BOOTS       to 1.0f,
            EquipSlot.CAPE        to 2.0f,
            EquipSlot.RING        to 1.5f,
            EquipSlot.NECKLACE    to 1.5f,
            EquipSlot.PICKAXE     to 2.5f,
            EquipSlot.AXE         to 2.5f,
            EquipSlot.FISHING_ROD to 2.0f,
            EquipSlot.HOE         to 2.0f,
        )
    }
}
