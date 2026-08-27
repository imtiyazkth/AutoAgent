package com.autoagent.personal.di

import com.autoagent.personal.agents.WorkerAgent
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.ElementsIntoSet

/**
 * Binds every WorkerAgent implementation into one Set<WorkerAgent> that
 * AgentRegistry consumes. Real agents (WhatsAppAgent, YouTubeAgent, ...)
 * will be added here one at a time as they're built — using @IntoSet on
 * an individual @Provides/@Binds method, or appended to this base set.
 *
 * Empty for now: no agents are registered yet, so AgentRegistry.findAgent()
 * always returns null. This lets the DI graph compile and be tested before
 * any real automation logic exists.
 */
@Module
@InstallIn(SingletonComponent::class)
object AgentsModule {

    @Provides
    @ElementsIntoSet
    fun provideEmptyWorkerAgentSet(): Set<WorkerAgent> = emptySet()
}
