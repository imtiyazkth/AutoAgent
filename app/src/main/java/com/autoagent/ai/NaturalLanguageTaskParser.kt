package com.autoagent.ai

import com.autoagent.domain.model.ActionType
import com.autoagent.domain.model.TaskStep
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NaturalLanguageTaskParser @Inject constructor() {

    data class ParsedTask(
        val taskName: String,
        val targetApp: String?,
        val targetAppName: String?,
        val message: String?,
        val url: String?,
        val buttonToPress: String?,
        val recipient: String?,
        val scheduledHour: Int?,
        val scheduledMinute: Int?,
        val steps: List<TaskStep>,
        val confidence: Float,
        val explanation: String
    )

    private val appMap = mapOf(
        "whatsapp" to Pair("com.whatsapp", "WhatsApp"),
        "wa" to Pair("com.whatsapp", "WhatsApp"),
        "facebook" to Pair("com.facebook.katana", "Facebook"),
        "fb" to Pair("com.facebook.katana", "Facebook"),
        "messenger" to Pair("com.facebook.orca", "Messenger"),
        "instagram" to Pair("com.instagram.android", "Instagram"),
        "telegram" to Pair("org.telegram.messenger", "Telegram"),
        "gmail" to Pair("com.google.android.gm", "Gmail"),
        "chrome" to Pair("com.android.chrome", "Chrome"),
        "youtube" to Pair("com.google.android.youtube", "YouTube"),
        "yt" to Pair("com.google.android.youtube", "YouTube"),
        "maps" to Pair("com.google.android.apps.maps", "Google Maps"),
        "settings" to Pair("com.android.settings", "Settings"),
        "phone" to Pair("com.android.dialer", "Phone"),
        "contacts" to Pair("com.android.contacts", "Contacts"),
        "calendar" to Pair("com.google.android.calendar", "Calendar"),
        "twitter" to Pair("com.twitter.android", "Twitter"),
        "linkedin" to Pair("com.linkedin.android", "LinkedIn"),
        "snapchat" to Pair("com.snapchat.android", "Snapchat"),
        "netflix" to Pair("com.netflix.mediaclient", "Netflix"),
        "spotify" to Pair("com.spotify.music", "Spotify"),
        "phonepe" to Pair("com.phonepe.app", "PhonePe"),
        "paytm" to Pair("net.one97.paytm", "Paytm"),
        "gpay" to Pair("com.google.android.apps.nbu.paisa.user", "Google Pay"),
        "zomato" to Pair("com.application.zomato", "Zomato"),
        "swiggy" to Pair("in.swiggy.android", "Swiggy"),
        "amazon" to Pair("in.amazon.mShop.android.shopping", "Amazon"),
        "flipkart" to Pair("com.flipkart.android", "Flipkart"),
        "zoom" to Pair("us.zoom.videomeetings", "Zoom"),
        "signal" to Pair("org.thoughtcrime.securesms", "Signal"),
        "claude" to Pair("com.anthropic.claude", "Claude"),
        "chatgpt" to Pair("com.openai.chatgpt", "ChatGPT")
    )

    private val timeKeywords = mapOf(
        "subah" to 8, "morning" to 8, "savere" to 7,
        "dopahar" to 13, "duphar" to 13, "afternoon" to 14, "noon" to 12,
        "shaam" to 18, "evening" to 18, "sham" to 18,
        "raat" to 21, "night" to 21, "midnight" to 0,
        "abhi" to -1, "now" to -1
    )

    fun parse(input: String): ParsedTask {
        val text = input.trim().lowercase()

        var targetPkg: String? = null
        var targetAppName: String? = null
        for ((keyword, pair) in appMap) {
            if (text.contains(keyword)) {
                targetPkg = pair.first
                targetAppName = pair.second
                break
            }
        }

        val urlRegex = Regex("""https?://[^\s]+|www\.[^\s]+""")
        val url = urlRegex.find(input)?.value

        val messagePatterns = listOf(
            Regex("""(?:saying|message:|msg:|bolna:|likhna:|type:?)\s*[:\-]?\s*(.+)""", RegexOption.IGNORE_CASE),
            Regex("""["""](.+)["""]"""),
            Regex("""'(.+)'"""),
            Regex(""":\s*(.+)$""")
        )
        var message: String? = null
        for (pattern in messagePatterns) {
            message = pattern.find(input)?.groupValues?.getOrNull(1)?.trim()
            if (!message.isNullOrBlank()) break
        }

        val recipientPatterns = listOf(
            Regex("""(?:to|ko)\s+([A-Za-z][A-Za-z\s]{1,20})(?:\s+(?:message|msg|bolo|kaho|send))""", RegexOption.IGNORE_CASE),
            Regex("""(?:send|bhejo)\s+(?:message\s+)?to\s+([A-Za-z][A-Za-z\s]{1,20})""", RegexOption.IGNORE_CASE)
        )
        var recipient: String? = null
        for (p in recipientPatterns) {
            recipient = p.find(input)?.groupValues?.getOrNull(1)?.trim()
            if (!recipient.isNullOrBlank()) break
        }

        var scheduledHour: Int? = null
        var scheduledMinute: Int? = null
        val timeRegex = Regex("""(\d{1,2})(?::(\d{2}))?\s*(am|pm|AM|PM)?""")
        val timeMatch = timeRegex.find(text)
        if (timeMatch != null) {
            var hour = timeMatch.groupValues[1].toIntOrNull() ?: 0
            val minute = timeMatch.groupValues[2].toIntOrNull() ?: 0
            val ampm = timeMatch.groupValues[3].lowercase()
            if (ampm == "pm" && hour < 12) hour += 12
            if (ampm == "am" && hour == 12) hour = 0
            if (hour in 0..23) { scheduledHour = hour; scheduledMinute = minute }
        }
        if (scheduledHour == null) {
            for ((kw, h) in timeKeywords) {
                if (text.contains(kw)) { scheduledHour = h; scheduledMinute = 0; break }
            }
        }

        val searchPatterns = listOf(
            Regex("""(?:search|dhundo|khojo)\s+(.+)""", RegexOption.IGNORE_CASE)
        )
        var searchQuery: String? = null
        for (p in searchPatterns) {
            searchQuery = p.find(input)?.groupValues?.getOrNull(1)?.trim()
            if (!searchQuery.isNullOrBlank()) break
        }

        val steps = mutableListOf<TaskStep>()
        var stepId = 1

        if (targetPkg != null) {
            steps.add(TaskStep(stepId++, ActionType.LAUNCH_APP,
                targetApp = targetPkg, delayMs = 2000L, retryCount = 3,
                description = "$targetAppName kholo"))
        }
        if (url != null) {
            steps.add(TaskStep(stepId++, ActionType.OPEN_URL,
                targetUrl = url, delayMs = 2000L, description = "URL kholo: $url"))
        }
        if (searchQuery != null) {
            steps.add(TaskStep(stepId++, ActionType.ENTER_TEXT,
                inputText = searchQuery, delayMs = 1000L,
                description = "Search: $searchQuery"))
            steps.add(TaskStep(stepId++, ActionType.TAP_BUTTON,
                buttonText = "Search", delayMs = 500L,
                description = "Search button dabao"))
        }
        if (message != null && searchQuery == null) {
            steps.add(TaskStep(stepId++, ActionType.ENTER_TEXT,
                inputText = message, delayMs = 1000L,
                description = "Message type karo"))
            val isMessaging = targetPkg?.let {
                it.contains("whatsapp") || it.contains("telegram") ||
                it.contains("messenger") || it.contains("signal")
            } ?: false
            if (isMessaging) {
                steps.add(TaskStep(stepId++, ActionType.TAP_BUTTON,
                    buttonText = "Send", delayMs = 500L, retryCount = 3,
                    description = "Send dabao"))
            }
        }
        if (steps.isEmpty()) {
            steps.add(TaskStep(1, ActionType.CONFIRM_ACTION,
                delayMs = 500L, description = "Manual action"))
        }

        val taskName = when {
            targetAppName != null && recipient != null -> "$targetAppName → $recipient"
            targetAppName != null && message != null -> "$targetAppName: ${message.take(20)}"
            targetAppName != null -> "$targetAppName task"
            url != null -> "Open: ${url.take(30)}"
            else -> "AI Task"
        }

        val confidence = when {
            targetPkg != null && message != null && scheduledHour != null -> 0.95f
            targetPkg != null && message != null -> 0.85f
            targetPkg != null && scheduledHour != null -> 0.80f
            targetPkg != null -> 0.70f
            url != null -> 0.65f
            else -> 0.40f
        }

        val sb = StringBuilder("🤖 Samjha:\n")
        if (targetAppName != null) sb.appendLine("• App: $targetAppName")
        if (recipient != null) sb.appendLine("• Recipient: $recipient")
        if (message != null) sb.appendLine("• Message: \"${message.take(40)}\"")
        if (url != null) sb.appendLine("• URL: $url")
        if (scheduledHour != null) {
            val t = if (scheduledHour == -1) "Abhi"
                    else "%02d:%02d".format(scheduledHour, scheduledMinute ?: 0)
            sb.appendLine("• Time: $t")
        }
        sb.appendLine("• Steps: ${steps.size}")

        return ParsedTask(
            taskName = taskName,
            targetApp = targetPkg,
            targetAppName = targetAppName,
            message = message,
            url = url,
            buttonToPress = null,
            recipient = recipient,
            scheduledHour = scheduledHour,
            scheduledMinute = scheduledMinute,
            steps = steps,
            confidence = confidence,
            explanation = sb.toString().trimEnd()
        )
    }
}
