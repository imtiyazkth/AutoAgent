package com.autoagent.presentation.diagnostics

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autoagent.data.repository.AgentRepository
import com.autoagent.presentation.setup.isAccessibilityEnabled
import com.autoagent.service.accessibility.AutoAgentAccessibilityService
import com.autoagent.util.PinManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.text.SimpleDateFormat
import java.util.*

enum class DiagStatus { OK, WARNING, ERROR }

data class DiagItem(
    val title: String,
    val status: DiagStatus,
    val detail: String,
    val fixLabel: String = "",
    val settingsAction: String = "",
    val settingsUri: String = ""
)

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pinManager: PinManager,
    private val repository: AgentRepository
) : ViewModel() {

    private val _items = MutableStateFlow<List<DiagItem>>(emptyList())
    val items: StateFlow<List<DiagItem>> = _items

    private val _debugInfo = MutableStateFlow<Map<String, String>>(emptyMap())
    val debugInfo: StateFlow<Map<String, String>> = _debugInfo

    init { runDiagnostics() }

    fun runDiagnostics() {
        viewModelScope.launch {
            val results = mutableListOf<DiagItem>()

            // 1. Accessibility Service registered
            val accessEnabled = isAccessibilityEnabled(context)
            results.add(DiagItem(
                title = "Accessibility Service",
                status = if (accessEnabled) DiagStatus.OK else DiagStatus.ERROR,
                detail = if (accessEnabled)
                    "AutoAgent Automation enabled hai ✅"
                else
                    "Not enabled — Settings mein ON karo",
                fixLabel = "Accessibility Settings Kholo",
                settingsAction = Settings.ACTION_ACCESSIBILITY_SETTINGS
            ))

            // 2. Service instance running
            val serviceRunning = AutoAgentAccessibilityService.isAvailable()
            val lastConnected = AutoAgentAccessibilityService.lastConnectedTime.value
            results.add(DiagItem(
                title = "Service Instance",
                status = when {
                    serviceRunning -> DiagStatus.OK
                    accessEnabled -> DiagStatus.WARNING
                    else -> DiagStatus.ERROR
                },
                detail = when {
                    serviceRunning -> "Service running ✅ (Connected: ${lastConnected?.let { formatTime(it) } ?: "N/A"})"
                    accessEnabled -> "Enabled but instance null — app restart karo"
                    else -> "Service nahi chal raha — Accessibility enable karo"
                },
                fixLabel = if (!serviceRunning) "Accessibility Settings" else "",
                settingsAction = Settings.ACTION_ACCESSIBILITY_SETTINGS
            ))

            // 3. Last error
            val lastError = AutoAgentAccessibilityService.lastError.value
            if (lastError != null) {
                results.add(DiagItem(
                    title = "Last Service Error",
                    status = DiagStatus.WARNING,
                    detail = lastError
                ))
            }

            // 4. PIN Setup
            val pinSetup = try { pinManager.isPinSetup() } catch (e: Exception) { false }
            results.add(DiagItem(
                title = "PIN Setup",
                status = if (pinSetup) DiagStatus.OK else DiagStatus.WARNING,
                detail = if (pinSetup) "10-digit PIN set hai ✅" else "PIN set nahi — app kholo aur set karo"
            ))

            // 5. Battery optimization
            val batteryOk = try {
                (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
                    .isIgnoringBatteryOptimizations(context.packageName)
            } catch (e: Exception) { false }
            results.add(DiagItem(
                title = "Battery Optimization",
                status = if (batteryOk) DiagStatus.OK else DiagStatus.WARNING,
                detail = if (batteryOk) "Background tasks allowed ✅" else "Background tasks block ho sakte hain",
                fixLabel = "Battery Settings",
                settingsAction = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                settingsUri = "package:${context.packageName}"
            ))

            // 6. Overlay permission
            val overlayOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                Settings.canDrawOverlays(context) else true
            results.add(DiagItem(
                title = "Overlay Permission",
                status = if (overlayOk) DiagStatus.OK else DiagStatus.WARNING,
                detail = if (overlayOk) "Overlay allowed ✅" else "Not granted (optional)",
                fixLabel = if (!overlayOk) "Overlay Settings" else "",
                settingsAction = Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                settingsUri = "package:${context.packageName}"
            ))

            // 7. Package details
            results.add(DiagItem(
                title = "Package Name",
                status = DiagStatus.OK,
                detail = context.packageName
            ))

            results.add(DiagItem(
                title = "Service Class",
                status = DiagStatus.OK,
                detail = "com.autoagent.service.accessibility.AutoAgentAccessibilityService"
            ))

            // 8. Android Version
            results.add(DiagItem(
                title = "Android Version",
                status = if (Build.VERSION.SDK_INT >= 26) DiagStatus.OK else DiagStatus.ERROR,
                detail = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) — ${Build.MANUFACTURER} ${Build.MODEL}"
            ))

            // 9. Task count
            val taskCount = try {
                var count = 0
                repository.getAllTasks().collect { count = it.size }
                count
            } catch (e: Exception) { -1 }
            results.add(DiagItem(
                title = "Room Database",
                status = if (taskCount >= 0) DiagStatus.OK else DiagStatus.ERROR,
                detail = if (taskCount >= 0) "Database OK — $taskCount tasks stored" else "Database error"
            ))

            _items.value = results

            // Debug info
            _debugInfo.value = mapOf(
                "App Package" to context.packageName,
                "Android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                "Device" to "${Build.MANUFACTURER} ${Build.MODEL}",
                "Accessibility" to if (accessEnabled) "ENABLED" else "DISABLED",
                "Service Running" to if (serviceRunning) "YES" else "NO",
                "PIN Set" to if (pinSetup) "YES" else "NO",
                "Battery OK" to if (batteryOk) "YES" else "NO",
                "ADB Component" to "${context.packageName}/com.autoagent.service.accessibility.AutoAgentAccessibilityService"
            )
        }
    }

    private fun formatTime(ms: Long): String =
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(ms))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    onBack: () -> Unit,
    viewModel: DiagnosticsViewModel = hiltViewModel()
) {
    val items by viewModel.items.collectAsState()
    val debugInfo by viewModel.debugInfo.collectAsState()
    val context = LocalContext.current
    var showDebug by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🔍 Diagnostics", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null) }
                },
                actions = {
                    IconButton(onClick = { showDebug = !showDebug }) {
                        Icon(Icons.Filled.BugReport, null)
                    }
                    IconButton(onClick = { viewModel.runDiagnostics() }) {
                        Icon(Icons.Filled.Refresh, null)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // SUMMARY
            item {
                val errors = items.count { it.status == DiagStatus.ERROR }
                val warnings = items.count { it.status == DiagStatus.WARNING }
                Card(
                    colors = CardDefaults.cardColors(containerColor = when {
                        errors > 0 -> Color(0xFFF44336).copy(0.15f)
                        warnings > 0 -> Color(0xFFFF9800).copy(0.15f)
                        else -> Color(0xFF4CAF50).copy(0.15f)
                    }),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(when {
                            errors > 0 -> "❌ $errors error hain — fix zaroori hai"
                            warnings > 0 -> "⚠️ $warnings warnings hain"
                            else -> "✅ Sab theek hai!"
                        }, fontWeight = FontWeight.Bold)
                        Text("${items.size} checks complete", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // DIAG ITEMS
            items(items) { item ->
                DiagCard(item = item, onFix = { action, uri ->
                    try {
                        val intent = if (uri.isNotEmpty())
                            Intent(action, Uri.parse(uri))
                        else Intent(action)
                        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (e: Exception) {
                        try { context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        catch (ex: Exception) {}
                    }
                })
            }

            // DEBUG INFO
            if (showDebug && debugInfo.isNotEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(0.85f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("🐛 Debug Info", color = Color.Green, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            debugInfo.forEach { (key, value) ->
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    Text("$key: ", color = Color.Gray,
                                        fontFamily = FontFamily.Monospace,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.width(120.dp))
                                    Text(value, color = Color.Green,
                                        fontFamily = FontFamily.Monospace,
                                        style = MaterialTheme.typography.labelSmall)
                                }
                                Spacer(Modifier.height(2.dp))
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}

@Composable
private fun DiagCard(item: DiagItem, onFix: (String, String) -> Unit) {
    val (bgColor, iconColor, icon) = when (item.status) {
        DiagStatus.OK -> Triple(Color(0xFF4CAF50).copy(0.1f), Color(0xFF4CAF50), Icons.Filled.CheckCircle)
        DiagStatus.WARNING -> Triple(Color(0xFFFF9800).copy(0.1f), Color(0xFFFF9800), Icons.Filled.Warning)
        DiagStatus.ERROR -> Triple(Color(0xFFF44336).copy(0.1f), Color(0xFFF44336), Icons.Filled.Error)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = iconColor, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(item.title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Surface(shape = RoundedCornerShape(20.dp), color = iconColor.copy(0.2f)) {
                    Text(
                        when (item.status) { DiagStatus.OK -> "✅ OK"; DiagStatus.WARNING -> "⚠️"; DiagStatus.ERROR -> "❌" },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall, color = iconColor
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(item.detail, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (item.fixLabel.isNotEmpty() && item.settingsAction.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(
                    onClick = { onFix(item.settingsAction, item.settingsUri) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Filled.Settings, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(item.fixLabel, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
