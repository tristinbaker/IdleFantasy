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

    /** Consume seed from inventory and plant it. Returns false if seed is missing. */
    suspend fun plantCrop(patchNumber: Int, crop: CropData): Boolean {
        val consumed = playerRepo.consumeItems(mapOf(crop.seedName to 1))
        if (!consumed) return false

        val plantedAt = System.currentTimeMillis()
        patchDao.upsert(FarmingPatch(patchNumber = patchNumber, cropType = crop.id, plantedAt = plantedAt))

        if (crop.plantingXp > 0) {
            playerRepo.applySessionResults(
                skillName   = Skills.FARMING,
                xpGained    = crop.plantingXp.toLong(),
                itemsGained = emptyMap(),
            )
        }

        scheduleAlarm(patchNumber, crop.displayName, plantedAt + crop.growthTimeMs)
        return true
    }

    /** Roll harvest yield, award items + XP, clear the patch. */
    suspend fun harvestPatch(patchNumber: Int) {
        val patch  = patchDao.getPatch(patchNumber) ?: return
        val cropId = patch.cropType ?: return
        val crop   = gameData.crops[cropId] ?: return

        val player   = playerRepo.getOrCreatePlayer()
        val equipped: Map<String, String?> = json.decodeFromString(player.equipped)

        val hoeBonus    = equipped[EquipSlot.HOE]?.let { gameData.equipment[it]?.farmingEfficiency } ?: 0f
        val capedDouble = equipped[EquipSlot.CAPE] == "farming_cape"

        var yield = Random.nextInt(crop.yieldMin, crop.yieldMax + 1)
        yield = (yield * (1f + hoeBonus)).roundToInt()
        if (capedDouble) yield *= 2

        val seedReturned = Random.nextDouble() < 0.20
        val items = buildMap<String, Int> {
            put(crop.id, yield)
            if (seedReturned) put(crop.seedName, 1)
        }

        playerRepo.applySessionResults(
            skillName   = Skills.FARMING,
            xpGained    = crop.harvestXp.toLong() * yield,
            itemsGained = items,
        )

        cancelAlarm(patchNumber)
        patchDao.clear(patchNumber)
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
