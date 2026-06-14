package com.fantasyidler.repository

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.fantasyidler.data.db.dao.FarmingPatchDao
import com.fantasyidler.data.json.CropData
import com.fantasyidler.data.model.EquipSlot
import com.fantasyidler.data.model.FarmingPatch
import com.fantasyidler.data.model.Skills
import com.fantasyidler.data.model.SoilState
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
        val consumed = playerRepo.consumeItems(mapOf(crop.seedName to 1))
        if (!consumed) return false

        if (ashKey != null) {
            playerRepo.consumeItems(mapOf(ashKey to 1))
            val flags = playerRepo.getFlags()
            playerRepo.updateFlags(flags.copy(
                farmingFertilizer = flags.farmingFertilizer + (patchNumber.toString() to ashKey)
            ))
        }

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

    /** Roll harvest yield, award items + XP, clear the patch. */
    suspend fun harvestPatch(patchNumber: Int) {
        val patch  = patchDao.getPatch(patchNumber) ?: return
        val cropId = patch.cropType ?: return
        val crop   = gameData.crops[cropId] ?: return

        val patchKey = patchNumber.toString()
        val flags    = playerRepo.getFlags()
        val ashKey   = flags.farmingFertilizer[patchKey]

        // ----------------------------------------------------------------
        // Cover crop branch: restore soil, award flat XP, yield no items
        // ----------------------------------------------------------------
        if (crop.isCoverCrop) {
            if (crop.soilRestoreXp > 0) {
                playerRepo.applySessionResults(
                    skillName   = Skills.FARMING,
                    xpGained    = crop.soilRestoreXp.toLong(),
                    itemsGained = emptyMap(),
                )
            }
            playerRepo.recordWeeklyProgress("farming", "any", 1)
            // Reset streak; clear lastCropPerPatch so next planting is neutral
            playerRepo.updateFlags(
                flags.copy(
                    consecutiveSameCrop = flags.consecutiveSameCrop + (patchKey to 0),
                    lastCropPerPatch    = flags.lastCropPerPatch - patchKey,
                    farmingFertilizer   = if (ashKey != null)
                                              flags.farmingFertilizer - patchKey
                                          else
                                              flags.farmingFertilizer,
                )
            )
            cancelAlarm(patchNumber)
            patchDao.clear(patchNumber)
            return
        }

        // ----------------------------------------------------------------
        // Regular crop branch
        // ----------------------------------------------------------------
        val player   = playerRepo.getOrCreatePlayer()
        val equipped: Map<String, String?> = json.decodeFromString(player.equipped)

        val hoeBonus    = equipped[EquipSlot.HOE]?.let { gameData.equipment[it]?.farmingEfficiency } ?: 0f
        val capedDouble = equipped[EquipSlot.CAPE] == "farming_cape"
        val ashMult     = ashYieldMultiplier(ashKey)

        // --- Crop rotation bonus/penalty ---
        val lastCrop  = flags.lastCropPerPatch[patchKey]
        val sameCount = flags.consecutiveSameCrop[patchKey] ?: 0
        val (rotationMult, _) = when {
            lastCrop == null    -> Pair(1.0f, SoilState.NEUTRAL)  // first-ever harvest on this patch
            lastCrop != cropId  -> Pair(1.10f, SoilState.FRESH)   // rotated crop → +10%
            sameCount >= 2      -> Pair(0.90f, SoilState.DEPLETED) // 3rd+ consecutive same crop → -10%
            else                -> Pair(1.0f, SoilState.NEUTRAL)
        }

        var yield = Random.nextInt(crop.yieldMin, crop.yieldMax + 1)
        yield = (yield * (1f + hoeBonus) * ashMult * rotationMult).roundToInt()
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
        if (farmingPet != null && Random.nextDouble() < 1.0 / 1000.0) {
            playerRepo.addPetIfNew(farmingPet.id, farmingPet.boostPercent)
        }

        // Update rotation history + clear fertilizer in a single flags write
        val newSameCount = if (lastCrop == cropId) sameCount + 1 else 0
        playerRepo.updateFlags(
            flags.copy(
                lastCropPerPatch    = flags.lastCropPerPatch    + (patchKey to cropId),
                consecutiveSameCrop = flags.consecutiveSameCrop + (patchKey to newSameCount),
                farmingFertilizer   = if (ashKey != null)
                                          flags.farmingFertilizer - patchKey
                                      else
                                          flags.farmingFertilizer,
            )
        )

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

    /** Remove the crop without reward, and claw back the planting XP. */
    suspend fun clearPatch(patchNumber: Int) {
        val patch = patchDao.getPatch(patchNumber)
        val plantingXp = patch?.cropType?.let { gameData.crops[it]?.plantingXp?.toLong() } ?: 0L
        if (plantingXp > 0) playerRepo.deductSkillXp(Skills.FARMING, plantingXp)
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
