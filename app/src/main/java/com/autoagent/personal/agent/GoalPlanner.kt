package com.autoagent.personal.agent

/**
 * GoalPlanner — THE single NLP → Execution Plan pipeline.
 *
 * ARCHITECTURE NOTE:
 * This replaces GoalDecomposer.kt and IntentEngine.kt which were introduced
 * in v3 but used incompatible Step/Plan types causing compile failures.
 * Delete GoalDecomposer.kt and IntentEngine.kt after applying this fix.
 *
 * All keyword matching is done here. If LLM-based planning is added later,
 * it should call plan() and return a Plan in the same format — the caller
 * (AgentController) should not need to change.
 */

// ─── Data Model ───────────────────────────────────────────────────────────────

enum class Intent {
    LAUNCH_APP,
    TAP,
    TAP_SEARCH_BAR,
    TAP_FIRST_RESULT,
    TYPE,
    SEARCH_KEY,
    SCROLL,
    WAIT,
    HOME,
    BACK
}

/**
 * A single execution step.
 *
 * @param intent  The action to perform.
 * @param target  The argument for the action (text to tap, ms to wait, package to launch, etc.)
 * @param desc    Human-readable description for logging / UI display.
 */
data class Step(
    val intent: Intent,
    val target: String = "",
    val desc: String = ""
)

/**
 * An ordered list of steps to complete a goal.
 *
 * @param steps   Ordered list of steps.
 * @param appPkg  Primary app package being automated (empty for multi-app tasks).
 */
data class Plan(
    val steps: List<Step>,
    val appPkg: String
)

// ─── Planner ──────────────────────────────────────────────────────────────────

object GoalPlanner {

    /**
     * Convert a natural-language goal string into a deterministic execution plan.
     * Supports English and Hinglish (Hindi + English mixed).
     */
    fun plan(raw: String): Plan {
        val g = raw.lowercase().trim()

        return when {
            g.hasAny("youtube", "yt")         -> planYouTube(g)
            g.hasAny("whatsapp", "watsapp",
                "whats app")                   -> planWhatsApp(g)
            g.hasAny("telegram")              -> planTelegram(g)
            g.hasAny("instagram", "insta")    -> planInstagram(g)
            g.hasAny("spotify")               -> planSpotify(g)
            g.hasAny("chrome", "browser",
                "google me", "google karo",
                "search karo")                 -> planChrome(g)
            g.hasAny("band karo", "close karo",
                "home pe jao", "ghar jao")     -> planHome()
            else                              -> planGenericApp(g)
        }
    }

    // ─── App-specific planners ────────────────────────────────────────────────

    private fun planYouTube(g: String): Plan {
        val query = g.extractQuery(
            "search", "dhundo", "play", "chalao", "bajao",
            "lagao", "suno", "find", "dekhna"
        ) ?: g.extractArtist()

        val steps = mutableListOf<Step>()
        steps += Step(Intent.LAUNCH_APP, "com.google.android.youtube", "YouTube kholo")
        steps += Step(Intent.WAIT, "3500", "App load hone do")

        if (query != null) {
            steps += Step(Intent.TAP_SEARCH_BAR, "youtube", "Search bar tap karo")
            steps += Step(Intent.WAIT, "1200", "Keyboard aane do")
            steps += Step(Intent.TYPE, query, "Type: $query")
            steps += Step(Intent.WAIT, "600", "Type complete")
            steps += Step(Intent.SEARCH_KEY, "", "Search karo")
            steps += Step(Intent.WAIT, "3000", "Results aane do")

            val shouldPlay = g.hasAny(
                "play", "chalao", "bajao", "lagao", "suno",
                "dekhna", "sunna", "first", "pehla"
            )
            if (shouldPlay) {
                steps += Step(Intent.SCROLL, "", "Thoda scroll karo")
                steps += Step(Intent.WAIT, "800", "Scroll complete")
                steps += Step(Intent.TAP_FIRST_RESULT, query, "Pehla result play karo")
            }
        }

        return Plan(steps, "com.google.android.youtube")
    }

