package com.fantasyidler.repository

import com.fantasyidler.data.json.BlessingData
import com.fantasyidler.data.json.BlessingType
import com.fantasyidler.data.model.PlayerFlags
import com.fantasyidler.data.model.Skills
import javax.inject.Inject
import javax.inject.Singleton
import javax.inject.Provider
import kotlinx.serialization.json.Json

sealed class BlessingActivateResult {
    object Success : BlessingActivateResult()
    object AlreadyActive : BlessingActivateResult()
    data class NotEnoughBones(val needed: Int) : BlessingActivateResult()
    data class LevelTooLow(val requiredLevel: Int) : BlessingActivateResult()
    /** Ironman characters may only activate defensive blessings. */
    object IronmanBlocked : BlessingActivateResult()
}

@Singleton
class ChurchRepository @Inject constructor(
    private val playerRepo: PlayerRepository,
    private val townRepoProvider: Provider<TownRepository>,
    private val buffNotifScheduler: BuffNotificationScheduler,
    private val boostRepoProvider: Provider<BoostRepository>,
    private val gameData: GameDataRepository,
) {
    /** Bone cost after the gnome Trickster's Favor prestige discount. */
    fun discountedBoneCost(blessing: BlessingData, flags: PlayerFlags): Int =
        discountedBoneCost(blessing, boostRepoProvider.get().blessingCostMultiplier(flags))

    fun activeBlessing(flags: PlayerFlags): BlessingData? {
        if (flags.activeBlessingKey.isEmpty()) return null
        if (flags.activeBlessingExpiresAt <= System.currentTimeMillis()) return null
        return gameData.blessings.firstOrNull { it.key == flags.activeBlessingKey }
    }

    fun xpMultiplier(flags: PlayerFlags, prayerCapeMult: Float): Float {
        val b = activeBlessing(flags) ?: return 1f
        return if (b.type == BlessingType.XP) effectiveMagnitude(b, prayerCapeMult) else 1f
    }

    fun defBonus(flags: PlayerFlags, prayerCapeMult: Float): Int {
        val b = activeBlessing(flags) ?: return 0
        return if (b.type == BlessingType.DEFENSE) effectiveMagnitude(b, prayerCapeMult).toInt() else 0
    }

    fun coinMultiplier(flags: PlayerFlags, prayerCapeMult: Float): Float {
        val b = activeBlessing(flags) ?: return 1f
        return if (b.type == BlessingType.COINS) 1f + effectiveMagnitude(b, prayerCapeMult) else 1f
    }

    companion object {
        /**
         * Blessing strength with the prayer cape's multiplier folded in (issue #1491). The
         * cape scales the blessing's BONUS: for XP the magnitude is a full multiplier (1.5x),
         * so only the part above 1 grows; DEFENSE/COINS magnitudes are already pure bonuses.
         */
        fun effectiveMagnitude(b: BlessingData, prayerCapeMult: Float): Float = when (b.type) {
            BlessingType.XP -> 1f + (b.magnitude - 1f) * prayerCapeMult
            BlessingType.DEFENSE, BlessingType.COINS -> b.magnitude * prayerCapeMult
        }

        /** Pure variant for UI display; [costMult] from BoostRepository.blessingCostMultiplier. */
        fun discountedBoneCost(blessing: BlessingData, costMult: Double): Int =
            (boneCostFor(blessing) * costMult + 0.5).toInt().coerceAtLeast(1)

        fun boneCostFor(blessing: BlessingData): Int = when {
            blessing.prayerLevelRequired >= 99 -> 300
            blessing.prayerLevelRequired >= 90 -> 265
            blessing.prayerLevelRequired >= 80 -> 230
            blessing.prayerLevelRequired >= 70 -> 185
            blessing.prayerLevelRequired >= 60 -> 145
            blessing.prayerLevelRequired >= 50 -> 110
            blessing.prayerLevelRequired >= 40 -> 80
            blessing.prayerLevelRequired >= 30 -> 55
            blessing.prayerLevelRequired >= 20 -> 35
            blessing.prayerLevelRequired >= 10 -> 20
            else                               -> 10
        }

        // xp_per_bone values for each accepted bone type
        private val BONE_XP = mapOf(
            "dragon_bone" to 80,
            "giant_bones" to 40,
            "big_bones"   to 20,
            "bones"       to 10,
        )
        private val BONE_TYPES_ORDERED = listOf("bones", "big_bones", "giant_bones", "dragon_bone")
        private const val BASE_BONE_XP = 10  // xp_per_bone for regular bones

        /** Total bone budget expressed in regular-bone equivalents (floor division). */
        fun totalBoneEquivalent(inventory: Map<String, Int>): Int =
            BONE_XP.entries.sumOf { (key, xp) -> (inventory[key] ?: 0) * xp } / BASE_BONE_XP

        /** Raw count of bone items on hand, unweighted by tier. */
        fun totalBoneCount(inventory: Map<String, Int>): Int =
            BONE_TYPES_ORDERED.sumOf { inventory[it] ?: 0 }

        private fun totalBoneXp(inventory: Map<String, Int>): Int =
            BONE_XP.entries.sumOf { (key, xp) -> (inventory[key] ?: 0) * xp }
    }

    fun blessingsForLevel(prayerLevel: Int): List<BlessingData> =
        gameData.blessings.filter { it.prayerLevelRequired <= prayerLevel }

    suspend fun activateBlessing(key: String): BlessingActivateResult = playerRepo.withLock {
        val flags     = playerRepo.getFlagsUnlocked()
        val active    = activeBlessing(flags)
        if (active != null && active.key != key) return@withLock BlessingActivateResult.AlreadyActive
        val blessing  = gameData.blessings.firstOrNull { it.key == key } ?: return@withLock BlessingActivateResult.AlreadyActive
        if (flags.ironman && blessing.type != BlessingType.DEFENSE) {
            return@withLock BlessingActivateResult.IronmanBlocked
        }
        val player    = playerRepo.getOrCreatePlayer()
        val levels: Map<String, Int> = Json.decodeFromString(player.skillLevels)
        val prayerLevel = levels[Skills.PRAYER] ?: 1
        if (prayerLevel < blessing.prayerLevelRequired) {
            return@withLock BlessingActivateResult.LevelTooLow(blessing.prayerLevelRequired)
        }
        val cost      = discountedBoneCost(blessing, flags)
        val inventory: Map<String, Int> = Json.decodeFromString(player.inventory)
        if (totalBoneXp(inventory) < cost * BASE_BONE_XP) return@withLock BlessingActivateResult.NotEnoughBones(cost)

        // Consume bone types greedily from most to least valuable
        val toConsume = mutableMapOf<String, Int>()
        var remainingXp = cost * BASE_BONE_XP
        for (boneKey in BONE_TYPES_ORDERED) {
            if (remainingXp <= 0) break
            val xp   = BONE_XP[boneKey] ?: continue
            val have = inventory[boneKey] ?: 0
            if (have == 0) continue
            val needed  = (remainingXp + xp - 1) / xp
            val consume = minOf(have, needed)
            toConsume[boneKey] = consume
            remainingXp -= consume * xp
        }
        playerRepo.consumeItemsUnlocked(toConsume)

        val now = System.currentTimeMillis()
        val durationMs = (townRepoProvider.get().blessingDurationMs(flags) *
            boostRepoProvider.get().blessingDurationMultiplier(flags)).toLong()
        val newExpiresAt = if (active != null && active.key == key) {
            flags.activeBlessingExpiresAt + durationMs
        } else {
            now + durationMs
        }
        playerRepo.updateFlagsUnlocked(
            flags.copy(
                activeBlessingKey       = key,
                activeBlessingExpiresAt = newExpiresAt,
            )
        )
        buffNotifScheduler.scheduleBlessingExpiry(newExpiresAt)
        BlessingActivateResult.Success
    }

    suspend fun deactivateBlessing() {
        playerRepo.updateFlagsAtomically { flags ->
            flags.copy(
                activeBlessingKey       = "",
                activeBlessingExpiresAt = 0L,
            )
        }
        buffNotifScheduler.cancelBlessingExpiry()
    }
}
