package com.autoagent.personal.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ExecutionLogDao {
    @Query("SELECT * FROM execution_logs ORDER BY startTime DESC LIMIT 50")
    fun observeRecent(): Flow<List<ExecutionLogEntity>>

    @Query("SELECT * FROM execution_logs WHERE taskId = :taskId ORDER BY startTime DESC")
    suspend fun getForTask(taskId: Long): List<ExecutionLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: ExecutionLogEntity): Long

    @Query("""
        UPDATE execution_logs SET status = :status, stepsCompleted = :stepsCompleted,
        failureReason = :failureReason, endTime = :endTime WHERE id = :logId
    """)
    suspend fun updateStatus(
        logId: Long,
        status: String,
        stepsCompleted: Int,
        failureReason: String?,
        endTime: Long = System.currentTimeMillis()
    )

    @Query("DELETE FROM execution_logs")
    suspend fun deleteAll()
}
