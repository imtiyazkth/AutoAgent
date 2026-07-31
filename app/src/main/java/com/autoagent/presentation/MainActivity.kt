package com.autoagent.presentation

import android.os.Bundle
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
    var currentScreen by remember { mutableStateOf("dashboard") }
    var selectedApp by remember { mutableStateOf<InstalledAppInfo?>(null) }

    when (currentScreen) {
        "dashboard" -> DashboardScreen(
            onAddTask = { currentScreen = "app_list" },
            onEditTask = { currentScreen = "add_task" },
            onViewLogs = { }
        )
        "app_list" -> AppListScreen(
            onAppSelected = { app ->
                selectedApp = app
                currentScreen = "add_task"
            },
            onBack = { currentScreen = "dashboard" }
        )
        "add_task" -> TaskBuilderScreen(
            preSelectedApp = selectedApp,
            onBack = {
                selectedApp = null
                currentScreen = "dashboard"
            },
            onPickApp = { currentScreen = "app_list" }
        )
    }
}
