package com.autoagent.personal.ai

import com.autoagent.personal.actions.AgentAction
import com.autoagent.personal.actions.UiRole
import com.autoagent.personal.actions.UiTarget
import javax.inject.Inject
import javax.inject.Singleton

data class AgentGoal(
    val id: String = java.util.UUID.randomUUID().toString(),
    val description: String,
    val subGoals: List<SubGoal> = emptyList(),
    val targetPkg: String? = null,
    val expectedFinalState: String? = null
)

data class SubGoal(
    val id: String = java.util.UUID.randomUUID().toString(),
    val description: String,
    val action: AgentAction,
    val expectedOutcome: String? = null,
    val verificationText: String? = null,
    val critical: Boolean = false,
    val retryable: Boolean = true,
    val waitAfterMs: Long = 800
)

@Singleton
class GoalDecomposer @Inject constructor() {

    fun decompose(cmd: ParsedCommand): AgentGoal {
        return when (cmd.intent) {
            ParsedIntent.SEARCH_AND_PLAY_MEDIA -> decomposeMedia(cmd)
            ParsedIntent.SEND_MESSAGE          -> decomposeMessage(cmd)
            ParsedIntent.OPEN_APP              -> decomposeOpenApp(cmd)
            ParsedIntent.LOCK_SCREEN     -> simple("Screen lock", AgentAction.LockScreen)
            ParsedIntent.ANSWER_CALL     -> simple("Call receive", AgentAction.AnswerCall)
            ParsedIntent.DECLINE_CALL    -> simple("Call decline", AgentAction.DeclineCall)
            ParsedIntent.TOGGLE_SPEAKER  -> simple("Speaker toggle", AgentAction.ToggleSpeakerphone)
            ParsedIntent.TAKE_SCREENSHOT -> simple("Screenshot", AgentAction.TakeScreenshot)
            ParsedIntent.TOGGLE_WIFI     -> simple("WiFi settings", AgentAction.ToggleWifi)
            ParsedIntent.TOGGLE_BLUETOOTH -> simple("BT settings", AgentAction.ToggleBluetooth)
            ParsedIntent.GO_HOME         -> simple("Home", AgentAction.Home)
            ParsedIntent.CLOSE_APP       -> simple("Close app", AgentAction.Home)
            ParsedIntent.STOP_AGENT      -> simple("Stop", AgentAction.EmergencyStop)
            else -> AgentGoal(description = "Unknown: ${cmd.raw.take(40)}", subGoals = emptyList())
        }
    }

    private fun decomposeMedia(cmd: ParsedCommand): AgentGoal {
        val pkg = cmd.targetPkg ?: "com.google.android.youtube"
        val appName = cmd.targetApp ?: "YouTube"
        val query = cmd.query()
        val raw = cmd.raw.lowercase()
        val shouldPlay = raw.hasAny("play","chalao","bajao","lagao","suno","first","pehla","dekhna")

        val steps = mutableListOf<SubGoal>()
        steps += SubGoal(
            description = "$appName launch",
            action = AgentAction.LaunchApp(pkg, appName),
            expectedOutcome = pkg, critical = true, waitAfterMs = 3500
        )
        steps += SubGoal(
            description = "Wait load",
            action = AgentAction.Wait(1500),
            waitAfterMs = 0
        )

        if (query != null) {
            val searchTarget = if (pkg.contains("youtube"))
                UiTarget(primaryText = "Search YouTube", contentDescription = "Search YouTube", role = UiRole.SEARCH_BAR)
            else UiTarget(primaryText = "Search", role = UiRole.SEARCH_BAR)

            steps += SubGoal(description = "Search bar tap", action = AgentAction.Tap(searchTarget), waitAfterMs = 1000)
            steps += SubGoal(
                description = "Type: $query",
                action = AgentAction.TypeText(query, clearFirst = true),
                expectedOutcome = query, critical = true, waitAfterMs = 600
            )
            steps += SubGoal(description = "Search execute", action = AgentAction.PressSearch, waitAfterMs = 3000)

            if (shouldPlay) {
                steps += SubGoal(description = "Wait results", action = AgentAction.Wait(1000), waitAfterMs = 0)
                val firstWord = query.split(" ").firstOrNull { it.length > 2 }
                steps += SubGoal(
                    description = "Pehla result play",
                    action = AgentAction.Tap(UiTarget(primaryText = firstWord, role = UiRole.LIST_ITEM)),
                    waitAfterMs = 1000
                )
            }
        }

        return AgentGoal(
            description = "$appName: $query",
            subGoals = steps,
            targetPkg = pkg,
            expectedFinalState = if (shouldPlay) "Media playing" else "Search results shown"
        )
    }

    private fun decomposeMessage(cmd: ParsedCommand): AgentGoal {
        val pkg = cmd.targetPkg ?: "com.whatsapp"
        val appName = cmd.targetApp ?: "WhatsApp"
        val contact = cmd.contact()
        val message = cmd.message()

        val steps = mutableListOf<SubGoal>()
        steps += SubGoal(
            description = "$appName launch",
            action = AgentAction.LaunchApp(pkg, appName),
            expectedOutcome = pkg, critical = true, waitAfterMs = 3000
        )

        if (contact != null) {
            steps += SubGoal(
                description = "Search bar",
                action = AgentAction.Tap(UiTarget(primaryText = "Search", role = UiRole.SEARCH_BAR)),
                waitAfterMs = 800
            )
            steps += SubGoal(
                description = "Contact: $contact",
                action = AgentAction.TypeText(contact, clearFirst = true),
                expectedOutcome = contact, critical = true, waitAfterMs = 1800
            )
            steps += SubGoal(
                description = "Contact tap",
                action = AgentAction.Tap(UiTarget(primaryText = contact)),
                critical = true, waitAfterMs = 1500
            )
        }

        if (message != null) {
            steps += SubGoal(
                description = "Message box",
                action = AgentAction.Tap(UiTarget(primaryText = "Type a message",
                    hint = "Type a message", role = UiRole.TEXT_FIELD)),
                waitAfterMs = 800
            )
            steps += SubGoal(
                description = "Message: ${message.take(20)}",
                action = AgentAction.TypeText(message, clearFirst = false),
                expectedOutcome = message, critical = true, waitAfterMs = 600
            )
            steps += SubGoal(
                description = "Send",
                action = AgentAction.Tap(UiTarget(role = UiRole.SEND_BUTTON, primaryText = "Send")),
                critical = true, waitAfterMs = 500
            )
        }

        return AgentGoal(
            description = "$appName: $contact",
            subGoals = steps,
            targetPkg = pkg,
            expectedFinalState = "Message sent"
        )
    }

    private fun decomposeOpenApp(cmd: ParsedCommand): AgentGoal {
        val pkg = cmd.targetPkg ?: return AgentGoal("Unknown app", emptyList())
        val name = cmd.targetApp ?: "App"
        return AgentGoal(
            description = "$name open",
            subGoals = listOf(SubGoal(
                description = "$name launch",
                action = AgentAction.LaunchApp(pkg, name),
                expectedOutcome = pkg, critical = true, waitAfterMs = 3000
            )),
            targetPkg = pkg
        )
    }

    private fun simple(desc: String, action: AgentAction) = AgentGoal(
        description = desc,
        subGoals = listOf(SubGoal(description = desc, action = action, critical = true))
    )

    private fun String.hasAny(vararg w: String) = w.any { this.contains(it) }
}
