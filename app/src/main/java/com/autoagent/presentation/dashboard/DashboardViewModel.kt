package com.autoagent.personal.presentation.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autoagent.personal.agent.ReactAgent
import com.autoagent.personal.data.db.ExecutionLogEntity
import com.autoagent.personal.data.db.TaskEntity
import com.autoagent.personal.data.repository.AgentRepository
import com.autoagent.personal.memory.MemoryEngine
import com.autoagent.personal.service.accessibility.AutoAgentAccessibilityService
import com.autoagent.personal.service.scheduler.TaskExecutorWorker
import com.autoagent.personal.util.GsonHelper
import com.autoagent.personal.util.L
import com.autoagent.personal.util.PinManager
import com.autoagent.personal.util.isAccessibilityEnabled
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class DashboardUiState(
    val accessibilityEnabled: Boolean = false,
    val accessibilityServiceAlive: Boolean = false,
    val showPinSetup: Boolean = false,
    val showPinVerify: Boolean = false,
    val pinError: String? = null,
    val allPaused: Boolean = false,
    val lastRunResult: String? = null,
    val error: String? = null,
    val navigateTo: String? = null,
    val pendingTaskId: Long? = null,
    val pendingToggleId: Long? = null,
    val pendingDeleteId: Long? = null,
    val watchdogWarning: String? = null
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: AgentRepository,
    private val pinManager: PinManager,
    private val gsonHelper: GsonHelper,
    private val memoryEngine: MemoryEngine,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState

    val tasks: StateFlow<List<TaskEntity>> = repository.tasks
    val recentLogs: StateFlow<List<ExecutionLogEntity>> = repository.recentLogs
    val isRunning = AutoAgentAccessibilityService.isRunning
    val currentStep = AutoAgentAccessibilityService.currentStep

    init {
        initPin()
        startWatchdog()
    }

    private fun initPin() {
        viewModelScope.launch {
            try {
                val pinSetup = withContext(Dispatchers.IO) {
                    runCatching { pinManager.isPinSetup() }.getOrDefault(false)
                }
                if (!pinSetup) _uiState.update { it.copy(showPinSetup = true) }
            } catch (e: Exception) {
                L.e("DashVM", "initPin error", e)
            }
        }
    }

    /** Watchdog: polls accessibility health every 10 seconds */
    private fun startWatchdog() {
        viewModelScope.launch {
            while (true) {
                delay(10_000)
                val settingOn = withContext(Dispatchers.IO) {
                    isAccessibilityEnabled(context)
                }
                val serviceAlive = AutoAgentAccessibilityService.isAvailable()

                val warning = when {
                    !settingOn -> "⚠️ Accessibility OFF — tasks chal nahi paenge"
                    settingOn && !serviceAlive ->
                        "⚠️ Service connected nahi — app restart karo"
                    else -> null
                }

                _uiState.update { it.copy(
                    accessibilityEnabled = settingOn,
                    accessibilityServiceAlive = serviceAlive,
                    watchdogWarning = warning
                )}
            }
        }
    }

    fun refreshAccessibility() {
        viewModelScope.launch {
            val enabled = withContext(Dispatchers.IO) { isAccessibilityEnabled(context) }
            val alive = AutoAgentAccessibilityService.isAvailable()
            _uiState.update { it.copy(
                accessibilityEnabled = enabled,
                accessibilityServiceAlive = alive
            )}
            L.d("DashVM", "refreshAccessibility: enabled=$enabled alive=$alive")
        }
    }

    fun setupPin(pin: String) {
        viewModelScope.launch {
            if (pin.length !in PinManager.PIN_MIN..PinManager.PIN_MAX) {
                _uiState.update { it.copy(pinError = "PIN ${PinManager.PIN_MIN}–${PinManager.PIN_MAX} digits ka hona chahiye") }
                return@launch
            }
            val result = withContext(Dispatchers.IO) {
                runCatching { pinManager.setupPin(pin) }.getOrElse { Result.failure(it) }
            }
            result.fold(
                onSuccess = {
                    L.d("DashVM", "PIN setup done")
                    _uiState.update { it.copy(showPinSetup = false, pinError = null) }
                },
                onFailure = { e ->
                    L.e("DashVM", "PIN setup error", e)
                    _uiState.update { it.copy(pinError = e.message) }
                }
            )
        }
    }

    fun verifyPin(pin: String) {
        viewModelScope.launch {
            try {
                val ok = withContext(Dispatchers.IO) {
                    runCatching { pinManager.verifyPin(pin) }.getOrDefault(false)
                }
                if (ok) {
                    val pending = _uiState.value.pendingTaskId
                    val pendingToggle = _uiState.value.pendingToggleId
                    val pendingDelete = _uiState.value.pendingDeleteId
                    _uiState.update { it.copy(
                        showPinVerify = false, pinError = null,
                        pendingTaskId = null, pendingToggleId = null, pendingDeleteId = null
                    )}
                    when {
                        pending != null      -> runTaskNow(pending)
                        pendingToggle != null -> toggleTaskNow(pendingToggle)
                        pendingDelete != null -> deleteTaskNow(pendingDelete)
                    }
                } else {
                    _uiState.update { it.copy(pinError = "Galat PIN — dobara try karo") }
                }
            } catch (e: Exception) {
                L.e("DashVM", "verifyPin error", e)
                _uiState.update { it.copy(pinError = "Verification fail hua") }
            }
        }
    }

    fun dismissPinVerify() = _uiState.update {
        it.copy(showPinVerify = false, pinError = null,
            pendingTaskId = null, pendingToggleId = null, pendingDeleteId = null)
    }

    fun requestRun(taskId: Long) {
        runTaskNow(taskId)
    }

    fun requestToggle(taskId: Long) {
        _uiState.update { it.copy(showPinVerify = true, pendingToggleId = taskId, pinError = null) }
    }

    fun requestDelete(taskId: Long) {
        _uiState.update { it.copy(showPinVerify = true, pendingDeleteId = taskId, pinError = null) }
    }

    private fun runTaskNow(taskId: Long) {
        viewModelScope.launch {
            try {
                val entity = withContext(Dispatchers.IO) { repository.getTask(taskId) }
                    ?: return@launch
                val task = gsonHelper.entityToTask(entity)
                L.d("DashVM", "Running task via ReactAgent: ${task.name}")
                _uiState.update { it.copy(lastRunResult = "▶️ ${task.name} start hua") }
                memoryEngine.saveLastCommand("Run: ${task.name}")

                // Use ReactAgent directly — opens actual app and does the task
                val agent = ReactAgent()
                agent.onDone = { success, msg ->
                    _uiState.update { it.copy(
                        lastRunResult = if (success) "✅ ${task.name} complete hua" else "⚠️ $msg"
                    )}
                }
                withContext(Dispatchers.IO) {
                    agent.execute(task.name)
                }
                withContext(Dispatchers.IO) {
                    repository.updateTaskLastRun(
                        taskId,
                        System.currentTimeMillis(),
                        com.autoagent.personal.domain.model.RunStatus.SUCCESS
                    )
                }
            } catch (e: Exception) {
                L.e("DashVM", "runTaskNow error", e)
                _uiState.update { it.copy(error = "Task start nahi hua: ${e.message}") }
            }
        }
    }

    private fun toggleTaskNow(taskId: Long) {
        viewModelScope.launch {
            try {
                val entity = withContext(Dispatchers.IO) { repository.getTask(taskId) }
                    ?: return@launch
                val newEnabled = !entity.isEnabled
                withContext(Dispatchers.IO) { repository.setTaskEnabled(taskId, newEnabled) }
                if (newEnabled) {
                    val task = gsonHelper.entityToTask(entity)
                    TaskExecutorWorker.scheduleTask(context, task)
                } else {
                    TaskExecutorWorker.cancelTask(context, taskId)
                }
                _uiState.update { it.copy(
                    lastRunResult = if (newEnabled) "✅ Task enabled" else "⏸ Task disabled"
                )}
            } catch (e: Exception) {
                L.e("DashVM", "toggleTask error", e)
                _uiState.update { it.copy(error = "Toggle nahi hua: ${e.message}") }
            }
        }
    }

    private fun deleteTaskNow(taskId: Long) {
        viewModelScope.launch {
            try {
                TaskExecutorWorker.cancelTask(context, taskId)
                withContext(Dispatchers.IO) { repository.deleteTask(taskId) }
                _uiState.update { it.copy(lastRunResult = "🗑 Task delete hua") }
            } catch (e: Exception) {
                L.e("DashVM", "deleteTask error", e)
                _uiState.update { it.copy(error = "Delete nahi hua: ${e.message}") }
            }
        }
    }

    fun emergencyStop() {
        AutoAgentAccessibilityService.getInstance()?.triggerEmergencyStop()
            ?: run { AutoAgentAccessibilityService.emergencyStop.value = true }
        _uiState.update { it.copy(lastRunResult = "🛑 Emergency Stop — sab ruk gaya") }
        L.d("DashVM", "Emergency stop triggered")
    }

    fun pauseAll() {
        AutoAgentAccessibilityService.emergencyStop.value = true
        _uiState.update { it.copy(allPaused = true) }
    }

    fun resumeAll() {
        AutoAgentAccessibilityService.emergencyStop.value = false
        _uiState.update { it.copy(allPaused = false) }
    }

    fun openAddTask() = _uiState.update { it.copy(navigateTo = "add_task") }
    fun openEditTask(taskId: Long) = _uiState.update { it.copy(navigateTo = "edit_task_$taskId") }
    fun onNavigationHandled() = _uiState.update { it.copy(navigateTo = null) }
    fun dismissResult() = _uiState.update { it.copy(lastRunResult = null) }
    fun dismissError() = _uiState.update { it.copy(error = null) }
}
