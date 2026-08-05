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

data class DashboardUiState(
    // PIN — only for delete/run/toggle actions
    val isPinSetup: Boolean = false,
    val showPinSetup: Boolean = false,
    val showPinVerify: Boolean = false,
    val pinError: String? = null,
    val pendingDeleteId: Long? = null,
    val pendingRunId: Long? = null,
    val pendingToggleId: Long? = null,
    // Other state
    val allPaused: Boolean = false,
    val accessibilityEnabled: Boolean = false,
    val lastRunResult: String? = null,
    val error: String? = null,
    // Navigation — state based
    val navigateTo: String? = null
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: AgentRepository,
    private val pinManager: PinManager,
    private val gsonHelper: GsonHelper,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val handler = CoroutineExceptionHandler { _, t ->
        L.e("DashboardVM", "Exception", t)
        _state.update { it.copy(error = t.message) }
    }

    private val _state = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _state.asStateFlow()

    val tasks: StateFlow<List<TaskEntity>> = repository.getAllTasks()
        .catch { L.e("DashboardVM", "tasks", it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentLogs: StateFlow<List<ExecutionLogEntity>> = repository.getRecentLogs()
        .catch { }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isRunning = AutoAgentAccessibilityService.isRunning
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val currentStep = AutoAgentAccessibilityService.currentStep
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        L.d("DashboardVM", "init")
        checkPinSetup()
        refreshAccessibility()
    }

    private fun checkPinSetup() {
        viewModelScope.launch(handler) {
            val setup = runCatching { pinManager.isPinSetup() }.getOrDefault(false)
            L.d("DashboardVM", "pinSetup=$setup")
            _state.update { it.copy(isPinSetup = setup, showPinSetup = !setup) }
        }
    }

    fun setupPin(pin: String) {
        viewModelScope.launch(handler) {
            runCatching { pinManager.setupPin(pin) }.fold(
                onSuccess = { result ->
                    result.fold(
                        onSuccess = {
                            L.d("DashboardVM", "PIN setup OK")
                            _state.update { it.copy(isPinSetup = true, showPinSetup = false, pinError = null) }
                        },
                        onFailure = { e -> _state.update { it.copy(pinError = e.message) } }
                    )
                },
                onFailure = { e -> _state.update { it.copy(pinError = e.message) } }
            )
        }
    }

    // =========================================
    // NAVIGATION — NO PIN REQUIRED
    // + button → direct to task builder
    // =========================================
    fun openAddTask() {
        L.d("DashboardVM", "openAddTask - direct, no PIN")
        _state.update { it.copy(navigateTo = "add_task") }
    }

    fun openEditTask(id: Long) {
        L.d("DashboardVM", "openEditTask $id - direct, no PIN")
        _state.update { it.copy(navigateTo = "edit_task_$id") }
    }

    fun onNavigationHandled() {
        L.d("DashboardVM", "navigation handled")
        _state.update { it.copy(navigateTo = null) }
    }

    // =========================================
    // ACTIONS THAT NEED PIN
    // =========================================
    fun requestDelete(id: Long) {
        if (_state.value.isPinSetup) {
            _state.update { it.copy(showPinVerify = true, pendingDeleteId = id, pinError = null) }
        } else {
            _state.update { it.copy(showPinSetup = true) }
        }
    }

    fun requestToggle(id: Long) {
        if (_state.value.isPinSetup) {
            _state.update { it.copy(showPinVerify = true, pendingToggleId = id, pinError = null) }
        } else {
            _state.update { it.copy(showPinSetup = true) }
        }
    }

    fun requestRun(id: Long) {
        if (_state.value.isPinSetup) {
            _state.update { it.copy(showPinVerify = true, pendingRunId = id, pinError = null) }
        } else {
            _state.update { it.copy(showPinSetup = true) }
        }
    }

    fun verifyPin(pin: String) {
        viewModelScope.launch(handler) {
            val correct = runCatching { pinManager.verifyPin(pin) }.getOrDefault(false)
            L.d("DashboardVM", "verifyPin correct=$correct")
            if (correct) {
                // Snapshot IDs before clearing state
                val pendingDelete = _state.value.pendingDeleteId
                val pendingToggle = _state.value.pendingToggleId
                val pendingRun = _state.value.pendingRunId
                _state.update { it.copy(showPinVerify = false, pinError = null,
                    pendingDeleteId = null, pendingRunId = null, pendingToggleId = null) }
                pendingDelete?.let { deleteTask(it) }
                pendingToggle?.let { toggleTask(it) }
                pendingRun?.let { runTask(it) }
            } else {
                L.d("DashboardVM", "Wrong PIN entered")
                _state.update { it.copy(pinError = "Galat PIN — dobara try karo") }
            }
        }
    }

    fun dismissPinVerify() {
        _state.update { it.copy(showPinVerify = false, pinError = null,
            pendingDeleteId = null, pendingRunId = null, pendingToggleId = null) }
    }

    private suspend fun deleteTask(id: Long) {
        runCatching {
            TaskExecutorWorker.cancelTask(context, id)
            repository.deleteTask(id)
            _state.update { it.copy(lastRunResult = "✅ Task deleted") }
        }.onFailure { L.e("DashboardVM", "delete", it) }
    }

    private suspend fun toggleTask(id: Long) {
        runCatching {
            val t = repository.getTask(id) ?: return
            repository.setTaskEnabled(id, !t.isEnabled)
            if (!t.isEnabled) TaskExecutorWorker.scheduleTask(context, gsonHelper.entityToTask(t))
            else TaskExecutorWorker.cancelTask(context, id)
        }.onFailure { L.e("DashboardVM", "toggle", it) }
    }

    private suspend fun runTask(id: Long) {
        if (AutoAgentAccessibilityService.isRunning.value) {
            L.d("DashboardVM", "runTask blocked — service already running")
            _state.update { it.copy(error = "Ek task pehle se chal raha hai — ruko ya Stop dabao") }
            return
        }
        runCatching {
            val entity = repository.getTask(id) ?: run {
                _state.update { it.copy(error = "Task nahi mila (id=$id)") }; return
            }
            val svc = AutoAgentAccessibilityService.getInstance() ?: run {
                _state.update { it.copy(error = "Accessibility ON karo:\nSettings → Accessibility → AutoAgent → ON\n\nPhir wapas aao aur Run karo.") }; return
            }
            val logs = mutableListOf<StepLog>()
            val status = svc.executeSteps(gsonHelper.entityToTask(entity).steps) { logs.add(it) }
            _state.update { it.copy(
                lastRunResult = if (status == RunStatus.SUCCESS) "✅ Task run hua!"
                else "❌ Failed: ${logs.firstOrNull { !it.success }?.errorMessage}"
            )}
        }.onFailure { e ->
            L.e("DashboardVM", "runTask", e)
            _state.update { it.copy(error = e.message) }
        }
    }

    fun emergencyStop() {
        AutoAgentAccessibilityService.getInstance()?.triggerEmergencyStop()
            ?: run { AutoAgentAccessibilityService.emergencyStop.value = true }
        _state.update { it.copy(lastRunResult = "🛑 Stopped!") }
    }

    fun pauseAll() {
        viewModelScope.launch(handler) {
            tasks.value.forEach { runCatching { TaskExecutorWorker.cancelTask(context, it.id) } }
            _state.update { it.copy(allPaused = true) }
        }
    }

    fun resumeAll() {
        viewModelScope.launch(handler) {
            tasks.value.filter { it.isEnabled }.forEach {
                runCatching { TaskExecutorWorker.scheduleTask(context, gsonHelper.entityToTask(it)) }
            }
            _state.update { it.copy(allPaused = false) }
        }
    }

    fun refreshAccessibility() {
        val en = runCatching { isAccessibilityEnabled(context) }.getOrDefault(false)
        _state.update { it.copy(accessibilityEnabled = en) }
    }

    fun dismissError() { _state.update { it.copy(error = null) } }
    fun dismissResult() { _state.update { it.copy(lastRunResult = null) } }
}
