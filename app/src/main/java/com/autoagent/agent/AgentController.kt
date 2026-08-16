package com.autoagent.personal.agent

import android.util.Log
import com.autoagent.personal.actions.ActionEngine
import com.autoagent.personal.ai.GoalDecomposer
import com.autoagent.personal.ai.IntentEngine
import com.autoagent.personal.ai.RecoveryEngine
import com.autoagent.personal.ai.VerificationEngine
import com.autoagent.personal.engine.ConversationEngine
import com.autoagent.personal.learning.ExperienceRecorder
import com.autoagent.personal.perception.ScreenObserver
import com.autoagent.personal.safety.RiskEngine
import com.autoagent.personal.service.accessibility.AutoAgentAccessibilityService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AgentController @Inject constructor(
    private val intentEngine: IntentEngine,
    private val goalDecomposer: GoalDecomposer,
    private val riskEngine: RiskEngine,
    private val actionEngine: ActionEngine,
    private val screenObserver: ScreenObserver,
    private val verificationEngine: VerificationEngine,
    private val recoveryEngine: RecoveryEngine,
    private val conversationEngine: ConversationEngine,
    private val experienceRecorder: ExperienceRecorder
) {
    private val TAG = "AgentController"

    private val _sessionState = MutableStateFlow<AgentState>(AgentState.IDLE)
    val sessionState: StateFlow<AgentState> = _sessionState

    var onThought: ((String) -> Unit)? = null
    var onAction:  ((String) -> Unit)? = null
    var onDone:    ((Boolean, String) -> Unit)? = null

    private var currentLoop: AgentLoop? = null

    fun stop() {
        currentLoop?.stop()
        AutoAgentAccessibilityService.getInstance()?.triggerEmergencyStop()
        _sessionState.value = AgentState.CANCELLED
    }

    suspend fun execute(rawGoal: String) {
        if (_sessionState.value.isActive()) {
            Log.w(TAG, "Already running — ignoring")
            return
        }

        _sessionState.value = AgentState.PARSING
        onThought?.invoke("💭 Samajh raha hoon...")

        // Resolve context
        val resolved = conversationEngine.resolveGoal(rawGoal)
        if (resolved != rawGoal) onThought?.invoke("💡 Resolved: $resolved")

        // PARSE
        _sessionState.value = AgentState.UNDERSTANDING
        val cmd = intentEngine.parse(resolved)
        onThought?.invoke("🎯 Intent: ${cmd.intent} (${(cmd.confidence*100).toInt()}%)")

        // RISK CHECK
        val risk = riskEngine.evaluate(cmd.confidence, cmd.intent.name)
        if (risk.blocked) {
            onDone?.invoke(false, risk.reason)
            _sessionState.value = AgentState.BLOCKED
            return
        }

        // SERVICE CHECK
        if (AutoAgentAccessibilityService.getInstance() == null) {
            onDone?.invoke(false, "Accessibility service ON nahi hai")
            _sessionState.value = AgentState.BLOCKED
            return
        }

        // PLAN
        _sessionState.value = AgentState.PLANNING
        val goal = goalDecomposer.decompose(cmd)
        onThought?.invoke("📋 ${goal.subGoals.size} steps planned")

        if (goal.subGoals.isEmpty()) {
            onDone?.invoke(false, "Samajh nahi aaya — clearly bolein")
            _sessionState.value = AgentState.FAILED
            return
        }

        // UPDATE CONTEXT
        conversationEngine.updateContext(
            app = cmd.targetPkg,
            appName = cmd.targetApp,
            query = cmd.query(),
            contact = cmd.contact(),
            goal = rawGoal
        )

        // RUN
        val session = AgentSession(goal = rawGoal, stepsTotal = goal.subGoals.size)
        val loop = AgentLoop(
            actionEngine, screenObserver, verificationEngine,
            recoveryEngine, riskEngine, experienceRecorder
        ).also { currentLoop = it }

        loop.onStateChange = { _sessionState.value = it }
        loop.onSubGoalStart = { desc -> onAction?.invoke("⚡ $desc") }
        loop.onSubGoalDone = { desc, ok -> onThought?.invoke(if (ok) "✅ $desc" else "❌ $desc") }
        loop.onDone = { success, msg ->
            _sessionState.value = if (success) AgentState.COMPLETED else AgentState.FAILED
            onDone?.invoke(success, msg)
        }

        loop.run(session, goal)
    }

    private fun AgentState.isActive() = this in setOf(
        AgentState.RUNNING, AgentState.OBSERVING, AgentState.VERIFYING,
        AgentState.RECOVERING, AgentState.PREPARING, AgentState.PLANNING
    )
}
