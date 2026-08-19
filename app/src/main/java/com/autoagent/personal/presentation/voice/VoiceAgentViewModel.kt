package com.autoagent.personal.presentation.voice

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autoagent.personal.agent.AgentController
import com.autoagent.personal.memory.MemoryEngine
import com.autoagent.personal.service.AutoAgentAccessibilityService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VoiceAgentUiState(
    val isListening: Boolean = false,
    val recognizedText: String = "",
    val agentResponse: String = "",
    val isExecuting: Boolean = false,
    val error: String? = null,
    val serviceConnected: Boolean = false
)

@HiltViewModel
class VoiceAgentViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val memoryEngine: MemoryEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(VoiceAgentUiState())
    val uiState: StateFlow<VoiceAgentUiState> = _uiState.asStateFlow()

    private val agentController = AgentController(context)

    init {
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

    fun processTextInput(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isExecuting = true, recognizedText = text) }
            if (!AutoAgentAccessibilityService.isConnected()) {
                _uiState.update { it.copy(isExecuting = false, error = "Service on nahi hai") }
                return@launch
            }
            val result = agentController.execute(text)
            val msg = when (result) {
                is AgentController.ExecutionResult.Success -> "Done: ${result.message}"
                is AgentController.ExecutionResult.Failure -> "Error: ${result.reason}"
                is AgentController.ExecutionResult.ServiceNotConnected -> "Service off hai"
                is AgentController.ExecutionResult.Cancelled -> "Roka gaya"
                is AgentController.ExecutionResult.Timeout -> "Timeout"
            }
            memoryEngine.saveLastCommand("$text -> $msg")
            _uiState.update { it.copy(isExecuting = false, agentResponse = msg) }
        }
    }

    fun emergencyStop() {
        agentController.triggerEmergencyStop()
        _uiState.update { it.copy(isExecuting = false) }
    }

    fun clearError() { _uiState.update { it.copy(error = null) } }

    override fun onCleared() {
        super.onCleared()
        agentController.triggerEmergencyStop()
    }
}
