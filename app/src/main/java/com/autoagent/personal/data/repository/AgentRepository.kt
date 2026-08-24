package com.autoagent.personal.data.repository

import com.autoagent.personal.data.db.ExecutionLogDao
import com.autoagent.personal.data.db.ExecutionLogEntity
import com.autoagent.personal.data.db.TaskDao
import com.autoagent.personal.data.db.TaskEntity
import com.autoagent.personal.domain.model.RunStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AgentRepository @Inject constructor(
    private val taskDao: TaskDao,
    private val logDao: ExecutionLogDao
) {
    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val tasks: StateFlow<List<TaskEntity>> = taskDao.observeAll()
        .stateIn(repoScope, SharingStarted.Eagerly, emptyList())

    val recentLogs: StateFlow<List<ExecutionLogEntity>> = logDao.observeRecent()
        .stateIn(repoScope, SharingStarted.Eagerly, emptyList())

    suspend fun getTask(id: Long): TaskEntity? = taskDao.getById(id)

    suspend fun saveTask(task: TaskEntity): Long = taskDao.insert(task)

    suspend fun deleteTask(id: Long) = taskDao.deleteById(id)

    suspend fun setTaskEnabled(id: Long, enabled: Boolean) = taskDao.setEnabled(id, enabled)

    suspend fun updateTaskLastRun(id: Long, time: Long, status: RunStatus) =
        taskDao.updateLastRun(id, time, status.name)

    suspend fun saveLog(log: ExecutionLogEntity): Long = logDao.insert(log)

    suspend fun updateLog(
        logId: Long,
        status: RunStatus,
        stepsCompleted: Int,
        failureReason: String?
    ) = logDao.updateStatus(logId, status.name, stepsCompleted, failureReason)

    suspend fun getLogsForTask(taskId: Long): List<ExecutionLogEntity> =
        logDao.getForTask(taskId)
}
