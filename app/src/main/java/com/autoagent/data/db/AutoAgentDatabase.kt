package com.autoagent.personal.data.db

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Database(
    entities = [
        TaskEntity::class,
        ExecutionLogEntity::class,
        PinEntity::class,
        AppCacheEntity::class,
        MemoryEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AutoAgentDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun logDao(): ExecutionLogDao
    abstract fun pinDao(): PinDao
    abstract fun appCacheDao(): AppCacheDao
    abstract fun memoryDao(): MemoryDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS app_cache (
                        packageName TEXT PRIMARY KEY NOT NULL,
                        appName TEXT NOT NULL,
                        versionName TEXT NOT NULL,
                        category TEXT NOT NULL,
                        canLaunch INTEGER NOT NULL DEFAULT 1,
                        installDate INTEGER NOT NULL DEFAULT 0,
                        lastUpdated INTEGER NOT NULL DEFAULT 0,
                        launchActivity TEXT,
                        scannedAt INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS memory (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        category TEXT NOT NULL,
                        key TEXT NOT NULL,
                        value TEXT NOT NULL,
                        usageCount INTEGER NOT NULL DEFAULT 0,
                        lastUsed INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE UNIQUE INDEX IF NOT EXISTS index_memory_category_key
                    ON memory(category, key)
                """.trimIndent())
            }
        }
    }
}

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String,
    val triggerType: String,
    val triggerTime: String?,
    val triggerDays: String,
    val intervalMinutes: Int,
    val stepsJson: String,
    val networkPolicy: String,
    val mobileDataAllowed: Boolean,
    val isEnabled: Boolean,
    val requiresConfirmation: Boolean,
    val priority: Int,
    val createdAt: Long,
    val lastRunAt: Long?,
    val lastRunStatus: String?,
    val totalRuns: Int,
    val successRuns: Int
)

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY priority DESC, createdAt DESC")
    fun getAllTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE isEnabled = 1")
    fun getEnabledTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTask(id: Long): TaskEntity?

    @Upsert
    suspend fun upsert(task: TaskEntity): Long

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE tasks SET isEnabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE tasks SET lastRunAt = :time, lastRunStatus = :status, totalRuns = totalRuns + 1 WHERE id = :id")
    suspend fun updateLastRun(id: Long, time: Long, status: String)

    @Query("UPDATE tasks SET successRuns = successRuns + 1 WHERE id = :id")
    suspend fun incrementSuccess(id: Long)

    @Query("SELECT COUNT(*) FROM tasks")
    suspend fun getCount(): Int
}

@Entity(tableName = "execution_logs")
data class ExecutionLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val taskName: String,
    val startTime: Long,
    val endTime: Long?,
    val status: String,
    val stepsCompleted: Int,
    val totalSteps: Int,
    val failureReason: String?,
    val networkUsed: String?,
    val stepLogsJson: String
)

@Dao
interface ExecutionLogDao {
    @Query("SELECT * FROM execution_logs ORDER BY startTime DESC LIMIT 200")
    fun getRecentLogs(): Flow<List<ExecutionLogEntity>>

    @Query("SELECT * FROM execution_logs WHERE taskId = :taskId ORDER BY startTime DESC LIMIT 50")
    fun getLogsForTask(taskId: Long): Flow<List<ExecutionLogEntity>>

    @Insert
    suspend fun insert(log: ExecutionLogEntity): Long

    @Query("UPDATE execution_logs SET endTime = :endTime, status = :status, stepsCompleted = :steps, failureReason = :reason WHERE id = :id")
    suspend fun updateResult(id: Long, endTime: Long, status: String, steps: Int, reason: String?)

    @Query("SELECT COUNT(*) FROM execution_logs WHERE status = 'SUCCESS'")
    suspend fun getSuccessCount(): Int
}

@Entity(tableName = "pin_config")
data class PinEntity(
    @PrimaryKey val id: Int = 1,
    val pinHash: String,
    val biometricEnabled: Boolean = false,
    val setupComplete: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val lastChangedAt: Long = System.currentTimeMillis()
)

@Dao
interface PinDao {
    @Query("SELECT * FROM pin_config WHERE id = 1")
    suspend fun getPin(): PinEntity?

    @Upsert
    suspend fun savePin(pin: PinEntity)

    @Query("UPDATE pin_config SET biometricEnabled = :enabled WHERE id = 1")
    suspend fun setBiometric(enabled: Boolean)
}
