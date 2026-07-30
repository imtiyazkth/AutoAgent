package com.autoagent.service.scheduler

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.autoagent.data.db.ExecutionLogEntity
import com.autoagent.data.repository.AgentRepository
import com.autoagent.domain.model.*
import com.autoagent.service.accessibility.AutoAgentAccessibilityService
import com.autoagent.util.GsonHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

@HiltWorker
class TaskExecutorWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: AgentRepository,
    private val gsonHelper: GsonHelper
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_TASK_ID = "task_id"

        fun scheduleTask(context: Context, task: AgentTask) {
            val data = workDataOf(KEY_TASK_ID to task.id)

            when (task.triggerType) {
                TriggerType.ONE_TIME -> {
                    val request = OneTimeWorkRequestBuilder<TaskExecutorWorker>()
                        .setInputData(data)
                        .build()
                    WorkManager.getInstance(context).enqueue(request)
                }
                TriggerType.DAILY, TriggerType.WEEKLY -> {
                    val request = PeriodicWorkRequestBuilder<TaskExecutorWorker>(
                        24, TimeUnit.HOURS
                    ).setInputData(data).build()
                    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                        "task_${task.id}",
                        ExistingPeriodicWorkPolicy.UPDATE,
                        request
                    )
                }
                TriggerType.INTERVAL -> {
                    val interval = task.intervalMinutes.toLong().coerceAtLeast(15)
                    val request = PeriodicWorkRequestBuilder<TaskExecutorWorker>(
                        interval, TimeUnit.MINUTES
                    ).setInputData(data).build()
                    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                        "task_${task.id}",
                        ExistingPeriodicWorkPolicy.UPDATE,
                        request
                    )
                }
                else -> { /* Manual or boot — not scheduled here */ }
            }
        }

        fun cancelTask(context: Context, taskId: Long) {
            WorkManager.getInstance(context).cancelUniqueWork("task_$taskId")
        }
    }

    override suspend fun doWork(): Result {
        val taskId = inputData.getLong(KEY_TASK_ID, -1)
        if (taskId == -1L) return Result.failure()

        val taskEntity = repository.getTask(taskId) ?: return Result.failure()
        val task = gsonHelper.entityToTask(taskEntity)

        if (!task.isEnabled) return Result.success()

        // EMERGENCY STOP CHECK
        if (AutoAgentAccessibilityService.emergencyStop.value) {
            Log.w("AutoAgent", "Emergency stop — skipping task: ${task.name}")
            return Result.success()
        }

        // NETWORK POLICY CHECK
        val networkResult = checkNetworkPolicy(task)
        if (!networkResult.canProceed) {
            Log.w("AutoAgent", "Network not available for task: ${task.name}")
            repository.saveLog(ExecutionLogEntity(
                taskId = taskId,
                taskName = task.name,
                startTime = System.currentTimeMillis(),
                endTime = System.currentTimeMillis(),
                status = RunStatus.SKIPPED.name,
                stepsCompleted = 0,
                totalSteps = task.steps.size,
                failureReason = "Network unavailable: ${networkResult.reason}",
                networkUsed = null,
                stepLogsJson = "[]"
            ))
            return Result.success()
        }

        // EXECUTE TASK
        val logId = repository.saveLog(ExecutionLogEntity(
            taskId = taskId,
            taskName = task.name,
            startTime = System.currentTimeMillis(),
            endTime = null,
            status = RunStatus.RUNNING.name,
            stepsCompleted = 0,
            totalSteps = task.steps.size,
            failureReason = null,
            networkUsed = networkResult.networkType,
            stepLogsJson = "[]"
        ))

        val service = AutoAgentAccessibilityService.getInstance()
        if (service == null) {
            Log.e("AutoAgent", "Accessibility service not running!")
            repository.updateLog(logId, RunStatus.FAILED, 0, "Accessibility Service ON nahi hai")
            return Result.retry()
        }

        val stepLogs = mutableListOf<StepLog>()
        val status = service.executeSteps(task.steps) { stepLog ->
            stepLogs.add(stepLog)
        }

        repository.updateLog(
            logId = logId,
            status = status,
            stepsCompleted = stepLogs.count { it.success },
            failureReason = if (status == RunStatus.FAILED)
                stepLogs.firstOrNull { !it.success }?.errorMessage else null
        )

        repository.updateTaskLastRun(taskId, System.currentTimeMillis(), status)

        return if (status == RunStatus.SUCCESS) Result.success() else Result.retry()
    }

    // =========================================
    // NETWORK POLICY CHECKER
    // =========================================
    private suspend fun checkNetworkPolicy(task: AgentTask): NetworkCheckResult {
        val needsInternet = task.steps.any {
            it.type == ActionType.OPEN_URL
        }

        if (!needsInternet || task.networkPolicy == NetworkPolicy.NO_INTERNET ||
            task.networkPolicy == NetworkPolicy.OFFLINE_ONLY) {
            return NetworkCheckResult(true, "offline", null)
        }

        val cm = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // Check current connectivity
        val network = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(network)
        val hasInternet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val isMobile = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true

        // Already on WiFi
        if (hasInternet && isWifi) {
            return NetworkCheckResult(true, "wifi", null)
        }

        // WiFi only policy
        if (task.networkPolicy == NetworkPolicy.WIFI_ONLY) {
            if (isWifi && hasInternet) return NetworkCheckResult(true, "wifi", null)
            // Try to enable WiFi
            tryEnableWifi()
            delay(5000)
            val retryNetwork = cm.activeNetwork
            val retryCaps = cm.getNetworkCapabilities(retryNetwork)
            val retryWifi = retryCaps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            val retryInternet = retryCaps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            return if (retryWifi && retryInternet) {
                NetworkCheckResult(true, "wifi", null)
            } else {
                NetworkCheckResult(false, null, "Wi-Fi available nahi hai aur Mobile Data is task ke liye allowed nahi")
            }
        }

        // Mobile data allowed
        if (task.networkPolicy == NetworkPolicy.MOBILE_DATA_ALLOWED && task.mobileDataAllowed) {
            if (hasInternet && isMobile) return NetworkCheckResult(true, "mobile", null)
        }

        // WiFi preferred — try wifi first, then mobile
        if (task.networkPolicy == NetworkPolicy.WIFI_PREFERRED) {
            if (isWifi && hasInternet) return NetworkCheckResult(true, "wifi", null)
            tryEnableWifi()
            delay(5000)
            if (hasInternet) return NetworkCheckResult(true, "wifi", null)
            if (task.mobileDataAllowed && isMobile) return NetworkCheckResult(true, "mobile", null)
        }

        return if (hasInternet) {
            NetworkCheckResult(true, if (isWifi) "wifi" else "mobile", null)
        } else {
            NetworkCheckResult(false, null, "Internet available nahi hai")
        }
    }

    private fun tryEnableWifi() {
        try {
            @Suppress("DEPRECATION")
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            if (!wifiManager.isWifiEnabled) {
                Log.i("AutoAgent", "Requesting WiFi enable...")
                // Note: Direct enable not allowed in API 29+, user must enable manually
                // We log this and continue checking
            }
        } catch (e: Exception) {
            Log.e("AutoAgent", "WiFi enable failed: ${e.message}")
        }
    }
}

data class NetworkCheckResult(
    val canProceed: Boolean,
    val networkType: String?,
    val reason: String?
)
