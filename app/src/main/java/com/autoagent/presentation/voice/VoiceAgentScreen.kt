package com.autoagent.personal.presentation.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autoagent.personal.ai.NaturalLanguageTaskParser
import com.autoagent.personal.data.db.TaskEntity
import com.autoagent.personal.data.repository.AgentRepository
import com.autoagent.personal.domain.model.*
import com.autoagent.personal.memory.MemoryEngine
import com.autoagent.personal.service.accessibility.AutoAgentAccessibilityService
import com.autoagent.personal.service.scheduler.TaskExecutorWorker
import com.autoagent.personal.util.L
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.*
import javax.inject.Inject

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isAgent: Boolean,
    val isAction: Boolean = false
)

enum class AgentState { IDLE, LISTENING, THINKING, SPEAKING, EXECUTING, WAITING_FOLLOWUP }

@HiltViewModel
class VoiceAgentViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val parser: NaturalLanguageTaskParser,
    private val repository: AgentRepository,
    private val memoryEngine: MemoryEngine
) : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _agentState = MutableStateFlow(AgentState.IDLE)
    val agentState: StateFlow<AgentState> = _agentState

    private val _partialText = MutableStateFlow("")
    private val _autoListen = MutableStateFlow(false)
    val autoListen: StateFlow<Boolean> = _autoListen
    val partialText: StateFlow<String> = _partialText

    private var pendingParsed: NaturalLanguageTaskParser.ParsedTask? = null
    private var lastActiveApp: String? = null
    private var lastActiveAppName: String? = null
    private var pendingTime: String? = null
    private var context2: String? = null
    private var tts: TextToSpeech? = null

    init {
        initTTS()
        viewModelScope.launch {
            delay(600)
            agentSpeak("Ji haan! Main AutoAgent hoon. Batayein, kya karna hai?")
            _agentState.value = AgentState.WAITING_FOLLOWUP
        }
    }

    private fun initTTS() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("hi", "IN")
            }
        }
    }
