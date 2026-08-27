package com.autoagent.personal.agents

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/**
 * A trivial WorkerAgent used purely to prove the interface contract works
 * end-to-end before any real automation logic is wired in.
 */
private class DummyAgent : WorkerAgent {
    override fun canHandle(taskType: String): Boolean = taskType == "test.echo"

    override fun validate(task: Task): ValidationResult {
        val hasMessage = task.params["message"]?.isNotBlank() == true
        return if (hasMessage) ValidationResult(valid = true)
        else ValidationResult(valid = false, errors = listOf("message param required"))
    }

    override suspend fun execute(task: Task): TaskResult {
        val msg = task.params["message"] ?: ""
        return TaskResult(success = true, message = "echo: $msg")
    }
}

class DummyAgentTest {

    private val agent = DummyAgent()

    @Test
    fun `canHandle matches only its own task type`() {
        assertTrue(agent.canHandle("test.echo"))
        assertFalse(agent.canHandle("whatsapp.send_message"))
    }

    @Test
    fun `validate fails when message param missing`() {
        val task = Task(id = "t1", type = "test.echo", params = emptyMap())
        val result = agent.validate(task)
        assertFalse(result.valid)
        assertTrue(result.errors.isNotEmpty())
    }

    @Test
    fun `execute returns success and echoes message`() = runBlocking {
        val task = Task(id = "t1", type = "test.echo", params = mapOf("message" to "hello"))
        val result = agent.execute(task)
        assertTrue(result.success)
        assertEquals("echo: hello", result.message)
    }
}
