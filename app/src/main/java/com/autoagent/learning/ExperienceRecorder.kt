package com.autoagent.personal.learning

import android.util.Log
import com.autoagent.personal.actions.AgentAction
import com.autoagent.personal.actions.ActionResult
import javax.inject.Inject
import javax.inject.Singleton

data class AgentExperience(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sessionId: String,
    val goalDescription: String,
    val subGoalDescription: String,
    val actionType: String,
    val actionTarget: String?,
    val success: Boolean,
    val verified: Boolean,
    val durationMs: Long,
    val errorMessage: String?,
    val timestamp: Long = System.currentTimeMillis()
)

@Singleton
class ExperienceRecorder @Inject constructor() {

    private val experiences = mutableListOf<AgentExperience>()
    private val TAG = "ExperienceRecorder"

    fun record(
        sessionId: String,
        goalDesc: String,
        subGoalDesc: String,
        action: AgentAction,
        result: ActionResult,
        verified: Boolean
    ) {
        val exp = AgentExperience(
            sessionId = sessionId,
            goalDescription = goalDesc,
            subGoalDescription = subGoalDesc,
            actionType = action::class.simpleName ?: "Unknown",
            actionTarget = extractTarget(action),
            success = result.success,
            verified = verified,
            durationMs = result.durationMs,
            errorMessage = if (!result.success) result.message else null
        )
        experiences.add(exp)
        Log.d(TAG, "Recorded: ${exp.actionType} → ${if (exp.success) "✅" else "❌"}")
    }

    fun getSuccessRate(): Float {
        if (experiences.isEmpty()) return 0f
        return experiences.count { it.success }.toFloat() / experiences.size
    }

    fun getSessionExperiences(sessionId: String): List<AgentExperience> =
        experiences.filter { it.sessionId == sessionId }

    fun summary(): String {
        val total = experiences.size
        val succeeded = experiences.count { it.success }
        return "Total: $total | Success: $succeeded"
    }

    private fun extractTarget(action: AgentAction): String? = when (action) {
        is AgentAction.LaunchApp  -> action.appName
        is AgentAction.Tap        -> action.target.primaryText?.take(30)
        is AgentAction.TypeText   -> "[text ${action.text.length}chars]"
        is AgentAction.OpenUrl    -> action.url.take(40)
        is AgentAction.Wait       -> "${action.durationMs}ms"
        else -> action::class.simpleName
    }
}
