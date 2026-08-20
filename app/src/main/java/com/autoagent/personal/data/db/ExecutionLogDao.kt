package com.autoagent.personal.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ExecutionLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: ExecutionLogEntity)

    @Query("SELECT * FROM execution_logs WHERE taskId = :taskId ORDER BY timestamp DESC")
    suspend fun getLogsForTask(taskId: Long): List<ExecutionLogEntity>

    @Query("DELETE FROM execution_logs")
    suspend fun deleteAll()
}
