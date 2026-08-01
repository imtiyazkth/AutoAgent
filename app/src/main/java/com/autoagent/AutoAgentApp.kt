package com.autoagent

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltAndroidApp
class AutoAgentApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        setupCrashHandler()
    }

    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
                val logFile = File(filesDir, "crash_logs/crash_$timestamp.txt")
                logFile.parentFile?.mkdirs()
                logFile.writeText("Time: $timestamp\nException: ${throwable::class.java.simpleName}\nMessage: ${throwable.message}\n\n$sw")
                Log.e("AutoAgent_CRASH", "Crash on ${thread.name}", throwable)
            } catch (e: Exception) { Log.e("AutoAgent", "Could not save crash", e) }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
}
