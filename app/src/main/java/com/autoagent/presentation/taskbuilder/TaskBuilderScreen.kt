package com.autoagent.presentation.taskbuilder

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
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
        name: String, description: String, triggerType: String,
        triggerTime: String, url: String, inputText: String,
        onDone: () -> Unit
    ) {
        viewModelScope.launch {
            val steps = buildList {
                if (url.isNotBlank()) {
                    add(mapOf("id" to 1, "type" to "OPEN_URL", "targetUrl" to url,
                        "delayMs" to 1000, "retryCount" to 2, "description" to "URL open karo"))
                }
                if (inputText.isNotBlank()) {
                    add(mapOf("id" to 2, "type" to "ENTER_TEXT", "inputText" to inputText,
                        "delayMs" to 500, "retryCount" to 2, "description" to "Text type karo"))
                }
            }
            val task = TaskEntity(
                name = name.ifBlank { "My Task" },
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
    onBack: () -> Unit,
    viewModel: TaskBuilderViewModel = hiltViewModel()
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var triggerType by remember { mutableStateOf("MANUAL") }
    var triggerTime by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var inputText by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }

    val triggers = listOf("MANUAL", "DAILY", "ONE_TIME", "INTERVAL")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Naya Task Banao", fontWeight = FontWeight.Bold) },
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("📋 Task Details", fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Task ka naam *") },
                placeholder = { Text("jaise: Chrome kholo, ChatGPT message") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Filled.Label, null) },
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description (optional)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Divider()
            Text("⏰ Trigger", fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium)

            // Trigger type selector
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                triggers.forEach { t ->
                    FilterChip(
                        selected = triggerType == t,
                        onClick = { triggerType = t },
                        label = { Text(when(t) {
                            "MANUAL" -> "Manual"
                            "DAILY" -> "Roz"
                            "ONE_TIME" -> "Ek Baar"
                            "INTERVAL" -> "Interval"
                            else -> t
                        }, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            if (triggerType != "MANUAL") {
                OutlinedTextField(
                    value = triggerTime,
                    onValueChange = { triggerTime = it },
                    label = { Text("Time (HH:MM format, jaise 08:00)") },
                    placeholder = { Text("08:00") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Filled.Schedule, null) },
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Divider()
            Text("🤖 Actions", fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("URL open karo (optional)") },
                placeholder = { Text("https://google.com") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Filled.Link, null) },
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                label = { Text("Text type/paste karo (optional)") },
                placeholder = { Text("Koi message ya prompt...") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                leadingIcon = { Icon(Icons.Filled.Edit, null) },
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    viewModel.saveTask(name, description, triggerType,
                        triggerTime, url, inputText) {
                        saved = true
                        onBack()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = name.isNotBlank(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Filled.Save, null)
                Spacer(Modifier.width(8.dp))
                Text("Task Save Karo", fontWeight = FontWeight.Bold)
            }

            if (name.isBlank()) {
                Text("* Task ka naam zaroori hai",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}
