package com.autoagent.presentation.setup

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autoagent.service.accessibility.AutoAgentAccessibilityService
import com.autoagent.util.isAccessibilityEnabled
import kotlinx.coroutines.delay

// =============================================
// CORRECT ADB COMMAND — single line, no breaks
// applicationId = com.autoagent.personal
// service class = com.autoagent.service.accessibility.AutoAgentAccessibilityService
// =============================================
private const val CORRECT_ADB_COMPONENT =
    "com.autoagent.personal/com.autoagent.service.accessibility.AutoAgentAccessibilityService"

private const val ADB_ENABLE_COMMAND =
    "adb shell settings put secure enabled_accessibility_services $CORRECT_ADB_COMPONENT"

private const val ADB_ENABLE_FLAG =
    "adb shell settings put secure accessibility_enabled 1"

// isAccessibilityEnabled imported from com.autoagent.util

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccessibilitySetupScreen(
    onDone: () -> Unit,
    onSkip: () -> Unit
) {
    val context = LocalContext.current
    var isEnabled by remember { mutableStateOf(isAccessibilityEnabled(context)) }
    var serviceConnected by remember { mutableStateOf(AutoAgentAccessibilityService.isAvailable()) }

    // Poll every second until enabled
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            isEnabled = isAccessibilityEnabled(context)
            serviceConnected = AutoAgentAccessibilityService.isAvailable()
            if (isEnabled && serviceConnected) break
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Accessibility Setup", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onSkip) {
                        Icon(Icons.Filled.ArrowBack, null)
                    }
                },
                actions = {
                    TextButton(onClick = onSkip) { Text("Skip") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // STATUS CARD
            if (isEnabled) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF4CAF50).copy(0.15f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("✅", fontSize = 48.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("Accessibility ON ho gaya!", fontWeight = FontWeight.Bold,
                            color = Color(0xFF4CAF50), fontSize = 18.sp)
                        Text(if (serviceConnected) "Service running hai ✅" else "Restart karo app ko",
                            style = MaterialTheme.typography.bodySmall, color = Color(0xFF4CAF50))
                    }
                }
                Button(
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                ) {
                    Icon(Icons.Filled.Check, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Dashboard Pe Jao ✅", fontWeight = FontWeight.Bold)
                }
            } else {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(0.3f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🤖", fontSize = 48.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("Accessibility Enable karo", fontWeight = FontWeight.Bold, fontSize = 18.sp, textAlign = TextAlign.Center)
                        Text("AutoAgent ko automation ke liye yeh zaroori hai", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    }
                }

                // PACKAGE INFO — for debugging
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("📦 App Info", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(4.dp))
                        InfoRow("Package", context.packageName)
                        InfoRow("Service", "AutoAgent Automation")
                        InfoRow("Look for", "AutoAgent Automation in Settings")
                    }
                }

                // STEP BY STEP
                Text("📋 Steps", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)

                val steps = listOf(
                    "Settings Open Karo" to "Neeche button tap karo",
                    "Accessibility dhundo" to "'Accessibility' ya 'Special App Access' mein jao",
                    "'Downloaded Services' dekho" to "Ya 'Installed Services' section",
                    "'AutoAgent Automation' dhundo" to "List mein scroll karke dhundo",
                    "Tap karke ON karo" to "Toggle ON karo → 'Allow' tap karo",
                    "Wapas AutoAgent aao" to "Back press karo — status update ho jayega"
                )

                steps.forEachIndexed { i, (title, detail) ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                            Box(
                                modifier = Modifier.size(28.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("${i + 1}", color = Color.White, fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelMedium)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(title, fontWeight = FontWeight.Bold)
                                Text(detail, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                // PRIMARY ACTION
                Button(
                    onClick = {
                        try {
                            context.startActivity(
                                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        } catch (e: Exception) {
                            try {
                                context.startActivity(
                                    Intent(Settings.ACTION_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            } catch (ex: Exception) {}
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Filled.Settings, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Settings → Accessibility Kholo", fontWeight = FontWeight.Bold)
                }

                // MIUI SPECIFIC
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFF9800).copy(0.15f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("⚠️ Xiaomi / MIUI Users", fontWeight = FontWeight.Bold, color = Color(0xFFFF9800))
                        Spacer(Modifier.height(4.dp))
                        Text("Agar 'AutoAgent Automation' list mein nahi dikh raha:", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(4.dp))
                        val miuiPaths = listOf(
                            "Settings → Additional Settings → Accessibility",
                            "Settings → Privacy → Special App Access → Accessibility",
                            "Phone restart karo phir dobara check karo",
                            "Play Protect temporarily disable karo"
                        )
                        miuiPaths.forEach { path ->
                            Text("• $path", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                // ADB COMMAND — CORRECT FORMAT
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(0.85f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Terminal, null,
                                tint = Color.Green, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Termux ADB Fix (Last Resort)",
                                fontWeight = FontWeight.Bold, color = Color.White,
                                style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("# Step 1: Enable accessibility",
                            color = Color.Gray, fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.labelSmall)
                        // CORRECT ADB COMMAND — one complete line
                        Text(
                            ADB_ENABLE_COMMAND,
                            color = Color.Green,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.labelSmall
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("# Step 2: Set accessibility flag",
                            color = Color.Gray, fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.labelSmall)
                        Text(
                            ADB_ENABLE_FLAG,
                            color = Color.Green,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.labelSmall
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Component: $CORRECT_ADB_COMPONENT",
                            color = Color(0xFFFFEB3B),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text("$label: ", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        Text(value, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
