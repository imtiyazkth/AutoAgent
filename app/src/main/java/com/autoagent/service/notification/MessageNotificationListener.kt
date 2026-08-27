package com.autoagent.personal.service.notification

import android.app.Notification
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.autoagent.personal.util.L
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

/**
 * MessageNotificationListener — intercepts incoming WhatsApp/Telegram/SMS
 * notifications, announces them via TTS, and can send a spoken reply back
 * using the notification's own quick-reply RemoteInput action — WITHOUT
 * opening the target app.
 *
 * ENABLE MANUALLY: user must go to
 * Settings > Apps > Special access > Notification access > AutoAgent > Allow
 * (this cannot be granted via a runtime permission dialog — Android requires
 * the user to enable it from Settings).
 */
@AndroidEntryPoint
class MessageNotificationListener : NotificationListenerService() {

    private val TAG = "MsgListener"
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null

    // Packages we care about. Add more as needed.
    private val watchedPackages = setOf(
        "com.whatsapp",
        "com.whatsapp.w4b",       // WhatsApp Business
        "org.telegram.messenger",
        "com.google.android.apps.messaging" // Google Messages (SMS)
    )

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("hi", "IN")
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        if (pkg !in watchedPackages) return

        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        // Skip our own summary/group notifications and empty bodies
        if (text.isBlank()) return
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        L.d(TAG, "Message from $pkg — $title: $text")

        val appLabel = when {
            pkg.startsWith("com.whatsapp") -> "WhatsApp"
            pkg.contains("telegram") -> "Telegram"
            else -> "SMS"
        }

        announceAndOfferReply(sbn, appLabel, title, text)
    }

    private fun announceAndOfferReply(
        sbn: StatusBarNotification,
        appLabel: String,
        sender: String,
        message: String
    ) {
        val announcement = "$sender ne $appLabel par message bheja hai. Kya sunna chahte hain?"

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onError(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                if (utteranceId == "announce_$sender") {
                    // After announcing sender, read the message, then listen for reply
                    serviceScope.launch {
                        speakMessage(message) {
                            listenForReplyAndSend(sbn, sender)
                        }
                    }
                }
            }
        })

        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "announce_$sender")
        }
        tts?.speak(announcement, TextToSpeech.QUEUE_FLUSH, params, "announce_$sender")
    }

    private fun speakMessage(message: String, onDone: () -> Unit) {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onError(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                if (utteranceId == "read_message") onDone()
            }
        })
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "read_message")
        }
        tts?.speak(message, TextToSpeech.QUEUE_FLUSH, params, "read_message")
    }

    /**
     * Listen for the user's spoken reply, then dispatch it using the
     * notification's own RemoteInput action — this sends the reply as if
     * typed inside WhatsApp/Telegram, without ever opening the app.
     */
    private fun listenForReplyAndSend(sbn: StatusBarNotification, sender: String) {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            L.e(TAG, "Speech recognition unavailable for reply capture")
            return
        }

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    val reply = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()

                    if (reply.isNullOrBlank() ||
                        reply.contains("skip", ignoreCase = true) ||
                        reply.contains("chodo", ignoreCase = true) ||
                        reply.contains("baad mein", ignoreCase = true)
                    ) {
                        L.d(TAG, "No reply / skipped")
                        return
                    }

                    val sent = sendQuickReply(sbn, reply)
                    if (sent) {
                        tts?.speak("Reply bhej diya: $reply", TextToSpeech.QUEUE_FLUSH, null, "confirm")
                    } else {
                        tts?.speak("Reply bhejne mein error aaya, app kholke khud bhej dijiye", TextToSpeech.QUEUE_FLUSH, null, "confirm_err")
                    }
                }

                override fun onError(error: Int) { L.e(TAG, "Reply capture error: $error") }
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
            }
            startListening(intent)
        }
    }

    /**
     * Finds the notification's "Reply" action (the one with a RemoteInput,
     * e.g. WhatsApp's inline reply) and fires it with our text —
     * this is the SAME mechanism the system notification shade uses,
     * so it works exactly like a manual quick-reply.
     */
    private fun sendQuickReply(sbn: StatusBarNotification, replyText: String): Boolean {
        val actions = sbn.notification.actions ?: return false

        for (action in actions) {
            val remoteInputs = action.remoteInputs ?: continue
            if (remoteInputs.isEmpty()) continue

            return try {
                val intent = Intent()
                val bundle = Bundle()
                for (ri in remoteInputs) {
                    bundle.putCharSequence(ri.resultKey, replyText)
                }
                RemoteInput.addResultsToIntent(remoteInputs, intent, bundle)
                action.actionIntent.send(this, 0, intent)
                L.d(TAG, "Quick reply sent via notification action")
                true
            } catch (e: Exception) {
                L.e(TAG, "sendQuickReply failed: ${e.message}")
                false
            }
        }
        return false
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // No-op — nothing to clean up per-notification
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
