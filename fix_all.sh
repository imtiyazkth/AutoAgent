#!/bin/bash
# Run from ~/AutoAgent: bash fix_all.sh

echo "🔧 Fixing all issues..."

# ═══════════════════════════════════════════════════════════════
# FIX 1: GoalPlanner — after search, tap FIRST result properly
# Uses accessibility tree text match, not coordinate
# ═══════════════════════════════════════════════════════════════
cat > app/src/main/java/com/autoagent/agent/GoalPlanner.kt << 'KT'
package com.autoagent.personal.agent

data class Plan(val steps: List<Step>, val appPkg: String)
data class Step(val intent: Intent, val target: String = "", val desc: String = "")

enum class Intent {
    LAUNCH_APP, TAP, TAP_SEARCH_BAR, TAP_FIRST_RESULT,
    TYPE, SEARCH_KEY, SCROLL, WAIT, HOME, BACK
}

object GoalPlanner {

    fun plan(raw: String): Plan {
        val g = raw.lowercase().trim()

        // ── YouTube ───────────────────────────────────────────────────────
        if (g.hasAny("youtube", "yt")) {
            val query = g.extractQuery("search","dhundo","play","chalao","bajao","lagao","suno","find")
                ?: g.extractArtist()
            val steps = mutableListOf<Step>()
            steps += Step(Intent.LAUNCH_APP, "com.google.android.youtube", "YouTube kholo")
            steps += Step(Intent.WAIT, "3000")
            if (query != null) {
                steps += Step(Intent.TAP_SEARCH_BAR, "", "Search bar tap")
                steps += Step(Intent.WAIT, "1200")
                steps += Step(Intent.TYPE, query, "Type: $query")
                steps += Step(Intent.WAIT, "600")
                steps += Step(Intent.SEARCH_KEY, "", "Search karo")
                steps += Step(Intent.WAIT, "3000", "Results aane do")
                if (g.hasAny("play","chalao","bajao","lagao","suno","dekhna","sunna","first","pehla")) {
                    steps += Step(Intent.TAP_FIRST_RESULT, query, "Pehla result play karo")
                }
            }
            return Plan(steps, "com.google.android.youtube")
        }

        // ── WhatsApp ──────────────────────────────────────────────────────
        if (g.hasAny("whatsapp","whats app","watsapp")) {
            val contact = g.extractContact()
            val message = g.extractMessage()
            val steps = mutableListOf<Step>()
            steps += Step(Intent.LAUNCH_APP, "com.whatsapp", "WhatsApp kholo")
            steps += Step(Intent.WAIT, "3000")
            if (contact != null) {
                steps += Step(Intent.TAP_SEARCH_BAR, "whatsapp", "Search tap")
                steps += Step(Intent.WAIT, "800")
                steps += Step(Intent.TYPE, contact, "Contact: $contact")
                steps += Step(Intent.WAIT, "1500")
                steps += Step(Intent.TAP_FIRST_RESULT, contact, "Contact tap")
                steps += Step(Intent.WAIT, "1500")
            }
            if (message != null) {
                steps += Step(Intent.TAP, "Type a message", "Message box tap")
                steps += Step(Intent.WAIT, "800")
                steps += Step(Intent.TYPE, message, "Message: $message")
                steps += Step(Intent.WAIT, "600")
                steps += Step(Intent.TAP, "Send", "Send karo")
            }
            return Plan(steps, "com.whatsapp")
        }

        // ── Telegram ──────────────────────────────────────────────────────
        if (g.hasAny("telegram")) {
            val contact = g.extractContact()
            val message = g.extractMessage()
            val steps = mutableListOf<Step>()
            steps += Step(Intent.LAUNCH_APP, "org.telegram.messenger", "Telegram kholo")
            steps += Step(Intent.WAIT, "3000")
            if (contact != null) {
                steps += Step(Intent.TAP_SEARCH_BAR, "telegram", "Search tap")
                steps += Step(Intent.TYPE, contact, "Contact: $contact")
                steps += Step(Intent.WAIT, "1500")
                steps += Step(Intent.TAP_FIRST_RESULT, contact, "Contact tap")
                steps += Step(Intent.WAIT, "1000")
            }
            if (message != null) {
                steps += Step(Intent.TYPE, message, "Message: $message")
                steps += Step(Intent.WAIT, "500")
                steps += Step(Intent.TAP, "Send", "Send")
            }
            return Plan(steps, "org.telegram.messenger")
        }

        // ── Instagram ─────────────────────────────────────────────────────
        if (g.hasAny("instagram","insta")) {
            val query = g.extractQuery("search","find","dhundo")
            val steps = mutableListOf<Step>()
            steps += Step(Intent.LAUNCH_APP, "com.instagram.android", "Instagram kholo")
            steps += Step(Intent.WAIT, "3000")
            if (query != null) {
                steps += Step(Intent.TAP_SEARCH_BAR, "instagram", "Search tap")
                steps += Step(Intent.TYPE, query, "Search: $query")
                steps += Step(Intent.SEARCH_KEY)
                steps += Step(Intent.WAIT, "2000")
                steps += Step(Intent.TAP_FIRST_RESULT, query, "Pehla result")
            }
            return Plan(steps, "com.instagram.android")
        }

        // ── Chrome / Google ───────────────────────────────────────────────
        if (g.hasAny("chrome","browser","google karo","search karo","google me")) {
            val query = g.extractQuery("search","google","find","dhundo") ?: g
            return Plan(listOf(
                Step(Intent.LAUNCH_APP, "com.android.chrome", "Chrome kholo"),
                Step(Intent.WAIT, "2500"),
                Step(Intent.TAP_SEARCH_BAR, "chrome", "Address bar tap"),
                Step(Intent.WAIT, "800"),
                Step(Intent.TYPE, query, "Search: $query"),
                Step(Intent.SEARCH_KEY)
            ), "com.android.chrome")
        }

        // ── Spotify ───────────────────────────────────────────────────────
        if (g.hasAny("spotify","music app")) {
            val query = g.extractQuery("play","search","suno","bajao","lagao") ?: g.extractArtist() ?: g
            return Plan(listOf(
                Step(Intent.LAUNCH_APP, "com.spotify.music", "Spotify kholo"),
                Step(Intent.WAIT, "2500"),
                Step(Intent.TAP_SEARCH_BAR, "spotify", "Search tap"),
                Step(Intent.TYPE, query, "Search: $query"),
                Step(Intent.SEARCH_KEY),
                Step(Intent.WAIT, "2000"),
                Step(Intent.TAP_FIRST_RESULT, query, "Play")
            ), "com.spotify.music")
        }

        // ── Close/Home ────────────────────────────────────────────────────
        if (g.hasAny("band karo","close karo","home pe jao","ghar jao","home jao")) {
            return Plan(listOf(Step(Intent.HOME, "", "Home pe jao")), "")
        }

        // ── Generic apps ──────────────────────────────────────────────────
        val apps = mapOf(
            "settings" to "com.android.settings",
            "camera"   to "com.android.camera2",
            "photos"   to "com.google.android.apps.photos",
            "gallery"  to "com.google.android.apps.photos",
            "maps"     to "com.google.android.apps.maps",
            "gmail"    to "com.google.android.gm",
            "calendar" to "com.google.android.calendar",
            "clock"    to "com.google.android.deskclock",
            "twitter"  to "com.twitter.android",
            "facebook" to "com.facebook.katana",
            "snapchat" to "com.snapchat.android"
        )
        for ((kw, pkg) in apps) {
            if (g.contains(kw)) return Plan(listOf(Step(Intent.LAUNCH_APP, pkg, "$kw kholo")), pkg)
        }

        return Plan(emptyList(), "")
    }

