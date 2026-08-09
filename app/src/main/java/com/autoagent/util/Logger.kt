package com.autoagent.util

import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

object L {
    private val logs = Collections.synchronizedList(mutableListOf<String>())
    private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    fun d(tag: String, msg: String) {
        val entry = "[${fmt.format(Date())}] [$tag] $msg"
        Log.d("AutoAgent", entry)
        logs.add(entry)
        if (logs.size > 500) logs.removeAt(0)
    }

    fun e(tag: String, msg: String, t: Throwable? = null) {
        val entry = "[${fmt.format(Date())}] [ERROR][$tag] $msg ${t?.stackTraceToString() ?: ""}"
        Log.e("AutoAgent", entry, t)
        logs.add(entry)
    }

    fun getLogs(): List<String> = logs.toList()
    fun clear() = logs.clear()
}
