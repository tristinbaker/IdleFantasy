package com.fantasyidler.repository

import com.fantasyidler.data.json.BossData
import com.fantasyidler.data.json.DungeonData
import com.fantasyidler.data.json.SpellData
import com.fantasyidler.data.model.EquipSlot
import com.fantasyidler.data.model.PlayerFlags
import com.fantasyidler.data.model.Skills
import com.fantasyidler.simulator.CombatSimulator
import com.fantasyidler.data.model.SessionFrame
import javax.inject.Inject
import javax.inject.Singleton

data class CombatSetup(
    val weaponSlot: String,
    val spell: SpellData?,
    val potionKey: String?,
    val frames: List<SessionFrame> = emptyList()
)

@Singleton
class AutoCombatService @Inject constructor(
    private val gameData: GameDataRepository
) {
    /**
     * Evaluates all equipped weapons to find the one that results in the most kills / highest survival.
     */
    fun findBestDungeonSetup(
        dungeon: DungeonData,
        levels: Map<String, Int>,
        equipped: Map<String, String?>,
        inventory: Map<String, Int>,
        flags: PlayerFlags,
        prestigeMap: Map<String, Int>,
        petBoostPct: Int,
        agilityLevel: Int
    ): CombatSetup {
        return findBestSetup(
            isBoss = false,
            dungeon = dungeon,
            boss = null,
            levels = levels,
            equipped = equipped,
            inventory = inventory,
            flags = flags,
            prestigeMap = prestigeMap,
            petBoostPct = petBoostPct,
            agilityLevel = agilityLevel
        )
    }

    fun findBestBossSetup(
        boss: BossData,
        levels: Map<String, Int>,
        equipped: Map<String, String?>,
        inventory: Map<String, Int>,
        flags: PlayerFlags,
        prestigeMap: Map<String, Int>,
        petBoostPct: Int,
        agilityLevel: Int
    ): CombatSetup {
        return findBestSetup(
            isBoss = true,
            dungeon = null,
            boss = boss,
            levels = levels,
            equipped = equipped,
            inventory = inventory,
            flags = flags,
            prestigeMap = prestigeMap,
            petBoostPct = petBoostPct,
            agilityLevel = agilityLevel
        )
    }

    private fun findBestSetup(
        isBoss: Boolean,
        dungeon: DungeonData?,
        boss: BossData?,
        levels: Map<String, Int>,
        equipped: Map<String, String?>,
        inventory: Map<String, Int>,
        flags: PlayerFlags,
        prestigeMap: Map<String, Int>,
        petBoostPct: Int,
        agilityLevel: Int
    ): CombatSetup {
        var bestSetup: CombatSetup? = null
        var bestScore = -1L

        val availablePotions = inventory.keys.filter { it in gameData.potionEffects && (inventory[it] ?: 0) > 0 }
        val magicLevel = levels[Skills.MAGIC] ?: 1
        val possibleSpells = gameData.spells.values.filter { it.magicLevelRequired <= magicLevel }

        val weaponSlotsToTry = EquipSlot.WEAPON_SLOTS.filter { equipped[it] != null }.ifEmpty { listOf(EquipSlot.WEAPON_ATK) }

        for (weaponSlot in weaponSlotsToTry) {
            val weapon = equipped[weaponSlot]?.let { gameData.equipment[it] }
            val combatStyle = when (weapon?.combatStyle) {
                "ranged" -> "ranged"
                "magic" -> "magic"
                "strength" -> "strength"
                else -> "attack"
            }

            val spellToUse = if (combatStyle == "magic") {
                possibleSpells.filter {
                    weapon?.infiniteRunes == it.runeType || (inventory[it.runeType] ?: 0) >= it.runeCost
                }.maxByOrNull { it.maxHit }
            } else null

            val potionToUse = availablePotions.firstOrNull { potion ->
                val effects = gameData.potionEffects[potion] ?: emptyMap()
                effects.containsKey(combatStyle) || effects.containsKey("defense")
            }

            val potionBonuses = potionToUse?.let { gameData.potionEffects[it] } ?: emptyMap()
            val totalAttackBonus = EquipSlot.ARMOR_SLOTS.sumOf { slot ->
                val eq = gameData.equipment[equipped[slot]] ?: return@sumOf 0
                eq.attackBonus + when (combatStyle) {
                    "ranged" -> eq.rangedAttackBonus ?: 0
                    "magic" -> eq.magicAttackBonus ?: 0
                    else -> 0
                }
            } + (weapon?.attackBonus ?: 0) + when (combatStyle) {
                "ranged" -> weapon?.rangedAttackBonus ?: 0
                "magic" -> weapon?.magicAttackBonus ?: 0
                else -> 0
            }

            val totalStrengthBonus = EquipSlot.ARMOR_SLOTS.sumOf { gameData.equipment[equipped[it]]?.strengthBonus ?: 0 } + (weapon?.strengthBonus ?: 0)
            val totalDefenseBonus = EquipSlot.ARMOR_SLOTS.sumOf { gameData.equipment[equipped[it]]?.defenseBonus ?: 0 } + (weapon?.defenseBonus ?: 0)

            val totalRangedStrBonus = if (combatStyle == "ranged") {
                EquipSlot.ARMOR_SLOTS.sumOf { gameData.equipment[equipped[it]]?.rangedStrengthBonus ?: 0 } + (weapon?.rangedStrengthBonus ?: 0)
            } else 0

            val totalMagicDmgBonus = if (combatStyle == "magic") {
                EquipSlot.ARMOR_SLOTS.sumOf { gameData.equipment[equipped[it]]?.magicDamageBonus ?: 0 } + (weapon?.magicDamageBonus ?: 0)
            } else 0

            val bestArrow = listOf("runite_arrow", "adamantite_arrow", "mithril_arrow", "steel_arrow", "iron_arrow", "bronze_arrow")
                .firstOrNull { (inventory[it] ?: 0) > 0 }
            val arrowStrengthBonus = bestArrow?.let { arrowKey ->
                when (arrowKey) {
                    "bronze_arrow" -> 0; "iron_arrow" -> 2; "steel_arrow" -> 4; "mithril_arrow" -> 6; "adamantite_arrow" -> 8; "runite_arrow" -> 10; else -> 0
                }
            } ?: 0

            val equippedFoodKeys = flags.equippedFood.keys
            val availableFood = inventory.filterKeys { it in equippedFoodKeys }

            val score: Long
            val generatedFrames: List<SessionFrame>
            if (isBoss && boss != null) {
                generatedFrames = CombatSimulator.simulateBoss(
                    boss = boss,
                    bossKey = boss.id,
                    playerAttack = (levels[Skills.ATTACK] ?: 1) + (potionBonuses["attack"] ?: 0) + (prestigeMap[Skills.ATTACK] ?: 0) * 5,
                    playerStrength = (levels[Skills.STRENGTH] ?: 1) + (potionBonuses["strength"] ?: 0) + (prestigeMap[Skills.STRENGTH] ?: 0) * 5,
                    playerDefence = (levels[Skills.DEFENSE] ?: 1) + totalDefenseBonus + (potionBonuses["defense"] ?: 0) + (prestigeMap[Skills.DEFENSE] ?: 0) * 5,
                    playerHp = (levels[Skills.HITPOINTS] ?: 1) + (prestigeMap[Skills.HITPOINTS] ?: 0) * 5,
                    weaponAttackBonus = totalAttackBonus,
                    weaponStrBonus = totalStrengthBonus,
                    combatStyle = combatStyle,
                    playerRanged = (levels[Skills.RANGED] ?: 1) + (potionBonuses["ranged"] ?: 0) + (prestigeMap[Skills.RANGED] ?: 0) * 5,
                    playerMagic = (levels[Skills.MAGIC] ?: 1) + (potionBonuses["magic"] ?: 0) + (prestigeMap[Skills.MAGIC] ?: 0) * 5,
                    arrowStrengthBonus = arrowStrengthBonus + totalRangedStrBonus,
                    spellMaxHit = (spellToUse?.maxHit ?: 0) + totalMagicDmgBonus,
                    availableArrows = bestArrow?.let { mapOf(it to (inventory[it] ?: 0)) } ?: emptyMap(),
                    equippedFood = availableFood,
                    foodHealValues = gameData.foodHealValues,
                    blessingDefBonus = 0, // Simplified for AI scoring
                    runeKey = spellToUse?.runeType,
                    runeCostPerAttack = spellToUse?.runeCost ?: 1
                )
                val died = generatedFrames.any { it.died }
                score = if (died) generatedFrames.size.toLong() else (100000L - generatedFrames.size.toLong())
            } else if (dungeon != null) {
                val result = CombatSimulator.simulateDungeon(
                    dungeon = dungeon,
                    enemies = gameData.enemies,
                    playerAttack = (levels[Skills.ATTACK] ?: 1) + (prestigeMap[Skills.ATTACK] ?: 0) * 5,
                    playerStrength = (levels[Skills.STRENGTH] ?: 1) + (prestigeMap[Skills.STRENGTH] ?: 0) * 5,
                    playerDefence = (levels[Skills.DEFENSE] ?: 1) + totalDefenseBonus + (prestigeMap[Skills.DEFENSE] ?: 0) * 5,
                    playerHp = (levels[Skills.HITPOINTS] ?: 1) + (prestigeMap[Skills.HITPOINTS] ?: 0) * 5,
                    blessingDefBonus = 0,
                    weaponAttackBonus = totalAttackBonus,
                    weaponStrengthBonus = totalStrengthBonus,
                    combatStyle = combatStyle,
                    playerRanged = (levels[Skills.RANGED] ?: 1) + (prestigeMap[Skills.RANGED] ?: 0) * 5,
                    playerMagic = (levels[Skills.MAGIC] ?: 1) + (prestigeMap[Skills.MAGIC] ?: 0) * 5,
                    arrowStrengthBonus = arrowStrengthBonus + totalRangedStrBonus,
                    spellMaxHit = (spellToUse?.maxHit ?: 0) + totalMagicDmgBonus,
                    agilityLevel = agilityLevel,
                    petBoostPct = petBoostPct,
                    equippedFood = availableFood,
                    foodHealValues = gameData.foodHealValues,
                    potionBonuses = potionBonuses,
                    availableArrows = bestArrow?.let { mapOf(it to (inventory[it] ?: 0)) } ?: emptyMap(),
                    runeKey = spellToUse?.runeType,
                    runeCostPerAttack = spellToUse?.runeCost ?: 1
                )
                generatedFrames = result.frames
                val totalKills = result.frames.sumOf { it.kills }
                val died = result.frames.any { it.died }
                score = if (died) totalKills.toLong() else 100000L + totalKills.toLong()
            } else {
                score = 0L
                generatedFrames = emptyList()
            }

            if (bestSetup == null || score > bestScore) {
                bestScore = score
                bestSetup = CombatSetup(weaponSlot, spellToUse, potionToUse, generatedFrames)
            }
        }

        return bestSetup ?: CombatSetup(EquipSlot.WEAPON_ATK, null, null)
    }
}
