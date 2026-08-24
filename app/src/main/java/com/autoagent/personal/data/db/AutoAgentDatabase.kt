package com.autoagent.personal.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TaskEntity::class,
        ExecutionLogEntity::class,
        PinEntity::class,
        MemoryEntity::class,
        AppCacheEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AutoAgentDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun logDao(): ExecutionLogDao
    abstract fun pinDao(): PinDao
    abstract fun memoryDao(): MemoryDao
    abstract fun appCacheDao(): AppCacheDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS execution_logs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        taskId INTEGER NOT NULL,
                        taskName TEXT NOT NULL,
                        startTime INTEGER NOT NULL,
                        endTime INTEGER,
                        status TEXT NOT NULL,
                        stepsCompleted INTEGER NOT NULL,
                        totalSteps INTEGER NOT NULL,
                        failureReason TEXT,
                        networkUsed TEXT,
                        stepLogsJson TEXT NOT NULL
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS pin (
                        id INTEGER PRIMARY KEY NOT NULL,
                        pinHash TEXT NOT NULL,
                        setupComplete INTEGER NOT NULL,
                        biometricEnabled INTEGER NOT NULL,
                        lastChangedAt INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS memory (
                        category TEXT NOT NULL,
                        key TEXT NOT NULL,
                        value TEXT NOT NULL,
                        usageCount INTEGER NOT NULL,
                        lastUsed INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        PRIMARY KEY(category, key)
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS app_cache (
                        packageName TEXT NOT NULL PRIMARY KEY,
                        appName TEXT NOT NULL,
                        versionName TEXT NOT NULL,
                        installDate INTEGER NOT NULL,
                        lastUpdated INTEGER NOT NULL,
                        category TEXT NOT NULL,
                        launchActivity TEXT,
                        canLaunch INTEGER NOT NULL,
                        scannedAt INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }
    }
}
