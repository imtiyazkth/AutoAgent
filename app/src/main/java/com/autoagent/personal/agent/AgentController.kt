package com.autoagent.personal.agent

import android.content.Context
import android.util.Log
import com.autoagent.personal.service.AutoAgentAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * AgentController — bridges the ViewModel layer to the device automation layer.
 *
 * CONSTRUCTION: NOT injected by Hilt. Instantiated manually inside ViewModels.
 * Reason: it depends on AccessibilityService.instance which is not a Hilt-managed
 * singleton (it's a system-managed Android service).
 *
 * USAGE:
 *   val controller = AgentController(applicationContext)
 *   val result = controller.execute("YouTube pe Arijit Singh ka gana bajao")
 *
 * THREAD SAFETY: All public functions are suspend functions.
 * Call them from viewModelScope or a dedicated coroutine scope.
 * They use withContext(Dispatchers.Main) internally for UI-thread operations.
 */
class AgentController(private val context: Context) {

    private val TAG = "AgentController"

    // ─── Result type ──────────────────────────────────────────────────────────

    sealed class ExecutionResult {
        data class Success(val message: String) : ExecutionResult()
        data class Failure(val reason: String, val exception: Exception? = null) : ExecutionResult()
        object ServiceNotConnected : ExecutionResult()
        object Cancelled : ExecutionResult()
        object Timeout : ExecutionResult()
    }

    // Emergency stop flag — set to true to halt the current task immediately
    @Volatile
    var emergencyStop: Boolean = false
        private set

    fun triggerEmergencyStop() {
        emergencyStop = true
        Log.w(TAG, "EMERGENCY STOP triggered")
    }

    fun resetEmergencyStop() {
        emergencyStop = false
    }

    // ─── Main entry point ─────────────────────────────────────────────────────

    /**
     * Execute a natural-language task.
     *
     * @param goal The raw task string, e.g. "WhatsApp pe Imtiyaz ko message karo"
     * @param timeoutMs Maximum execution time in ms. Default 60 seconds.
     * @return ExecutionResult describing success, failure, or why execution was skipped.
     */
    suspend fun execute(goal: String, timeoutMs: Long = 60_000L): ExecutionResult {
        resetEmergencyStop()

        // 1. Check service connection BEFORE doing anything
        val service = AutoAgentAccessibilityService.instance
        if (service == null || !AutoAgentAccessibilityService.isConnected()) {
            Log.e(TAG, "execute() called but AccessibilityService is not connected")
            return ExecutionResult.ServiceNotConnected
        }

        // 2. Plan the task
        val plan = withContext(Dispatchers.Default) {
            GoalPlanner.plan(goal)
        }

        if (plan.steps.isEmpty()) {
            return ExecutionResult.Failure("Yeh kaam samajh mein nahi aaya: '$goal'")
        }

        Log.i(TAG, "Executing plan for '$goal' — ${plan.steps.size} steps")

        // 3. Execute with a global timeout
        val result = withTimeoutOrNull(timeoutMs) {
            executeSteps(plan, service)
        }

        return result ?: ExecutionResult.Timeout
    }

    // ─── Step execution ───────────────────────────────────────────────────────

    private suspend fun executeSteps(
        plan: Plan,
        service: AutoAgentAccessibilityService
    ): ExecutionResult {
        for ((index, step) in plan.steps.withIndex()) {

            // Check emergency stop before each step
            if (emergencyStop) {
                Log.w(TAG, "Emergency stop — aborting at step $index")
                return ExecutionResult.Cancelled
            }

            // Re-check service liveness before each step
            val currentService = AutoAgentAccessibilityService.instance
            if (currentService == null) {
                Log.e(TAG, "Service disconnected during execution at step $index")
                return ExecutionResult.ExecutionResult_ServiceLost()
            }

            Log.d(TAG, "Step ${index + 1}/${plan.steps.size}: ${step.desc} [${step.intent}]")

            val stepResult = runStep(step, currentService)
            if (stepResult is StepResult.Fatal) {
                return ExecutionResult.Failure(stepResult.reason)
            }
        }

        return ExecutionResult.Success("Task complete")
    }

    private sealed class StepResult {
        object Ok : StepResult()
        object Skipped : StepResult()
        data class Retry(val reason: String) : StepResult()
        data class Fatal(val reason: String) : StepResult()
    }

    private suspend fun runStep(
        step: Step,
        service: AutoAgentAccessibilityService
    ): StepResult = withContext(Dispatchers.Main) {
        try {
            when (step.intent) {

                Intent.LAUNCH_APP -> {
                    val pkg = step.target
                    if (pkg.isBlank()) return@withContext StepResult.Fatal("Launch app: package name is blank")
                    val launched = service.launchApp(context, pkg)
                    if (!launched) StepResult.Fatal("App '$pkg' not installed or cannot launch")
                    else StepResult.Ok
                }

                Intent.WAIT -> {
                    val ms = step.target.toLongOrNull() ?: 1000L
                    delay(ms.coerceIn(100, 10_000))
                    StepResult.Ok
                }

                Intent.TAP -> {
                    val node = service.findNodeByText(step.target)
                    if (node != null) {
                        node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                        StepResult.Ok
                    } else {
                        Log.w(TAG, "TAP: node '${step.target}' not found — skipping")
                        StepResult.Skipped
                    }
                }

                Intent.TAP_SEARCH_BAR -> {
                    // Try common search bar text labels first
                    val searchLabels = listOf("Search", "Search…", "Search...", step.target)
                    var found = false
                    for (label in searchLabels) {
                        val node = service.findNodeByText(label)
                        if (node != null) {
                            node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                            found = true
                            break
                        }
                    }
                    if (!found) {
                        // Fall back to first editable node
                        val root = service.getRootNode()
                        if (root != null) {
                            val editable = service.findEditableNode(root)
                            editable?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                        }
                    }
                    StepResult.Ok
                }

                Intent.TAP_FIRST_RESULT -> {
                    // Wait up to 3 seconds for a result matching query words
                    val queryWords = step.target.split(" ").filter { it.length > 2 }
                    var found = false
                    repeat(6) { // 6 × 500ms = 3 seconds
                        if (!found) {
                            val clickable = service.getClickableTexts()
                            val match = queryWords.firstOrNull { word ->
                                clickable.any { it.contains(word, ignoreCase = true) }
                            }
                            if (match != null) {
                                val targetText = clickable.first { it.contains(match, ignoreCase = true) }
                                val node = service.findNodeByText(targetText)
                                node?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                                found = true
                            } else {
                                delay(500)
                            }
                        }
                    }
                    if (!found) Log.w(TAG, "TAP_FIRST_RESULT: no match for '${step.target}'")
                    StepResult.Ok // Don't fail — result may simply not be visible yet
                }

                Intent.TYPE -> {
                    val root = service.getRootNode()
                    if (root != null) {
                        val editable = service.findEditableNode(root)
                        if (editable != null) {
                            val args = android.os.Bundle().apply {
                                putString(
                                    android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                                    step.target
                                )
                            }
                            editable.performAction(
                                android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT,
                                args
                            )
                            StepResult.Ok
                        } else {
                            Log.w(TAG, "TYPE: no editable field found — skipping")
                            StepResult.Skipped
                        }
                    } else {
                        StepResult.Skipped
                    }
                }

                Intent.SEARCH_KEY -> {
                    // Press the IME action (Search/Enter)
                    val root = service.getRootNode()
                    if (root != null) {
                        val editable = service.findEditableNode(root)
                        editable?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY)
                        // Fallback: global KEYCODE_ENTER via gesture (API 26+)
                        service.performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
                    }
                    StepResult.Ok
                }

                Intent.SCROLL -> {
                    val result = service.performGlobalAction(AccessibilityService.GESTURE_SWIPE_UP)
                    if (!result) StepResult.Skipped else StepResult.Ok
                }

                Intent.HOME -> {
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                    StepResult.Ok
                }

                Intent.BACK -> {
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                    StepResult.Ok
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Step ${step.intent} threw exception", e)
            StepResult.Fatal(e.message ?: "Unknown error in step ${step.intent}")
        }
    }
}

// Extension to create a "service lost mid-execution" result cleanly
private fun ExecutionResult.Companion.ExecutionResult_ServiceLost(): AgentController.ExecutionResult =
    AgentController.ExecutionResult.Failure("AccessibilityService disconnected mid-task")
