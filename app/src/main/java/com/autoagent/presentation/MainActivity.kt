package com.autoagent.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.autoagent.domain.model.InstalledAppInfo
import com.autoagent.presentation.applist.AppListScreen
import com.autoagent.presentation.dashboard.DashboardScreen
import com.autoagent.presentation.dashboard.DashboardViewModel
import com.autoagent.presentation.diagnostics.DiagnosticsScreen
import com.autoagent.presentation.setup.AccessibilitySetupScreen
import com.autoagent.presentation.taskbuilder.TaskBuilderScreen
import com.autoagent.util.L
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { L.d("MainActivity", "Perms: $it") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestPerms()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation()
                }
            }
        }
    }

    private fun requestPerms() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (needed.isNotEmpty()) permLauncher.launch(needed.toTypedArray())
    }
}

@Composable
fun AppNavigation() {
    val dashVM: DashboardViewModel = hiltViewModel()
    val uiState by dashVM.uiState.collectAsState()

    var screen by remember { mutableStateOf("dashboard") }
    var selectedApp by remember { mutableStateOf<InstalledAppInfo?>(null) }

    // Navigation — state based at top level, never cancelled
    LaunchedEffect(uiState.navigateTo) {
        val target = uiState.navigateTo ?: return@LaunchedEffect
        L.d("AppNav", "navigateTo=$target")
        when {
            target == "add_task" -> { selectedApp = null; screen = "app_list" }
            target.startsWith("edit_task_") -> screen = "add_task"
        }
        dashVM.onNavigationHandled()
    }

    when (screen) {
        "dashboard" -> DashboardScreen(
            viewModel = dashVM,
            onViewLogs = {},
            onDiagnostics = { screen = "diagnostics" },
            onSetupAccessibility = { screen = "accessibility_setup" }
        )
        "app_list" -> AppListScreen(
            onAppSelected = { app ->
                L.d("AppNav", "App picked: ${app.packageName}")
                selectedApp = app
                screen = "add_task"
            },
            onBack = { selectedApp = null; screen = "dashboard" }
        )
        "add_task" -> TaskBuilderScreen(
            preSelectedApp = selectedApp,
            onBack = { selectedApp = null; screen = "dashboard" },
            onPickApp = { screen = "app_list" },
            onSaved = {
                L.d("AppNav", "Task saved, back to dashboard")
                selectedApp = null
                screen = "dashboard"
                dashVM.uiState.value.let {
                    // Show success message
                }
            }
        )
        "diagnostics" -> DiagnosticsScreen(onBack = { screen = "dashboard" })
        "accessibility_setup" -> AccessibilitySetupScreen(
            onDone = { screen = "dashboard" },
            onSkip = { screen = "dashboard" }
        )
        else -> { screen = "dashboard" }
    }
}
