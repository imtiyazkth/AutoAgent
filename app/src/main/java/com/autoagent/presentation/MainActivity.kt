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
import com.autoagent.domain.model.InstalledAppInfo
import com.autoagent.presentation.applist.AppListScreen
import com.autoagent.presentation.dashboard.DashboardScreen
import com.autoagent.presentation.diagnostics.DiagnosticsScreen
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
    var screen by remember { mutableStateOf("dashboard") }
    var selectedApp by remember { mutableStateOf<InstalledAppInfo?>(null) }

    when (screen) {
        "dashboard" -> DashboardScreen(
            onAddTask = { try { selectedApp = null; screen = "app_list" } catch (e: Exception) { Log.e("AutoAgent", e.message ?: "") } },
            onEditTask = { screen = "add_task" },
            onViewLogs = {},
            onDiagnostics = { screen = "diagnostics" }
        )
        "app_list" -> AppListScreen(
            onAppSelected = { app -> try { selectedApp = app; screen = "add_task" } catch (e: Exception) { screen = "dashboard" } },
            onBack = { selectedApp = null; screen = "dashboard" }
        )
        "add_task" -> TaskBuilderScreen(
            preSelectedApp = selectedApp,
            onBack = { selectedApp = null; screen = "dashboard" },
            onPickApp = { screen = "app_list" }
        )
        "diagnostics" -> DiagnosticsScreen(onBack = { screen = "dashboard" })
        else -> { screen = "dashboard" }
    }
}