fun agentSpeak(text: String) {
        viewModelScope.launch {
            _agentState.value = AgentState.SPEAKING
            addMsg(text, true)
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "id_${System.currentTimeMillis()}")
            delay(text.length * 55L + 600)
            _agentState.value = AgentState.IDLE
        }
    }

    fun setPartial(text: String) { _partialText.value = text }

    fun onInput(text: String) {
        if (text.isBlank()) return
        _partialText.value = ""
        addMsg(text, false)
        handle(text)
    }

    private fun handle(input: String) {
        viewModelScope.launch {
            _agentState.value = AgentState.THINKING
            val lower = input.lowercase().trim()

            // Emergency stop
            if (lower.contains("stop") || lower.contains("band karo") || lower.contains("ruk")) {
                AutoAgentAccessibilityService.getInstance()?.triggerEmergencyStop()
                agentSpeak("Theek hai, ruk gaya!")
                pendingParsed = null; pendingTime = null; context2 = null
                return@launch
            }

            // Followup: time answer
            if (pendingParsed != null && context2 == "time") {
                handleTimeReply(input); return@launch
            }

            // Followup: confirm
            if (pendingParsed != null && context2 == "confirm") {
                handleConfirm(input); return@launch
            }

            // Parse new command
            val parsed = withContext(Dispatchers.Default) { parser.parse(input) }
            pendingParsed = parsed

            if (parsed.targetApp == null && parsed.url == null) {
                // Last active app ka context use karo
                val ctxApp = lastActiveApp
                val ctxName = lastActiveAppName
                if (ctxApp != null) {
                    agentSpeak("$ctxName mein ${parsed.message ?: parsed.steps.firstOrNull()?.description ?: "kaam"} — abhi karun?")
                    pendingParsed = parsed.copy(targetApp = ctxApp, targetAppName = ctxName)
                    return@launch
                }
                agentSpeak("Samajh nahi aaya. Kaunsi app mein kya karna hai, batayein?")
                pendingParsed = null; return@launch
            }

            if (parsed.scheduledHour != null && parsed.scheduledHour >= 0) {
                val t = "%02d:%02d".format(parsed.scheduledHour, parsed.scheduledMinute ?: 0)
                val d = if (parsed.scheduledDateOffset == 1) "kal" else "aaj"
                pendingTime = t
                context2 = "confirm"
                val name = parsed.targetAppName ?: "App"
                val recip = if (parsed.recipient != null) "${parsed.recipient} ko " else ""
                val msg = if (parsed.message != null) "'${parsed.message?.take(25)}' " else ""
                agentSpeak("$name mein ${recip}${msg}$d $t baje — schedule kar dun?")
                _agentState.value = AgentState.WAITING_FOLLOWUP
            } else {
                val name = parsed.targetAppName ?: "App"
                val recip = if (parsed.recipient != null) "${parsed.recipient} ko " else ""
                val what = if (parsed.message != null) "'${parsed.message?.take(20)}'" else "kaam"
                context2 = "time"
                agentSpeak("$name mein $recip$what — kab karna hai? Abhi ya time batao?")
                _agentState.value = AgentState.WAITING_FOLLOWUP
            }
        }
    }

    private suspend fun handleTimeReply(input: String) {
        val lower = input.lowercase()
        val parsed = pendingParsed ?: return

        if (lower.contains("abhi") || lower.contains("now") || lower.contains("turant") || lower.contains("immediately")) {
            context2 = null
            agentSpeak("Abhi shuru karta hoon!")
            delay(400)
            executeNow(parsed)
            return
        }

        val tr = Regex("""(\d{1,2})(?::(\d{2}))?\s*(am|pm|baje)?""")
        val m = tr.find(lower)
        if (m != null) {
            var h = m.groupValues[1].toIntOrNull() ?: 0
            val mn = m.groupValues[2].toIntOrNull() ?: 0
            val ap = m.groupValues[3]
            if (ap == "pm" && h < 12) h += 12
            if (lower.contains("subah") || lower.contains("morning")) { if (h > 12) h -= 12 }
            if (lower.contains("shaam") || lower.contains("evening")) { if (h < 12) h += 12 }
            if (lower.contains("raat") || lower.contains("night")) { if (h < 8) h += 12 }
            val t = "%02d:%02d".format(h, mn)
            val d = if (lower.contains("kal") || lower.contains("tomorrow")) "kal" else "aaj"
            pendingTime = t
            context2 = "confirm"
            agentSpeak("${parsed.targetAppName ?: "Task"} $d $t baje — theek hai?")
            _agentState.value = AgentState.WAITING_FOLLOWUP
        } else {
            agentSpeak("Time samajh nahi aaya. Jaise '3 baje' ya 'subah 8 baje' — kab karna hai?")
        }
    }

    private suspend fun handleConfirm(input: String) {
        val lower = input.lowercase()
        val parsed = pendingParsed ?: return
        context2 = null

        val yes = lower.contains("han") || lower.contains("haan") || lower.contains("yes") ||
                  lower.contains("ok") || lower.contains("theek") || lower.contains("bilkul") ||
                  lower.contains("kar do") || lower.contains("schedule")
        val no = lower.contains("nahi") || lower.contains("no") || lower.contains("cancel") ||
                 lower.contains("mat")

        when {
            yes -> {
                val t = pendingTime
                if (t == null) {
                    agentSpeak("Abhi shuru karta hoon!")
                    delay(400)
                    executeNow(parsed)
                } else {
                    saveScheduled(parsed, t)
                }
            }
            no -> {
                agentSpeak("Theek hai, cancel kar diya. Koi aur kaam?")
                pendingParsed = null; pendingTime = null
                _agentState.value = AgentState.IDLE
            }
            else -> {
                pendingParsed = null; pendingTime = null
                handle(input)
            }
        }
    }
