package com.autoagent.util

import com.autoagent.data.db.TaskEntity
import com.autoagent.domain.model.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GsonHelper @Inject constructor() {
    private val gson = Gson()

    fun entityToTask(entity: TaskEntity): AgentTask {
        val steps: List<TaskStep> = try {
            val type = object : TypeToken<List<TaskStep>>() {}.type
            gson.fromJson(entity.stepsJson, type) ?: emptyList()
        } catch (e: Exception) { emptyList() }

        val days: List<Int> = try {
            val type = object : TypeToken<List<Int>>() {}.type
            gson.fromJson(entity.triggerDays, type) ?: emptyList()
        } catch (e: Exception) { emptyList() }

        return AgentTask(
            id = entity.id,
            name = entity.name,
            description = entity.description,
            triggerType = try { TriggerType.valueOf(entity.triggerType) } catch (e: Exception) { TriggerType.MANUAL },
            triggerTime = entity.triggerTime,
            triggerDays = days,
            intervalMinutes = entity.intervalMinutes,
            steps = steps,
            networkPolicy = try { NetworkPolicy.valueOf(entity.networkPolicy) } catch (e: Exception) { NetworkPolicy.WIFI_PREFERRED },
            mobileDataAllowed = entity.mobileDataAllowed,
            isEnabled = entity.isEnabled,
            requiresConfirmation = entity.requiresConfirmation,
            priority = entity.priority,
            createdAt = entity.createdAt,
            lastRunAt = entity.lastRunAt,
            lastRunStatus = entity.lastRunStatus?.let { try { RunStatus.valueOf(it) } catch (e: Exception) { null } },
            totalRuns = entity.totalRuns,
            successRuns = entity.successRuns
        )
    }

    fun taskToEntity(task: AgentTask): TaskEntity {
        return TaskEntity(
            id = task.id,
            name = task.name,
            description = task.description,
            triggerType = task.triggerType.name,
            triggerTime = task.triggerTime,
            triggerDays = gson.toJson(task.triggerDays),
            intervalMinutes = task.intervalMinutes,
            stepsJson = gson.toJson(task.steps),
            networkPolicy = task.networkPolicy.name,
            mobileDataAllowed = task.mobileDataAllowed,
            isEnabled = task.isEnabled,
            requiresConfirmation = task.requiresConfirmation,
            priority = task.priority,
            createdAt = task.createdAt,
            lastRunAt = task.lastRunAt,
            lastRunStatus = task.lastRunStatus?.name,
            totalRuns = task.totalRuns,
            successRuns = task.successRuns
        )
    }

    fun stepsToJson(steps: List<TaskStep>): String = gson.toJson(steps)
    fun jsonToSteps(json: String): List<TaskStep> {
        val type = object : TypeToken<List<TaskStep>>() {}.type
        return try { gson.fromJson(json, type) ?: emptyList() } catch (e: Exception) { emptyList() }
    }
}
