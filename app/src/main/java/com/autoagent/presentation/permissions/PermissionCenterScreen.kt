package com.autoagent.presentation.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import com.autoagent.util.isAccessibilityEnabled

enum class PermStatus { GRANTED, PENDING, BLOCKED }
enum class PermRisk { LOW, MEDIUM, HIGH }

data class PermissionItem(
    val id: String,
    val title: String,
    val reason: String,
    val risk: PermRisk,
    val required: Boolean,
    val status: PermStatus,
    val fixAction: (() -> Unit)?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionCenterScreen(
    onAllCriticalGranted: () -> Unit,
    onSkip: () -> Unit
) {
    val context = LocalContext.current
    var refreshKey by remember { mutableIntStateOf(0) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refreshKey++ }

    val permissions by remember(refreshKey) {
        mutableStateOf(
            buildPermissionList(context) { perms ->
                permLauncher.launch(perms.toTypedArray())
            }
        )
    }

    val criticalGranted = permissions.filter { it.required }.all { it.status == PermStatus.GRANTED }
    val grantedCount = permissions.count { it.status == PermStatus.GRANTED }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Permission Center", fontWeight = FontWeight.Bold)
                        Text("$grantedCount / ${permissions.size} granted",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = { TextButton(onClick = onSkip) { Text("Skip") } }
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Column(Modifier.padding(16.dp, 10.dp, 16.dp, 24.dp)) {
                    LinearProgressIndicator(
                        progress = grantedCount.toFloat() / permissions.size.coerceAtLeast(1),
                        modifier = Modifier.fillMaxWidth(),
                        color = if (criticalGranted) Color(0xFF4CAF50)
                        else MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { if (criticalGranted) onAllCriticalGranted() else refreshKey++ },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            if (criticalGranted) Icons.Filled.Check else Icons.Filled.Refresh,
                            null
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (criticalGranted) "Sab Ready — Aage Jao"
                            else "Status Refresh Karo",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("AutoAgent ko kaam karne ke liye permissions chahiye.",
                            fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text("Zaroori grant karo — baaki optional hain.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            val critical = permissions.filter { it.required }
            val optional = permissions.filter { !it.required }

            if (critical.isNotEmpty()) {
                item {
                    Text("Zaroori Permissions", fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error)
                }
                items(critical) { perm -> PermCard(perm) { refreshKey++ } }
            }

            if (optional.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(4.dp))
                    Text("Optional Permissions", fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color(0xFFFF9800))
                }
                items(optional) { perm -> PermCard(perm) { refreshKey++ } }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun PermCard(item: PermissionItem, onRefresh: () -> Unit) {
    val bgColor = when (item.status) {
        PermStatus.GRANTED -> Color(0xFF4CAF50).copy(0.1f)
        PermStatus.PENDING -> Color(0xFFFF9800).copy(0.1f)
        PermStatus.BLOCKED -> Color(0xFFF44336).copy(0.1f)
    }
    val statusLabel = when (item.status) {
        PermStatus.GRANTED -> "Granted"
        PermStatus.PENDING -> "Pending"
        PermStatus.BLOCKED -> "Blocked"
    }

    Card(colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    when (item.status) {
                        PermStatus.GRANTED -> Icons.Filled.CheckCircle
                        PermStatus.PENDING -> Icons.Filled.Warning
                        PermStatus.BLOCKED -> Icons.Filled.Block
                    },
                    null,
                    tint = when (item.status) {
                        PermStatus.GRANTED -> Color(0xFF4CAF50)
                        PermStatus.PENDING -> Color(0xFFFF9800)
                        PermStatus.BLOCKED -> Color(0xFFF44336)
                    },
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(item.title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(statusLabel, style = MaterialTheme.typography.labelSmall)
            }
            Text(item.reason, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (item.status != PermStatus.GRANTED && item.fixAction != null) {
                FilledTonalButton(
                    onClick = { item.fixAction.invoke(); onRefresh() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Filled.OpenInNew, null, Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Fix Karo", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

private fun buildPermissionList(
    context: Context,
    requestRuntime: (List<String>) -> Unit
): List<PermissionItem> {

    // SAFE check — never throws on any Android version
    fun safeCheck(perm: String): Boolean = try {
        ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
    } catch (e: Exception) { false }

    fun openSettings(action: String, uri: String? = null) {
        try {
            val intent = if (uri != null) Intent(action, Uri.parse(uri)) else Intent(action)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (ex: Exception) {}
        }
    }

    val list = mutableListOf<PermissionItem>()

    // 1. Accessibility — most critical
    val accessEnabled = try { isAccessibilityEnabled(context) } catch (e: Exception) { false }
    list.add(PermissionItem(
        id = "accessibility",
        title = "Accessibility Service",
        reason = "Apps launch karna, text type karna, buttons tap karna — sab ke liye zaroori.",
        risk = PermRisk.HIGH, required = true,
        status = if (accessEnabled) PermStatus.GRANTED else PermStatus.PENDING,
        fixAction = { openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS) }
    ))

    // 2. Notifications (Android 13+)
    val notifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        safeCheck(Manifest.permission.POST_NOTIFICATIONS) else true
    list.add(PermissionItem(
        id = "notifications", title = "Notifications",
        reason = "Task status aur alerts dikhane ke liye.",
        risk = PermRisk.LOW, required = true,
        status = if (notifGranted) PermStatus.GRANTED else PermStatus.PENDING,
        fixAction = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                requestRuntime(listOf(Manifest.permission.POST_NOTIFICATIONS))
        }
    ))

    // 3. Battery optimization
    val batteryOk = try {
        (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .isIgnoringBatteryOptimizations(context.packageName)
    } catch (e: Exception) { false }
    list.add(PermissionItem(
        id = "battery", title = "Battery Optimization Exemption",
        reason = "Background tasks rok na sake — zaroor grant karo.",
        risk = PermRisk.MEDIUM, required = true,
        status = if (batteryOk) PermStatus.GRANTED else PermStatus.PENDING,
        fixAction = {
            openSettings(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                "package:${context.packageName}"
            )
        }
    ))

    // 4. Overlay (optional)
    val overlayOk = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            Settings.canDrawOverlays(context) else true
    } catch (e: Exception) { false }
    list.add(PermissionItem(
        id = "overlay", title = "Display Over Other Apps",
        reason = "Emergency stop button dikhane ke liye.",
        risk = PermRisk.MEDIUM, required = false,
        status = if (overlayOk) PermStatus.GRANTED else PermStatus.PENDING,
        fixAction = {
            openSettings(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:${context.packageName}"
            )
        }
    ))

    // 5. Contacts (optional)
    val contactsGranted = safeCheck(Manifest.permission.READ_CONTACTS)
    list.add(PermissionItem(
        id = "contacts", title = "Contacts",
        reason = "WhatsApp/Telegram mein contact dhundne ke liye.",
        risk = PermRisk.MEDIUM, required = false,
        status = if (contactsGranted) PermStatus.GRANTED else PermStatus.PENDING,
        fixAction = { requestRuntime(listOf(Manifest.permission.READ_CONTACTS)) }
    ))

    // 6. Microphone (optional)
    val micGranted = safeCheck(Manifest.permission.RECORD_AUDIO)
    list.add(PermissionItem(
        id = "microphone", title = "Microphone",
        reason = "Voice se commands dene ke liye.",
        risk = PermRisk.LOW, required = false,
        status = if (micGranted) PermStatus.GRANTED else PermStatus.PENDING,
        fixAction = { requestRuntime(listOf(Manifest.permission.RECORD_AUDIO)) }
    ))

    // 7. SMS (optional) — SAFE: only add if declared in manifest
    val smsGranted = safeCheck(Manifest.permission.SEND_SMS)
    list.add(PermissionItem(
        id = "sms", title = "SMS",
        reason = "SMS tasks ke liye — optional.",
        risk = PermRisk.HIGH, required = false,
        status = if (smsGranted) PermStatus.GRANTED else PermStatus.PENDING,
        fixAction = {
            requestRuntime(listOf(
                Manifest.permission.SEND_SMS,
                Manifest.permission.READ_SMS
            ))
        }
    ))

    return list
}
