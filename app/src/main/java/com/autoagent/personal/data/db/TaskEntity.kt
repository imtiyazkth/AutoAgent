package com.autoagent.personal.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String = "",
    val description: String = "",
    val triggerType: String = "MANUAL",
    val triggerTime: String? = null,
    val triggerDays: String = "[]",
    val intervalMinutes: Int = 0,
    val stepsJson: String = "[]",
    val networkPolicy: String = "WIFI_PREFERRED",
    val mobileDataAllowed: Boolean = false,
    val isEnabled: Boolean = true,
    val requiresConfirmation: Boolean = false,
    val priority: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val lastRunAt: Long? = null,
    val lastRunStatus: String? = null,
    val totalRuns: Int = 0,
    val successRuns: Int = 0
)
