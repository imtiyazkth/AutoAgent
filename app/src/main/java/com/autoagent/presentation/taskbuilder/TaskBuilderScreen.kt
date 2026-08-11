package com.autoagent.personal.presentation.taskbuilder

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autoagent.personal.data.db.AppCacheDao
import com.autoagent.personal.data.db.AppCacheEntity
import com.autoagent.personal.data.db.TaskEntity
import com.autoagent.personal.data.repository.AgentRepository
import com.autoagent.personal.domain.model.ActionType
import com.autoagent.personal.domain.model.InstalledAppInfo
import com.autoagent.personal.domain.model.TaskStep
import com.autoagent.personal.domain.usecase.AppScanner
import com.autoagent.personal.util.L
import com.autoagent.personal.util.PinManager
import com.autoagent.personal.service.scheduler.TaskExecutorWorker
import com.autoagent.personal.domain.model.*
import com.google.gson.Gson
import android.content.Context
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
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
    val appQuery: String = "",
    val lastScanInfo: String = "",
    val scanProgress: String = ""
)

@HiltViewModel
class TaskBuilderViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: AgentRepository,
    private val pinManager: PinManager,
    private val appScanner: AppScanner,
    private val appCacheDao: AppCacheDao
) : ViewModel() {

    private val _state = MutableStateFlow(TaskBuilderState())
    val state: StateFlow<TaskBuilderState> = _state

    var pendingName = ""
    var pendingDesc = ""
    var pendingTrigger = "MANUAL"
    var pendingTime = ""
    var pendingDate = ""
    var pendingApp: InstalledAppInfo? = null
    var aiSteps: List<com.autoagent.personal.domain.model.TaskStep> = emptyList()
    var pendingUrl = ""
    var pendingText = ""
    var pendingButton = ""

    init {
        loadCachedApps()
    }

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

    private fun loadCachedApps() {
        viewModelScope.launch {
            try {
                val cached = withContext(Dispatchers.IO) { appCacheDao.getAllAppsOnce() }
                val lastScan = withContext(Dispatchers.IO) { appCacheDao.getLastScanTime() }
                if (cached.isNotEmpty()) {
                    val scanStr = lastScan?.let {
                        SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date(it))
                    } ?: ""
                    _state.update { s ->
                        s.copy(
                            appList = cached.map { it.toInfo() },
                            lastScanInfo = if (scanStr.isNotEmpty()) "Cached: $scanStr" else ""
                        )
                    }
                    L.d("TaskVM", "Loaded ${cached.size} apps from cache")
                }
                // Trigger background scan to refresh cache
                scanInBackground()
            } catch (e: Exception) {
                L.e("TaskVM", "loadCachedApps error", e)
                scanInBackground()
            }
        }
    }

    fun openAppPicker() {
        _state.update { it.copy(showAppPicker = true, appListError = null) }
        if (_state.value.appList.isEmpty()) scanInBackground()
    }

    fun closeAppPicker() {
        _state.update { it.copy(showAppPicker = false, appQuery = "") }
    }

    fun setAppQuery(q: String) {
        _state.update { it.copy(appQuery = q) }
    }

    fun selectApp(app: InstalledAppInfo) {
        pendingApp = app
        _state.update { it.copy(showAppPicker = false, appQuery = "") }
        L.d("TaskVM", "Selected: ${app.packageName}")
    }

    fun clearApp() { pendingApp = null }

    fun rescanApps() { scanInBackground(force = true) }

    private fun scanInBackground(force: Boolean = false) {
        viewModelScope.launch {
            try {
                _state.update { it.copy(appListLoading = true, scanProgress = "Scanning...") }
                val apps = withContext(Dispatchers.IO) { appScanner.scanInstalledApps() }
                if (apps.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        appCacheDao.upsertAll(apps.map { it.toEntity() })
                    }
                    val now = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date())
                    _state.update { s ->
                        s.copy(
                            appList = apps,
                            appListLoading = false,
                            appListError = null,
                            lastScanInfo = "Scanned: $now (${apps.size} apps)",
                            scanProgress = ""
                        )
                    }
                    L.d("TaskVM", "Scanned ${apps.size} apps")
                } else {
                    _state.update { s ->
                        s.copy(
                            appListLoading = false,
                            scanProgress = "",
                            appListError = if (s.appList.isEmpty())
                                "Apps scan nahi ho sake — package naam manually type karo"
                            else null
                        )
                    }
                }
            } catch (e: Exception) {
                L.e("TaskVM", "scanInBackground error", e)
                _state.update { s ->
                    s.copy(
                        appListLoading = false,
                        scanProgress = "",
                        appListError = if (s.appList.isEmpty())
                            "Scan fail: ${e.message}" else null
                    )
                }
            }
        }
    }

    fun requestSave(name: String, desc: String, trigger: String, time: String,
                    date: String, url: String, text: String, button: String) {
        pendingName = name; pendingDesc = desc; pendingTrigger = trigger
        pendingTime = time; pendingDate = date
        pendingUrl = url; pendingText = text; pendingButton = button
        _state.update { it.copy(showPinDialog = true, pinError = null) }
    }

    fun dismissPin() {
        _state.update { it.copy(showPinDialog = false, pinError = null) }
    }

    fun verifyAndSave(pin: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                val ok = withContext(Dispatchers.IO) {
                    runCatching { pinManager.verifyPin(pin) }.getOrDefault(false)
                }
                if (ok) {
                    _state.update { it.copy(showPinDialog = false, pinError = null, isSaving = true) }
                    val taskId = withContext(Dispatchers.IO) { saveTask() }
                    withContext(Dispatchers.Main) { scheduleCurrentTask(taskId) }
                    _state.update { it.copy(isSaving = false, savedOk = true) }
                    onSuccess()
                } else {
                    _state.update { it.copy(pinError = "Galat PIN — dobara try karo") }
                }
            } catch (e: Exception) {
                L.e("TaskVM", "verifyAndSave error", e)
                _state.update { it.copy(isSaving = false, saveError = "Save nahi hua: ${e.message}") }
            }
        }
    }

    private suspend fun saveTask(): Long {
        // AI steps ko priority do — agar AI ne steps diye hain to wahi use karo
        if (aiSteps.isNotEmpty()) {
            return repository.saveTask(TaskEntity(
                id = 0, name = pendingName.ifBlank { "AI Task" },
                description = pendingDesc,
                triggerType = pendingTrigger,
                triggerTime = pendingTime.ifBlank { null },
                triggerDays = "[]", intervalMinutes = 0,
                stepsJson = com.google.gson.Gson().toJson(aiSteps),
                networkPolicy = "WIFI_PREFERRED", mobileDataAllowed = false,
                isEnabled = true, requiresConfirmation = false, priority = 1,
                createdAt = System.currentTimeMillis(), lastRunAt = null,
                lastRunStatus = null, totalRuns = 0, successRuns = 0
            ))
        }
        val steps = mutableListOf<TaskStep>()
        var sid = 1
        pendingApp?.let {
            steps.add(TaskStep(sid++, ActionType.LAUNCH_APP,
                targetApp = it.packageName, delayMs = 2000L,
                retryCount = 3, description = "${it.appName} launch karo"))
        }
        if (pendingUrl.isNotBlank()) steps.add(TaskStep(sid++, ActionType.OPEN_URL,
            targetUrl = pendingUrl, delayMs = 2000L, description = "URL: $pendingUrl"))
        if (pendingText.isNotBlank()) steps.add(TaskStep(sid++, ActionType.ENTER_TEXT,
            inputText = pendingText, delayMs = 1000L, description = "Type: $pendingText"))
        if (pendingButton.isNotBlank()) steps.add(TaskStep(sid, ActionType.TAP_BUTTON,
            buttonText = pendingButton, delayMs = 500L, retryCount = 3,
            description = "'$pendingButton' tap karo"))
        if (steps.isEmpty()) steps.add(TaskStep(1, ActionType.CONFIRM_ACTION,
            delayMs = 500L, description = "Manual confirm"))

        val triggerTime = when {
            pendingTrigger == "ONE_TIME" && pendingDate.isNotBlank() ->
                "${pendingDate} ${pendingTime}".trim()
            pendingTime.isNotBlank() -> pendingTime
            else -> null
        }

        return repository.saveTask(TaskEntity(
            name = pendingName.ifBlank { pendingApp?.appName ?: "Naya Task" },
            description = pendingDesc,
            triggerType = pendingTrigger.ifBlank { "MANUAL" },
            triggerTime = triggerTime,
            triggerDays = "[]", intervalMinutes = 0,
            stepsJson = Gson().toJson(steps),
            networkPolicy = "WIFI_PREFERRED",
            mobileDataAllowed = false, isEnabled = true,
            requiresConfirmation = false, priority = 1,
            createdAt = System.currentTimeMillis(),
            lastRunAt = null, lastRunStatus = null,
            totalRuns = 0, successRuns = 0
        ))
        L.d("TaskVM", "Task saved with ${steps.size} typed steps")
    }

    private fun scheduleCurrentTask(taskId: Long) {
        try {
            val triggerTime = when {
                pendingTrigger == "ONE_TIME" && pendingDate.isNotBlank() ->
                    "${pendingDate} ${pendingTime}".trim()
                pendingTime.isNotBlank() -> pendingTime
                else -> null
            }
            val task = com.autoagent.personal.domain.model.AgentTask(
                id = taskId,
                name = pendingName.ifBlank { pendingApp?.appName ?: "Naya Task" },
                description = pendingDesc,
                triggerType = try { com.autoagent.personal.domain.model.TriggerType.valueOf(pendingTrigger) } catch (e: Exception) { com.autoagent.personal.domain.model.TriggerType.MANUAL },
                triggerTime = triggerTime,
                triggerDays = emptyList(),
                intervalMinutes = 0,
                steps = emptyList(),
                networkPolicy = com.autoagent.personal.domain.model.NetworkPolicy.WIFI_PREFERRED,
                mobileDataAllowed = false,
                isEnabled = true,
                requiresConfirmation = false,
                priority = 1,
                createdAt = System.currentTimeMillis(),
                lastRunAt = null, lastRunStatus = null,
                totalRuns = 0, successRuns = 0
            )
            TaskExecutorWorker.scheduleTask(context, task)
            L.d("TaskVM", "Task scheduled: ${task.name} trigger=${task.triggerType}")
        } catch (e: Exception) {
            L.e("TaskVM", "scheduleCurrentTask error", e)
        }
    }

    fun dismissError() { _state.update { it.copy(saveError = null) } }

    fun filteredApps(): List<InstalledAppInfo> {
        val q = _state.value.appQuery.lowercase().trim()
        return if (q.isEmpty()) _state.value.appList
        else _state.value.appList.filter {
            it.appName.lowercase().contains(q) || it.packageName.lowercase().contains(q)
        }
    }

    fun messagingApps(): List<InstalledAppInfo> =
        _state.value.appList.filter {
            it.category == "Messaging" || it.category == "Social"
        }
}

