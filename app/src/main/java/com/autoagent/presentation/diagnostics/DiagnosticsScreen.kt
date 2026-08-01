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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autoagent.data.repository.AgentRepository
import com.autoagent.service.accessibility.AutoAgentAccessibilityService
import com.autoagent.util.PinManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DiagItem(val title: String, val status: DiagStatus, val detail: String, val fixAction: String, val settingsAction: String = "")
enum class DiagStatus { OK, WARNING, ERROR }

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pinManager: PinManager,
    private val repository: AgentRepository
) : ViewModel() {
    private val _items = MutableStateFlow<List<DiagItem>>(emptyList())
    val items: StateFlow<List<DiagItem>> = _items

    init { runDiagnostics() }

    fun runDiagnostics() {
        viewModelScope.launch {
            val results = mutableListOf<DiagItem>()

            val accessEnabled = try {
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                    ?.contains(context.packageName) == true
            } catch (e: Exception) { false }
            results.add(DiagItem("Accessibility Service",
                if (accessEnabled) DiagStatus.OK else DiagStatus.ERROR,
                if (accessEnabled) "AutoAgent Automation ON hai ✅" else "OFF — Automation kaam nahi karega",
                "Settings → Accessibility → AutoAgent Automation → ON",
                Settings.ACTION_ACCESSIBILITY_SETTINGS))

            val pinSetup = try { pinManager.isPinSetup() } catch (e: Exception) { false }
            results.add(DiagItem("PIN Setup", if (pinSetup) DiagStatus.OK else DiagStatus.WARNING,
                if (pinSetup) "PIN set hai ✅" else "PIN set nahi", "App open karo"))

            val serviceRunning = AutoAgentAccessibilityService.getInstance() != null
            results.add(DiagItem("Service Instance",
                if (serviceRunning) DiagStatus.OK else DiagStatus.WARNING,
                if (serviceRunning) "Service running ✅" else "Null — Accessibility enable karo",
                "Accessibility ON karo", Settings.ACTION_ACCESSIBILITY_SETTINGS))

            val batteryOk = try {
                val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                pm.isIgnoringBatteryOptimizations(context.packageName)
            } catch (e: Exception) { false }
            results.add(DiagItem("Battery Optimization",
                if (batteryOk) DiagStatus.OK else DiagStatus.WARNING,
                if (batteryOk) "Optimized nahi ✅" else "ON hai — Background tasks band ho sakte hain",
                "Settings → Battery → Don't optimize → AutoAgent",
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS))

            results.add(DiagItem("Android Version",
                if (Build.VERSION.SDK_INT >= 26) DiagStatus.OK else DiagStatus.ERROR,
                "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                if (Build.VERSION.SDK_INT < 26) "Android 8.0+ required" else ""))

            results.add(DiagItem("Package Name", DiagStatus.OK,
                context.packageName, ""))

            _items.value = results
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(onBack: () -> Unit, viewModel: DiagnosticsViewModel = hiltViewModel()) {
    val items by viewModel.items.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🔍 Diagnostics", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null) } },
                actions = { IconButton(onClick = { viewModel.runDiagnostics() }) { Icon(Icons.Filled.Refresh, null) } }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                val errors = items.count { it.status == DiagStatus.ERROR }
                val warnings = items.count { it.status == DiagStatus.WARNING }
                Card(colors = CardDefaults.cardColors(containerColor = when { errors > 0 -> Color(0xFFF44336).copy(0.15f); warnings > 0 -> Color(0xFFFF9800).copy(0.15f); else -> Color(0xFF4CAF50).copy(0.15f) }),
                    shape = RoundedCornerShape(14.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(when { errors > 0 -> "❌ $errors error hain — fix karo"; warnings > 0 -> "⚠️ $warnings warnings hain"; else -> "✅ Sab theek hai!" }, fontWeight = FontWeight.Bold)
                        Text("${items.size} checks run kiye", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            items(items) { item ->
                val (bgColor, iconColor, icon) = when (item.status) {
                    DiagStatus.OK -> Triple(Color(0xFF4CAF50).copy(0.1f), Color(0xFF4CAF50), Icons.Filled.CheckCircle)
                    DiagStatus.WARNING -> Triple(Color(0xFFFF9800).copy(0.1f), Color(0xFFFF9800), Icons.Filled.Warning)
                    DiagStatus.ERROR -> Triple(Color(0xFFF44336).copy(0.1f), Color(0xFFF44336), Icons.Filled.Error)
                }
                Card(colors = CardDefaults.cardColors(containerColor = bgColor), shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(icon, null, tint = iconColor, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(item.title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Surface(shape = RoundedCornerShape(20.dp), color = iconColor.copy(0.2f)) {
                                Text(when (item.status) { DiagStatus.OK -> "OK"; DiagStatus.WARNING -> "⚠️"; DiagStatus.ERROR -> "❌" },
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall, color = iconColor)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(item.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (item.status != DiagStatus.OK && item.settingsAction.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            FilledTonalButton(onClick = {
                                try {
                                    val intent = when (item.settingsAction) {
                                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS ->
                                            Intent(item.settingsAction, Uri.parse("package:${context.packageName}"))
                                        else -> Intent(item.settingsAction)
                                    }
                                    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                } catch (e: Exception) {
                                    try { context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (ex: Exception) {}
                                }
                            }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                                Icon(Icons.Filled.Settings, null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Fix karo", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        if (item.fixAction.isNotEmpty()) {
                            Text("📌 ${item.fixAction}", style = MaterialTheme.typography.labelSmall, color = iconColor)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}
