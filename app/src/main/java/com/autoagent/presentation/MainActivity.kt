package com.autoagent.presentation

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.autoagent.domain.model.InstalledAppInfo
import com.autoagent.presentation.applist.AppListScreen
import com.autoagent.presentation.dashboard.DashboardScreen
import com.autoagent.presentation.diagnostics.DiagnosticsScreen
import com.autoagent.presentation.setup.AccessibilitySetupScreen
import com.autoagent.presentation.taskbuilder.TaskBuilderScreen
import com.autoagent.util.AccessibilityHelper
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation()
                }
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val context = LocalContext.current
    var screen by remember { mutableStateOf("dashboard") }
    var selectedApp by remember { mutableStateOf<InstalledAppInfo?>(null) }

    // Check accessibility on every resume
    var accessibilityEnabled by remember {
        mutableStateOf(AccessibilityHelper.isAccessibilityEnabled(context))
    }

    LaunchedEffect(screen) {
        accessibilityEnabled = AccessibilityHelper.isAccessibilityEnabled(context)
    }

    when (screen) {
        "dashboard" -> DashboardScreen(
            onAddTask = {
                try { selectedApp = null; screen = "app_list" }
                catch (e: Exception) { Log.e("Nav", e.message ?: "") }
            },
            onEditTask = { screen = "add_task" },
            onViewLogs = {},
            onDiagnostics = { screen = "diagnostics" },
            onSetupAccessibility = { screen = "accessibility_setup" },
        )
        "app_list" -> AppListScreen(
            onAppSelected = { app ->
                try { selectedApp = app; screen = "add_task" }
                catch (e: Exception) { screen = "dashboard" }
            },
            onBack = { selectedApp = null; screen = "dashboard" }
        )
        "add_task" -> TaskBuilderScreen(
            preSelectedApp = selectedApp,
            onBack = { selectedApp = null; screen = "dashboard" },
            onPickApp = { screen = "app_list" }
        )
        "diagnostics" -> DiagnosticsScreen(onBack = { screen = "dashboard" })
        "accessibility_setup" -> AccessibilitySetupScreen(
            onDone = { screen = "dashboard" },
            onSkip = { screen = "dashboard" }
        )
        else -> { screen = "dashboard" }
    }
}
