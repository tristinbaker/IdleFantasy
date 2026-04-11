package com.fantasyidler.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.fantasyidler.data.db.dao.*
import com.fantasyidler.data.model.*

@Database(
    entities = [
        Player::class,
        SkillSession::class,
        QuestProgress::class,
        FarmingPatch::class,
        GlobalState::class,
        ArenaRecord::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playerDao(): PlayerDao
    abstract fun skillSessionDao(): SkillSessionDao
    abstract fun questProgressDao(): QuestProgressDao
    abstract fun farmingPatchDao(): FarmingPatchDao
    abstract fun globalStateDao(): GlobalStateDao
    abstract fun arenaRecordDao(): ArenaRecordDao
}
