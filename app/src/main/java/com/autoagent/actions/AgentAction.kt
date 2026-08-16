package com.autoagent.personal.actions

sealed interface AgentAction {
    data class LaunchApp(val packageName: String, val appName: String) : AgentAction
    data class OpenUrl(val url: String) : AgentAction
    data class Tap(val target: UiTarget) : AgentAction
    data class LongPress(val target: UiTarget, val durationMs: Long = 1000) : AgentAction
    data class TypeText(val text: String, val clearFirst: Boolean = true) : AgentAction
    data class PasteText(val text: String) : AgentAction
    data class Scroll(val direction: ScrollDirection, val amount: Float = 0.5f) : AgentAction
    data class Swipe(val startX: Float, val startY: Float, val endX: Float, val endY: Float) : AgentAction
    object Back : AgentAction
    object Home : AgentAction
    object Recents : AgentAction
    data class Wait(val durationMs: Long) : AgentAction
    object ReadScreen : AgentAction
    object TakeScreenshot : AgentAction
    data class SelectElement(val target: UiTarget) : AgentAction
    object OpenNotifications : AgentAction
    object PressSearch : AgentAction
    data class RequestUserConfirmation(val reason: String) : AgentAction
    object LockScreen : AgentAction
    object AnswerCall : AgentAction
    object DeclineCall : AgentAction
    object ToggleSpeakerphone : AgentAction
    data class SetVolume(val direction: VolumeDirection) : AgentAction
    object ToggleWifi : AgentAction
    object ToggleBluetooth : AgentAction
    object EmergencyStop : AgentAction
}

enum class ScrollDirection { UP, DOWN, LEFT, RIGHT }
enum class VolumeDirection { UP, DOWN, MUTE }
