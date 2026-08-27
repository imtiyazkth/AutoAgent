package com.autoagent.personal.agents

/**
 * A pluggable capability handler. Each concrete agent (WhatsApp, YouTube,
 * Sheets, ...) implements this and is bound into the Hilt multibinding set
 * so AgentRegistry can route tasks to it by [Task.type].
 */
interface WorkerAgent {

    /** Return true if this agent can handle the given [taskType]. */
    fun canHandle(taskType: String): Boolean

    /** Execute [task]. Called only after [validate] returns valid = true. */
    suspend fun execute(task: Task): TaskResult

    /** Cheap, synchronous pre-flight check before execute() is attempted. */
    fun validate(task: Task): ValidationResult
}
