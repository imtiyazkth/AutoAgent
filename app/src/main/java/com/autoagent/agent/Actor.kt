package com.autoagent.personal.agent

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
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
                    svc.startActivity(i)
                    delay(3000)
                    true
                } catch (e: Exception) { Log.e(TAG, "Launch: ${e.message}"); false }
            }

            is Action.Tap       -> { delay(400); svc.tapText(action.text) }

            // Smart search bar tap — tries text first, then coordinate tap at top of screen
            is Action.TapSearchBar -> {
                delay(500)
                val searchTexts = listOf("Search YouTube", "Search", "Search...")
                val found = searchTexts.any { svc.tapText(it) }
                if (!found) {
                    // YouTube search bar is always near top — tap coordinate
                    val dm = svc.resources.displayMetrics
                    val w = dm.widthPixels.toFloat()
                    val h = dm.heightPixels.toFloat()
                    // Search bar is roughly at 50% width, 12% from top
                    tapCoordinate(svc, w * 0.5f, h * 0.12f)
                } else true
            }

            is Action.TapFirstResult -> {
                delay(600)
                // First result in YouTube is roughly at 50% width, 35% from top after search
                val dm = svc.resources.displayMetrics
                val w = dm.widthPixels.toFloat()
                val h = dm.heightPixels.toFloat()
                tapCoordinate(svc, w * 0.5f, h * 0.35f)
            }

            is Action.Type      -> { delay(300); svc.typeText(action.text) }

            is Action.SearchKey -> {
                // Try IME search action first
                val result = svc.pressSearchKey()
                if (!result) {
                    // Fallback: tap search/go button coordinate (bottom right of keyboard)
                    val dm = svc.resources.displayMetrics
                    val w = dm.widthPixels.toFloat()
                    val h = dm.heightPixels.toFloat()
                    tapCoordinate(svc, w * 0.92f, h * 0.88f)
                } else result
            }

            is Action.Scroll    -> { svc.scrollDown(); delay(700); true }

            is Action.Wait      -> { delay(action.ms); true }

            is Action.Home      -> {
                svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME); true
            }

            is Action.Back      -> {
                svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK); true
            }
        }
    }

    private fun tapCoordinate(svc: AutoAgentAccessibilityService, x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 80))
            .build()
        return svc.dispatchGesture(gesture, null, null)
    }
}