    private fun String.hasAny(vararg words: String) = words.any { this.contains(it) }

    private fun String.extractQuery(vararg keywords: String): String? {
        for (kw in keywords) {
            val i = this.indexOf(kw); if (i < 0) continue
            val after = this.substring(i + kw.length).trim()
                .removePrefix("karo").removePrefix("kar").trim()
                .split(Regex("\\s+(aur|and|then|phir|ke baad)\\s+"))[0].trim()
            if (after.length > 1) return after
        }
        return null
    }

    private fun String.extractArtist(): String? =
        Regex("""(.+?)\s+(?:ka gana|ka song|ki song|songs?|music|gane|bajao|lagao|suno)""")
            .find(this)?.groupValues?.getOrNull(1)?.trim()

    private fun String.extractContact(): String? {
        val p1 = Regex("""(?:search|dhundo|find)\s+([a-zA-Z][a-zA-Z\s]{1,20})(?:\s+(?:text|message|ko|se|aur|and)|$)""")
        val p2 = Regex("""(?:to|ko|send to)\s+([a-zA-Z][a-zA-Z\s]{1,20})(?:\s+(?:text|message|bolo|kaho|send|aur)|$)""")
        val p3 = Regex("""([a-zA-Z][a-zA-Z\s]{1,15})\s+(?:ko text|ko message|ko|se baat)""")
        for (p in listOf(p1, p2, p3)) {
            val r = p.find(this)?.groupValues?.getOrNull(1)?.trim()
            if (!r.isNullOrBlank() && r.length > 1) return r
        }
        return null
    }

