package com.autoagent.personal.agent

enum class AgentState {
    IDLE, PARSING, UNDERSTANDING, PLANNING,
    VALIDATING, WAITING_FOR_SCHEDULE, PREPARING,
    RUNNING, OBSERVING, VERIFYING, RECOVERING,
    PAUSED, WAITING_FOR_USER, BLOCKED,
    COMPLETED, FAILED, CANCELLED
}

data class AgentSession(
    val id: String = java.util.UUID.randomUUID().toString(),
    val goal: String,
    val startedAt: Long = System.currentTimeMillis(),
    val state: AgentState = AgentState.IDLE,
    val stepsDone: Int = 0,
    val stepsTotal: Int = 0,
    val lastError: String? = null,
    val completedAt: Long? = null
) {
    val isActive: Boolean get() = state in setOf(
        AgentState.RUNNING, AgentState.OBSERVING,
        AgentState.VERIFYING, AgentState.RECOVERING,
        AgentState.PREPARING, AgentState.PLANNING
    )
    val isTerminal: Boolean get() = state in setOf(
        AgentState.COMPLETED, AgentState.FAILED,
        AgentState.CANCELLED, AgentState.BLOCKED
    )
}
