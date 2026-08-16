package com.autoagent.personal.ai

import com.autoagent.personal.actions.ActionResult
import com.autoagent.personal.perception.ScreenObserver
import com.autoagent.personal.perception.UiSnapshot
import javax.inject.Inject
import javax.inject.Singleton

data class VerificationResult(
    val success: Boolean,
    val confidence: Float,
    val reason: String,
    val recoverable: Boolean = true
)

@Singleton
class VerificationEngine @Inject constructor(
    private val screenObserver: ScreenObserver
) {
    fun verify(
        expectedOutcome: String?,
        actionResult: ActionResult,
        snapshot: UiSnapshot? = null
    ): VerificationResult {
        if (!actionResult.success) return VerificationResult(
            false, 0.9f, "Action failed: ${actionResult.message}",
            recoverable = actionResult.recoverable
        )
        if (expectedOutcome == null) return VerificationResult(
            true, 0.7f, "No verification target — action reported success"
        )
        val snap = snapshot ?: screenObserver.observe()
        return if (snap.hasText(expectedOutcome)) {
            VerificationResult(true, 0.95f, "Expected state found: $expectedOutcome")
        } else {
            VerificationResult(false, 0.8f,
                "Expected '$expectedOutcome' not found. Screen: ${snap.topTexts.take(60)}",
                recoverable = true)
        }
    }

    fun verifyAppLaunched(pkg: String): VerificationResult {
        val snap = screenObserver.observe()
        return if (snap.packageName == pkg)
            VerificationResult(true, 0.99f, "App launched: $pkg")
        else VerificationResult(false, 0.9f,
            "Expected $pkg but got ${snap.packageName}", recoverable = true)
    }
}
