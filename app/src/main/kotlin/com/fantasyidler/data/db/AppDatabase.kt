package com.fantasyidler.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.fantasyidler.data.db.dao.*
import com.fantasyidler.data.model.*

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE skill_sessions ADD COLUMN is_worker_session INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE skill_sessions ADD COLUMN efficiency_multiplier REAL NOT NULL DEFAULT 1.0")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE skill_sessions ADD COLUMN worker_slot INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE skill_sessions SET worker_slot = 1 WHERE is_worker_session = 1")
    }
}

@Database(
    entities = [
        Player::class,
        SkillSession::class,
        QuestProgress::class,
        FarmingPatch::class,
        GlobalState::class,
        ArenaRecord::class,
    ],
    version = 3,
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
