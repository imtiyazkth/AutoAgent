package com.autoagent.domain.model

// =============================================
// SCHEDULE / TASK MODEL
// =============================================
data class AgentTask(
    val id: Long = 0,
    val name: String,
    val description: String = "",

    // TRIGGER
    val triggerType: TriggerType,
    val triggerTime: String?,          // "08:00"
    val triggerDays: List<Int>,        // 0=Sun,1=Mon...6=Sat
    val intervalMinutes: Int = 0,      // for interval-based
    val startDate: Long? = null,
    val endDate: Long? = null,

    // STEPS
    val steps: List<TaskStep>,

    // NETWORK POLICY
    val networkPolicy: NetworkPolicy,
    val mobileDataAllowed: Boolean = false,

    // STATE
    val isEnabled: Boolean = true,
    val requiresConfirmation: Boolean = false,
    val priority: Int = 1,

    // META
    val createdAt: Long = System.currentTimeMillis(),
    val lastRunAt: Long? = null,
    val lastRunStatus: RunStatus? = null,
    val totalRuns: Int = 0,
    val successRuns: Int = 0
)

// =============================================
// TASK STEP — one action in the automation
// =============================================
data class TaskStep(
    val id: Int,
    val type: ActionType,
    val targetApp: String? = null,        // package name
    val targetUrl: String? = null,
    val buttonText: String? = null,       // "Submit", "Next"
    val inputText: String? = null,
    val delayMs: Long = 500,
    val waitForText: String? = null,      // wait until this text appears
    val scrollDirection: String? = null,  // "up", "down"
    val retryCount: Int = 2,
    val description: String = ""          // human readable
)

// =============================================
// ACTION TYPES
// =============================================
enum class ActionType(val displayName: String, val emoji: String) {
    LAUNCH_APP("App Launch karo", "📱"),
    OPEN_URL("URL open karo", "🔗"),
    TAP_BUTTON("Button tap karo", "👆"),
    TAP_BY_LABEL("Label se tap karo", "🏷️"),
    ENTER_TEXT("Text type karo", "⌨️"),
    PASTE_CLIPBOARD("Clipboard paste karo", "📋"),
    SCROLL_DOWN("Neeche scroll karo", "⬇️"),
    SCROLL_UP("Upar scroll karo", "⬆️"),
    WAIT_FOR_TEXT("Text ka intezaar karo", "⏳"),
    WAIT_SECONDS("Ruko", "⏱️"),
    READ_TEXT("Text padho", "👁️"),
    PRESS_BACK("Back press karo", "◀️"),
    GO_HOME("Home jao", "🏠"),
    CLOSE_APP("App band karo", "❌"),
    TAKE_SCREENSHOT("Screenshot lo", "📸"),
    CONFIRM_ACTION("Confirm karo", "✅")
}

// =============================================
// TRIGGER TYPES
// =============================================
enum class TriggerType(val displayName: String) {
    ONE_TIME("Ek baar"),
    DAILY("Roz"),
    WEEKLY("Hafte mein"),
    MONTHLY("Mahine mein"),
    INTERVAL("Har X minute"),
    MANUAL("Sirf manually"),
    ON_BOOT("Phone start pe")
}

// =============================================
// NETWORK POLICY
// =============================================
enum class NetworkPolicy(val displayName: String) {
    NO_INTERNET("Internet nahi chahiye"),
    WIFI_ONLY("Sirf Wi-Fi"),
    WIFI_PREFERRED("Wi-Fi pehle, data baad mein"),
    MOBILE_DATA_ALLOWED("Mobile data bhi allowed"),
    OFFLINE_ONLY("Bilkul offline")
}

// =============================================
// RUN STATUS
// =============================================
enum class RunStatus { SUCCESS, FAILED, SKIPPED, RUNNING, CANCELLED }

// =============================================
// EXECUTION LOG
// =============================================
data class ExecutionLog(
    val id: Long = 0,
    val taskId: Long,
    val taskName: String,
    val startTime: Long,
    val endTime: Long? = null,
    val status: RunStatus,
    val stepsCompleted: Int = 0,
    val totalSteps: Int = 0,
    val failureReason: String? = null,
    val networkUsed: String? = null,
    val stepLogs: List<StepLog> = emptyList()
)

data class StepLog(
    val stepId: Int,
    val actionType: ActionType,
    val description: String,
    val success: Boolean,
    val errorMessage: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

// =============================================
// APP INFO (for task builder)
// =============================================
data class InstalledApp(
    val packageName: String,
    val appName: String,
    val icon: android.graphics.drawable.Drawable?
)

// =============================================
// DRY RUN PREVIEW
// =============================================
data class DryRunPreview(
    val taskName: String,
    val stepDescriptions: List<String>,
    val estimatedDurationSeconds: Int,
    val requiresInternet: Boolean,
    val appsInvolved: List<String>,
    val warnings: List<String>
)
