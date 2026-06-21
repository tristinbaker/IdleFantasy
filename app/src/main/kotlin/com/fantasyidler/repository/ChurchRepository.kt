package com.fantasyidler.repository

import com.fantasyidler.data.json.BlessingData
import com.fantasyidler.data.json.BlessingType
import com.fantasyidler.data.model.PlayerFlags
import javax.inject.Inject
import javax.inject.Singleton
import javax.inject.Provider

sealed class BlessingActivateResult {
    object Success : BlessingActivateResult()
    object AlreadyActive : BlessingActivateResult()
    data class NotEnoughBones(val needed: Int) : BlessingActivateResult()
}

@Singleton
class ChurchRepository @Inject constructor(
    private val playerRepo: PlayerRepository,
    private val townRepoProvider: Provider<TownRepository>,
    private val buffNotifScheduler: BuffNotificationScheduler,
) {
    companion object {
        const val BLESSING_DURATION_MS = 24L * 3_600_000L

        val ALL_BLESSINGS: List<BlessingData> = listOf(
            BlessingData("blessed_focus",      1,  BlessingType.XP,      1.05f),
            BlessingData("stone_skin",         1,  BlessingType.DEFENSE, 2f),
            BlessingData("blessed_focus_ii",   10, BlessingType.XP,      1.10f),
            BlessingData("stone_skin_ii",      10, BlessingType.DEFENSE, 4f),
            BlessingData("blessed_focus_iii",  20, BlessingType.XP,      1.15f),
            BlessingData("stone_skin_iii",     20, BlessingType.DEFENSE, 6f),
            BlessingData("tithe_blessing",     30, BlessingType.XP,      1.18f),
            BlessingData("stone_skin_iv",      30, BlessingType.DEFENSE, 9f),
            BlessingData("fortune_i",          30, BlessingType.COINS,   0.08f),
            BlessingData("tithe_blessing_ii",  40, BlessingType.XP,      1.20f),
            BlessingData("iron_ward",          40, BlessingType.DEFENSE, 12f),
            BlessingData("fortune_ii",         40, BlessingType.COINS,   0.10f),
            BlessingData("tithe_blessing_iii", 50, BlessingType.XP,      1.25f),
            BlessingData("iron_ward_ii",       50, BlessingType.DEFENSE, 15f),
            BlessingData("fortune_iii",        50, BlessingType.COINS,   0.13f),
            BlessingData("divine_focus",       60, BlessingType.XP,      1.28f),
            BlessingData("diamond_skin",       60, BlessingType.DEFENSE, 18f),
            BlessingData("fortune_iv",         60, BlessingType.COINS,   0.15f),
            BlessingData("divine_focus_ii",    70, BlessingType.XP,      1.32f),
            BlessingData("diamond_skin_ii",    70, BlessingType.DEFENSE, 22f),
            BlessingData("fortune_v",          70, BlessingType.COINS,   0.18f),
            BlessingData("divine_grace",       80, BlessingType.XP,      1.37f),
            BlessingData("holy_shield",        80, BlessingType.DEFENSE, 26f),
            BlessingData("abundance",          80, BlessingType.COINS,   0.20f),
            BlessingData("divine_grace_ii",    90, BlessingType.XP,      1.43f),
            BlessingData("holy_shield_ii",     90, BlessingType.DEFENSE, 30f),
            BlessingData("abundance_ii",       90, BlessingType.COINS,   0.23f),
            BlessingData("sacred_grace",       99, BlessingType.XP,      1.50f),
            BlessingData("aegis",              99, BlessingType.DEFENSE, 35f),
            BlessingData("abundance_iii",      99, BlessingType.COINS,   0.25f),
        )

        private val BY_KEY = ALL_BLESSINGS.associateBy { it.key }

        fun activeBlessing(flags: PlayerFlags): BlessingData? {
            if (flags.activeBlessingKey.isEmpty()) return null
            if (flags.activeBlessingExpiresAt <= System.currentTimeMillis()) return null
            return BY_KEY[flags.activeBlessingKey]
        }

        fun xpMultiplier(flags: PlayerFlags): Float {
            val b = activeBlessing(flags) ?: return 1f
            return if (b.type == BlessingType.XP) b.magnitude else 1f
        }

        fun defBonus(flags: PlayerFlags): Int {
            val b = activeBlessing(flags) ?: return 0
            return if (b.type == BlessingType.DEFENSE) b.magnitude.toInt() else 0
        }

        fun coinMultiplier(flags: PlayerFlags): Float {
            val b = activeBlessing(flags) ?: return 1f
            return if (b.type == BlessingType.COINS) 1f + b.magnitude else 1f
        }

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

        private fun totalBoneXp(inventory: Map<String, Int>): Int =
            BONE_XP.entries.sumOf { (key, xp) -> (inventory[key] ?: 0) * xp }
    }

    fun blessingsForLevel(prayerLevel: Int): List<BlessingData> =
        ALL_BLESSINGS.filter { it.prayerLevelRequired <= prayerLevel }

    suspend fun activateBlessing(key: String): BlessingActivateResult {
        val flags     = playerRepo.getFlags()
        val active    = activeBlessing(flags)
        if (active != null && active.key != key) return BlessingActivateResult.AlreadyActive
        val blessing  = BY_KEY[key] ?: return BlessingActivateResult.AlreadyActive
        val cost      = boneCostFor(blessing)
        val inventory = playerRepo.getInventory()
        if (totalBoneXp(inventory) < cost * BASE_BONE_XP) return BlessingActivateResult.NotEnoughBones(cost)

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
        playerRepo.consumeItems(toConsume)

        val now = System.currentTimeMillis()
        val durationMs = townRepoProvider.get().blessingDurationMs(flags)
        val newExpiresAt = if (active != null && active.key == key) {
            flags.activeBlessingExpiresAt + durationMs
        } else {
            now + durationMs
        }
        playerRepo.updateFlags(
            flags.copy(
                activeBlessingKey       = key,
                activeBlessingExpiresAt = newExpiresAt,
            )
        )
        buffNotifScheduler.scheduleBlessingExpiry(newExpiresAt)
        return BlessingActivateResult.Success
    }

    suspend fun deactivateBlessing() {
        val flags = playerRepo.getFlags()
        playerRepo.updateFlags(
            flags.copy(
                activeBlessingKey       = "",
                activeBlessingExpiresAt = 0L,
            )
        )
        buffNotifScheduler.cancelBlessingExpiry()
    }
}
