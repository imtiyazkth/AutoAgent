package com.autoagent.personal.service.scheduler

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.autoagent.personal.data.db.ExecutionLogEntity
import com.autoagent.personal.data.repository.AgentRepository
import com.autoagent.personal.domain.model.*
import com.autoagent.personal.service.accessibility.AutoAgentAccessibilityService
import com.autoagent.personal.util.GsonHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.Calendar
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
        private const val MAX_RETRIES = 3

        fun scheduleTask(context: Context, task: AgentTask) {
            val data = workDataOf(KEY_TASK_ID to task.id)
            val wm = WorkManager.getInstance(context)

            when (task.triggerType) {
                TriggerType.ONE_TIME -> {
                    val delay = calculateInitialDelay(task.triggerTime)
                    val request = OneTimeWorkRequestBuilder<TaskExecutorWorker>()
                        .setInputData(data)
                        .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                        .build()
                    wm.enqueue(request)
                }

                TriggerType.DAILY -> {
                    // FIXED: use initialDelay so first fire is at the correct clock time
                    val delay = calculateInitialDelay(task.triggerTime)
                    val request = PeriodicWorkRequestBuilder<TaskExecutorWorker>(
                        24, TimeUnit.HOURS
                    )
                        .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                        .setInputData(data)
                        .build()
                    wm.enqueueUniquePeriodicWork(
                        "task_${task.id}",
                        ExistingPeriodicWorkPolicy.UPDATE,
                        request
                    )
                }

                TriggerType.WEEKLY -> {
                    // FIXED: 7 days, not 24 hours
                    val delay = calculateInitialDelay(task.triggerTime)
                    val request = PeriodicWorkRequestBuilder<TaskExecutorWorker>(
                        7, TimeUnit.DAYS
                    )
                        .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                        .setInputData(data)
                        .build()
                    wm.enqueueUniquePeriodicWork(
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
                    wm.enqueueUniquePeriodicWork(
                        "task_${task.id}",
                        ExistingPeriodicWorkPolicy.UPDATE,
                        request
                    )
                }

                else -> { /* MANUAL or ON_BOOT — not scheduled here */ }
            }
        }

        fun cancelTask(context: Context, taskId: Long) {
            WorkManager.getInstance(context).cancelUniqueWork("task_$taskId")
        }

        /**
         * Calculate milliseconds until the next occurrence of triggerTime (HH:mm).
         * If time is null or already passed today, defaults to immediate (0).
         */
        private fun calculateInitialDelay(triggerTime: String?): Long {
            if (triggerTime.isNullOrBlank()) return 0L
            return try {
                val parts = triggerTime.split(":")
                val targetHour = parts[0].toInt()
                val targetMin = if (parts.size > 1) parts[1].toInt() else 0
                val now = Calendar.getInstance()
                val target = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, targetHour)
                    set(Calendar.MINUTE, targetMin)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                // If time already passed today, schedule for tomorrow
                if (target.before(now)) target.add(Calendar.DAY_OF_YEAR, 1)
                (target.timeInMillis - now.timeInMillis).coerceAtLeast(0L)
            } catch (e: Exception) { 0L }
        }
    }

    override suspend fun doWork(): Result {
        val taskId = inputData.getLong(KEY_TASK_ID, -1)
        if (taskId == -1L) return Result.failure()

        val taskEntity = repository.getTask(taskId) ?: return Result.failure()
        val task = gsonHelper.entityToTask(taskEntity)

        if (!task.isEnabled) return Result.success()

        // Emergency stop check
        if (AutoAgentAccessibilityService.emergencyStop.value) {
            Log.w("AutoAgent", "Emergency stop — skipping task: ${task.name}")
            return Result.success()
        }

        // Network policy check
        val networkResult = checkNetworkPolicy(task)
        if (!networkResult.canProceed) {
            Log.w("AutoAgent", "Network unavailable for task: ${task.name}")
            repository.saveLog(ExecutionLogEntity(
                taskId = taskId, taskName = task.name,
                startTime = System.currentTimeMillis(),
                endTime = System.currentTimeMillis(),
                status = RunStatus.SKIPPED.name,
                stepsCompleted = 0, totalSteps = task.steps.size,
                failureReason = "Network: ${networkResult.reason}",
                networkUsed = null, stepLogsJson = "[]"
            ))
            return Result.success()
        }

        val logId = repository.saveLog(ExecutionLogEntity(
            taskId = taskId, taskName = task.name,
            startTime = System.currentTimeMillis(), endTime = null,
            status = RunStatus.RUNNING.name,
            stepsCompleted = 0, totalSteps = task.steps.size,
            failureReason = null, networkUsed = networkResult.networkType,
            stepLogsJson = "[]"
        ))

        val service = AutoAgentAccessibilityService.getInstance()
        if (service == null) {
            Log.e("AutoAgent", "Accessibility service not running!")
            repository.updateLog(logId, RunStatus.FAILED, 0, "Accessibility Service ON nahi hai")
            // FIXED: only retry if we haven't exceeded limit
            return if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }

        val stepLogs = mutableListOf<com.autoagent.domain.model.StepLog>()
        val status = service.executeSteps(task.steps) { stepLog -> stepLogs.add(stepLog) }

        repository.updateLog(
            logId = logId,
            status = status,
            stepsCompleted = stepLogs.count { it.success },
            failureReason = if (status == RunStatus.FAILED)
                stepLogs.firstOrNull { !it.success }?.errorMessage else null
        )
        repository.updateTaskLastRun(taskId, System.currentTimeMillis(), status)

        return when (status) {
            RunStatus.SUCCESS   -> Result.success()
            RunStatus.CANCELLED -> Result.success() // user-initiated, don't retry
            RunStatus.FAILED    -> if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
            else                -> Result.failure()
        }
    }

    private fun checkNetworkPolicy(task: AgentTask): NetworkCheckResult {
        val needsInternet = task.steps.any { it.type == ActionType.OPEN_URL }

        if (!needsInternet || task.networkPolicy == NetworkPolicy.NO_INTERNET ||
            task.networkPolicy == NetworkPolicy.OFFLINE_ONLY) {
            return NetworkCheckResult(true, "offline", null)
        }

        val cm = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(network)
        val hasInternet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val isMobile = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true

        if (hasInternet && isWifi) return NetworkCheckResult(true, "wifi", null)

        if (task.networkPolicy == NetworkPolicy.WIFI_ONLY) {
            return if (isWifi && hasInternet) NetworkCheckResult(true, "wifi", null)
            else NetworkCheckResult(false, null, "Wi-Fi available nahi — task skip hua")
        }

        if (task.networkPolicy == NetworkPolicy.MOBILE_DATA_ALLOWED && task.mobileDataAllowed) {
            if (hasInternet && isMobile) return NetworkCheckResult(true, "mobile", null)
        }

        return if (hasInternet) NetworkCheckResult(true, if (isWifi) "wifi" else "mobile", null)
        else NetworkCheckResult(false, null, "Internet available nahi")
    }
}

data class NetworkCheckResult(
    val canProceed: Boolean,
    val networkType: String?,
    val reason: String?
)
