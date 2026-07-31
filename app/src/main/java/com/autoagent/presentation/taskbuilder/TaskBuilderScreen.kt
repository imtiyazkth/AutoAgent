package com.autoagent.presentation.taskbuilder

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.autoagent.data.db.TaskEntity
import com.autoagent.data.repository.AgentRepository
import com.autoagent.domain.model.InstalledAppInfo
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TaskBuilderViewModel @Inject constructor(
    private val repository: AgentRepository
) : ViewModel() {
    fun saveTask(
        name: String,
        description: String,
        triggerType: String,
        triggerTime: String,
        selectedApp: InstalledAppInfo?,
        url: String,
        inputText: String,
        buttonText: String,
        onDone: () -> Unit
    ) {
        viewModelScope.launch {
            val steps = mutableListOf<Map<String, Any>>()

            if (selectedApp != null) {
                steps.add(mapOf(
                    "id" to 1,
                    "type" to "LAUNCH_APP",
                    "targetApp" to selectedApp.packageName,
                    "delayMs" to 1500,
                    "retryCount" to 2,
                    "description" to "${selectedApp.appName} open karo"
                ))
            }

            if (url.isNotBlank()) {
                steps.add(mapOf(
                    "id" to steps.size + 1,
                    "type" to "OPEN_URL",
                    "targetUrl" to url,
                    "delayMs" to 2000,
                    "retryCount" to 2,
                    "description" to "URL open karo: $url"
                ))
            }

            if (inputText.isNotBlank()) {
                steps.add(mapOf(
                    "id" to steps.size + 1,
                    "type" to "ENTER_TEXT",
                    "inputText" to inputText,
                    "delayMs" to 1000,
                    "retryCount" to 2,
                    "description" to "Text type karo"
                ))
            }

            if (buttonText.isNotBlank()) {
                steps.add(mapOf(
                    "id" to steps.size + 1,
                    "type" to "TAP_BUTTON",
                    "buttonText" to buttonText,
                    "delayMs" to 500,
                    "retryCount" to 3,
                    "description" to "'$buttonText' button tap karo"
                ))
            }

            val task = TaskEntity(
                name = name.ifBlank { selectedApp?.appName ?: "My Task" },
                description = description,
                triggerType = triggerType,
                triggerTime = triggerTime.ifBlank { null },
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
            )
            repository.saveTask(task)
            onDone()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskBuilderScreen(
    preSelectedApp: InstalledAppInfo? = null,
    onBack: () -> Unit,
    onPickApp: () -> Unit,
    viewModel: TaskBuilderViewModel = hiltViewModel()
) {
    var name by remember { mutableStateOf(preSelectedApp?.appName?.let { "$it Task" } ?: "") }
    var description by remember { mutableStateOf("") }
    var triggerType by remember { mutableStateOf("MANUAL") }
    var triggerTime by remember { mutableStateOf("") }
    var selectedApp by remember { mutableStateOf(preSelectedApp) }
    var url by remember { mutableStateOf("") }
    var inputText by remember { mutableStateOf("") }
    var buttonText by remember { mutableStateOf("") }

    LaunchedEffect(preSelectedApp) {
        if (preSelectedApp != null) selectedApp = preSelectedApp
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

            // STEP 1 - App Select
            Text("📱 Step 1: App Choose karo", fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                onClick = onPickApp
            ) {
                Row(modifier = Modifier.padding(16.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Icon(Icons.Filled.Apps, null,
                        tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(selectedApp?.appName ?: "App select karo",
                            fontWeight = FontWeight.Bold)
                        Text(selectedApp?.packageName ?: "Tap karke app choose karo",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Filled.ChevronRight, null)
                }
            }

            HorizontalDivider()

            // STEP 2 - Task Details
            Text("✏️ Step 2: Task Details", fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Task naam *") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                leadingIcon = { Icon(Icons.Filled.Label, null) }
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description (optional)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            HorizontalDivider()

            // STEP 3 - Trigger
            Text("⏰ Step 3: Kab chalega?", fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium)

            val triggers = listOf("MANUAL" to "Manual (Abhi)", "DAILY" to "Roz",
                "ONE_TIME" to "Ek Baar", "INTERVAL" to "Interval")

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                triggers.forEach { (type, label) ->
                    FilterChip(
                        selected = triggerType == type,
                        onClick = { triggerType = type },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) }
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

            HorizontalDivider()

            // STEP 4 - Actions
            Text("🤖 Step 4: Kya karna hai?", fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("URL open karo (optional)") },
                placeholder = { Text("https://claude.ai/chat") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                leadingIcon = { Icon(Icons.Filled.Link, null) }
            )

            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                label = { Text("Text type karo (optional)") },
                placeholder = { Text("Prompt ya message yahan likho...") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                shape = RoundedCornerShape(12.dp),
                leadingIcon = { Icon(Icons.Filled.Edit, null) }
            )

            OutlinedTextField(
                value = buttonText,
                onValueChange = { buttonText = it },
                label = { Text("Button tap karo (optional)") },
                placeholder = { Text("Send, Submit, Search...") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                leadingIcon = { Icon(Icons.Filled.TouchApp, null) }
            )

            Spacer(Modifier.height(8.dp))

            // PREVIEW
            if (selectedApp != null || url.isNotBlank() || inputText.isNotBlank()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("👁️ Preview — Ye steps honge:",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(6.dp))
                        var stepNum = 1
                        selectedApp?.let {
                            Text("$stepNum. 📱 ${it.appName} launch hogi")
                            stepNum++
                        }
                        if (url.isNotBlank()) {
                            Text("$stepNum. 🔗 URL open hoga: ${url.take(40)}")
                            stepNum++
                        }
                        if (inputText.isNotBlank()) {
                            Text("$stepNum. ⌨️ Text type hoga: '${inputText.take(30)}...'")
                            stepNum++
                        }
                        if (buttonText.isNotBlank()) {
                            Text("$stepNum. 👆 '$buttonText' button tap hoga")
                        }
                    }
                }
            }

            Button(
                onClick = {
                    viewModel.saveTask(name, description, triggerType, triggerTime,
                        selectedApp, url, inputText, buttonText) { onBack() }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = name.isNotBlank() || selectedApp != null,
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Filled.Save, null)
                Spacer(Modifier.width(8.dp))
                Text("Task Save Karo", fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}
