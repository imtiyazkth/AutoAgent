package com.autoagent.personal.di

import com.autoagent.personal.agents.WhatsAppAgent
import com.autoagent.personal.agents.WorkerAgent
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * Binds every WorkerAgent implementation into one Set<WorkerAgent> that
 * AgentRegistry consumes. Add one @Binds @IntoSet method per new agent.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AgentsModule {

    @Binds
    @IntoSet
    abstract fun bindWhatsAppAgent(agent: WhatsAppAgent): WorkerAgent
}
