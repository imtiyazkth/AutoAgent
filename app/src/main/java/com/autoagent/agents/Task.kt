package com.autoagent.personal.agents

/**
 * Generic task passed to a WorkerAgent for execution.
 *
 * @param type   Dotted identifier the AgentRegistry uses to route this task,
 *               e.g. "whatsapp.send_message", "youtube.search".
 * @param params Free-form key/value parameters specific to [type].
 * @param schedule Optional ISO-8601 timestamp — if set and in the future,
 *               the executing worker should defer/reschedule instead of running now.
 */
data class Task(
    val id: String,
    val type: String,
    val params: Map<String, String?> = emptyMap(),
    val schedule: String? = null,
    val notes: String? = null
)