    private fun planWhatsApp(g: String): Plan {
        val contact = g.extractContact()
        val message = g.extractMessage()

        val steps = mutableListOf<Step>()
        steps += Step(Intent.LAUNCH_APP, "com.whatsapp", "WhatsApp kholo")
        steps += Step(Intent.WAIT, "3000", "App load hone do")

        if (contact != null) {
            steps += Step(Intent.TAP_SEARCH_BAR, "whatsapp", "Search tap karo")
            steps += Step(Intent.WAIT, "800", "Search khula")
            steps += Step(Intent.TYPE, contact, "Contact: $contact")
            steps += Step(Intent.WAIT, "1800", "Results aane do")
            steps += Step(Intent.TAP_FIRST_RESULT, contact, "Contact tap karo")
            steps += Step(Intent.WAIT, "1500", "Chat khuli")
        }

        if (message != null) {
            steps += Step(Intent.TAP, "Type a message", "Message box tap karo")
            steps += Step(Intent.WAIT, "800", "Keyboard aaya")
            steps += Step(Intent.TYPE, message, "Message: $message")
            steps += Step(Intent.WAIT, "500", "Typing complete")
            steps += Step(Intent.TAP, "Send", "Send karo")
        }

        return Plan(steps, "com.whatsapp")
    }

    private fun planTelegram(g: String): Plan {
        val contact = g.extractContact()
        val message = g.extractMessage()

        val steps = mutableListOf<Step>()
        steps += Step(Intent.LAUNCH_APP, "org.telegram.messenger", "Telegram kholo")
        steps += Step(Intent.WAIT, "3000", "App load hone do")

        if (contact != null) {
            steps += Step(Intent.TAP_SEARCH_BAR, "telegram", "Search tap karo")
            steps += Step(Intent.TYPE, contact, "Contact: $contact")
            steps += Step(Intent.WAIT, "1500", "Results aane do")
            steps += Step(Intent.TAP_FIRST_RESULT, contact, "Contact tap karo")
            steps += Step(Intent.WAIT, "1000", "Chat khuli")
        }

        if (message != null) {
            steps += Step(Intent.TYPE, message, "Message type karo")
            steps += Step(Intent.WAIT, "500", "Typing complete")
            steps += Step(Intent.TAP, "Send", "Send karo")
        }

        return Plan(steps, "org.telegram.messenger")
    }

    private fun planInstagram(g: String): Plan {
        val query = g.extractQuery("search", "find", "dhundo")

        val steps = mutableListOf<Step>()
        steps += Step(Intent.LAUNCH_APP, "com.instagram.android", "Instagram kholo")
        steps += Step(Intent.WAIT, "3000", "App load hone do")

        if (query != null) {
            steps += Step(Intent.TAP_SEARCH_BAR, "instagram", "Search tap karo")
            steps += Step(Intent.TYPE, query, "Search: $query")
            steps += Step(Intent.SEARCH_KEY, "", "Search karo")
            steps += Step(Intent.WAIT, "2000", "Results aane do")
            steps += Step(Intent.TAP_FIRST_RESULT, query, "Pehla result tap karo")
        }

        return Plan(steps, "com.instagram.android")
    }

    private fun planSpotify(g: String): Plan {
        val query = g.extractQuery("play", "search", "suno", "bajao", "lagao")
            ?: g.extractArtist()
            ?: g

        return Plan(
            steps = listOf(
                Step(Intent.LAUNCH_APP, "com.spotify.music", "Spotify kholo"),
                Step(Intent.WAIT, "2500", "App load hone do"),
                Step(Intent.TAP_SEARCH_BAR, "spotify", "Search tap karo"),
                Step(Intent.TYPE, query, "Search: $query"),
                Step(Intent.SEARCH_KEY, "", "Search karo"),
                Step(Intent.WAIT, "2000", "Results aane do"),
                Step(Intent.TAP_FIRST_RESULT, query, "Pehla result play karo")
            ),
            appPkg = "com.spotify.music"
        )
    }

    private fun planChrome(g: String): Plan {
        val query = g.extractQuery("search", "google", "find", "dhundo") ?: g

        return Plan(
            steps = listOf(
                Step(Intent.LAUNCH_APP, "com.android.chrome", "Chrome kholo"),
                Step(Intent.WAIT, "2500", "App load hone do"),
                Step(Intent.TAP_SEARCH_BAR, "chrome", "Address bar tap karo"),
                Step(Intent.WAIT, "800", "Bar selected"),
                Step(Intent.TYPE, query, "Search: $query"),
                Step(Intent.SEARCH_KEY, "", "Search karo")
            ),
            appPkg = "com.android.chrome"
        )
    }

