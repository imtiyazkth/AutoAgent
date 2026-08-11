package com.autoagent.personal.agent

import android.util.Log
import com.autoagent.personal.service.accessibility.AutoAgentAccessibilityService
import kotlinx.coroutines.*

class ReactAgent {

    companion object {
        private const val TAG = "ReactAgent"
        private const val MAX_STEPS = 30
        private const val STEP_DELAY = 1200L
    }

    var onThought:  ((String) -> Unit)? = null
    var onAction:   ((String) -> Unit)? = null
    var onDone:     ((Boolean, String) -> Unit)? = null
    private var shouldStop = false

    fun stop() { shouldStop = true }

    suspend fun execute(goal: String) = withContext(Dispatchers.Default) {
        shouldStop = false
        val svc = AutoAgentAccessibilityService.getInstance()
        if (svc == null) {
            onDone?.invoke(false, "Accessibility service ON nahi hai")
            return@withContext
        }

        Log.d(TAG, "Goal: $goal")
        val plan = GoalPlanner.plan(goal)

        if (plan.steps.isEmpty()) {
            onDone?.invoke(false, "Samajh nahi aaya — zyada clearly bolein")
            return@withContext
        }

        var stepNum = 0
        var goalAchieved = false

        for (step in plan.steps) {
            if (shouldStop || stepNum >= MAX_STEPS) break
            if (AutoAgentAccessibilityService.emergencyStop.value) {
                onDone?.invoke(false, "Emergency stop"); return@withContext
            }
            stepNum++

            val screen = svc.getScreenState()
            val decision = Thinker.decide(step, screen)
            onThought?.invoke(decision.thought)

            if (decision.isDone) { goalAchieved = true; break }
            if (decision.skip)   continue

            onAction?.invoke(decision.description)
            Actor.perform(decision.action, svc)

            delay(STEP_DELAY)
        }

        onDone?.invoke(true, if (goalAchieved) "Ho gaya! ✅" else "Steps complete ✅")
    }
}
