package com.autoagent.personal.domain.model

data class InstalledAppInfo(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val installDate: Long,
    val lastUpdated: Long,
    val canLaunch: Boolean,
    val category: String,
    val launchActivity: String?
)
