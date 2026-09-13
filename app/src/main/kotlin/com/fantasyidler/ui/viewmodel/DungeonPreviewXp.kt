package com.fantasyidler.ui.viewmodel

import com.fantasyidler.data.json.SpellData
import com.fantasyidler.data.model.EquipSlot
import com.fantasyidler.data.model.OwnedPet
import com.fantasyidler.data.model.PlayerFlags
import com.fantasyidler.data.model.Skills
import com.fantasyidler.repository.BoostRepository
import com.fantasyidler.repository.ChurchRepository
import com.fantasyidler.repository.GameDataRepository
import com.fantasyidler.repository.TownRepository
import com.fantasyidler.repository.blessingPrayerCapeMult
import com.fantasyidler.simulator.CombatSimulator
import com.fantasyidler.simulator.HeirloomStats
import kotlinx.serialization.json.Json

internal val PREVIEW_ARROW_TIERS = listOf(
    "runite_arrow", "adamantite_arrow", "mithril_arrow",
    "steel_arrow", "iron_arrow", "bronze_arrow",
)

internal val PREVIEW_ARROW_STRENGTH_BONUS = mapOf(
    "bronze_arrow"     to 7,
    "iron_arrow"       to 10,
    "steel_arrow"      to 16,
    "mithril_arrow"    to 22,
    "adamantite_arrow" to 31,
    "runite_arrow"     to 49,
)

/**
 * Pre-simulates one dungeon run to estimate the XP a queued action will pay out.
 * Shared by the Combat and Slayer queue paths so both show the same estimate (issue #1558).
 */