    private fun String.extractMessage(): String? {
        Regex("""['""](.*?)['"""]""").find(this)?.let { return it.groupValues[1] }
        return Regex("""(?:text|message|bolo|likho|kaho|bol|type)[:\s]+(.+?)(?:\s+(?:aur|and|send)|$)""")
            .find(this)?.groupValues?.getOrNull(1)?.trim()
    }
}
KT
echo "✅ GoalPlanner.kt"

# ═══════════════════════════════════════════════════════════════
# FIX 2: Thinker — smart TAP_FIRST_RESULT using screen text
# ═══════════════════════════════════════════════════════════════
cat > app/src/main/java/com/autoagent/agent/Thinker.kt << 'KT'
package com.autoagent.personal.agent

data class Decision(
    val thought: String,
    val action: Action,
    val description: String,
    val isDone: Boolean = false,
    val skip: Boolean = false
)

sealed class Action {
    object None : Action()
    data class Launch(val pkg: String) : Action()
    data class Tap(val text: String) : Action()
    data class TapSearchBar(val appHint: String) : Action()
    data class TapFirstResult(val query: String) : Action()
    data class Type(val text: String) : Action()
    object SearchKey : Action()
    object Scroll : Action()
    data class Wait(val ms: Long) : Action()
    object Home : Action()
    object Back : Action()
}

object Thinker {
    fun decide(step: Step, screen: ScreenState): Decision = when (step.intent) {

        Intent.LAUNCH_APP -> {
            if (screen.pkg == step.target)
                Decision("App already open", Action.None, "Skip", skip = true)
            else
                Decision("${step.desc}", Action.Launch(step.target), step.desc)
        }

        Intent.TAP -> {
            if (screen.hasText(step.target) || screen.hasClickable(step.target))
                Decision("'${step.target}' mila", Action.Tap(step.target), "Tap: ${step.target}")
            else
                Decision("'${step.target}' nahi mila — scroll", Action.Scroll, "Scroll")
        }

        Intent.TAP_SEARCH_BAR ->
            Decision("Search bar tap", Action.TapSearchBar(step.target), "Search bar tap")

        Intent.TAP_FIRST_RESULT -> {
            // Look for any result text on screen that matches query words
            val queryWords = step.target.split(" ").filter { it.length > 2 }
            val matchedText = queryWords.firstOrNull { w ->
                screen.clickable.any { it.contains(w, ignoreCase = true) }
            }
            if (matchedText != null) {
                val clickableText = screen.clickable.first { it.contains(matchedText, ignoreCase = true) }
                Decision("Result mila: $clickableText", Action.Tap(clickableText), "Tap result: $clickableText")
            } else {
                Decision("Result tap (coordinate)", Action.TapFirstResult(step.target), "Tap first result")
            }
        }

        Intent.TYPE -> {
            if (screen.hasInput)
                Decision("Type: ${step.target}", Action.Type(step.target), "Type: ${step.target}")
            else
                Decision("Input nahi mila — wait", Action.Wait(1500), "Wait for input")
        }

        Intent.SEARCH_KEY -> Decision("Search!", Action.SearchKey, "Search")
        Intent.SCROLL     -> Decision("Scroll", Action.Scroll, "Scroll")
        Intent.WAIT       -> Decision("Wait", Action.Wait(step.target.toLongOrNull() ?: 1000L), "Wait")
        Intent.HOME       -> Decision("Home", Action.Home, "Home")
        Intent.BACK       -> Decision("Back", Action.Back, "Back")
    }
}
KT
echo "✅ Thinker.kt"

# ═══════════════════════════════════════════════════════════════
# FIX 3: Actor — smart search bar + first result tap
# ═══════════════════════════════════════════════════════════════
cat > app/src/main/java/com/autoagent/agent/Actor.kt << 'KT'
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
KT
echo "✅ Actor.kt"

# ═══════════════════════════════════════════════════════════════
# FIX 4: TaskExecutorWorker — use ReactAgent for scheduled tasks
# so they actually open the real app (YouTube, WhatsApp etc.)
# ═══════════════════════════════════════════════════════════════
python3 << 'PYEOF'
path = "app/src/main/java/com/autoagent/service/scheduler/TaskExecutorWorker.kt"
content = open(path).read()

# Add ReactAgent import
if "import com.autoagent.personal.agent.ReactAgent" not in content:
    content = content.replace(
        "import com.autoagent.personal.domain.model.*",
        "import com.autoagent.personal.agent.ReactAgent\nimport com.autoagent.personal.domain.model.*"
    )

# Replace the doWork body to use ReactAgent when task has a name suggesting an app
old_exec = "        val stepLogs = mutableListOf<com.autoagent.personal.domain.model.StepLog>()\n        val status = service.executeSteps(task.steps) { stepLog -> stepLogs.add(stepLog) }"

new_exec = """        // Use ReactAgent for tasks that need real app interaction
        val taskNameLower = task.name.lowercase()
        val needsReactAgent = taskNameLower.contains("youtube") || taskNameLower.contains("whatsapp") ||
            taskNameLower.contains("instagram") || taskNameLower.contains("telegram") ||
            taskNameLower.contains("spotify") || taskNameLower.contains("chrome") ||
            task.steps.any { it.type == ActionType.LAUNCH_APP }

        val stepLogs = mutableListOf<com.autoagent.personal.domain.model.StepLog>()
        val status = if (needsReactAgent && task.steps.isNotEmpty()) {
            // Build goal string from task name + steps
            val goal = task.name
            var agentSuccess = false
            var agentDone = false
            val agent = ReactAgent()
            agent.onDone = { success, _ -> agentSuccess = success; agentDone = true }
            kotlinx.coroutines.withTimeout(60_000) {
                agent.execute(goal)
            }
            if (agentSuccess) RunStatus.SUCCESS else RunStatus.FAILED
        } else {
            service.executeSteps(task.steps) { stepLog -> stepLogs.add(stepLog) }
        }"""

if old_exec in content:
    content = content.replace(old_exec, new_exec)
    print("✅ TaskExecutorWorker patched — ReactAgent for scheduled tasks")
else:
    print("⚠️ TaskExecutorWorker not patched — old string not found")

# Add kotlinx import if missing
if "import kotlinx.coroutines.withTimeout" not in content:
    content = content.replace(
        "import dagger.assisted.Assisted",
        "import kotlinx.coroutines.withTimeout\nimport dagger.assisted.Assisted"
    )

open(path, 'w').write(content)
PYEOF

echo ""
echo "═══════════════════════════════════════════"
echo "✅ All fixes applied! Commit and push:"
echo ""
echo "  git add -A"
echo "  git commit -m 'fix: YouTube play + WhatsApp contact + scheduled tasks use ReactAgent'"
echo "  git push origin main"
echo "═══════════════════════════════════════════"
