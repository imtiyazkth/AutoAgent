package com.autoagent.personal.data.domain.model

data class Task(
    val id: Long = 0,
    val name: String = "",
    val appName: String = "",
    val appPackage: String = "",
    val triggerType: TriggerType = TriggerType.MANUAL,
    val scheduledTime: Long? = null,
    val isEnabled: Boolean = true
)
