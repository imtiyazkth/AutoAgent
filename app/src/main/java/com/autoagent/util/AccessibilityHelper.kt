package com.autoagent.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.TextUtils
import com.autoagent.service.accessibility.AutoAgentAccessibilityService

object AccessibilityHelper {

    fun isAccessibilityEnabled(context: Context): Boolean {
        return try {
            val expectedComponent = ComponentName(
                context.packageName,
                AutoAgentAccessibilityService::class.java.name
            ).flattenToString()

            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(enabledServices)
            while (colonSplitter.hasNext()) {
                val componentName = colonSplitter.next()
                if (componentName.equals(expectedComponent, ignoreCase = true)) {
                    return true
                }
            }
            false
        } catch (e: Exception) { false }
    }

    // MIUI + Stock Android dono ke liye settings open karo
    fun openAccessibilitySettings(context: Context) {
        val intents = listOf(
            // Try 1: Direct service settings (best)
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            // Try 2: MIUI specific
            Intent("miui.intent.action.SETTINGS").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            // Try 3: Generic settings
            Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )

        for (intent in intents) {
            try {
                context.startActivity(intent)
                return
            } catch (e: Exception) { continue }
        }
    }
}
