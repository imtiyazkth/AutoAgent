package com.autoagent.personal.di

import android.content.Context
import androidx.room.Room
import com.autoagent.personal.actions.AccessibilityExecutor
import com.autoagent.personal.actions.ActionEngine
import com.autoagent.personal.agent.AgentController
import com.autoagent.personal.ai.EntityResolver
import com.autoagent.personal.ai.GoalDecomposer
import com.autoagent.personal.ai.IntentEngine
import com.autoagent.personal.ai.RecoveryEngine
import com.autoagent.personal.ai.VerificationEngine
import com.autoagent.personal.data.db.*
import com.autoagent.personal.data.db.AutoAgentDatabase.Companion.MIGRATION_1_2
import com.autoagent.personal.data.db.AutoAgentDatabase.Companion.MIGRATION_2_3
import com.autoagent.personal.engine.ConversationEngine
import com.autoagent.personal.learning.ExperienceRecorder
import com.autoagent.personal.perception.AccessibilityTreeParser
import com.autoagent.personal.perception.ScreenObserver
import com.autoagent.personal.safety.RiskEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): AutoAgentDatabase =
        Room.databaseBuilder(ctx, AutoAgentDatabase::class.java, "autoagent.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .fallbackToDestructiveMigration()
            .build()

    @Provides @Singleton
    fun provideTaskDao(db: AutoAgentDatabase): TaskDao = db.taskDao()

    @Provides @Singleton
    fun provideExecutionLogDao(db: AutoAgentDatabase): ExecutionLogDao = db.executionLogDao()

    @Provides @Singleton
    fun provideAccessibilityTreeParser() = AccessibilityTreeParser()

    @Provides @Singleton
    fun provideScreenObserver(parser: AccessibilityTreeParser) = ScreenObserver(parser)

    @Provides @Singleton
    fun provideAccessibilityExecutor() = AccessibilityExecutor()

    @Provides @Singleton
    fun provideActionEngine(
        @ApplicationContext ctx: Context,
        executor: AccessibilityExecutor,
        observer: ScreenObserver
    ) = ActionEngine(ctx, executor, observer)

    @Provides @Singleton
    fun provideEntityResolver() = EntityResolver()

    @Provides @Singleton
    fun provideIntentEngine(er: EntityResolver) = IntentEngine(er)

    @Provides @Singleton
    fun provideGoalDecomposer() = GoalDecomposer()

    @Provides @Singleton
    fun provideRiskEngine() = RiskEngine()

    @Provides @Singleton
    fun provideExperienceRecorder() = ExperienceRecorder()

    @Provides @Singleton
    fun provideVerificationEngine(observer: ScreenObserver) = VerificationEngine(observer)

    @Provides @Singleton
    fun provideRecoveryEngine(
        actionEngine: ActionEngine,
        observer: ScreenObserver
    ) = RecoveryEngine(actionEngine, observer)

    @Provides @Singleton
    fun provideConversationEngine() = ConversationEngine()


    @Provides @Singleton
    fun provideAgentController(
        intentEngine: IntentEngine,
        goalDecomposer: GoalDecomposer,
        riskEngine: RiskEngine,
        actionEngine: ActionEngine,
        screenObserver: ScreenObserver,
        verificationEngine: VerificationEngine,
        recoveryEngine: RecoveryEngine,
        conversationEngine: ConversationEngine,
        experienceRecorder: ExperienceRecorder
    ) = AgentController(
        intentEngine, goalDecomposer, riskEngine, actionEngine,
        screenObserver, verificationEngine, recoveryEngine,
        conversationEngine, experienceRecorder
    )
}
