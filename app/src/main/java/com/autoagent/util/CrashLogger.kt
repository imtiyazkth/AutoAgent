package com.autoagent.personal.util

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object CrashLogger {
    private const val TAG = "CrashLogger"
    private const val MAX_LOG_FILES = 20
    private lateinit var logDir: File

    fun init(context: Context) {
        logDir = File(context.filesDir, "crash_logs")
        logDir.mkdirs()

        // Global uncaught exception handler — catches crashes
        // that slip through coroutine handlers
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                saveCrashLog(throwable, thread.name, "UNCAUGHT")
                Log.e(TAG, "UNCAUGHT on ${thread.name}", throwable)
            } catch (e: Exception) {
                Log.e(TAG, "CrashLogger itself failed", e)
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
        Log.d(TAG, "CrashLogger initialized, dir=${logDir.absolutePath}")
    }

    fun logException(tag: String, message: String, throwable: Throwable?,
                     currentScreen: String = "Unknown",
                     currentTask: String? = null) {
        Log.e(tag, message, throwable)
        try {
            saveCrashLog(throwable, tag, "HANDLED", message, currentScreen, currentTask)
        } catch (e: Exception) {
            Log.e(TAG, "saveCrashLog failed", e)
        }
    }

    private fun saveCrashLog(
        throwable: Throwable?,
        thread: String,
        type: String,
        message: String = "",
        screen: String = "Unknown",
        task: String? = null
    ) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
            .format(Date())
        val file = File(logDir, "crash_${timestamp}.txt")

        val sb = StringBuilder()
        sb.appendLine("=== AutoAgent Crash Log ===")
        sb.appendLine("Time     : $timestamp")
        sb.appendLine("Type     : $type")
        sb.appendLine("Thread   : $thread")
        sb.appendLine("Screen   : $screen")
        sb.appendLine("Task     : ${task ?: "N/A"}")
        sb.appendLine("Message  : $message")
        sb.appendLine()
        sb.appendLine("--- Device Info ---")
        sb.appendLine("Model    : ${Build.MANUFACTURER} ${Build.MODEL}")
        sb.appendLine("Android  : ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        sb.appendLine("Brand    : ${Build.BRAND}")
        sb.appendLine()
        if (throwable != null) {
            sb.appendLine("--- Exception ---")
            sb.appendLine("${throwable.javaClass.name}: ${throwable.message}")
            sb.appendLine()
            sb.appendLine("--- Stack Trace ---")
            sb.appendLine(throwable.stackTraceToString())
            var cause = throwable.cause
            while (cause != null) {
                sb.appendLine("--- Caused By ---")
                sb.appendLine("${cause.javaClass.name}: ${cause.message}")
                sb.appendLine(cause.stackTraceToString())
                cause = cause.cause
            }
        }
        file.writeText(sb.toString())
        pruneOldLogs()
    }

    fun getRecentLogs(): List<File> =
        logDir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()

    fun getLatestLog(): String? =
        getRecentLogs().firstOrNull()?.readText()

    fun clearLogs() = logDir.listFiles()?.forEach { it.delete() }

    private fun pruneOldLogs() {
        val files = logDir.listFiles()?.sortedByDescending { it.lastModified() } ?: return
        files.drop(MAX_LOG_FILES).forEach { it.delete() }
    }
}
