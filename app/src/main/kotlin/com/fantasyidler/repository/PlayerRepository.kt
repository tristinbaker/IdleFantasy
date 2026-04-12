package com.fantasyidler.repository

import com.fantasyidler.data.db.dao.PlayerDao
import com.fantasyidler.data.model.*
import com.fantasyidler.simulator.XpTable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import javax.inject.Inject
import javax.inject.Singleton

// ---------------------------------------------------------------------------
// Helpers — use explicit two-arg encodeToString(serializer, value) to avoid
// Kotlin 2.0 extension/member resolution ambiguity with the single-arg form.
// ---------------------------------------------------------------------------

private inline fun <reified T> Json.encode(value: T): String =
    encodeToString(serializersModule.serializer<T>(), value)

@Singleton
class PlayerRepository @Inject constructor(
    private val playerDao: PlayerDao,
    private val json: Json,
) {
    /**
     * Emits the raw [Player] entity whenever the DB row changes.
     * Creates the default player on first access so observers never stall on null.
     */
    val playerFlow: Flow<Player?> = flow {
        getOrCreatePlayer()
        emitAll(playerDao.observePlayer())
    }

    /** Returns the player, creating a default profile if none exists. */
    suspend fun getOrCreatePlayer(): Player =
        playerDao.getPlayer() ?: createDefaultPlayer().also { playerDao.upsert(it) }

    suspend fun getSkillLevels(): Map<String, Int> =
        json.decodeFromString(getOrCreatePlayer().skillLevels)

    suspend fun getSkillXp(): Map<String, Long> =
        json.decodeFromString(getOrCreatePlayer().skillXp)

    suspend fun getInventory(): Map<String, Int> =
        json.decodeFromString(getOrCreatePlayer().inventory)

    suspend fun getEquipped(): Map<String, String?> =
        json.decodeFromString(getOrCreatePlayer().equipped)

    suspend fun getFlags(): PlayerFlags =
        json.decodeFromString(getOrCreatePlayer().flags)

    suspend fun getOwnedPets(): List<OwnedPet> =
        json.decodeFromString(getOrCreatePlayer().pets)

    // ------------------------------------------------------------------
    // Write operations
    // ------------------------------------------------------------------

    /**
     * Apply completed session results to the player: add XP, recalculate level,
     * and merge loot into inventory.
     */
    suspend fun applySessionResults(
        skillName: String,
        xpGained: Long,
        itemsGained: Map<String, Int>,
    ) {
        val player = getOrCreatePlayer()
        val levels: MutableMap<String, Int>  = json.decodeFromString(player.skillLevels)
        val xpMap: MutableMap<String, Long>  = json.decodeFromString(player.skillXp)
        val inventory: MutableMap<String, Int> = json.decodeFromString(player.inventory)

        val newXp = (xpMap[skillName] ?: 0L) + xpGained
        xpMap[skillName] = newXp
        levels[skillName] = XpTable.levelForXp(newXp)

        for ((item, qty) in itemsGained) {
            inventory[item] = (inventory[item] ?: 0) + qty
        }

        playerDao.upsert(
            player.copy(
                skillLevels = json.encode<Map<String, Int>>(levels),
                skillXp     = json.encode<Map<String, Long>>(xpMap),
                inventory   = json.encode<Map<String, Int>>(inventory),
            )
        )
    }

    /**
     * Remove items from the player's inventory.
     * Returns false (and makes no change) if any item is in insufficient quantity.
     */
    suspend fun consumeItems(items: Map<String, Int>): Boolean {
        val player = getOrCreatePlayer()
        val inventory: MutableMap<String, Int> = json.decodeFromString(player.inventory)

        for ((item, qty) in items) {
            if ((inventory[item] ?: 0) < qty) return false
        }
        for ((item, qty) in items) {
            val newQty = (inventory[item] ?: 0) - qty
            if (newQty <= 0) inventory.remove(item) else inventory[item] = newQty
        }
        playerDao.upsert(player.copy(inventory = json.encode<Map<String, Int>>(inventory)))
        return true
    }

    suspend fun addCoins(amount: Long) {
        val player = getOrCreatePlayer()
        playerDao.upsert(player.copy(coins = player.coins + amount))
    }

    /** Returns false if the player has insufficient coins. */
    suspend fun spendCoins(amount: Long): Boolean {
        val player = getOrCreatePlayer()
        if (player.coins < amount) return false
        playerDao.upsert(player.copy(coins = player.coins - amount))
        return true
    }

    suspend fun updateFlags(flags: PlayerFlags) {
        val player = getOrCreatePlayer()
        playerDao.upsert(player.copy(flags = json.encode<PlayerFlags>(flags)))
    }

    suspend fun updateEquipped(equipped: Map<String, String?>) {
        val player = getOrCreatePlayer()
        playerDao.upsert(player.copy(equipped = json.encode<Map<String, String?>>(equipped)))
    }

    suspend fun updatePets(pets: List<OwnedPet>) {
        val player = getOrCreatePlayer()
        playerDao.upsert(player.copy(pets = json.encode<List<OwnedPet>>(pets)))
    }

    /** Buy [qty] of [itemKey] at [priceEach] coins. Returns false if insufficient coins. */
    suspend fun buyItem(itemKey: String, qty: Int, priceEach: Int): Boolean {
        val player = getOrCreatePlayer()
        val total  = priceEach.toLong() * qty
        if (player.coins < total) return false
        val inventory: MutableMap<String, Int> = json.decodeFromString(player.inventory)
        inventory[itemKey] = (inventory[itemKey] ?: 0) + qty
        playerDao.upsert(
            player.copy(
                coins     = player.coins - total,
                inventory = json.encode<Map<String, Int>>(inventory),
            )
        )
        return true
    }

    /** Sell [qty] of [itemKey] for [priceEach] coins each. Returns false if not enough in inventory. */
    suspend fun sellItem(itemKey: String, qty: Int, priceEach: Int): Boolean {
        val player = getOrCreatePlayer()
        val inventory: MutableMap<String, Int> = json.decodeFromString(player.inventory)
        if ((inventory[itemKey] ?: 0) < qty) return false
        val remaining = (inventory[itemKey] ?: 0) - qty
        if (remaining <= 0) inventory.remove(itemKey) else inventory[itemKey] = remaining
        playerDao.upsert(
            player.copy(
                coins     = player.coins + priceEach.toLong() * qty,
                inventory = json.encode<Map<String, Int>>(inventory),
            )
        )
        return true
    }

    /**
     * Apply combat session results: XP distributed across multiple skills,
     * loot added to inventory, coins added to the coins field.
     */
    suspend fun applyMultiSkillResults(
        xpPerSkill: Map<String, Long>,
        itemsGained: Map<String, Int>,
        coinsGained: Long = 0L,
    ) {
        val player    = getOrCreatePlayer()
        val levels:    MutableMap<String, Int>  = json.decodeFromString(player.skillLevels)
        val xpMap:     MutableMap<String, Long> = json.decodeFromString(player.skillXp)
        val inventory: MutableMap<String, Int>  = json.decodeFromString(player.inventory)

        for ((skill, xp) in xpPerSkill) {
            val newXp = (xpMap[skill] ?: 0L) + xp
            xpMap[skill]   = newXp
            levels[skill]  = XpTable.levelForXp(newXp)
        }
        for ((item, qty) in itemsGained) {
            inventory[item] = (inventory[item] ?: 0) + qty
        }

        playerDao.upsert(
            player.copy(
                skillLevels = json.encode<Map<String, Int>>(levels),
                skillXp     = json.encode<Map<String, Long>>(xpMap),
                inventory   = json.encode<Map<String, Int>>(inventory),
                coins       = player.coins + coinsGained,
            )
        )
    }

    /**
     * Atomically craft [quantity] of a recipe:
     *   1. Verify and consume [materialsPerItem] × [quantity]
     *   2. Add [outputKey] × ([outputQtyPerItem] × [quantity]) to inventory
     *   3. Award [xpPerItem] × [quantity] XP to [skillName]
     *
     * Returns false (no changes) if the player lacks any required material.
     */
    suspend fun applyCraftingResult(
        skillName: String,
        quantity: Int,
        xpPerItem: Double,
        materialsPerItem: Map<String, Int>,
        outputKey: String,
        outputQtyPerItem: Int,
    ): Boolean {
        val player    = getOrCreatePlayer()
        val inventory: MutableMap<String, Int>  = json.decodeFromString(player.inventory)
        val levels:    MutableMap<String, Int>  = json.decodeFromString(player.skillLevels)
        val xpMap:     MutableMap<String, Long> = json.decodeFromString(player.skillXp)

        // Check all materials are available
        for ((item, needed) in materialsPerItem) {
            if ((inventory[item] ?: 0) < needed * quantity) return false
        }

        // Consume materials
        for ((item, needed) in materialsPerItem) {
            val remaining = (inventory[item] ?: 0) - needed * quantity
            if (remaining <= 0) inventory.remove(item) else inventory[item] = remaining
        }

        // Add output
        val totalOut = outputQtyPerItem * quantity
        inventory[outputKey] = (inventory[outputKey] ?: 0) + totalOut

        // Add XP and recalculate level
        val xpGained = (xpPerItem * quantity).toLong()
        val newXp    = (xpMap[skillName] ?: 0L) + xpGained
        xpMap[skillName]    = newXp
        levels[skillName]   = XpTable.levelForXp(newXp)

        playerDao.upsert(
            player.copy(
                inventory   = json.encode<Map<String, Int>>(inventory),
                skillLevels = json.encode<Map<String, Int>>(levels),
                skillXp     = json.encode<Map<String, Long>>(xpMap),
            )
        )
        return true
    }

    /**
     * Adds [petId] to the player's pet list if not already owned.
     * Returns true if the pet was newly added, false if already owned.
     */
    suspend fun addPetIfNew(petId: String, boostPercent: Int = 0): Boolean {
        val player = getOrCreatePlayer()
        val pets: MutableList<OwnedPet> = json.decodeFromString(player.pets)
        if (pets.any { it.id == petId }) return false
        pets.add(OwnedPet(id = petId, boostPercent = boostPercent))
        playerDao.upsert(player.copy(pets = json.encode<List<OwnedPet>>(pets)))
        return true
    }

    /**
     * Removes [materialsPerItem] × [quantity] from inventory.
     * Returns false (no changes) if the player lacks any required material.
     */
    suspend fun consumeMaterials(
        materialsPerItem: Map<String, Int>,
        quantity: Int,
    ): Boolean {
        val player    = getOrCreatePlayer()
        val inventory: MutableMap<String, Int> = json.decodeFromString(player.inventory)

        for ((item, needed) in materialsPerItem) {
            if ((inventory[item] ?: 0) < needed * quantity) return false
        }
        for ((item, needed) in materialsPerItem) {
            val remaining = (inventory[item] ?: 0) - needed * quantity
            if (remaining <= 0) inventory.remove(item) else inventory[item] = remaining
        }
        playerDao.upsert(player.copy(inventory = json.encode<Map<String, Int>>(inventory)))
        return true
    }

    suspend fun resetProgression() {
        playerDao.upsert(createDefaultPlayer())
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun createDefaultPlayer(): Player {
        val defaultEquipped: Map<String, String?> = EquipSlot.ALL.associateWith { null } +
            mapOf(
                EquipSlot.PICKAXE     to "bronze_pickaxe",
                EquipSlot.AXE         to "bronze_axe",
                EquipSlot.FISHING_ROD to "bronze_fishing_rod",
            )
        val defaultInventory: Map<String, Int> = mapOf(
            "bronze_pickaxe"     to 1,
            "bronze_axe"         to 1,
            "bronze_fishing_rod" to 1,
        )
        return Player(
            skillLevels = json.encode<Map<String, Int>>(Skills.DEFAULT_LEVELS),
            skillXp     = json.encode<Map<String, Long>>(Skills.DEFAULT_XP),
            inventory   = json.encode<Map<String, Int>>(defaultInventory),
            equipped    = json.encode<Map<String, String?>>(defaultEquipped),
            flags       = json.encode<PlayerFlags>(PlayerFlags()),
        )
    }
}
