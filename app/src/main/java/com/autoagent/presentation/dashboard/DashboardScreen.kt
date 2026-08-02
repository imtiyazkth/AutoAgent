package com.autoagent.presentation.dashboard

import android.content.Intent
import android.provider.Settings
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.autoagent.data.db.ExecutionLogEntity
import com.autoagent.data.db.TaskEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onAddTask: () -> Unit,
    onEditTask: (Long) -> Unit,
    onViewLogs: () -> Unit,
    onDiagnostics: () -> Unit = {},
    onSetupAccessibility: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val tasks by viewModel.tasks.collectAsState()
    val recentLogs by viewModel.recentLogs.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()
    val currentStep by viewModel.currentStep.collectAsState()
    val context = LocalContext.current

    // KEY FIX: Collect navigation events here, NOT in ViewModel UiState
    LaunchedEffect(Unit) {
        viewModel.navEvent.collect { event ->
            try {
                when (event) {
                    is NavigationEvent.GoToAddTask -> onAddTask()
                    is NavigationEvent.GoToEditTask -> onEditTask(event.taskId)
                }
            } catch (e: Exception) { /* swallow */ }
        }
    }

    // Refresh accessibility status on each composition
    LaunchedEffect(Unit) { viewModel.refreshAccessibility() }

    // PIN Setup Dialog — shown before anything else
    if (uiState.showPinSetup) {
        PinSetupDialog(error = uiState.pinError, onSetPin = { viewModel.setupPin(it) })
        return
    }

    // PIN Verify Dialog
    if (uiState.showPinVerify) {
        PinVerifyDialog(
            error = uiState.pinError,
            onVerify = { viewModel.verifyPin(it) },
            onDismiss = { viewModel.dismissPinVerify() }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🤖", fontSize = 22.sp)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("AutoAgent", fontWeight = FontWeight.Bold)
                            Text("Personal Scheduler",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                actions = {
                    if (isRunning) {
                        IconButton(onClick = { viewModel.emergencyStop() }) {
                            Icon(Icons.Filled.Stop, null, tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    IconButton(onClick = onDiagnostics) {
                        Icon(Icons.Filled.BugReport, "Diagnostics")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.requestAddTask() },
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

            // Running indicator
            if (isRunning) {
                item {
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
                                Text(currentStep ?: "Executing...", style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = { viewModel.emergencyStop() }) {
                                Icon(Icons.Filled.Stop, null, tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            // Accessibility status
            item {
                if (!uiState.accessibilityEnabled) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFF9800).copy(0.12f)),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFFF9800)),
                        modifier = Modifier.clickable { onSetupAccessibility() }
                    ) {
                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Warning, null, tint = Color(0xFFFF9800))
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Accessibility OFF hai", fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFF9800))
                                Text("Tap → Setup guide dekhne ke liye",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            Icon(Icons.Filled.ChevronRight, null, tint = Color(0xFFFF9800))
                        }
                    }
                } else {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF4CAF50).copy(0.12f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth().padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CheckCircle, null,
                                tint = Color(0xFF4CAF50), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Accessibility ON — Automation ready! ✅",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Controls
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { viewModel.emergencyStop() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Filled.Stop, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp)); Text("Stop All")
                    }
                    if (uiState.allPaused) {
                        Button(onClick = { viewModel.resumeAll() }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp)); Text("Resume")
                        }
                    } else {
                        OutlinedButton(onClick = { viewModel.pauseAll() }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Pause, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp)); Text("Pause All")
                        }
                    }
                }
            }

            // Stats
            item {
                val successRate = if (recentLogs.isEmpty()) 100
                else recentLogs.count { it.status == "SUCCESS" } * 100 / recentLogs.size
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    StatBox("Tasks", "${tasks.size}", MaterialTheme.colorScheme.primary)
                    StatBox("Active", "${tasks.count { it.isEnabled }}", Color(0xFF4CAF50))
                    StatBox("Runs", "${recentLogs.size}", Color(0xFF2196F3))
                    StatBox("Success", "$successRate%", if (successRate >= 80) Color(0xFF4CAF50) else Color(0xFFFF9800))
                }
            }

            // Empty state
            if (tasks.isEmpty()) {
                item {
                    Column(modifier = Modifier.fillMaxWidth().padding(40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🤖", fontSize = 48.sp)
                        Spacer(Modifier.height(16.dp))
                        Text("Koi task nahi hai", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        Text("+ button se pehla automation banao",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { viewModel.requestAddTask() }) { Text("Pehla Task Banao") }
                    }
                }
            } else {
                item { Text("📋 Tasks (${tasks.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                items(tasks, key = { it.id }) { task ->
                    TaskCard(
                        task = task,
                        onToggle = { viewModel.requestToggle(task.id) },
                        onRun = { viewModel.requestRun(task.id) },
                        onEdit = { viewModel.requestEditTask(task.id) },
                        onDelete = { viewModel.requestDelete(task.id) }
                    )
                }
            }

            // Recent logs
            if (recentLogs.isNotEmpty()) {
                item { Text("📜 Recent Activity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                items(recentLogs.take(5)) { log -> LogItem(log) }
            }

            uiState.lastRunResult?.let { result ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor =
                            if (result.contains("✅")) Color(0xFF4CAF50).copy(0.15f)
                            else Color(0xFFF44336).copy(0.15f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
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

    // Error dialog
    uiState.error?.let { error ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissError() },
            title = { Text("⚠️ Error", fontWeight = FontWeight.Bold) },
            text = { Text(error) },
            confirmButton = { Button(onClick = { viewModel.dismissError() }) { Text("OK") } }
        )
    }
}

@Composable
fun PinSetupDialog(error: String?, onSetPin: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = {},
        title = { Text("🔐 10-Digit PIN Set karo", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("AutoAgent ke liye secure PIN banao.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = pin,
                    onValueChange = { if (it.length <= 10 && it.all(Char::isDigit)) pin = it },
                    label = { Text("10-Digit PIN") }, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = confirm,
                    onValueChange = { if (it.length <= 10 && it.all(Char::isDigit)) confirm = it },
                    label = { Text("Confirm PIN") }, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Text("${pin.length}/10", style = MaterialTheme.typography.labelSmall,
                    color = if (pin.length == 10) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Button(onClick = { if (pin == confirm && pin.length == 10) onSetPin(pin) },
                enabled = pin.length == 10 && pin == confirm) { Text("PIN Set Karo") }
        }
    )
}

@Composable
fun PinVerifyDialog(error: String?, onVerify: (String) -> Unit, onDismiss: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🔐 PIN Enter Karo", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = pin,
                    onValueChange = { if (it.length <= 10 && it.all(Char::isDigit)) pin = it },
                    label = { Text("10-Digit PIN") }, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { Button(onClick = { if (pin.length == 10) onVerify(pin) }, enabled = pin.length == 10) { Text("Verify") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun TaskCard(task: TaskEntity, onToggle: () -> Unit, onRun: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val statusColor = when (task.lastRunStatus) {
        "SUCCESS" -> Color(0xFF4CAF50); "FAILED" -> Color(0xFFF44336)
        "RUNNING" -> Color(0xFF2196F3); else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
        border = if (task.isEnabled) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(0.3f)) else null) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(task.name, fontWeight = FontWeight.Bold)
                    if (task.description.isNotBlank()) Text(task.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Chip(task.triggerType)
                        Chip("${task.totalRuns} runs")
                        task.lastRunStatus?.let { Chip(it, statusColor) }
                    }
                }
                Switch(checked = task.isEnabled, onCheckedChange = { onToggle() })
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRun, modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(8.dp, 6.dp)) {
                    Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp)); Text("Run Now", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(8.dp, 6.dp)) {
                    Icon(Icons.Filled.Edit, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp)); Text("Edit", style = MaterialTheme.typography.labelSmall)
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Delete, null, modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun Chip(label: String, color: Color = MaterialTheme.colorScheme.primary) {
    Surface(shape = RoundedCornerShape(20.dp), color = color.copy(0.12f)) {
        Text(label, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
fun StatBox(label: String, value: String, color: Color) {
    Surface(shape = RoundedCornerShape(12.dp), color = color.copy(0.1f)) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontWeight = FontWeight.Bold, color = color, fontSize = 20.sp)
            Text(label, style = MaterialTheme.typography.labelSmall, color = color)
        }
    }
}

@Composable
fun LogItem(log: ExecutionLogEntity) {
    val c = when (log.status) {
        "SUCCESS" -> Color(0xFF4CAF50); "FAILED" -> Color(0xFFF44336)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).background(c, CircleShape))
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(log.taskName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            log.failureReason?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error) }
        }
        Text(java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(log.startTime)),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
