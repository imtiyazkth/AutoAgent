package com.autoagent.personal.memory

import android.content.Context
import android.content.SharedPreferences
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryEngine @Inject constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("autoagent_memory", Context.MODE_PRIVATE)

    fun saveLastCommand(text: String) {
        prefs.edit().putString("last_command", text).apply()
    }

    fun getLastCommand(): String? = prefs.getString("last_command", null)

    fun clear() = prefs.edit().clear().apply()
}
