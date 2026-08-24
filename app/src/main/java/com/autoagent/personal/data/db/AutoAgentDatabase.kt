package com.autoagent.personal.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TaskEntity::class, ExecutionLogEntity::class, PinEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AutoAgentDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun logDao(): ExecutionLogDao
    abstract fun pinDao(): PinDao

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
    }
}
