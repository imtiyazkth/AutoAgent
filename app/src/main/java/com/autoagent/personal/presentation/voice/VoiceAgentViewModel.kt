package com.autoagent.personal.presentation.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autoagent.personal.agent.AgentController
import com.autoagent.personal.agent.GoalPlanner
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
import java.util.Locale
import javax.inject.Inject

// ─── UI State ─────────────────────────────────────────────────────────────────

data class VoiceAgentUiState(
    val isListening: Boolean = false,
    val recognizedText: String = "",
    val agentResponse: String = "",
    val isExecuting: Boolean = false,
    val messages: List<ChatMessage> = emptyList(),
    val error: String? = null,
    val serviceConnected: Boolean = false
)

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

// ─── ViewModel ────────────────────────────────────────────────────────────────

/**
 * VoiceAgentViewModel
 *
 * HILT FIX: AgentController is NOT a Hilt-injectable type because it depends on
 * AccessibilityService.instance (a system-managed object, not a Hilt singleton).
 * Attempting to inject AgentController via Hilt caused "NonExistentClass" errors
 * in builds 81–87 and "VoiceAgentViewModel_Factory" symbol errors in build 87.
 *
 * Solution: Create AgentController manually with `context` which IS available
 * via @ApplicationContext injection.
 */
@HiltViewModel
class VoiceAgentViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val memoryEngine: MemoryEngine
) : ViewModel() {

    private val TAG = "VoiceAgentVM"

    // Manual construction — NOT from Hilt
    private val agentController = AgentController(context)

    private val _uiState = MutableStateFlow(VoiceAgentUiState())
    val uiState: StateFlow<VoiceAgentUiState> = _uiState.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null

    init {
        observeServiceState()
    }

    // ─── Service state ────────────────────────────────────────────────────────

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

    // ─── Speech recognition ───────────────────────────────────────────────────

    fun startListening() {
        if (_uiState.value.isListening || _uiState.value.isExecuting) return

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _uiState.update { it.copy(error = "Speech recognition available nahi hai is device par") }
            return
        }

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).also { recognizer ->
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _uiState.update { it.copy(isListening = true, error = null) }
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull()?.trim() ?: ""
                    if (text.isNotBlank()) {
                        _uiState.update {
                            it.copy(
                                isListening = false,
                                recognizedText = text,
                                messages = it.messages + ChatMessage(text, isUser = true)
                            )
                        }
                        processGoal(text)
                    } else {
                        _uiState.update { it.copy(isListening = false) }
                    }
                }

                override fun onError(error: Int) {
                    val msg = speechErrorMessage(error)
                    Log.w(TAG, "Speech error: $msg (code=$error)")
                    _uiState.update { it.copy(isListening = false, error = msg) }
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN") // Hindi + English
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
            recognizer.startListening(intent)
        }
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        _uiState.update { it.copy(isListening = false) }
    }

    // ─── Text input (fallback for when speech isn't used) ─────────────────────

    fun processTextInput(text: String) {
        if (text.isBlank()) return
        _uiState.update {
            it.copy(
                recognizedText = text,
                messages = it.messages + ChatMessage(text, isUser = true)
            )
        }
        processGoal(text)
    }

    // ─── Goal execution ───────────────────────────────────────────────────────

    private fun processGoal(goal: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExecuting = true) }

            // Check service before doing anything
            if (!AutoAgentAccessibilityService.isConnected()) {
                val response = "❌ Accessibility Service on nahi hai. Settings > Accessibility > AutoAgent mein jaake enable karo."
                _uiState.update {
                    it.copy(
                        isExecuting = false,
                        messages = it.messages + ChatMessage(response, isUser = false)
                    )
                }
                return@launch
            }

            // Quick plan preview so user sees feedback immediately
            val plan = kotlinx.coroutines.withContext(Dispatchers.Default) {
                GoalPlanner.plan(goal)
            }

            if (plan.steps.isEmpty()) {
                val response = "🤔 Samajh nahi aaya: '$goal'. Aur clearly boliye?"
                _uiState.update {
                    it.copy(
                        isExecuting = false,
                        messages = it.messages + ChatMessage(response, isUser = false)
                    )
                }
                return@launch
            }

            val planPreview = "📋 ${plan.steps.size} steps ka plan banaya. Shuruaat hoti hai..."
            _uiState.update {
                it.copy(messages = it.messages + ChatMessage(planPreview, isUser = false))
            }

            // Execute
            val result = agentController.execute(goal, timeoutMs = 90_000L)

            val response = when (result) {
                is AgentController.ExecutionResult.Success -> "✅ Ho gaya! ${result.message}"
                is AgentController.ExecutionResult.Failure -> "⚠️ ${result.reason}"
                is AgentController.ExecutionResult.ServiceNotConnected ->
                    "❌ Accessibility Service disconnect ho gayi. Wapas enable karo."
                is AgentController.ExecutionResult.Cancelled -> "🛑 Rok diya gaya"
                is AgentController.ExecutionResult.Timeout ->
                    "⏱️ 90 seconds mein complete nahi hua. Dobara try karo?"
            }

            memoryEngine.saveLastCommand("Voice: $goal → $response")

            _uiState.update {
                it.copy(
                    isExecuting = false,
                    agentResponse = response,
                    messages = it.messages + ChatMessage(response, isUser = false)
                )
            }
        }
    }

    // ─── Emergency stop ───────────────────────────────────────────────────────

    fun emergencyStop() {
        agentController.triggerEmergencyStop()
        stopListening()
        _uiState.update {
            it.copy(
                isExecuting = false,
                isListening = false,
                messages = it.messages + ChatMessage("🛑 Emergency stop!", isUser = false)
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    // ─── Cleanup ──────────────────────────────────────────────────────────────

    override fun onCleared() {
        super.onCleared()
        speechRecognizer?.destroy()
        speechRecognizer = null
        agentController.triggerEmergencyStop()
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private fun speechErrorMessage(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO             -> "Audio recording error"
        SpeechRecognizer.ERROR_CLIENT            -> "Client error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission chahiye"
        SpeechRecognizer.ERROR_NETWORK           -> "Network error"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT   -> "Network timeout"
        SpeechRecognizer.ERROR_NO_MATCH          -> "Kuch samajh nahi aaya — dobara boliye"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY   -> "Recognizer busy hai"
        SpeechRecognizer.ERROR_SERVER            -> "Server error"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT    -> "Kuch bola nahi — mic ke paas boliye"
        else                                     -> "Speech recognition error ($error)"
    }
}