    private fun planHome(): Plan = Plan(
        steps = listOf(Step(Intent.HOME, "", "Home screen pe jao")),
        appPkg = ""
    )

    private fun planGenericApp(g: String): Plan {
        val apps = mapOf(
            "settings"  to "com.android.settings",
            "camera"    to "com.android.camera2",
            "photos"    to "com.google.android.apps.photos",
            "gallery"   to "com.google.android.apps.photos",
            "maps"      to "com.google.android.apps.maps",
            "gmail"     to "com.google.android.gm",
            "calendar"  to "com.google.android.calendar",
            "clock"     to "com.google.android.deskclock",
            "twitter"   to "com.twitter.android",
            "facebook"  to "com.facebook.katana",
            "snapchat"  to "com.snapchat.android"
        )

        for ((keyword, pkg) in apps) {
            if (g.contains(keyword)) {
                return Plan(
                    steps = listOf(Step(Intent.LAUNCH_APP, pkg, "$keyword kholo")),
                    appPkg = pkg
                )
            }
        }

        // Nothing matched — return empty plan so caller can show user feedback
        return Plan(steps = emptyList(), appPkg = "")
    }

    // ─── String utilities ─────────────────────────────────────────────────────

    private fun String.hasAny(vararg words: String): Boolean =
        words.any { this.contains(it) }

    /**
     * Extract the query/subject that follows one of the [keywords].
     * Strips common filler words after the keyword.
     * Stops at conjunctions like "aur", "and", "phir".
     */
    private fun String.extractQuery(vararg keywords: String): String? {
        for (kw in keywords) {
            val idx = this.indexOf(kw)
            if (idx < 0) continue
            val after = this.substring(idx + kw.length).trim()
                .removePrefix("karo").removePrefix("kar")
                .removePrefix("me").removePrefix("mein")
                .trim()
                .split(Regex("\\s+(aur|and|then|phir|ke baad)\\s+"))[0]
                .trim()
            if (after.length > 1) return after
        }
        return null
    }

    /**
     * Extract artist name from patterns like "Arijit ka gana bajao".
     */
    private fun String.extractArtist(): String? =
        Regex("""(.+?)\s+(?:ka gana|ka song|ki song|songs?|music|gane|bajao|lagao|suno)""")
            .find(this)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotBlank() }

    /**
     * Extract a contact name from WhatsApp/Telegram task descriptions.
     * Tries multiple patterns to handle Hinglish variations.
     */
    private fun String.extractContact(): String? {
        val patterns = listOf(
            // "search Imtiyaz text"
            Regex("""(?:search|dhundo|find)\s+([a-zA-Z][a-zA-Z\s]{1,25})(?:\s+(?:text|message|ko|se|aur|and)|${'$'})"""),
            // "send to Imtiyaz"
            Regex("""(?:to|ko|send to)\s+([a-zA-Z][a-zA-Z\s]{1,25})(?:\s+(?:text|message|bolo|send|aur)|${'$'})"""),
            // "Imtiyaz ko text"
            Regex("""([a-zA-Z][a-zA-Z\s]{1,20})\s+(?:ko text|ko message|ko|se baat)""")
        )
        for (pattern in patterns) {
            val match = pattern.find(this)?.groupValues?.getOrNull(1)?.trim()
            if (!match.isNullOrBlank() && match.length > 1) return match
        }
        return null
    }

    /**
     * Extract a message body from quoted text or post-keyword text.
     * Handles both straight and curly quotes.
     */
    private fun String.extractMessage(): String? {
        // Quoted: 'hello' or "hello" or "hello"
        Regex("""['""\u201c\u2018](.*?)['""\u201d\u2019]""").find(this)
            ?.let { return it.groupValues[1].trim() }

        // After keyword: "bolo: Hi there"
        return Regex("""(?:text|message|bolo|likho|kaho|bol|type)[:\s]+(.+?)(?:\s+(?:aur|and|send)|${'$'})""")
            .find(this)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }
}
