package com.autoagent.personal.agents

import com.autoagent.personal.agent.ReactAgent
import com.autoagent.personal.data.db.ExecutionLogEntity
import com.autoagent.personal.data.repository.AgentRepository
import com.autoagent.personal.domain.model.RunStatus
import com.autoagent.personal.safety.RiskEngine
import com.autoagent.personal.service.accessibility.AutoAgentAccessibilityService
import com.autoagent.personal.util.L
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WhatsAppAgent — first real WorkerAgent. Handles WhatsApp message tasks
 * by driving the existing, already-tested ReactAgent pipeline (the same
 * one used by voice commands) rather than re-implementing UI automation.
 *
 * Deliberate design choice: the raw AccessibilityExecutor + UiSnapshot
 * stack requires building a fresh screen snapshot per call and is not
 * yet wired for this task-based flow. ReactAgent already does search →
 * type → send reliably (see the TAP_FIRST_RESULT bounds fix), so this
 * agent builds a natural-language goal string and delegates to it.
 */
@Singleton
class WhatsAppAgent @Inject constructor(
    private val repository: AgentRepository,
    private val riskEngine: RiskEngine
) : WorkerAgent {

    private val TAG = "WhatsAppAgent"
    private val supportedTypes = setOf(
        "whatsapp.send_message",
        "whatsapp.search_contact",
        "whatsapp.reply"
    )

    override fun canHandle(taskType: String): Boolean = taskType in supportedTypes

    override fun validate(task: Task): ValidationResult {
        val errors = mutableListOf<String>()
        val contact = task.params["contact"]
        val message = task.params["message"] ?: task.params["text"]

        if (contact.isNullOrBlank()) errors.add("contact param required")
        if (task.type == "whatsapp.send_message" && message.isNullOrBlank()) {
            errors.add("message param required for send_message")
        }
        return if (errors.isEmpty()) ValidationResult(valid = true)
        else ValidationResult(valid = false, errors = errors)
    }

    override suspend fun execute(task: Task): TaskResult {
        val validation = validate(task)
        if (!validation.valid) {
            return TaskResult(success = false, message = validation.errors.joinToString("; "))
        }

        val risk = riskEngine.evaluate(confidence = 1.0f, intentName = task.type)
        if (risk.blocked) {
            return TaskResult(success = false, message = risk.reason)
        }

        if (!AutoAgentAccessibilityService.isAvailable()) {
            return TaskResult(success = false, message = "Accessibility Service ON nahi hai")
        }

        val contact = task.params["contact"] ?: ""
        val message = task.params["message"] ?: task.params["text"] ?: ""

        val goal = when (task.type) {
            "whatsapp.send_message" -> "WhatsApp pe $contact ko '$message' bolo"
            "whatsapp.reply" -> "WhatsApp pe $contact ko '$message' bolo"
            "whatsapp.search_contact" -> "WhatsApp pe $contact search karo"
            else -> return TaskResult(success = false, message = "Unsupported type: ${task.type}")
        }

        val logId = repository.saveLog(
            ExecutionLogEntity(
                taskId = task.id.hashCode().toLong(),
                taskName = "WhatsApp: $contact",
                status = RunStatus.RUNNING.name,
                totalSteps = 1
            )
        )

        var agentSuccess = false
        var agentMessage = "Timeout"

        val agent = ReactAgent()
        agent.onDone = { success, msg -> agentSuccess = success; agentMessage = msg }

        val completed = withTimeoutOrNull(45_000) {
            repeat(3) { attempt ->
                if (agentSuccess) return@repeat
                L.d(TAG, "Attempt ${attempt + 1}/3 for goal: $goal")
                agent.execute(goal)
                if (agentSuccess) return@withTimeoutOrNull true
            }
            agentSuccess
        }

        val finalSuccess = completed == true && agentSuccess
        repository.updateLog(
            logId = logId,
            status = if (finalSuccess) RunStatus.SUCCESS else RunStatus.FAILED,
            stepsCompleted = if (finalSuccess) 1 else 0,
            failureReason = if (!finalSuccess) agentMessage else null
        )

        return TaskResult(
            success = finalSuccess,
            message = if (finalSuccess) "Message sent to $contact" else "Failed: $agentMessage",
            details = mapOf("contact" to contact, "goal" to goal)
        )
    }
}
