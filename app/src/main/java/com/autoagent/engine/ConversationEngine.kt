package com.autoagent.personal.engine

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConversationEngine @Inject constructor() {

    data class ConversationContext(
        val lastApp: String? = null,
        val lastAppName: String? = null,
        val lastQuery: String? = null,
        val lastContact: String? = null,
        val lastGoal: String? = null,
        val timestamp: Long = System.currentTimeMillis()
    )

    private var ctx = ConversationContext()

    fun updateContext(
        app: String? = null, appName: String? = null,
        query: String? = null, contact: String? = null, goal: String? = null
    ) {
        ctx = ctx.copy(
            lastApp     = app ?: ctx.lastApp,
            lastAppName = appName ?: ctx.lastAppName,
            lastQuery   = query ?: ctx.lastQuery,
            lastContact = contact ?: ctx.lastContact,
            lastGoal    = goal ?: ctx.lastGoal,
            timestamp   = System.currentTimeMillis()
        )
    }

    fun getContext() = ctx

    fun resolveGoal(input: String): String {
        val g = input.lowercase().trim()
        val lastApp = ctx.lastApp ?: return input
        val lastAppName = ctx.lastAppName ?: lastApp

        return when {
            g.hasAny("band karo","close karo","rok do","stop karo") && g.length < 25 ->
                "close $lastAppName"
            g.hasAny("dusra search","aur search","dobara search") ->
                "$lastAppName search ${ctx.lastQuery ?: input}"
            g.hasAny("use message","isko message","usse bolo") && ctx.lastContact != null ->
                "whatsapp ${ctx.lastContact} ko message karo"
            else -> input
        }
    }

    fun clear() { ctx = ConversationContext() }

    private fun String.hasAny(vararg words: String) = words.any { this.contains(it) }
}
