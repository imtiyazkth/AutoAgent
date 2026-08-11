package com.autoagent.personal.agent

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import com.autoagent.personal.service.accessibility.AutoAgentAccessibilityService
import kotlinx.coroutines.delay

object Actor {
    private const val TAG = "Actor"

    suspend fun perform(action: Action, svc: AutoAgentAccessibilityService): Boolean {
        return when (action) {
            is Action.None      -> true
            is Action.Launch    -> {
                try {
                    val i = svc.packageManager.getLaunchIntentForPackage(action.pkg) ?: return false
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    svc.startActivity(i); delay(2500); true
                } catch (e: Exception) { Log.e(TAG, "${e.message}"); false }
            }
            is Action.Tap       -> { delay(400); svc.tapText(action.text) }
            is Action.Type      -> { delay(300); svc.typeText(action.text) }
            is Action.SearchKey -> svc.pressSearchKey()
            is Action.Scroll    -> { svc.scrollDown(); delay(700); true }
            is Action.Wait      -> { delay(action.ms); true }
            is Action.Home      -> { svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME); true }
            is Action.Back      -> { svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK); true }
        }
    }
}
