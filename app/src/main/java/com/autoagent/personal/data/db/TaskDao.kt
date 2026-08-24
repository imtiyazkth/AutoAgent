package com.autoagent.personal.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY priority DESC, createdAt DESC")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: Long): TaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity): Long

    @Update
    suspend fun update(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE tasks SET isEnabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("""
        UPDATE tasks SET lastRunAt = :time, lastRunStatus = :status,
        totalRuns = totalRuns + 1,
        successRuns = successRuns + CASE WHEN :status = 'SUCCESS' THEN 1 ELSE 0 END
        WHERE id = :id
    """)
    suspend fun updateLastRun(id: Long, time: Long, status: String)

    @Query("DELETE FROM tasks")
    suspend fun deleteAll()
}
