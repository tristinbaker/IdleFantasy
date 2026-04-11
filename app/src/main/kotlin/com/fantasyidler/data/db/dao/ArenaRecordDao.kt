package com.fantasyidler.data.db.dao

import androidx.room.*
import com.fantasyidler.data.model.ArenaRecord

@Dao
interface ArenaRecordDao {
    @Query("SELECT * FROM arena_records ORDER BY completed_at DESC LIMIT :limit")
    suspend fun getRecentRecords(limit: Int = 20): List<ArenaRecord>

    @Query("SELECT COUNT(*) FROM arena_records WHERE won = 1")
    suspend fun getWinCount(): Int

    @Query("SELECT COUNT(*) FROM arena_records WHERE won = 0")
    suspend fun getLossCount(): Int

    @Insert
    suspend fun insert(record: ArenaRecord)
}
