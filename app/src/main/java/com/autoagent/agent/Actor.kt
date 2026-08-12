package com.autoagent.personal.agent

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.util.Log
import com.autoagent.personal.service.accessibility.AutoAgentAccessibilityService
import kotlinx.coroutines.delay

object Actor {
    private const val TAG = "Actor"

    suspend fun perform(action: Action, svc: AutoAgentAccessibilityService): Boolean {
        return when (action) {
            is Action.None   -> true

            is Action.Launch -> {
                try {
                    val i = svc.packageManager.getLaunchIntentForPackage(action.pkg) ?: return false
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    svc.startActivity(i)
                    delay(3500)
                    true
                } catch (e: Exception) { Log.e(TAG, "Launch: ${e.message}"); false }
            }

            is Action.Tap -> { delay(400); svc.tapText(action.text) }

            is Action.TapSearchBar -> {
                delay(600)
                val hint = action.appHint.lowercase()
                val dm = svc.resources.displayMetrics
                val w = dm.widthPixels.toFloat()
                val h = dm.heightPixels.toFloat()

                // Each app's search bar location and text
                val found = when {
                    hint.contains("youtube") -> {
                        // YouTube search icon is at top right ~88% width, 8% height
                        svc.tapText("Search YouTube") ||
                        tapCoord(svc, w * 0.88f, h * 0.07f)
                    }
                    hint.contains("whatsapp") -> {
                        svc.tapText("Search…") ||
                        svc.tapText("Search") ||
                        tapCoord(svc, w * 0.5f, h * 0.075f)
                    }
                    hint.contains("telegram") -> {
                        svc.tapText("Search") ||
                        tapCoord(svc, w * 0.5f, h * 0.075f)
                    }
                    hint.contains("chrome") -> {
                        svc.tapText("Search or type URL") ||
                        tapCoord(svc, w * 0.5f, h * 0.06f)
                    }
                    hint.contains("spotify") -> {
                        svc.tapText("Search") ||
                        tapCoord(svc, w * 0.5f, h * 0.10f)
                    }
                    hint.contains("instagram") -> {
                        tapCoord(svc, w * 0.5f, h * 0.93f) // bottom nav search
                    }
                    else -> {
                        svc.tapText("Search") ||
                        tapCoord(svc, w * 0.5f, h * 0.08f)
                    }
                }
                delay(700)
                true
            }

            is Action.TapFirstResult -> {
                delay(800)
                val dm = svc.resources.displayMetrics
                val w = dm.widthPixels.toFloat()
                val h = dm.heightPixels.toFloat()

                // Try to find result text in accessibility tree first
                val queryWords = action.query.split(" ").filter { it.length > 2 }
                val foundByText = queryWords.any { word ->
                    val screen = svc.getScreenState()
                    val match = screen.clickable.firstOrNull { it.contains(word, ignoreCase = true) }
                    if (match != null) {
                        svc.tapText(match)
                    } else false
                }

                if (!foundByText) {
                    // YouTube: first video result thumbnail is at ~28% from top
                    // WhatsApp: first contact result is at ~18% from top
                    tapCoord(svc, w * 0.5f, h * 0.28f)
                } else true
            }

            is Action.Type -> { delay(300); svc.typeText(action.text) }

            is Action.SearchKey -> {
                val ok = svc.pressSearchKey()
                if (!ok) {
                    // Tap blue search button on keyboard (bottom right)
                    val dm = svc.resources.displayMetrics
                    tapCoord(svc, dm.widthPixels * 0.92f, dm.heightPixels * 0.875f)
                } else ok
            }

            is Action.Scroll  -> { svc.scrollDown(); delay(800); true }
            is Action.Wait    -> { delay(action.ms); true }
            is Action.Home    -> { svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME); true }
            is Action.Back    -> { svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK); true }
        }
    }

    private fun tapCoord(svc: AutoAgentAccessibilityService, x: Float, y: Float): Boolean {
        return try {
            val path = Path().apply { moveTo(x, y) }
            val g = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
                .build()
            svc.dispatchGesture(g, null, null)
        } catch (e: Exception) { false }
    }
}
