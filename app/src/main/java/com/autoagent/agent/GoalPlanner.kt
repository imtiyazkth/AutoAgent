package com.autoagent.personal.agent

data class Plan(val steps: List<Step>, val appPkg: String)
data class Step(val intent: Intent, val target: String = "", val desc: String = "")

enum class Intent {
    LAUNCH_APP, TAP, TAP_SEARCH_BAR, TAP_FIRST_RESULT,
    TYPE, SEARCH_KEY, SCROLL, WAIT, HOME, BACK
}

object GoalPlanner {

    fun plan(raw: String): Plan {
        val g = raw.lowercase().trim()

        // ── YouTube ───────────────────────────────────────────────
        if (g.hasAny("youtube", "yt")) {
            val query = g.extractQuery(
                "search","dhundo","play","chalao","bajao","lagao","suno","find","dekhna"
            ) ?: g.extractYouTubeQuery() ?: g.extractArtist()
            val steps = mutableListOf<Step>()
            steps += Step(Intent.LAUNCH_APP, "com.google.android.youtube", "YouTube kholo")
            steps += Step(Intent.WAIT, "3500")
            if (query != null) {
                steps += Step(Intent.TAP_SEARCH_BAR, "youtube", "Search bar tap")
                steps += Step(Intent.WAIT, "1200")
                steps += Step(Intent.TYPE, query, "Type: $query")
                steps += Step(Intent.WAIT, "600")
                steps += Step(Intent.SEARCH_KEY, "", "Search karo")
                steps += Step(Intent.WAIT, "3000", "Results aane do")
                if (g.hasAny("play","chalao","bajao","lagao","suno","dekhna","sunna","first","pehla")) {
                    steps += Step(Intent.SCROLL, "", "Thoda scroll")
                    steps += Step(Intent.WAIT, "800")
                    steps += Step(Intent.TAP_FIRST_RESULT, query, "Pehla result play karo")
                }
            }
            return Plan(steps, "com.google.android.youtube")
        }

        // ── WhatsApp ──────────────────────────────────────────────
        if (g.hasAny("whatsapp","whats app","watsapp")) {
            val contact = g.extractContact()
            val message = g.extractMessage()
            val steps = mutableListOf<Step>()
            steps += Step(Intent.LAUNCH_APP, "com.whatsapp", "WhatsApp kholo")
            steps += Step(Intent.WAIT, "3000")
            if (contact != null) {
                steps += Step(Intent.TAP_SEARCH_BAR, "whatsapp", "Search tap")
                steps += Step(Intent.WAIT, "800")
                steps += Step(Intent.TYPE, contact, "Contact: $contact")
                steps += Step(Intent.WAIT, "1800")
                steps += Step(Intent.TAP_FIRST_RESULT, contact, "Contact tap")
                steps += Step(Intent.WAIT, "1500")
            }
            if (message != null) {
                steps += Step(Intent.TAP, "Type a message", "Message box tap")
                steps += Step(Intent.WAIT, "800")
                steps += Step(Intent.TYPE, message, "Message: $message")
                steps += Step(Intent.WAIT, "500")
                steps += Step(Intent.TAP, "Send", "Send karo")
            }
            return Plan(steps, "com.whatsapp")
        }

        // ── Telegram ──────────────────────────────────────────────
        if (g.hasAny("telegram")) {
            val contact = g.extractContact()
            val message = g.extractMessage()
            val steps = mutableListOf<Step>()
            steps += Step(Intent.LAUNCH_APP, "org.telegram.messenger", "Telegram kholo")
            steps += Step(Intent.WAIT, "3000")
            if (contact != null) {
                steps += Step(Intent.TAP_SEARCH_BAR, "telegram", "Search tap")
                steps += Step(Intent.TYPE, contact, "Contact: $contact")
                steps += Step(Intent.WAIT, "1500")
                steps += Step(Intent.TAP_FIRST_RESULT, contact, "Contact tap")
                steps += Step(Intent.WAIT, "1000")
            }
            if (message != null) {
                steps += Step(Intent.TYPE, message, "Message: $message")
                steps += Step(Intent.WAIT, "500")
                steps += Step(Intent.TAP, "Send", "Send")
            }
            return Plan(steps, "org.telegram.messenger")
        }

        // ── Instagram ─────────────────────────────────────────────
        if (g.hasAny("instagram","insta")) {
            val query = g.extractQuery("search","find","dhundo")
            val steps = mutableListOf<Step>()
            steps += Step(Intent.LAUNCH_APP, "com.instagram.android", "Instagram kholo")
            steps += Step(Intent.WAIT, "3000")
            if (query != null) {
                steps += Step(Intent.TAP_SEARCH_BAR, "instagram", "Search tap")
                steps += Step(Intent.TYPE, query, "Search: $query")
                steps += Step(Intent.SEARCH_KEY)
                steps += Step(Intent.WAIT, "2000")
                steps += Step(Intent.TAP_FIRST_RESULT, query, "Pehla result")
            }
            return Plan(steps, "com.instagram.android")
        }

        // ── Chrome ────────────────────────────────────────────────
        if (g.hasAny("chrome","browser","google karo","google me","search karo")) {
            val query = g.extractQuery("search","google","find","dhundo") ?: g
            return Plan(listOf(
                Step(Intent.LAUNCH_APP, "com.android.chrome", "Chrome kholo"),
                Step(Intent.WAIT, "2500"),
                Step(Intent.TAP_SEARCH_BAR, "chrome", "Address bar tap"),
                Step(Intent.WAIT, "800"),
                Step(Intent.TYPE, query, "Search: $query"),
                Step(Intent.SEARCH_KEY)
            ), "com.android.chrome")
        }

        // ── Spotify ───────────────────────────────────────────────
        if (g.hasAny("spotify")) {
            val query = g.extractQuery("play","search","suno","bajao","lagao") ?: g.extractArtist() ?: g
            return Plan(listOf(
                Step(Intent.LAUNCH_APP, "com.spotify.music", "Spotify kholo"),
                Step(Intent.WAIT, "2500"),
                Step(Intent.TAP_SEARCH_BAR, "spotify", "Search tap"),
                Step(Intent.TYPE, query, "Search: $query"),
                Step(Intent.SEARCH_KEY),
                Step(Intent.WAIT, "2000"),
                Step(Intent.TAP_FIRST_RESULT, query, "Play")
            ), "com.spotify.music")
        }

        // ── Close/Home ────────────────────────────────────────────
        if (g.hasAny("band karo","close karo","home pe jao","ghar jao")) {
            return Plan(listOf(Step(Intent.HOME, "", "Home pe jao")), "")
        }

        // ── Generic apps ──────────────────────────────────────────
        val apps = mapOf(
            "settings" to "com.android.settings",
            "camera"   to "com.android.camera2",
            "photos"   to "com.google.android.apps.photos",
            "gallery"  to "com.google.android.apps.photos",
            "maps"     to "com.google.android.apps.maps",
            "gmail"    to "com.google.android.gm",
            "calendar" to "com.google.android.calendar",
            "clock"    to "com.google.android.deskclock",
            "twitter"  to "com.twitter.android",
            "facebook" to "com.facebook.katana",
            "snapchat" to "com.snapchat.android"
        )
        for ((kw, pkg) in apps) {
            if (g.contains(kw)) return Plan(listOf(Step(Intent.LAUNCH_APP, pkg, "$kw kholo")), pkg)
        }

        return Plan(emptyList(), "")
    }

