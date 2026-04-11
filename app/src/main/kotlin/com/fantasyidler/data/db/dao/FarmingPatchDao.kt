package com.fantasyidler.data.db.dao

import androidx.room.*
import com.fantasyidler.data.model.FarmingPatch
import kotlinx.coroutines.flow.Flow

@Dao
interface FarmingPatchDao {
    @Query("SELECT * FROM farming_patches ORDER BY patch_number")
    suspend fun getAllPatches(): List<FarmingPatch>

    @Query("SELECT * FROM farming_patches ORDER BY patch_number")
    fun observeAllPatches(): Flow<List<FarmingPatch>>

    @Query("SELECT * FROM farming_patches WHERE patch_number = :patchNumber")
    suspend fun getPatch(patchNumber: Int): FarmingPatch?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(patch: FarmingPatch)

    @Query("UPDATE farming_patches SET crop_type = NULL, planted_at = NULL WHERE patch_number = :patchNumber")
    suspend fun clear(patchNumber: Int)

    @Query("UPDATE farming_patches SET crop_type = NULL, planted_at = NULL")
    suspend fun clearAll()
}
