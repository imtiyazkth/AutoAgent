package com.autoagent.personal.actions

enum class ActionStatus {
    SUCCESS, FAILED, BLOCKED, TIMEOUT, CANCELLED,
    WAITING_FOR_USER, NOT_FOUND, PARTIAL
}

data class ActionEvidence(
    val beforeText: String? = null,
    val afterText: String? = null,
    val nodeFound: Boolean = false,
    val gestureDispatched: Boolean = false
)

data class ActionResult(
    val success: Boolean,
    val status: ActionStatus,
    val message: String? = null,
    val durationMs: Long = 0L,
    val evidence: ActionEvidence? = null,
    val recoverable: Boolean = true
) {
    companion object {
        fun success(msg: String? = null, evidence: ActionEvidence? = null, ms: Long = 0) =
            ActionResult(true, ActionStatus.SUCCESS, msg, ms, evidence, false)
        fun failed(msg: String, recoverable: Boolean = true, ms: Long = 0) =
            ActionResult(false, ActionStatus.FAILED, msg, ms, null, recoverable)
        fun blocked(msg: String) =
            ActionResult(false, ActionStatus.BLOCKED, msg, 0, null, false)
        fun timeout(msg: String = "Timed out") =
            ActionResult(false, ActionStatus.TIMEOUT, msg, 0, null, true)
        fun notFound(target: String) =
            ActionResult(false, ActionStatus.NOT_FOUND, "Not found: $target", 0, null, true)
        fun waitingForUser(reason: String) =
            ActionResult(false, ActionStatus.WAITING_FOR_USER, reason, 0, null, false)
    }
}
