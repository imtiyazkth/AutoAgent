package com.autoagent.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.autoagent.domain.model.ActionType
import com.autoagent.domain.model.RunStatus
import com.autoagent.domain.model.StepLog
import com.autoagent.domain.model.TaskStep
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * CRITICAL: Do NOT use @AndroidEntryPoint on AccessibilityService.
 * Hilt injection on AccessibilityService causes silent registration failure
 * on Android 10+ and MIUI devices.
 * Use companion object for shared state instead.
 */
class AutoAgentAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        private val TAG = "AutoAgent_Accessibility"

        // Shared state observable by UI
        val isRunning = MutableStateFlow(false)
        val currentStep = MutableStateFlow<String?>(null)
        val emergencyStop = MutableStateFlow(false)
        val isServiceConnected = MutableStateFlow(false)
        val lastConnectedTime = MutableStateFlow<Long?>(null)
        val lastError = MutableStateFlow<String?>(null)

        private var instance: AutoAgentAccessibilityService? = null

        fun getInstance(): AutoAgentAccessibilityService? = instance

        fun isAvailable(): Boolean = instance != null && isServiceConnected.value
    }

    // =========================================
    // LIFECYCLE
    // =========================================
    override fun onServiceConnected() {
        instance = this
        isServiceConnected.value = true
        lastConnectedTime.value = System.currentTimeMillis()
        lastError.value = null
        Log.i(TAG, "✅ AutoAgent Accessibility Service connected!")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        isServiceConnected.value = false
        isRunning.value = false
        currentStep.value = null
        Log.i(TAG, "AutoAgent Accessibility Service disconnected")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        isServiceConnected.value = false
        isRunning.value = false
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Used for waitForText — handled in executeSteps
    }

    override fun onInterrupt() {
        Log.w(TAG, "Service interrupted")
        isRunning.value = false
    }

    // =========================================
    // EXECUTE STEPS
    // =========================================
    suspend fun executeSteps(
        steps: List<TaskStep>,
        onStepDone: (StepLog) -> Unit
    ): RunStatus {
        isRunning.value = true
        emergencyStop.value = false

        return try {
            for (step in steps) {
                if (emergencyStop.value) {
                    return RunStatus.CANCELLED
                }

                val desc = step.description.ifEmpty { step.type.displayName }
                currentStep.value = "${step.type.emoji} $desc"

                val success = try {
                    withTimeout(10_000) { executeStep(step) }
                } catch (e: TimeoutCancellationException) {
                    Log.w(TAG, "Step timeout: ${step.type}")
                    false
                } catch (e: Exception) {
                    Log.e(TAG, "Step error: ${e.message}")
                    lastError.value = e.message
                    false
                }

                onStepDone(StepLog(
                    stepId = step.id,
                    actionType = step.type,
                    description = desc,
                    success = success,
                    errorMessage = if (!success) "Step execute nahi hua" else null
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
            ActionType.LAUNCH_APP    -> launchApp(step.targetApp ?: return false)
            ActionType.OPEN_URL      -> openUrl(step.targetUrl ?: return false)
            ActionType.TAP_BUTTON    -> { delay(500); tapByText(step.buttonText ?: return false) }
            ActionType.TAP_BY_LABEL  -> { delay(500); tapByLabel(step.buttonText ?: return false) }
            ActionType.ENTER_TEXT    -> { delay(300); enterText(step.inputText ?: return false) }
            ActionType.PASTE_CLIPBOARD -> { delay(300); pasteClipboard() }
            ActionType.SCROLL_DOWN   -> scrollScreen("down")
            ActionType.SCROLL_UP     -> scrollScreen("up")
            ActionType.WAIT_FOR_TEXT -> waitForText(step.waitForText ?: return true)
            ActionType.WAIT_SECONDS  -> { delay(step.delayMs.coerceAtLeast(500)); true }
            ActionType.PRESS_BACK    -> { performGlobalAction(GLOBAL_ACTION_BACK); true }
            ActionType.GO_HOME       -> { performGlobalAction(GLOBAL_ACTION_HOME); true }
            ActionType.CLOSE_APP     -> {
                performGlobalAction(GLOBAL_ACTION_RECENTS)
                delay(300)
                performGlobalAction(GLOBAL_ACTION_HOME)
                true
            }
            ActionType.READ_TEXT     -> readVisibleText()
            ActionType.TAKE_SCREENSHOT -> { performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT); true }
            ActionType.CONFIRM_ACTION -> true
        }
    }

    // =========================================
    // ACTION IMPLEMENTATIONS
    // =========================================
    private fun launchApp(packageName: String): Boolean {
        return try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
                ?: return false
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Launch failed: $packageName — ${e.message}")
            false
        }
    }

    private fun openUrl(url: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "URL open failed: $url — ${e.message}")
            false
        }
    }

    private fun tapByText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val nodes = root.findAccessibilityNodeInfosByText(text)
        val node = nodes.firstOrNull { it.isClickable }
            ?: nodes.firstOrNull()
            ?: return false
        return performClickOn(node)
    }

    private fun tapByLabel(label: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val nodes = root.findAccessibilityNodeInfosByText(label)
        return nodes.firstOrNull()?.let { performClickOn(it) } ?: false
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
            if (direction == "down") {
                moveTo(w / 2, h * 0.7f); lineTo(w / 2, h * 0.3f)
            } else {
                moveTo(w / 2, h * 0.3f); lineTo(w / 2, h * 0.7f)
            }
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
            if (root?.findAccessibilityNodeInfosByText(text)?.isNotEmpty() == true) return true
            delay(500)
        }
        return false
    }

    private fun readVisibleText(): Boolean {
        val root = rootInActiveWindow ?: return false
        val sb = StringBuilder()
        collectText(root, sb)
        Log.i(TAG, "Screen: ${sb.toString().take(200)}")
        return true
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

    private fun findEditableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            val found = findEditableNode(node.getChild(i) ?: continue)
            if (found != null) return found
        }
        return null
    }

    private fun collectText(node: AccessibilityNodeInfo, sb: StringBuilder) {
        if (!node.text.isNullOrEmpty()) sb.append(node.text).append(" ")
        for (i in 0 until node.childCount) collectText(node.getChild(i) ?: continue, sb)
    }

    fun triggerEmergencyStop() {
        emergencyStop.value = true
        isRunning.value = false
        currentStep.value = null
        Log.w(TAG, "🛑 Emergency stop!")
    }
}
