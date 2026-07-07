package com.fantasyidler.repository

import com.fantasyidler.data.db.dao.FarmingPatchDao
import com.fantasyidler.data.db.dao.PlayerDao
import com.fantasyidler.data.db.dao.QuestProgressDao
import com.fantasyidler.data.model.*
import com.fantasyidler.simulator.SkillSimulator
import com.fantasyidler.simulator.XpTable
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.withLock
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

private fun capeKeyForSkill(skill: String): String? = when (skill) {
    Skills.HITPOINTS -> "hp_cape"
    else             -> "${skill}_cape"
}

private fun PlayerFlags.plusSeen(keys: Collection<String>): PlayerFlags =
    if (keys.isEmpty()) this else copy(seenItemKeys = seenItemKeys + keys)

@Singleton
class PlayerRepository @Inject constructor(
    private val playerDao: PlayerDao,
    private val questProgressDao: QuestProgressDao,
    private val farmingPatchDao: FarmingPatchDao,
    private val json: Json,
    private val dailyQuestRepo: DailyQuestRepository,
    private val weeklyQuestRepo: WeeklyQuestRepository,
    private val buffNotifScheduler: BuffNotificationScheduler,
    private val gameData: GameDataRepository,
) {
    val playerMutex = kotlinx.coroutines.sync.Mutex()

    /**
     * Emits the raw [Player] entity whenever the DB row changes.
     * Creates the default player on first access so observers never stall on null.
     */
    val playerFlow: Flow<Player?> = flow {
        getOrCreatePlayer()
        emitAll(playerDao.observePlayer())
    }

    /** Returns the player, creating a default profile if none exists. */
    suspend fun getOrCreatePlayer(): Player {
        val player = playerDao.getPlayer() ?: createDefaultPlayer().also { playerDao.upsert(it) }
        return if (player.skillXp.contains("\"hp\":")) migrateHpKey(player) else player
    }

    private suspend fun migrateHpKey(player: Player): Player {
        val xpMap: MutableMap<String, Long> = json.decodeFromString(player.skillXp)
        val hpXp = xpMap.remove("hp") ?: return player
        val levels: MutableMap<String, Int> = json.decodeFromString(player.skillLevels)
        levels.remove("hp")
        val newHpXp = (xpMap[Skills.HITPOINTS] ?: 0L) + hpXp
        xpMap[Skills.HITPOINTS] = newHpXp
        levels[Skills.HITPOINTS] = XpTable.levelForXp(newHpXp)
        val migrated = player.copy(
            skillXp     = json.encode<Map<String, Long>>(xpMap),
            skillLevels = json.encode<Map<String, Int>>(levels),
        )
        playerDao.upsert(migrated)
        return migrated
    }

    suspend fun getSkillLevels(): Map<String, Int> =
        json.decodeFromString(getOrCreatePlayer().skillLevels)

    suspend fun getSkillXp(): Map<String, Long> =
        json.decodeFromString(getOrCreatePlayer().skillXp)

    suspend fun getInventory(): Map<String, Int> =
        json.decodeFromString(getOrCreatePlayer().inventory)

    internal suspend fun getInventoryUnlocked(): Map<String, Int> =
        json.decodeFromString(getOrCreatePlayer().inventory)

    suspend fun getEquipped(): Map<String, String?> =
        json.decodeFromString(getOrCreatePlayer().equipped)

    suspend fun getFlags(): PlayerFlags =
        json.decodeFromString(getOrCreatePlayer().flags)

    internal suspend fun getFlagsUnlocked(): PlayerFlags =
        json.decodeFromString(getOrCreatePlayer().flags)

    suspend fun getOwnedPets(): List<OwnedPet> =
        json.decodeFromString(getOrCreatePlayer().pets)

    // ------------------------------------------------------------------
    // Write operations
    // ------------------------------------------------------------------

    /**
     * Apply completed session results to the player: add XP (doubled if boost active),
     * recalculate level, and merge loot into inventory.
     * Returns the keys of any skill capes awarded (level 99 reached for the first time).
     */
    suspend fun applySessionResults(
        skillName: String,
        xpGained: Long,
        itemsGained: Map<String, Int>,
        efficiencyMultiplier: Float = 1.0f,
    ): List<String> = playerMutex.withLock {
        val player    = getOrCreatePlayer()
        val flags: PlayerFlags = json.decodeFromString(player.flags)
        val boostActive = flags.xpBoostExpiresAt > System.currentTimeMillis()
        val scaledXp = if (efficiencyMultiplier == 1.0f) xpGained else (xpGained * efficiencyMultiplier).toLong()
        val baseXp = ((if (boostActive) scaledXp * 2 else scaledXp) * ChurchRepository.xpMultiplier(flags)).toLong()
        val prestigeLevel = flags.skillPrestige[skillName] ?: 0
        val boostedXp = if (prestigeLevel > 0) (baseXp * (1.0 + prestigeLevel * 0.10)).toLong() else baseXp
        val scaledItems = if (efficiencyMultiplier == 1.0f) itemsGained
            else itemsGained.mapValues { (_, v) -> (v * efficiencyMultiplier).roundToInt().coerceAtLeast(1) }

        val levels: MutableMap<String, Int>  = json.decodeFromString(player.skillLevels)
        val xpMap: MutableMap<String, Long>  = json.decodeFromString(player.skillXp)
        val inventory: MutableMap<String, Int> = json.decodeFromString(player.inventory)

        val oldLevel = XpTable.levelForXp(xpMap[skillName] ?: 0L)
        val newXp = (xpMap[skillName] ?: 0L) + boostedXp
        xpMap[skillName] = newXp
        levels[skillName] = XpTable.levelForXp(newXp)

        val awardedCapes = mutableListOf<String>()
        if (oldLevel < 99 && levels[skillName]!! >= 99) {
            val capeKey = capeKeyForSkill(skillName)
            if (capeKey != null && !inventory.containsKey(capeKey)) {
                inventory[capeKey] = 1
                awardedCapes += capeKey
            }
        }

        for ((item, qty) in scaledItems) {
            inventory[item] = (inventory[item] ?: 0) + qty
        }

        playerDao.upsert(
            player.copy(
                skillLevels = json.encode<Map<String, Int>>(levels),
                skillXp     = json.encode<Map<String, Long>>(xpMap),
                inventory   = json.encode<Map<String, Int>>(inventory),
                flags       = json.encode<PlayerFlags>(flags.plusSeen(scaledItems.keys + awardedCapes)),
            )
        )
        return awardedCapes
    }

    /** Subtract XP from a skill, flooring at 0. Recalculates level. */
    suspend fun deductSkillXp(skillName: String, amount: Long) {
        val player = getOrCreatePlayer()
        val levels: MutableMap<String, Int> = json.decodeFromString(player.skillLevels)
        val xpMap:  MutableMap<String, Long> = json.decodeFromString(player.skillXp)
        val newXp = ((xpMap[skillName] ?: 0L) - amount).coerceAtLeast(0L)
        xpMap[skillName]    = newXp
        levels[skillName]   = XpTable.levelForXp(newXp)
        playerDao.upsert(player.copy(
            skillLevels = json.encode<Map<String, Int>>(levels),
            skillXp     = json.encode<Map<String, Long>>(xpMap),
        ))
    }

    data class BuryBoneResult(val xpGained: Long, val awardedCape: String?)

    /**
     * Atomically consume one [boneKey] from inventory and award [xpToAward] prayer XP.
     * Returns a result with xpGained=0 if the bone is not in inventory.
     */
    suspend fun buryBoneAtomic(boneKey: String, xpToAward: Long): BuryBoneResult {
        val player    = getOrCreatePlayer()
        val inventory: MutableMap<String, Int> = json.decodeFromString(player.inventory)
        if ((inventory[boneKey] ?: 0) <= 0) return BuryBoneResult(0L, null)

        val newQty = (inventory[boneKey] ?: 0) - 1
        if (newQty <= 0) inventory.remove(boneKey) else inventory[boneKey] = newQty

        val levels: MutableMap<String, Int> = json.decodeFromString(player.skillLevels)
        val xpMap:  MutableMap<String, Long> = json.decodeFromString(player.skillXp)
        val oldLevel = XpTable.levelForXp(xpMap[Skills.PRAYER] ?: 0L)
        val newXp    = (xpMap[Skills.PRAYER] ?: 0L) + xpToAward
        xpMap[Skills.PRAYER]  = newXp
        levels[Skills.PRAYER] = XpTable.levelForXp(newXp)

        var awardedCape: String? = null
        if (oldLevel < 99 && levels[Skills.PRAYER]!! >= 99) {
            val capeKey = capeKeyForSkill(Skills.PRAYER)
            if (capeKey != null && !inventory.containsKey(capeKey)) {
                inventory[capeKey] = 1
                awardedCape = capeKey
            }
        }

        playerDao.upsert(player.copy(
            inventory   = json.encode<Map<String, Int>>(inventory),
            skillLevels = json.encode<Map<String, Int>>(levels),
            skillXp     = json.encode<Map<String, Long>>(xpMap),
        ))
        return BuryBoneResult(xpToAward, awardedCape)
    }

    /**
     * Remove items from the player's inventory.
     * Returns false (and makes no change) if any item is in insufficient quantity.
     */
    suspend fun consumeItems(items: Map<String, Int>): Boolean = playerMutex.withLock { consumeItemsUnlocked(items) }

    internal suspend fun consumeItemsUnlocked(items: Map<String, Int>): Boolean {
        val player = getOrCreatePlayer()
        val inventory: MutableMap<String, Int> = json.decodeFromString(player.inventory)

        for ((item, qty) in items) {
            require(qty >= 0) { "Cannot consume negative quantity" }
            if ((inventory[item] ?: 0) < qty) return false
        }
        for ((item, qty) in items) {
            val newQty = (inventory[item] ?: 0) - qty
            if (newQty <= 0) inventory.remove(item) else inventory[item] = newQty
        }
        playerDao.upsert(player.copy(inventory = json.encode<Map<String, Int>>(inventory)))
        return true
    }

    suspend fun addCoins(amount: Long) = playerMutex.withLock { addCoinsUnlocked(amount) }

    private suspend fun addCoinsUnlocked(amount: Long) {
        require(amount >= 0) { "Cannot add negative coins" }
        val player = getOrCreatePlayer()
        val newCoins = (player.coins + amount).coerceAtMost(Long.MAX_VALUE)
        playerDao.upsert(player.copy(coins = newCoins))
    }

    /** Awards capes for any skill already at 99 that doesn't have one yet (retroactive fix). */
    suspend fun awardMissingCapes() = playerMutex.withLock { awardMissingCapesUnlocked() }

    private suspend fun awardMissingCapesUnlocked() {
        val player    = getOrCreatePlayer()
        val levels:    MutableMap<String, Int> = json.decodeFromString(player.skillLevels)
        val inventory: MutableMap<String, Int> = json.decodeFromString(player.inventory)
        var changed = false
        for ((skill, level) in levels) {
            if (level >= 99) {
                val capeKey = capeKeyForSkill(skill) ?: continue
                if (!inventory.containsKey(capeKey)) {
                    inventory[capeKey] = 1
                    changed = true
                }
            }
        }
        if (changed) playerDao.upsert(player.copy(inventory = json.encode<Map<String, Int>>(inventory)))
    }

    /** Adds qty of item to the player's inventory at no coin cost (prize/drop grant). */
    suspend fun grantItem(key: String, qty: Int = 1) = playerMutex.withLock { grantItemUnlocked(key, qty) }

    private suspend fun grantItemUnlocked(key: String, qty: Int = 1) {
        require(qty >= 0) { "Cannot grant negative quantity" }
        val player = getOrCreatePlayer()
        val inventory: MutableMap<String, Int> = json.decodeFromString(player.inventory)
        inventory[key] = ((inventory[key] ?: 0).toLong() + qty).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val flags: PlayerFlags = json.decodeFromString(player.flags)
        playerDao.upsert(player.copy(
            inventory = json.encode<Map<String, Int>>(inventory),
            flags     = json.encode<PlayerFlags>(flags.plusSeen(listOf(key))),
        ))
    }

    /** Returns false if the player has insufficient coins. */
    suspend fun spendCoins(amount: Long): Boolean = playerMutex.withLock { spendCoinsUnlocked(amount) }

    internal suspend fun spendCoinsUnlocked(amount: Long): Boolean {
        require(amount >= 0) { "Cannot spend negative coins" }
        val player = getOrCreatePlayer()
        if (player.coins < amount) return false
        playerDao.upsert(player.copy(coins = player.coins - amount))
        return true
    }

    suspend fun updateFlags(flags: PlayerFlags) = playerMutex.withLock { updateFlagsUnlocked(flags) }

    suspend fun updateFlagsAtomically(block: (PlayerFlags) -> PlayerFlags) = playerMutex.withLock {
        val current = getFlagsUnlocked()
        updateFlagsUnlocked(block(current))
    }

    suspend fun <T> withLock(block: suspend () -> T): T = playerMutex.withLock { block() }

    internal suspend fun updateFlagsUnlocked(flags: PlayerFlags) {
        val player = getOrCreatePlayer()
        playerDao.upsert(player.copy(flags = json.encode<PlayerFlags>(flags)))
    }

    suspend fun getQueue(): List<QueuedAction> = getFlags().sessionQueue

    /** Appends an action to the queue. Returns false (no change) if the queue is already full (3 items). */
    suspend fun enqueueAction(action: QueuedAction): Boolean = playerMutex.withLock { enqueueActionUnlocked(action) }

    private suspend fun enqueueActionUnlocked(action: QueuedAction): Boolean {
        val flags = getFlags()
        if (flags.sessionQueue.size >= 3) return false
        updateFlagsUnlocked(flags.copy(sessionQueue = flags.sessionQueue + action))
        return true
    }

    /** Creates and enqueues a combat (dungeon) session for a Slayer task's auto-advance. Returns false if queue is full. */
    suspend fun enqueueCombatSession(dungeonKey: String, dungeonDisplayName: String): Boolean = playerMutex.withLock {
        val flags = getFlags()
        if (flags.sessionQueue.size >= 3) return@withLock false
        val player = getOrCreatePlayer()
        val levels: Map<String, Int> = json.decodeFromString(player.skillLevels)
        val agility = levels[Skills.AGILITY] ?: 1
        val agilityPrestige = flags.skillPrestige[Skills.AGILITY] ?: 0
        val equipped: Map<String, String?> = json.decodeFromString(player.equipped)
        val weaponSlot = flags.activeWeaponSlot
            ?: EquipSlot.WEAPON_SLOTS.firstOrNull { equipped[it] != null }
            ?: EquipSlot.WEAPON_ATK
        enqueueActionUnlocked(QueuedAction(
            skillName           = "combat",
            activityKey         = dungeonKey,
            skillDisplayName    = dungeonDisplayName,
            estimatedDurationMs = SkillSimulator.sessionDurationMs(agility, agilityPrestige),
            equippedSnapshot    = player.equipped,
            arrowsKey           = flags.equippedArrows,
            spellName           = flags.activeSpell,
            potionKey           = flags.activePotionKey,
            weaponSlot          = weaponSlot,
        ))
    }

    /** Removes and returns the first item in the queue, or null if empty. */
    suspend fun dequeueNextAction(): QueuedAction? = playerMutex.withLock { dequeueNextActionUnlocked() }

    internal suspend fun dequeueNextActionUnlocked(): QueuedAction? {
        val flags = getFlags()
        val queue = flags.sessionQueue
        if (queue.isEmpty()) return null
        updateFlagsUnlocked(flags.copy(sessionQueue = queue.drop(1)))
        return queue.first()
    }

    suspend fun requeueActionAtFront(action: QueuedAction) = playerMutex.withLock { requeueActionAtFrontUnlocked(action) }

    internal suspend fun requeueActionAtFrontUnlocked(action: QueuedAction) {
        val flags = getFlags()
        updateFlagsUnlocked(flags.copy(sessionQueue = listOf(action) + flags.sessionQueue))
    }

    private fun PlayerFlags.workerForSlot(slot: Int) = if (slot == 2) hiredWorker2 else hiredWorker
    private fun PlayerFlags.withWorkerForSlot(slot: Int, w: HiredWorker?) =
        if (slot == 2) copy(hiredWorker2 = w) else copy(hiredWorker = w)

    /** Appends an action to the given worker slot's queue. Returns false if full (1 item) or no worker hired. */
    suspend fun enqueueWorkerAction(slot: Int, action: QueuedAction): Boolean = playerMutex.withLock { enqueueWorkerActionUnlocked(slot, action) }

    internal suspend fun enqueueWorkerActionUnlocked(slot: Int, action: QueuedAction): Boolean {
        val flags = getFlags()
        val worker = flags.workerForSlot(slot) ?: return false
        if (worker.sessionQueue.size >= 1) return false
        updateFlagsUnlocked(flags.withWorkerForSlot(slot, worker.copy(sessionQueue = worker.sessionQueue + action)))
        return true
    }

    /** Removes and returns the first item in the given slot's queue, or null if empty/no worker. */
    suspend fun dequeueNextWorkerAction(slot: Int): QueuedAction? = playerMutex.withLock { dequeueNextWorkerActionUnlocked(slot) }

    internal suspend fun dequeueNextWorkerActionUnlocked(slot: Int): QueuedAction? {
        val flags = getFlags()
        val worker = flags.workerForSlot(slot) ?: return null
        val queue = worker.sessionQueue
        if (queue.isEmpty()) return null
        updateFlagsUnlocked(flags.withWorkerForSlot(slot, worker.copy(sessionQueue = queue.drop(1))))
        return queue.first()
    }

    suspend fun requeueWorkerActionAtFront(slot: Int, action: QueuedAction) = playerMutex.withLock { requeueWorkerActionAtFrontUnlocked(slot, action) }

    internal suspend fun requeueWorkerActionAtFrontUnlocked(slot: Int, action: QueuedAction) {
        val flags = getFlags()
        val worker = flags.workerForSlot(slot) ?: return
        updateFlagsUnlocked(flags.withWorkerForSlot(slot, worker.copy(sessionQueue = listOf(action) + worker.sessionQueue)))
    }

    suspend fun clearHiredWorker(slot: Int) = playerMutex.withLock {
        val flags = getFlags()
        updateFlagsUnlocked(flags.withWorkerForSlot(slot, null))
    }

    /** Removes and returns the queued item at [index], or null if out of range. */
    suspend fun removeFromQueue(index: Int): QueuedAction? {
        val flags = getFlags()
        val queue = flags.sessionQueue
        if (index < 0 || index >= queue.size) return null
        val removed = queue[index]
        val newQueue = queue.toMutableList().apply { removeAt(index) }
        updateFlags(flags.copy(sessionQueue = renumberTowerQueue(newQueue, flags.towerCurrentFloor)))
        return removed
    }

    /**
     * Keeps queued Infinite Tower floors contiguous after a cancellation, so a player can't
     * skip floors by cancelling low entries while a higher one survives in the queue.
     */
    private fun renumberTowerQueue(queue: List<QueuedAction>, currentFloor: Int): List<QueuedAction> {
        var nextFloor = currentFloor + 1
        return queue.map { action ->
            if (action.skillName != "tower") return@map action
            val renumbered = action.copy(
                activityKey      = "tower_floor_$nextFloor",
                skillDisplayName = "Infinite Tower: Floor $nextFloor",
            )
            nextFloor++
            renumbered
        }
    }

    suspend fun evictQueueForSkill(skillName: String): List<QueuedAction> {
        val flags = getFlags()
        val (evicted, remaining) = flags.sessionQueue.partition { it.skillName == skillName }
        if (evicted.isNotEmpty()) updateFlags(flags.copy(sessionQueue = remaining))
        return evicted
    }

    suspend fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val flags = getFlags()
        val queue = flags.sessionQueue.toMutableList()
        if (fromIndex < 0 || toIndex < 0 || fromIndex >= queue.size || toIndex >= queue.size) return
        val item = queue.removeAt(fromIndex)
        queue.add(toIndex, item)
        updateFlags(flags.copy(sessionQueue = queue))
    }

    suspend fun incrementDungeonRun(activityKey: String) {
        val flags = getFlags()
        val updated = flags.dungeonRuns.toMutableMap()
        updated[activityKey] = (updated[activityKey] ?: 0) + 1
        updateFlags(flags.copy(dungeonRuns = updated))
    }

    suspend fun markWhatsNewSeen(versionCode: Int) {
        updateFlags(getFlags().copy(lastSeenVersionCode = versionCode))
    }

    suspend fun updateCharacterProfile(name: String, gender: String, race: String) {
        val player = getOrCreatePlayer()
        val flags: PlayerFlags = json.decodeFromString(player.flags)
        val updated = flags.copy(
            characterName = name,
            characterGender = gender,
            characterRace = race,
            characterSetupDone = true,
        )
        playerDao.upsert(player.copy(flags = json.encode<PlayerFlags>(updated)))
    }

    suspend fun dismissCharacterSetup() {
        val player = getOrCreatePlayer()
        val flags: PlayerFlags = json.decodeFromString(player.flags)
        playerDao.upsert(player.copy(flags = json.encode<PlayerFlags>(flags.copy(characterSetupDone = true))))
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
        val flags: PlayerFlags = json.decodeFromString(player.flags)
        playerDao.upsert(
            player.copy(
                coins     = player.coins - total,
                inventory = json.encode<Map<String, Int>>(inventory),
                flags     = json.encode<PlayerFlags>(flags.plusSeen(listOf(itemKey))),
            )
        )
        return true
    }

    /** Sell [qty] of [itemKey] for [priceEach] coins each. Returns false if not enough in inventory. Unequips the item if no copies remain. */
    suspend fun sellItem(itemKey: String, qty: Int, priceEach: Int): Boolean {
        val player = getOrCreatePlayer()
        val inventory: MutableMap<String, Int> = json.decodeFromString(player.inventory)
        if ((inventory[itemKey] ?: 0) < qty) return false
        val remaining = (inventory[itemKey] ?: 0) - qty
        if (remaining <= 0) inventory.remove(itemKey) else inventory[itemKey] = remaining

        val equipped: MutableMap<String, String?> = json.decodeFromString(player.equipped)
        if (!inventory.containsKey(itemKey)) {
            equipped.entries.forEach { if (it.value == itemKey) it.setValue(null) }
        }

        playerDao.upsert(
            player.copy(
                coins     = player.coins + priceEach.toLong() * qty,
                inventory = json.encode<Map<String, Int>>(inventory),
                equipped  = json.encode<Map<String, String?>>(equipped),
            )
        )
        return true
    }

    /**
     * Apply combat session results: XP distributed across multiple skills (doubled if
     * boost active), loot added to inventory, coins added to the coins field.
     * Returns the keys of any skill capes awarded (level 99 reached for the first time).
     */
    suspend fun applyMultiSkillResults(
        xpPerSkill: Map<String, Long>,
        itemsGained: Map<String, Int>,
        coinsGained: Long = 0L,
        efficiencyMultiplier: Float = 1.0f,
        perSkillPetBoostPct: Map<String, Int> = emptyMap(),
    ): List<String> = playerMutex.withLock {
        applyMultiSkillResultsUnlocked(xpPerSkill, itemsGained, coinsGained, efficiencyMultiplier, perSkillPetBoostPct)
    }

    internal suspend fun applyMultiSkillResultsUnlocked(
        xpPerSkill: Map<String, Long>,
        itemsGained: Map<String, Int>,
        coinsGained: Long = 0L,
        efficiencyMultiplier: Float = 1.0f,
        perSkillPetBoostPct: Map<String, Int> = emptyMap(),
    ): List<String> {
        val player    = getOrCreatePlayer()
        val flags: PlayerFlags = json.decodeFromString(player.flags)
        val boostActive = flags.xpBoostExpiresAt > System.currentTimeMillis()
        val boostMult = if (boostActive) 2L else 1L
        val xpBlessingMult = ChurchRepository.xpMultiplier(flags)
        val coinBlessingMult = ChurchRepository.coinMultiplier(flags)
        val scaledItems = if (efficiencyMultiplier == 1.0f) itemsGained
            else itemsGained.mapValues { (_, v) -> (v * efficiencyMultiplier).roundToInt().coerceAtLeast(1) }

        val levels:    MutableMap<String, Int>  = json.decodeFromString(player.skillLevels)
        val xpMap:     MutableMap<String, Long> = json.decodeFromString(player.skillXp)
        val inventory: MutableMap<String, Int>  = json.decodeFromString(player.inventory)

        val awardedCapes = mutableListOf<String>()
        for ((skill, xp) in xpPerSkill) {
            val oldLevel = XpTable.levelForXp(xpMap[skill] ?: 0L)
            val scaledXp = if (efficiencyMultiplier == 1.0f) xp else (xp * efficiencyMultiplier).toLong()
            val petPct = perSkillPetBoostPct[skill] ?: 0
            val withPet = if (petPct > 0) (scaledXp * (1.0 + petPct / 100.0)).toLong() else scaledXp
            val afterBoostBlessing = (withPet * boostMult * xpBlessingMult).toLong()
            val prestigeLevel = flags.skillPrestige[skill] ?: 0
            val finalXp = if (prestigeLevel > 0) (afterBoostBlessing * (1.0 + prestigeLevel * 0.10)).toLong() else afterBoostBlessing
            val newXp = (xpMap[skill] ?: 0L) + finalXp
            xpMap[skill]  = newXp
            levels[skill] = XpTable.levelForXp(newXp)
            if (oldLevel < 99 && levels[skill]!! >= 99) {
                val capeKey = capeKeyForSkill(skill)
                if (capeKey != null && !inventory.containsKey(capeKey)) {
                    inventory[capeKey] = 1
                    awardedCapes += capeKey
                }
            }
        }
        for ((item, qty) in scaledItems) {
            inventory[item] = (inventory[item] ?: 0) + qty
        }

        playerDao.upsert(
            player.copy(
                skillLevels = json.encode<Map<String, Int>>(levels),
                skillXp     = json.encode<Map<String, Long>>(xpMap),
                inventory   = json.encode<Map<String, Int>>(inventory),
                coins       = player.coins + (coinsGained * coinBlessingMult).toLong(),
                flags       = json.encode<PlayerFlags>(flags.plusSeen(scaledItems.keys + awardedCapes)),
            )
        )
        return awardedCapes
    }

    /**
     * Activates or extends the 2× XP boost for [durationMs] × [qty] milliseconds.
     * Deducts [XP_BOOST_COST] × [qty] coins. Returns false if not enough coins.
     */
    suspend fun activateXpBoost(durationMs: Long, qty: Int = 1, costEach: Long = XP_BOOST_COST): Boolean {
        val totalCost = costEach * qty
        val player    = getOrCreatePlayer()
        if (player.coins < totalCost) return false

        val flags: PlayerFlags = json.decodeFromString(player.flags)
        val now        = System.currentTimeMillis()
        val currentEnd = if (flags.xpBoostExpiresAt > now) flags.xpBoostExpiresAt else now
        val newExpiry  = currentEnd + durationMs * qty

        playerDao.upsert(
            player.copy(
                coins = player.coins - totalCost,
                flags = json.encode<PlayerFlags>(flags.copy(xpBoostExpiresAt = newExpiry)),
            )
        )
        buffNotifScheduler.cancelXpBoostExpiry()
        buffNotifScheduler.scheduleXpBoostExpiry(newExpiry)
        return true
    }

    /** Bronze/starter fallback item granted to a slot if prestige invalidates its gear and nothing else in inventory qualifies. */
    private val prestigeStarterGearForSlot = mapOf(
        EquipSlot.WEAPON_ATK    to "bronze_sword",
        EquipSlot.WEAPON_STR    to "bronze_scimitar",
        EquipSlot.WEAPON_RANGED to "wooden_bow",
        EquipSlot.WEAPON_MAGIC  to "basic_staff",
        EquipSlot.HEAD          to "bronze_full_helmet",
        EquipSlot.BODY          to "bronze_platebody",
        EquipSlot.LEGS          to "bronze_platelegs",
        EquipSlot.SHIELD        to "bronze_kiteshield",
        EquipSlot.BOOTS         to "bronze_boots",
        EquipSlot.PICKAXE       to "bronze_pickaxe",
        EquipSlot.AXE           to "bronze_axe",
        EquipSlot.FISHING_ROD   to "bronze_fishing_rod",
        EquipSlot.HOE           to "bronze_hoe",
    )

    /**
     * Resets [skillName] back to level 1 and increments its prestige count (max 3).
     * Guard: skill must be level 99 and prestige count must be < 3.
     *
     * Also re-validates every equipped slot against the new (lower) levels: gear that no
     * longer meets its requirements is swapped for the best valid item in inventory, or a
     * bronze/starter fallback, or unequipped if neither is available.
     */
    suspend fun prestigeSkill(skillName: String) = playerMutex.withLock {
        val player = getOrCreatePlayer()
        val levels: MutableMap<String, Int>  = json.decodeFromString(player.skillLevels)
        val xpMap:  MutableMap<String, Long> = json.decodeFromString(player.skillXp)
        val flags: PlayerFlags               = json.decodeFromString(player.flags)

        val currentPrestige = flags.skillPrestige[skillName] ?: 0
        if ((levels[skillName] ?: 1) < 99 || currentPrestige >= 3) return@withLock

        levels[skillName] = 1
        xpMap[skillName]  = 0L
        val newPrestige = flags.skillPrestige.toMutableMap()
        newPrestige[skillName] = currentPrestige + 1

        val inventory: MutableMap<String, Int> = json.decodeFromString(player.inventory)
        val equipped: MutableMap<String, String?> = json.decodeFromString(player.equipped)
        val allEquip = gameData.equipment

        for (slot in EquipSlot.ALL) {
            val currentKey = equipped[slot]
            val currentValid = currentKey == null ||
                (allEquip[currentKey]?.requirements?.all { (skill, lvl) -> (levels[skill] ?: 1) >= lvl } == true)
            if (currentValid) continue

            val style = EquipSlot.combatStyleForSlot(slot)
            val bestFromInv = inventory.keys
                .mapNotNull { k -> allEquip[k]?.let { eq -> k to eq } }
                .filter { (_, eq) -> if (style != null) eq.slot == "weapon" && eq.combatStyle == style else eq.slot == slot }
                .filter { (_, eq) -> eq.requirements.all { (skill, lvl) -> (levels[skill] ?: 1) >= lvl } }
                .maxByOrNull { (_, eq) -> eq.attackBonus + eq.strengthBonus + eq.defenseBonus }
                ?.first

            val starterKey = prestigeStarterGearForSlot[slot]
            when {
                bestFromInv != null -> equipped[slot] = bestFromInv
                starterKey != null && allEquip[starterKey] != null -> {
                    inventory[starterKey] = (inventory[starterKey] ?: 0) + 1
                    equipped[slot] = starterKey
                }
                else -> equipped[slot] = null
            }
        }

        playerDao.upsert(
            player.copy(
                skillLevels = json.encode<Map<String, Int>>(levels),
                skillXp     = json.encode<Map<String, Long>>(xpMap),
                flags       = json.encode<PlayerFlags>(flags.copy(skillPrestige = newPrestige)),
                inventory   = json.encode<Map<String, Int>>(inventory),
                equipped    = json.encode<Map<String, String?>>(equipped),
            )
        )
    }

    companion object {
        const val XP_BOOST_COST = 250_000L
        const val XP_BOOST_DURATION_MS = 48 * 3_600_000L   // 48 hours
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
     * Pre-1.8.6 bug: pet drops went into inventory instead of the pet list because
     * pet keys were absent from pets.json. Moves any matching inventory items into
     * the OwnedPet list and removes them from inventory.
     */
    suspend fun migratePetsFromInventory(petKeys: Set<String>) {
        val player = getOrCreatePlayer()
        val inventory: MutableMap<String, Int> = json.decodeFromString(player.inventory)
        val pets: MutableList<OwnedPet> = json.decodeFromString(player.pets)
        val ownedIds = pets.map { it.id }.toSet()
        val toMigrate = petKeys.filter { it in inventory && it !in ownedIds }
        if (toMigrate.isEmpty()) return
        toMigrate.forEach { key ->
            inventory.remove(key)
            pets.add(OwnedPet(id = key, boostPercent = 0))
        }
        playerDao.upsert(player.copy(
            inventory = json.encode<Map<String, Int>>(inventory),
            pets      = json.encode<List<OwnedPet>>(pets),
        ))
    }

    /**
     * Adds [petId] to the player's pet list if not already owned.
     * Returns true if the pet was newly added, false if already owned.
     */
    suspend fun addPetIfNew(petId: String, boostPercent: Int = 0): Boolean = playerMutex.withLock {
        addPetIfNewUnlocked(petId, boostPercent)
    }

    internal suspend fun addPetIfNewUnlocked(petId: String, boostPercent: Int = 0): Boolean {
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

    /** Returns a JSON string capturing the full player save including quest progress and sessions. */
    suspend fun exportSave(sessions: List<com.fantasyidler.data.model.SkillSessionExport> = emptyList()): String {
        val player = getOrCreatePlayer()
        return json.encode<PlayerExport>(
            PlayerExport(
                skillLevels    = player.skillLevels,
                skillXp        = player.skillXp,
                inventory      = player.inventory,
                equipped       = player.equipped,
                flags          = player.flags,
                pets           = player.pets,
                coins          = player.coins,
                questProgress  = questProgressDao.getAllProgress(),
                farmingPatches = farmingPatchDao.getAllPatches(),
                sessions       = sessions,
                exportedAt     = System.currentTimeMillis(),
            )
        )
    }

    /** Overwrites the current save with data from a previously exported JSON string. Returns the parsed export. */
    suspend fun importSave(jsonString: String): PlayerExport {
        val export = json.decodeFromString<PlayerExport>(stripJsonGarbage(jsonString))
        val player = getOrCreatePlayer()
        playerDao.upsert(
            player.copy(
                skillLevels = export.skillLevels,
                skillXp     = export.skillXp,
                inventory   = export.inventory,
                equipped    = export.equipped,
                flags       = export.flags,
                pets        = export.pets,
                coins       = export.coins,
            )
        )
        questProgressDao.deleteAll()
        export.questProgress.forEach { questProgressDao.upsert(it) }
        farmingPatchDao.clearAll()
        export.farmingPatches.forEach { farmingPatchDao.upsert(it) }
        return export
    }

    // Finds the end of the root JSON object and drops any trailing garbage.
    // Guards against files that were written twice without truncation.
    private fun stripJsonGarbage(s: String): String {
        var depth = 0
        var inString = false
        var escape = false
        for (i in s.indices) {
            val c = s[i]
            if (escape) { escape = false; continue }
            if (c == '\\' && inString) { escape = true; continue }
            if (c == '"') { inString = !inString; continue }
            if (inString) continue
            when (c) {
                '{' -> depth++
                '}' -> { depth--; if (depth == 0) return s.substring(0, i + 1) }
            }
        }
        return s
    }

    suspend fun resetProgression() {
        playerDao.upsert(createDefaultPlayer())
    }

    // ------------------------------------------------------------------
    // Daily quest helpers
    // ------------------------------------------------------------------

    /** Refresh daily and weekly quests if past 6am, then record progress for a gather session. */
    suspend fun recordDailyGathering(items: Map<String, Int>) {
        var flags = getRefreshedDailyFlags()
        for ((target, amount) in items) {
            flags = dailyQuestRepo.recordProgress(flags, "gather", target, amount)
            flags = weeklyQuestRepo.recordProgress(flags, "gather", target, amount)
        }
        updateFlags(flags)
    }

    /** Refresh daily and weekly quests if past 6am, then record progress for a crafting session. */
    suspend fun recordDailyCrafting(items: Map<String, Int>) {
        var flags = getRefreshedDailyFlags()
        for ((target, amount) in items) {
            flags = dailyQuestRepo.recordProgress(flags, "craft", target, amount)
            flags = weeklyQuestRepo.recordProgress(flags, "craft", target, amount)
        }
        updateFlags(flags)
    }

    /** Refresh daily and weekly quests if past 6am, then record progress for combat kills. */
    suspend fun recordDailyKills(killsByEnemy: Map<String, Int>) {
        var flags = getRefreshedDailyFlags()
        for ((enemy, count) in killsByEnemy) {
            flags = dailyQuestRepo.recordProgress(flags, "kill_enemy", enemy, count)
            flags = weeklyQuestRepo.recordProgress(flags, "kill_enemy", enemy, count)
        }
        if (killsByEnemy.isNotEmpty()) {
            val updated = flags.enemyKills.toMutableMap()
            for ((enemy, count) in killsByEnemy) updated[enemy] = (updated[enemy] ?: 0) + count
            flags = flags.copy(enemyKills = updated)
        }
        updateFlags(flags)
    }

    /** Refresh daily quests if past 6am, then record bones buried. */
    suspend fun recordDailyPrayer(amount: Int) {
        var flags = getRefreshedDailyFlags()
        flags = dailyQuestRepo.recordPrayerProgress(flags, amount)
        flags = weeklyQuestRepo.recordPrayerProgress(flags, amount)
        updateFlags(flags)
    }

    /** Record arbitrary weekly progress (for new weekly quest types). */
    suspend fun recordWeeklyProgress(type: String, target: String, amount: Int) {
        var flags = getRefreshedDailyFlags()
        flags = weeklyQuestRepo.recordProgress(flags, type, target, amount)
        updateFlags(flags)
    }

    /** Returns current flags after refreshing daily and weekly quests if the boundary has passed. */
    suspend fun getRefreshedDailyFlags(): PlayerFlags {
        val player = getOrCreatePlayer()
        var flags: PlayerFlags = json.decodeFromString(player.flags)
        var changed = false
        val skillLevels: Map<String, Int> by lazy { json.decodeFromString(player.skillLevels) }

        if (dailyQuestRepo.shouldRefresh(flags.dailyQuestGeneratedAt)) {
            flags = dailyQuestRepo.refreshFlags(flags, skillLevels)
            changed = true
        }
        
        if (weeklyQuestRepo.shouldRefresh(flags.weeklyQuestGeneratedAt)) {
            flags = weeklyQuestRepo.refreshFlags(flags, skillLevels)
            changed = true
        }

        if (changed) {
            updateFlags(flags)
        }
        return flags
    }

    /**
     * Atomically applies a claimed daily quest reward:
     * - Updates [PlayerFlags] with the new [newFlags] (which includes the quest marked as claimed).
     * - If [reward] is [DailyReward.DwarvenItemReward], adds the item to inventory in the SAME write.
     *
     * This prevents the silent item loss that occurred when [updateFlags] and [addItem] were two
     * separate DB writes — if anything interrupted between them (crash, concurrent write), the
     * item was lost even though the notification had already fired.
     *
     * Returns the item key that was granted, or null for a coins reward (coins are handled by the
     * caller since they don't touch the inventory/flags row).
     */
    suspend fun claimDailyReward(newFlags: PlayerFlags, reward: DailyReward): String? {
        val player = getOrCreatePlayer()
        val flags = newFlags.let { it.plusSeen(
            if (reward is DailyReward.DwarvenItemReward) listOf(reward.itemKey) else emptyList()
        ) }
        val inventory: MutableMap<String, Int> = json.decodeFromString(player.inventory)
        val grantedKey: String? = if (reward is DailyReward.DwarvenItemReward) {
            inventory[reward.itemKey] = (inventory[reward.itemKey] ?: 0) + 1
            reward.itemKey
        } else null

        playerDao.upsert(
            player.copy(
                flags     = json.encode<PlayerFlags>(flags),
                inventory = json.encode<Map<String, Int>>(inventory),
            )
        )
        return grantedKey
    }

    /**
     * Atomically applies a claimed weekly bonus reward:
     * - Updates [PlayerFlags] with [newFlags] (weeklyBonusClaimed = true).
     * - If [reward] is [WeeklyBonusReward.DivineItemReward], adds the item to inventory in the SAME write.
     *
     * Mirrors [claimDailyReward]: prevents the silent divine-item loss that occurred when
     * [updateFlags] and [addItem] were two separate DB writes in [claimWeeklyBonus].
     *
     * Returns the item key that was granted, or null for a coins reward (coins are handled by the
     * caller since they don't touch the inventory/flags row).
     */
    suspend fun claimWeeklyBonusReward(newFlags: PlayerFlags, reward: WeeklyBonusReward): String? {
        val player = getOrCreatePlayer()
        val flags = newFlags.let { it.plusSeen(
            if (reward is WeeklyBonusReward.DivineItemReward) listOf(reward.itemKey) else emptyList()
        ) }
        val inventory: MutableMap<String, Int> = json.decodeFromString(player.inventory)
        val grantedKey: String? = if (reward is WeeklyBonusReward.DivineItemReward) {
            inventory[reward.itemKey] = (inventory[reward.itemKey] ?: 0) + 1
            reward.itemKey
        } else null

        playerDao.upsert(
            player.copy(
                flags     = json.encode<PlayerFlags>(flags),
                inventory = json.encode<Map<String, Int>>(inventory),
            )
        )
        return grantedKey
    }

    /** Adds [qty] of [itemKey] to inventory. */
    suspend fun addItem(itemKey: String, amount: Int = 1) = playerMutex.withLock { addItemUnlocked(itemKey, amount) }

    internal suspend fun addItemUnlocked(itemKey: String, amount: Int = 1) {
        if (amount <= 0) return
        val player = getOrCreatePlayer()
        val inventory: MutableMap<String, Int> = json.decodeFromString(player.inventory)
        inventory[itemKey] = ((inventory[itemKey] ?: 0).toLong() + amount).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val flags: PlayerFlags = json.decodeFromString(player.flags)
        playerDao.upsert(player.copy(
            inventory = json.encode<Map<String, Int>>(inventory),
            flags     = json.encode<PlayerFlags>(flags.plusSeen(listOf(itemKey))),
        ))
    }

    /** Adds multiple items to inventory in a single DB write. */
    suspend fun addItems(items: Map<String, Int>) = playerMutex.withLock {
        if (items.isEmpty()) return@withLock
        val player = getOrCreatePlayer()
        val inventory: MutableMap<String, Int> = json.decodeFromString(player.inventory)
        for ((key, qty) in items) {
            require(qty >= 0) { "Cannot add negative quantity" }
            inventory[key] = ((inventory[key] ?: 0).toLong() + qty).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }
        val flags: PlayerFlags = json.decodeFromString(player.flags)
        playerDao.upsert(player.copy(
            inventory = json.encode<Map<String, Int>>(inventory),
            flags     = json.encode<PlayerFlags>(flags.plusSeen(items.keys)),
        ))
    }


    /** Seeds seenItemKeys from current inventory + equipped; always ensures starting items are present. */
    suspend fun migrateSeenItems() {
        val player = getOrCreatePlayer()
        val flags: PlayerFlags = json.decodeFromString(player.flags)
        val startingItems = setOf("bronze_pickaxe", "bronze_axe", "bronze_fishing_rod", "bronze_boots")
        if (flags.seenItemKeys.isNotEmpty()) {
            val missing = startingItems - flags.seenItemKeys
            if (missing.isNotEmpty()) {
                playerDao.upsert(player.copy(
                    flags = json.encode<PlayerFlags>(flags.copy(seenItemKeys = flags.seenItemKeys + missing))
                ))
            }
            return
        }
        val inventory: Map<String, Int> = json.decodeFromString(player.inventory)
        val equipped: Map<String, String?> = json.decodeFromString(player.equipped)
        val allCurrentKeys = inventory.keys + equipped.values.filterNotNull() + startingItems
        playerDao.upsert(player.copy(
            flags = json.encode<PlayerFlags>(flags.copy(seenItemKeys = allCurrentKeys.toSet()))
        ))
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
                EquipSlot.BOOTS       to "bronze_boots",
            )
        val defaultInventory: Map<String, Int> = mapOf(
            "bronze_pickaxe"     to 1,
            "bronze_axe"         to 1,
            "bronze_fishing_rod" to 1,
            "bronze_boots"       to 1,
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
