package com.fantasyidler.repository

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.room.withTransaction
import com.fantasyidler.data.db.AppDatabase
import com.fantasyidler.data.db.dao.FarmingPatchDao
import com.fantasyidler.data.json.CropData
import com.fantasyidler.data.model.EquipSlot
import com.fantasyidler.util.toolEfficiency
import com.fantasyidler.data.model.FarmingPatch
import com.fantasyidler.data.model.Skills
import com.fantasyidler.receiver.FarmPatchAlarmReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlin.random.Random

@Singleton
class FarmingRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appDatabase: AppDatabase,
    private val patchDao: FarmingPatchDao,
    private val playerRepo: PlayerRepository,
    private val gameData: GameDataRepository,
    private val seasonalEventRepo: SeasonalEventRepository,
    private val globalStateRepo: GlobalStateRepository,
    private val json: Json,
    private val boostRepo: BoostRepository,
) {
    fun observePatches(): Flow<List<FarmingPatch>> = patchDao.observeAllPatches()

    fun patchCountForLevel(farmingLevel: Int): Int = when {
        farmingLevel >= 40 -> 5
        farmingLevel >= 20 -> 4
        else               -> 3
    }

    /** Get list of empty (unoccupied) patch numbers. Empty = no crop planted. */
    suspend fun getEmptyPatches(patchCount: Int): List<Int> {
        val allPatches = patchDao.getAllPatches()
        val occupiedNumbers = allPatches.filter { it.cropType != null }.map { it.patchNumber }.toSet()
        return (1..patchCount).filter { it !in occupiedNumbers }
    }

    /** Consume seed (and optional fertilizer ash) from inventory and plant the crop. Returns false if seed is missing. */
    suspend fun plantCrop(patchNumber: Int, crop: CropData, ashKey: String? = null): Boolean {
        val success = playerRepo.withLock {
            val player = playerRepo.getOrCreatePlayer()
            val inventory: Map<String, Int> = Json.decodeFromString(player.inventory)
            if ((inventory[crop.seedName] ?: 0) < 1) return@withLock false
            if (ashKey != null && (inventory[ashKey] ?: 0) < 1) return@withLock false
            
            val toConsume = mutableMapOf(crop.seedName to 1)
            if (ashKey != null) toConsume[ashKey] = 1
            playerRepo.consumeItemsUnlocked(toConsume)

            if (ashKey != null) {
                val flags = playerRepo.getFlagsUnlocked()
                playerRepo.updateFlagsUnlocked(flags.copy(
                    farmingFertilizer = flags.farmingFertilizer + (patchNumber.toString() to ashKey)
                ))
            }
            if (crop.id == "magic_bean") {
                val f = playerRepo.getFlagsUnlocked()
                if (!f.magicBeanPlanted) playerRepo.updateFlagsUnlocked(f.copy(magicBeanPlanted = true))
            }
            true
        }
        if (!success) return false

        val plantedAt = System.currentTimeMillis()
        patchDao.upsert(FarmingPatch(patchNumber = patchNumber, cropType = crop.id, plantedAt = plantedAt))

        if (crop.plantingXp > 0) {
            // Raw XP so clearPatch's base-value deduction reverses it exactly; boosted
            // planting XP made plant-and-clear cycles net positive (issue #1645).
            playerRepo.applySessionResults(
                skillName     = Skills.FARMING,
                xpGained      = crop.plantingXp.toLong(),
                itemsGained   = emptyMap(),
                applyXpBoosts = false,
            )
        }

        scheduleAlarm(patchNumber, crop.id, plantedAt + crop.growthTimeMs)
        return true
    }

    /** Called when the player taps "Climb" on a ready magic bean patch. Unlocks the Cloud Kingdom dungeon. */
    suspend fun climbBeanstalk(patchNumber: Int) {
        playerRepo.updateFlagsAtomically { flags ->
            if (!flags.unlockedDungeons.contains("cloud_kingdom")) {
                flags.copy(
                    unlockedDungeons  = flags.unlockedDungeons + "cloud_kingdom",
                    magicBeanPlanted  = true,
                )
            } else flags
        }
        cancelAlarm(patchNumber)
        patchDao.clear(patchNumber)
    }

    /** Harvests every patch in one DB transaction, so a multi-plot harvest commits once instead of once per plot. */
    suspend fun harvestPatches(patchNumbers: List<Int>) {
        appDatabase.withTransaction {
            patchNumbers.forEach { harvestPatch(it) }
        }
    }

    /** Plants across patches in one DB transaction, so bulk planting commits once instead of once per plot. Stops at the first patch that fails to plant (e.g. out of seeds). */
    suspend fun plantCrops(patchNumbers: List<Int>, crop: CropData, ashKey: String? = null): Int {
        var plantedCount = 0
        appDatabase.withTransaction {
            for (patchNumber in patchNumbers) {
                if (plantCrop(patchNumber, crop, ashKey)) plantedCount++ else return@withTransaction
            }
        }
        return plantedCount
    }

    /** Roll harvest yield, award items + XP, clear the patch. */
    suspend fun harvestPatch(patchNumber: Int) {
        val patch  = patchDao.getPatch(patchNumber) ?: return
        val cropId = patch.cropType ?: return
        if (cropId == "magic_bean") return          // bean patches are collected via climbBeanstalk()
        val crop   = gameData.crops[cropId] ?: return
        val cropGrowTime = crop.growthTimeMs.coerceAtLeast(0L)

        val player   = playerRepo.getOrCreatePlayer()
        val inventory: Map<String, Int> = json.decodeFromString(player.inventory)
        val equipped: Map<String, String?> = json.decodeFromString(player.equipped)
        val flags = playerRepo.getFlags()

        val levels: Map<String, Int> = json.decodeFromString(player.skillLevels)
        val hoeMult = gameData.toolEfficiency(equipped[EquipSlot.HOE], EquipSlot.HOE, skillLevels = levels, heirloomXp = flags.heirloomXp)

        val capeMult = resolveCapeMultiplier(
            skillName         = Skills.FARMING,
            equippedCape      = equipped[EquipSlot.CAPE]?.let { gameData.equipment[it] },
            inventoryKeys     = inventory.keys,
            townBuildingTiers = flags.townBuildingTiers,
            capeScaling       = boostRepo.capeScalingBySkill(flags),
            allEquipment      = gameData.equipment,
            ironman           = flags.ironman,
        )

        val ashKey = flags.farmingFertilizer[patchNumber.toString()]
        val ashMult = ashYieldMultiplier(ashKey)

        // Prestige: flat farming yield nodes, plus Crop Rotation when this patch's crop
        // differs from its previous harvest (or always, with the gnome capstone).
        val rotated       = flags.lastCropByPatch[patchNumber.toString()].let { it != null && it != cropId }
        val rotationMult  = 1.0 + boostRepo.cropRotationBonusPct(flags, rotated) / 100.0
        val prestigeMult = boostRepo.yieldMultiplier(Skills.FARMING, flags) *
                boostRepo.flowMultiplier(Skills.FARMING, flags, boostRepo.flowElapsedMs(flags,
                    Skills.FARMING, cropGrowTime))

        var yield = Random.nextInt(crop.yieldMin, crop.yieldMax + 1)
        yield = (yield * hoeMult * ashMult * capeMult * prestigeMult * rotationMult).roundToInt()

        val items = buildMap {
            put(crop.id, yield)
            put(crop.seedName, 1)
        }

        playerRepo.applySessionResults(
            skillName   = Skills.FARMING,
            xpGained    = crop.harvestXp.toLong() * yield,
            itemsGained = items,
        )

        playerRepo.recordWeeklyProgress("farming", "any", 1)
        playerRepo.updateFlagsAtomically { f ->
            f.copy(lastCropByPatch = f.lastCropByPatch + (patchNumber.toString() to cropId))
        }
        seasonalEventRepo.recordGathering(items)

        val farmingPet = gameData.pets.values.firstOrNull { it.boostedSkill == Skills.FARMING }
        if (farmingPet != null) {
            val petHit = Random.nextDouble() < 1.0 / 1000.0
            if (petHit) playerRepo.addPetIfNew(farmingPet.id, farmingPet.boostPercent)
            // Rolls even once the pet is owned, so the hidden dry streaks stay earnable.
            playerRepo.updateFlagsAtomically { HiddenAchievements.onFarmPetRoll(it, farmingPet.id, petHit) }
        }

        playerRepo.withLock {
            val latestFlags = playerRepo.getFlagsUnlocked()
            var newFlags = latestFlags
            if (ashKey != null) {
                newFlags = newFlags.copy(farmingFertilizer = newFlags.farmingFertilizer - patchNumber.toString())
            }
            
            val inv = playerRepo.getInventoryUnlocked()
            if (!newFlags.magicBeanPlanted && (inv["magic_bean"] ?: 0) == 0 && Random.nextInt(100) == 0) {
                playerRepo.addItemUnlocked("magic_bean", 1)
            }
            
            if (newFlags != latestFlags) playerRepo.updateFlagsUnlocked(newFlags)
        }

        cancelAlarm(patchNumber)
        patchDao.clear(patchNumber)
    }

    companion object {
        fun ashYieldMultiplier(ashKey: String?): Float = when (ashKey) {
            "ashes"         -> 1.10f
            "oak_ashes"     -> 1.20f
            "willow_ashes"  -> 1.35f
            "maple_ashes"   -> 1.50f
            "yew_ashes"     -> 1.75f
            "magic_ashes"   -> 2.00f
            "redwood_ashes" -> 2.50f
            else            -> 1.00f
        }
    }

    /** Wipes every patch (used by Reset Progression) and cancels any pending grow-alarms. */
    suspend fun resetAllPatches() {
        patchDao.getAllPatches().forEach { cancelAlarm(it.patchNumber) }
        patchDao.clearAll()
    }

    /** Remove the crop without reward, and claw back the planting XP. */
    suspend fun clearPatch(patchNumber: Int) {
        val patch = patchDao.getPatch(patchNumber)
        val plantingXp = patch?.cropType?.let { gameData.crops[it]?.plantingXp?.toLong() } ?: 0L
        if (plantingXp > 0) playerRepo.deductSkillXp(Skills.FARMING, plantingXp)
        if (patch?.cropType == "magic_bean") {
            playerRepo.withLock {
                playerRepo.addItemUnlocked("magic_bean", 1)
                val flags = playerRepo.getFlagsUnlocked()
                playerRepo.updateFlagsUnlocked(flags.copy(magicBeanPlanted = false))
            }
        }
        cancelAlarm(patchNumber)
        patchDao.clear(patchNumber)
    }

    // ------------------------------------------------------------------

    // Request codes are slot * 100 + patchNumber so each character's alarms are independent —
    // otherwise planting or harvesting on one character overwrites/cancels another's pending
    // alarm for the same patch number (issue #1471).
    private fun pendingIntent(slot: Int, patchNumber: Int, cropDisplayName: String): PendingIntent {
        val intent = Intent(context, FarmPatchAlarmReceiver::class.java).apply {
            putExtra(FarmPatchAlarmReceiver.EXTRA_PATCH_NUMBER, patchNumber)
            putExtra(FarmPatchAlarmReceiver.EXTRA_CROP_NAME, cropDisplayName)
            putExtra(FarmPatchAlarmReceiver.EXTRA_SAVE_SLOT, slot)
        }
        return PendingIntent.getBroadcast(
            context, slot * 100 + patchNumber, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    // Alarms scheduled before slot-awareness used the bare patch number as request code.
    private fun legacyPendingIntent(patchNumber: Int): PendingIntent {
        val intent = Intent(context, FarmPatchAlarmReceiver::class.java).apply {
            putExtra(FarmPatchAlarmReceiver.EXTRA_PATCH_NUMBER, patchNumber)
            putExtra(FarmPatchAlarmReceiver.EXTRA_CROP_NAME, "")
        }
        return PendingIntent.getBroadcast(
            context, patchNumber, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private suspend fun scheduleAlarm(patchNumber: Int, cropDisplayName: String, triggerAt: Long) {
        val slot = globalStateRepo.getActiveSaveSlot()
        context.getSystemService(AlarmManager::class.java)
            .setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent(slot, patchNumber, cropDisplayName))
    }

    private suspend fun cancelAlarm(patchNumber: Int) {
        val slot = globalStateRepo.getActiveSaveSlot()
        val mgr = context.getSystemService(AlarmManager::class.java)
        mgr.cancel(pendingIntent(slot, patchNumber, ""))
        mgr.cancel(legacyPendingIntent(patchNumber))
    }
}
