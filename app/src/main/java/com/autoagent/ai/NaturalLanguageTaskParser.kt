package com.autoagent.personal.ai

import com.autoagent.personal.domain.model.ActionType
import com.autoagent.personal.domain.model.TaskStep
import javax.inject.Inject
import javax.inject.Singleton
import java.util.Calendar

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
        val scheduledDateOffset: Int,   // 0=today, 1=tomorrow, 2=day-after
        val delayMinutes: Int?,          // "10 minute baad"
        val steps: List<TaskStep>,
        val confidence: Float,
        val explanation: String,
        val needsContactResolution: Boolean,
        val onlineFallbackSuggested: Boolean
    )

    private val appMap = mapOf(
        "whatsapp" to Pair("com.whatsapp", "WhatsApp"),
        "wa"       to Pair("com.whatsapp", "WhatsApp"),
        "facebook" to Pair("com.facebook.katana", "Facebook"),
        "fb"       to Pair("com.facebook.katana", "Facebook"),
        "messenger" to Pair("com.facebook.orca", "Messenger"),
        "instagram" to Pair("com.instagram.android", "Instagram"),
        "telegram"  to Pair("org.telegram.messenger", "Telegram"),
        "gmail"     to Pair("com.google.android.gm", "Gmail"),
        "chrome"    to Pair("com.android.chrome", "Chrome"),
        "youtube"   to Pair("com.google.android.youtube", "YouTube"),
        "yt"        to Pair("com.google.android.youtube", "YouTube"),
        "maps"      to Pair("com.google.android.apps.maps", "Google Maps"),
        "settings"  to Pair("com.android.settings", "Settings"),
        "phone"     to Pair("com.android.dialer", "Phone"),
        "contacts"  to Pair("com.android.contacts", "Contacts"),
        "calendar"  to Pair("com.google.android.calendar", "Calendar"),
        "twitter"   to Pair("com.twitter.android", "Twitter"),
        "linkedin"  to Pair("com.linkedin.android", "LinkedIn"),
        "snapchat"  to Pair("com.snapchat.android", "Snapchat"),
        "netflix"   to Pair("com.netflix.mediaclient", "Netflix"),
        "spotify"   to Pair("com.spotify.music", "Spotify"),
        "phonepe"   to Pair("com.phonepe.app", "PhonePe"),
        "paytm"     to Pair("net.one97.paytm", "Paytm"),
        "gpay"      to Pair("com.google.android.apps.nbu.paisa.user", "Google Pay"),
        "zomato"    to Pair("com.application.zomato", "Zomato"),
        "swiggy"    to Pair("in.swiggy.android", "Swiggy"),
        "amazon"    to Pair("in.amazon.mShop.android.shopping", "Amazon"),
        "flipkart"  to Pair("com.flipkart.android", "Flipkart"),
        "zoom"      to Pair("us.zoom.videomeetings", "Zoom"),
        "signal"    to Pair("org.thoughtcrime.securesms", "Signal"),
        "claude"    to Pair("com.anthropic.claude", "Claude"),
        "chatgpt"   to Pair("com.openai.chatgpt", "ChatGPT"),
        "slack"     to Pair("com.Slack", "Slack"),
        "teams"     to Pair("com.microsoft.teams", "Teams"),
        "discord"   to Pair("com.discord", "Discord"),
        "gmail"     to Pair("com.google.android.gm", "Gmail")
    )

    // Hinglish time-of-day keywords → default hour
    private val timeOfDay = mapOf(
        "subah"     to 8,  "savere"    to 7,  "morning"   to 8,
        "dopahar"   to 13, "duphar"    to 13, "afternoon" to 14, "noon" to 12,
        "shaam"     to 18, "sham"      to 18, "evening"   to 18,
        "raat"      to 21, "night"     to 21, "midnight"  to 0,
        "abhi"      to -1, "now"       to -1, "turant"    to -1
    )

    // Hinglish date keywords → day offset
    private val dateKeywords = mapOf(
        "kal"    to 1, "tomorrow"  to 1, "agle din" to 1,
        "parso"  to 2, "day after" to 2,
        "aaj"    to 0, "today"     to 0,
        "is hafte" to 0
    )

    fun parse(input: String): ParsedTask {
        val text = input.trim().lowercase()

        // --- App detection ---
        var targetPkg: String? = null
        var targetAppName: String? = null
        for ((keyword, pair) in appMap) {
            if (text.contains(keyword)) {
                targetPkg = pair.first
                targetAppName = pair.second
                break
            }
        }

        // --- URL detection ---
        val urlRegex = Regex("""https?://[^\s]+|www\.[^\s]+""")
        val url = urlRegex.find(input)?.value

        // --- Message detection ---
        val messagePatterns = listOf(
            Regex("""(?:saying|message:|msg:|bolna:|likhna:|bol do:|likh do:)\s*(.+)""", RegexOption.IGNORE_CASE),
            Regex(""":\s*['""](.+)['""]"""),
            Regex(""":\s*(.+)$""")
        )
        var message: String? = null
        for (pattern in messagePatterns) {
            val found = pattern.find(input)?.groupValues?.getOrNull(1)?.trim()
            if (!found.isNullOrBlank()) { message = found; break }
        }

        // --- Recipient detection (expanded for Hindi patterns) ---
        val recipientRegex = Regex(
            """(?:to|ko)\s+([A-Za-z][A-Za-z\s]{1,25}?)(?:\s+(?:message|msg|bolo|kaho|send|bhejo|ko|pe|par))\b""",
            RegexOption.IGNORE_CASE
        )
        val recipient = recipientRegex.find(input)?.groupValues?.getOrNull(1)?.trim()

        // --- Date offset detection ---
        var dateOffset = 0
        for ((kw, offset) in dateKeywords) {
            if (text.contains(kw)) { dateOffset = offset; break }
        }

        // --- Time detection: explicit numbers first ---
        var scheduledHour: Int? = null
        var scheduledMinute: Int? = null
        val timeRegex = Regex("""(\d{1,2})(?::(\d{2}))?\s*(am|pm|baje)?""")
        val timeMatch = timeRegex.find(text)
        if (timeMatch != null) {
            var hour = timeMatch.groupValues[1].toIntOrNull() ?: 0
            val minute = timeMatch.groupValues[2].toIntOrNull() ?: 0
            val ampm = timeMatch.groupValues[3].lowercase()
            if (ampm == "pm" && hour < 12) hour += 12
            if (ampm == "am" && hour == 12) hour = 0
            // "6 baje" without am/pm — use time-of-day context
            if (ampm.isEmpty() || ampm == "baje") {
                for ((kw, defaultHour) in timeOfDay) {
                    if (text.contains(kw) && defaultHour != -1) {
                        if (hour < 12 && defaultHour >= 12) hour += 12
                        break
                    }
                }
            }
            if (hour in 0..23) { scheduledHour = hour; scheduledMinute = minute }
        }
        // Fall back to time-of-day keywords
        if (scheduledHour == null) {
            for ((kw, h) in timeOfDay) {
                if (text.contains(kw)) { scheduledHour = h; scheduledMinute = 0; break }
            }
        }

        // --- Delay detection: "X minute baad", "X minutes later" ---
        var delayMinutes: Int? = null
        val delayRegex = Regex("""(\d{1,3})\s*(?:minute|min|mins|minutes)\s*(?:baad|bad|later|ke baad)""")
        delayRegex.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { delayMinutes = it }

        // --- Search query detection ---
        val searchRegex = Regex("""(?:search|dhundo|khojo)\s+(.+)""", RegexOption.IGNORE_CASE)
        val searchQuery = searchRegex.find(input)?.groupValues?.getOrNull(1)?.trim()

        // --- Build steps ---
        val steps = mutableListOf<TaskStep>()
        var stepId = 1

        if (targetPkg != null) {
            steps.add(TaskStep(
                id = stepId++, type = ActionType.LAUNCH_APP,
                targetApp = targetPkg, delayMs = 2000L, retryCount = 3,
                description = "$targetAppName kholo"
            ))
        }

        if (url != null) {
            steps.add(TaskStep(
                id = stepId++, type = ActionType.OPEN_URL,
                targetUrl = url, delayMs = 2000L,
                description = "URL kholo: $url"
            ))
        }

        if (searchQuery != null) {
            steps.add(TaskStep(
                id = stepId++, type = ActionType.ENTER_TEXT,
                inputText = searchQuery, delayMs = 1000L,
                description = "Search: $searchQuery"
            ))
            steps.add(TaskStep(
                id = stepId++, type = ActionType.TAP_BUTTON,
                buttonText = "Search", delayMs = 500L,
                description = "Search button dabao"
            ))
        }

        if (message != null && searchQuery == null) {
            steps.add(TaskStep(
                id = stepId++, type = ActionType.ENTER_TEXT,
                inputText = message, delayMs = 1000L,
                description = "Message type karo"
            ))
            val isMessaging = targetPkg != null && (
                targetPkg.contains("whatsapp") || targetPkg.contains("telegram") ||
                targetPkg.contains("messenger") || targetPkg.contains("signal") ||
                targetPkg.contains("discord") || targetPkg.contains("slack")
            )
            if (isMessaging) {
                steps.add(TaskStep(
                    id = stepId++, type = ActionType.TAP_BUTTON,
                    buttonText = "Send", delayMs = 500L, retryCount = 3,
                    description = "Send dabao"
                ))
            }
        }

        if (steps.isEmpty()) {
            steps.add(TaskStep(
                id = 1, type = ActionType.CONFIRM_ACTION,
                delayMs = 500L, description = "Manual action"
            ))
        }

        // --- Task name ---
        val taskName = when {
            targetAppName != null && recipient != null -> "$targetAppName → $recipient"
            targetAppName != null && message != null  -> "$targetAppName: ${message.take(20)}"
            targetAppName != null                     -> "$targetAppName task"
            url != null                               -> "Open: ${url.take(30)}"
            else                                      -> "AI Task"
        }

        // --- Confidence scoring ---
        val confidence = when {
            targetPkg != null && message != null && scheduledHour != null -> 0.95f
            targetPkg != null && message != null                          -> 0.85f
            targetPkg != null && recipient != null                        -> 0.80f
            targetPkg != null && scheduledHour != null                    -> 0.78f
            targetPkg != null                                             -> 0.70f
            url != null                                                   -> 0.65f
            delayMinutes != null                                          -> 0.55f
            else                                                          -> 0.35f
        }

        // Suggest online AI if confidence is low
        val needsOnline = confidence < 0.50f

        // --- Explanation ---
        val lines = mutableListOf("Samjha:")
        if (targetAppName != null) lines.add("App: $targetAppName")
        if (recipient != null) lines.add("Recipient: $recipient")
        if (message != null) lines.add("Message: ${message.take(40)}")
        if (url != null) lines.add("URL: $url")
        if (scheduledHour != null) {
            val t = if (scheduledHour == -1) "Abhi"
                    else "%02d:%02d".format(scheduledHour, scheduledMinute ?: 0)
            val dayStr = when (dateOffset) { 1 -> " kal"; 2 -> " parso"; else -> " aaj" }
            lines.add("Time:$dayStr $t")
        }
        if (delayMinutes != null) lines.add("Delay: $delayMinutes min baad")
        lines.add("Steps: ${steps.size}")
        if (needsOnline) lines.add("⚠️ Command complex hai — online AI madad kar sakta hai")

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
            scheduledDateOffset = dateOffset,
            delayMinutes = delayMinutes,
            steps = steps,
            confidence = confidence,
            explanation = lines.joinToString("\n"),
            needsContactResolution = recipient != null,
            onlineFallbackSuggested = needsOnline
        )
    }
}
