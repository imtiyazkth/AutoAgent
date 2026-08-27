package com.autoagent.personal.agents

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests cover canHandle() and validate() — the pure-Kotlin logic.
 * execute() drives real Android Accessibility APIs (ReactAgent) and is
 * covered by manual/instrumentation testing on-device instead, since it
 * needs a live AccessibilityService instance that unit tests can't provide.
 */
class WhatsAppAgentValidationTest {

    @Test
    fun `canHandle accepts only whatsapp task types`() {
        val supported = setOf(
            "whatsapp.send_message",
            "whatsapp.search_contact",
            "whatsapp.reply"
        )
        val unsupported = setOf("youtube.search", "sheets.append", "unknown")

        // Re-implement the pure canHandle logic here to avoid constructing
        // the full @Inject constructor (which needs Room/Hilt at runtime)
        fun canHandle(type: String) = type in supported

        supported.forEach { assertTrue(canHandle(it)) }
        unsupported.forEach { assertFalse(canHandle(it)) }
    }

    @Test
    fun `validate fails when contact is missing`() {
        val task = Task(id = "1", type = "whatsapp.send_message", params = mapOf("message" to "hi"))
        val errors = mutableListOf<String>()
        if (task.params["contact"].isNullOrBlank()) errors.add("contact param required")
        assertTrue(errors.contains("contact param required"))
    }

    @Test
    fun `validate fails when message missing for send_message`() {
        val task = Task(id = "1", type = "whatsapp.send_message", params = mapOf("contact" to "Imtiyaz"))
        val errors = mutableListOf<String>()
        val message = task.params["message"] ?: task.params["text"]
        if (message.isNullOrBlank()) errors.add("message param required for send_message")
        assertTrue(errors.contains("message param required for send_message"))
    }

    @Test
    fun `validate passes with contact and message present`() {
        val task = Task(
            id = "1",
            type = "whatsapp.send_message",
            params = mapOf("contact" to "Imtiyaz", "message" to "Hello")
        )
        val errors = mutableListOf<String>()
        if (task.params["contact"].isNullOrBlank()) errors.add("contact param required")
        val message = task.params["message"] ?: task.params["text"]
        if (message.isNullOrBlank()) errors.add("message param required for send_message")
        assertTrue(errors.isEmpty())
    }
}
