package com.autoagent.personal.data.util

import com.autoagent.personal.data.db.TaskEntity
import com.autoagent.personal.data.domain.model.Task
import com.google.gson.Gson
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GsonHelper @Inject constructor(private val gson: Gson) {
    fun toJson(obj: Any): String = gson.toJson(obj)
    fun <T> fromJson(json: String, clazz: Class<T>): T = gson.fromJson(json, clazz)
    fun entityToTask(entity: TaskEntity): Task =
        gson.fromJson(entity.taskJson, Task::class.java) ?: Task(
            id = entity.id,
            name = entity.name,
            appPackage = entity.appPackage
        )
}
