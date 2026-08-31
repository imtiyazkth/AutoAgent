package com.autoagent.personal.agents

import com.autoagent.personal.agent.ReactAgent
import com.autoagent.personal.util.L
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TaskDispatcher — sits ABOVE both AgentRegistry and the legacy
 * ReactAgent goal pipeline. This is the ONLY new entry point;
 * ActionEngine.kt and AgentController.kt are untouched.
 *
 * Routing rule:
 *   1. If a registered WorkerAgent can handle task.type, use it.
 *   2. Otherwise fall back to ReactAgent with a natural-language goal
 *      string — the same pipeline VoiceAgentScreen and WhatsAppAgent use.
 */
@Singleton
class TaskDispatcher @Inject constructor(
    private val registry: AgentRegistry
) {
    private val TAG = "TaskDispatcher"

    suspend fun dispatch(task: Task): TaskResult {
        val agent = registry.findAgent(task.type)

        if (agent != null) {
            L.d(TAG, "Routing '${task.type}' to ${agent::class.simpleName}")
            val validation = agent.validate(task)
            if (!validation.valid) {
                return TaskResult(success = false, message = validation.errors.joinToString("; "))
            }
            return agent.execute(task)
        }

        // Fallback: no registered agent for this type — use the legacy
        // natural-language ReactAgent pipeline unchanged.
        L.d(TAG, "No agent for '${task.type}' — falling back to ReactAgent")
        val goal = task.notes ?: task.params["goal"] ?: task.type

        var resultSuccess = false
        var resultMessage = "Timeout"

        val reactAgent = ReactAgent()
        reactAgent.onDone = { ok, msg -> resultSuccess = ok; resultMessage = msg }

        withTimeoutOrNull(45_000) {
            reactAgent.execute(goal)
        }

        return TaskResult(success = resultSuccess, message = resultMessage)
    }
}
