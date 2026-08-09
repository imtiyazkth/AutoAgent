package com.autoagent.personal.di

import android.content.Context
import androidx.room.Room
import com.autoagent.personal.data.db.*
import com.autoagent.personal.data.db.AutoAgentDatabase.Companion.MIGRATION_1_2
import com.autoagent.personal.data.db.AutoAgentDatabase.Companion.MIGRATION_2_3
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AutoAgentDatabase {
        return Room.databaseBuilder(
            context,
            AutoAgentDatabase::class.java,
            "autoagent_db"
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideTaskDao(db: AutoAgentDatabase): TaskDao = db.taskDao()

    @Provides
    @Singleton
    fun provideLogDao(db: AutoAgentDatabase): ExecutionLogDao = db.logDao()

    @Provides
    @Singleton
    fun providePinDao(db: AutoAgentDatabase): PinDao = db.pinDao()

    @Provides
    @Singleton
    fun provideAppCacheDao(db: AutoAgentDatabase): AppCacheDao = db.appCacheDao()

    @Provides
    @Singleton
    fun provideMemoryDao(db: AutoAgentDatabase): MemoryDao = db.memoryDao()
}
