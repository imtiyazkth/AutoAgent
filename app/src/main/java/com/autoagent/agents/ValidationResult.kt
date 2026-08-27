package com.autoagent.personal.agents

/**
 * Result of WorkerAgent.validate() — checked before execute() is called.
 */
data class ValidationResult(
    val valid: Boolean,
    val errors: List<String> = emptyList()
)
