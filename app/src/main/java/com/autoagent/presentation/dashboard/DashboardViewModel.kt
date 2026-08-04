package com.autoagent.presentation.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autoagent.data.db.ExecutionLogEntity
import com.autoagent.data.db.TaskEntity
import com.autoagent.data.repository.AgentRepository
import com.autoagent.domain.model.RunStatus
import com.autoagent.domain.model.StepLog
import com.autoagent.service.accessibility.AutoAgentAccessibilityService
import com.autoagent.service.scheduler.TaskExecutorWorker
import com.autoagent.util.GsonHelper
import com.autoagent.util.L
import com.autoagent.util.PinManager
import com.autoagent.util.isAccessibilityEnabled
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class PendingAction { NONE, OPEN_ADD_TASK, OPEN_EDIT_TASK, DELETE_TASK, TOGGLE_TASK, RUN_TASK }

// STATE-BASED navigation — no SharedFlow timing issues
data class DashboardUiState(
    val isPinSetup: Boolean = false,
    val showPinSetup: Boolean = false,
    val showPinVerify: Boolean = false,
    val pinError: String? = null,
    val allPaused: Boolean = false,
    val accessibilityEnabled: Boolean = false,
    val isLoading: Boolean = false,
    val lastRunResult: String? = null,
    val error: String? = null,
    // Navigation via state — reliable on all Android versions
    val navigateTo: String? = null,
    val navigateTaskId: Long? = null
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: AgentRepository,
    private val pinManager: PinManager,
    private val gsonHelper: GsonHelper,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val handler = CoroutineExceptionHandler { _, t ->
        L.e("DashboardVM", "Unhandled exception", t)
        _uiState.update { it.copy(error = "Error: ${t.message}", isLoading = false) }
    }

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    val tasks: StateFlow<List<TaskEntity>> = repository.getAllTasks()
        .catch { e -> L.e("DashboardVM", "tasks error", e) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentLogs: StateFlow<List<ExecutionLogEntity>> = repository.getRecentLogs()
        .catch { }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isRunning = AutoAgentAccessibilityService.isRunning
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val currentStep = AutoAgentAccessibilityService.currentStep
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private var pendingAction = PendingAction.NONE
    private var pendingTaskId: Long = -1L

    init {
        L.d("DashboardVM", "init")
        checkPinSetup()
        refreshAccessibility()
    }

    private fun checkPinSetup() {
        viewModelScope.launch(handler) {
            try {
                val setup = pinManager.isPinSetup()
                L.d("DashboardVM", "PIN setup: $setup")
                _uiState.update { it.copy(isPinSetup = setup, showPinSetup = !setup) }
            } catch (e: Exception) {
                L.e("DashboardVM", "checkPinSetup error", e)
                _uiState.update { it.copy(isPinSetup = false, showPinSetup = true) }
            }
        }
    }

    fun setupPin(pin: String) {
        viewModelScope.launch(handler) {
            try {
                L.d("DashboardVM", "setupPin called")
                pinManager.setupPin(pin).fold(
                    onSuccess = {
                        L.d("DashboardVM", "PIN setup success")
                        _uiState.update {
                            it.copy(isPinSetup = true, showPinSetup = false,
                                showPinVerify = false, pinError = null)
                        }
                    },
                    onFailure = { e ->
                        L.e("DashboardVM", "PIN setup failed", e)
                        _uiState.update { it.copy(pinError = e.message) }
                    }
                )
            } catch (e: Exception) {
                L.e("DashboardVM", "setupPin exception", e)
                _uiState.update { it.copy(pinError = "PIN setup failed: ${e.message}") }
            }
        }
    }

    fun verifyPin(pin: String) {
        viewModelScope.launch(handler) {
            try {
                L.d("DashboardVM", "verifyPin called, pendingAction=$pendingAction")
                val correct = pinManager.verifyPin(pin)
                L.d("DashboardVM", "PIN correct: $correct")

                if (correct) {
                    // Close dialog FIRST
                    _uiState.update { it.copy(showPinVerify = false, pinError = null) }

                    // Small delay to ensure dialog closes before navigation
                    kotlinx.coroutines.delay(100)

                    // Now navigate via STATE (not SharedFlow)
                    val target = when (pendingAction) {
                        PendingAction.OPEN_ADD_TASK -> "add_task"
                        PendingAction.OPEN_EDIT_TASK -> "edit_task"
                        PendingAction.DELETE_TASK -> { deleteTaskInternal(pendingTaskId); null }
                        PendingAction.TOGGLE_TASK -> { toggleTaskInternal(pendingTaskId); null }
                        PendingAction.RUN_TASK -> { runTaskInternal(pendingTaskId); null }
                        PendingAction.NONE -> null
                    }

                    L.d("DashboardVM", "navigating to: $target")
                    val tid = if (pendingAction == PendingAction.OPEN_EDIT_TASK) pendingTaskId else null

                    // Reset pending BEFORE navigation update
                    pendingAction = PendingAction.NONE
                    pendingTaskId = -1L

                    if (target != null) {
                        _uiState.update { it.copy(navigateTo = target, navigateTaskId = tid) }
                    }
                } else {
                    L.d("DashboardVM", "Wrong PIN")
                    _uiState.update { it.copy(pinError = "Galat PIN — dobara try karo") }
                }
            } catch (e: Exception) {
                L.e("DashboardVM", "verifyPin exception", e)
                _uiState.update { it.copy(pinError = "Verification failed: ${e.message}", showPinVerify = false) }
            }
        }
    }

    // Called by UI after navigation is handled
    fun onNavigationHandled() {
        L.d("DashboardVM", "navigation handled, clearing navigateTo")
        _uiState.update { it.copy(navigateTo = null, navigateTaskId = null) }
    }

    fun requestAddTask() {
        L.d("DashboardVM", "requestAddTask")
        pendingAction = PendingAction.OPEN_ADD_TASK
        pendingTaskId = -1L
        if (_uiState.value.isPinSetup) {
            _uiState.update { it.copy(showPinVerify = true, pinError = null) }
        } else {
            _uiState.update { it.copy(showPinSetup = true) }
        }
    }

    fun requestEditTask(id: Long) {
        pendingAction = PendingAction.OPEN_EDIT_TASK; pendingTaskId = id
        _uiState.update { it.copy(showPinVerify = true, pinError = null) }
    }

    fun requestDelete(id: Long) {
        pendingAction = PendingAction.DELETE_TASK; pendingTaskId = id
        _uiState.update { it.copy(showPinVerify = true, pinError = null) }
    }

    fun requestToggle(id: Long) {
        pendingAction = PendingAction.TOGGLE_TASK; pendingTaskId = id
        _uiState.update { it.copy(showPinVerify = true, pinError = null) }
    }

    fun requestRun(id: Long) {
        pendingAction = PendingAction.RUN_TASK; pendingTaskId = id
        _uiState.update { it.copy(showPinVerify = true, pinError = null) }
    }

    private suspend fun deleteTaskInternal(id: Long) {
        try {
            TaskExecutorWorker.cancelTask(context, id)
            repository.deleteTask(id)
            _uiState.update { it.copy(lastRunResult = "✅ Task deleted") }
        } catch (e: Exception) {
            L.e("DashboardVM", "delete error", e)
            _uiState.update { it.copy(error = e.message) }
        }
    }

    private suspend fun toggleTaskInternal(id: Long) {
        try {
            val t = repository.getTask(id) ?: return
            repository.setTaskEnabled(id, !t.isEnabled)
            if (!t.isEnabled) TaskExecutorWorker.scheduleTask(context, gsonHelper.entityToTask(t))
            else TaskExecutorWorker.cancelTask(context, id)
        } catch (e: Exception) {
            L.e("DashboardVM", "toggle error", e)
            _uiState.update { it.copy(error = e.message) }
        }
    }

    private suspend fun runTaskInternal(id: Long) {
        try {
            L.d("DashboardVM", "runTask $id")
            val entity = repository.getTask(id) ?: run {
                _uiState.update { it.copy(error = "Task nahi mila") }; return
            }
            val task = gsonHelper.entityToTask(entity)
            val svc = AutoAgentAccessibilityService.getInstance() ?: run {
                L.e("DashboardVM", "Service null — accessibility not running")
                _uiState.update { it.copy(error = "Accessibility Service enable karo:\nSettings → Accessibility → AutoAgent Automation → ON") }
                return
            }
            if (!AutoAgentAccessibilityService.isServiceConnected.value) {
                _uiState.update { it.copy(error = "Service connected nahi hai — wait karo ya restart karo") }
                return
            }
            val logs = mutableListOf<StepLog>()
            val status = svc.executeSteps(task.steps) { logs.add(it) }
            L.d("DashboardVM", "task run result: $status")
            _uiState.update { it.copy(
                lastRunResult = if (status == RunStatus.SUCCESS) "✅ '${task.name}' successful!"
                else "❌ Failed: ${logs.firstOrNull { !it.success }?.errorMessage ?: "Unknown"}"
            )}
        } catch (e: Exception) {
            L.e("DashboardVM", "runTask error", e)
            _uiState.update { it.copy(error = "Run failed: ${e.message}") }
        }
    }

    fun emergencyStop() {
        L.d("DashboardVM", "emergencyStop")
        AutoAgentAccessibilityService.getInstance()?.triggerEmergencyStop()
            ?: run { AutoAgentAccessibilityService.emergencyStop.value = true }
        _uiState.update { it.copy(lastRunResult = "🛑 Stopped!") }
    }

    fun pauseAll() {
        viewModelScope.launch(handler) {
            tasks.value.forEach { runCatching { TaskExecutorWorker.cancelTask(context, it.id) } }
            _uiState.update { it.copy(allPaused = true) }
        }
    }

    fun resumeAll() {
        viewModelScope.launch(handler) {
            tasks.value.filter { it.isEnabled }.forEach {
                runCatching { TaskExecutorWorker.scheduleTask(context, gsonHelper.entityToTask(it)) }
            }
            _uiState.update { it.copy(allPaused = false) }
        }
    }

    fun refreshAccessibility() {
        val enabled = runCatching { isAccessibilityEnabled(context) }.getOrDefault(false)
        L.d("DashboardVM", "accessibility enabled: $enabled")
        _uiState.update { it.copy(accessibilityEnabled = enabled) }
    }

    fun dismissError() { _uiState.update { it.copy(error = null) } }
    fun dismissResult() { _uiState.update { it.copy(lastRunResult = null) } }
    fun dismissPinError() { _uiState.update { it.copy(pinError = null) } }
    fun dismissPinVerify() {
        L.d("DashboardVM", "dismissPinVerify")
        pendingAction = PendingAction.NONE; pendingTaskId = -1L
        _uiState.update { it.copy(showPinVerify = false, pinError = null) }
    }
}
