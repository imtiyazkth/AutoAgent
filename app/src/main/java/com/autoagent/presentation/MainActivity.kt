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

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms -> L.d("MainActivity", "Permissions: $perms") }

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
        if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray())
    }
}

@Composable
fun AppNavigation() {
    // ViewModel at TOP LEVEL — survives screen changes
    val dashboardViewModel: DashboardViewModel = hiltViewModel()
    val uiState by dashboardViewModel.uiState.collectAsState()

    var screen by remember { mutableStateOf("dashboard") }
    var selectedApp by remember { mutableStateOf<InstalledAppInfo?>(null) }

    // KEY FIX: LaunchedEffect here at top level — NEVER cancelled
    // This fires whenever navigateTo changes, regardless of which screen is showing
    LaunchedEffect(uiState.navigateTo) {
        val target = uiState.navigateTo ?: return@LaunchedEffect
        L.d("AppNavigation", "navigateTo=$target, current screen=$screen")
        try {
            when (target) {
                "add_task" -> {
                    selectedApp = null
                    screen = "app_list"
                }
                "edit_task" -> {
                    screen = "add_task"
                }
            }
        } catch (e: Exception) {
            L.e("AppNavigation", "Navigation error", e)
        } finally {
            // Always clear after handling
            dashboardViewModel.onNavigationHandled()
        }
    }

    when (screen) {
        "dashboard" -> DashboardScreen(
            viewModel = dashboardViewModel,
            onViewLogs = {},
            onDiagnostics = { screen = "diagnostics" },
            onSetupAccessibility = { screen = "accessibility_setup" }
        )
        "app_list" -> AppListScreen(
            onAppSelected = { app ->
                L.d("AppNavigation", "App selected: ${app.packageName}")
                selectedApp = app
                screen = "add_task"
            },
            onBack = { selectedApp = null; screen = "dashboard" }
        )
        "add_task" -> TaskBuilderScreen(
            preSelectedApp = selectedApp,
            onBack = { selectedApp = null; screen = "dashboard" },
            onPickApp = { screen = "app_list" }
        )
        "diagnostics" -> DiagnosticsScreen(
            onBack = { screen = "dashboard" }
        )
        "accessibility_setup" -> AccessibilitySetupScreen(
            onDone = { screen = "dashboard" },
            onSkip = { screen = "dashboard" }
        )
        else -> {
            L.e("AppNavigation", "Unknown screen: $screen")
            screen = "dashboard"
        }
    }
}
