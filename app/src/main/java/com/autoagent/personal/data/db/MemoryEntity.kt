package com.autoagent.personal.data.db

import androidx.room.Entity

@Entity(tableName = "memory", primaryKeys = ["category", "key"])
data class MemoryEntity(
    val category: String = "",
    val key: String = "",
    val value: String = "",
    val usageCount: Int = 0,
    val lastUsed: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
)
