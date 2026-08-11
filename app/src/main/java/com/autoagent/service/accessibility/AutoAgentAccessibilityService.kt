package com.autoagent.personal.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.autoagent.personal.domain.model.ActionType
import com.autoagent.personal.domain.model.RunStatus
import com.autoagent.personal.domain.model.StepLog
import com.autoagent.personal.domain.model.TaskStep
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AutoAgentAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    companion object {
        private const val TAG = "AutoAgent_A11y"
        private const val MAX_TREE_DEPTH = 30

        val isRunning        = MutableStateFlow(false)
        val currentStep      = MutableStateFlow<String?>(null)
        val emergencyStop    = MutableStateFlow(false)
        val isServiceConnected = MutableStateFlow(false)
        val lastConnectedTime  = MutableStateFlow<Long?>(null)
        val lastError          = MutableStateFlow<String?>(null)

        @Volatile private var instance: AutoAgentAccessibilityService? = null

        fun getInstance(): AutoAgentAccessibilityService? = instance
        fun isAvailable(): Boolean = instance != null && isServiceConnected.value

        fun checkConnection(): Boolean {
            val alive = instance != null && isServiceConnected.value
            if (!alive && instance != null) isServiceConnected.value = true
            return isServiceConnected.value
        }
    }

    override fun onServiceConnected() {
        instance = this
        isServiceConnected.value = true
        lastConnectedTime.value = System.currentTimeMillis()
        lastError.value = null
        Log.i(TAG, "✅ Service connected")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        isServiceConnected.value = false
        isRunning.value = false
        currentStep.value = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        isServiceConnected.value = false
        isRunning.value = false
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() { isRunning.value = false }

    suspend fun executeSteps(
        steps: List<TaskStep>,
        onStepDone: (StepLog) -> Unit
    ): RunStatus {
        isRunning.value = true
        emergencyStop.value = false

        return try {
            for (step in steps) {
                if (emergencyStop.value) return RunStatus.CANCELLED

                val desc = step.description.ifEmpty { step.type.displayName }
                currentStep.value = "${step.type.emoji} $desc"

                var success = false
                var attempts = 0
                val maxAttempts = (step.retryCount + 1).coerceAtLeast(1)

                while (attempts < maxAttempts && !emergencyStop.value) {
                    success = try {
                        withTimeout(12_000) { executeStep(step) }
                    } catch (e: TimeoutCancellationException) {
                        Log.w(TAG, "Step timeout: ${step.type} attempt $attempts")
                        false
                    } catch (e: Exception) {
                        Log.e(TAG, "Step error: ${e.message}")
                        lastError.value = e.message
                        false
                    }
                    if (success) break
                    attempts++
                    if (attempts < maxAttempts) delay(1000)
                }

                onStepDone(StepLog(
                    stepId = step.id,
                    actionType = step.type,
                    description = desc,
                    success = success,
                    errorMessage = if (!success) "Step execute nahi hua (${attempts} attempts)" else null
                ))

                if (step.delayMs > 0) delay(step.delayMs)
            }
            RunStatus.SUCCESS
        } catch (e: Exception) {
            Log.e(TAG, "executeSteps error: ${e.message}")
            lastError.value = e.message
            RunStatus.FAILED
        } finally {
            isRunning.value = false
            currentStep.value = null
        }
    }

    private suspend fun executeStep(step: TaskStep): Boolean {
        return when (step.type) {
            ActionType.LAUNCH_APP      -> launchApp(step.targetApp ?: return false)
            ActionType.OPEN_URL        -> openUrl(step.targetUrl ?: return false)
            ActionType.TAP_BUTTON      -> { delay(500); tapByText(step.buttonText ?: return false) }
            ActionType.TAP_BY_LABEL    -> { delay(500); tapByLabel(step.buttonText ?: return false) }
            ActionType.ENTER_TEXT      -> { delay(300); enterText(step.inputText ?: return false) }
            ActionType.PASTE_CLIPBOARD -> { delay(300); pasteClipboard() }
            ActionType.SCROLL_DOWN     -> scrollScreen("down")
            ActionType.SCROLL_UP       -> scrollScreen("up")
            ActionType.WAIT_FOR_TEXT   -> waitForText(step.waitForText ?: return true)
            ActionType.WAIT_SECONDS    -> { delay(step.delayMs.coerceAtLeast(500)); true }
            ActionType.PRESS_BACK      -> { performGlobalAction(GLOBAL_ACTION_BACK); true }
            ActionType.GO_HOME         -> { performGlobalAction(GLOBAL_ACTION_HOME); true }
            ActionType.CLOSE_APP       -> {
                performGlobalAction(GLOBAL_ACTION_RECENTS)
                delay(300)
                performGlobalAction(GLOBAL_ACTION_HOME)
                true
            }
            ActionType.READ_TEXT       -> readVisibleText()
            ActionType.TAKE_SCREENSHOT -> { performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT); true }
            ActionType.CONFIRM_ACTION  -> true
        }
    }

    private fun launchApp(packageName: String): Boolean {
        return try {
            val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return false
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Launch failed: $packageName — ${e.message}"); false
        }
    }

    private fun openUrl(url: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "URL open failed: $url — ${e.message}"); false
        }
    }

    private fun tapByText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val nodes = root.findAccessibilityNodeInfosByText(text)
        return try {
            val node = nodes.firstOrNull { it.isClickable } ?: nodes.firstOrNull() ?: return false
            performClickOn(node)
        } finally {
            nodes.forEach { it.recycle() }
        }
    }

    private fun tapByLabel(label: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val nodes = root.findAccessibilityNodeInfosByText(label)
        return try {
            nodes.firstOrNull()?.let { performClickOn(it) } ?: false
        } finally {
            nodes.forEach { it.recycle() }
        }
    }

    private fun enterText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val editNode = findEditableNode(root) ?: return false
        editNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return editNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun pasteClipboard(): Boolean {
        val root = rootInActiveWindow ?: return false
        val editNode = findEditableNode(root) ?: return false
        editNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        return editNode.performAction(AccessibilityNodeInfo.ACTION_PASTE)
    }

    private fun scrollScreen(direction: String): Boolean {
        val dm = resources.displayMetrics
        val w = dm.widthPixels.toFloat()
        val h = dm.heightPixels.toFloat()
        val path = Path().apply {
            if (direction == "down") { moveTo(w / 2, h * 0.7f); lineTo(w / 2, h * 0.3f) }
            else { moveTo(w / 2, h * 0.3f); lineTo(w / 2, h * 0.7f) }
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 300))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    private suspend fun waitForText(text: String, timeoutMs: Long = 8000): Boolean {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (emergencyStop.value) return false
            val root = rootInActiveWindow
            val nodes = root?.findAccessibilityNodeInfosByText(text)
            val found = nodes?.isNotEmpty() == true
            nodes?.forEach { it.recycle() }
            if (found) return true
            delay(500)
        }
        return false
    }

    private fun readVisibleText(): Boolean {
        val root = rootInActiveWindow ?: return false
        val sb = StringBuilder()
        collectText(root, sb, 0)
        Log.i(TAG, "Screen text: ${sb.toString().take(300)}")
        return true
    }

    /** Read screen text and return it as a String for ReplyHandler */
    fun readScreenText(): String {
        val root = rootInActiveWindow ?: return ""
        val sb = StringBuilder()
        collectText(root, sb, 0)
        return sb.toString().trim()
    }

    private fun performClickOn(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable) return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        val parent = node.parent
        if (parent?.isClickable == true) return parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (!bounds.isEmpty) {
            val path = Path().apply { moveTo(bounds.exactCenterX(), bounds.exactCenterY()) }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
                .build()
            return dispatchGesture(gesture, null, null)
        }
        return false
    }

    private fun findEditableNode(
        node: AccessibilityNodeInfo,
        depth: Int = 0
    ): AccessibilityNodeInfo? {
        if (depth > MAX_TREE_DEPTH) return null
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            val found = findEditableNode(node.getChild(i) ?: continue, depth + 1)
            if (found != null) return found
        }
        return null
    }

    // FIXED: depth limit prevents stack overflow on deeply nested UIs
    private fun collectText(node: AccessibilityNodeInfo, sb: StringBuilder, depth: Int) {
        if (depth > MAX_TREE_DEPTH) return
        if (!node.text.isNullOrEmpty()) sb.append(node.text).append(" ")
        for (i in 0 until node.childCount) {
            collectText(node.getChild(i) ?: continue, sb, depth + 1)
        }
    }


    fun getScreenState(): com.autoagent.personal.agent.ScreenState {
        val root = rootInActiveWindow
        val pkg  = root?.packageName?.toString() ?: ""
        val texts = mutableListOf<String>()
        val clickable = mutableListOf<String>()
        var hasInput = false
        var scrollable = false
        if (root != null) collectNodes(root, texts, clickable, { hasInput = true }, { scrollable = true }, 0)
        return com.autoagent.personal.agent.ScreenState(pkg, texts, clickable, hasInput, scrollable)
    }

    private fun collectNodes(node: AccessibilityNodeInfo, texts: MutableList<String>,
        clickable: MutableList<String>, onInput: () -> Unit, onScroll: () -> Unit, depth: Int) {
        if (depth > MAX_TREE_DEPTH) return
        val t = node.text?.toString() ?: node.contentDescription?.toString()
        if (!t.isNullOrBlank()) { texts.add(t); if (node.isClickable) clickable.add(t) }
        if (node.isEditable) onInput()
        if (node.isScrollable) onScroll()
        for (i in 0 until node.childCount) collectNodes(node.getChild(i) ?: continue, texts, clickable, onInput, onScroll, depth + 1)
    }

    fun tapText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        var nodes = root.findAccessibilityNodeInfosByText(text)
        if (nodes.isNullOrEmpty()) {
            val list = mutableListOf<AccessibilityNodeInfo>()
            walkTree(root, 0) { n ->
                val t = n.text?.toString() ?: n.contentDescription?.toString() ?: ""
                if (t.contains(text, ignoreCase = true)) list.add(n)
            }
            nodes = list
        }
        return try {
            val node = nodes.firstOrNull { it.isClickable } ?: nodes.firstOrNull() ?: return false
            performClickOn(node)
        } finally { nodes.forEach { try { it.recycle() } catch (_: Exception) {} } }
    }

    fun typeText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val edit = findEditableNode(root) ?: return false
        edit.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return edit.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    fun pressSearchKey(): Boolean {
        val root = rootInActiveWindow ?: return false
        val edit = findEditableNode(root) ?: return false
        val args = Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT,
                AccessibilityNodeInfo.MOVEMENT_GRANULARITY_LINE)
        }
        return edit.performAction(AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY, args)
    }

    fun scrollDown(): Boolean {
        val dm = resources.displayMetrics
        val w = dm.widthPixels.toFloat(); val h = dm.heightPixels.toFloat()
        val path = android.graphics.Path().apply { moveTo(w/2, h*0.72f); lineTo(w/2, h*0.28f) }
        val gesture = android.accessibilityservice.GestureDescription.Builder()
            .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 350)).build()
        return dispatchGesture(gesture, null, null)
    }

    private fun walkTree(node: AccessibilityNodeInfo, depth: Int, v: (AccessibilityNodeInfo) -> Unit) {
        if (depth > MAX_TREE_DEPTH) return
        v(node)
        for (i in 0 until node.childCount) walkTree(node.getChild(i) ?: return, depth+1, v)
    }

    fun triggerEmergencyStop() {
        emergencyStop.value = true
        isRunning.value = false
        currentStep.value = null
        Log.w(TAG, "🛑 Emergency stop triggered!")
    }

    /** Watchdog: check if service is genuinely functional */
    fun diagnose(): AccessibilityDiagnosis {
        val settingEnabled = true // checked externally via isAccessibilityEnabled()
        val instanceAlive = isAvailable()
        val rootAccessible = try { rootInActiveWindow != null } catch (e: Exception) { false }
        return AccessibilityDiagnosis(
            settingEnabled = settingEnabled,
            instanceAlive = instanceAlive,
            rootAccessible = rootAccessible,
            lastConnectedAt = lastConnectedTime.value,
            lastError = lastError.value,
            recommendation = when {
                !instanceAlive -> "App band karke dobara kholo, phir accessibility service toggle karo"
                !rootAccessible -> "Koi screen active nahi — pehle koi app foreground mein kholo"
                else -> null
            }
        )
    }
}

data class AccessibilityDiagnosis(
    val settingEnabled: Boolean,
    val instanceAlive: Boolean,
    val rootAccessible: Boolean,
    val lastConnectedAt: Long?,
    val lastError: String?,
    val recommendation: String?
)
