package com.fantasyidler.repository

import com.fantasyidler.data.model.PlayerFlags
import com.fantasyidler.data.model.TownBuildingDef
import com.fantasyidler.data.model.TownBuildings
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
    private val playerRepo: PlayerRepository,
    private val questRepo: QuestRepository,
) {

    // -------------------------------------------------------------------------
    // Bonus accessors — pure functions, safe to call from any context
    // -------------------------------------------------------------------------

    /** Multiplier applied to worker XP based on Inn tier. 1.0 = no bonus. */
    fun workerXpMultiplier(flags: PlayerFlags): Float {
        val tier = flags.townBuildingTiers["inn"] ?: 0
        return 1.0f + tier * 0.10f
    }

    /** Factor to multiply guild quest requirement amounts by (e.g. 0.9 at tier 1 = 10% fewer needed). */
    fun guildQuestRequirementFactor(flags: PlayerFlags): Float {
        val tier = flags.townBuildingTiers["guild_hall"] ?: 0
        return 1.0f - tier * 0.10f
    }

    /** Extra farm plots from the Garden building (+1 per tier). */
    fun extraFarmPlots(flags: PlayerFlags): Int = flags.townBuildingTiers["garden"] ?: 0

    /** Number of active carnival minigames (4 base + 1 at fairgrounds T1 + 1 at T2). */
    fun carnivalGameCount(flags: PlayerFlags): Int {
        val tier = flags.townBuildingTiers["fairgrounds"] ?: 0
        return 4 + minOf(tier, 2)
    }

    /** Carnival active game cooldown in ms (10 min base; T1→7.5 min; T3→5 min). */
    fun carnivalCooldownMs(flags: PlayerFlags): Long {
        return when (flags.townBuildingTiers["fairgrounds"] ?: 0) {
            1, 2 -> (7.5 * 60_000).toLong()
            3    -> 5L * 60_000L
            else -> 10L * 60_000L
        }
    }

    /** Blessing duration in ms based on Church tier. Tier 0 = 24h, 1 = 30h, 2 = 36h, 3 = 48h. */
    fun blessingDurationMs(flags: PlayerFlags): Long {
        val hoursMs = 3_600_000L
        return when (flags.townBuildingTiers["church"] ?: 0) {
            1    -> 30L * hoursMs
            2    -> 36L * hoursMs
            3    -> 48L * hoursMs
            else -> 24L * hoursMs
        }
    }

    // -------------------------------------------------------------------------
    // Upgrade action
    // -------------------------------------------------------------------------

    suspend fun upgradeBuilding(buildingKey: String): UpgradeBuildingResult = playerRepo.withLock {
        val def: TownBuildingDef = TownBuildings.byKey(buildingKey)
            ?: return@withLock UpgradeBuildingResult.UnknownBuilding

        val flags = playerRepo.getFlagsUnlocked()
        val currentTier = flags.townBuildingTiers[buildingKey] ?: 0

        if (currentTier >= def.tiers.size) return@withLock UpgradeBuildingResult.AlreadyMaxed

        val tierDef = def.tiers[currentTier]
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
