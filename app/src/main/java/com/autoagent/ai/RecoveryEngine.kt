package com.autoagent.personal.ai

import com.autoagent.personal.actions.ActionEngine
import com.autoagent.personal.actions.AgentAction
import com.autoagent.personal.actions.ActionResult
import com.autoagent.personal.actions.ScrollDirection
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

enum class RecoveryStrategy {
    RETRY, SCROLL_AND_RETRY, BACK_AND_RETRY,
    RELAUNCH_APP, WAIT_AND_RETRY, GIVE_UP
}

@Singleton
class RecoveryEngine @Inject constructor(
    private val actionEngine: ActionEngine,
    private val screenObserver: com.autoagent.personal.perception.ScreenObserver
) {
    private val MAX_ATTEMPTS = 3

    suspend fun recover(
        failedSubGoal: SubGoal,
        verificationResult: VerificationResult,
        attemptCount: Int,
        targetPkg: String?
    ): ActionResult {
        if (attemptCount >= MAX_ATTEMPTS)
            return ActionResult.failed("Max recovery attempts reached", recoverable = false)

        val strategy = when {
            !failedSubGoal.retryable -> RecoveryStrategy.GIVE_UP
            attemptCount == 0 -> RecoveryStrategy.WAIT_AND_RETRY
            attemptCount == 1 -> when (failedSubGoal.action) {
                is AgentAction.Tap -> RecoveryStrategy.SCROLL_AND_RETRY
                else -> RecoveryStrategy.BACK_AND_RETRY
            }
            else -> RecoveryStrategy.RELAUNCH_APP
        }

        return when (strategy) {
            RecoveryStrategy.RETRY -> {
                delay(1000L * attemptCount)
                actionEngine.execute(failedSubGoal.action)
            }
            RecoveryStrategy.SCROLL_AND_RETRY -> {
                actionEngine.execute(AgentAction.Scroll(ScrollDirection.DOWN))
                delay(800)
                actionEngine.execute(failedSubGoal.action)
            }
            RecoveryStrategy.BACK_AND_RETRY -> {
                actionEngine.execute(AgentAction.Back)
                delay(1000)
                actionEngine.execute(failedSubGoal.action)
            }
            RecoveryStrategy.RELAUNCH_APP -> {
                if (targetPkg != null) {
                    actionEngine.execute(AgentAction.LaunchApp(targetPkg, targetPkg.substringAfterLast(".")))
                    delay(3000)
                    actionEngine.execute(failedSubGoal.action)
                } else ActionResult.failed("Cannot relaunch — no pkg", recoverable = false)
            }
            RecoveryStrategy.WAIT_AND_RETRY -> {
                delay(2000)
                actionEngine.execute(failedSubGoal.action)
            }
            RecoveryStrategy.GIVE_UP ->
                ActionResult.failed("Recovery gave up", recoverable = false)
        }
    }
}
