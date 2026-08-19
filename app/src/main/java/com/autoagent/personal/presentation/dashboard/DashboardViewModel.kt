package com.autoagent.personal.presentation.dashboard

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autoagent.personal.agent.AgentController
import com.autoagent.personal.data.domain.model.RunStatus
import com.autoagent.personal.data.domain.model.TriggerType
import com.autoagent.personal.data.repository.TaskRepository
import com.autoagent.personal.data.util.GsonHelper
import com.autoagent.personal.memory.MemoryEngine
import com.autoagent.personal.service.AutoAgentAccessibilityService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

// ─── UI State ─────────────────────────────────────────────────────────────────

data class DashboardUiState(
    val isLoading: Boolean = false,
    val tasks: List<TaskDisplayItem> = emptyList(),
    val lastRunResult: String? = null,
    val error: String? = null,
    val serviceState: AutoAgentAccessibilityService.ServiceState =
        AutoAgentAccessibilityService.ServiceState.DISABLED
)

data class TaskDisplayItem(
    val id: Long,
    val name: String,
    val appName: String,
    val triggerType: String,
    val lastRunStatus: String?,
    val isRunnable: Boolean
)

// ─── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class DashboardViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: TaskRepository,
    private val gsonHelper: GsonHelper,
    private val memoryEngine: MemoryEngine
) : ViewModel() {

    private val TAG = "DashboardViewModel"

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    // AgentController is NOT injected via Hilt — see AppModule.kt for explanation.
    // It is created here and lives for the ViewModel's lifetime.
    private val agentController = AgentController(context)

    init {
        loadTasks()
        observeServiceState()
    }

    // ─── Service state observation ─────────────────────────────────────────────

    private fun observeServiceState() {
        viewModelScope.launch {
            AutoAgentAccessibilityService.state.collect { state ->
                _uiState.update { it.copy(serviceState = state) }
            }
        }
    }

    // ─── Task loading ──────────────────────────────────────────────────────────

    fun loadTasks() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val entities = withContext(Dispatchers.IO) { repository.getAllTasks() }
                val items = entities.map { entity ->
                    val task = runCatching { gsonHelper.entityToTask(entity) }.getOrNull()
                    TaskDisplayItem(
                        id = entity.id,
                        name = entity.name,
                        appName = task?.appName ?: entity.appPackage,
                        triggerType = task?.triggerType?.name ?: TriggerType.MANUAL.name,
                        lastRunStatus = entity.lastRunStatus,
                        isRunnable = true
                    )
                }
                _uiState.update { it.copy(isLoading = false, tasks = items, error = null) }
            } catch (e: Exception) {
                Log.e(TAG, "loadTasks error", e)
                _uiState.update { it.copy(isLoading = false, error = "Tasks load nahi hue: ${e.message}") }
            }
        }
    }

    // ─── Task execution ───────────────────────────────────────────────────────

    /**
     * Called after PIN is verified and user confirms they want to run [taskId].
     *
     * CRASH FIX: This function previously crashed because:
     * 1. It called ReactAgent() directly with no null-check on AccessibilityService.
     * 2. ReactAgent.execute() called Thread.sleep() which blocked Dispatchers.IO
     *    and starved the thread pool.
     * 3. Navigation happened concurrently while the coroutine was pending,
     *    causing "Cannot perform this action after onSaveInstanceState".
     *
     * The fix: use AgentController which is null-safe and properly coroutine-based.
     */
    fun runTask(taskId: Long) {
        viewModelScope.launch {
            // ── Pre-flight checks ──────────────────────────────────────────────

            // Check 1: Is the accessibility service actually connected?
            if (!AutoAgentAccessibilityService.isConnected()) {
                val state = AutoAgentAccessibilityService.state.value
                val message = when (state) {
                    AutoAgentAccessibilityService.ServiceState.DISABLED ->
                        "Accessibility Service enable karo pehle. Settings > Accessibility > AutoAgent"
                    AutoAgentAccessibilityService.ServiceState.CONNECTING ->
                        "Service connect ho rahi hai, thoda ruko..."
                    AutoAgentAccessibilityService.ServiceState.DISCONNECTED ->
                        "Service disconnect ho gayi. Accessibility settings mein jaake wapas enable karo."
                    AutoAgentAccessibilityService.ServiceState.ERROR ->
                        "Service error mein hai. App restart karo."
                    else -> "Accessibility Service available nahi hai."
                }
                _uiState.update { it.copy(error = message) }
                return@launch
            }

            // Check 2: Load the task
            val entity = withContext(Dispatchers.IO) {
                runCatching { repository.getTask(taskId) }.getOrNull()
            }
            if (entity == null) {
                _uiState.update { it.copy(error = "Task nahi mila (id=$taskId)") }
                return@launch
            }

            val task = runCatching { gsonHelper.entityToTask(entity) }.getOrElse { e ->
                _uiState.update { it.copy(error = "Task parse nahi hua: ${e.message}") }
                return@launch
            }

            // ── Execute ────────────────────────────────────────────────────────

            _uiState.update { it.copy(lastRunResult = "▶️ ${task.name} start hua...") }
            Log.i(TAG, "Running task '${task.name}' via AgentController")

            val result = agentController.execute(
                goal = task.name,
                timeoutMs = 90_000L
            )

            // ── Handle result ──────────────────────────────────────────────────

            val (statusMsg, runStatus) = when (result) {
                is AgentController.ExecutionResult.Success ->
                    "✅ ${task.name} complete!" to RunStatus.SUCCESS
                is AgentController.ExecutionResult.Failure ->
                    "⚠️ ${result.reason}" to RunStatus.FAILED
                is AgentController.ExecutionResult.ServiceNotConnected ->
                    "❌ Accessibility Service disconnect ho gayi" to RunStatus.FAILED
                is AgentController.ExecutionResult.Cancelled ->
                    "🛑 Task rok diya gaya" to RunStatus.CANCELLED
                is AgentController.ExecutionResult.Timeout ->
                    "⏱️ Task timeout — 90 seconds mein complete nahi hua" to RunStatus.FAILED
            }

            _uiState.update { it.copy(lastRunResult = statusMsg) }
            memoryEngine.saveLastCommand("Run: ${task.name} → $statusMsg")

            // Save run result to DB
            withContext(Dispatchers.IO) {
                runCatching {
                    repository.updateTaskLastRun(taskId, System.currentTimeMillis(), runStatus)
                }
            }

            // Reload task list to reflect updated status
            loadTasks()
        }
    }

    // ─── Emergency stop ───────────────────────────────────────────────────────

    fun emergencyStop() {
        agentController.triggerEmergencyStop()
        _uiState.update { it.copy(lastRunResult = "🛑 Emergency stop triggered") }
        Log.w(TAG, "Emergency stop triggered from UI")
    }

    // ─── Error dismissal ──────────────────────────────────────────────────────

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearLastResult() {
        _uiState.update { it.copy(lastRunResult = null) }
    }

    override fun onCleared() {
        super.onCleared()
        agentController.triggerEmergencyStop()
    }
}
