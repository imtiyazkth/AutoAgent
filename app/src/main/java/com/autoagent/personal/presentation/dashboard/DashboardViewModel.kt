package com.autoagent.personal.presentation.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autoagent.personal.agent.AgentController
import com.autoagent.personal.data.repository.TaskRepository
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

data class DashboardUiState(
    val isLoading: Boolean = false,
    val tasks: List<TaskDisplayItem> = emptyList(),
    val lastRunResult: String? = null,
    val error: String? = null,
    val serviceConnected: Boolean = false
)

data class TaskDisplayItem(
    val id: Long,
    val name: String,
    val appName: String,
    val lastRunStatus: String?
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: TaskRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val agentController = AgentController(context)

    init {
        loadTasks()
        observeServiceState()
    }

    private fun observeServiceState() {
        viewModelScope.launch {
            AutoAgentAccessibilityService.state.collect { state ->
                _uiState.update {
                    it.copy(
                        serviceConnected = state == AutoAgentAccessibilityService.ServiceState.CONNECTED
                    )
                }
            }
        }
    }

    fun loadTasks() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val entities = withContext(Dispatchers.IO) {
                    repository.getAllTasks()
                }
                val items = entities.map { entity ->
                    TaskDisplayItem(
                        id = entity.id,
                        name = entity.name,
                        appName = entity.appPackage,
                        lastRunStatus = entity.lastRunStatus
                    )
                }
                _uiState.update { it.copy(isLoading = false, tasks = items, error = null) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun runTask(taskId: Long) {
        viewModelScope.launch {
            if (!AutoAgentAccessibilityService.isConnected()) {
                _uiState.update { it.copy(error = "Accessibility Service on nahi hai") }
                return@launch
            }
            val entity = withContext(Dispatchers.IO) {
                runCatching { repository.getTask(taskId) }.getOrNull()
            } ?: run {
                _uiState.update { it.copy(error = "Task nahi mila") }
                return@launch
            }
            _uiState.update { it.copy(lastRunResult = "Running: ${entity.name}") }
            val result = agentController.execute(entity.name)
            val msg = when (result) {
                is AgentController.ExecutionResult.Success -> "Done: ${result.message}"
                is AgentController.ExecutionResult.Failure -> "Error: ${result.reason}"
                is AgentController.ExecutionResult.ServiceNotConnected -> "Service disconnect"
                is AgentController.ExecutionResult.Cancelled -> "Stopped"
                is AgentController.ExecutionResult.Timeout -> "Timeout"
            }
            _uiState.update { it.copy(lastRunResult = msg) }
            loadTasks()
        }
    }

    fun emergencyStop() {
        agentController.triggerEmergencyStop()
        _uiState.update { it.copy(lastRunResult = "Emergency stop!") }
    }

    fun clearError() { _uiState.update { it.copy(error = null) } }

    override fun onCleared() {
        super.onCleared()
        agentController.triggerEmergencyStop()
    }
}
