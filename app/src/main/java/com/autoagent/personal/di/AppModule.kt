package com.autoagent.personal.di

import android.content.Context
import androidx.room.Room
import com.autoagent.personal.data.db.AppDatabase
import com.autoagent.personal.data.db.ExecutionLogDao
import com.autoagent.personal.data.db.TaskDao
import com.autoagent.personal.data.repository.TaskRepository
import com.autoagent.personal.data.util.GsonHelper
import com.autoagent.personal.memory.MemoryEngine
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * AppModule — Hilt dependency graph root
 *
 * RULES:
 * 1. Only @Singleton-scoped objects go here.
 * 2. PhoneControlEngine is NOT provided here. It requires the
 *    AccessibilityService instance which is not a Hilt-managed object.
 *    It is constructed on-demand by AgentController when needed.
 * 3. AgentController is NOT provided here. It is instantiated inside
 *    VoiceAgentViewModel and DashboardViewModel with manual construction.
 *    This avoids the Hilt circular dependency that crashed builds 81–87.
 *
 * WHY: Hilt cannot inject AccessibilityService instances because the
 * service lifecycle is managed by the Android system, not by Hilt.
 * Attempting to @Inject a class that depends on AccessibilityService
 * causes "NonExistentClass" at KSP generation time.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // ─── Database ─────────────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "autoagent_database"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideTaskDao(db: AppDatabase): TaskDao = db.taskDao()

    @Provides
    @Singleton
    fun provideExecutionLogDao(db: AppDatabase): ExecutionLogDao = db.executionLogDao()

    // ─── Repository ───────────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideTaskRepository(
        taskDao: TaskDao,
        executionLogDao: ExecutionLogDao,
        gsonHelper: GsonHelper
    ): TaskRepository = TaskRepository(taskDao, executionLogDao, gsonHelper)

    // ─── Utilities ────────────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()

    @Provides
    @Singleton
    fun provideGsonHelper(gson: Gson): GsonHelper = GsonHelper(gson)

    @Provides
    @Singleton
    fun provideMemoryEngine(@ApplicationContext context: Context): MemoryEngine =
        MemoryEngine(context)
}
