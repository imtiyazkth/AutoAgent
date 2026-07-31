package com.autoagent.domain.model

import android.graphics.drawable.Drawable

data class InstalledAppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val versionName: String,
    val installDate: Long,
    val lastUpdated: Long,
    val canLaunch: Boolean,
    val category: String,
    val launchActivity: String?
)

data class AppConnector(
    val id: Long = 0,
    val packageName: String,
    val appName: String,
    val savedTemplates: List<String> = emptyList(),
    val defaultUrl: String? = null,
    val defaultMessage: String? = null,
    val lastUsed: Long? = null
)
