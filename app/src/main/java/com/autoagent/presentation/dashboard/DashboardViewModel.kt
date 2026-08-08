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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class DashboardUiState(
    val isPinSetup: Boolean = false,
    val showPinSetup: Boolean = false,
    val showPinVerify: Boolean = false,
    val pinError: String? = null,
    val pendingDeleteId: Long? = null,
    val pendingRunId: Long? = null,
    val pendingToggleId: Long? = null,
    val allPaused: Boolean = false,
    val accessibilityEnabled: Boolean = false,
    val lastRunResult: String? = null,
    val error: String? = null,
    val navigateTo: String? = null
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: AgentRepository,
    private val pinManager: PinManager,
    private val gsonHelper: GsonHelper,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _state.asStateFlow()

    val tasks: StateFlow<List<TaskEntity>> = repository.getAllTasks()
        .catch { e -> L.e("DashboardVM", "tasks flow error", e) }
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
        viewModelScope.launch {
            try {
                val setup = withContext(Dispatchers.IO) { pinManager.isPinSetup() }
                L.d("DashboardVM", "pinSetup=$setup")
                _state.update { it.copy(isPinSetup = setup, showPinSetup = !setup) }
            } catch (e: Exception) {
                L.e("DashboardVM", "checkPinSetup error", e)
            }
        }
    }

    fun setupPin(pin: String) {
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { pinManager.setupPin(pin) }
                result.fold(
                    onSuccess = {
                        L.d("DashboardVM", "PIN setup OK")
                        _state.update {
                            it.copy(isPinSetup = true, showPinSetup = false, pinError = null)
                        }
                    },
                    onFailure = { e -> _state.update { it.copy(pinError = e.message) } }
                )
            } catch (e: Exception) {
                L.e("DashboardVM", "setupPin error", e)
                _state.update { it.copy(pinError = e.message) }
            }
        }
    }

    fun openAddTask() {
        L.d("DashboardVM", "openAddTask")
        _state.update { it.copy(navigateTo = "add_task") }
    }

    fun openEditTask(id: Long) {
        L.d("DashboardVM", "openEditTask $id")
        _state.update { it.copy(navigateTo = "edit_task_$id") }
    }

    fun onNavigationHandled() {
        _state.update { it.copy(navigateTo = null) }
    }

    fun requestDelete(id: Long) {
        if (_state.value.isPinSetup)
            _state.update { it.copy(showPinVerify = true, pendingDeleteId = id, pinError = null) }
        else
            _state.update { it.copy(showPinSetup = true) }
    }

    fun requestToggle(id: Long) {
        if (_state.value.isPinSetup)
            _state.update { it.copy(showPinVerify = true, pendingToggleId = id, pinError = null) }
        else
            _state.update { it.copy(showPinSetup = true) }
    }

    fun requestRun(id: Long) {
        if (_state.value.isPinSetup)
            _state.update { it.copy(showPinVerify = true, pendingRunId = id, pinError = null) }
        else
            _state.update { it.copy(showPinSetup = true) }
    }

    fun verifyPin(pin: String) {
        viewModelScope.launch {
            try {
                // FIX: run DB check on IO thread, not Main
                val correct = withContext(Dispatchers.IO) {
                    runCatching { pinManager.verifyPin(pin) }.getOrDefault(false)
                }
                L.d("DashboardVM", "verifyPin correct=$correct")

                if (correct) {
                    // Snapshot pending IDs BEFORE clearing state
                    val pendingDelete = _state.value.pendingDeleteId
                    val pendingToggle = _state.value.pendingToggleId
                    val pendingRun = _state.value.pendingRunId

                    // Clear PIN dialog first — UI returns to normal immediately
                    _state.update {
                        it.copy(
                            showPinVerify = false,
                            pinError = null,
                            pendingDeleteId = null,
                            pendingRunId = null,
                            pendingToggleId = null
                        )
                    }

                    // Execute actions after UI is unblocked
                    pendingDelete?.let { deleteTask(it) }
                    pendingToggle?.let { toggleTask(it) }
                    pendingRun?.let { runTask(it) }

                } else {
                    L.d("DashboardVM", "Wrong PIN")
                    _state.update { it.copy(pinError = "Galat PIN — dobara try karo") }
                }
            } catch (e: Exception) {
                L.e("DashboardVM", "verifyPin exception", e)
                _state.update { it.copy(pinError = "Error: ${e.message}") }
            }
        }
    }

    fun dismissPinVerify() {
        _state.update {
            it.copy(
                showPinVerify = false, pinError = null,
                pendingDeleteId = null, pendingRunId = null, pendingToggleId = null
            )
        }
    }

    private suspend fun deleteTask(id: Long) {
        try {
            withContext(Dispatchers.IO) {
                TaskExecutorWorker.cancelTask(context, id)
                repository.deleteTask(id)
            }
            _state.update { it.copy(lastRunResult = "✅ Task delete ho gaya") }
            L.d("DashboardVM", "Task $id deleted")
        } catch (e: Exception) {
            L.e("DashboardVM", "deleteTask error", e)
            _state.update { it.copy(error = "Delete nahi hua: ${e.message}") }
        }
    }

    private suspend fun toggleTask(id: Long) {
        try {
            withContext(Dispatchers.IO) {
                val t = repository.getTask(id) ?: return@withContext
                repository.setTaskEnabled(id, !t.isEnabled)
                if (!t.isEnabled) TaskExecutorWorker.scheduleTask(context, gsonHelper.entityToTask(t))
                else TaskExecutorWorker.cancelTask(context, id)
            }
            L.d("DashboardVM", "Task $id toggled")
        } catch (e: Exception) {
            L.e("DashboardVM", "toggleTask error", e)
            _state.update { it.copy(error = "Toggle nahi hua: ${e.message}") }
        }
    }

    private suspend fun runTask(id: Long) {
        // Guard: don't run if already running
        if (AutoAgentAccessibilityService.isRunning.value) {
            _state.update { it.copy(error = "Ek task pehle se chal raha hai — ruko ya Stop karo") }
            return
        }

        try {
            // Get task on IO thread
            val entity = withContext(Dispatchers.IO) { repository.getTask(id) }
            if (entity == null) {
                _state.update { it.copy(error = "Task nahi mila (id=$id)") }
                return
            }

            // Check service ONCE — snapshot the reference
            val svc = AutoAgentAccessibilityService.getInstance()
            if (svc == null) {
                L.d("DashboardVM", "runTask: service null")
                _state.update {
                    it.copy(
                        error = "Accessibility Service abhi connect nahi hai.\n\n" +
                                "✅ Fix:\n" +
                                "1. Settings → Accessibility → AutoAgent → OFF karo\n" +
                                "2. Phir ON karo\n" +
                                "3. App dobara kholo\n" +
                                "4. Dobara Run karo"
                    )
                }
                return
            }

            L.d("DashboardVM", "runTask $id starting")

            // FIX: executeSteps runs on IO dispatcher — avoids Main thread blocking
            // which caused "Already resumed" IllegalStateException crash
            val agentTask = gsonHelper.entityToTask(entity)
            val logs = mutableListOf<StepLog>()

            val status = withContext(Dispatchers.IO) {
                svc.executeSteps(agentTask.steps) { log ->
                    logs.add(log)
                    L.d("DashboardVM", "Step: ${log.actionType} success=${log.success}")
                }
            }

            val failed = logs.firstOrNull { !it.success }
            _state.update {
                it.copy(
                    lastRunResult = when (status) {
                        RunStatus.SUCCESS -> "✅ Task run hua! (${logs.size} steps)"
                        RunStatus.CANCELLED -> "🛑 Task rok diya gaya"
                        else -> "❌ Failed: ${failed?.description ?: "unknown"} — ${failed?.errorMessage ?: "check logs"}"
                    }
                )
            }

            // Save run log to DB
            withContext(Dispatchers.IO) {
                repository.updateTaskLastRun(id, System.currentTimeMillis(), status)
            }

            L.d("DashboardVM", "runTask complete: $status")

        } catch (e: Exception) {
            L.e("DashboardVM", "runTask exception", e)
            _state.update { it.copy(error = "Task run nahi hua: ${e.message}") }
        }
    }

    fun emergencyStop() {
        AutoAgentAccessibilityService.getInstance()?.triggerEmergencyStop()
            ?: run { AutoAgentAccessibilityService.emergencyStop.value = true }
        _state.update { it.copy(lastRunResult = "🛑 Emergency stop!") }
        L.d("DashboardVM", "emergencyStop triggered")
    }

    fun pauseAll() {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    tasks.value.forEach {
                        runCatching { TaskExecutorWorker.cancelTask(context, it.id) }
                    }
                }
                _state.update { it.copy(allPaused = true) }
            } catch (e: Exception) {
                L.e("DashboardVM", "pauseAll error", e)
            }
        }
    }

    fun resumeAll() {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    tasks.value.filter { it.isEnabled }.forEach {
                        runCatching {
                            TaskExecutorWorker.scheduleTask(context, gsonHelper.entityToTask(it))
                        }
                    }
                }
                _state.update { it.copy(allPaused = false) }
            } catch (e: Exception) {
                L.e("DashboardVM", "resumeAll error", e)
            }
        }
    }

    fun refreshAccessibility() {
        val en = runCatching { isAccessibilityEnabled(context) }.getOrDefault(false)
        L.d("DashboardVM", "refreshAccessibility: enabled=$en")
        _state.update { it.copy(accessibilityEnabled = en) }
    }

    fun dismissError() { _state.update { it.copy(error = null) } }
    fun dismissResult() { _state.update { it.copy(lastRunResult = null) } }
}
