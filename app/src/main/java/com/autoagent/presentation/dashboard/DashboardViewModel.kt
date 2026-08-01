package com.autoagent.presentation.dashboard

import android.content.Context
import android.provider.Settings
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

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: AgentRepository,
    private val pinManager: PinManager,
    private val gsonHelper: GsonHelper,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        _uiState.update { it.copy(error = "Error: ${throwable.message}", isLoading = false) }
    }

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<NavigationEvent>()
    val navigationEvent: SharedFlow<NavigationEvent> = _navigationEvent.asSharedFlow()

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
    private var pendingAction: PendingAction = PendingAction.NONE

    init {
        checkPinSetup()
        refreshAccessibilityStatus()
    }

    private fun checkPinSetup() {
        viewModelScope.launch(exceptionHandler) {
            try {
                val setup = pinManager.isPinSetup()
                _uiState.update { it.copy(isPinSetup = setup, showPinSetup = !setup) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isPinSetup = false, showPinSetup = true) }
            }
        }
    }

    fun setupPin(pin: String) {
        viewModelScope.launch(exceptionHandler) {
            try {
                pinManager.setupPin(pin).fold(
                    onSuccess = { _uiState.update { it.copy(isPinSetup = true, showPinSetup = false, showPinVerify = false, pinError = null) } },
                    onFailure = { e -> _uiState.update { it.copy(pinError = e.message) } }
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(pinError = "PIN setup failed: ${e.message}") }
            }
        }
    }

    fun verifyPin(pin: String) {
        viewModelScope.launch(exceptionHandler) {
            try {
                val correct = pinManager.verifyPin(pin)
                if (correct) {
                    _uiState.update { it.copy(showPinVerify = false, pinError = null, isPinVerified = true) }
                    executePendingAction()
                } else {
                    _uiState.update { it.copy(pinError = "Galat PIN — dobara try karo") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(pinError = "Verification failed: ${e.message}") }
            }
        }
    }

    private suspend fun executePendingAction() {
        try {
            when (pendingAction) {
                PendingAction.OPEN_ADD_TASK -> _navigationEvent.emit(NavigationEvent.GoToAddTask)
                PendingAction.OPEN_EDIT_TASK -> _navigationEvent.emit(NavigationEvent.GoToEditTask(pendingTaskId))
                PendingAction.DELETE_TASK -> deleteTaskInternal(pendingTaskId)
                PendingAction.TOGGLE_TASK -> toggleTaskInternal(pendingTaskId)
                PendingAction.RUN_TASK -> runTaskInternal(pendingTaskId)
                PendingAction.NONE -> {}
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(error = "Action failed: ${e.message}") }
        } finally {
            pendingAction = PendingAction.NONE
            pendingTaskId = -1L
        }
    }

    fun requestAddTask() {
        pendingAction = PendingAction.OPEN_ADD_TASK
        pendingTaskId = -1L
        if (_uiState.value.isPinSetup) {
            _uiState.update { it.copy(showPinVerify = true, pinError = null) }
        } else {
            _uiState.update { it.copy(showPinSetup = true) }
        }
    }

    fun requestEditTask(taskId: Long) {
        pendingAction = PendingAction.OPEN_EDIT_TASK
        pendingTaskId = taskId
        _uiState.update { it.copy(showPinVerify = true, pinError = null) }
    }

    fun requestDeleteTask(taskId: Long) {
        pendingAction = PendingAction.DELETE_TASK
        pendingTaskId = taskId
        _uiState.update { it.copy(showPinVerify = true, pinError = null) }
    }

    fun requestToggleTask(taskId: Long) {
        pendingAction = PendingAction.TOGGLE_TASK
        pendingTaskId = taskId
        _uiState.update { it.copy(showPinVerify = true, pinError = null) }
    }

    fun requestRunTask(taskId: Long) {
        pendingAction = PendingAction.RUN_TASK
        pendingTaskId = taskId
        _uiState.update { it.copy(showPinVerify = true, pinError = null) }
    }

    private suspend fun deleteTaskInternal(taskId: Long) {
        try {
            TaskExecutorWorker.cancelTask(context, taskId)
            repository.deleteTask(taskId)
            _uiState.update { it.copy(lastRunResult = "✅ Task delete ho gaya") }
        } catch (e: Exception) {
            _uiState.update { it.copy(error = "Delete failed: ${e.message}") }
        }
    }

    private suspend fun toggleTaskInternal(taskId: Long) {
        try {
            val task = repository.getTask(taskId) ?: return
            val newEnabled = !task.isEnabled
            repository.setTaskEnabled(taskId, newEnabled)
            if (newEnabled) {
                TaskExecutorWorker.scheduleTask(context, gsonHelper.entityToTask(task))
            } else {
                TaskExecutorWorker.cancelTask(context, taskId)
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(error = "Toggle failed: ${e.message}") }
        }
    }

    private suspend fun runTaskInternal(taskId: Long) {
        try {
            val taskEntity = repository.getTask(taskId) ?: run {
                _uiState.update { it.copy(error = "Task nahi mila") }
                return
            }
            val task = gsonHelper.entityToTask(taskEntity)
            val service = AutoAgentAccessibilityService.getInstance() ?: run {
                _uiState.update { it.copy(error = "Accessibility Service ON karo:\nSettings → Accessibility → AutoAgent → ON") }
                return
            }
            val stepLogs = mutableListOf<StepLog>()
            val status = service.executeSteps(task.steps) { log -> stepLogs.add(log) }
            _uiState.update { it.copy(
                lastRunResult = if (status == RunStatus.SUCCESS) "✅ '${task.name}' run hua!"
                else "❌ '${task.name}' fail — ${stepLogs.firstOrNull { !it.success }?.errorMessage ?: "Unknown"}"
            )}
        } catch (e: Exception) {
            _uiState.update { it.copy(error = "Run failed: ${e.message}") }
        }
    }

    fun emergencyStop() {
        try {
            AutoAgentAccessibilityService.getInstance()?.triggerEmergencyStop()
                ?: run { AutoAgentAccessibilityService.emergencyStop.value = true }
            _uiState.update { it.copy(lastRunResult = "🛑 Emergency Stop!") }
        } catch (e: Exception) {
            _uiState.update { it.copy(error = e.message) }
        }
    }

    fun pauseAll() {
        viewModelScope.launch(exceptionHandler) {
            tasks.value.forEach { runCatching { TaskExecutorWorker.cancelTask(context, it.id) } }
            _uiState.update { it.copy(allPaused = true) }
        }
    }

    fun resumeAll() {
        viewModelScope.launch(exceptionHandler) {
            tasks.value.filter { it.isEnabled }.forEach {
                runCatching { TaskExecutorWorker.scheduleTask(context, gsonHelper.entityToTask(it)) }
            }
            _uiState.update { it.copy(allPaused = false) }
        }
    }

    fun refreshAccessibilityStatus() {
        try {
            val enabled = Settings.Secure.getString(context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)?.contains(context.packageName) == true
            _uiState.update { it.copy(accessibilityEnabled = enabled) }
        } catch (e: Exception) {
            _uiState.update { it.copy(accessibilityEnabled = false) }
        }
    }

    fun dismissError() { _uiState.update { it.copy(error = null) } }
    fun dismissResult() { _uiState.update { it.copy(lastRunResult = null) } }
    fun dismissPinError() { _uiState.update { it.copy(pinError = null) } }
    fun dismissPinVerify() {
        _uiState.update { it.copy(showPinVerify = false, pinError = null) }
        pendingAction = PendingAction.NONE
        pendingTaskId = -1L
    }
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
