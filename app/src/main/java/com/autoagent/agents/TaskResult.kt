package com.autoagent.personal.agents

/**
 * Outcome of a WorkerAgent.execute() call.
 */
data class TaskResult(
    val success: Boolean,
    val message: String,
    val details: Map<String, String?>? = null
)
