package com.autoagent.personal.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.autoagent.personal.ai.NaturalLanguageTaskParser
import com.autoagent.personal.presentation.ai.AiTaskScreen
import com.autoagent.personal.presentation.dashboard.DashboardScreen
import com.autoagent.personal.presentation.dashboard.DashboardViewModel
import com.autoagent.personal.presentation.diagnostics.DiagnosticsScreen
import com.autoagent.personal.presentation.permissions.PermissionCenterScreen
import com.autoagent.personal.presentation.setup.AccessibilitySetupScreen
import com.autoagent.personal.presentation.taskbuilder.TaskBuilderScreen
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
    val dashVM: DashboardViewModel = hiltViewModel()
    val uiState by dashVM.uiState.collectAsState()

    var screen by remember { mutableStateOf("dashboard") }
    var editTaskId by remember { mutableStateOf<Long?>(null) }
    // AI se aaya parsed task store karo
    var aiParsedTask by remember {
        mutableStateOf<NaturalLanguageTaskParser.ParsedTask?>(null)
    }

    LaunchedEffect(uiState.navigateTo) {
        val target = uiState.navigateTo ?: return@LaunchedEffect
        when {
            target == "add_task" -> { editTaskId = null; screen = "add_task" }
            target.startsWith("edit_task_") -> {
                editTaskId = target.removePrefix("edit_task_").toLongOrNull()
                screen = "add_task"
            }
        }
        dashVM.onNavigationHandled()
    }

    when (screen) {
        "permission_center" -> PermissionCenterScreen(
            onAllCriticalGranted = { screen = "dashboard" },
            onSkip = { screen = "dashboard" }
        )
        "dashboard" -> DashboardScreen(
            viewModel = dashVM,
            onViewLogs = {},
            onDiagnostics = { screen = "diagnostics" },
            onSetupAccessibility = { screen = "accessibility_setup" },
            onPermissions = { screen = "permission_center" },
            onAiTask = { screen = "ai_task" }
        )
        "add_task" -> TaskBuilderScreen(
            editTaskId = editTaskId,
            prefillFromAi = aiParsedTask,
            onBack = {
                editTaskId = null
                aiParsedTask = null
                screen = "dashboard"
            },
            onSaved = {
                editTaskId = null
                aiParsedTask = null
                screen = "dashboard"
            }
        )
        "diagnostics" -> DiagnosticsScreen(onBack = { screen = "dashboard" })
        "ai_task" -> AiTaskScreen(
            onBack = { screen = "dashboard" },
            onTaskParsed = { parsed ->
                aiParsedTask = parsed   // parsed task save karo
                screen = "add_task"     // TaskBuilder mein jao
            }
        )
        "accessibility_setup" -> AccessibilitySetupScreen(
            onDone = { screen = "dashboard" },
            onSkip = { screen = "dashboard" }
        )
        else -> { screen = "dashboard" }
    }
}
