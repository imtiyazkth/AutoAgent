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
                    delay(3000)
                    true
                } catch (e: Exception) { Log.e(TAG, "Launch: ${e.message}"); false }
            }

            is Action.Tap -> { delay(400); svc.tapText(action.text) }

            is Action.TapSearchBar -> {
                delay(500)
                val dm = svc.resources.displayMetrics
                val w = dm.widthPixels.toFloat()
                val h = dm.heightPixels.toFloat()

                // App-specific search bar texts
                val hint = action.appHint.lowercase()
                val candidates = when {
                    hint.contains("whatsapp") -> listOf("Search…", "Search", "Search or start new chat")
                    hint.contains("telegram") -> listOf("Search", "Search for chats")
                    hint.contains("chrome")   -> listOf("Search or type URL", "Search Google or type a URL")
                    hint.contains("spotify")  -> listOf("Search", "Artists, songs, or podcasts")
                    hint.contains("instagram")-> listOf("Search", "Search")
                    else -> listOf("Search YouTube", "Search", "Search…", "Search or type URL")
                }

                // Try text-based tap first
                val found = candidates.any { svc.tapText(it) }
                if (found) {
                    delay(600)
                    true
                } else {
                    // Coordinate fallback — search bar is always near top
                    val yPos = when {
                        hint.contains("youtube") -> h * 0.085f  // YouTube search is very top
                        hint.contains("whatsapp") -> h * 0.075f
                        else -> h * 0.10f
                    }
                    tapCoord(svc, w * 0.5f, yPos)
                    delay(600)
                    true
                }
            }

            is Action.TapFirstResult -> {
                delay(500)
                val dm = svc.resources.displayMetrics
                val w = dm.widthPixels.toFloat()
                val h = dm.heightPixels.toFloat()
                // First video result in YouTube after search is ~30-35% from top
                tapCoord(svc, w * 0.5f, h * 0.32f)
            }

            is Action.Type -> { delay(300); svc.typeText(action.text) }

            is Action.SearchKey -> {
                val ok = svc.pressSearchKey()
                if (!ok) {
                    // Tap the blue search button on keyboard (bottom right)
                    val dm = svc.resources.displayMetrics
                    tapCoord(svc, dm.widthPixels * 0.92f, dm.heightPixels * 0.875f)
                } else ok
            }

            is Action.Scroll  -> { svc.scrollDown(); delay(700); true }
            is Action.Wait    -> { delay(action.ms); true }
            is Action.Home    -> { svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME); true }
            is Action.Back    -> { svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK); true }
        }
    }

    private fun tapCoord(svc: AutoAgentAccessibilityService, x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()
        return svc.dispatchGesture(g, null, null)
    }
}
