package com.autoagent.personal.agent

data class ScreenState(
    val pkg: String,
    val texts: List<String>,
    val clickable: List<String>,
    val hasInput: Boolean,
    val scrollable: Boolean
) {
    fun hasText(q: String) = texts.any { it.contains(q, ignoreCase = true) }
    fun hasClickable(q: String) = clickable.any { it.contains(q, ignoreCase = true) }
    val topText: String get() = texts.take(5).joinToString(" ")
}
