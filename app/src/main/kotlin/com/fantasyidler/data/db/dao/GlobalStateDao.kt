package com.fantasyidler.data.db.dao

import androidx.room.*
import com.fantasyidler.data.model.GlobalState

@Dao
interface GlobalStateDao {
    @Query("SELECT value FROM global_state WHERE key = :key")
    suspend fun getValue(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setValue(state: GlobalState)

    @Query("DELETE FROM global_state WHERE key = :key")
    suspend fun delete(key: String)
}
