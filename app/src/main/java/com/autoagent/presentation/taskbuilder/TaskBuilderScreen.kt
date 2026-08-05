package com.autoagent.presentation.taskbuilder

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autoagent.data.db.TaskEntity
import com.autoagent.domain.model.ActionType
import com.autoagent.domain.model.TaskStep
import com.autoagent.data.repository.AgentRepository
import com.autoagent.domain.model.InstalledAppInfo
import com.autoagent.util.L
import com.autoagent.util.PinManager
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// =============================================
// ViewModel — PIN only on Save
// =============================================
data class TaskBuilderState(
    val isSaving: Boolean = false,
    val showPinDialog: Boolean = false,
    val pinError: String? = null,
    val saveError: String? = null,
    val savedOk: Boolean = false
)

@HiltViewModel
class TaskBuilderViewModel @Inject constructor(
    private val repository: AgentRepository,
    private val pinManager: PinManager
) : ViewModel() {

    private val _state = MutableStateFlow(TaskBuilderState())
    val state: StateFlow<TaskBuilderState> = _state

    // Task data kept here — never lost on PIN dialog
    private var pendingName = ""
    private var pendingDesc = ""
    private var pendingTrigger = ""
    private var pendingTime = ""
    private var pendingApp: InstalledAppInfo? = null
    private var pendingUrl = ""
    private var pendingText = ""
    private var pendingButton = ""

    // Called when user taps Save — show PIN first
    fun onSaveRequested(
        name: String, desc: String, triggerType: String, triggerTime: String,
        selectedApp: InstalledAppInfo?, url: String, inputText: String, buttonText: String
    ) {
        L.d("TaskBuilderVM", "Save requested, showing PIN")
        // Store all task data safely
        pendingName = name
        pendingDesc = desc
        pendingTrigger = triggerType
        pendingTime = triggerTime
        pendingApp = selectedApp
        pendingUrl = url
        pendingText = inputText
        pendingButton = buttonText
        // Show PIN dialog — task data is safe
        _state.value = _state.value.copy(showPinDialog = true, pinError = null)
    }

    // User entered PIN — verify then save
    fun onPinVerified(pin: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                L.d("TaskBuilderVM", "Verifying PIN")
                val correct = runCatching { pinManager.verifyPin(pin) }.getOrDefault(false)
                if (correct) {
                    L.d("TaskBuilderVM", "PIN correct — saving task")
                    _state.value = _state.value.copy(
                        showPinDialog = false, pinError = null, isSaving = true
                    )
                    saveTaskInternal(onSuccess)
                } else {
                    L.d("TaskBuilderVM", "Wrong PIN")
                    // STAY on task builder, keep all data, just show error
                    _state.value = _state.value.copy(pinError = "Galat PIN — dobara try karo")
                }
            } catch (e: Exception) {
                L.e("TaskBuilderVM", "PIN verify error", e)
                _state.value = _state.value.copy(
                    pinError = "Error: ${e.message}",
                    isSaving = false
                )
            }
        }
    }

    fun dismissPinDialog() {
        L.d("TaskBuilderVM", "PIN dialog dismissed — task data preserved")
        _state.value = _state.value.copy(showPinDialog = false, pinError = null)
        // All pending* fields still have the data — user can continue editing
    }

    private suspend fun saveTaskInternal(onSuccess: () -> Unit) {
        try {
            // FIX: use typed TaskStep objects, NOT raw Map<String,Any>.
            // Gson deserializes Map<String,Any> with type erasure — Int becomes Double,
            // causing ClassCastException when TaskStep.id (Int) is read back later.
            val steps = mutableListOf<TaskStep>()
            var stepId = 1

            pendingApp?.let {
                steps.add(TaskStep(
                    id = stepId++,
                    type = ActionType.LAUNCH_APP,
                    targetApp = it.packageName,
                    delayMs = 1500L,
                    retryCount = 2,
                    description = "${it.appName} open karo"
                ))
            }
            if (pendingUrl.isNotBlank()) {
                steps.add(TaskStep(
                    id = stepId++,
                    type = ActionType.OPEN_URL,
                    targetUrl = pendingUrl,
                    delayMs = 2000L,
                    retryCount = 2,
                    description = "URL open karo"
                ))
            }
            if (pendingText.isNotBlank()) {
                steps.add(TaskStep(
                    id = stepId++,
                    type = ActionType.ENTER_TEXT,
                    inputText = pendingText,
                    delayMs = 1000L,
                    retryCount = 2,
                    description = "Text type karo"
                ))
            }
            if (pendingButton.isNotBlank()) {
                steps.add(TaskStep(
                    id = stepId,
                    type = ActionType.TAP_BUTTON,
                    buttonText = pendingButton,
                    delayMs = 500L,
                    retryCount = 3,
                    description = "'$pendingButton' tap karo"
                ))
            }

            if (steps.isEmpty()) {
                // Always add at least a LAUNCH_APP or CONFIRM step so task is not empty
                steps.add(TaskStep(
                    id = 1,
                    type = ActionType.CONFIRM_ACTION,
                    delayMs = 500L,
                    description = "Task confirm"
                ))
            }

            repository.saveTask(TaskEntity(
                name = pendingName.ifBlank { pendingApp?.appName ?: "My Task" },
                description = pendingDesc,
                triggerType = pendingTrigger.ifBlank { "MANUAL" },
                triggerTime = pendingTime.ifBlank { null },
                triggerDays = "[]",
                intervalMinutes = 0,
                stepsJson = Gson().toJson(steps),
                networkPolicy = "WIFI_PREFERRED",
                mobileDataAllowed = false,
                isEnabled = true,
                requiresConfirmation = false,
                priority = 1,
                createdAt = System.currentTimeMillis(),
                lastRunAt = null,
                lastRunStatus = null,
                totalRuns = 0,
                successRuns = 0
            ))

            L.d("TaskBuilderVM", "Task saved: ${steps.size} steps")
            _state.value = _state.value.copy(isSaving = false, savedOk = true)
            onSuccess()
        } catch (e: Exception) {
            L.e("TaskBuilderVM", "Save failed", e)
            _state.value = _state.value.copy(
                isSaving = false,
                saveError = "Task save nahi hua: ${e.message}"
            )
        }
    }

    fun dismissSaveError() {
        _state.value = _state.value.copy(saveError = null)
    }
}

