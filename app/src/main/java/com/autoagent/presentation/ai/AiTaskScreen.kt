package com.autoagent.presentation.ai

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autoagent.ai.NaturalLanguageTaskParser
import com.autoagent.memory.MemoryEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AiTaskUiState(
    val inputText: String = "",
    val parsed: NaturalLanguageTaskParser.ParsedTask? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val lastCommands: List<String> = emptyList()
)

@HiltViewModel
class AiTaskViewModel @Inject constructor(
    private val parser: NaturalLanguageTaskParser,
    private val memoryEngine: MemoryEngine
) : ViewModel() {

    private val _state = MutableStateFlow(AiTaskUiState())
    val state: StateFlow<AiTaskUiState> = _state

    init { loadHistory() }

    private fun loadHistory() {
        viewModelScope.launch {
            val top = memoryEngine.getTopEntries(MemoryEngine.CAT_LAST_CMD, 5)
            _state.value = _state.value.copy(
                lastCommands = top.map { it.value }
            )
        }
    }

    fun parseCommand(text: String) {
        if (text.isBlank()) return
        _state.value = _state.value.copy(isLoading = true, error = null, parsed = null)
        viewModelScope.launch {
            try {
                val result = parser.parse(text)
                memoryEngine.saveLastCommand(text)
                // If contact found, save alias hint for future
                if (result.needsContactResolution && result.recipient != null) {
                    // Pre-populate so memory recalls it next time
                    val existing = memoryEngine.resolveContact(result.recipient)
                    if (existing == null) {
                        // Just save name for now; real phone resolution needs contacts permission
                        memoryEngine.saveContactAlias(result.recipient, "UNRESOLVED:${result.recipient}")
                    }
                }
                _state.value = _state.value.copy(parsed = result, isLoading = false)
                loadHistory()
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    error = "Parse nahi ho saka: ${e.message}", isLoading = false
                )
            }
        }
    }

    fun updateInput(text: String) {
        _state.value = _state.value.copy(inputText = text)
    }
    fun clearResult() { _state.value = _state.value.copy(parsed = null, error = null) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiTaskScreen(
    onBack: () -> Unit,
    onTaskParsed: (NaturalLanguageTaskParser.ParsedTask) -> Unit,
    viewModel: AiTaskViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("✨", fontSize = MaterialTheme.typography.headlineSmall.fontSize)
                        Spacer(Modifier.width(6.dp))
                        Text("AI Task Builder", fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null) }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Instruction card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("Apna command likho 👇", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("Hindi, English ya Hinglish mein — sab samjha jayega",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    val examples = listOf(
                        "\"WhatsApp mein Sipun ko 'Good morning' bhejo\"",
                        "\"Kal subah 8 baje reminder set karo\"",
                        "\"YouTube kholo aur 'lofi music' search karo\"",
                        "\"10 minute baad SMS bhejo\""
                    )
                    examples.forEach {
                        Text("• $it",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // Recent commands
            if (state.lastCommands.isNotEmpty()) {
                Text("🕐 Pichle Commands", fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleSmall)
                state.lastCommands.take(3).forEach { cmd ->
                    AssistChip(
                        onClick = { viewModel.updateInput(cmd) },
                        label = {
                            Text(cmd.take(40), style = MaterialTheme.typography.labelSmall)
                        },
                        leadingIcon = { Icon(Icons.Filled.History, null, Modifier.size(14.dp)) }
                    )
                }
            }

            // Command input
            OutlinedTextField(
                value = state.inputText,
                onValueChange = { viewModel.updateInput(it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Command likho...") },
                placeholder = {
                    Text("WhatsApp mein Sipun ko 'Hello' bhejo kal subah 9 baje")
                },
                leadingIcon = { Icon(Icons.Filled.Mic, null) },
                trailingIcon = {
                    if (state.inputText.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateInput("") }) {
                            Icon(Icons.Filled.Clear, null)
                        }
                    }
                },
                minLines = 3,
                shape = RoundedCornerShape(12.dp)
            )

            Button(
                onClick = { viewModel.parseCommand(state.inputText) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = state.inputText.isNotBlank() && !state.isLoading,
                shape = RoundedCornerShape(14.dp)
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Samajh raha hai...")
                } else {
                    Icon(Icons.Filled.AutoAwesome, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Command Samjho", fontWeight = FontWeight.Bold)
                }
            }

            // Error
            state.error?.let {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(it, Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }

            // Parse result
            state.parsed?.let { parsed ->
                Divider()

                // Confidence bar
                val confColor = when {
                    parsed.confidence >= 0.8f -> Color(0xFF4CAF50)
                    parsed.confidence >= 0.6f -> Color(0xFFFF9800)
                    else -> Color(0xFFF44336)
                }
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = confColor.copy(0.1f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Confidence: ${(parsed.confidence * 100).toInt()}%",
                                fontWeight = FontWeight.Bold, color = confColor)
                            Spacer(Modifier.weight(1f))
                            Icon(
                                if (parsed.confidence >= 0.7f) Icons.Filled.CheckCircle
                                else Icons.Filled.Warning,
                                null, tint = confColor, modifier = Modifier.size(18.dp)
                            )
                        }
                        LinearProgressIndicator(
                            progress = parsed.confidence,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            color = confColor
                        )
                    }
                }

                // Explanation card
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("📋 Samjha Gaya:", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(parsed.explanation, style = MaterialTheme.typography.bodySmall)
                    }
                }

                // Contact resolution warning
                if (parsed.needsContactResolution) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFF9800).copy(0.1f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Contacts, null, tint = Color(0xFFFF9800),
                                modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Contact '${parsed.recipient}' — task builder mein manually select karo",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                // Online AI suggestion
                if (parsed.onlineFallbackSuggested) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Cloud, null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Yeh command complex hai — online AI better samjhega. Settings mein enable karo.",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                // Steps preview
                Text("🔢 Steps (${parsed.steps.size})",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall)
                Card(shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        parsed.steps.forEach { step ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${step.type.emoji}",
                                    style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(step.description.ifEmpty { step.type.displayName },
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium)
                                    if (step.delayMs > 0)
                                        Text("Wait: ${step.delayMs}ms",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (step != parsed.steps.last()) Divider()
                        }
                    }
                }

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.clearResult() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Reset")
                    }
                    Button(
                        onClick = { onTaskParsed(parsed) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Add, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Task Banao", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(60.dp))
        }
    }
}
