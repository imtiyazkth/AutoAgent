package com.autoagent.personal.reply

import android.util.Log
import com.autoagent.personal.memory.MemoryEngine
import com.autoagent.personal.service.accessibility.AutoAgentAccessibilityService
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay

/**
 * ReplyHandler — reads screen text after sending a message
 * and detects whether a reply or result has appeared.
 */
@Singleton
class ReplyHandler @Inject constructor(
    private val memoryEngine: MemoryEngine
) {
    companion object {
        private const val TAG = "ReplyHandler"

        // Positive reply signals
        private val positiveKeywords = listOf(
            "haan", "ha", "yes", "ok", "okay", "theek", "bilkul",
            "sure", "done", "received", "mil gaya", "dekh liya",
            "👍", "✅", "😊"
        )
        // Negative reply signals
        private val negativeKeywords = listOf(
            "nahi", "no", "nope", "sorry", "maafi", "band", "busy",
            "baad mein", "later", "wait", "ruko"
        )
        // Question signals (reply needs response)
        private val questionKeywords = listOf("?", "kya", "kyun", "kab", "kaise", "when", "why", "how")
    }

    data class ReplyResult(
        val detected: Boolean,
        val replyText: String,
        val sentiment: ReplySentiment,
        val isQuestion: Boolean,
        val suggestedAction: String?
    )

    enum class ReplySentiment { POSITIVE, NEGATIVE, NEUTRAL, QUESTION }

    /**
     * Watch the screen for up to [timeoutMs] ms, polling every [pollMs] ms,
     * looking for text that wasn't there before [beforeText].
     */
    suspend fun watchForReply(
        beforeText: String = "",
        timeoutMs: Long = 30_000,
        pollMs: Long = 1_500
    ): ReplyResult {
        val start = System.currentTimeMillis()
        var lastText = beforeText

        while (System.currentTimeMillis() - start < timeoutMs) {
            if (AutoAgentAccessibilityService.emergencyStop.value) break

            val service = AutoAgentAccessibilityService.getInstance()
            val currentText = service?.readScreenText() ?: ""

            if (currentText.isNotBlank() && currentText != lastText) {
                val newContent = extractNewContent(lastText, currentText)
                if (newContent.isNotBlank()) {
                    Log.d(TAG, "Reply detected: ${newContent.take(80)}")
                    val result = analyzeReply(newContent)
                    // Save reply context to memory
                    memoryEngine.remember(
                        MemoryEngine.CAT_LAST_CMD,
                        "last_reply",
                        newContent.take(200)
                    )
                    return result
                }
                lastText = currentText
            }
            delay(pollMs)
        }

        return ReplyResult(
            detected = false,
            replyText = "",
            sentiment = ReplySentiment.NEUTRAL,
            isQuestion = false,
            suggestedAction = "Koi reply nahi aayi ${timeoutMs / 1000}s mein — dobara try karein?"
        )
    }

    private fun extractNewContent(before: String, after: String): String {
        if (before.isBlank()) return after
        val beforeWords = before.trim()
        val afterWords = after.trim()
        return if (afterWords.length > beforeWords.length)
            afterWords.substring(beforeWords.length).trim()
        else ""
    }

    private fun analyzeReply(text: String): ReplyResult {
        val lower = text.lowercase()
        val isQuestion = questionKeywords.any { lower.contains(it) }
        val isPositive = positiveKeywords.any { lower.contains(it) }
        val isNegative = negativeKeywords.any { lower.contains(it) }

        val sentiment = when {
            isQuestion -> ReplySentiment.QUESTION
            isPositive -> ReplySentiment.POSITIVE
            isNegative -> ReplySentiment.NEGATIVE
            else -> ReplySentiment.NEUTRAL
        }

        val suggestion = when (sentiment) {
            ReplySentiment.POSITIVE -> "Reply positive hai — follow-up bhejein?"
            ReplySentiment.NEGATIVE -> "Reply negative hai — koi aur action chahiye?"
            ReplySentiment.QUESTION -> "Unhone sawaal kiya hai — jawab do?"
            ReplySentiment.NEUTRAL  -> null
        }

        return ReplyResult(
            detected = true,
            replyText = text.take(500),
            sentiment = sentiment,
            isQuestion = isQuestion,
            suggestedAction = suggestion
        )
    }
}