// =============================================
// TASK BUILDER SCREEN
// PIN appears ONLY when user taps Save
// =============================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskBuilderScreen(
    preSelectedApp: InstalledAppInfo? = null,
    onBack: () -> Unit,
    onPickApp: () -> Unit,
    onSaved: () -> Unit = onBack,
    viewModel: TaskBuilderViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    // Task fields — kept in Compose state, NEVER cleared by PIN dialog
    var name by remember { mutableStateOf(preSelectedApp?.appName?.let { "$it Task" } ?: "") }
    var desc by remember { mutableStateOf("") }
    var triggerType by remember { mutableStateOf("MANUAL") }
    var triggerTime by remember { mutableStateOf("") }
    var selectedApp by remember { mutableStateOf(preSelectedApp) }
    var url by remember { mutableStateOf("") }
    var inputText by remember { mutableStateOf("") }
    var buttonText by remember { mutableStateOf("") }

    // Update selectedApp if parent changes it (app picker returned)
    LaunchedEffect(preSelectedApp) {
        if (preSelectedApp != null && selectedApp == null) {
            selectedApp = preSelectedApp
            if (name.isBlank()) name = "${preSelectedApp.appName} Task"
        }
    }

    // PIN Dialog — overlays screen, ALL task state preserved
    if (state.showPinDialog) {
        TaskSavePinDialog(
            error = state.pinError,
            onVerify = { pin ->
                viewModel.onPinVerified(pin) { onSaved() }
            },
            onDismiss = {
                viewModel.dismissPinDialog()
                // User returns to task builder with ALL fields intact
            }
        )
    }

    // Save error dialog
    state.saveError?.let { err ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissSaveError() },
            title = { Text("❌ Save Failed", fontWeight = FontWeight.Bold) },
            text = { Text(err) },
            confirmButton = {
                Button(onClick = { viewModel.dismissSaveError() }) { Text("OK") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Naya Task", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, null)
                    }
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // STEP 1 — App
            Text("📱 Step 1: App Choose karo",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                onClick = onPickApp
            ) {
                Row(modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Apps, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(selectedApp?.appName ?: "App select karo (optional)",
                            fontWeight = FontWeight.Bold)
                        Text(selectedApp?.packageName ?: "Tap karke choose karo",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Filled.ChevronRight, null)
                }
            }

            Divider()

            // STEP 2 — Name
            Text("✏️ Step 2: Task naam",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall)

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Task naam *") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                leadingIcon = { Icon(Icons.Filled.Label, null) }
            )

            Divider()

            // STEP 3 — Trigger
            Text("⏰ Step 3: Kab chalega?",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("MANUAL" to "Manual", "DAILY" to "Roz", "ONE_TIME" to "Ek Baar").forEach { (t, l) ->
                    FilterChip(
                        selected = triggerType == t,
                        onClick = { triggerType = t },
                        label = { Text(l, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            if (triggerType != "MANUAL") {
                OutlinedTextField(
                    value = triggerTime,
                    onValueChange = { triggerTime = it },
                    label = { Text("Time (HH:MM)") },
                    placeholder = { Text("08:00") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = { Icon(Icons.Filled.Schedule, null) }
                )
            }

            Divider()

            // STEP 4 — Actions
            Text("🤖 Step 4: Kya karna hai?",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall)

            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("URL open karo (optional)") },
                placeholder = { Text("https://claude.ai") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                leadingIcon = { Icon(Icons.Filled.Link, null) }
            )

            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                label = { Text("Text type karo (optional)") },
                placeholder = { Text("Prompt ya message...") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                shape = RoundedCornerShape(12.dp),
                leadingIcon = { Icon(Icons.Filled.Edit, null) }
            )

            OutlinedTextField(
                value = buttonText,
                onValueChange = { buttonText = it },
                label = { Text("Button tap karo (optional)") },
                placeholder = { Text("Send, Submit...") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                leadingIcon = { Icon(Icons.Filled.TouchApp, null) }
            )

            // Preview
            if (selectedApp != null || url.isNotBlank() || inputText.isNotBlank()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("👁️ Preview:", fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(4.dp))
                        var n = 1
                        selectedApp?.let { Text("${n++}. 📱 ${it.appName} launch hogi") }
                        if (url.isNotBlank()) Text("${n++}. 🔗 ${url.take(50)}")
                        if (inputText.isNotBlank()) Text("${n++}. ⌨️ '${inputText.take(40)}'")
                        if (buttonText.isNotBlank()) Text("${n}. 👆 '$buttonText' tap")
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // SAVE BUTTON — PIN appears HERE, not before
            Button(
                onClick = {
                    L.d("TaskBuilder", "Save tapped — requesting PIN")
                    viewModel.onSaveRequested(
                        name, desc, triggerType, triggerTime,
                        selectedApp, url, inputText, buttonText
                    )
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = (name.isNotBlank() || selectedApp != null) && !state.isSaving,
                shape = RoundedCornerShape(14.dp)
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Save ho raha hai...")
                } else {
                    Icon(Icons.Filled.Lock, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Save Task (PIN Required)", fontWeight = FontWeight.Bold)
                }
            }

            if (name.isBlank() && selectedApp == null) {
                Text("* Task naam ya App zaroori hai",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

// PIN Dialog specifically for saving task
@Composable
fun TaskSavePinDialog(
    error: String?,
    onVerify: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🔐 Task Save karne ke liye PIN", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Task save karne se pehle apna PIN enter karo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 10 && it.all(Char::isDigit)) pin = it },
                    label = { Text("10-Digit PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = error != null
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
                Text("${pin.length}/10", style = MaterialTheme.typography.labelSmall,
                    color = if (pin.length == 10) Color(0xFF4CAF50)
                    else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Button(
                onClick = { if (pin.length == 10) onVerify(pin) },
                enabled = pin.length == 10
            ) { Text("Confirm & Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel (task data safe hai)")
            }
        }
    )
}
