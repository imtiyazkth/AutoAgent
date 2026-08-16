package com.autoagent.personal.agent

import android.util.Log
import com.autoagent.personal.actions.ActionEngine
import com.autoagent.personal.actions.AgentAction
import com.autoagent.personal.actions.ActionResult
import com.autoagent.personal.ai.AgentGoal
import com.autoagent.personal.ai.RecoveryEngine
import com.autoagent.personal.ai.SubGoal
import com.autoagent.personal.ai.VerificationEngine
import com.autoagent.personal.learning.ExperienceRecorder
import com.autoagent.personal.perception.ScreenObserver
import com.autoagent.personal.safety.RiskEngine
import com.autoagent.personal.service.accessibility.AutoAgentAccessibilityService
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

class AgentLoop(
    private val actionEngine: ActionEngine,
    private val screenObserver: ScreenObserver,
    private val verificationEngine: VerificationEngine,
    private val recoveryEngine: RecoveryEngine,
    private val riskEngine: RiskEngine,
    private val experienceRecorder: ExperienceRecorder
) {
    private val TAG = "AgentLoop"
    private val MAX_STEPS = 40
    private val MAX_FAILURES = 4

    var onStateChange: ((AgentState) -> Unit)? = null
    var onSubGoalStart: ((String) -> Unit)? = null
    var onSubGoalDone: ((String, Boolean) -> Unit)? = null
    var onDone: ((Boolean, String) -> Unit)? = null

    private var shouldStop = false
    fun stop() { shouldStop = true }

    suspend fun run(session: AgentSession, goal: AgentGoal) {
        shouldStop = false
        var consecutiveFailures = 0
        var stepCount = 0

        Log.i(TAG, "▶ ${session.id} — ${goal.description}")
        onStateChange?.invoke(AgentState.RUNNING)

        if (goal.subGoals.isEmpty()) {
            onDone?.invoke(false, "Samajh nahi aaya — command unclear")
            return
        }

        for (subGoal in goal.subGoals) {
            if (!coroutineContext.isActive || shouldStop) {
                onDone?.invoke(false, "Cancelled"); return
            }
            if (AutoAgentAccessibilityService.emergencyStop.value) {
                onDone?.invoke(false, "Emergency stop"); return
            }
            if (stepCount >= MAX_STEPS) {
                onDone?.invoke(false, "Max steps reached"); return
            }
            if (consecutiveFailures >= MAX_FAILURES) {
                onDone?.invoke(false, "Too many failures"); return
            }
            stepCount++

            onSubGoalStart?.invoke(subGoal.description)
            onStateChange?.invoke(AgentState.RUNNING)

            // RISK CHECK
            val actionRisk = riskEngine.evaluateAction(subGoal.action)
            if (actionRisk.blocked) {
                onDone?.invoke(false, "Blocked: ${actionRisk.reason}"); return
            }

            // OBSERVE
            onStateChange?.invoke(AgentState.OBSERVING)
            val snapshot = screenObserver.observe()
            Log.d(TAG, "Screen: ${snapshot.packageName} | ${snapshot.topTexts.take(50)}")

            // ACT
            val result = actionEngine.execute(subGoal.action)
            Log.d(TAG, "Result: ${result.status} — ${result.message}")

            if (subGoal.waitAfterMs > 0) delay(subGoal.waitAfterMs)

            // VERIFY
            onStateChange?.invoke(AgentState.VERIFYING)
            val verification = verificationEngine.verify(
                expectedOutcome = subGoal.expectedOutcome,
                actionResult = result,
                snapshot = if (subGoal.expectedOutcome != null) screenObserver.observe() else null
            )

            // RECORD
            experienceRecorder.record(
                sessionId = session.id,
                goalDesc = goal.description,
                subGoalDesc = subGoal.description,
                action = subGoal.action,
                result = result,
                verified = verification.success
            )

            if (verification.success) {
                consecutiveFailures = 0
                onSubGoalDone?.invoke(subGoal.description, true)
                Log.d(TAG, "✅ ${subGoal.description}")
            } else {
                consecutiveFailures++
                onSubGoalDone?.invoke(subGoal.description, false)
                Log.w(TAG, "❌ ${subGoal.description} — ${verification.reason}")

                if (subGoal.critical && !verification.recoverable) {
                    onDone?.invoke(false, "Critical step failed: ${subGoal.description}"); return
                }

                if (subGoal.retryable && verification.recoverable) {
                    onStateChange?.invoke(AgentState.RECOVERING)
                    val recovery = recoveryEngine.recover(
                        failedSubGoal = subGoal,
                        verificationResult = verification,
                        attemptCount = consecutiveFailures - 1,
                        targetPkg = goal.targetPkg
                    )
                    if (recovery.success) consecutiveFailures = 0
                }
            }
        }

        onStateChange?.invoke(AgentState.COMPLETED)
        onDone?.invoke(true, "Ho gaya! ✅")
        Log.i(TAG, "✅ Complete: ${goal.description}")
    }
}
