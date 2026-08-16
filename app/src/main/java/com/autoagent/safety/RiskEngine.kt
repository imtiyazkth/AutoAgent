package com.autoagent.personal.safety

import com.autoagent.personal.actions.AgentAction
import javax.inject.Inject
import javax.inject.Singleton

enum class RiskLevel { LOW, MEDIUM, HIGH, CRITICAL }

data class RiskAssessment(
    val level: RiskLevel,
    val blocked: Boolean,
    val requiresConfirmation: Boolean,
    val reason: String
)

@Singleton
class RiskEngine @Inject constructor() {

    fun evaluate(confidence: Float, intentName: String): RiskAssessment {
        if (confidence < 0.3f) return RiskAssessment(
            RiskLevel.HIGH, blocked = true, requiresConfirmation = false,
            reason = "Confidence too low ($confidence). Please rephrase."
        )
        return RiskAssessment(RiskLevel.LOW, false, false, "Safe to proceed")
    }

    fun evaluateAction(action: AgentAction): RiskAssessment = when (action) {
        is AgentAction.EmergencyStop ->
            RiskAssessment(RiskLevel.LOW, false, false, "Emergency stop — always allowed")
        is AgentAction.TypeText ->
            if (action.text.length > 500)
                RiskAssessment(RiskLevel.MEDIUM, false, true, "Very long text")
            else RiskAssessment(RiskLevel.LOW, false, false, "Text input")
        else -> RiskAssessment(RiskLevel.LOW, false, false, "Standard action")
    }
}
