package com.autoagent.util

import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import com.autoagent.service.accessibility.AutoAgentAccessibilityService

const val ACCESSIBILITY_COMPONENT =
    "com.autoagent.personal/com.autoagent.service.accessibility.AutoAgentAccessibilityService"

fun isAccessibilityEnabled(context: Context): Boolean {
    return try {
        val services = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(services)
        while (splitter.hasNext()) {
            if (splitter.next().equals(ACCESSIBILITY_COMPONENT, ignoreCase = true)) return true
        }
        services.contains(context.packageName, ignoreCase = true)
    } catch (e: Exception) { false }
}
