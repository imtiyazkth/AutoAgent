package com.autoagent.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autoagent.data.db.ExecutionLogEntity
import com.autoagent.data.db.TaskEntity
import com.autoagent.data.repository.AgentRepository
import com.autoagent.domain.model.*
import com.autoagent.service.accessibility.AutoAgentAccessibilityService
import com.autoagent.service.scheduler.TaskExecutorWorker
import com.autoagent.util.GsonHelper
import com.autoagent.util.PinManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: AgentRepository,
    private val pinManager: PinManager,
    private val gsonHelper: GsonHelper,
    @ApplicationContext private val context: Context
) : ViewModel() {

    // =========================================
    // UI STATE
    // =========================================
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    // Live data
    val tasks: StateFlow<List<TaskEntity>> = repository.getAllTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentLogs: StateFlow<List<ExecutionLogEntity>> = repository.getRecentLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Accessibility + automation state
    val isRunning: StateFlow<Boolean> = AutoAgentAccessibilityService.isRunning
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val currentStep: StateFlow<String?> = AutoAgentAccessibilityService.currentStep
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        checkPinSetup()
        checkAccessibilityPermission()
    }

    // =========================================
    // PIN OPERATIONS
    // =========================================
    fun setupPin(pin: String) {
        viewModelScope.launch {
            val result = pinManager.setupPin(pin)
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(
                        isPinSetup = true,
                        pinError = null,
                        showPinSetup = false
                    )}
                },
                onFailure = { e ->
                    _uiState.update { it.copy(pinError = e.message) }
                }
            )
        }
    }

    fun verifyPin(pin: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val correct = pinManager.verifyPin(pin)
            if (correct) {
                _uiState.update { it.copy(
                    isPinVerified = true,
                    pinError = null,
                    showPinVerify = false
                )}
                onSuccess()
            } else {
                _uiState.update { it.copy(pinError = "Galat PIN — dobara try karo") }
            }
        }
    }

    fun requestPinVerification(onVerified: () -> Unit) {
        _uiState.update { it.copy(
            showPinVerify = true,
            pendingAction = onVerified
        )}
    }

    private fun checkPinSetup() {
        viewModelScope.launch {
            val setup = pinManager.isPinSetup()
            _uiState.update { it.copy(
                isPinSetup = setup,
                showPinSetup = !setup
            )}
        }
    }

    // =========================================
    // TASK OPERATIONS (all require PIN)
    // =========================================
    fun deleteTask(taskId: Long) {
        requestPinVerification {
            viewModelScope.launch {
                TaskExecutorWorker.cancelTask(context, taskId)
                repository.deleteTask(taskId)
            }
        }
    }

    fun toggleTask(taskId: Long, enabled: Boolean) {
        requestPinVerification {
            viewModelScope.launch {
                repository.setTaskEnabled(taskId, enabled)
                val task = repository.getTask(taskId) ?: return@launch
                if (enabled) {
                    val agentTask = gsonHelper.entityToTask(task)
                    TaskExecutorWorker.scheduleTask(context, agentTask)
                } else {
                    TaskExecutorWorker.cancelTask(context, taskId)
                }
            }
        }
    }

    fun runTaskNow(taskId: Long) {
        requestPinVerification {
            viewModelScope.launch {
                val taskEntity = repository.getTask(taskId) ?: return@launch
                val task = gsonHelper.entityToTask(taskEntity)

                // Check accessibility
                if (AutoAgentAccessibilityService.getInstance() == null) {
                    _uiState.update { it.copy(
                        error = "Pehle Accessibility Service ON karo:\nSettings → Accessibility → AutoAgent"
                    )}
                    return@launch
                }

                // Run directly
                val service = AutoAgentAccessibilityService.getInstance()!!
                val stepLogs = mutableListOf<StepLog>()
                val status = service.executeSteps(task.steps) { log ->
                    stepLogs.add(log)
                }

                _uiState.update { it.copy(
                    lastRunResult = if (status == RunStatus.SUCCESS)
                        "✅ '${task.name}' successfully run hua!" else
                        "❌ '${task.name}' fail ho gaya"
                )}
            }
        }
    }

    // =========================================
    // DRY RUN PREVIEW
    // =========================================
    fun previewTask(task: AgentTask) {
        val descriptions = task.steps.map { step ->
            "${step.type.emoji} ${step.description.ifEmpty { step.type.displayName }}" +
            when {
                step.targetApp != null -> " (${step.targetApp})"
                step.targetUrl != null -> " → ${step.targetUrl.take(40)}"
                step.buttonText != null -> " → '${step.buttonText}'"
                step.inputText != null -> " → '${step.inputText.take(30)}'"
                else -> ""
            }
        }

        val preview = DryRunPreview(
            taskName = task.name,
            stepDescriptions = descriptions,
            estimatedDurationSeconds = task.steps.sumOf { (it.delayMs / 1000 + 1).toInt() },
            requiresInternet = task.steps.any { it.type == ActionType.OPEN_URL },
            appsInvolved = task.steps.mapNotNull { it.targetApp }.distinct(),
            warnings = buildList {
                if (task.steps.any { it.type == ActionType.ENTER_TEXT }) {
                    add("⚠️ Ye task text type karega — sahi field pe focus rakho")
                }
                if (task.networkPolicy == NetworkPolicy.MOBILE_DATA_ALLOWED) {
                    add("📶 Mobile data use ho sakta hai — charges lag sakte hain")
                }
            }
        )
        _uiState.update { it.copy(dryRunPreview = preview) }
    }

    // =========================================
    // EMERGENCY STOP
    // =========================================
    fun emergencyStop() {
        AutoAgentAccessibilityService.getInstance()?.triggerEmergencyStop()
            ?: run { AutoAgentAccessibilityService.emergencyStop.value = true }
        _uiState.update { it.copy(lastRunResult = "🛑 EMERGENCY STOP activated!") }
    }

    fun pauseAll() {
        viewModelScope.launch {
            val allTasks = tasks.value
            allTasks.forEach { task ->
                TaskExecutorWorker.cancelTask(context, task.id)
            }
            _uiState.update { it.copy(allPaused = true) }
        }
    }

    fun resumeAll() {
        viewModelScope.launch {
            val allTasks = tasks.value
            allTasks.filter { it.isEnabled }.forEach { taskEntity ->
                val task = gsonHelper.entityToTask(taskEntity)
                TaskExecutorWorker.scheduleTask(context, task)
            }
            _uiState.update { it.copy(allPaused = false) }
        }
    }

    // =========================================
    // HELPERS
    // =========================================
    private fun checkAccessibilityPermission() {
        val service = AutoAgentAccessibilityService.getInstance()
        _uiState.update { it.copy(accessibilityEnabled = service != null) }
    }

    fun dismissPreview() { _uiState.update { it.copy(dryRunPreview = null) } }
    fun dismissError() { _uiState.update { it.copy(error = null) } }
    fun dismissResult() { _uiState.update { it.copy(lastRunResult = null) } }
    fun dismissPinError() { _uiState.update { it.copy(pinError = null) } }
}

// =============================================
// UI STATE
// =============================================
data class DashboardUiState(
    // PIN
    val isPinSetup: Boolean = false,
    val isPinVerified: Boolean = false,
    val showPinSetup: Boolean = false,
    val showPinVerify: Boolean = false,
    val pinError: String? = null,
    val pendingAction: (() -> Unit)? = null,

    // Tasks
    val allPaused: Boolean = false,

    // Accessibility
    val accessibilityEnabled: Boolean = false,

    // Dry run
    val dryRunPreview: DryRunPreview? = null,

    // Feedback
    val lastRunResult: String? = null,
    val error: String? = null
)
