package com.autoagent.personal.actions

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.util.Log
import com.autoagent.personal.perception.ScreenObserver
import com.autoagent.personal.service.accessibility.AutoAgentAccessibilityService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActionEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val executor: AccessibilityExecutor,
    private val screenObserver: ScreenObserver
) {
    private val TAG = "ActionEngine"

    suspend fun execute(action: AgentAction): ActionResult {
        val t0 = System.currentTimeMillis()
        Log.d(TAG, "Execute: ${action::class.simpleName}")
        return when (action) {

            is AgentAction.LaunchApp -> {
                try {
                    val intent = context.packageManager
                        .getLaunchIntentForPackage(action.packageName)
                        ?: return ActionResult.blocked("App not installed: ${action.packageName}")
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    context.startActivity(intent)
                    delay(3500)
                    val snap = screenObserver.observe()
                    val launched = snap.packageName == action.packageName
                    if (launched) ActionResult.success("${action.appName} launched",
                        evidence = ActionEvidence(afterText = snap.packageName),
                        ms = System.currentTimeMillis() - t0)
                    else ActionResult.failed("${action.appName} launch unconfirmed — got ${snap.packageName}",
                        recoverable = true, ms = System.currentTimeMillis() - t0)
                } catch (e: Exception) {
                    ActionResult.failed("Launch error: ${e.message}", ms = System.currentTimeMillis() - t0)
                }
            }

            is AgentAction.OpenUrl -> {
                try {
                    val i = Intent(Intent.ACTION_VIEW, Uri.parse(action.url))
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(i)
                    delay(2500)
                    ActionResult.success("URL opened", ms = System.currentTimeMillis() - t0)
                } catch (e: Exception) {
                    ActionResult.failed("URL error: ${e.message}", ms = System.currentTimeMillis() - t0)
                }
            }

            is AgentAction.Tap -> {
                val snap = screenObserver.observe()
                executor.tap(action.target, snap)
            }

            is AgentAction.TypeText -> {
                val snap = screenObserver.observe()
                executor.typeText(action.text, snap)
            }

            is AgentAction.PressSearch -> {
                val snap = screenObserver.observe()
                executor.pressSearch(snap)
            }

            is AgentAction.Scroll -> executor.scroll(action.direction)

            is AgentAction.Wait -> {
                delay(action.durationMs)
                ActionResult.success("Waited ${action.durationMs}ms", ms = action.durationMs)
            }

            is AgentAction.Back -> {
                svc()?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                ActionResult.success("Back", ms = System.currentTimeMillis() - t0)
            }

            is AgentAction.Home -> {
                svc()?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                ActionResult.success("Home", ms = System.currentTimeMillis() - t0)
            }

            is AgentAction.Recents -> {
                svc()?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
                ActionResult.success("Recents", ms = System.currentTimeMillis() - t0)
            }

            is AgentAction.TakeScreenshot -> {
                svc()?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
                ActionResult.success("Screenshot", ms = System.currentTimeMillis() - t0)
            }

            is AgentAction.OpenNotifications -> {
                svc()?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
                ActionResult.success("Notifications", ms = System.currentTimeMillis() - t0)
            }

            is AgentAction.LockScreen -> {
                svc()?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
                ActionResult.success("Locked", ms = System.currentTimeMillis() - t0)
            }

            is AgentAction.AnswerCall -> {
                val snap = screenObserver.observe()
                executor.tap(UiTarget.byText("Answer"), snap).let {
                    if (!it.success) executor.tap(UiTarget.byText("Accept"), snap) else it
                }
            }

            is AgentAction.DeclineCall -> {
                val snap = screenObserver.observe()
                executor.tap(UiTarget.byText("Decline"), snap).let {
                    if (!it.success) executor.tap(UiTarget.byText("Reject"), snap) else it
                }
            }

            is AgentAction.ToggleSpeakerphone -> {
                try {
                    val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    audio.isSpeakerphoneOn = !audio.isSpeakerphoneOn
                    ActionResult.success("Speaker toggled", ms = System.currentTimeMillis() - t0)
                } catch (e: Exception) {
                    ActionResult.failed("Speaker error: ${e.message}", ms = System.currentTimeMillis() - t0)
                }
            }

            is AgentAction.SetVolume -> {
                try {
                    val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    val dir = when (action.direction) {
                        VolumeDirection.UP   -> AudioManager.ADJUST_RAISE
                        VolumeDirection.DOWN -> AudioManager.ADJUST_LOWER
                        VolumeDirection.MUTE -> AudioManager.ADJUST_MUTE
                    }
                    audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, AudioManager.FLAG_SHOW_UI)
                    ActionResult.success("Volume ${action.direction}", ms = System.currentTimeMillis() - t0)
                } catch (e: Exception) {
                    ActionResult.failed("Volume error: ${e.message}", ms = System.currentTimeMillis() - t0)
                }
            }

            is AgentAction.ToggleWifi -> {
                try {
                    val i = Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(i)
                    ActionResult.success("WiFi settings", ms = System.currentTimeMillis() - t0)
                } catch (e: Exception) { ActionResult.failed("WiFi error", ms = System.currentTimeMillis() - t0) }
            }

            is AgentAction.ToggleBluetooth -> {
                try {
                    val i = Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(i)
                    ActionResult.success("BT settings", ms = System.currentTimeMillis() - t0)
                } catch (e: Exception) { ActionResult.failed("BT error", ms = System.currentTimeMillis() - t0) }
            }

            is AgentAction.RequestUserConfirmation ->
                ActionResult.waitingForUser(action.reason)

            is AgentAction.ReadScreen -> {
                val snap = screenObserver.observe()
                ActionResult.success("Screen: ${snap.topTexts.take(100)}",
                    evidence = ActionEvidence(beforeText = snap.topTexts),
                    ms = System.currentTimeMillis() - t0)
            }

            is AgentAction.LongPress -> {
                val snap = screenObserver.observe()
                executor.tap(action.target, snap)
            }

            is AgentAction.SelectElement -> {
                val snap = screenObserver.observe()
                executor.tap(action.target, snap)
            }

            is AgentAction.Swipe -> {
                try {
                    val path = android.graphics.Path().apply {
                        moveTo(action.startX, action.startY); lineTo(action.endX, action.endY)
                    }
                    val gesture = android.accessibilityservice.GestureDescription.Builder()
                        .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 400)).build()
                    svc()?.dispatchGesture(gesture, null, null)
                    ActionResult.success("Swiped", ms = System.currentTimeMillis() - t0)
                } catch (e: Exception) { ActionResult.failed("Swipe error", ms = System.currentTimeMillis() - t0) }
            }

            is AgentAction.EmergencyStop -> {
                AutoAgentAccessibilityService.getInstance()?.triggerEmergencyStop()
                ActionResult.success("Emergency stop", ms = System.currentTimeMillis() - t0)
            }

            is AgentAction.PasteText ->
                ActionResult.success("Paste attempted", ms = System.currentTimeMillis() - t0)
        }
    }

    private fun svc() = AutoAgentAccessibilityService.getInstance()
}
