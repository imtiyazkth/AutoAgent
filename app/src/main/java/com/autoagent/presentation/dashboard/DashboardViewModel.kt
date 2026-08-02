package com.autoagent.presentation.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autoagent.data.db.ExecutionLogEntity
import com.autoagent.data.db.TaskEntity
import com.autoagent.data.repository.AgentRepository
import com.autoagent.domain.model.RunStatus
import com.autoagent.domain.model.StepLog
import com.autoagent.util.isAccessibilityEnabled
import com.autoagent.service.accessibility.AutoAgentAccessibilityService
import com.autoagent.service.scheduler.TaskExecutorWorker
import com.autoagent.util.GsonHelper
import com.autoagent.util.PinManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class PendingAction {
    NONE, OPEN_ADD_TASK, OPEN_EDIT_TASK, DELETE_TASK, TOGGLE_TASK, RUN_TASK
}

sealed class NavigationEvent {
    object GoToAddTask : NavigationEvent()
    data class GoToEditTask(val taskId: Long) : NavigationEvent()
}

data class DashboardUiState(
    val isPinSetup: Boolean = false,
    val isPinVerified: Boolean = false,
    val showPinSetup: Boolean = false,
    val showPinVerify: Boolean = false,
    val pinError: String? = null,
    val allPaused: Boolean = false,
    val accessibilityEnabled: Boolean = false,
    val isLoading: Boolean = false,
    val lastRunResult: String? = null,
    val error: String? = null
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: AgentRepository,
    private val pinManager: PinManager,
    private val gsonHelper: GsonHelper,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val handler = CoroutineExceptionHandler { _, t ->
        _uiState.update { it.copy(error = t.message, isLoading = false) }
    }

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    // Navigation events — separate from UiState (no lambdas!)
    private val _navEvent = MutableSharedFlow<NavigationEvent>()
    val navEvent: SharedFlow<NavigationEvent> = _navEvent.asSharedFlow()

    val tasks: StateFlow<List<TaskEntity>> = repository.getAllTasks()
        .catch { e -> _uiState.update { it.copy(error = e.message) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentLogs: StateFlow<List<ExecutionLogEntity>> = repository.getRecentLogs()
        .catch { }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isRunning = AutoAgentAccessibilityService.isRunning
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val currentStep = AutoAgentAccessibilityService.currentStep
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private var pendingTaskId: Long = -1L
    private var pendingAction = PendingAction.NONE

    init {
        checkPinSetup()
        refreshAccessibility()
    }

    private fun checkPinSetup() {
        viewModelScope.launch(handler) {
            val setup = runCatching { pinManager.isPinSetup() }.getOrDefault(false)
            _uiState.update { it.copy(isPinSetup = setup, showPinSetup = !setup) }
        }
    }

    fun setupPin(pin: String) {
        viewModelScope.launch(handler) {
            runCatching { pinManager.setupPin(pin) }
                .getOrElse { e -> Result.failure(e) }
                .fold(
                    onSuccess = {
                        _uiState.update { it.copy(isPinSetup = true, showPinSetup = false,
                            showPinVerify = false, pinError = null) }
                    },
                    onFailure = { e ->
                        _uiState.update { it.copy(pinError = e.message) }
                    }
                )
        }
    }

    fun verifyPin(pin: String) {
        viewModelScope.launch(handler) {
            val correct = runCatching { pinManager.verifyPin(pin) }.getOrDefault(false)
            if (correct) {
                _uiState.update { it.copy(showPinVerify = false, pinError = null, isPinVerified = true) }
                runCatching { executePending() }
            } else {
                _uiState.update { it.copy(pinError = "Galat PIN — dobara try karo") }
            }
        }
    }

    private suspend fun executePending() {
        try {
            when (pendingAction) {
                PendingAction.OPEN_ADD_TASK  -> _navEvent.emit(NavigationEvent.GoToAddTask)
                PendingAction.OPEN_EDIT_TASK -> _navEvent.emit(NavigationEvent.GoToEditTask(pendingTaskId))
                PendingAction.DELETE_TASK    -> deleteTask(pendingTaskId)
                PendingAction.TOGGLE_TASK    -> toggleTask(pendingTaskId)
                PendingAction.RUN_TASK       -> runTask(pendingTaskId)
                PendingAction.NONE           -> {}
            }
        } finally {
            pendingAction = PendingAction.NONE
            pendingTaskId = -1L
        }
    }

    fun requestAddTask() {
        pendingAction = PendingAction.OPEN_ADD_TASK
        if (_uiState.value.isPinSetup)
            _uiState.update { it.copy(showPinVerify = true, pinError = null) }
        else
            _uiState.update { it.copy(showPinSetup = true) }
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

    private suspend fun deleteTask(id: Long) {
        runCatching {
            TaskExecutorWorker.cancelTask(context, id)
            repository.deleteTask(id)
            _uiState.update { it.copy(lastRunResult = "✅ Task deleted") }
        }.onFailure { e -> _uiState.update { it.copy(error = e.message) } }
    }

    private suspend fun toggleTask(id: Long) {
        runCatching {
            val t = repository.getTask(id) ?: return
            repository.setTaskEnabled(id, !t.isEnabled)
            if (!t.isEnabled) TaskExecutorWorker.scheduleTask(context, gsonHelper.entityToTask(t))
            else TaskExecutorWorker.cancelTask(context, id)
        }.onFailure { e -> _uiState.update { it.copy(error = e.message) } }
    }

    private suspend fun runTask(id: Long) {
        runCatching {
            val entity = repository.getTask(id) ?: run {
                _uiState.update { it.copy(error = "Task nahi mila") }; return
            }
            val task = gsonHelper.entityToTask(entity)
            val svc = AutoAgentAccessibilityService.getInstance() ?: run {
                _uiState.update { it.copy(error = "Accessibility Service enable karo\nSettings → Accessibility → AutoAgent Automation → ON") }
                return
            }
            val logs = mutableListOf<StepLog>()
            val status = svc.executeSteps(task.steps) { logs.add(it) }
            _uiState.update { it.copy(
                lastRunResult = if (status == RunStatus.SUCCESS)
                    "✅ '${task.name}' successful!"
                else "❌ '${task.name}' failed — ${logs.firstOrNull { !it.success }?.errorMessage}"
            )}
        }.onFailure { e -> _uiState.update { it.copy(error = e.message) } }
    }

    fun emergencyStop() {
        runCatching {
            AutoAgentAccessibilityService.getInstance()?.triggerEmergencyStop()
                ?: run { AutoAgentAccessibilityService.emergencyStop.value = true }
            _uiState.update { it.copy(lastRunResult = "🛑 Stopped!") }
        }
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
        _uiState.update { it.copy(accessibilityEnabled = enabled) }
    }

    fun dismissError() { _uiState.update { it.copy(error = null) } }
    fun dismissResult() { _uiState.update { it.copy(lastRunResult = null) } }
    fun dismissPinError() { _uiState.update { it.copy(pinError = null) } }
    fun dismissPinVerify() {
        _uiState.update { it.copy(showPinVerify = false, pinError = null) }
        pendingAction = PendingAction.NONE; pendingTaskId = -1L
    }
}
