package com.autoagent.presentation.dashboard

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.autoagent.data.db.ExecutionLogEntity
import com.autoagent.data.db.TaskEntity
import com.autoagent.domain.model.DryRunPreview
import com.autoagent.domain.model.RunStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onAddTask: () -> Unit,
    onEditTask: (Long) -> Unit,
    onViewLogs: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val tasks by viewModel.tasks.collectAsState()
    val recentLogs by viewModel.recentLogs.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()
    val currentStep by viewModel.currentStep.collectAsState()

    // PIN Setup Dialog
    if (uiState.showPinSetup) {
        PinSetupDialog(
            error = uiState.pinError,
            onSetPin = { pin -> viewModel.setupPin(pin) }
        )
        return
    }

    // PIN Verify Dialog
    if (uiState.showPinVerify) {
        PinVerifyDialog(
            error = uiState.pinError,
            onVerify = { pin ->
                viewModel.verifyPin(pin) {
                    uiState.pendingAction?.invoke()
                }
            },
            onDismiss = { viewModel.dismissPinError() }
        )
    }

    // Dry Run Preview Dialog
    uiState.dryRunPreview?.let { preview ->
        DryRunDialog(preview = preview, onDismiss = { viewModel.dismissPreview() })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🤖", fontSize = 24.sp)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("AutoAgent", fontWeight = FontWeight.Bold)
                            Text("Personal Scheduler", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                actions = {
                    // EMERGENCY STOP
                    if (isRunning) {
                        IconButton(onClick = { viewModel.emergencyStop() }) {
                            Icon(Icons.Filled.Stop, null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(28.dp))
                        }
                    }
                    IconButton(onClick = onViewLogs) {
                        Icon(Icons.Filled.History, "Logs")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    viewModel.requestPinVerification { onAddTask() }
                },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Naya Task") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // RUNNING STATUS
            if (isRunning) {
                item {
                    RunningStatusCard(
                        currentStep = currentStep,
                        onStop = { viewModel.emergencyStop() }
                    )
                }
            }

            // ACCESSIBILITY WARNING
            if (!uiState.accessibilityEnabled) {
                item { AccessibilityWarningCard() }
            }

            // CONTROL BUTTONS
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.emergencyStop() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Filled.Stop, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Emergency Stop")
                    }
                    if (uiState.allPaused) {
                        Button(
                            onClick = { viewModel.resumeAll() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Resume All")
                        }
                    } else {
                        OutlinedButton(
                            onClick = { viewModel.pauseAll() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Pause, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Pause All")
                        }
                    }
                }
            }

            // STATS ROW
            item {
                StatsRow(
                    totalTasks = tasks.size,
                    enabledTasks = tasks.count { it.isEnabled },
                    totalRuns = recentLogs.size,
                    successRate = if (recentLogs.isEmpty()) 100
                    else (recentLogs.count { it.status == "SUCCESS" } * 100 / recentLogs.size)
                )
            }

            // TASKS
            if (tasks.isEmpty()) {
                item { EmptyState(onAdd = { viewModel.requestPinVerification { onAddTask() } }) }
            } else {
                item {
                    Text("📋 Tasks (${tasks.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold)
                }
                items(tasks, key = { it.id }) { task ->
                    TaskCard(
                        task = task,
                        onToggle = { viewModel.toggleTask(task.id, !task.isEnabled) },
                        onRun = { viewModel.runTaskNow(task.id) },
                        onEdit = { viewModel.requestPinVerification { onEditTask(task.id) } },
                        onDelete = { viewModel.deleteTask(task.id) },
                        onPreview = {
                            /* Preview dry run */ viewModel.dismissPreview()
                        }
                    )
                }
            }

            // RECENT LOGS
            if (recentLogs.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("📜 Recent Activity",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f))
                        TextButton(onClick = onViewLogs) { Text("Sab dekho") }
                    }
                }
                items(recentLogs.take(5)) { log ->
                    LogItem(log = log)
                }
            }

            // Result snackbar
            uiState.lastRunResult?.let { result ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (result.contains("✅"))
                                Color(0xFF4CAF50).copy(0.15f) else Color(0xFFF44336).copy(0.15f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(result, modifier = Modifier.weight(1f))
                            IconButton(onClick = { viewModel.dismissResult() }) {
                                Icon(Icons.Filled.Close, null, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

// =============================================
// PIN SETUP DIALOG
// =============================================
@Composable
fun PinSetupDialog(error: String?, onSetPin: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = {},
        title = { Text("🔐 10-Digit PIN Setup karo", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("AutoAgent ke liye ek 10-digit PIN set karo. Har task create/edit/delete karne ke liye ye PIN chahiye hoga.",
                    style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 10 && it.all { c -> c.isDigit() }) pin = it },
                    label = { Text("10-Digit PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { if (it.length <= 10 && it.all { c -> c.isDigit() }) confirmPin = it },
                    label = { Text("PIN confirm karo") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
                Text("PIN ${pin.length}/10 digits",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (pin.length == 10) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (pin == confirmPin) onSetPin(pin)
                },
                enabled = pin.length == 10 && pin == confirmPin
            ) { Text("PIN Set Karo") }
        }
    )
}

// =============================================
// PIN VERIFY DIALOG
// =============================================
@Composable
fun PinVerifyDialog(error: String?, onVerify: (String) -> Unit, onDismiss: () -> Unit) {
    var pin by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🔐 PIN Enter Karo", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 10 && it.all { c -> c.isDigit() }) pin = it },
                    label = { Text("10-Digit PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = { onVerify(pin) }, enabled = pin.length == 10) {
                Text("Verify Karo")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// =============================================
// DRY RUN PREVIEW DIALOG
// =============================================
@Composable
fun DryRunDialog(preview: DryRunPreview, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("👁️ Task Preview: ${preview.taskName}", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    Text("Ye ${preview.stepDescriptions.size} steps honge (~${preview.estimatedDurationSeconds}s):",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(preview.stepDescriptions.size) { i ->
                    Row {
                        Text("${i + 1}. ", fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodySmall)
                        Text(preview.stepDescriptions[i],
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (preview.warnings.isNotEmpty()) {
                    item { Spacer(Modifier.height(8.dp)) }
                    items(preview.warnings) { warning ->
                        Text(warning, style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFF9800))
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Samajh gaya") }
        }
    )
}

// =============================================
// TASK CARD
// =============================================
@Composable
fun TaskCard(
    task: TaskEntity,
    onToggle: () -> Unit,
    onRun: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPreview: () -> Unit
) {
    val statusColor = when (task.lastRunStatus) {
        "SUCCESS" -> Color(0xFF4CAF50)
        "FAILED" -> Color(0xFFF44336)
        "RUNNING" -> Color(0xFF2196F3)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (task.isEnabled)
                MaterialTheme.colorScheme.surface else
                MaterialTheme.colorScheme.surfaceVariant.copy(0.5f)
        ),
        border = if (task.isEnabled)
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(0.3f)) else null
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(task.name, fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge)
                    Text(task.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Chip(task.triggerType)
                        Chip("${task.totalRuns} runs")
                        if (task.lastRunStatus != null) {
                            Chip(task.lastRunStatus ?: "", statusColor)
                        }
                    }
                }
                Switch(checked = task.isEnabled, onCheckedChange = { onToggle() })
            }

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onPreview,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Filled.Visibility, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Preview", style = MaterialTheme.typography.labelSmall)
                }
                Button(
                    onClick = onRun,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Run Now", style = MaterialTheme.typography.labelSmall)
                }
                IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Edit, null, modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Delete, null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun Chip(label: String, color: Color = MaterialTheme.colorScheme.primary) {
    Surface(shape = RoundedCornerShape(20.dp), color = color.copy(0.12f)) {
        Text(label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color)
    }
}

@Composable
fun RunningStatusCard(currentStep: String?, onStop: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2196F3).copy(0.15f)),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color(0xFF2196F3))
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Task chal raha hai...", fontWeight = FontWeight.Bold)
                Text(currentStep ?: "Executing...",
                    style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onStop) {
                Icon(Icons.Filled.Stop, null, tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun AccessibilityWarningCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFF9800).copy(0.15f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Icon(Icons.Filled.Warning, null, tint = Color(0xFFFF9800))
            Spacer(Modifier.width(8.dp))
            Text("Automation ke liye Accessibility Service ON karo:\nSettings → Accessibility → AutoAgent ON karo",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun StatsRow(totalTasks: Int, enabledTasks: Int, totalRuns: Int, successRate: Int) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        StatBox("Tasks", "$totalTasks", MaterialTheme.colorScheme.primary)
        StatBox("Active", "$enabledTasks", Color(0xFF4CAF50))
        StatBox("Total Runs", "$totalRuns", Color(0xFF2196F3))
        StatBox("Success", "$successRate%",
            if (successRate >= 80) Color(0xFF4CAF50) else Color(0xFFFF9800))
    }
}

@Composable
fun StatBox(label: String, value: String, color: Color) {
    Surface(shape = RoundedCornerShape(12.dp), color = color.copy(0.1f)) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontWeight = FontWeight.Bold, color = color, fontSize = 20.sp)
            Text(label, style = MaterialTheme.typography.labelSmall, color = color)
        }
    }
}

@Composable
fun LogItem(log: ExecutionLogEntity) {
    val statusColor = when (log.status) {
        "SUCCESS" -> Color(0xFF4CAF50)
        "FAILED" -> Color(0xFFF44336)
        "RUNNING" -> Color(0xFF2196F3)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(8.dp).background(statusColor, CircleShape))
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(log.taskName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            log.failureReason?.let {
                Text(it, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error)
            }
        }
        Text(
            java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date(log.startTime)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun EmptyState(onAdd: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🤖", fontSize = 48.sp)
        Spacer(Modifier.height(16.dp))
        Text("Koi task nahi hai abhi", style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text("+ button se pehla automation task banao",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onAdd) { Text("Pehla Task Banao") }
    }
}
