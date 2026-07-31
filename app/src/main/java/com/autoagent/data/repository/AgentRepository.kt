package com.autoagent.data.repository

import com.autoagent.data.db.*
import com.autoagent.domain.model.RunStatus
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AgentRepository @Inject constructor(
    private val taskDao: TaskDao,
    private val logDao: ExecutionLogDao
) {
    fun getAllTasks(): Flow<List<TaskEntity>> = taskDao.getAllTasks()
    fun getEnabledTasks(): Flow<List<TaskEntity>> = taskDao.getEnabledTasks()
    suspend fun getTask(id: Long): TaskEntity? = taskDao.getTask(id)
    suspend fun saveTask(task: TaskEntity): Long = taskDao.upsert(task)
    suspend fun deleteTask(id: Long) = taskDao.delete(id)
    suspend fun setTaskEnabled(id: Long, enabled: Boolean) = taskDao.setEnabled(id, enabled)
    suspend fun updateTaskLastRun(id: Long, time: Long, status: RunStatus) {
        taskDao.updateLastRun(id, time, status.name)
        if (status == RunStatus.SUCCESS) taskDao.incrementSuccess(id)
    }

    fun getRecentLogs(): Flow<List<ExecutionLogEntity>> = logDao.getRecentLogs()
    fun getLogsForTask(taskId: Long): Flow<List<ExecutionLogEntity>> = logDao.getLogsForTask(taskId)
    suspend fun saveLog(log: ExecutionLogEntity): Long = logDao.insert(log)
    suspend fun updateLog(logId: Long, status: RunStatus, stepsCompleted: Int, failureReason: String?) {
        logDao.updateResult(logId, System.currentTimeMillis(), status.name, stepsCompleted, failureReason)
    }
}