private suspend fun executeNow(parsed: NaturalLanguageTaskParser.ParsedTask) {
        val svc = AutoAgentAccessibilityService.getInstance()
        if (svc == null) {
            agentSpeak("Accessibility service ON nahi hai. Settings mein enable karein.")
            _agentState.value = AgentState.IDLE; pendingParsed = null; return
        }
        _agentState.value = AgentState.EXECUTING
        addMsg("⚙️ ${parsed.steps.size} steps chal rahe hain...", true, isAction = true)

        val status = svc.executeSteps(parsed.steps) { log ->
            viewModelScope.launch {
                if (log.success) addMsg("✅ ${log.description}", true, isAction = true)
                else addMsg("⚠️ ${log.description} — retry", true, isAction = true)
            }
        }

        pendingParsed = null; pendingTime = null

        when (status) {
            RunStatus.SUCCESS -> {
                memoryEngine.saveLastCommand(parsed.taskName)
                agentSpeak("Ho gaya! Koi aur kaam batana hai?")
                _agentState.value = AgentState.WAITING_FOLLOWUP
            }
            RunStatus.CANCELLED -> {
                agentSpeak("Aapke kehne par ruk gaya.")
                _agentState.value = AgentState.IDLE
            }
            else -> {
                agentSpeak("Kuch steps poore nahi hue. Dobara try karein?")
                _agentState.value = AgentState.IDLE
            }
        }
    }

    private suspend fun saveScheduled(parsed: NaturalLanguageTaskParser.ParsedTask, time: String) {
        _agentState.value = AgentState.EXECUTING
        try {
            val id = withContext(Dispatchers.IO) {
                repository.saveTask(TaskEntity(
                    id = 0, name = parsed.taskName, description = "Voice agent",
                    triggerType = "ONE_TIME", triggerTime = time, triggerDays = "[]",
                    intervalMinutes = 0, stepsJson = Gson().toJson(parsed.steps),
                    networkPolicy = "WIFI_PREFERRED", mobileDataAllowed = false,
                    isEnabled = true, requiresConfirmation = false, priority = 1,
                    createdAt = System.currentTimeMillis(), lastRunAt = null,
                    lastRunStatus = null, totalRuns = 0, successRuns = 0
                ))
            }
            val task = AgentTask(
                id = id, name = parsed.taskName, description = "Voice",
                triggerType = TriggerType.ONE_TIME, triggerTime = time, triggerDays = emptyList(),
                intervalMinutes = 0, steps = parsed.steps, networkPolicy = NetworkPolicy.WIFI_PREFERRED,
                mobileDataAllowed = false, isEnabled = true, requiresConfirmation = false,
                priority = 1, createdAt = System.currentTimeMillis(), lastRunAt = null,
                lastRunStatus = null, totalRuns = 0, successRuns = 0
            )
            TaskExecutorWorker.scheduleTask(context, task)
            memoryEngine.saveLastCommand(parsed.taskName)
            agentSpeak("${parsed.taskName} $time baje ke liye schedule ho gaya! Koi aur kaam?")
        } catch (e: Exception) {
            agentSpeak("Schedule nahi hua: ${e.message}")
            L.e("VAgent", "save error", e)
        }
        pendingParsed = null; pendingTime = null
        _agentState.value = AgentState.IDLE
    }

    private fun addMsg(text: String, isAgent: Boolean, isAction: Boolean = false) {
        _messages.update { it + ChatMessage(text = text, isAgent = isAgent, isAction = isAction) }
    }

    override fun onCleared() { tts?.stop(); tts?.shutdown(); super.onCleared() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceAgentScreen(onBack: () -> Unit, viewModel: VoiceAgentViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val messages by viewModel.messages.collectAsState()
    val agentState by viewModel.agentState.collectAsState()
    val partialText by viewModel.partialText.collectAsState()
    val listState = rememberLazyListState()
    var isListening by remember { mutableStateOf(false) }
    var showKeyboard by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }

    val recognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }
    val inf = rememberInfiniteTransition(label = "pulse")
    val pulse by inf.animateFloat(1f, 1.3f,
        infiniteRepeatable(tween(600, easing = EaseInOut), RepeatMode.Reverse), label = "p")

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    fun listen() {
        if (isListening) { recognizer.stopListening(); isListening = false; return }
        isListening = true
        showKeyboard = false
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        recognizer.startListening(i)
    }
    val autoListen by viewModel.autoListen.collectAsState()
    LaunchedEffect(autoListen) {
        if (autoListen) { delay(500); listen() }
    }
    LaunchedEffect(Unit) { delay(2200); listen() }
    DisposableEffect(Unit) {
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(r: Bundle?) {
                val t = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: return
                isListening = false
                viewModel.onInput(t)
            }
            override fun onPartialResults(r: Bundle?) {
                viewModel.setPartial(r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: "")
            }
            override fun onError(e: Int) { isListening = false }
            override fun onReadyForSpeech(p: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(t: Int, p: Bundle?) {}
        })
        onDispose { recognizer.destroy() }
    }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🤖", fontSize = 22.sp); Spacer(Modifier.width(8.dp))
                        Column {
                            Text("AutoAgent", fontWeight = FontWeight.Bold)
                            Text(when (agentState) {
                                AgentState.LISTENING -> "🎙 Sun raha hoon..."
                                AgentState.THINKING -> "💭 Soch raha hoon..."
                                AgentState.SPEAKING -> "🔊 Bol raha hoon..."
                                AgentState.EXECUTING -> "⚙️ Kaam kar raha hoon..."
                                AgentState.WAITING_FOLLOWUP -> "⏳ Jawab chahiye..."
                                else -> "Ji haan, batayein!"
                            }, style = MaterialTheme.typography.labelSmall,
                            color = when (agentState) {
                                AgentState.EXECUTING -> Color(0xFF4CAF50)
                                AgentState.LISTENING -> Color(0xFF2196F3)
                                AgentState.THINKING, AgentState.WAITING_FOLLOWUP -> Color(0xFFFF9800)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            })
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null) } },
                actions = {
                    IconButton(onClick = { AutoAgentAccessibilityService.getInstance()?.triggerEmergencyStop() }) {
                        Icon(Icons.Filled.Stop, null, tint = MaterialTheme.colorScheme.error)
                    }
                }
            )
        },
        bottomBar = {
            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
                .padding(12.dp, 8.dp, 12.dp, 28.dp)) {

                if (partialText.isNotEmpty()) {
                    Text(partialText, style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF2196F3), modifier = Modifier.padding(bottom = 6.dp))
                }

                if (showKeyboard) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = typed, onValueChange = { typed = it },
                            placeholder = { Text("Command likho...") },
                            modifier = Modifier.weight(1f), shape = RoundedCornerShape(24.dp),
                            singleLine = false, maxLines = 3)
                        IconButton(onClick = {
                            if (typed.isNotBlank()) {
                                viewModel.onInput(typed); typed = ""; showKeyboard = false
                            }
                        }) { Icon(Icons.Filled.Send, null, tint = MaterialTheme.colorScheme.primary) }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly, Alignment.CenterVertically) {
                    IconButton(onClick = { showKeyboard = !showKeyboard }) {
                        Icon(if (showKeyboard) Icons.Filled.Mic else Icons.Filled.Keyboard, null,
                            tint = MaterialTheme.colorScheme.primary)
                    }
                    Box(contentAlignment = Alignment.Center) {
                        if (isListening) Box(Modifier.size(80.dp).scale(pulse).clip(CircleShape)
                            .background(Color(0xFF2196F3).copy(0.25f)))
                        Button(onClick = { listen() }, modifier = Modifier.size(72.dp),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isListening) Color(0xFF2196F3)
                                else MaterialTheme.colorScheme.primary),
                            contentPadding = PaddingValues(0.dp)) {
                            Icon(if (isListening) Icons.Filled.MicOff else Icons.Filled.Mic,
                                null, modifier = Modifier.size(32.dp))
                        }
                    }
                    IconButton(onClick = {
                        AutoAgentAccessibilityService.getInstance()?.triggerEmergencyStop()
                    }) { Icon(Icons.Filled.Stop, null, tint = MaterialTheme.colorScheme.error) }
                }
            }
        }
    ) { pad ->
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(pad)
            .padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 12.dp)) {
            items(messages, key = { it.id }) { Bubble(it) }
            if (agentState == AgentState.THINKING || agentState == AgentState.EXECUTING) {
                item { TypingDots() }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
fun Bubble(msg: ChatMessage) {
    Row(Modifier.fillMaxWidth(),
        if (msg.isAgent) Arrangement.Start else Arrangement.End, Alignment.Bottom) {
        if (msg.isAgent) {
            Box(Modifier.size(32.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center) {
                Text(if (msg.isAction) "⚙️" else "🤖", fontSize = 14.sp)
            }
            Spacer(Modifier.width(6.dp))
        }
        Card(shape = if (msg.isAgent) RoundedCornerShape(18.dp,18.dp,18.dp,4.dp)
            else RoundedCornerShape(18.dp,18.dp,4.dp,18.dp),
            colors = CardDefaults.cardColors(containerColor = when {
                msg.isAction -> Color(0xFF4CAF50).copy(0.12f)
                msg.isAgent -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.secondaryContainer
            }), modifier = Modifier.widthIn(max = 290.dp)) {
            Text(msg.text, Modifier.padding(12.dp,10.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = if (msg.isAction) Color(0xFF1B5E20)
                else MaterialTheme.colorScheme.onSurface)
        }
        if (!msg.isAgent) {
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(32.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center) { Text("👤", fontSize = 14.sp) }
        }
    }
}

@Composable
fun TypingDots() {
    Row(verticalAlignment = Alignment.Bottom) {
        Box(Modifier.size(32.dp).clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center) { Text("🤖", fontSize = 14.sp) }
        Spacer(Modifier.width(6.dp))
        Card(shape = RoundedCornerShape(18.dp,18.dp,18.dp,4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Row(Modifier.padding(14.dp, 12.dp), horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically) {
                repeat(3) { i ->
                    val a by rememberInfiniteTransition(label = "d$i").animateFloat(
                        0f, 8f, infiniteRepeatable(tween(400, delayMillis = i*130),
                        RepeatMode.Reverse), label = "d$i")
                    Box(Modifier.size(7.dp).offset(y=(-a).dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary))
                }
            }
        }
    }
}
