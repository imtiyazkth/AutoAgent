package com.autoagent.data.db

import androidx.room.*
import com.autoagent.domain.model.RunStatus
import kotlinx.coroutines.flow.Flow

// =============================================
// DATABASE
// =============================================
@Database(
    entities = [TaskEntity::class, ExecutionLogEntity::class, PinEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AutoAgentDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun logDao(): ExecutionLogDao
    abstract fun pinDao(): PinDao
}

// =============================================
// TASK ENTITY
// =============================================
@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String,
    val triggerType: String,
    val triggerTime: String?,
    val triggerDays: String,          // JSON "[0,1,2]"
    val intervalMinutes: Int,
    val stepsJson: String,            // Full steps as JSON
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

// =============================================
// EXECUTION LOG ENTITY
// =============================================
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
    val stepLogsJson: String          // JSON array of step results
)

@Dao
interface ExecutionLogDao {
    @Query("SELECT * FROM execution_logs ORDER BY startTime DESC LIMIT 200")
    fun getRecentLogs(): Flow<List<ExecutionLogEntity>>

    @Query("SELECT * FROM execution_logs WHERE taskId = :taskId ORDER BY startTime DESC LIMIT 50")
    fun getLogsForTask(taskId: Long): Flow<List<ExecutionLogEntity>>

    @Query("SELECT * FROM execution_logs WHERE status = 'FAILED' ORDER BY startTime DESC LIMIT 50")
    fun getFailedLogs(): Flow<List<ExecutionLogEntity>>

    @Insert
    suspend fun insert(log: ExecutionLogEntity): Long

    @Query("UPDATE execution_logs SET endTime = :endTime, status = :status, stepsCompleted = :steps, failureReason = :reason WHERE id = :id")
    suspend fun updateResult(id: Long, endTime: Long, status: String, steps: Int, reason: String?)

    @Query("DELETE FROM execution_logs WHERE startTime < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)

    @Query("SELECT COUNT(*) FROM execution_logs WHERE status = 'SUCCESS'")
    suspend fun getSuccessCount(): Int

    @Query("SELECT COUNT(*) FROM execution_logs WHERE status = 'FAILED'")
    suspend fun getFailCount(): Int
}

// =============================================
// PIN ENTITY — stores SHA-256 hash only
// =============================================
@Entity(tableName = "pin_config")
data class PinEntity(
    @PrimaryKey val id: Int = 1,
    val pinHash: String,              // SHA-256 of 10-digit PIN
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
