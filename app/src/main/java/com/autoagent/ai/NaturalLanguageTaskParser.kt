package com.autoagent.personal.ai

import com.autoagent.personal.domain.model.ActionType
import com.autoagent.personal.domain.model.TaskStep
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
        val scheduledDateOffset: Int,
        val delayMinutes: Int?,
        val steps: List<TaskStep>,
        val confidence: Float,
        val explanation: String,
        val needsContactResolution: Boolean,
        val onlineFallbackSuggested: Boolean
    )

    private val appMap = mapOf(
        "whatsapp" to Pair("com.whatsapp","WhatsApp"),
        "wa" to Pair("com.whatsapp","WhatsApp"),
        "telegram" to Pair("org.telegram.messenger","Telegram"),
        "instagram" to Pair("com.instagram.android","Instagram"),
        "youtube" to Pair("com.google.android.youtube","YouTube"),
        "yt" to Pair("com.google.android.youtube","YouTube"),
        "chrome" to Pair("com.android.chrome","Chrome"),
        "brave" to Pair("com.brave.browser","Brave"),
        "firefox" to Pair("org.mozilla.firefox","Firefox"),
        "gmail" to Pair("com.google.android.gm","Gmail"),
        "maps" to Pair("com.google.android.apps.maps","Google Maps"),
        "facebook" to Pair("com.facebook.katana","Facebook"),
        "fb" to Pair("com.facebook.katana","Facebook"),
        "messenger" to Pair("com.facebook.orca","Messenger"),
        "twitter" to Pair("com.twitter.android","Twitter"),
        "linkedin" to Pair("com.linkedin.android","LinkedIn"),
        "snapchat" to Pair("com.snapchat.android","Snapchat"),
        "netflix" to Pair("com.netflix.mediaclient","Netflix"),
        "spotify" to Pair("com.spotify.music","Spotify"),
        "phonepe" to Pair("com.phonepe.app","PhonePe"),
        "paytm" to Pair("net.one97.paytm","Paytm"),
        "gpay" to Pair("com.google.android.apps.nbu.paisa.user","Google Pay"),
        "zomato" to Pair("com.application.zomato","Zomato"),
        "swiggy" to Pair("in.swiggy.android","Swiggy"),
        "amazon" to Pair("in.amazon.mShop.android.shopping","Amazon"),
        "flipkart" to Pair("com.flipkart.android","Flipkart"),
        "zoom" to Pair("us.zoom.videomeetings","Zoom"),
        "signal" to Pair("org.thoughtcrime.securesms","Signal"),
        "slack" to Pair("com.Slack","Slack"),
        "discord" to Pair("com.discord","Discord"),
        "phone" to Pair("com.android.dialer","Phone"),
        "contacts" to Pair("com.android.contacts","Contacts"),
        "settings" to Pair("com.android.settings","Settings"),
        "calendar" to Pair("com.google.android.calendar","Calendar"),
        "clock" to Pair("com.google.android.deskclock","Clock"),
        "alarm" to Pair("com.google.android.deskclock","Clock"),
        "camera" to Pair("com.android.camera2","Camera"),
        "photos" to Pair("com.google.android.apps.photos","Photos"),
        "gallery" to Pair("com.google.android.apps.photos","Photos"),
        "claude" to Pair("com.anthropic.claude","Claude"),
        "chatgpt" to Pair("com.openai.chatgpt","ChatGPT"),
        "gemini" to Pair("com.google.android.apps.bard","Gemini")
    )

    private val timeOfDay = mapOf(
        "subah" to 8,"savere" to 7,"morning" to 8,
        "dopahar" to 13,"duphar" to 13,"afternoon" to 14,"noon" to 12,
        "shaam" to 18,"sham" to 18,"evening" to 18,
        "raat" to 21,"night" to 21,"midnight" to 0,
        "abhi" to -1,"now" to -1,"turant" to -1
    )

    private val dateKeywords = mapOf(
        "kal" to 1,"tomorrow" to 1,"agle din" to 1,
        "parso" to 2,"day after" to 2,
        "aaj" to 0,"today" to 0
    )

    private fun detectDurationMs(text: String): Long {
        val minR = Regex("""(\d+)\s*(?:minute|min|mins|minat)""")
        val secR = Regex("""(\d+)\s*(?:second|sec|secs)""")
        val m = minR.find(text)
        val s = secR.find(text)
        return when {
            m != null -> (m.groupValues[1].toLongOrNull() ?: 0L) * 60_000L
            s != null -> (s.groupValues[1].toLongOrNull() ?: 0L) * 1_000L
            else -> 0L
        }
    }

    fun parse(input: String): ParsedTask {
        val text = input.trim().lowercase()

        var targetPkg: String? = null
        var targetAppName: String? = null
        for ((kw, pair) in appMap) {
            if (text.contains(kw)) { targetPkg = pair.first; targetAppName = pair.second; break }
        }

        val urlR = Regex("""https?://[^\s]+|www\.[^\s]+""")
        val url = urlR.find(input)?.value

        val msgPatterns = listOf(
            Regex("""(?:likho|likh do|type karo|bol do|bolo|write|message:)\s*['""]?(.+?)['""]?\s*$""", RegexOption.IGNORE_CASE),
            Regex("""['""]([^'""]+)['""]"""),
            Regex("""(?:send|bhejo|reply)\s+['""]?(.+?)['""]?\s*$""", RegexOption.IGNORE_CASE)
        )
        var message: String? = null
        for (p in msgPatterns) {
            val f = p.find(input)?.groupValues?.getOrNull(1)?.trim()
            if (!f.isNullOrBlank() && f.length > 1) { message = f; break }
        }

        val recipR = Regex(
            """(?:to|ko)\s+([A-Za-z][A-Za-z\s]{1,25}?)(?:\s+(?:message|msg|bolo|kaho|send|bhejo|ko|pe|par|mein|likho))\b""",
            RegexOption.IGNORE_CASE
        )
        val recipient = recipR.find(input)?.groupValues?.getOrNull(1)?.trim()

        var dateOffset = 0
        for ((kw, off) in dateKeywords) { if (text.contains(kw)) { dateOffset = off; break } }

        var scheduledHour: Int? = null
        var scheduledMinute: Int? = null
        val timeR = Regex("""(\d{1,2})(?::(\d{2}))?\s*(am|pm|baje)?""")
        val tm = timeR.find(text)
        if (tm != null) {
            var h = tm.groupValues[1].toIntOrNull() ?: 0
            val mn = tm.groupValues[2].toIntOrNull() ?: 0
            val ap = tm.groupValues[3].lowercase()
            if (ap == "pm" && h < 12) h += 12
            if (ap == "am" && h == 12) h = 0
            if (ap.isEmpty() || ap == "baje") {
                for ((kw, dh) in timeOfDay) {
                    if (text.contains(kw) && dh != -1) {
                        if (h < 12 && dh >= 12) h += 12; break
                    }
                }
            }
            if (h in 0..23) { scheduledHour = h; scheduledMinute = mn }
        }
        if (scheduledHour == null) {
            for ((kw, h) in timeOfDay) {
                if (text.contains(kw) && h != -1) { scheduledHour = h; scheduledMinute = 0; break }
            }
        }

        var delayMinutes: Int? = null
        val delR = Regex("""(\d{1,3})\s*(?:minute|min|mins)\s*(?:baad|bad|later|ke baad|wait)""")
        delR.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { delayMinutes = it }

        val srchR = Regex("""(?:search|dhundo|khojo|search karo)\s+(.+?)(?:\s+and|\s+aur|\s+phir|\s+then|$)""", RegexOption.IGNORE_CASE)
        val searchQuery = srchR.find(input)?.groupValues?.getOrNull(1)?.trim()

        val duration = detectDurationMs(text)

        val isAlarm = text.contains("alarm") || text.contains("wake") || text.contains("jagao") || text.contains("uthao")
        val isAlarmSet = isAlarm && scheduledHour != null
        val isPlay = text.contains("play") || text.contains("chalao") || text.contains("bajao") || text.contains("suno")
        val isStop = text.contains("stop") || text.contains("band karo") || text.contains("rok") || text.contains("close")
        val isNext = text.contains(" next") || text.contains("agla") || text.contains("next dabao")
        val isReply = text.contains("reply") || text.contains("jawab") || text.contains("wait for reply")

        val isMessaging = targetPkg != null && (
            targetPkg.contains("whatsapp") || targetPkg.contains("telegram") ||
            targetPkg.contains("messenger") || targetPkg.contains("signal") ||
            targetPkg.contains("discord") || targetPkg.contains("slack"))
        val isBrowser = targetPkg != null && (
            targetPkg.contains("chrome") || targetPkg.contains("brave") || targetPkg.contains("firefox"))
        val isYouTube = targetPkg == "com.google.android.youtube"

        val steps = mutableListOf<TaskStep>()
        var sid = 1

        if (isAlarmSet) {
            val ts = "%02d:%02d".format(scheduledHour ?: 5, scheduledMinute ?: 0)
            steps.add(TaskStep(id=sid++,type=ActionType.LAUNCH_APP,targetApp="com.google.android.deskclock",delayMs=2000L,retryCount=2,description="Clock app kholo"))
            steps.add(TaskStep(id=sid++,type=ActionType.TAP_BY_LABEL,buttonText="Alarm",delayMs=1000L,description="Alarm tab pe jao"))
            steps.add(TaskStep(id=sid++,type=ActionType.TAP_BUTTON,buttonText="Add alarm",delayMs=800L,retryCount=2,description="Naya alarm add karo"))
            steps.add(TaskStep(id=sid++,type=ActionType.WAIT_SECONDS,delayMs=1500L,description="Dialog khulne ka wait"))
            steps.add(TaskStep(id=sid++,type=ActionType.TAP_BUTTON,buttonText="OK",delayMs=500L,retryCount=3,description="Alarm $ts confirm karo"))
            return mk("Alarm: $ts ${if(dateOffset==1)"kal" else "aaj"}",steps,
                "com.google.android.deskclock","Clock",scheduledHour,scheduledMinute,
                dateOffset,0.88f,"Alarm set: $ts",null,null,null,null)
        }

        if (targetPkg != null) {
            steps.add(TaskStep(id=sid++,type=ActionType.LAUNCH_APP,targetApp=targetPkg,delayMs=2500L,retryCount=3,description="$targetAppName kholo"))
        }

        if (url != null) {
            steps.add(TaskStep(id=sid++,type=ActionType.OPEN_URL,targetUrl=url,delayMs=2000L,description="URL kholo: $url"))
        }

        if (isMessaging && recipient != null) {
            steps.add(TaskStep(id=sid++,type=ActionType.WAIT_FOR_TEXT,waitForText="Search",delayMs=1500L,retryCount=3,description="Search button ka wait"))
            steps.add(TaskStep(id=sid++,type=ActionType.TAP_BUTTON,buttonText="Search",delayMs=800L,retryCount=3,description="Search tap karo"))
            steps.add(TaskStep(id=sid++,type=ActionType.ENTER_TEXT,inputText=recipient,delayMs=1000L,description="$recipient ka naam type karo"))
            steps.add(TaskStep(id=sid++,type=ActionType.WAIT_FOR_TEXT,waitForText=recipient,delayMs=2000L,retryCount=3,description="$recipient aane ka wait"))
            steps.add(TaskStep(id=sid++,type=ActionType.TAP_BY_LABEL,buttonText=recipient,delayMs=1200L,retryCount=3,description="$recipient pe tap karo"))
        }

        if (message != null && !isYouTube) {
            if (isMessaging) {
                steps.add(TaskStep(id=sid++,type=ActionType.WAIT_FOR_TEXT,waitForText="Type a message",delayMs=1500L,description="Chat box ka wait"))
                steps.add(TaskStep(id=sid++,type=ActionType.TAP_BUTTON,buttonText="Type a message",delayMs=600L,description="Message box tap"))
            }
            steps.add(TaskStep(id=sid++,type=ActionType.ENTER_TEXT,inputText=message,delayMs=1000L,description="Type: $message"))
            if (isMessaging) {
                steps.add(TaskStep(id=sid++,type=ActionType.TAP_BUTTON,buttonText="Send",delayMs=800L,retryCount=3,description="Send dabao"))
            }
        }

        if (isReply && isMessaging) {
            val wMs = if (delayMinutes!=null) delayMinutes*60_000L else 30_000L
            steps.add(TaskStep(id=sid++,type=ActionType.WAIT_SECONDS,delayMs=wMs,description="Reply ka wait (${wMs/1000}s)"))
            steps.add(TaskStep(id=sid++,type=ActionType.READ_TEXT,delayMs=500L,description="Reply check karo"))
        }

        if (isYouTube && searchQuery != null) {
            steps.add(TaskStep(id=sid++,type=ActionType.WAIT_FOR_TEXT,waitForText="Search YouTube",delayMs=1500L,description="Search bar ka wait"))
            steps.add(TaskStep(id=sid++,type=ActionType.TAP_BUTTON,buttonText="Search YouTube",delayMs=800L,description="Search bar tap"))
            steps.add(TaskStep(id=sid++,type=ActionType.ENTER_TEXT,inputText=searchQuery,delayMs=1000L,description="Search: $searchQuery"))
            steps.add(TaskStep(id=sid++,type=ActionType.TAP_BUTTON,buttonText="Search",delayMs=600L,description="Search karo"))
            if (isPlay) {
                steps.add(TaskStep(id=sid++,type=ActionType.WAIT_SECONDS,delayMs=2000L,description="Results aane ka wait"))
                steps.add(TaskStep(id=sid++,type=ActionType.TAP_BY_LABEL,buttonText=searchQuery,delayMs=1000L,retryCount=3,description="Pehla result play karo"))
            }
            if (isNext) {
                steps.add(TaskStep(id=sid++,type=ActionType.WAIT_SECONDS,delayMs=3000L,description="Video start hone ka wait"))
                steps.add(TaskStep(id=sid++,type=ActionType.TAP_BUTTON,buttonText="Next",delayMs=500L,description="Next dabao"))
            }
            if (duration > 0) {
                steps.add(TaskStep(id=sid++,type=ActionType.WAIT_SECONDS,delayMs=duration,description="${duration/60000} min bajne do"))
                if (isStop) steps.add(TaskStep(id=sid++,type=ActionType.GO_HOME,delayMs=500L,description="Stop — home pe jao"))
            }
        }

        if (isBrowser && searchQuery != null) {
            steps.add(TaskStep(id=sid++,type=ActionType.WAIT_SECONDS,delayMs=2000L,description="Browser load ka wait"))
            steps.add(TaskStep(id=sid++,type=ActionType.ENTER_TEXT,inputText=searchQuery,delayMs=1000L,description="Search: $searchQuery"))
            steps.add(TaskStep(id=sid++,type=ActionType.TAP_BUTTON,buttonText="Search",delayMs=600L,description="Search karo"))
        }

        if (isStop && steps.isNotEmpty() && !isYouTube) {
            steps.add(TaskStep(id=sid++,type=ActionType.GO_HOME,delayMs=500L,description="App band karo"))
        }

        if (steps.isEmpty()) {
            steps.add(TaskStep(id=1,type=ActionType.CONFIRM_ACTION,delayMs=500L,description="Manual action"))
        }

        val taskName = when {
            isMessaging && recipient!=null && message!=null -> "$targetAppName: $recipient ko message"
            isYouTube && searchQuery!=null -> "YouTube: $searchQuery"
            isBrowser && searchQuery!=null -> "$targetAppName: $searchQuery"
            targetAppName!=null && message!=null -> "$targetAppName: ${message.take(20)}"
            targetAppName!=null -> "$targetAppName task"
            url!=null -> "URL: ${url.take(30)}"
            else -> "AI Task"
        }

        val conf = when {
            isMessaging && recipient!=null && message!=null && scheduledHour!=null -> 0.97f
            isMessaging && recipient!=null && message!=null -> 0.92f
            isYouTube && searchQuery!=null && isPlay -> 0.90f
            isBrowser && searchQuery!=null -> 0.85f
            targetPkg!=null && message!=null -> 0.82f
            targetPkg!=null && searchQuery!=null -> 0.82f
            targetPkg!=null && scheduledHour!=null -> 0.78f
            targetPkg!=null -> 0.70f
            url!=null -> 0.65f
            else -> 0.35f
        }

        val lines = mutableListOf("Samjha:")
        if (targetAppName!=null) lines.add("App: $targetAppName")
        if (recipient!=null) lines.add("Recipient: $recipient")
        if (message!=null) lines.add("Message: ${message.take(40)}")
        if (searchQuery!=null) lines.add("Search: $searchQuery")
        if (url!=null) lines.add("URL: $url")
        if (scheduledHour!=null && scheduledHour!=-1) lines.add("Time: ${if(dateOffset==1)"kal" else "aaj"} %02d:%02d".format(scheduledHour,scheduledMinute?:0))
        if (delayMinutes!=null) lines.add("Delay: $delayMinutes min baad")
        if (duration>0) lines.add("Duration: ${duration/60000} min")
        if (isReply) lines.add("Reply detection: ON")
        if (isStop) lines.add("Auto-stop: ON")
        lines.add("Steps: ${steps.size}")

        return mk(taskName,steps,targetPkg,targetAppName,scheduledHour,scheduledMinute,
            dateOffset,conf,lines.joinToString("\n"),recipient,message,url,delayMinutes)
    }

    private fun mk(
        taskName:String, steps:List<TaskStep>, targetPkg:String?, targetAppName:String?,
        scheduledHour:Int?, scheduledMinute:Int?, dateOffset:Int, confidence:Float,
        explanation:String, recipient:String?, message:String?, url:String?, delayMinutes:Int?
    ) = ParsedTask(taskName=taskName,targetApp=targetPkg,targetAppName=targetAppName,
        message=message,url=url,buttonToPress=null,recipient=recipient,
        scheduledHour=scheduledHour,scheduledMinute=scheduledMinute,
        scheduledDateOffset=dateOffset,delayMinutes=delayMinutes,steps=steps,
        confidence=confidence,explanation=explanation,
        needsContactResolution=recipient!=null,onlineFallbackSuggested=confidence<0.50f)
}
