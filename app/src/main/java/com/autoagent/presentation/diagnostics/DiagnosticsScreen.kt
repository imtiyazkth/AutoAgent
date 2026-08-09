package com.autoagent.personal.presentation.diagnostics

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import com.autoagent.personal.data.repository.AgentRepository
import com.autoagent.personal.service.accessibility.AutoAgentAccessibilityService
import com.autoagent.personal.util.PinManager
import com.autoagent.personal.util.isAccessibilityEnabled
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

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

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            val result = mutableListOf<DiagItem>()

            // 1. Accessibility setting
            val accessEnabled = withContext(Dispatchers.IO) { isAccessibilityEnabled(context) }
            result.add(DiagItem(
                title = "Accessibility Service",
                status = if (accessEnabled) DiagStatus.OK else DiagStatus.ERROR,
                detail = if (accessEnabled) "ON ✅" else "OFF — tasks nahi chalenge",
                fixLabel = "Enable karo",
                settingsAction = Settings.ACTION_ACCESSIBILITY_SETTINGS
            ))

            // 2. Accessibility service alive
            val serviceAlive = AutoAgentAccessibilityService.isAvailable()
            result.add(DiagItem(
                title = "Service Instance",
                status = if (serviceAlive) DiagStatus.OK
                         else if (accessEnabled) DiagStatus.WARNING
                         else DiagStatus.ERROR,
                detail = if (serviceAlive) "Connected ✅"
                         else if (accessEnabled) "Setting ON par service nahi chali — app restart karo"
                         else "Accessibility pehle enable karo"
            ))

            // 3. Battery optimization
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val batteryOk = pm.isIgnoringBatteryOptimizations(context.packageName)
            result.add(DiagItem(
                title = "Battery Optimization",
                status = if (batteryOk) DiagStatus.OK else DiagStatus.WARNING,
                detail = if (batteryOk) "Exempt ✅" else "Scheduled tasks rok sakta hai",
                fixLabel = "Fix karo",
                settingsAction = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                settingsUri = "package:${context.packageName}"
            ))

            // 4. Notifications
            val notifOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                        PackageManager.PERMISSION_GRANTED
            else true
            result.add(DiagItem(
                title = "Notifications",
                status = if (notifOk) DiagStatus.OK else DiagStatus.WARNING,
                detail = if (notifOk) "Allowed ✅" else "Task alerts nahi milenge",
                fixLabel = "Allow karo",
                settingsAction = Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                settingsUri = "package:${context.packageName}"
            ))

            // 5. PIN setup
            val pinOk = withContext(Dispatchers.IO) {
                runCatching { pinManager.isPinSetup() }.getOrDefault(false)
            }
            result.add(DiagItem(
                title = "PIN Security",
                status = if (pinOk) DiagStatus.OK else DiagStatus.WARNING,
                detail = if (pinOk) "Set ✅" else "PIN set nahi — app mein set karo"
            ))

            // 6. Task count — use StateFlow.value, NOT firstOrNull()
            val taskCount = repository.tasks.value.size
            result.add(DiagItem(
                title = "Saved Tasks",
                status = DiagStatus.OK,
                detail = "$taskCount tasks saved"
            ))

            // 7. Storage
            val freeBytes = context.filesDir.freeSpace
            val freeMb = freeBytes / (1024 * 1024)
            result.add(DiagItem(
                title = "Storage",
                status = if (freeMb > 50) DiagStatus.OK else DiagStatus.WARNING,
                detail = "${freeMb}MB free"
            ))

            // 8. App version
            val versionName = runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }.getOrDefault("Unknown")
            result.add(DiagItem(
                title = "App Version",
                status = DiagStatus.OK,
                detail = "v$versionName — AutoAgent"
            ))

            _items.value = result
            _isLoading.value = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    onBack: () -> Unit,
    viewModel: DiagnosticsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val items by viewModel.items.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🔍 Diagnostics", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, null)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Filled.Refresh, null)
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Checking system...", style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val ok = items.count { it.status == DiagStatus.OK }
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (ok == items.size)
                                Color(0xFF4CAF50).copy(0.15f)
                            else MaterialTheme.colorScheme.primaryContainer
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (ok == items.size) "✅ Sab theek hai!" else "⚠️ Kuch issues hain",
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "$ok / ${items.size} OK",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (ok == items.size) Color(0xFF4CAF50)
                                else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                items(items) { item ->
                    DiagCard(item) { action, uri ->
                        try {
                            val intent = if (uri.isNotEmpty())
                                Intent(action, Uri.parse(uri))
                            else Intent(action)
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            context.startActivity(
                                Intent(Settings.ACTION_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(60.dp)) }
            }
        }
    }
}

@Composable
fun DiagCard(item: DiagItem, onFix: (String, String) -> Unit) {
    val (color, icon) = when (item.status) {
        DiagStatus.OK      -> Pair(Color(0xFF4CAF50), Icons.Filled.CheckCircle)
        DiagStatus.WARNING -> Pair(Color(0xFFFF9800), Icons.Filled.Warning)
        DiagStatus.ERROR   -> Pair(Color(0xFFF44336), Icons.Filled.Error)
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = color.copy(0.08f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(item.title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(4.dp))
            Text(item.detail, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (item.fixLabel.isNotEmpty() && item.status != DiagStatus.OK) {
                Spacer(Modifier.height(6.dp))
                FilledTonalButton(
                    onClick = { onFix(item.settingsAction, item.settingsUri) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Filled.OpenInNew, null, Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(item.fixLabel, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