internal fun estimateDungeonPreviewXp(
    gameData: GameDataRepository,
    boostRepo: BoostRepository,
    townRepo: TownRepository,
    churchRepo: ChurchRepository,
    json: Json,
    dungeonKey: String,
    weaponSlot: String,
    equipped: Map<String, String?>,
    inventory: Map<String, Int>,
    levels: Map<String, Int>,
    flags: PlayerFlags,
    selectedSpell: SpellData?,
    potionKey: String?,
    petsJson: String,
): Long {
    val equipMap = HeirloomStats.resolveAll(gameData.equipment, levels, flags.heirloomXp)
    val dungeon = gameData.dungeons[dungeonKey] ?: return 0L
    val weapon  = equipped[weaponSlot]?.let { equipMap[it] }
    val combatStyle = when (weapon?.combatStyle) {
        "ranged"   -> "ranged"
        "magic"    -> "magic"
        "strength" -> "strength"
        else       -> "attack"
    }
    if (combatStyle == "magic" && selectedSpell == null) return 0L

    val totalAttackBonus = EquipSlot.ARMOR_SLOTS.sumOf { slot ->
        val eq = equipMap[equipped[slot]] ?: return@sumOf 0
        eq.attackBonus + when (combatStyle) {
            "ranged" -> eq.rangedAttackBonus ?: 0
            "magic"  -> eq.magicAttackBonus  ?: 0
            else     -> 0
        }
    } + (weapon?.attackBonus ?: 0) + when (combatStyle) {
        "ranged" -> weapon?.rangedAttackBonus ?: 0
        "magic"  -> weapon?.magicAttackBonus  ?: 0
        else     -> 0
    }
    val totalStrengthBonus = EquipSlot.ARMOR_SLOTS.sumOf { equipMap[equipped[it]]?.strengthBonus ?: 0 } + (weapon?.strengthBonus ?: 0)
    val totalDefenseBonus  = EquipSlot.ARMOR_SLOTS.sumOf { equipMap[equipped[it]]?.defenseBonus  ?: 0 } + (weapon?.defenseBonus  ?: 0)
    val totalRangedStrBonus = if (combatStyle == "ranged")
        EquipSlot.ARMOR_SLOTS.sumOf { equipMap[equipped[it]]?.rangedStrengthBonus ?: 0 } + (weapon?.rangedStrengthBonus ?: 0)
    else 0
    val totalMagicDmgBonus = if (combatStyle == "magic")
        EquipSlot.ARMOR_SLOTS.sumOf { equipMap[equipped[it]]?.magicDamageBonus ?: 0 } + (weapon?.magicDamageBonus ?: 0)
    else 0

    val potionBonuses = potionKey?.let { gameData.potionEffects[it] } ?: emptyMap()
    val staffCoversRune  = combatStyle == "magic" && selectedSpell != null && (weapon?.infiniteRunes == "all" || weapon?.infiniteRunes == selectedSpell.runeType)
    val simulatorRuneKey = if (combatStyle == "magic" && selectedSpell != null && !staffCoversRune) selectedSpell.runeType else null

    val petBoostPct = if (flags.ironman) 0 else try {
        json.decodeFromString<List<OwnedPet>>(petsJson).sumOf { pet ->
            val pd = gameData.pets[pet.id]
            if (pd != null && (pd.boostedSkill == "combat" || pd.boostedSkill == "all")) pd.boostPercent else 0
        }
    } catch (_: Exception) { 0 }

    val result = CombatSimulator.simulateDungeon(
        dungeon             = dungeon,
        enemies             = gameData.enemies,
        playerAttack        = (levels[Skills.ATTACK]    ?: 1) + boostRepo.combatStatBonus(Skills.ATTACK, flags, levels[Skills.ATTACK] ?: 1),
        playerStrength      = (levels[Skills.STRENGTH]  ?: 1) + boostRepo.combatStatBonus(Skills.STRENGTH, flags, levels[Skills.STRENGTH] ?: 1),
        playerDefence       = (levels[Skills.DEFENSE]   ?: 1) + totalDefenseBonus + boostRepo.combatStatBonus(Skills.DEFENSE, flags, levels[Skills.DEFENSE] ?: 1),
        blessingDefBonus    = churchRepo.defBonus(flags, blessingPrayerCapeMult(flags, equipped, inventory.keys, gameData)),
        playerHp            = (levels[Skills.HITPOINTS] ?: 1) + boostRepo.combatStatBonus(Skills.HITPOINTS, flags, levels[Skills.HITPOINTS] ?: 1) + flags.towerHpBonus,
        weaponAttackBonus   = totalAttackBonus,
        weaponStrengthBonus = totalStrengthBonus,
        combatStyle         = combatStyle,
        playerRanged        = (levels[Skills.RANGED]    ?: 1) + boostRepo.combatStatBonus(Skills.RANGED, flags, levels[Skills.RANGED] ?: 1),
        playerMagic         = (levels[Skills.MAGIC]     ?: 1) + boostRepo.combatStatBonus(Skills.MAGIC, flags, levels[Skills.MAGIC] ?: 1),
        rangedGearStrengthBonus = totalRangedStrBonus,
        spellMaxHit         = (selectedSpell?.maxHit ?: 0) + totalMagicDmgBonus,
        agilityLevel        = levels[Skills.AGILITY]   ?: 1,
        floorReductionMin     = boostRepo.sessionFloorReductionMin(flags),
        petBoostPct         = petBoostPct,
        equippedFood        = flags.equippedFood.keys.associateWith { Int.MAX_VALUE },
        foodHealValues      = gameData.foodHealValues,
        potionBonuses       = potionBonuses,
        availableArrows     = PREVIEW_ARROW_TIERS.associateWith { Int.MAX_VALUE },
        arrowStrengthBonuses = PREVIEW_ARROW_STRENGTH_BONUS,
        runeKey             = simulatorRuneKey,
        runeCostPerAttack   = selectedSpell?.runeCost ?: 1,
        availableRunes      = Int.MAX_VALUE,
        attackSpeedSec      = weapon?.attackSpeed ?: CombatSimulator.BASE_ATTACK_SPEED_SEC,
        eatThresholdPct     = flags.foodEatThresholdPct,
        foodEatOrder        = flags.foodEatOrder,
        chronosMultiplier   = townRepo.playerSessionDurationMultiplier(flags),
        doubleHitChance     = boostRepo.doubleHitChance(flags),
        secondChance        = boostRepo.secondChanceActive(flags),
    )
    return result.frames.sumOf { it.xpGain.toLong() }
}
