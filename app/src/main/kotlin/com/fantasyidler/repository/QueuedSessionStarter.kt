package com.fantasyidler.repository

import com.fantasyidler.data.json.BossData
import com.fantasyidler.data.json.CookingRecipe
import com.fantasyidler.data.model.EquipSlot
import com.fantasyidler.data.model.OwnedPet
import com.fantasyidler.data.model.PlayerFlags
import com.fantasyidler.data.model.QueuedAction
import com.fantasyidler.data.model.SessionFrame
import com.fantasyidler.data.model.Skills
import com.fantasyidler.simulator.CombatSimulator
import com.fantasyidler.simulator.SkillSimulator
import com.fantasyidler.simulator.XpTable
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlin.math.max
import kotlin.random.Random
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Starts the next queued session using current player state.
 * Shared between ViewModels (on collect) and [com.fantasyidler.receiver.SessionAlarmReceiver]
 * (background auto-advance).
 */
@Singleton
class QueuedSessionStarter @Inject constructor(
    private val playerRepo: PlayerRepository,
    private val sessionRepo: SessionRepository,
    private val gameData: GameDataRepository,
    private val json: Json,
) {
    /**
     * Pops the first item from the queue and starts it as a new session.
     * Returns true if a session was started, false if the queue was empty or the
     * session couldn't be started (e.g. missing materials).
     */
    suspend fun startNextQueued(): Boolean {
        val next = playerRepo.dequeueNextAction() ?: return false
        return try {
            startQueuedAction(next)
            true
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun startQueuedAction(action: QueuedAction) {
        val player    = playerRepo.getOrCreatePlayer()
        val levels:   Map<String, Int>     = json.decodeFromString(player.skillLevels)
        val xpMap:    Map<String, Long>    = json.decodeFromString(player.skillXp)
        val equipped: Map<String, String?> = json.decodeFromString(player.equipped)
        val inventory: Map<String, Int>    = json.decodeFromString(player.inventory)
        val flags: PlayerFlags             = json.decodeFromString(player.flags)
        val agilityLevel = levels[Skills.AGILITY] ?: 1

        when (action.skillName) {
            Skills.MINING -> {
                val oreKey  = action.activityKey
                val oreData = gameData.ores[oreKey] ?: return
                val result  = SkillSimulator.simulateMining(
                    oreKey          = oreKey,
                    oreData         = oreData,
                    gems            = gameData.gems,
                    startXp         = xpMap[Skills.MINING] ?: 0L,
                    agilityLevel    = agilityLevel,
                    petBoostPct     = gatheringPetBoost(player.pets, Skills.MINING),
                    toolEfficiency  = toolEfficiency(equipped[EquipSlot.PICKAXE], EquipSlot.PICKAXE, oreData.levelRequired),
                    petDropKey      = petDropKey(Skills.MINING),
                    petDropChance   = petDropChance(Skills.MINING),
                )
                startSession(action, result)
            }
            Skills.WOODCUTTING -> {
                val treeKey  = action.activityKey
                val treeData = gameData.trees[treeKey] ?: return
                val result   = SkillSimulator.simulateWoodcutting(
                    treeData        = treeData,
                    startXp         = xpMap[Skills.WOODCUTTING] ?: 0L,
                    agilityLevel    = agilityLevel,
                    petBoostPct     = gatheringPetBoost(player.pets, Skills.WOODCUTTING),
                    toolEfficiency  = toolEfficiency(equipped[EquipSlot.AXE], EquipSlot.AXE, treeData.levelRequired),
                    petDropKey      = petDropKey(Skills.WOODCUTTING),
                    petDropChance   = petDropChance(Skills.WOODCUTTING),
                )
                startSession(action, result)
            }
            Skills.FISHING -> {
                val result = SkillSimulator.simulateGathering(
                    skillData       = gameData.fishingSkillData,
                    startXp         = xpMap[Skills.FISHING] ?: 0L,
                    agilityLevel    = agilityLevel,
                    petBoostPct     = gatheringPetBoost(player.pets, Skills.FISHING),
                    toolEfficiency  = toolEfficiency(equipped[EquipSlot.FISHING_ROD], EquipSlot.FISHING_ROD, levels[Skills.FISHING] ?: 1),
                    petDropKey      = petDropKey(Skills.FISHING),
                    petDropChance   = petDropChance(Skills.FISHING),
                )
                startSession(action, result)
            }
            Skills.AGILITY -> {
                val courseKey  = action.activityKey
                val courseData = gameData.agilityCourses[courseKey] ?: return
                val result     = SkillSimulator.simulateAgility(
                    courseData   = courseData,
                    startXp      = xpMap[Skills.AGILITY] ?: 0L,
                    agilityLevel = agilityLevel,
                    petBoostPct  = gatheringPetBoost(player.pets, Skills.AGILITY),
                )
                startSession(action, result)
            }
            Skills.FIREMAKING -> {
                val logKey  = action.activityKey
                val logData = gameData.logs[logKey] ?: return
                if ((inventory[logKey] ?: 0) <= 0) return
                val result  = SkillSimulator.simulateGathering(
                    skillData    = gameData.firemakingSkillData,
                    startXp      = xpMap[Skills.FIREMAKING] ?: 0L,
                    agilityLevel = agilityLevel,
                    petBoostPct  = gatheringPetBoost(player.pets, Skills.FIREMAKING),
                )
                startSession(action, result)
            }
            Skills.RUNECRAFTING -> {
                val runeKey  = action.activityKey
                val runeData = gameData.runes[runeKey] ?: return
                val qty      = action.qty.takeIf { it > 0 } ?: return
                if ((inventory["rune_essence"] ?: 0) < runeData.essenceCost * qty) return
                val currentXp = xpMap[Skills.RUNECRAFTING] ?: 0L
                val frames = buildList {
                    var xp = currentXp
                    for (i in 1..qty) {
                        val before = XpTable.levelForXp(xp)
                        val multiplier = when {
                            before >= 75 -> 3
                            before >= 50 -> 2
                            else         -> 1
                        }
                        val runesProduced = multiplier
                        val gain = (runeData.xpPerRune * runesProduced).toInt()
                        xp += gain
                        add(SessionFrame(
                            minute      = i,
                            xpGain      = gain,
                            xpBefore    = xp - gain,
                            xpAfter     = xp,
                            levelBefore = before,
                            levelAfter  = XpTable.levelForXp(xp),
                            items       = mapOf(runeKey to runesProduced),
                        ))
                    }
                }
                val perEssenceMs = SkillSimulator.sessionDurationMs(agilityLevel) / 60
                sessionRepo.startSession(Skills.RUNECRAFTING, runeKey, encodeFrames(frames), qty.toLong() * perEssenceMs, action.skillDisplayName)
            }
            Skills.PRAYER -> {
                val boneKey = action.activityKey
                val bone    = gameData.bones[boneKey] ?: return
                val qty     = action.qty.takeIf { it > 0 } ?: return
                if ((inventory[boneKey] ?: 0) < qty) return
                val currentXp = xpMap[Skills.PRAYER] ?: 0L
                val frames = buildList {
                    var xp = currentXp
                    for (i in 1..qty) {
                        val before = XpTable.levelForXp(xp)
                        val gain   = bone.xpPerBone.toInt()
                        xp        += gain
                        add(SessionFrame(
                            minute      = i,
                            xpGain      = gain,
                            xpBefore    = xp - gain,
                            xpAfter     = xp,
                            levelBefore = before,
                            levelAfter  = XpTable.levelForXp(xp),
                            kills       = 1,
                        ))
                    }
                }
                val perBoneMs = SkillSimulator.sessionDurationMs(agilityLevel) / 60
                sessionRepo.startSession(
                    skillName        = Skills.PRAYER,
                    activityKey      = boneKey,
                    frames           = encodeFrames(frames),
                    durationMs       = qty.toLong() * perBoneMs,
                    skillDisplayName = action.skillDisplayName,
                )
            }
            Skills.SMITHING -> {
                val r   = gameData.smithingRecipes[action.activityKey] ?: return
                val qty = action.qty.takeIf { it > 0 } ?: return
                if (!r.materials.all { (item, needed) -> (inventory[item] ?: 0) >= needed * qty }) return
                val frames = buildCraftFrames(xpMap[Skills.SMITHING] ?: 0L, qty, r.xpPerItem, r.outputQuantity, action.activityKey)
                val perItemMs = SkillSimulator.sessionDurationMs(agilityLevel) / 60
                sessionRepo.startSession(Skills.SMITHING, action.activityKey, encodeFrames(frames), qty * perItemMs, action.skillDisplayName)
            }
            Skills.COOKING -> {
                val r: CookingRecipe = gameData.cookingRecipes[action.activityKey] ?: return
                val qty = action.qty.takeIf { it > 0 } ?: return
                if ((inventory[r.rawItem] ?: 0) < qty) return
                val frames = buildCraftFrames(xpMap[Skills.COOKING] ?: 0L, qty, r.xpPerItem, 1, r.cookedItem)
                val perItemMs = SkillSimulator.sessionDurationMs(agilityLevel) / 60
                sessionRepo.startSession(Skills.COOKING, action.activityKey, encodeFrames(frames), qty * perItemMs, action.skillDisplayName)
            }
            Skills.FLETCHING -> {
                val r   = gameData.fletchingRecipes[action.activityKey] ?: return
                val qty = action.qty.takeIf { it > 0 } ?: return
                if (!r.materials.all { (item, needed) -> (inventory[item] ?: 0) >= needed * qty }) return
                val frames = buildCraftFrames(xpMap[Skills.FLETCHING] ?: 0L, qty, r.xpPerItem, r.outputQuantity, r.itemName)
                val perItemMs = SkillSimulator.sessionDurationMs(agilityLevel) / 60
                sessionRepo.startSession(Skills.FLETCHING, action.activityKey, encodeFrames(frames), qty * perItemMs, action.skillDisplayName)
            }
            Skills.CRAFTING -> {
                val r   = gameData.craftingRecipes[action.activityKey] ?: return
                val qty = action.qty.takeIf { it > 0 } ?: return
                if (!r.materials.all { (item, needed) -> (inventory[item] ?: 0) >= needed * qty }) return
                val frames = buildCraftFrames(xpMap[Skills.CRAFTING] ?: 0L, qty, r.xpPerItem, r.outputQuantity, action.activityKey)
                val perItemMs = SkillSimulator.sessionDurationMs(agilityLevel) / 60
                sessionRepo.startSession(Skills.CRAFTING, action.activityKey, encodeFrames(frames), qty * perItemMs, action.skillDisplayName)
            }
            "boss" -> {
                val bossKey = action.activityKey
                val boss    = gameData.bosses[bossKey] ?: return
                val weaponKey = equipped[EquipSlot.WEAPON]
                val weapon    = weaponKey?.let { gameData.equipment[it] }
                val frame = simulateBoss(
                    boss              = boss,
                    playerAttack      = levels[Skills.ATTACK]    ?: 1,
                    playerStrength    = levels[Skills.STRENGTH]  ?: 1,
                    playerDefence     = levels[Skills.DEFENSE]   ?: 1,
                    playerHp          = levels[Skills.HITPOINTS] ?: 1,
                    weaponAttackBonus = weapon?.attackBonus      ?: 0,
                    weaponStrBonus    = weapon?.strengthBonus    ?: 0,
                )
                val framesJson = encodeFrames(listOf(frame))
                sessionRepo.startSession(
                    skillName        = "boss",
                    activityKey      = bossKey,
                    frames           = framesJson,
                    durationMs       = boss.durationMinutes * 60_000L,
                    skillDisplayName = action.skillDisplayName,
                )
            }
            "combat" -> {
                val dungeonKey = action.activityKey
                val dungeon    = gameData.dungeons[dungeonKey] ?: return
                val weaponKey  = equipped[EquipSlot.WEAPON]
                val weapon     = weaponKey?.let { gameData.equipment[it] }
                val combatStyle = when (weapon?.combatStyle) {
                    "ranged"   -> "ranged"
                    "magic"    -> "magic"
                    "strength" -> "strength"
                    else       -> "attack"
                }
                val bestArrow = ARROW_TIERS.firstOrNull { (inventory[it] ?: 0) > 0 }
                val arrowBonus = bestArrow?.let { ARROW_STRENGTH_BONUS[it] } ?: 0
                val equippedFoodKeys = flags.equippedFood.keys
                val availableFood    = inventory.filterKeys { it in equippedFoodKeys }
                val spell = gameData.spells[flags.activeSpell]
                val result = CombatSimulator.simulateDungeon(
                    dungeon             = dungeon,
                    enemies             = gameData.enemies,
                    playerAttack        = levels[Skills.ATTACK]    ?: 1,
                    playerStrength      = levels[Skills.STRENGTH]  ?: 1,
                    playerDefence       = levels[Skills.DEFENSE]   ?: 1,
                    playerHp            = levels[Skills.HITPOINTS] ?: 1,
                    weaponAttackBonus   = weapon?.attackBonus      ?: 0,
                    weaponStrengthBonus = weapon?.strengthBonus    ?: 0,
                    combatStyle         = combatStyle,
                    playerRanged        = levels[Skills.RANGED]    ?: 1,
                    playerMagic         = levels[Skills.MAGIC]     ?: 1,
                    arrowStrengthBonus  = arrowBonus,
                    spellMaxHit         = spell?.maxHit             ?: 0,
                    agilityLevel        = agilityLevel,
                    petBoostPct         = combatPetBoost(player.pets),
                    equippedFood        = availableFood,
                    foodHealValues      = gameData.foodHealValues,
                )
                val totalKills = result.frames.sumOf { it.kills }
                if (combatStyle == "ranged" && bestArrow != null && totalKills > 0)
                    playerRepo.consumeItems(mapOf(bestArrow to minOf(totalKills, inventory[bestArrow] ?: 0)))
                if (combatStyle == "magic" && spell != null && totalKills > 0) {
                    val staffCoversRune = weapon?.infiniteRunes == spell.runeType
                    if (!staffCoversRune)
                        playerRepo.consumeItems(mapOf(spell.runeType to totalKills * spell.runeCost))
                }
                startSession(action, result)
            }
        }
    }

    private fun simulateBoss(
        boss: BossData,
        playerAttack: Int,
        playerStrength: Int,
        playerDefence: Int,
        playerHp: Int,
        weaponAttackBonus: Int,
        weaponStrBonus: Int,
    ): SessionFrame {
        val effStr    = playerStrength + weaponStrBonus
        val playerMax = max(1, 1 + effStr * (weaponStrBonus + 64) / 640)
        val effAtk    = playerAttack + weaponAttackBonus
        val bossDef   = boss.defensiveStats.attackDefense
        val playerHit = (if (effAtk > bossDef) 1.0 - bossDef / (2.0 * effAtk.coerceAtLeast(1))
                         else effAtk / (2.0 * bossDef.coerceAtLeast(1))).coerceIn(0.10, 0.95)
        val playerDps = (playerMax / 2.0) * playerHit / 2.4

        val bossEffStr = boss.combatStats.strengthLevel + boss.combatStats.strengthBonus
        val bossMax    = max(1, 1 + bossEffStr * (boss.combatStats.strengthBonus + 64) / 640)
        val bossEffAtk = boss.combatStats.attackLevel + boss.combatStats.attackBonus
        val bossHit    = (if (bossEffAtk > playerDefence) 1.0 - playerDefence / (2.0 * bossEffAtk.coerceAtLeast(1))
                          else bossEffAtk / (2.0 * playerDefence.coerceAtLeast(1))).coerceIn(0.10, 0.95)
        val bossDps    = (bossMax / 2.0) * bossHit / 2.4

        val playerTtk = if (playerDps > 0) boss.hp / playerDps else Double.MAX_VALUE
        val bossTtk   = if (bossDps > 0) playerHp * 10.0 / bossDps else Double.MAX_VALUE
        val won       = playerTtk <= bossTtk

        val items     = mutableMapOf<String, Int>()
        val xpBySkill = mutableMapOf<String, Long>()
        if (won) {
            items["coins"] = Random.nextInt(boss.commonLoot.coinsMin, boss.commonLoot.coinsMax + 1)
            for ((item, range) in boss.commonLoot.items)
                items[item] = if (range.min >= range.max) range.min else Random.nextInt(range.min, range.max + 1)
            for (rare in boss.rareDrops)
                if (Random.nextDouble() < rare.chance) items[rare.item] = (items[rare.item] ?: 0) + 1
            boss.pet?.let { pet -> if (Random.nextDouble() < pet.chance) items[pet.id] = 1 }
            for ((skill, xp) in boss.xpRewards) xpBySkill[skill] = xp.toLong()
        }
        val totalXp = xpBySkill.values.sum()
        return SessionFrame(
            minute = 1, xpGain = totalXp.toInt(), xpBefore = 0L, xpAfter = totalXp,
            levelBefore = 0, levelAfter = 0, items = items, xpBySkill = xpBySkill,
            kills = if (won) 1 else 0, killsByEnemy = emptyMap(),
        )
    }

    private suspend fun startSession(action: QueuedAction, result: SkillSimulator.Result) {
        sessionRepo.startSession(
            skillName        = action.skillName,
            activityKey      = action.activityKey,
            frames           = encodeFrames(result.frames),
            durationMs       = result.durationMs,
            skillDisplayName = action.skillDisplayName,
        )
    }

    private fun encodeFrames(frames: List<SessionFrame>): String =
        json.encodeToString(json.serializersModule.serializer<List<SessionFrame>>(), frames)

    private fun gatheringPetBoost(petsJson: String, skillKey: String): Int {
        val pets = try { json.decodeFromString<List<OwnedPet>>(petsJson) } catch (_: Exception) { return 0 }
        val id   = pets.firstOrNull { gameData.pets[it.id]?.boostedSkill == skillKey } ?: return 0
        return gameData.pets[id.id]?.boostPercent ?: 0
    }

    private fun combatPetBoost(petsJson: String): Int {
        val pets = try { json.decodeFromString<List<OwnedPet>>(petsJson) } catch (_: Exception) { return 0 }
        val id   = pets.firstOrNull { gameData.pets[it.id]?.boostedSkill in Skills.COMBAT } ?: return 0
        return gameData.pets[id.id]?.boostPercent ?: 0
    }

    private fun petDropKey(skillKey: String): String? =
        gameData.pets.values.firstOrNull { it.boostedSkill == skillKey }?.id

    private fun petDropChance(skillKey: String): Double =
        if (gameData.pets.values.any { it.boostedSkill == skillKey }) 1.0 / 1000.0 else 0.0

    private val TOOL_TIERS = listOf(1, 15, 30, 55, 70, 85)

    private fun tierIndex(level: Int): Int = TOOL_TIERS.indexOfLast { it <= level }.coerceAtLeast(0)

    private fun toolEfficiency(itemKey: String?, slot: String, resourceLevelRequired: Int = 0): Float {
        if (itemKey == null) return 1.0f
        val eq   = gameData.equipment[itemKey] ?: return 1.0f
        val base = when (slot) {
            EquipSlot.PICKAXE     -> eq.miningEfficiency      ?: 1.0f
            EquipSlot.AXE         -> eq.woodcuttingEfficiency ?: 1.0f
            EquipSlot.FISHING_ROD -> eq.fishingEfficiency     ?: 1.0f
            else                  -> 1.0f
        }
        if (resourceLevelRequired <= 0) return base
        val skillKey = when (slot) {
            EquipSlot.PICKAXE     -> Skills.MINING
            EquipSlot.AXE         -> Skills.WOODCUTTING
            EquipSlot.FISHING_ROD -> Skills.FISHING
            else                  -> return base
        }
        val toolReqLevel = eq.requirements[skillKey] ?: 1
        val tierDiff     = tierIndex(toolReqLevel) - tierIndex(resourceLevelRequired)
        return if (tierDiff > 0) base * (1.0f + 0.25f * tierDiff) else base
    }

    private fun buildCraftFrames(startXp: Long, qty: Int, xpPerItem: Double, outputQty: Int, outputKey: String): List<SessionFrame> {
        var xp = startXp
        return buildList {
            for (i in 1..qty) {
                val levelBefore = XpTable.levelForXp(xp)
                val gain = xpPerItem.toInt()
                xp += gain
                val levelAfter = XpTable.levelForXp(xp)
                add(SessionFrame(
                    minute = i, xpGain = gain, xpBefore = xp - gain, xpAfter = xp,
                    levelBefore = levelBefore, levelAfter = levelAfter,
                    items = mapOf(outputKey to outputQty),
                    leveledUp = levelAfter > levelBefore,
                ))
            }
        }
    }

    private val ARROW_TIERS = listOf(
        "runite_arrow", "adamantite_arrow", "mithril_arrow",
        "steel_arrow", "iron_arrow", "bronze_arrow",
    )

    private val ARROW_STRENGTH_BONUS = mapOf(
        "bronze_arrow"     to 0,
        "iron_arrow"       to 2,
        "steel_arrow"      to 4,
        "mithril_arrow"    to 6,
        "adamantite_arrow" to 8,
        "runite_arrow"     to 10,
    )
}
