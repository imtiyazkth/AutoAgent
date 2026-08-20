package com.autoagent.personal.data.repository

import com.autoagent.personal.data.db.ExecutionLogDao
import com.autoagent.personal.data.db.TaskDao
import com.autoagent.personal.data.db.TaskEntity
import com.autoagent.personal.data.domain.model.RunStatus
import com.autoagent.personal.data.util.GsonHelper
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRepository @Inject constructor(
    private val taskDao: TaskDao,
    private val executionLogDao: ExecutionLogDao,
    private val gsonHelper: GsonHelper
) {
    suspend fun getAllTasks(): List<TaskEntity> = taskDao.getAllTasks()

    suspend fun getTask(id: Long): TaskEntity? = taskDao.getTaskById(id)

    suspend fun saveTask(entity: TaskEntity): Long = taskDao.insertTask(entity)

    suspend fun deleteTask(entity: TaskEntity) = taskDao.deleteTask(entity)

    suspend fun updateTaskLastRun(id: Long, timestamp: Long, status: RunStatus) {
        taskDao.updateLastRun(id, status.name, timestamp)
    }
}
