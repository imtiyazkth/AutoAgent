package com.autoagent.personal.agent

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * EntityResolver — extracts structured entities from natural language.
 *
 * FIXES APPLIED:
 * 1. Removed unclosed `[` in resolveMessage Regex (was: `(?:resolve[Message])`)
 *    Correct: `(?:message|msg|text)`
 * 2. All Regex patterns now use raw strings correctly.
 * 3. Added null safety throughout.
 *
 * This class is NOT Hilt-injected. Instantiate it directly where needed.
 */
class EntityResolver {

    // ─── Contact resolution ───────────────────────────────────────────────────

    /**
     * Extract a contact name from a natural-language string.
     * Returns null if no contact pattern is matched.
     */
    fun resolveContact(input: String): String? {
        val g = input.lowercase(Locale.getDefault()).trim()

        val patterns = listOf(
            // "send to Imtiyaz", "message to John"
            Regex("""(?:send|message|msg|text)\s+(?:to|ko)\s+([a-zA-Z][a-zA-Z\s]{1,30})(?:\s|${'$'})"""),
            // "Imtiyaz ko message karo"
            Regex("""([a-zA-Z][a-zA-Z\s]{1,30})\s+(?:ko|se)\s+(?:message|text|msg|bolo|kaho)"""),
            // "search Imtiyaz"
            Regex("""(?:search|dhundo|find)\s+([a-zA-Z][a-zA-Z\s]{1,30})(?:\s+(?:aur|and|in|on)|${'$'})"""),
            // "call Imtiyaz"
            Regex("""(?:call|ring|phone)\s+([a-zA-Z][a-zA-Z\s]{1,30})(?:\s|${'$'})""")
        )

        for (pattern in patterns) {
            val match = pattern.find(g)?.groupValues?.getOrNull(1)?.trim()
            if (!match.isNullOrBlank() && match.length > 1) {
                return match.split(" ").joinToString(" ") { word ->
                    word.replaceFirstChar { it.uppercaseChar() }
                }
            }
        }
        return null
    }

    // ─── Message resolution ───────────────────────────────────────────────────

    /**
     * Extract the message body from a natural-language task string.
     *
     * FIX: Previous version had `Regex("""(?:resolve[Message])...""")` which is an
     * unclosed character class — `[Message]` means "any of M,e,s,a,g" not the word
     * "Message". This caused a PatternSyntaxException at runtime.
     */
    fun resolveMessage(input: String): String? {
        // 1. Quoted text takes highest priority
        val quotePattern = Regex("""['""\u201c\u2018](.*?)['""\u201d\u2019]""")
        quotePattern.find(input)?.let { return it.groupValues[1].trim() }

        // 2. After explicit keyword
        val keywordPattern = Regex(
            """(?:message|msg|text|bolo|likho|kaho|type|bol)[:\s]+(.+?)(?:\s+(?:aur|and|send|bhejo)|${'$'})""",
            RegexOption.IGNORE_CASE
        )
        return keywordPattern.find(input)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
    }

    // ─── Date/time resolution ─────────────────────────────────────────────────

    data class ResolvedDateTime(
        val dateTime: LocalDateTime,
        val zoneId: ZoneId = ZoneId.systemDefault(),
        val isRelative: Boolean = false
    )

