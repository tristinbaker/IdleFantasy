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
    private val json: Json,
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
            val inventory: Map<String, Int> = kotlinx.serialization.json.Json.decodeFromString(player.inventory)
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
            playerRepo.applySessionResults(
                skillName   = Skills.FARMING,
                xpGained    = crop.plantingXp.toLong(),
                itemsGained = emptyMap(),
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

    /** Roll harvest yield, award items + XP, clear the patch. */
    suspend fun harvestPatch(patchNumber: Int) {
        val patch  = patchDao.getPatch(patchNumber) ?: return
        val cropId = patch.cropType ?: return
        if (cropId == "magic_bean") return          // bean patches are collected via climbBeanstalk()
        val crop   = gameData.crops[cropId] ?: return

        val player   = playerRepo.getOrCreatePlayer()
        val equipped: Map<String, String?> = json.decodeFromString(player.equipped)

        val hoeBonus    = equipped[EquipSlot.HOE]?.let { gameData.equipment[it]?.farmingEfficiency } ?: 0f
        val capedDouble = equipped[EquipSlot.CAPE] == "farming_cape"

        val flags = playerRepo.getFlags()
        val ashKey = flags.farmingFertilizer[patchNumber.toString()]
        val ashMult = ashYieldMultiplier(ashKey)

        var yield = kotlin.random.Random.nextInt(crop.yieldMin, crop.yieldMax + 1)
        yield = (yield * (1f + hoeBonus) * ashMult).roundToInt()
        if (capedDouble) yield *= 2

        val items = buildMap<String, Int> {
            put(crop.id, yield)
            put(crop.seedName, 1)
        }

        playerRepo.applySessionResults(
            skillName   = Skills.FARMING,
            xpGained    = crop.harvestXp.toLong() * yield,
            itemsGained = items,
        )
        
        playerRepo.recordWeeklyProgress("farming", "any", 1)

        val farmingPet = gameData.pets.values.firstOrNull { it.boostedSkill == Skills.FARMING }
        if (farmingPet != null && kotlin.random.Random.nextDouble() < 1.0 / 1000.0) {
            playerRepo.addPetIfNew(farmingPet.id, farmingPet.boostPercent)
        }

        playerRepo.withLock {
            val latestFlags = playerRepo.getFlagsUnlocked()
            var newFlags = latestFlags
            if (ashKey != null) {
                newFlags = newFlags.copy(farmingFertilizer = newFlags.farmingFertilizer - patchNumber.toString())
            }
            
            val inv = playerRepo.getInventoryUnlocked()
            if (!newFlags.magicBeanPlanted && (inv["magic_bean"] ?: 0) == 0 && kotlin.random.Random.nextInt(100) == 0) {
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

    private fun pendingIntent(patchNumber: Int, cropDisplayName: String): PendingIntent {
        val intent = Intent(context, FarmPatchAlarmReceiver::class.java).apply {
            putExtra(FarmPatchAlarmReceiver.EXTRA_PATCH_NUMBER, patchNumber)
            putExtra(FarmPatchAlarmReceiver.EXTRA_CROP_NAME, cropDisplayName)
        }
        return PendingIntent.getBroadcast(
            context, patchNumber, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun scheduleAlarm(patchNumber: Int, cropDisplayName: String, triggerAt: Long) {
        context.getSystemService(AlarmManager::class.java)
            .setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent(patchNumber, cropDisplayName))
    }

    private fun cancelAlarm(patchNumber: Int) {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(patchNumber, ""))
    }
}
