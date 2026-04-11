package com.fantasyidler.data.db.dao

import androidx.room.*
import com.fantasyidler.data.model.Player
import kotlinx.coroutines.flow.Flow

@Dao
interface PlayerDao {
    @Query("SELECT * FROM players WHERE id = 1")
    suspend fun getPlayer(): Player?

    @Query("SELECT * FROM players WHERE id = 1")
    fun observePlayer(): Flow<Player?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(player: Player)
}
