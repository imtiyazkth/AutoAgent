package com.autoagent.personal.ai

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EntityResolver @Inject constructor() {

    private val APP_MAP = mapOf(
        "youtube"   to ("YouTube"   to "com.google.android.youtube"),
        "yt"        to ("YouTube"   to "com.google.android.youtube"),
        "whatsapp"  to ("WhatsApp"  to "com.whatsapp"),
        "watsapp"   to ("WhatsApp"  to "com.whatsapp"),
        "telegram"  to ("Telegram"  to "org.telegram.messenger"),
        "instagram" to ("Instagram" to "com.instagram.android"),
        "insta"     to ("Instagram" to "com.instagram.android"),
        "chrome"    to ("Chrome"    to "com.android.chrome"),
        "spotify"   to ("Spotify"   to "com.spotify.music"),
        "gmail"     to ("Gmail"     to "com.google.android.gm"),
        "maps"      to ("Maps"      to "com.google.android.apps.maps"),
        "camera"    to ("Camera"    to "com.android.camera2"),
        "settings"  to ("Settings"  to "com.android.settings"),
        "calendar"  to ("Calendar"  to "com.google.android.calendar"),
        "clock"     to ("Clock"     to "com.google.android.deskclock"),
        "twitter"   to ("Twitter"   to "com.twitter.android"),
        "facebook"  to ("Facebook"  to "com.facebook.katana"),
        "snapchat"  to ("Snapchat"  to "com.snapchat.android"),
        "netflix"   to ("Netflix"   to "com.netflix.mediaclient"),
        "phonepe"   to ("PhonePe"   to "com.phonepe.app"),
        "paytm"     to ("Paytm"     to "net.one97.paytm"),
        "amazon"    to ("Amazon"    to "in.amazon.mShop.android.shopping"),
        "flipkart"  to ("Flipkart"  to "com.flipkart.android"),
        "zoom"      to ("Zoom"      to "us.zoom.videomeetings"),
        "zomato"    to ("Zomato"    to "com.application.zomato"),
        "swiggy"    to ("Swiggy"    to "in.swiggy.android"),
        "photos"    to ("Photos"    to "com.google.android.apps.photos")
    )

    fun resolveApp(text: String): Pair<String?, String?> {
        for ((kw, pair) in APP_MAP) {
            if (text.contains(kw)) return pair
        }
        return null to null
    }

    fun resolveQuery(text: String, appName: String?): String? {
        // Strategy 1: "aur X search/play karo"
        val aurPattern = Regex(
            """(?:aur|and)\s+([a-zA-Z\u0900-\u097F][a-zA-Z\u0900-\u097F\s]{1,50}?)\s+(?:search karo|search|play karo|play|bajao|lagao|suno|chalao|dhundo)""",
            RegexOption.IGNORE_CASE
        )
        aurPattern.find(text)?.groupValues?.getOrNull(1)?.trim()
            ?.let { if (it.length > 1) return cleanQuery(it) }

        // Strategy 2: after search keywords
        for (kw in listOf("search karo", "dhundo", "khojo", "find karo")) {
            val idx = text.indexOf(kw); if (idx < 0) continue
            val after = text.substring(idx + kw.length).trim()
            val q = after.split(Regex("\\s+(aur|and|phir|then)\\s+"))[0].trim()
            if (q.length > 1 && q.length < 60) return cleanQuery(q)
        }

        // Strategy 3: after play/bajao keywords
        for (kw in listOf("play karo", "bajao", "lagao", "suno", "chalao", "play")) {
            val idx = text.indexOf(kw); if (idx < 0) continue
            val after = text.substring(idx + kw.length).trim()
            val q = after.split(Regex("\\s+(aur|and|phir|then)\\s+"))[0].trim()
            if (q.length > 1 && q.length < 60) return cleanQuery(q)
        }

        // Strategy 4: artist patterns
        Regex("""(.+?)\s+(?:ka gana|ka song|ki song|songs?|music|gane)""")
            .find(text)?.groupValues?.getOrNull(1)?.trim()?.let { return it }

        return null
    }

    private fun cleanQuery(q: String): String = q
        .replace(Regex("\\s+ka gana$"), "")
        .replace(Regex("\\s+ki song$"), "")
        .replace(Regex("\\s+ka song$"), "")
        .replace(Regex("\\s+songs?$"), "")
        .replace(Regex("^(?:mein|pe|par)\\s+"), "")
        .trim()

    fun resolveContact(text: String): String? {
        val p1 = Regex("""(?:search|dhundo|find)\s+([a-zA-Z][a-zA-Z\s]{1,20})(?:\s+(?:text|message|ko|se|aur)|$)""")
        val p2 = Regex("""(?:to|ko|send to)\s+([a-zA-Z][a-zA-Z\s]{1,20})(?:\s+(?:text|message|bolo|send|aur)|$)""")
        val p3 = Regex("""([a-zA-Z][a-zA-Z\s]{1,15})\s+(?:ko text|ko message|ko|se baat)""")
        for (p in listOf(p1, p2, p3)) {
            val r = p.find(text)?.groupValues?.getOrNull(1)?.trim()
            if (!r.isNullOrBlank() && r.length > 1) return r
        }
        return null
    }

    fun resolveMessage(text: String): String? {
        Regex("""['""](.*?)['"""]""").find(text)?.let { return it.groupValues[1] }
        return Regex("""(?:text|message|bolo|likho|kaho|bol|type)[:\s]+(.+?)(?:\s+(?:aur|and|send)|$)""")
            .find(text)?.groupValues?.getOrNull(1)?.trim()
    }

    fun resolveTime(text: String): String? {
        Regex("""(\d{1,2})(?::(\d{2}))?\s*(am|pm|baje)?""", RegexOption.IGNORE_CASE)
            .find(text)?.let { m ->
                var h = m.groupValues[1].toIntOrNull() ?: return null
                val mn = m.groupValues[2].toIntOrNull() ?: 0
                val ap = m.groupValues[3].lowercase()
                if (ap == "pm" && h < 12) h += 12
                if (ap == "am" && h == 12) h = 0
                return "%02d:%02d".format(h, mn)
            }
        return null
    }
}
