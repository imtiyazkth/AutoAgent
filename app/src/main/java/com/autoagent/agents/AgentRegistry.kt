package com.autoagent.personal.agents

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central lookup table for all WorkerAgents. Hilt injects every agent bound
 * with @IntoSet in AgentsModule as one Set<WorkerAgent> here — no agent
 * needs to know about any other agent.
 */
@Singleton
class AgentRegistry @Inject constructor(
    private val agents: Set<@JvmSuppressWildcards WorkerAgent>
) {
    /** Returns the first agent that reports it can handle [taskType], or null. */
    fun findAgent(taskType: String): WorkerAgent? =
        agents.firstOrNull { it.canHandle(taskType) }

    /** All registered agents — useful for diagnostics / listing capabilities. */
    fun allAgents(): List<WorkerAgent> = agents.toList()
}
