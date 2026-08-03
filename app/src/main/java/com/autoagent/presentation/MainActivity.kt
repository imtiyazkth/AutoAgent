package com.autoagent.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.autoagent.domain.model.InstalledAppInfo
import com.autoagent.presentation.applist.AppListScreen
import com.autoagent.presentation.dashboard.DashboardScreen
import com.autoagent.presentation.diagnostics.DiagnosticsScreen
import com.autoagent.presentation.setup.AccessibilitySetupScreen
import com.autoagent.presentation.taskbuilder.TaskBuilderScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Permission launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        Log.d("AutoAgent", "Permissions result: $permissions")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request permissions on start
        requestRequiredPermissions()

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation()
                }
            }
        }
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf<String>()

        // Notifications (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }
}

@Composable
fun AppNavigation() {
    var screen by remember { mutableStateOf("dashboard") }
    var selectedApp by remember { mutableStateOf<InstalledAppInfo?>(null) }

    fun navigate(to: String) {
        try {
            screen = to
        } catch (e: Exception) {
            Log.e("AutoAgent", "Nav error: ${e.message}")
            screen = "dashboard"
        }
    }

    when (screen) {
        "dashboard" -> DashboardScreen(
            onAddTask = {
                try {
                    selectedApp = null
                    navigate("app_list")
                } catch (e: Exception) {
                    Log.e("AutoAgent", "onAddTask error: ${e.message}")
                }
            },
            onEditTask = { navigate("add_task") },
            onViewLogs = {},
            onDiagnostics = { navigate("diagnostics") },
            onSetupAccessibility = { navigate("accessibility_setup") }
        )
        "app_list" -> AppListScreen(
            onAppSelected = { app ->
                try {
                    selectedApp = app
                    navigate("add_task")
                } catch (e: Exception) {
                    Log.e("AutoAgent", "App select error: ${e.message}")
                    navigate("dashboard")
                }
            },
            onBack = {
                selectedApp = null
                navigate("dashboard")
            }
        )
        "add_task" -> TaskBuilderScreen(
            preSelectedApp = selectedApp,
            onBack = {
                selectedApp = null
                navigate("dashboard")
            },
            onPickApp = { navigate("app_list") }
        )
        "diagnostics" -> DiagnosticsScreen(
            onBack = { navigate("dashboard") }
        )
        "accessibility_setup" -> AccessibilitySetupScreen(
            onDone = { navigate("dashboard") },
            onSkip = { navigate("dashboard") }
        )
        else -> navigate("dashboard")
    }
}