fun InstalledAppInfo.toEntity() = AppCacheEntity(
    packageName = packageName, appName = appName,
    versionName = versionName, category = category,
    canLaunch = canLaunch, installDate = installDate,
    lastUpdated = lastUpdated, launchActivity = launchActivity,
    scannedAt = System.currentTimeMillis()
)

fun AppCacheEntity.toInfo() = InstalledAppInfo(
    packageName = packageName, appName = appName,
    versionName = versionName, category = category,
    canLaunch = canLaunch, installDate = installDate,
    lastUpdated = lastUpdated, launchActivity = launchActivity
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskBuilderScreen(
    editTaskId: Long? = null,
    prefillFromAi: com.autoagent.personal.ai.NaturalLanguageTaskParser.ParsedTask? = null,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: TaskBuilderViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var trigger by remember { mutableStateOf("MANUAL") }
    var time by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var inputText by remember { mutableStateOf("") }
    var buttonText by remember { mutableStateOf("") }
    var manualPkg by remember { mutableStateOf("") }
    val selectedApp = viewModel.pendingApp

    LaunchedEffect(editTaskId) {
        editTaskId?.let {
            viewModel.loadTask(it)
            name = viewModel.pendingName
            desc = viewModel.pendingDesc
            trigger = viewModel.pendingTrigger
            time = viewModel.pendingTime
        }
    }

    LaunchedEffect(prefillFromAi) {
        prefillFromAi?.let { ai ->
            if (name.isBlank()) name = ai.taskName
            ai.targetApp?.let { pkg ->
                viewModel.state.value.appList.firstOrNull { it.packageName == pkg }
                    ?.let { viewModel.selectApp(it) }
            }
            if (inputText.isBlank()) inputText = ai.message ?: ""
            if (url.isBlank()) url = ai.url ?: ""
            ai.scheduledHour?.let { h ->
                if (h >= 0) time = "%02d:%02d".format(h, ai.scheduledMinute ?: 0)
            }
            if (trigger == "MANUAL" && ai.scheduledHour != null) trigger = "ONE_TIME"
            viewModel.aiSteps = ai.steps
        }
    }
    if (state.showPinDialog) {
        var pin by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { viewModel.dismissPin() },
            title = { Text("PIN Enter Karo", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Task save karne ke liye 6-digit PIN.",
                        style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) pin = it },
                        label = { Text("6-Digit PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        isError = state.pinError != null
                    )
                    state.pinError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Text("${pin.length}/${PinManager.PIN_MAX}", style = MaterialTheme.typography.labelSmall)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { viewModel.dismissPin() }) { Text("Cancel") }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { viewModel.verifyAndSave(pin) { onSaved() } },
                            enabled = pin.length == PinManager.PIN_MAX && !state.isSaving
                        ) {
                            if (state.isSaving) CircularProgressIndicator(
                                Modifier.size(16.dp), strokeWidth = 2.dp)
                            else Text("Save")
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    state.saveError?.let {
        AlertDialog(onDismissRequest = { viewModel.dismissError() },
            title = { Text("Save Nahi Hua") }, text = { Text(it) },
            confirmButton = { Button(onClick = { viewModel.dismissError() }) { Text("OK") } })
    }

    // App Picker Bottom Sheet
    if (state.showAppPicker) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.closeAppPicker() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(Modifier.fillMaxWidth().fillMaxHeight(0.88f).padding(horizontal = 16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("App Choose Karo", fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (state.appListLoading) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    IconButton(onClick = { viewModel.rescanApps() }) {
                        Icon(Icons.Filled.Refresh, null, Modifier.size(20.dp))
                    }
                }
                if (state.lastScanInfo.isNotEmpty()) {
                    Text(state.lastScanInfo, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (state.scanProgress.isNotEmpty()) {
                    Text(state.scanProgress, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(8.dp))

                // Search field
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
                    shape = RoundedCornerShape(12.dp), singleLine = true
                )
                Spacer(Modifier.height(6.dp))

                // Messaging quick row
                val msgApps = viewModel.messagingApps()
                if (msgApps.isNotEmpty() && state.appQuery.isEmpty()) {
                    Text("Messaging Apps:", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        msgApps.take(4).forEach { app ->
                            AssistChip(onClick = { viewModel.selectApp(app) },
                                label = { Text(app.appName,
                                    style = MaterialTheme.typography.labelSmall) })
                        }
                    }
                    Divider(Modifier.padding(vertical = 6.dp))
                }

                // Error + manual fallback
                state.appListError?.let { err ->
                    Card(colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFF9800).copy(0.12f)),
                        modifier = Modifier.fillMaxWidth()) {
                        Text("$err", Modifier.padding(10.dp),
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(8.dp))
                }

                // Manual package input — always visible
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = manualPkg, onValueChange = { manualPkg = it },
                        label = { Text("Manual package naam") },
                        placeholder = { Text("com.whatsapp") },
                        modifier = Modifier.weight(1f), singleLine = true
                    )
                    Spacer(Modifier.width(6.dp))
                    IconButton(
                        onClick = {
                            if (manualPkg.isNotBlank()) {
                                viewModel.selectApp(InstalledAppInfo(
                                    packageName = manualPkg.trim(),
                                    appName = manualPkg.trim().substringAfterLast("."),
                                    versionName = "?", installDate = 0L,
                                    lastUpdated = 0L, canLaunch = true,
                                    category = "Manual", launchActivity = null
                                ))
                                manualPkg = ""
                            }
                        },
                        enabled = manualPkg.isNotBlank()
                    ) {
                        Icon(Icons.Filled.Check, null,
                            tint = if (manualPkg.isNotBlank()) Color(0xFF4CAF50)
                            else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(6.dp))

                val filtered = viewModel.filteredApps()
                LazyColumn(contentPadding = PaddingValues(bottom = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (filtered.isEmpty() && !state.appListLoading) {
                        item {
                            Box(Modifier.fillMaxWidth().height(80.dp),
                                contentAlignment = Alignment.Center) {
                                Text("Koi app nahi mili — upar search karo ya rescan karo",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    items(filtered, key = { it.packageName }) { app ->
                        Card(modifier = Modifier.fillMaxWidth().clickable { viewModel.selectApp(app) },
                            shape = RoundedCornerShape(10.dp)) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                val icon = when (app.category) {
                                    "Messaging" -> Icons.Filled.Chat
                                    "Social" -> Icons.Filled.People
                                    "Browser" -> Icons.Filled.Language
                                    "Media" -> Icons.Filled.PlayCircle
                                    "Email" -> Icons.Filled.Email
                                    "Finance" -> Icons.Filled.AccountBalance
                                    "Food" -> Icons.Filled.Restaurant
                                    "Shopping" -> Icons.Filled.ShoppingCart
                                    else -> Icons.Filled.Android
                                }
                                Icon(icon, null, tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp))
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(app.appName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text(app.packageName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Surface(color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(6.dp)) {
                                    Text(app.category,
                                        Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall)
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
                title = { Text(if (editTaskId != null) "Task Edit" else "Naya Task",
                    fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null) }
                }
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Column(Modifier.padding(16.dp, 10.dp, 16.dp, 24.dp)) {
                    Button(
                        onClick = { viewModel.requestSave(name, desc, trigger, time, date,
                            url, inputText, buttonText) },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        enabled = (name.isNotBlank() || selectedApp != null ||
                                url.isNotBlank() || inputText.isNotBlank()) && !state.isSaving,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp)); Text("Save ho raha hai...")
                        } else {
                            Icon(Icons.Filled.Save, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Task Save Karo", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                    if (name.isBlank() && selectedApp == null && url.isBlank() && inputText.isBlank())
                        Text("Naam, App, URL ya Text mein se kuch bhar do",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Task Ki Jankari", fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(value = name, onValueChange = { name = it },
                label = { Text("Task Naam *") }, placeholder = { Text("Jaise: Morning WhatsApp") },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                leadingIcon = { Icon(Icons.Filled.Label, null) }, singleLine = true)
            OutlinedTextField(value = desc, onValueChange = { desc = it },
                label = { Text("Description (optional)") }, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                leadingIcon = { Icon(Icons.Filled.Notes, null) }, minLines = 2)
            Divider()

            Text("App Choose Karo (Optional)", fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleSmall)
            if (selectedApp != null) {
                Card(colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF4CAF50).copy(0.12f)),
                    shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Android, null, tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(32.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(selectedApp.appName, fontWeight = FontWeight.Bold)
                            Text(selectedApp.packageName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(selectedApp.category,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { viewModel.clearApp() }) {
                            Icon(Icons.Filled.Close, null, tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                if (selectedApp.category == "Messaging" || selectedApp.category == "Social") {
                    Card(colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(10.dp)) {
                        Column(Modifier.padding(10.dp)) {
                            Text("${selectedApp.appName} ke liye steps:",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelSmall)
                            Spacer(Modifier.height(4.dp))
                            Text("1. Neeche text mein message likho\n" +
                                "2. Button mein 'Send' likho\n" +
                                "3. App launch hogi, contact manually select karo\n" +
                                "   (Android direct contact send allow nahi karta)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                OutlinedButton(onClick = { viewModel.openAppPicker() },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Filled.Apps, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Installed App Choose Karo")
                }
                if (state.appList.isNotEmpty()) {
                    Text("${state.appList.size} apps available — ${state.lastScanInfo}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Divider()

            Text("Kab Chalega?", fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf("MANUAL" to "Manual", "DAILY" to "Roz", "ONE_TIME" to "Ek Baar").forEach { (t, l) ->
                    FilterChip(selected = trigger == t, onClick = { trigger = t },
                        label = { Text(l, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.weight(1f))
                }
            }
            when (trigger) {
                "DAILY" -> OutlinedTextField(value = time, onValueChange = { time = it },
                    label = { Text("Time (HH:MM)") }, placeholder = { Text("08:30") },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                    leadingIcon = { Icon(Icons.Filled.Schedule, null) },
                    trailingIcon = {
                        IconButton(onClick = {
                            val c = Calendar.getInstance()
                            TimePickerDialog(context,
                                { _, h, m -> time = "%02d:%02d".format(h, m) },
                                c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show()
                        }) { Icon(Icons.Filled.AccessTime, null) }
                    }, singleLine = true)
                "ONE_TIME" -> Card(colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Exact Date aur Time chunno", fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleSmall)
                        OutlinedButton(onClick = {
                            val c = Calendar.getInstance()
                            DatePickerDialog(context, { _, y, m, d ->
                                date = "%02d/%02d/%04d".format(d, m + 1, y)
                            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH),
                                c.get(Calendar.DAY_OF_MONTH))
                                .also { it.datePicker.minDate = System.currentTimeMillis() }
                                .show()
                        }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                            Icon(Icons.Filled.CalendarMonth, null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (date.isBlank()) "Tarikh Chuno" else date)
                        }
                        OutlinedButton(onClick = {
                            val c = Calendar.getInstance()
                            TimePickerDialog(context,
                                { _, h, m -> time = "%02d:%02d".format(h, m) },
                                c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show()
                        }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                            Icon(Icons.Filled.AccessTime, null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (time.isBlank()) "Samay Chuno" else time)
                        }
                        if (date.isNotBlank() && time.isNotBlank())
                            Surface(color = Color(0xFF4CAF50).copy(0.15f),
                                shape = RoundedCornerShape(8.dp)) {
                                Text("Schedule: $date at $time", Modifier.padding(8.dp),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodySmall)
                            }
                    }
                }
            }
            Divider()

            Text("Kya Karega?", fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(value = url, onValueChange = { url = it },
                label = { Text("URL Open Karo (optional)") },
                placeholder = { Text("https://claude.ai") },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                leadingIcon = { Icon(Icons.Filled.Link, null) }, singleLine = true)
            OutlinedTextField(value = inputText, onValueChange = { inputText = it },
                label = { Text("Text Type Karo (optional)") },
                placeholder = { Text("Message ya koi text...") },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                leadingIcon = { Icon(Icons.Filled.Edit, null) }, minLines = 3)
            OutlinedTextField(value = buttonText, onValueChange = { buttonText = it },
                label = { Text("Button Press Karo (optional)") },
                placeholder = { Text("Send, Submit...") },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                leadingIcon = { Icon(Icons.Filled.TouchApp, null) }, singleLine = true)

            Text("⚡ Quick Actions", fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleSmall)
            val qBtns = listOf(
                "Search" to "🔍", "Play" to "▶️", "Next" to "⏭️",
                "Stop" to "⏹️", "Send" to "📤", "OK" to "✅",
                "Back" to "◀️", "Skip" to "⏩", "Like" to "👍",
                "Share" to "🔗", "Download" to "⬇️", "Subscribe" to "🔔"
            )
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                qBtns.forEach { (btn, emoji) ->
                    val sel = buttonText.split(",").map { it.trim() }.contains(btn)
                    FilterChip(
                        selected = sel,
                        onClick = {
                            buttonText = if (buttonText.isBlank()) btn
                            else {
                                val parts = buttonText.split(",").map { it.trim() }.toMutableList()
                                if (sel) { parts.remove(btn); parts.joinToString(", ") }
                                else { parts.add(btn); parts.joinToString(", ") }
                            }
                        },
                        label = { Text("$emoji $btn", style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = if (sel) {{ Icon(Icons.Filled.Check, null, Modifier.size(12.dp)) }} else null
                    )
                }
            }

            if (selectedApp != null || url.isNotBlank() || inputText.isNotBlank()) {
                Divider()
                Text("Preview", fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleSmall)
                Card(colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        var n = 1
                        selectedApp?.let { Text("$n. ${it.appName} launch karo", fontSize = 13.sp); n++ }
                        if (url.isNotBlank()) { Text("$n. URL: ${url.take(40)}", fontSize = 13.sp); n++ }
                        if (inputText.isNotBlank()) { Text("$n. Type: ${inputText.take(30)}", fontSize = 13.sp); n++ }
                        if (buttonText.isNotBlank()) Text("$n. Tap: '$buttonText'", fontSize = 13.sp)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
