package com.fantasyidler.repository

import com.fantasyidler.data.json.CookingRecipe
import com.fantasyidler.data.model.EquipSlot
import com.fantasyidler.data.model.PlayerFlags
import com.fantasyidler.data.model.QueuedAction
import com.fantasyidler.data.model.SessionFrame
import com.fantasyidler.data.model.Skills
import com.fantasyidler.simulator.CombatSimulator
import com.fantasyidler.simulator.SkillSimulator
import com.fantasyidler.simulator.ThievingSimulator
import com.fantasyidler.simulator.XpTable
import com.fantasyidler.ui.viewmodel.combatLevelFrom
import com.fantasyidler.util.toolEfficiency
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import javax.inject.Inject
import javax.inject.Singleton

private val GATHERING_SKILLS = setOf(
    Skills.MINING, Skills.WOODCUTTING, Skills.FISHING,
    Skills.AGILITY, Skills.THIEVING, "combat", "boss",
)

/**
 * Starts the next queued action for the hired worker.
 * Mirrors [QueuedSessionStarter] but reads from [PlayerFlags.hiredWorker.sessionQueue]
 * and calls [SessionRepository.startWorkerSession].
 */
@Singleton
class WorkerQueuedSessionStarter @Inject constructor(
    private val playerRepo: PlayerRepository,
    private val sessionRepo: SessionRepository,
    private val gameData: GameDataRepository,
    private val json: Json,
) {
    private val mutex = Mutex()

    suspend fun startNextQueued(slot: Int = 1): Boolean {
        return playerRepo.playerMutex.withLock {
            mutex.withLock {
                val current = sessionRepo.getActiveWorkerSession(slot)
                if (current != null && !current.completed) return@withLock false
                val next = playerRepo.dequeueNextWorkerActionUnlocked(slot) ?: return@withLock false
                try {
                    startQueuedAction(slot, next)
                    true
                } catch (_: Exception) {
                    playerRepo.requeueWorkerActionAtFrontUnlocked(slot, next)
                    false
                }
            }
        }
    }

    private suspend fun startQueuedAction(slot: Int, action: QueuedAction) {
        val player    = playerRepo.getOrCreatePlayer()
        val levels:   Map<String, Int>     = json.decodeFromString(player.skillLevels)
        val xpMap:    Map<String, Long>    = json.decodeFromString(player.skillXp)
        val equipped: Map<String, String?> = json.decodeFromString(player.equipped)
        val inventory: Map<String, Int>    = json.decodeFromString(player.inventory)
        val flags: PlayerFlags             = json.decodeFromString(player.flags)
        val worker = (if (slot == 2) flags.hiredWorker2 else flags.hiredWorker) ?: return
        val tier = worker.tier
        val agilityLevel = levels[Skills.AGILITY] ?: 1
        val equippedCapeData = equipped[EquipSlot.CAPE]?.let { gameData.equipment[it] }
        val attackCapeMult   = resolveCapeMultiplier("attack", equippedCapeData, inventory.keys, flags.townBuildingTiers, flags.skillPrestige, gameData.equipment)
        val strengthCapeMult = resolveCapeMultiplier("strength", equippedCapeData, inventory.keys, flags.townBuildingTiers, flags.skillPrestige, gameData.equipment)
        val defenseCapeMult  = resolveCapeMultiplier("defense", equippedCapeData, inventory.keys, flags.townBuildingTiers, flags.skillPrestige, gameData.equipment)
        val rangedCapeMult   = resolveCapeMultiplier("ranged", equippedCapeData, inventory.keys, flags.townBuildingTiers, flags.skillPrestige, gameData.equipment)
        val magicCapeMult    = resolveCapeMultiplier("magic", equippedCapeData, inventory.keys, flags.townBuildingTiers, flags.skillPrestige, gameData.equipment)
        val prayerCapeMult   = resolveCapeMultiplier("prayer", equippedCapeData, inventory.keys, flags.townBuildingTiers, flags.skillPrestige, gameData.equipment)
        val levelAtStart = when (action.skillName) {
            "boss", "combat" -> combatLevelFrom(levels)
            else -> levels[action.skillName] ?: 1
        }

        val isGathering = action.skillName in GATHERING_SKILLS
        val efficiencyMultiplier = if (isGathering) tier.combinedGatheringMultiplier else 1.0f
        val durationMs = if (isGathering) tier.durationMs
                         else action.estimatedDurationMs.takeIf { it > 0 } ?: tier.durationMs

        when (action.skillName) {
            Skills.MINING -> {
                val oreKey  = action.activityKey
                val oreData = gameData.ores[oreKey] ?: return
                val result  = SkillSimulator.simulateMining(
                    oreKey         = oreKey,
                    oreData        = oreData,
                    gems           = gameData.gems,
                    startXp        = xpMap[Skills.MINING] ?: 0L,
                    agilityLevel   = agilityLevel,
                    petBoostPct    = 0,
                    toolEfficiency = gameData.toolEfficiency(equipped[EquipSlot.PICKAXE], EquipSlot.PICKAXE, oreData.levelRequired),
                    petDropKey     = null,
                    petDropChance  = 0.0,
                )
                startSession(slot, action, result.frames, durationMs, efficiencyMultiplier, levelAtStart)
            }
            Skills.WOODCUTTING -> {
                val treeKey  = action.activityKey
                val treeData = gameData.trees[treeKey] ?: return
                val result   = SkillSimulator.simulateWoodcutting(
                    treeData       = treeData,
                    startXp        = xpMap[Skills.WOODCUTTING] ?: 0L,
                    agilityLevel   = agilityLevel,
                    petBoostPct    = 0,
                    toolEfficiency = gameData.toolEfficiency(equipped[EquipSlot.AXE], EquipSlot.AXE, treeData.levelRequired),
                    petDropKey     = null,
                    petDropChance  = 0.0,
                )
                startSession(slot, action, result.frames, durationMs, efficiencyMultiplier, levelAtStart)
            }
            Skills.FISHING -> {
                val fishKey  = action.activityKey
                val fishData = gameData.fish[fishKey] ?: return
                val result   = SkillSimulator.simulateFishing(
                    fishKey        = fishKey,
                    fishData       = fishData,
                    startXp        = xpMap[Skills.FISHING] ?: 0L,
                    agilityLevel   = agilityLevel,
                    petBoostPct    = 0,
                    rodEfficiency  = gameData.toolEfficiency(equipped[EquipSlot.FISHING_ROD], EquipSlot.FISHING_ROD, fishData.levelRequired),
                    petDropKey     = null,
                    petDropChance  = 0.0,
                )
                startSession(slot, action, result.frames, durationMs, efficiencyMultiplier, levelAtStart)
            }
            Skills.AGILITY -> {
                val courseKey  = action.activityKey
                val courseData = gameData.agilityCourses[courseKey] ?: return
                val result     = SkillSimulator.simulateAgility(
                    courseData     = courseData,
                    startXp        = xpMap[Skills.AGILITY] ?: 0L,
                    agilityLevel   = agilityLevel,
                    petBoostPct    = 0,
                    toolEfficiency = gameData.toolEfficiency(equipped[EquipSlot.GRAPPLING_HOOK], EquipSlot.GRAPPLING_HOOK, courseData.levelRequired),
                )
                startSession(slot, action, result.frames, durationMs, efficiencyMultiplier, levelAtStart)
            }
            Skills.FIREMAKING -> {
                val logKey  = action.activityKey
                val qty     = action.qty.takeIf { it > 0 } ?: return
                val logData = gameData.logs[logKey] ?: return
                val ashKey  = ashForLog(logKey)
                val frames  = buildCraftFrames(xpMap[Skills.FIREMAKING] ?: 0L, qty, logData.xpPerLog.toDouble(), 1, ashKey,
                    efficiency = gameData.toolEfficiency(equipped[EquipSlot.TINDERBOX], EquipSlot.TINDERBOX, logData.levelRequired))
                startSession(slot, action, frames, durationMs, efficiencyMultiplier, levelAtStart)
            }
            Skills.RUNECRAFTING -> {
                val runeKey  = action.activityKey
                val runeData = gameData.runes[runeKey] ?: return
                val qty      = action.qty.takeIf { it > 0 } ?: return
                val startXp  = xpMap[Skills.RUNECRAFTING] ?: 0L
                val level    = XpTable.levelForXp(startXp)
                val ashBonus = action.catalystKey?.let { ashRuneBonus(it) } ?: 0
                val mult     = (when { level >= 75 -> 3; level >= 50 -> 2; else -> 1 }) + ashBonus
                val totalRunes   = mult * qty
                val totalXpGain  = (runeData.xpPerRune * totalRunes).toInt()
                val xpAfter      = startXp + totalXpGain
                val frames = listOf(SessionFrame(
                    minute      = 1,
                    xpGain      = totalXpGain,
                    xpBefore    = startXp,
                    xpAfter     = xpAfter,
                    levelBefore = level,
                    levelAfter  = XpTable.levelForXp(xpAfter),
                    items       = mapOf(runeKey to totalRunes),
                    leveledUp   = XpTable.levelForXp(xpAfter) > level,
                    kills       = qty,
                ))
                startSession(slot, action, frames, durationMs, efficiencyMultiplier, levelAtStart)
            }
            Skills.PRAYER -> {
                val boneKey     = action.activityKey
                val bone        = gameData.bones[boneKey] ?: return
                val qty         = action.qty.takeIf { it > 0 } ?: return
                val startXp     = xpMap[Skills.PRAYER] ?: 0L
                val totalXpGain = bone.xpPerBone.toInt() * qty
                val xpAfter     = startXp + totalXpGain
                val frames = listOf(SessionFrame(
                    minute      = 1,
                    xpGain      = totalXpGain,
                    xpBefore    = startXp,
                    xpAfter     = xpAfter,
                    levelBefore = XpTable.levelForXp(startXp),
                    levelAfter  = XpTable.levelForXp(xpAfter),
                    leveledUp   = XpTable.levelForXp(xpAfter) > XpTable.levelForXp(startXp),
                    kills       = qty,
                ))
                startSession(slot, action, frames, durationMs, efficiencyMultiplier, levelAtStart)
            }
            Skills.SMITHING -> {
                val r   = gameData.smithingRecipes[action.activityKey] ?: return
                val qty = action.qty.takeIf { it > 0 } ?: return
                val frames = buildCraftFrames(xpMap[Skills.SMITHING] ?: 0L, qty, r.xpPerItem, r.outputQuantity, action.activityKey,
                    efficiency = gameData.toolEfficiency(equipped[EquipSlot.HAMMER], EquipSlot.HAMMER, r.levelRequired))
                startSession(slot, action, frames, durationMs, efficiencyMultiplier, levelAtStart)
            }
            Skills.COOKING -> {
                val r: CookingRecipe = gameData.cookingRecipes[action.activityKey] ?: return
                val qty = action.qty.takeIf { it > 0 } ?: return
                val frames = buildCraftFrames(xpMap[Skills.COOKING] ?: 0L, qty, r.xpPerItem, 1, r.cookedItem,
                    efficiency = gameData.toolEfficiency(equipped[EquipSlot.FRYING_PAN], EquipSlot.FRYING_PAN, r.levelRequired))
                startSession(slot, action, frames, durationMs, efficiencyMultiplier, levelAtStart)
            }
            Skills.FLETCHING -> {
                val r   = gameData.fletchingRecipes[action.activityKey] ?: return
                val qty = action.qty.takeIf { it > 0 } ?: return
                val frames = buildCraftFrames(xpMap[Skills.FLETCHING] ?: 0L, qty, r.xpPerItem, r.outputQuantity, r.itemName)
                startSession(slot, action, frames, durationMs, efficiencyMultiplier, levelAtStart)
            }
            Skills.CRAFTING -> {
                val r   = gameData.craftingRecipes[action.activityKey] ?: return
                val qty = action.qty.takeIf { it > 0 } ?: return
                val frames = buildCraftFrames(xpMap[Skills.CRAFTING] ?: 0L, qty, r.xpPerItem, r.outputQuantity, action.activityKey)
                startSession(slot, action, frames, durationMs, efficiencyMultiplier, levelAtStart)
            }
            Skills.HERBLORE -> {
                val r           = gameData.herbloreRecipes[action.activityKey] ?: return
                val qty         = action.qty.takeIf { it > 0 } ?: return
                val catalystKey = action.catalystKey
                val outputKey   = if (catalystKey != null) "enhanced_${action.activityKey}" else action.activityKey
                if (catalystKey != null) playerRepo.consumeItemsUnlocked(mapOf(catalystKey to qty))
                val frames = buildCraftFrames(xpMap[Skills.HERBLORE] ?: 0L, qty, r.xpPerItem, r.outputQuantity, outputKey)
                startSession(slot, action, frames, durationMs, efficiencyMultiplier, levelAtStart)
            }
            Skills.CONSTRUCTION -> {
                val r   = gameData.constructionRecipes[action.activityKey] ?: return
                val qty = action.qty.takeIf { it > 0 } ?: return
                val frames = buildCraftFrames(xpMap[Skills.CONSTRUCTION] ?: 0L, qty, r.xpPerItem, r.outputQuantity, action.activityKey)
                startSession(slot, action, frames, durationMs, efficiencyMultiplier, levelAtStart)
            }
            Skills.THIEVING -> {
                val npcKey  = action.activityKey
                val npc     = gameData.thievingNpcs[npcKey] ?: return
                val result  = ThievingSimulator.simulate(
                    npcKey         = npcKey,
                    npc            = npc,
                    startXp        = xpMap[Skills.THIEVING] ?: 0L,
                    thievingLevel  = levels[Skills.THIEVING] ?: 1,
                    agilityLevel   = agilityLevel,
                    toolEfficiency = gameData.toolEfficiency(equipped[EquipSlot.LOCKPICK], EquipSlot.LOCKPICK, npc.levelRequired),
                )
                startSession(slot, action, result.frames, durationMs, efficiencyMultiplier, levelAtStart)
            }
            "boss" -> {
                val bossKey = action.activityKey
                val boss    = gameData.bosses[bossKey] ?: return
                val bossWeapon = (flags.activeWeaponSlot
                    ?: EquipSlot.WEAPON_SLOTS.firstOrNull { equipped[it] != null }
                    ?: EquipSlot.WEAPON).let { equipped[it] }.let { gameData.equipment[it] }
                val combatStyle = when (bossWeapon?.combatStyle) {
                    "ranged"   -> "ranged"
                    "magic"    -> "magic"
                    "strength" -> "strength"
                    else       -> "melee"
                }
                val totalAtkBonus = EquipSlot.ARMOR_SLOTS.sumOf { slot ->
                    val eq = gameData.equipment[equipped[slot]]
                    when (combatStyle) { "ranged" -> eq?.rangedAttackBonus ?: 0; "magic" -> eq?.magicAttackBonus ?: 0; else -> eq?.attackBonus ?: 0 }
                } + when (combatStyle) { "ranged" -> bossWeapon?.rangedAttackBonus ?: bossWeapon?.attackBonus ?: 0; "magic" -> bossWeapon?.magicAttackBonus ?: 0; else -> bossWeapon?.attackBonus ?: 0 }
                val totalStrBonus = EquipSlot.ARMOR_SLOTS.sumOf { gameData.equipment[equipped[it]]?.strengthBonus ?: 0 } + (bossWeapon?.strengthBonus ?: 0)
                val totalDefBonus = EquipSlot.ARMOR_SLOTS.sumOf { gameData.equipment[equipped[it]]?.defenseBonus  ?: 0 } + (bossWeapon?.defenseBonus  ?: 0)
                val totalMagicDmgBonus = if (combatStyle == "magic") EquipSlot.ARMOR_SLOTS.sumOf { gameData.equipment[equipped[it]]?.magicDamageBonus ?: 0 } + (bossWeapon?.magicDamageBonus ?: 0) else 0
                val equippedFoodKeys = flags.equippedFood.keys
                val availableFood    = inventory.filterKeys { it in equippedFoodKeys }
                val spell = gameData.spells[flags.activeSpell]
                val preferredArrow = flags.equippedArrows?.takeIf { (inventory[it] ?: 0) > 0 }
                val orderedWorkerBossArrowKeys = if (preferredArrow != null)
                    listOf(preferredArrow) + ARROW_TIERS.reversed().filter { it != preferredArrow && (inventory[it] ?: 0) > 0 }
                    else ARROW_TIERS.filter { (inventory[it] ?: 0) > 0 }
                val availableArrows = orderedWorkerBossArrowKeys.associateWith { inventory[it] ?: 0 }
                val bossFrames = CombatSimulator.simulateBoss(
                    boss               = boss,
                    bossKey            = bossKey,
                    playerAttack       = ((levels[Skills.ATTACK]    ?: 1) * attackCapeMult).toInt(),
                    playerStrength     = ((levels[Skills.STRENGTH]  ?: 1) * strengthCapeMult).toInt(),
                    playerDefence      = (((levels[Skills.DEFENSE]  ?: 1) * defenseCapeMult).toInt() + totalDefBonus),
                    playerHp           = levels[Skills.HITPOINTS] ?: 1,
                    weaponAttackBonus  = totalAtkBonus,
                    weaponStrBonus     = totalStrBonus,
                    combatStyle        = combatStyle,
                    playerRanged       = ((levels[Skills.RANGED] ?: 1) * rangedCapeMult).toInt(),
                    playerMagic        = ((levels[Skills.MAGIC]  ?: 1) * magicCapeMult).toInt(),
                    arrowStrengthBonuses = ARROW_STRENGTH_BONUS,
                    spellMaxHit        = (spell?.maxHit ?: 0) + totalMagicDmgBonus,
                    availableArrows    = availableArrows,
                    equippedFood       = availableFood,
                    foodHealValues     = gameData.foodHealValues,
                    blessingDefBonus   = (ChurchRepository.defBonus(flags) * prayerCapeMult).toInt(),
                    attackSpeedSec     = bossWeapon?.attackSpeed ?: CombatSimulator.BASE_ATTACK_SPEED_SEC,
                )
                startSession(slot, action, bossFrames, durationMs, efficiencyMultiplier, levelAtStart)
            }
            "combat" -> {
                val dungeonKey = action.activityKey
                val dungeon    = gameData.dungeons[dungeonKey] ?: return
                val activeWeaponSlot = flags.activeWeaponSlot
                    ?: EquipSlot.WEAPON_SLOTS.firstOrNull { equipped[it] != null }
                    ?: EquipSlot.WEAPON
                val weaponKey  = equipped[activeWeaponSlot]
                val weapon     = weaponKey?.let { gameData.equipment[it] }
                val combatStyle = when (weapon?.combatStyle) {
                    "ranged"   -> "ranged"
                    "magic"    -> "magic"
                    "strength" -> "strength"
                    else       -> "attack"
                }
                val totalAtkBonus = EquipSlot.ARMOR_SLOTS.sumOf { slot ->
                    val eq = gameData.equipment[equipped[slot]]
                    when (combatStyle) { "ranged" -> eq?.rangedAttackBonus ?: 0; "magic" -> eq?.magicAttackBonus ?: 0; else -> eq?.attackBonus ?: 0 }
                } + when (combatStyle) { "ranged" -> weapon?.rangedAttackBonus ?: weapon?.attackBonus ?: 0; "magic" -> weapon?.magicAttackBonus ?: 0; else -> weapon?.attackBonus ?: 0 }
                val totalStrBonus = EquipSlot.ARMOR_SLOTS.sumOf { gameData.equipment[equipped[it]]?.strengthBonus ?: 0 } + (weapon?.strengthBonus ?: 0)
                val totalDefBonus = EquipSlot.ARMOR_SLOTS.sumOf { gameData.equipment[equipped[it]]?.defenseBonus  ?: 0 } + (weapon?.defenseBonus  ?: 0)
                val totalMagicDmgBonus = if (combatStyle == "magic") EquipSlot.ARMOR_SLOTS.sumOf { gameData.equipment[equipped[it]]?.magicDamageBonus ?: 0 } + (weapon?.magicDamageBonus ?: 0) else 0
                val equippedFoodKeys = flags.equippedFood.keys
                val availableFood    = inventory.filterKeys { it in equippedFoodKeys }
                val spell = gameData.spells[flags.activeSpell]
                val preferredArrow = flags.equippedArrows?.takeIf { (inventory[it] ?: 0) > 0 }
                val orderedWorkerCombatArrowKeys = if (preferredArrow != null)
                    listOf(preferredArrow) + ARROW_TIERS.reversed().filter { it != preferredArrow && (inventory[it] ?: 0) > 0 }
                    else ARROW_TIERS.filter { (inventory[it] ?: 0) > 0 }
                val availableArrows = orderedWorkerCombatArrowKeys.associateWith { inventory[it] ?: 0 }
                val result = CombatSimulator.simulateDungeon(
                    dungeon             = dungeon,
                    enemies             = gameData.enemies,
                    playerAttack        = ((levels[Skills.ATTACK]    ?: 1) * attackCapeMult).toInt(),
                    playerStrength      = ((levels[Skills.STRENGTH]  ?: 1) * strengthCapeMult).toInt(),
                    playerDefence       = (((levels[Skills.DEFENSE]  ?: 1) * defenseCapeMult).toInt() + totalDefBonus),
                    playerHp            = levels[Skills.HITPOINTS] ?: 1,
                    blessingDefBonus    = (ChurchRepository.defBonus(flags) * prayerCapeMult).toInt(),
                    weaponAttackBonus   = totalAtkBonus,
                    weaponStrengthBonus = totalStrBonus,
                    combatStyle         = combatStyle,
                    playerRanged        = ((levels[Skills.RANGED]    ?: 1) * rangedCapeMult).toInt(),
                    playerMagic         = ((levels[Skills.MAGIC]     ?: 1) * magicCapeMult).toInt(),
                    arrowStrengthBonuses = ARROW_STRENGTH_BONUS,
                    spellMaxHit         = (spell?.maxHit ?: 0) + totalMagicDmgBonus,
                    agilityLevel        = agilityLevel,
                    agilityPrestige     = flags.skillPrestige[Skills.AGILITY] ?: 0,
                    petBoostPct         = 0,
                    equippedFood        = availableFood,
                    foodHealValues      = gameData.foodHealValues,
                    availableArrows     = availableArrows,
                    attackSpeedSec      = weapon?.attackSpeed ?: CombatSimulator.BASE_ATTACK_SPEED_SEC,
                )
                startSession(slot, action, result.frames, durationMs, efficiencyMultiplier, levelAtStart)
            }
        }
    }

    private suspend fun startSession(
        slot: Int,
        action: QueuedAction,
        frames: List<SessionFrame>,
        durationMs: Long,
        efficiencyMultiplier: Float,
        levelAtStart: Int = 0,
    ) {
        sessionRepo.startWorkerSession(
            workerSlot           = slot,
            skillName            = action.skillName,
            activityKey          = action.activityKey,
            frames               = encodeFrames(frames),
            durationMs           = durationMs,
            skillDisplayName     = action.skillDisplayName,
            efficiencyMultiplier = efficiencyMultiplier,
            levelAtStart         = levelAtStart,
        )
    }

    private fun encodeFrames(frames: List<SessionFrame>): String =
        json.encodeToString(json.serializersModule.serializer<List<SessionFrame>>(), frames)

    private fun ashForLog(logKey: String): String = when (logKey) {
        "oak_log"     -> "oak_ashes"
        "willow_log"  -> "willow_ashes"
        "maple_log"   -> "maple_ashes"
        "yew_log"     -> "yew_ashes"
        "magic_log"   -> "magic_ashes"
        "redwood_log" -> "redwood_ashes"
        else          -> "ashes"
    }


    private fun ashRuneBonus(ashKey: String): Int = when (ashKey) {
        "ashes"         -> 1
        "oak_ashes"     -> 2
        "willow_ashes"  -> 3
        "maple_ashes"   -> 4
        "yew_ashes"     -> 5
        "magic_ashes"   -> 6
        "redwood_ashes" -> 7
        else            -> 0
    }

    private fun buildCraftFrames(startXp: Long, qty: Int, xpPerItem: Double, outputQty: Int, outputKey: String, efficiency: Float = 1.0f): List<SessionFrame> {
        val totalXpGain = (xpPerItem * qty * efficiency).toInt()
        val xpAfter     = startXp + totalXpGain
        return listOf(SessionFrame(
            minute      = 1,
            xpGain      = totalXpGain,
            xpBefore    = startXp,
            xpAfter     = xpAfter,
            levelBefore = XpTable.levelForXp(startXp),
            levelAfter  = XpTable.levelForXp(xpAfter),
            items       = mapOf(outputKey to outputQty * qty),
            leveledUp   = XpTable.levelForXp(xpAfter) > XpTable.levelForXp(startXp),
            kills       = qty,
        ))
    }

    private val ARROW_TIERS = listOf(
        "runite_arrow", "adamantite_arrow", "mithril_arrow",
        "steel_arrow", "iron_arrow", "bronze_arrow",
    )

    private val ARROW_STRENGTH_BONUS = mapOf(
        "bronze_arrow"     to 7,
        "iron_arrow"       to 10,
        "steel_arrow"      to 16,
        "mithril_arrow"    to 22,
        "adamantite_arrow" to 31,
        "runite_arrow"     to 49,
    )
}