    /**
     * Extract a date and time from a natural-language string.
     * Returns null if no recognizable date/time pattern is found.
     *
     * Supported patterns (examples):
     *   "5 October at 5:00 PM"
     *   "tomorrow at 9am"
     *   "in 30 minutes"
     *   "today 6pm"
     *   "Monday at noon"
     */
    fun resolveDateTime(input: String): ResolvedDateTime? {
        val g = input.lowercase(Locale.getDefault()).trim()
        val now = LocalDateTime.now(ZoneId.systemDefault())

        // 1. Relative: "in X minutes/hours"
        val relativePattern = Regex("""in\s+(\d+)\s+(minute|min|hour|hr)s?""")
        relativePattern.find(g)?.let { m ->
            val amount = m.groupValues[1].toLongOrNull() ?: return@let
            val unit = m.groupValues[2]
            val dt = when {
                unit.startsWith("h") -> now.plusHours(amount)
                else -> now.plusMinutes(amount)
            }
            return ResolvedDateTime(dt, isRelative = true)
        }

        // 2. "tomorrow at HH:mm"
        if (g.contains("tomorrow") || g.contains("kal")) {
            val time = extractTime(g)
            return ResolvedDateTime(
                now.plusDays(1).withHour(time.hour).withMinute(time.minute).withSecond(0),
                isRelative = true
            )
        }

        // 3. "today at HH:mm"
        if (g.contains("today") || g.contains("aaj")) {
            val time = extractTime(g)
            return ResolvedDateTime(
                now.withHour(time.hour).withMinute(time.minute).withSecond(0),
                isRelative = true
            )
        }

        // 4. "5 October at 5:00 PM" or "October 5 at 5pm"
        val monthNames = mapOf(
            "january" to 1, "jan" to 1,
            "february" to 2, "feb" to 2,
            "march" to 3, "mar" to 3,
            "april" to 4, "apr" to 4,
            "may" to 5,
            "june" to 6, "jun" to 6,
            "july" to 7, "jul" to 7,
            "august" to 8, "aug" to 8,
            "september" to 9, "sep" to 9, "sept" to 9,
            "october" to 10, "oct" to 10,
            "november" to 11, "nov" to 11,
            "december" to 12, "dec" to 12
        )

        for ((monthName, monthNum) in monthNames) {
            if (!g.contains(monthName)) continue

            // Try "5 October" or "October 5"
            val dayPattern = Regex("""(\d{1,2})\s+$monthName|$monthName\s+(\d{1,2})""")
            val dayMatch = dayPattern.find(g) ?: continue
            val day = (dayMatch.groupValues[1].takeIf { it.isNotBlank() }
                ?: dayMatch.groupValues[2]).toIntOrNull() ?: continue

            val time = extractTime(g)
            var date = LocalDate.of(now.year, monthNum, day)

            // If the date has already passed this year, assume next year
            if (date.isBefore(now.toLocalDate())) {
                date = date.plusYears(1)
            }

            return ResolvedDateTime(
                LocalDateTime.of(date, time)
            )
        }

        return null
    }

    /**
     * Extract a LocalTime from a natural-language string.
     * Falls back to 09:00 if no time is found.
     */
    private fun extractTime(input: String): LocalTime {
        // "5:30 PM", "17:30", "5pm", "noon", "midnight"
        when {
            input.contains("noon")     -> return LocalTime.of(12, 0)
            input.contains("midnight") -> return LocalTime.of(0, 0)
        }

        // "HH:MM AM/PM"
        val fullTime = Regex("""(\d{1,2}):(\d{2})\s*(am|pm)?""", RegexOption.IGNORE_CASE)
        fullTime.find(input)?.let { m ->
            var hour = m.groupValues[1].toIntOrNull() ?: return@let
            val minute = m.groupValues[2].toIntOrNull() ?: 0
            val amPm = m.groupValues[3].lowercase()
            if (amPm == "pm" && hour != 12) hour += 12
            if (amPm == "am" && hour == 12) hour = 0
            return LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
        }

        // "5pm", "9am"
        val shortTime = Regex("""(\d{1,2})\s*(am|pm)""", RegexOption.IGNORE_CASE)
        shortTime.find(input)?.let { m ->
            var hour = m.groupValues[1].toIntOrNull() ?: return@let
            val amPm = m.groupValues[2].lowercase()
            if (amPm == "pm" && hour != 12) hour += 12
            if (amPm == "am" && hour == 12) hour = 0
            return LocalTime.of(hour.coerceIn(0, 23), 0)
        }

        return LocalTime.of(9, 0) // Default fallback
    }

    // ─── App resolution ───────────────────────────────────────────────────────

    /**
     * Resolve an app name to its package name.
     * Returns null if the app is not in the known list.
     * For unknown apps, the caller should use PackageManager to search.
     */
    fun resolveAppPackage(appName: String): String? {
        val name = appName.lowercase(Locale.getDefault()).trim()
        return knownApps.entries.firstOrNull { (key, _) ->
            name.contains(key)
        }?.value
    }

    private val knownApps = mapOf(
        "whatsapp"  to "com.whatsapp",
        "youtube"   to "com.google.android.youtube",
        "instagram" to "com.instagram.android",
        "telegram"  to "org.telegram.messenger",
        "spotify"   to "com.spotify.music",
        "chrome"    to "com.android.chrome",
        "gmail"     to "com.google.android.gm",
        "maps"      to "com.google.android.apps.maps",
        "calendar"  to "com.google.android.calendar",
        "clock"     to "com.google.android.deskclock",
        "twitter"   to "com.twitter.android",
        "facebook"  to "com.facebook.katana",
        "snapchat"  to "com.snapchat.android",
        "settings"  to "com.android.settings",
        "camera"    to "com.android.camera2",
        "photos"    to "com.google.android.apps.photos"
    )
}
