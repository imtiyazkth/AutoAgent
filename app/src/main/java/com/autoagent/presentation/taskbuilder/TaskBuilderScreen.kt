package com.autoagent.presentation.taskbuilder

import android.app.DatePickerDialog
import android.app.TimePickerDialog
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autoagent.data.db.AppCacheDao
import com.autoagent.data.db.AppCacheEntity
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
    val lastScanTime: String = ""
)

@HiltViewModel
class TaskBuilderViewModel @Inject constructor(
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
    var pendingDate = ""         // for ONE_TIME: "dd/MM/yyyy"
    var pendingDateTime = ""     // combined display
    var pendingApp: InstalledAppInfo? = null
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

    // Load from Room cache first — fast, no permissions needed
    private fun loadCachedApps() {
        viewModelScope.launch {
            try {
                val cached = withContext(Dispatchers.IO) {
                    appCacheDao.getAllAppsOnce()
                }
                val lastScan = withContext(Dispatchers.IO) {
                    appCacheDao.getLastScanTime()
                }
                if (cached.isNotEmpty()) {
                    val apps = cached.map { it.toInstalledAppInfo() }
                    val scanStr = lastScan?.let {
                        SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date(it))
                    } ?: ""
                    _state.value = _state.value.copy(
                        appList = apps,
                        lastScanTime = if (scanStr.isNotEmpty()) "Last scan: $scanStr" else ""
                    )
                    L.d("TaskBuilderVM", "Loaded ${apps.size} apps from cache")
                }
            } catch (e: Exception) {
                L.e("TaskBuilderVM", "loadCachedApps failed", e)
            }
        }
    }

    fun openAppPicker() {
        _state.update { it.copy(showAppPicker = true, appListError = null) }
        // Always try a fresh scan in background
        scanAppsInBackground()
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
        L.d("TaskBuilderVM", "App selected: ${app.packageName}")
    }

    fun clearApp() { pendingApp = null }

    private fun scanAppsInBackground() {
        viewModelScope.launch {
            _state.update { it.copy(appListLoading = true, appListError = null) }
            try {
                val apps = withContext(Dispatchers.IO) {
                    appScanner.scanInstalledApps()
                }
                if (apps.isNotEmpty()) {
                    // Save to Room cache
                    withContext(Dispatchers.IO) {
                        appCacheDao.upsertAll(apps.map { it.toEntity() })
                    }
                    val now = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())
                        .format(Date())
                    _state.value = _state.value.copy(
                        appList = apps,
                        appListLoading = false,
                        appListError = null,
                        lastScanTime = "Last scan: $now"
                    )
                    L.d("TaskBuilderVM", "Scanned ${apps.size} apps, saved to cache")
                } else {
                    _state.value = _state.value.copy(
                        appListLoading = false,
                        appListError = if (_state.value.appList.isEmpty())
                            "Koi app nahi mili — package naam manually type karo neeche"
                        else null  // keep showing cached if scan returns empty
                    )
                }
            } catch (e: Exception) {
                L.e("TaskBuilderVM", "scanAppsInBackground failed", e)
                _state.value = _state.value.copy(
                    appListLoading = false,
                    appListError = if (_state.value.appList.isEmpty())
                        "Scan fail: ${e.message}\nPackage naam manually type karo"
                    else null
                )
            }
        }
    }

    fun requestSave(
        name: String, desc: String, trigger: String,
        time: String, date: String,
        url: String, text: String, button: String
    ) {
        pendingName = name; pendingDesc = desc
        pendingTrigger = trigger; pendingTime = time; pendingDate = date
        pendingUrl = url; pendingText = text; pendingButton = button
        _state.update { it.copy(showPinDialog = true, pinError = null) }
    }

    fun dismissPin() {
        _state.update { it.copy(showPinDialog = false, pinError = null) }
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
                    _state.value = _state.value.copy(
                        pinError = "Galat PIN — dobara try karo"
                    )
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
                targetUrl = pendingUrl, delayMs = 2000L,
                description = "URL: $pendingUrl"))
            if (pendingText.isNotBlank()) steps.add(TaskStep(sid++, ActionType.ENTER_TEXT,
                inputText = pendingText, delayMs = 1000L,
                description = "Type: $pendingText"))
            if (pendingButton.isNotBlank()) steps.add(TaskStep(sid, ActionType.TAP_BUTTON,
                buttonText = pendingButton, delayMs = 500L,
                retryCount = 3, description = "'$pendingButton' tap karo"))
            if (steps.isEmpty()) steps.add(TaskStep(1, ActionType.CONFIRM_ACTION,
                delayMs = 500L, description = "Manual confirm"))

            // For ONE_TIME: combine date + time
            val triggerTimeValue = when {
                pendingTrigger == "ONE_TIME" && pendingDate.isNotBlank() ->
                    "${pendingDate} ${pendingTime}".trim()
                pendingTime.isNotBlank() -> pendingTime
                else -> null
            }

            repository.saveTask(TaskEntity(
                name = pendingName.ifBlank { pendingApp?.appName ?: "Naya Task" },
                description = pendingDesc,
                triggerType = pendingTrigger.ifBlank { "MANUAL" },
                triggerTime = triggerTimeValue,
                triggerDays = "[]", intervalMinutes = 0,
                stepsJson = Gson().toJson(steps),
                networkPolicy = "WIFI_PREFERRED",
                mobileDataAllowed = false, isEnabled = true,
                requiresConfirmation = false, priority = 1,
                createdAt = System.currentTimeMillis(),
                lastRunAt = null, lastRunStatus = null,
                totalRuns = 0, successRuns = 0
            ))
            L.d("TaskBuilderVM", "Task saved: ${steps.size} steps")
            _state.update { it.copy(isSaving = false, savedOk = true) }
            onSuccess()
        } catch (e: Exception) {
            L.e("TaskBuilderVM", "saveTask failed", e)
            _state.value = _state.value.copy(
                isSaving = false,
                saveError = "Save nahi hua: ${e.message}"
            )
        }
    }

    fun dismissError() { _state.update { it.copy(saveError = null) } }

    fun filteredApps(): List<InstalledAppInfo> {
        val q = _state.value.appQuery.lowercase().trim()
        return if (q.isEmpty()) _state.value.appList
        else _state.value.appList.filter {
            it.appName.lowercase().contains(q) ||
            it.packageName.lowercase().contains(q)
        }
    }

    // Messaging app connectors
    fun getMessagingApps(): List<InstalledAppInfo> =
        _state.value.appList.filter { it.category == "Messaging" || it.category == "Social" }
}

