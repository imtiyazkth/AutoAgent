package com.autoagent.personal.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "execution_logs")
data class ExecutionLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val taskId: Long = 0,
    val taskName: String = "",
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val status: String = "",
    val stepsCompleted: Int = 0,
    val totalSteps: Int = 0,
    val failureReason: String? = null,
    val networkUsed: String? = null,
    val stepLogsJson: String = "[]"
)
