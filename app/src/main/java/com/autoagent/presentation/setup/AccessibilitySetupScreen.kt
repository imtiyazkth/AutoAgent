package com.autoagent.presentation.setup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autoagent.util.AccessibilityHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccessibilitySetupScreen(
    onDone: () -> Unit,
    onSkip: () -> Unit
) {
    val context = LocalContext.current
    var isEnabled by remember { mutableStateOf(AccessibilityHelper.isAccessibilityEnabled(context)) }

    // Check every second if user enabled it
    LaunchedEffect(Unit) {
        while (!isEnabled) {
            kotlinx.coroutines.delay(1000)
            isEnabled = AccessibilityHelper.isAccessibilityEnabled(context)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Accessibility Setup", fontWeight = FontWeight.Bold) },
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
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // STATUS
            AnimatedContent(targetState = isEnabled) { enabled ->
                if (enabled) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF4CAF50).copy(0.15f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("✅", fontSize = 48.sp)
                            Spacer(Modifier.height(8.dp))
                            Text("Accessibility ON ho gaya!",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4CAF50),
                                fontSize = 18.sp)
                            Text("AutoAgent ab automation kar sakta hai",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF4CAF50),
                                textAlign = TextAlign.Center)
                        }
                    }
                } else {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(0.3f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("🤖", fontSize = 48.sp)
                            Spacer(Modifier.height(8.dp))
                            Text("Accessibility Enable karo",
                                fontWeight = FontWeight.Bold, fontSize = 18.sp,
                                textAlign = TextAlign.Center)
                            Text("Automation ke liye yeh zaroori hai",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center)
                        }
                    }
                }
            }

            // STEP BY STEP GUIDE
            if (!isEnabled) {
                Text("📋 Step by Step Guide",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium)

                val steps = listOf(
                    Triple("1", "Settings kholo", "Neeche 'Settings Open Karo' button tap karo"),
                    Triple("2", "Accessibility dhundo", "'Accessibility' option pe tap karo"),
                    Triple("3", "Downloaded Services", "'Downloaded Services' ya 'Installed Services' section dekho"),
                    Triple("4", "AutoAgent Automation", "'AutoAgent Automation' pe tap karo"),
                    Triple("5", "ON karo", "Toggle ON karo → 'Allow' tap karo"),
                    Triple("6", "Wapas aao", "Back button se AutoAgent pe wapas aao")
                )

                steps.forEach { (num, title, detail) ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(num, color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelMedium)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(title, fontWeight = FontWeight.Bold)
                                Text(detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                // MIUI WARNING
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFF9800).copy(0.15f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("⚠️ Xiaomi/MIUI Users ke liye",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF9800))
                        Text("Agar AutoAgent list mein nahi dikh raha:",
                            style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(4.dp))
                        Text("• Settings → Additional Settings → Accessibility",
                            style = MaterialTheme.typography.bodySmall)
                        Text("• Ya: Settings → Privacy → Special App Access → Accessibility",
                            style = MaterialTheme.typography.bodySmall)
                        Text("• Ya: Phone restart karo phir check karo",
                            style = MaterialTheme.typography.bodySmall)
                        Text("• MIUI mein unknown source apps restrict hoti hain — neeche 'MIUI Fix' try karo",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFF9800))
                    }
                }

                // OPEN SETTINGS BUTTON
                Button(
                    onClick = { AccessibilityHelper.openAccessibilitySettings(context) },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Filled.Settings, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Settings Open Karo → Accessibility",
                        fontWeight = FontWeight.Bold)
                }

                // MIUI SPECIFIC FIX
                OutlinedButton(
                    onClick = {
                        // Try MIUI-specific paths
                        val miuiPaths = listOf(
                            "com.android.settings/.accessibility.AccessibilitySettings",
                            "com.miui.securitycenter/.MainActivity"
                        )
                        var opened = false
                        for (path in miuiPaths) {
                            try {
                                val parts = path.split("/")
                                val intent = Intent().apply {
                                    setClassName(parts[0], parts[0] + parts[1])
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                                opened = true
                                break
                            } catch (e: Exception) { continue }
                        }
                        if (!opened) AccessibilityHelper.openAccessibilitySettings(context)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("MIUI Fix — Alternative Settings")
                }

                // ADB COMMAND OPTION
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("🔧 Termux se fix (Agar upar wala kaam nahi kiya)",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(6.dp))
                        Surface(
                            color = Color.Black.copy(0.8f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                "adb shell settings put secure\nenabled_accessibility_services\ncom.autoagent.personal/com.autoagent\n.service.accessibility\n.AutoAgentAccessibilityService",
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Green,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            // DONE BUTTON
            if (isEnabled) {
                Button(
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                ) {
                    Icon(Icons.Filled.Check, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Dashboard Pe Jao ✅", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}
