package com.autoagent.domain.model

data class AgentTask(
    val id: Long = 0,
    val name: String,
    val description: String,
    val triggerType: TriggerType,
    val triggerTime: String?,
    val triggerDays: List<Int>,
    val intervalMinutes: Int,
    val steps: List<TaskStep>,
    val networkPolicy: NetworkPolicy,
    val mobileDataAllowed: Boolean,
    val isEnabled: Boolean,
    val requiresConfirmation: Boolean,
    val priority: Int,
    val createdAt: Long,
    val lastRunAt: Long?,
    val lastRunStatus: RunStatus?,
    val totalRuns: Int,
    val successRuns: Int
)

data class TaskStep(
    val id: Int,
    val type: ActionType,
    val targetApp: String? = null,
    val targetUrl: String? = null,
    val inputText: String? = null,
    val buttonText: String? = null,
    val waitForText: String? = null,
    val delayMs: Long = 500,
    val retryCount: Int = 0,
    val description: String = ""
)

data class StepLog(
    val stepId: Int,
    val actionType: ActionType,
    val description: String,
    val success: Boolean,
    val errorMessage: String? = null,
    val screenTextSnapshot: String? = null
)

data class DryRunPreview(
    val taskName: String,
    val steps: List<TaskStep>,
    val estimatedDurationSeconds: Int,
    val requiredPermissions: List<String>,
    val requiresInternet: Boolean,
    val requiresAccessibility: Boolean,
    val requiresContacts: Boolean
)

enum class TriggerType { MANUAL, DAILY, WEEKLY, ONE_TIME, INTERVAL, ON_BOOT }

enum class NetworkPolicy { ANY, WIFI_ONLY, WIFI_PREFERRED, MOBILE_DATA_ALLOWED, OFFLINE_ONLY, NO_INTERNET }

enum class RunStatus { PENDING, RUNNING, SUCCESS, FAILED, CANCELLED, SKIPPED, PAUSED }

enum class ActionType(val displayName: String, val emoji: String) {
    LAUNCH_APP      ("App Launch",        "🚀"),
    OPEN_URL        ("URL Open",          "🌐"),
    TAP_BUTTON      ("Button Tap",        "👆"),
    TAP_BY_LABEL    ("Label Tap",         "🏷️"),
    ENTER_TEXT      ("Text Type",         "⌨️"),
    PASTE_CLIPBOARD ("Clipboard Paste",   "📋"),
    SCROLL_DOWN     ("Scroll Down",       "⬇️"),
    SCROLL_UP       ("Scroll Up",         "⬆️"),
    WAIT_FOR_TEXT   ("Wait for Text",     "👀"),
    WAIT_SECONDS    ("Wait",              "⏱️"),
    PRESS_BACK      ("Press Back",        "◀️"),
    GO_HOME         ("Go Home",           "🏠"),
    CLOSE_APP       ("Close App",         "❌"),
    READ_TEXT       ("Read Screen",       "📖"),
    TAKE_SCREENSHOT ("Screenshot",        "📸"),
    CONFIRM_ACTION  ("Confirm",           "✅")
}
