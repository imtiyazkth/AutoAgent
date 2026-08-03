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

    // Safe navigation helper
    fun navigate(to: String) {
        try { screen = to } catch (e: Exception) {
            Log.e("AutoAgent_Nav", "Navigation error to $to: ${e.message}")
            screen = "dashboard"
        }
    }

    when (screen) {
        "dashboard" -> DashboardScreen(
            onAddTask = { selectedApp = null; navigate("app_list") },
            onEditTask = { navigate("add_task") },
            onViewLogs = { /* TODO */ },
            onDiagnostics = { navigate("diagnostics") },
            onSetupAccessibility = { navigate("accessibility_setup") }
        )
        "app_list" -> AppListScreen(
            onAppSelected = { app ->
                selectedApp = app
                navigate("add_task")
            },
            onBack = { selectedApp = null; navigate("dashboard") }
        )
        "add_task" -> TaskBuilderScreen(
            preSelectedApp = selectedApp,
            onBack = { selectedApp = null; navigate("dashboard") },
            onPickApp = { navigate("app_list") }
        )
        "diagnostics" -> DiagnosticsScreen(
            onBack = { navigate("dashboard") }
        )
        "accessibility_setup" -> AccessibilitySetupScreen(
            onDone = { navigate("dashboard") },
            onSkip = { navigate("dashboard") }
        )
        else -> { navigate("dashboard") }
    }
}
