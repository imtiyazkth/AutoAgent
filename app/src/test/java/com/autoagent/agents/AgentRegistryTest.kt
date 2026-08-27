package com.autoagent.personal.agents

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

private class EchoAgent : WorkerAgent {
    override fun canHandle(taskType: String) = taskType == "test.echo"
    override fun validate(task: Task) = ValidationResult(valid = true)
    override suspend fun execute(task: Task) =
        TaskResult(success = true, message = "echoed")
}

private class SilentAgent : WorkerAgent {
    override fun canHandle(taskType: String) = taskType == "test.silent"
    override fun validate(task: Task) = ValidationResult(valid = true)
    override suspend fun execute(task: Task) =
        TaskResult(success = true, message = "silent")
}

class AgentRegistryTest {

    @Test
    fun `findAgent returns the matching agent`() {
        val registry = AgentRegistry(setOf(EchoAgent(), SilentAgent()))
        val found = registry.findAgent("test.echo")
        assertNotNull(found)
        assertTrue(found is EchoAgent)
    }

    @Test
    fun `findAgent returns null when no agent matches`() {
        val registry = AgentRegistry(setOf(EchoAgent()))
        assertNull(registry.findAgent("unknown.type"))
    }

    @Test
    fun `allAgents returns every registered agent`() {
        val registry = AgentRegistry(setOf(EchoAgent(), SilentAgent()))
        assertEquals(2, registry.allAgents().size)
    }

    @Test
    fun `dispatch through found agent executes correctly`() = runBlocking {
        val registry = AgentRegistry(setOf(EchoAgent()))
        val agent = registry.findAgent("test.echo")
        assertNotNull(agent)
        val result = agent!!.execute(Task(id = "1", type = "test.echo"))
        assertTrue(result.success)
        assertEquals("echoed", result.message)
    }
}
