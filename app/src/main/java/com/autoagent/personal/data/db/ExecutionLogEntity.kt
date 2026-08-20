package com.autoagent.personal.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "execution_logs")
data class ExecutionLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val taskId: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "",
    val message: String = "",
    val durationMs: Long = 0
)
