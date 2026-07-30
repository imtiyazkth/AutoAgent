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
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@AndroidEntryPoint
class AutoAgentAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        // Shared state — UI can observe these
        val isRunning = MutableStateFlow(false)
        val currentStep = MutableStateFlow<String?>(null)
        val stepLogs = MutableStateFlow<List<StepLog>>(emptyList())
        val emergencyStop = MutableStateFlow(false)

        // Action broadcast
        const val ACTION_EXECUTE_STEPS = "com.autoagent.EXECUTE_STEPS"
        const val ACTION_EMERGENCY_STOP = "com.autoagent.EMERGENCY_STOP"

        private var instance: AutoAgentAccessibilityService? = null
        fun getInstance(): AutoAgentAccessibilityService? = instance
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i("AutoAgent", "Accessibility Service connected ✅")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        serviceScope.cancel()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Used for waitForText detection
    }

    override fun onInterrupt() {
        Log.w("AutoAgent", "Accessibility Service interrupted")
    }

    // =========================================
    // EXECUTE A LIST OF STEPS
    // =========================================
    suspend fun executeSteps(
        steps: List<TaskStep>,
        onStepComplete: (StepLog) -> Unit
    ): RunStatus {
        isRunning.value = true
        emergencyStop.value = false
        val logs = mutableListOf<StepLog>()

        try {
            for (step in steps) {
                // Emergency stop check
                if (emergencyStop.value) {
                    Log.w("AutoAgent", "Emergency stop triggered!")
                    return RunStatus.CANCELLED
                }

                currentStep.value = "${step.type.emoji} ${step.description.ifEmpty { step.type.displayName }}"
                Log.i("AutoAgent", "Executing step: ${step.type.name}")

                val success = try {
                    executeStep(step)
                } catch (e: Exception) {
                    Log.e("AutoAgent", "Step failed: ${e.message}")
                    false
                }

                val log = StepLog(
                    stepId = step.id,
                    actionType = step.type,
                    description = step.description.ifEmpty { step.type.displayName },
                    success = success,
                    errorMessage = if (!success) "Step execute nahi hua" else null
                )
                logs.add(log)
                onStepComplete(log)

                // Retry logic
                if (!success && step.retryCount > 0) {
                    Log.i("AutoAgent", "Retrying step ${step.id}...")
                    delay(1000)
                    val retrySuccess = executeStep(step)
                    if (!retrySuccess) {
                        Log.e("AutoAgent", "Step ${step.id} retry bhi fail ho gaya")
                    }
                }

                // Delay after step
                if (step.delayMs > 0) {
                    delay(step.delayMs)
                }
            }

            return RunStatus.SUCCESS
        } finally {
            isRunning.value = false
            currentStep.value = null
            stepLogs.value = logs
        }
    }

    // =========================================
    // EXECUTE INDIVIDUAL STEP
    // =========================================
    private suspend fun executeStep(step: TaskStep): Boolean {
        return when (step.type) {
            ActionType.LAUNCH_APP -> launchApp(step.targetApp ?: return false)
            ActionType.OPEN_URL -> openUrl(step.targetUrl ?: return false)
            ActionType.TAP_BUTTON -> tapByText(step.buttonText ?: return false)
            ActionType.TAP_BY_LABEL -> tapByContentDescription(step.buttonText ?: return false)
            ActionType.ENTER_TEXT -> enterText(step.inputText ?: return false)
            ActionType.PASTE_CLIPBOARD -> pasteClipboard()
            ActionType.SCROLL_DOWN -> scrollScreen("down")
            ActionType.SCROLL_UP -> scrollScreen("up")
            ActionType.WAIT_FOR_TEXT -> waitForText(step.waitForText ?: return false)
            ActionType.WAIT_SECONDS -> { delay((step.delayMs).coerceAtLeast(500)); true }
            ActionType.PRESS_BACK -> { performGlobalAction(GLOBAL_ACTION_BACK); true }
            ActionType.GO_HOME -> { performGlobalAction(GLOBAL_ACTION_HOME); true }
            ActionType.CLOSE_APP -> { performGlobalAction(GLOBAL_ACTION_RECENTS); delay(300)
                performGlobalAction(GLOBAL_ACTION_HOME); true }
            ActionType.READ_TEXT -> readVisibleText()
            ActionType.TAKE_SCREENSHOT -> { performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT); true }
            ActionType.CONFIRM_ACTION -> true // User already confirmed via PIN
        }
    }

    // =========================================
    // LAUNCH APP
    // =========================================
    private fun launchApp(packageName: String): Boolean {
        return try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
                ?: return false
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e("AutoAgent", "App launch failed: $packageName - ${e.message}")
            false
        }
    }

    // =========================================
    // OPEN URL
    // =========================================
    private fun openUrl(url: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e("AutoAgent", "URL open failed: $url")
            false
        }
    }

    // =========================================
    // TAP BY TEXT
    // =========================================
    private suspend fun tapByText(text: String): Boolean {
        delay(500) // Wait for UI to settle
        val root = rootInActiveWindow ?: return false
        val node = findNodeByText(root, text) ?: run {
            Log.w("AutoAgent", "Node with text '$text' not found")
            return false
        }
        return performClick(node)
    }

    // =========================================
    // TAP BY CONTENT DESCRIPTION
    // =========================================
    private suspend fun tapByContentDescription(description: String): Boolean {
        delay(500)
        val root = rootInActiveWindow ?: return false
        val nodes = root.findAccessibilityNodeInfosByText(description)
        val node = nodes.firstOrNull() ?: return false
        return performClick(node)
    }

    // =========================================
    // ENTER TEXT
    // =========================================
    private suspend fun enterText(text: String): Boolean {
        delay(300)
        val root = rootInActiveWindow ?: return false
        // Find focused or editable node
        val editNode = findEditableNode(root) ?: return false
        editNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        delay(200)
        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        return editNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    // =========================================
    // PASTE CLIPBOARD
    // =========================================
    private suspend fun pasteClipboard(): Boolean {
        delay(300)
        val root = rootInActiveWindow ?: return false
        val editNode = findEditableNode(root) ?: return false
        editNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        delay(200)
        return editNode.performAction(AccessibilityNodeInfo.ACTION_PASTE)
    }

    // =========================================
    // SCROLL SCREEN
    // =========================================
    private suspend fun scrollScreen(direction: String): Boolean {
        val displayMetrics = resources.displayMetrics
        val width = displayMetrics.widthPixels.toFloat()
        val height = displayMetrics.heightPixels.toFloat()

        val path = Path()
        if (direction == "down") {
            path.moveTo(width / 2, height * 0.7f)
            path.lineTo(width / 2, height * 0.3f)
        } else {
            path.moveTo(width / 2, height * 0.3f)
            path.lineTo(width / 2, height * 0.7f)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 300))
            .build()

        return dispatchGesture(gesture, null, null)
    }

    // =========================================
    // WAIT FOR TEXT
    // =========================================
    private suspend fun waitForText(text: String, timeoutMs: Long = 10000): Boolean {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (emergencyStop.value) return false
            val root = rootInActiveWindow
            if (root != null) {
                val nodes = root.findAccessibilityNodeInfosByText(text)
                if (nodes.isNotEmpty()) return true
            }
            delay(500)
        }
        return false
    }

    // =========================================
    // READ VISIBLE TEXT
    // =========================================
    private fun readVisibleText(): Boolean {
        val root = rootInActiveWindow ?: return false
        val sb = StringBuilder()
        collectText(root, sb)
        Log.i("AutoAgent", "Screen text: ${sb.toString().take(500)}")
        return true
    }

    // =========================================
    // EMERGENCY STOP
    // =========================================
    fun triggerEmergencyStop() {
        emergencyStop.value = true
        isRunning.value = false
        currentStep.value = null
        Log.w("AutoAgent", "🛑 EMERGENCY STOP triggered!")
    }

    // =========================================
    // HELPER FUNCTIONS
    // =========================================
    private fun findNodeByText(root: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val nodes = root.findAccessibilityNodeInfosByText(text)
        return nodes.firstOrNull { it.isClickable } ?: nodes.firstOrNull()
    }

    private fun findEditableNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (root.isEditable) return root
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val found = findEditableNode(child)
            if (found != null) return found
        }
        return null
    }

    private fun performClick(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        // Try clicking parent
        val parent = node.parent
        if (parent != null && parent.isClickable) {
            return parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        // Try gesture click on bounds
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (!bounds.isEmpty) {
            val path = Path()
            path.moveTo(bounds.exactCenterX(), bounds.exactCenterY())
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
                .build()
            return dispatchGesture(gesture, null, null)
        }
        return false
    }

    private fun collectText(node: AccessibilityNodeInfo, sb: StringBuilder) {
        if (!node.text.isNullOrEmpty()) sb.append(node.text).append(" ")
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectText(child, sb)
        }
    }
}