    private fun String.hasAny(vararg words: String) = words.any { this.contains(it) }

    private fun String.extractQuery(vararg keywords: String): String? {
        for (kw in keywords) {
            val i = this.indexOf(kw); if (i < 0) continue
            val after = this.substring(i + kw.length).trim()
                .removePrefix("karo").removePrefix("kar").trim()
            // Stop at conjunctions — we only want the search query part
            val query = after.split(Regex("\\s+(aur|and|then|phir|ke baad|aur use|and then|phir use)\\s+"))[0].trim()
                .removePrefix("mein").removePrefix("pe").removePrefix("par").trim()
            if (query.length > 1 && query.length < 60) return query
        }
        return null
    }

    private fun String.extractYouTubeQuery(): String? {
        // Handle: "youtube mein jao aur arijeet singh ka gana search karo"
        // Pattern: after "aur" find the actual search subject
        val aurIdx = this.indexOf(" aur ")
        if (aurIdx >= 0) {
            val afterAur = this.substring(aurIdx + 5).trim()
            // Extract subject before action verbs
            val query = afterAur
                .split(Regex("\\s+(search karo|search|dhundo|play karo|play|bajao|lagao|suno|chalao)\\s*"))[0]
                .trim()
                .removePrefix("ka gana").removePrefix("ki song").removePrefix("ka song").trim()
            if (query.length > 1 && query.length < 50) return query
        }
        return null
    }

    private fun String.extractArtist(): String? =
        Regex("""(.+?)\s+(?:ka gana|ka song|ki song|songs?|music|gane|bajao|lagao|suno)""")
            .find(this)?.groupValues?.getOrNull(1)?.trim()

    private fun String.extractContact(): String? {
        val p1 = Regex("""(?:search|dhundo|find)\s+([a-zA-Z][a-zA-Z ]{1,20})(?:\s+(?:text|message|ko|se|aur)|$)""")
        val p2 = Regex("""(?:to|ko|send to)\s+([a-zA-Z][a-zA-Z ]{1,20})(?:\s+(?:text|message|bolo|send|aur)|$)""")
        val p3 = Regex("""([a-zA-Z][a-zA-Z ]{1,15})\s+(?:ko text|ko message|ko)""")
        for (p in listOf(p1, p2, p3)) {
            val r = p.find(this)?.groupValues?.getOrNull(1)?.trim()
            if (!r.isNullOrBlank() && r.length > 1) return r
        }
        return null
    }

    private fun String.extractMessage(): String? {
        Regex("""['\""](.*?)['\""]""").find(this)?.let { return it.groupValues[1] }
        return Regex("""(?:text|message|bolo|likho|kaho|bol|type)[:\s]+(.+?)(?:\s+(?:aur|and|send)|$)""")
            .find(this)?.groupValues?.getOrNull(1)?.trim()
    }
}
