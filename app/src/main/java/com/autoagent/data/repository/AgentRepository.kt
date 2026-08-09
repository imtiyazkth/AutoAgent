package com.autoagent.personal.data.repository

import com.autoagent.personal.data.db.*
import com.autoagent.personal.domain.model.RunStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AgentRepository @Inject constructor(
    private val taskDao: TaskDao,
    private val logDao: ExecutionLogDao,
    private val memoryDao: MemoryDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val tasks: StateFlow<List<TaskEntity>> = taskDao.getAllTasks()
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val recentLogs: StateFlow<List<ExecutionLogEntity>> = logDao.getRecentLogs()
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    suspend fun getTask(id: Long): TaskEntity? = taskDao.getTask(id)
    suspend fun saveTask(task: TaskEntity): Long = taskDao.upsert(task)
    suspend fun deleteTask(id: Long) = taskDao.delete(id)
    suspend fun setTaskEnabled(id: Long, enabled: Boolean) = taskDao.setEnabled(id, enabled)

    suspend fun updateTaskLastRun(id: Long, time: Long, status: RunStatus) {
        taskDao.updateLastRun(id, time, status.name)
        if (status == RunStatus.SUCCESS) taskDao.incrementSuccess(id)
    }

    suspend fun saveLog(log: ExecutionLogEntity): Long = logDao.insert(log)

    suspend fun updateLog(
        logId: Long,
        status: RunStatus,
        stepsCompleted: Int,
        failureReason: String? = null
    ) {
        logDao.updateResult(
            id = logId,
            endTime = System.currentTimeMillis(),
            status = status.name,
            steps = stepsCompleted,
            reason = failureReason
        )
    }
}
