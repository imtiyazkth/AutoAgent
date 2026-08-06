package com.autoagent.presentation.taskbuilder

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autoagent.data.db.TaskEntity
import com.autoagent.data.repository.AgentRepository
import com.autoagent.domain.model.ActionType
import com.autoagent.domain.model.InstalledAppInfo
import com.autoagent.domain.model.TaskStep
import com.autoagent.domain.usecase.AppScanner
import com.autoagent.util.L
import com.autoagent.util.PinManager
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class TaskBuilderState(
    val isSaving: Boolean = false,
    val savedOk: Boolean = false,
    val saveError: String? = null,
    val showPinDialog: Boolean = false,
    val pinError: String? = null,
    val showAppPicker: Boolean = false,
    val appList: List<InstalledAppInfo> = emptyList(),
    val appListLoading: Boolean = false,
    val appListError: String? = null,
    val appQuery: String = ""
)

@HiltViewModel
class TaskBuilderViewModel @Inject constructor(
    private val repository: AgentRepository,
    private val pinManager: PinManager,
    private val appScanner: AppScanner
) : ViewModel() {

    private val _state = MutableStateFlow(TaskBuilderState())
    val state: StateFlow<TaskBuilderState> = _state

    var pendingName = ""
    var pendingDesc = ""
    var pendingTrigger = "MANUAL"
    var pendingTime = ""
    var pendingApp: InstalledAppInfo? = null
    var pendingUrl = ""
    var pendingText = ""
    var pendingButton = ""

    fun loadTask(id: Long) {
        viewModelScope.launch {
            runCatching { repository.getTask(id) }.getOrNull()?.let { t ->
                pendingName = t.name
                pendingDesc = t.description
                pendingTrigger = t.triggerType
                pendingTime = t.triggerTime ?: ""
            }
        }
    }

    fun openAppPicker() {
        _state.value = _state.value.copy(showAppPicker = true, appListError = null)
        if (_state.value.appList.isEmpty()) loadAppList()
    }

    fun closeAppPicker() {
        _state.value = _state.value.copy(showAppPicker = false, appQuery = "")
    }

    fun setAppQuery(q: String) {
        _state.value = _state.value.copy(appQuery = q)
    }

    fun selectApp(app: InstalledAppInfo) {
        pendingApp = app
        _state.value = _state.value.copy(showAppPicker = false, appQuery = "")
        L.d("TaskBuilderVM", "App selected: ${app.packageName}")
    }

    fun clearApp() { pendingApp = null }

    private fun loadAppList() {
        viewModelScope.launch {
            _state.value = _state.value.copy(appListLoading = true, appListError = null)
            try {
                val apps = withContext(Dispatchers.IO) { appScanner.scanInstalledApps() }
                _state.value = _state.value.copy(
                    appList = apps,
                    appListLoading = false,
                    appListError = if (apps.isEmpty())
                        "Koi app nahi mili — package naam manually type karo"
                    else null
                )
            } catch (e: Exception) {
                L.e("TaskBuilderVM", "loadAppList failed", e)
                _state.value = _state.value.copy(
                    appListLoading = false,
                    appListError = "Apps load nahi ho sake — package naam manually type karo"
                )
            }
        }
    }

    fun requestSave(
        name: String, desc: String, trigger: String, time: String,
        url: String, text: String, button: String
    ) {
        pendingName = name; pendingDesc = desc
        pendingTrigger = trigger; pendingTime = time
        pendingUrl = url; pendingText = text; pendingButton = button
        _state.value = _state.value.copy(showPinDialog = true, pinError = null)
    }

    fun dismissPin() {
        _state.value = _state.value.copy(showPinDialog = false, pinError = null)
    }

    fun verifyAndSave(pin: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                val ok = runCatching { pinManager.verifyPin(pin) }.getOrDefault(false)
                if (ok) {
                    _state.value = _state.value.copy(
                        showPinDialog = false, pinError = null, isSaving = true
                    )
                    saveTask(onSuccess)
                } else {
                    _state.value = _state.value.copy(pinError = "Galat PIN — dobara try karo")
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    pinError = "Error: ${e.message}", isSaving = false
                )
            }
        }
    }

    private suspend fun saveTask(onSuccess: () -> Unit) {
        try {
            val steps = mutableListOf<TaskStep>()
            var sid = 1
            pendingApp?.let {
                steps.add(TaskStep(sid++, ActionType.LAUNCH_APP,
                    targetApp = it.packageName, delayMs = 1500L,
                    retryCount = 2, description = "${it.appName} launch karo"))
            }
            if (pendingUrl.isNotBlank()) steps.add(TaskStep(sid++, ActionType.OPEN_URL,
                targetUrl = pendingUrl, delayMs = 2000L, description = "URL: $pendingUrl"))
            if (pendingText.isNotBlank()) steps.add(TaskStep(sid++, ActionType.ENTER_TEXT,
                inputText = pendingText, delayMs = 1000L, description = "Type: $pendingText"))
            if (pendingButton.isNotBlank()) steps.add(TaskStep(sid, ActionType.TAP_BUTTON,
                buttonText = pendingButton, delayMs = 500L,
                retryCount = 3, description = "'$pendingButton' tap karo"))
            if (steps.isEmpty()) steps.add(TaskStep(1, ActionType.CONFIRM_ACTION,
                delayMs = 500L, description = "Manual confirm"))

            repository.saveTask(TaskEntity(
                name = pendingName.ifBlank { pendingApp?.appName ?: "Naya Task" },
                description = pendingDesc,
                triggerType = pendingTrigger.ifBlank { "MANUAL" },
                triggerTime = pendingTime.ifBlank { null },
                triggerDays = "[]", intervalMinutes = 0,
                stepsJson = Gson().toJson(steps),
                networkPolicy = "WIFI_PREFERRED",
                mobileDataAllowed = false, isEnabled = true,
                requiresConfirmation = false, priority = 1,
                createdAt = System.currentTimeMillis(),
                lastRunAt = null, lastRunStatus = null,
                totalRuns = 0, successRuns = 0
            ))
            _state.value = _state.value.copy(isSaving = false, savedOk = true)
            onSuccess()
        } catch (e: Exception) {
            L.e("TaskBuilderVM", "saveTask failed", e)
            _state.value = _state.value.copy(
                isSaving = false,
                saveError = "Save nahi hua: ${e.message}"
            )
        }
    }

    fun dismissError() { _state.value = _state.value.copy(saveError = null) }

    fun filteredApps(): List<InstalledAppInfo> {
        val q = _state.value.appQuery.lowercase()
        return if (q.isEmpty()) _state.value.appList
        else _state.value.appList.filter {
            it.appName.lowercase().contains(q) ||
            it.packageName.lowercase().contains(q)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskBuilderScreen(
    editTaskId: Long? = null,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: TaskBuilderViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var trigger by remember { mutableStateOf("MANUAL") }
    var time by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var inputText by remember { mutableStateOf("") }
    var buttonText by remember { mutableStateOf("") }
    var manualPkg by remember { mutableStateOf("") }

    LaunchedEffect(editTaskId) {
        editTaskId?.let {
            viewModel.loadTask(it)
            name = viewModel.pendingName
            desc = viewModel.pendingDesc
            trigger = viewModel.pendingTrigger
            time = viewModel.pendingTime
        }
    }

    val selectedApp = viewModel.pendingApp

    // PIN Dialog
    if (state.showPinDialog) {
        var pin by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { viewModel.dismissPin() },
            title = { Text("🔐 PIN Enter Karo", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Task save karne ke liye 10-digit PIN.",
                        style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = pin,
                        onValueChange = {
                            if (it.length <= 10 && it.all(Char::isDigit)) pin = it
                        },
                        label = { Text("10-Digit PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = state.pinError != null
                    )
                    state.pinError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Text("${pin.length}/10",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (pin.length == 10) Color(0xFF4CAF50)
                        else MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { viewModel.dismissPin() }) {
                            Text("Cancel")
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { viewModel.verifyAndSave(pin) { onSaved() } },
                            enabled = pin.length == 10 && !state.isSaving
                        ) {
                            if (state.isSaving)
                                CircularProgressIndicator(Modifier.size(16.dp),
                                    strokeWidth = 2.dp)
                            else Text("Save Karo ✅")
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    // Save Error Dialog
    state.saveError?.let { err ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissError() },
            title = { Text("❌ Save Nahi Hua", fontWeight = FontWeight.Bold) },
            text = { Text(err) },
            confirmButton = {
                Button(onClick = { viewModel.dismissError() }) { Text("OK") }
            }
        )
    }

    // App Picker Bottom Sheet — inline, no separate screen
    if (state.showAppPicker) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.closeAppPicker() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
                    .padding(horizontal = 16.dp)
            ) {
                Text("📱 App Choose Karo",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 12.dp))

                OutlinedTextField(
                    value = state.appQuery,
                    onValueChange = { viewModel.setAppQuery(it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("App ya package dhundo...") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = {
                        if (state.appQuery.isNotEmpty())
                            IconButton(onClick = { viewModel.setAppQuery("") }) {
                                Icon(Icons.Filled.Clear, null)
                            }
                    },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))

                when {
                    state.appListLoading -> {
                        Box(Modifier.fillMaxWidth().height(200.dp),
                            contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(Modifier.height(8.dp))
                                Text("Apps load ho rahe hain...",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    state.appListError != null -> {
                        Card(colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFF44336).copy(0.1f)),
                            modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text("⚠️ ${state.appListError}",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = manualPkg,
                            onValueChange = { manualPkg = it },
                            label = { Text("Package naam type karo") },
                            placeholder = { Text("com.whatsapp") },
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                if (manualPkg.isNotBlank())
                                    IconButton(onClick = {
                                        viewModel.selectApp(InstalledAppInfo(
                                            packageName = manualPkg.trim(),
                                            appName = manualPkg.trim()
                                                .substringAfterLast("."),
                                            versionName = "?",
                                            installDate = 0,
                                            lastUpdated = 0,
                                            canLaunch = true,
                                            category = "Manual",
                                            launchActivity = null
                                        ))
                                        manualPkg = ""
                                    }) {
                                        Icon(Icons.Filled.Check, null,
                                            tint = Color(0xFF4CAF50))
                                    }
                            }
                        )
                    }
                    else -> {
                        val filtered = viewModel.filteredApps()
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 40.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (filtered.isEmpty()) item {
                                Box(Modifier.fillMaxWidth().height(100.dp),
                                    contentAlignment = Alignment.Center) {
                                    Text("Koi app nahi mili",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            items(filtered, key = { it.packageName }) { app ->
                                Card(modifier = Modifier.fillMaxWidth()
                                    .clickable { viewModel.selectApp(app) },
                                    shape = RoundedCornerShape(10.dp)) {
                                    Row(Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.Android, null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(32.dp))
                                        Spacer(Modifier.width(10.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(app.appName,
                                                fontWeight = FontWeight.SemiBold)
                                            Text(app.packageName,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme
                                                    .onSurfaceVariant)
                                        }
                                        Icon(Icons.Filled.ChevronRight, null)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (editTaskId != null) "✏️ Task Edit" else "➕ Naya Task",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, null)
                    }
                }
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Column(Modifier.padding(16.dp, 12.dp, 16.dp, 28.dp)) {
                    Button(
                        onClick = {
                            viewModel.requestSave(
                                name, desc, trigger, time,
                                url, inputText, buttonText
                            )
                        },
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        enabled = (name.isNotBlank() || selectedApp != null ||
                                url.isNotBlank() || inputText.isNotBlank()) &&
                                !state.isSaving,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Save ho raha hai...")
                        } else {
                            Icon(Icons.Filled.Save, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Task Save Karo 🔐",
                                fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                    if (name.isBlank() && selectedApp == null &&
                        url.isBlank() && inputText.isBlank())
                        Text("* Naam, App, URL ya Text mein se kuch bhar do",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("📝 Task Ki Jankari", fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(value = name, onValueChange = { name = it },
                label = { Text("Task Naam *") },
                placeholder = { Text("Jaise: WhatsApp backup") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                leadingIcon = { Icon(Icons.Filled.Label, null) },
                singleLine = true)
            OutlinedTextField(value = desc, onValueChange = { desc = it },
                label = { Text("Description (optional)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                leadingIcon = { Icon(Icons.Filled.Notes, null) },
                minLines = 2)
            Divider()

            Text("📱 App Choose Karo (Optional)", fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleSmall)
            if (selectedApp != null) {
                Card(colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF4CAF50).copy(0.1f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Android, null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(32.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(selectedApp.appName, fontWeight = FontWeight.Bold)
                            Text(selectedApp.packageName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { viewModel.clearApp() }) {
                            Icon(Icons.Filled.Close, null,
                                tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            } else {
                OutlinedButton(
                    onClick = { viewModel.openAppPicker() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.Apps, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Installed App Choose Karo")
                }
            }
            Divider()

            Text("⏰ Kab Chalega?", fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()) {
                listOf("MANUAL" to "Manual",
                    "DAILY" to "Roz",
                    "ONE_TIME" to "Ek Baar").forEach { (t, l) ->
                    FilterChip(selected = trigger == t,
                        onClick = { trigger = t },
                        label = { Text(l, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.weight(1f))
                }
            }
            if (trigger != "MANUAL") {
                OutlinedTextField(value = time, onValueChange = { time = it },
                    label = { Text("Time (HH:MM)") },
                    placeholder = { Text("08:30") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = { Icon(Icons.Filled.Schedule, null) },
                    singleLine = true)
            }
            Divider()

            Text("🤖 Kya Karega?", fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(value = url, onValueChange = { url = it },
                label = { Text("URL Open Karo (optional)") },
                placeholder = { Text("https://claude.ai") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                leadingIcon = { Icon(Icons.Filled.Link, null) },
                singleLine = true)
            OutlinedTextField(value = inputText, onValueChange = { inputText = it },
                label = { Text("Text Type Karo (optional)") },
                placeholder = { Text("Message ya prompt...") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                leadingIcon = { Icon(Icons.Filled.Edit, null) },
                minLines = 3)
            OutlinedTextField(value = buttonText, onValueChange = { buttonText = it },
                label = { Text("Button Press Karo (optional)") },
                placeholder = { Text("Send, Submit, Next...") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                leadingIcon = { Icon(Icons.Filled.TouchApp, null) },
                singleLine = true)

            if (selectedApp != null || url.isNotBlank() || inputText.isNotBlank()) {
                Divider()
                Text("👁️ Preview", fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleSmall)
                Card(colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        var n = 1
                        selectedApp?.let {
                            Text("$n. 📱 ${it.appName} launch karo", fontSize = 13.sp); n++ }
                        if (url.isNotBlank()) {
                            Text("$n. 🔗 ${url.take(40)}", fontSize = 13.sp); n++ }
                        if (inputText.isNotBlank()) {
                            Text("$n. ⌨️ \"${inputText.take(30)}\"", fontSize = 13.sp); n++ }
                        if (buttonText.isNotBlank())
                            Text("$n. 👆 '$buttonText' tap karo", fontSize = 13.sp)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
