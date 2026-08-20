package com.autoagent.personal.agent

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.autoagent.personal.service.AutoAgentAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class AgentController(private val context: Context) {

    private val TAG = "AgentController"

    sealed class ExecutionResult {
        data class Success(val message: String) : ExecutionResult()
        data class Failure(val reason: String, val exception: Exception? = null) : ExecutionResult()
        object ServiceNotConnected : ExecutionResult()
        object Cancelled : ExecutionResult()
        object Timeout : ExecutionResult()
    }

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

    suspend fun execute(goal: String, timeoutMs: Long = 60_000L): ExecutionResult {
        resetEmergencyStop()

        val service = AutoAgentAccessibilityService.instance
        if (service == null || !AutoAgentAccessibilityService.isConnected()) {
            Log.e(TAG, "AccessibilityService not connected")
            return ExecutionResult.ServiceNotConnected
        }

        val plan = withContext(Dispatchers.Default) {
            GoalPlanner.plan(goal)
        }

        if (plan.steps.isEmpty()) {
            return ExecutionResult.Failure("Samajh nahi aaya: '$goal'")
        }

        Log.i(TAG, "Executing plan for '$goal' — ${plan.steps.size} steps")

        return withTimeoutOrNull(timeoutMs) {
            executeSteps(plan, service)
        } ?: ExecutionResult.Timeout
    }

    private suspend fun executeSteps(
        plan: Plan,
        service: AutoAgentAccessibilityService
    ): ExecutionResult {
        for ((index, step) in plan.steps.withIndex()) {
            if (emergencyStop) {
                Log.w(TAG, "Emergency stop at step $index")
                return ExecutionResult.Cancelled
            }

            if (AutoAgentAccessibilityService.instance == null) {
                Log.e(TAG, "Service lost at step $index")
                return ExecutionResult.Failure("Service disconnected mid-task")
            }

            Log.d(TAG, "Step ${index + 1}/${plan.steps.size}: ${step.desc}")

            val stepResult = runStep(step, service)
            if (stepResult is StepResult.Fatal) {
                return ExecutionResult.Failure(stepResult.reason)
            }
        }
        return ExecutionResult.Success("Task complete")
    }

    private sealed class StepResult {
        object Ok : StepResult()
        object Skipped : StepResult()
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
                    if (pkg.isBlank()) return@withContext StepResult.Fatal("Package blank")
                    val launched = service.launchApp(context, pkg)
                    if (!launched) StepResult.Fatal("Cannot launch '$pkg'")
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
                        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        StepResult.Ok
                    } else {
                        Log.w(TAG, "TAP: '${step.target}' not found")
                        StepResult.Skipped
                    }
                }
                Intent.TAP_SEARCH_BAR -> {
                    val labels = listOf("Search", "Search…", "Search...", step.target)
                    var found = false
                    for (label in labels) {
                        val node = service.findNodeByText(label)
                        if (node != null) {
                            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            found = true
                            break
                        }
                    }
                    if (!found) {
                        val root = service.getRootNode()
                        if (root != null) {
                            service.findEditableNode(root)
                                ?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        }
                    }
                    StepResult.Ok
                }
                Intent.TAP_FIRST_RESULT -> {
                    val words = step.target.split(" ").filter { it.length > 2 }
                    var found = false
                    repeat(6) {
                        if (!found) {
                            val clickable = service.getClickableTexts()
                            val match = words.firstOrNull { w ->
                                clickable.any { it.contains(w, ignoreCase = true) }
                            }
                            if (match != null) {
                                val t = clickable.first { it.contains(match, ignoreCase = true) }
                                service.findNodeByText(t)
                                    ?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                                found = true
                            } else {
                                delay(500)
                            }
                        }
                    }
                    StepResult.Ok
                }
                Intent.TYPE -> {
                    val root = service.getRootNode()
                    if (root != null) {
                        val editable = service.findEditableNode(root)
                        if (editable != null) {
                            val args = android.os.Bundle().apply {
                                putString(
                                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                                    step.target
                                )
                            }
                            editable.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                            StepResult.Ok
                        } else StepResult.Skipped
                    } else StepResult.Skipped
                }
                Intent.SEARCH_KEY -> {
                    val root = service.getRootNode()
                    if (root != null) {
                        service.findEditableNode(root)
                            ?.performAction(AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY)
                    }
                    StepResult.Ok
                }
                Intent.SCROLL -> {
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_ACCESSIBILITY_ALL_APPS)
                    StepResult.Ok
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
            Log.e(TAG, "Step ${step.intent} error", e)
            StepResult.Fatal(e.message ?: "Unknown error")
        }
    }
}
