package com.autoagent.personal.util

import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import com.autoagent.personal.service.accessibility.AutoAgentAccessibilityService

fun isAccessibilityEnabled(context: Context): Boolean {
    return try {
        val component = "${context.packageName}/" +
            "com.autoagent.personal.service.accessibility.AutoAgentAccessibilityService"
        val services = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(services)
        while (splitter.hasNext()) {
            if (splitter.next().equals(component, ignoreCase = true)) return true
        }
        services.contains(context.packageName, ignoreCase = true)
    } catch (e: Exception) { false }
}
