package com.autoagent.di

import android.content.Context
import androidx.room.Room
import com.autoagent.data.db.*
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
        return Room.databaseBuilder(context, AutoAgentDatabase::class.java, "autoagent_db")
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides fun provideTaskDao(db: AutoAgentDatabase): TaskDao = db.taskDao()
    @Provides fun provideLogDao(db: AutoAgentDatabase): ExecutionLogDao = db.logDao()
    @Provides fun providePinDao(db: AutoAgentDatabase): PinDao = db.pinDao()
}
