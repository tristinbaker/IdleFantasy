package com.fantasyidler.di

import android.content.Context
import androidx.room.Room
import com.fantasyidler.data.db.AppDatabase
import com.fantasyidler.data.db.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "fantasy_idler.db")
            .build()

    @Provides fun providePlayerDao(db: AppDatabase): PlayerDao = db.playerDao()
    @Provides fun provideSkillSessionDao(db: AppDatabase): SkillSessionDao = db.skillSessionDao()
    @Provides fun provideQuestProgressDao(db: AppDatabase): QuestProgressDao = db.questProgressDao()
    @Provides fun provideFarmingPatchDao(db: AppDatabase): FarmingPatchDao = db.farmingPatchDao()
    @Provides fun provideGlobalStateDao(db: AppDatabase): GlobalStateDao = db.globalStateDao()
    @Provides fun provideArenaRecordDao(db: AppDatabase): ArenaRecordDao = db.arenaRecordDao()
}