fun InstalledAppInfo.toEntity() = AppCacheEntity(
    packageName = packageName, appName = appName,
    versionName = versionName, category = category,
    canLaunch = canLaunch, installDate = installDate,
    lastUpdated = lastUpdated, launchActivity = launchActivity,
    scannedAt = System.currentTimeMillis()
)

fun AppCacheEntity.toInstalledAppInfo() = InstalledAppInfo(
    packageName = packageName, appName = appName,
    versionName = versionName, category = category,
    canLaunch = canLaunch, installDate = installDate,
    lastUpdated = lastUpdated, launchActivity = launchActivity
)

// ─────────────────────────────────────────────────────────
// MAIN COMPOSABLE
// ─────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskBuilderScreen(
    editTaskId: Long? = null,
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
    var date by remember { mutableStateOf("") }   // dd/MM/yyyy for ONE_TIME
    var url by remember { mutableStateOf("") }
    var inputText by remember { mutableStateOf("") }
    var buttonText by remember { mutableStateOf("") }
    var manualPkg by remember { mutableStateOf("") }
    var showGuide by remember { mutableStateOf(false) }

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

    // ── User Guide Dialog ────────────────────────────────
    if (showGuide) {
        AlertDialog(
            onDismissRequest = { showGuide = false },
            title = { Text("📖 App Guide", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GuideItem("1️⃣ Task Naam", "Task ka naam do — jaise 'WhatsApp morning message'")
                    GuideItem("2️⃣ App Choose Karo", "Wo app chunni jahan kaam karna hai")
                    GuideItem("3️⃣ Kab Chalega", "Manual = sirf jab tum chaho\nRoz = roz ek time pe\nEk Baar = exact date+time pe")
                    GuideItem("4️⃣ Kya Karega", "URL, text type karna, ya button press — sab optional hain")
                    GuideItem("5️⃣ Save Karo", "Save pe tap karo → PIN dalo → task save ho jata hai")
                    GuideItem("⚡ Accessibility", "Settings → Accessibility → AutoAgent → ON karo\nTabhi automation chalegi")
                    GuideItem("🔴 Service null?", "Accessibility ON karne ke baad app band karo aur dobara kholo")
                    GuideItem("📱 WhatsApp Message", "WhatsApp choose karo → Text likho → 'Send' button → schedule karo")
                }
            },
            confirmButton = {
                Button(onClick = { showGuide = false }) { Text("Samajh Gaya ✅") }
            }
        )
    }

    // ── PIN Dialog ───────────────────────────────────────
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
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
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
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { viewModel.dismissPin() }) { Text("Cancel") }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { viewModel.verifyAndSave(pin) { onSaved() } },
                            enabled = pin.length == 10 && !state.isSaving
                        ) {
                            if (state.isSaving)
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            else Text("Save ✅")
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    // ── Save Error ───────────────────────────────────────
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

    // ── App Picker Bottom Sheet ──────────────────────────
    if (state.showAppPicker) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.closeAppPicker() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.88f)
                    .padding(horizontal = 16.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("📱 App Choose Karo",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f))
                    if (state.lastScanTime.isNotEmpty()) {
                        Text(state.lastScanTime,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (state.appListLoading) {
                        Spacer(Modifier.width(8.dp))
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    }
                }
                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = state.appQuery,
                    onValueChange = { viewModel.setAppQuery(it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("App ya package naam dhundo...") },
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
                Spacer(Modifier.height(6.dp))

                // Quick category filter for messaging apps
                val messagingApps = viewModel.getMessagingApps()
                if (messagingApps.isNotEmpty() && state.appQuery.isEmpty()) {
                    Text("💬 Messaging Apps",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 4.dp))
                    messagingApps.take(5).forEach { app ->
                        AssistChip(
                            onClick = { viewModel.selectApp(app) },
                            label = { Text(app.appName, style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = { Icon(Icons.Filled.Chat, null, Modifier.size(14.dp)) },
                            modifier = Modifier.padding(end = 6.dp, bottom = 4.dp)
                        )
                    }
                    Divider(Modifier.padding(vertical = 4.dp))
                }

                // Error + manual fallback — never closes the sheet
                state.appListError?.let { err ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFFF9800).copy(0.15f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Text("⚠️ $err",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                // Manual package fallback — always visible at bottom
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = manualPkg,
                        onValueChange = { manualPkg = it },
                        label = { Text("Manual package naam") },
                        placeholder = { Text("com.whatsapp") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Spacer(Modifier.width(8.dp))
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
                Spacer(Modifier.height(8.dp))

                val filtered = viewModel.filteredApps()
                if (filtered.isEmpty() && !state.appListLoading) {
                    Box(Modifier.fillMaxWidth().height(80.dp),
                        contentAlignment = Alignment.Center) {
                        Text("Koi app nahi mili — upar search karo",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 40.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filtered, key = { it.packageName }) { app ->
                            Card(
                                modifier = Modifier.fillMaxWidth()
                                    .clickable { viewModel.selectApp(app) },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    val catIcon = when (app.category) {
                                        "Messaging" -> Icons.Filled.Chat
                                        "Social"    -> Icons.Filled.People
                                        "Browser"   -> Icons.Filled.Language
                                        "Media"     -> Icons.Filled.PlayCircle
                                        "Email"     -> Icons.Filled.Email
                                        else        -> Icons.Filled.Android
                                    }
                                    Icon(catIcon, null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(28.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(app.appName, fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp)
                                        Text(app.packageName,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
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
    }

    // ── Main Screen ──────────────────────────────────────
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
                },
                actions = {
                    IconButton(onClick = { showGuide = true }) {
                        Icon(Icons.Filled.Help, null)
                    }
                }
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Column(Modifier.padding(16.dp, 10.dp, 16.dp, 24.dp)) {
                    Button(
                        onClick = {
                            viewModel.requestSave(
                                name, desc, trigger, time, date,
                                url, inputText, buttonText
                            )
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        enabled = (name.isNotBlank() || selectedApp != null ||
                                url.isNotBlank() || inputText.isNotBlank()) &&
                                !state.isSaving,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Save ho raha hai...")
                        } else {
                            Icon(Icons.Filled.Save, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Task Save Karo 🔐",
                                fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                    if (name.isBlank() && selectedApp == null &&
                        url.isBlank() && inputText.isBlank()
                    ) {
                        Text(
                            "* Naam, App, URL ya Text mein se kuch bhar do",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
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
            // ── Section 1: Task Info ──────────────────────
            SectionLabel("📝 Task Ki Jankari")

            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Task Naam *") },
                placeholder = { Text("Jaise: Morning WhatsApp message") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                leadingIcon = { Icon(Icons.Filled.Label, null) },
                singleLine = true
            )
            OutlinedTextField(
                value = desc, onValueChange = { desc = it },
                label = { Text("Description (optional)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                leadingIcon = { Icon(Icons.Filled.Notes, null) },
                minLines = 2
            )
            Divider()

            // ── Section 2: App ────────────────────────────
            SectionLabel("📱 App Choose Karo (Optional)")

            if (selectedApp != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF4CAF50).copy(0.12f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Android, null,
                            tint = Color(0xFF4CAF50), modifier = Modifier.size(32.dp))
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
                            Icon(Icons.Filled.Close, null,
                                tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                // If messaging app: show contact + message hint
                if (selectedApp.category == "Messaging" ||
                    selectedApp.category == "Social") {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Text("💡 ${selectedApp.appName} ke liye tip:",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "1. Neeche 'Text Type Karo' mein apna message likho\n" +
                                "2. 'Button Press Karo' mein 'Send' ya 'Bhejo' likho\n" +
                                "3. App khulne ke baad contact manually select karna hoga\n" +
                                "   (Android limitation — direct contact send allowed nahi)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                OutlinedButton(
                    onClick = { viewModel.openAppPicker() },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.Apps, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Installed App Choose Karo")
                }
            }
            Divider()

            // ── Section 3: Schedule ───────────────────────
            SectionLabel("⏰ Kab Chalega?")

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf(
                    "MANUAL"   to "Manual",
                    "DAILY"    to "Roz",
                    "ONE_TIME" to "Ek Baar"
                ).forEach { (t, l) ->
                    FilterChip(
                        selected = trigger == t,
                        onClick = { trigger = t },
                        label = {
                            Text(l, style = MaterialTheme.typography.labelSmall)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            when (trigger) {
                "DAILY" -> {
                    // Simple time picker for DAILY
                    OutlinedTextField(
                        value = time,
                        onValueChange = { time = it },
                        label = { Text("Time (HH:MM)") },
                        placeholder = { Text("08:30") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = { Icon(Icons.Filled.Schedule, null) },
                        trailingIcon = {
                            IconButton(onClick = {
                                val cal = Calendar.getInstance()
                                TimePickerDialog(context,
                                    { _, h, m -> time = "%02d:%02d".format(h, m) },
                                    cal.get(Calendar.HOUR_OF_DAY),
                                    cal.get(Calendar.MINUTE), true
                                ).show()
                            }) { Icon(Icons.Filled.AccessTime, null) }
                        },
                        singleLine = true
                    )
                }
                "ONE_TIME" -> {
                    // Full date + time picker
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("📅 Exact Date aur Time chunno",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.titleSmall)

                            // Date picker button
                            OutlinedButton(
                                onClick = {
                                    val cal = Calendar.getInstance()
                                    DatePickerDialog(context,
                                        { _, y, m, d ->
                                            date = "%02d/%02d/%04d".format(d, m + 1, y)
                                        },
                                        cal.get(Calendar.YEAR),
                                        cal.get(Calendar.MONTH),
                                        cal.get(Calendar.DAY_OF_MONTH)
                                    ).also { it.datePicker.minDate = System.currentTimeMillis() }
                                        .show()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Filled.CalendarMonth, null)
                                Spacer(Modifier.width(8.dp))
                                Text(if (date.isBlank()) "📅 Tarikh Chuno" else "📅 $date")
                            }

                            // Time picker button
                            OutlinedButton(
                                onClick = {
                                    val cal = Calendar.getInstance()
                                    TimePickerDialog(context,
                                        { _, h, m -> time = "%02d:%02d".format(h, m) },
                                        cal.get(Calendar.HOUR_OF_DAY),
                                        cal.get(Calendar.MINUTE), true
                                    ).show()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Filled.AccessTime, null)
                                Spacer(Modifier.width(8.dp))
                                Text(if (time.isBlank()) "🕐 Samay Chuno" else "🕐 $time")
                            }

                            if (date.isNotBlank() && time.isNotBlank()) {
                                Surface(
                                    color = Color(0xFF4CAF50).copy(0.15f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("✅ Schedule: $date at $time",
                                        Modifier.padding(8.dp),
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
            Divider()

            // ── Section 4: Actions ────────────────────────
            SectionLabel("🤖 Kya Karega?")

            OutlinedTextField(
                value = url, onValueChange = { url = it },
                label = { Text("URL Open Karo (optional)") },
                placeholder = { Text("https://claude.ai") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                leadingIcon = { Icon(Icons.Filled.Link, null) },
                singleLine = true
            )
            OutlinedTextField(
                value = inputText, onValueChange = { inputText = it },
                label = { Text("Text Type Karo (optional)") },
                placeholder = { Text("Message ya koi text...") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                leadingIcon = { Icon(Icons.Filled.Edit, null) },
                minLines = 3
            )
            OutlinedTextField(
                value = buttonText, onValueChange = { buttonText = it },
                label = { Text("Button Press Karo (optional)") },
                placeholder = { Text("Send, Submit, Bhejo...") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                leadingIcon = { Icon(Icons.Filled.TouchApp, null) },
                singleLine = true
            )

            // ── Section 5: Preview ────────────────────────
            val hasAny = selectedApp != null || url.isNotBlank() ||
                    inputText.isNotBlank() || buttonText.isNotBlank()
            if (hasAny) {
                Divider()
                SectionLabel("👁️ Preview")
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        var n = 1
                        selectedApp?.let {
                            Text("$n. 📱 ${it.appName} launch karo", fontSize = 13.sp); n++
                        }
                        if (url.isNotBlank()) {
                            Text("$n. 🔗 ${url.take(45)}", fontSize = 13.sp); n++
                        }
                        if (inputText.isNotBlank()) {
                            Text("$n. ⌨️ \"${inputText.take(35)}...\"", fontSize = 13.sp); n++
                        }
                        if (buttonText.isNotBlank()) {
                            Text("$n. 👆 '$buttonText' tap karo", fontSize = 13.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.titleSmall)
}

@Composable
private fun GuideItem(title: String, body: String) {
    Column {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(body, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
