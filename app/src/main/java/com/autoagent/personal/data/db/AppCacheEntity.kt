package com.autoagent.personal.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_cache")
data class AppCacheEntity(
    @PrimaryKey
    val packageName: String = "",
    val appName: String = "",
    val versionName: String = "?",
    val installDate: Long = 0,
    val lastUpdated: Long = 0,
    val category: String = "General",
    val launchActivity: String? = null,
    val cachedAt: Long = System.currentTimeMillis()
)
