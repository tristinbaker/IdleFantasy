package com.fantasyidler.repository

import com.fantasyidler.data.model.PlayerFlags
import javax.inject.Inject
import javax.inject.Singleton

sealed class UpgradeBuildingResult {
    object Success : UpgradeBuildingResult()
    object InsufficientLevel : UpgradeBuildingResult()
    object InsufficientCoins : UpgradeBuildingResult()
    object InsufficientMaterials : UpgradeBuildingResult()
    object AlreadyMaxed : UpgradeBuildingResult()
    object UnknownBuilding : UpgradeBuildingResult()
}

@Singleton
class TownRepository @Inject constructor(
    private val gameData: GameDataRepository,
    private val playerRepo: PlayerRepository,
    private val questRepo: QuestRepository,
) {

    // -------------------------------------------------------------------------
    // Bonus accessors — pure functions, safe to call from any context
    // -------------------------------------------------------------------------

    /** Calculates the multiplier for the worker XP for an individual building. 1.0 = no bonus. */
    fun workerXpMultiplier(building: String, tier: Int): Float {
        val bonuses = gameData.townBuildings[building]?.tiers?.getOrNull(tier - 1)?.bonuses
        return 1.0f + (bonuses?.get("worker_xp")?.toFloat() ?: 0f)
    }

    /** Multiplier applied to worker XP. 1.0 = no bonus. */
    fun workerXpMultiplier(flags: PlayerFlags): Float {
        var multiplier = 1.0f
        flags.townBuildingTiers.forEach { buildingName, tier ->
            multiplier *= workerXpMultiplier(buildingName, tier)
        }
        return multiplier
    }

    /** Factor to multiply guild quest requirement amounts by. */
    fun guildQuestRequirementFactor(building: String, tier: Int): Float {
        val bonuses = gameData.townBuildings[building]?.tiers?.getOrNull(tier - 1)?.bonuses
        return 1.0f - (bonuses?.get("guild_quest_reduction")?.toFloat() ?: 0f)
    }

    /** Factor to multiply guild quest requirement amounts by. */
    fun guildQuestRequirementFactor(flags: PlayerFlags): Float {
        var multiplier = 1.0f
        flags.townBuildingTiers.forEach { buildingName, tier ->
            multiplier *= guildQuestRequirementFactor(buildingName, tier)
        }
        return multiplier
    }

    /** Extra farm plots bonuses */
    fun extraFarmPlots(building: String, tier: Int): Int {
        val bonuses = gameData.townBuildings[building]?.tiers?.getOrNull(tier - 1)?.bonuses ?: return 0
        return bonuses["farm_plots"]?.toInt() ?: 0
    }

    /** Extra farm plots from all builders (+1 per garden tier). */
    fun extraFarmPlots(flags: PlayerFlags): Int {
        var extraPlots = 0
        flags.townBuildingTiers.forEach { buildingName, tier ->
            extraPlots += extraFarmPlots(buildingName, tier)
        }
        return extraPlots
    }

    /** Number of additional carnival minigames. */
    fun extraCarnivalGames(building: String, tier: Int): Int {
        val bonuses = gameData.townBuildings[building]?.tiers?.getOrNull(tier - 1)?.bonuses ?: return 0
        return bonuses["extra_carnival_games"]?.toInt() ?: 0
    }

    /** Number of active carnival minigames (4 base + 1 at fairgrounds T1 + 1 at T2). */
    fun carnivalGameCount(flags: PlayerFlags): Int {
        var carnivalGames = 4
        flags.townBuildingTiers.forEach { buildingName, tier ->
            carnivalGames += extraCarnivalGames(buildingName, tier)
        }
        return carnivalGames.coerceIn(4, 6)
    }

    /** Carnival active game cooldown in ms. */
    fun carnivalCooldownFactor(building: String, tier: Int): Float {
        val bonuses = gameData.townBuildings[building]?.tiers?.getOrNull(tier - 1)?.bonuses ?: return 1.0f
        return bonuses["carnival_cooldown_mult"]?.toFloat() ?: 1.0f
    }

    /** Carnival active game cooldown in ms (10 min base; T1→7.5 min; T3→5 min). */
    fun carnivalCooldownMs(flags: PlayerFlags): Long {
        var multiplier = 1.0f
        flags.townBuildingTiers.forEach { buildingName, tier ->
            multiplier *= carnivalCooldownFactor(buildingName, tier)
        }
        return (multiplier * 600_000).toLong()
    }

    /** Calculates the multiplier for the idle tickets in the fairgrounds */
    fun idleTicketBonusChance(building: String, tier: Int): Float {
        val bonuses = gameData.townBuildings[building]?.tiers?.getOrNull(tier - 1)?.bonuses
        return (bonuses?.get("idle_ticket_bonus_chance")?.toFloat() ?: 0f)
    }

    /** Calculates the multiplier for the idle tickets in the fairgrounds */
    fun idleTicketBonusChance(flags: PlayerFlags): Float {
        var bonusChance = 0.0f
        flags.townBuildingTiers.forEach { buildingName, tier ->
            bonusChance += idleTicketBonusChance(buildingName, tier)
        }
        return bonusChance
    }

    /** Blessing duration in ms */
    fun extraBlessingDuration(building: String, tier: Int): Long {
        val hoursMs = 3_600_000L
        val bonuses = gameData.townBuildings[building]?.tiers?.getOrNull(tier - 1)?.bonuses ?: return 0
        return (bonuses["extra_blessing_hrs"]?.toInt() ?: 0) * hoursMs
    }

    /** Blessing duration in ms based on Church tier. */
    fun blessingDurationMs(flags: PlayerFlags): Long {
        var duration = 24 * 3_600_000L
        flags.townBuildingTiers.forEach { buildingName, tier ->
            duration += extraBlessingDuration(buildingName, tier)
        }
        return duration
    }

    /** Extra action queue slots bonus. */
    fun extraQueueSlots(building: String, tier: Int): Int {
        val bonuses = gameData.townBuildings[building]?.tiers?.getOrNull(tier - 1)?.bonuses ?: return 0
        return bonuses["queue_slots"]?.toInt() ?: 0
    }

    /** Max action queue size (3 base + Queue Master tier). */
    fun maxQueueSize(flags: PlayerFlags): Int {
        var extraSlots = 0
        flags.townBuildingTiers.forEach { buildingName, tier ->
            extraSlots += extraQueueSlots(buildingName, tier)
        }
        return 3 + extraSlots
    }

    // -------------------------------------------------------------------------
    // Upgrade action
    // -------------------------------------------------------------------------

    suspend fun upgradeBuilding(buildingKey: String): UpgradeBuildingResult = playerRepo.withLock {
        val building = gameData.townBuildings[buildingKey]
            ?: return@withLock UpgradeBuildingResult.UnknownBuilding

        val flags = playerRepo.getFlagsUnlocked()
        val currentTier = flags.townBuildingTiers[buildingKey] ?: 0

        if (currentTier >= building.tiers.size) return@withLock UpgradeBuildingResult.AlreadyMaxed

        val tierDef = building.tiers[currentTier]
        val player = playerRepo.getOrCreatePlayer()
        val skillLevels: Map<String, Int> = kotlinx.serialization.json.Json.decodeFromString(player.skillLevels)
        val constructionLevel = skillLevels["construction"] ?: 1

        if (constructionLevel < tierDef.constructionLevelRequired) {
            return@withLock UpgradeBuildingResult.InsufficientLevel
        }

        if (player.coins < tierDef.coinCost) return@withLock UpgradeBuildingResult.InsufficientCoins

        val inventory: Map<String, Int> = kotlinx.serialization.json.Json.decodeFromString(player.inventory)
        for ((item, qty) in tierDef.materials) {
            if ((inventory[item] ?: 0) < qty) return@withLock UpgradeBuildingResult.InsufficientMaterials
        }

        playerRepo.consumeItemsUnlocked(tierDef.materials)
        playerRepo.spendCoinsUnlocked(tierDef.coinCost)

        val newTiers = flags.townBuildingTiers.toMutableMap()
        newTiers[buildingKey] = currentTier + 1
        playerRepo.updateFlagsUnlocked(flags.copy(townBuildingTiers = newTiers))

        questRepo.recordBuildingUpgraded(buildingKey)

        UpgradeBuildingResult.Success
    }
}
