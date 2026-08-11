package com.autoagent.personal.agent

data class Plan(val steps: List<Step>, val appPkg: String)
data class Step(val intent: Intent, val target: String = "", val desc: String = "")

enum class Intent {
    LAUNCH_APP, TAP, TYPE, SEARCH_KEY, SCROLL, WAIT, HOME, BACK
}

object GoalPlanner {

    fun plan(raw: String): Plan {
        val g = raw.lowercase().trim()

        if (g.hasAny("youtube","yt")) {
            val query = g.extractQuery("search","dhundo","play","chalao","bajao","lagao","suno","find")
                ?: g.extractArtist()
            val steps = mutableListOf<Step>()
            steps += Step(Intent.LAUNCH_APP, "com.google.android.youtube", "YouTube kholo")
            steps += Step(Intent.WAIT, "2500")
            if (query != null) {
                steps += Step(Intent.TAP, "Search YouTube", "Search bar tap")
                steps += Step(Intent.WAIT, "800")
                steps += Step(Intent.TYPE, query, "Type: $query")
                steps += Step(Intent.SEARCH_KEY, "", "Search karo")
                steps += Step(Intent.WAIT, "2000", "Results aane do")
                if (g.hasAny("play","chalao","bajao","lagao","suno","dekhna","sunna")) {
                    steps += Step(Intent.SCROLL, "", "Scroll")
                    steps += Step(Intent.TAP, query.split(" ").first(), "Pehla result tap")
                }
            }
            return Plan(steps, "com.google.android.youtube")
        }

        if (g.hasAny("whatsapp","whats app","watsapp")) {
            val contact = g.extractContact()
            val message = g.extractMessage()
            val steps = mutableListOf<Step>()
            steps += Step(Intent.LAUNCH_APP, "com.whatsapp", "WhatsApp kholo")
            steps += Step(Intent.WAIT, "2500")
            if (contact != null) {
                steps += Step(Intent.TAP, "Search", "Search tap")
                steps += Step(Intent.WAIT, "600")
                steps += Step(Intent.TYPE, contact, "Contact: $contact")
                steps += Step(Intent.WAIT, "1500")
                steps += Step(Intent.TAP, contact, "Contact tap")
                steps += Step(Intent.WAIT, "1200")
            }
            if (message != null) {
                steps += Step(Intent.TAP, "Type a message", "Message box tap")
                steps += Step(Intent.WAIT, "600")
                steps += Step(Intent.TYPE, message, "Message: $message")
                steps += Step(Intent.WAIT, "500")
                steps += Step(Intent.TAP, "Send", "Send karo")
            }
            return Plan(steps, "com.whatsapp")
        }

        if (g.hasAny("telegram")) {
            val contact = g.extractContact()
            val message = g.extractMessage()
            val steps = mutableListOf<Step>()
            steps += Step(Intent.LAUNCH_APP, "org.telegram.messenger", "Telegram kholo")
            steps += Step(Intent.WAIT, "2500")
            if (contact != null) {
                steps += Step(Intent.TAP, "Search", "Search tap")
                steps += Step(Intent.TYPE, contact, "Contact: $contact")
                steps += Step(Intent.WAIT, "1200")
                steps += Step(Intent.TAP, contact, "Contact tap")
                steps += Step(Intent.WAIT, "1000")
            }
            if (message != null) {
                steps += Step(Intent.TYPE, message, "Message: $message")
                steps += Step(Intent.TAP, "Send", "Send")
            }
            return Plan(steps, "org.telegram.messenger")
        }

        if (g.hasAny("instagram","insta")) {
            val query = g.extractQuery("search","find","dhundo")
            val steps = mutableListOf<Step>()
            steps += Step(Intent.LAUNCH_APP, "com.instagram.android", "Instagram kholo")
            steps += Step(Intent.WAIT, "2500")
            if (query != null) {
                steps += Step(Intent.TAP, "Search", "Search tap")
                steps += Step(Intent.TYPE, query, "Search: $query")
                steps += Step(Intent.SEARCH_KEY)
            }
            return Plan(steps, "com.instagram.android")
        }

        if (g.hasAny("chrome","browser","google karo","search karo","google me")) {
            val query = g.extractQuery("search","google","find","dhundo") ?: g
            return Plan(listOf(
                Step(Intent.LAUNCH_APP, "com.android.chrome", "Chrome kholo"),
                Step(Intent.WAIT, "2500"),
                Step(Intent.TAP, "Search or type URL", "Address bar tap"),
                Step(Intent.TYPE, query, "Search: $query"),
                Step(Intent.SEARCH_KEY)
            ), "com.android.chrome")
        }

        if (g.hasAny("spotify","music app")) {
            val query = g.extractQuery("play","search","suno","bajao","lagao") ?: g.extractArtist() ?: g
            return Plan(listOf(
                Step(Intent.LAUNCH_APP, "com.spotify.music", "Spotify kholo"),
                Step(Intent.WAIT, "2500"),
                Step(Intent.TAP, "Search", "Search tap"),
                Step(Intent.TYPE, query, "Search: $query"),
                Step(Intent.SEARCH_KEY)
            ), "com.spotify.music")
        }

        if (g.hasAny("band karo","close karo","home pe jao","ghar jao","home jao")) {
            return Plan(listOf(Step(Intent.HOME, "", "Home pe jao")), "")
        }

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
                .split(Regex("\\s+(aur|and|then|phir|ke baad)\\s+"))[0].trim()
            if (after.length > 1) return after
        }
        return null
    }

    private fun String.extractArtist(): String? =
        Regex("""(.+?)\s+(?:ka gana|ka song|ki song|songs?|music|gane|bajao|lagao|suno)""")
            .find(this)?.groupValues?.getOrNull(1)?.trim()

    private fun String.extractContact(): String? =
        Regex("""(?:to|ko|send to|message|text|bolo)\s+([a-zA-Z\s]{2,25})(?:\s+(?:text|message|bolo|kaho|likho|send|aur|and)|$)""")
            .find(this)?.groupValues?.getOrNull(1)?.trim()

    private fun String.extractMessage(): String? {
        Regex("""['""](.*?)['"""]""").find(this)?.let { return it.groupValues[1] }
        return Regex("""(?:text|message|bolo|likho|kaho|bol)[:\s]+(.+?)(?:\s+(?:aur|and|send)|$)""")
            .find(this)?.groupValues?.getOrNull(1)?.trim()
    }
}
