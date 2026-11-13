package kr.tunnelfree.app.platform.pleos

import android.content.Context
import android.util.Log
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import kr.tunnelfree.app.platform.SpeechOutput
import java.util.Locale

class PleosSpeechOutput(
    context: Context,
    private val fallback: SpeechOutput,
) : SpeechOutput {

    private var tts: TextToSpeech? = null
    private var ready = false
    private var pendingDone: (() -> Unit)? = null
    private var pendingText: String? = null
    private var flushedStops = 0

    private val events = object : EventListener {
        override fun onStart() {}
        override fun onReady() {}
        override fun onDone() {
            flushedStops = 0
            val done = pendingDone
            pendingDone = null
            pendingText = null
            done?.invoke()
        }
        override fun onStop() {
            if (flushedStops > 0) {
                flushedStops--
                return
            }
            pendingDone = null
            pendingText = null
        }
        override fun onError(type: ErrorType) {
            Log.w(TAG, "tts error $type, using Android TTS")
            flushedStops = 0
            val text = pendingText
            val done = pendingDone
            pendingDone = null
            pendingText = null
            if (!text.isNullOrBlank()) fallback.speak(text, done)
        }
        override fun onUpdatedRms(rms: Double) {}
    }

    init {
        try {
            val t = TextToSpeech(context.applicationContext, Mode.ON_DEVICE)
            t.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    try {
                        t.addEventListener(events)
                        t.setLocale(Locale.KOREA)
                        ready = true
                        Log.i(TAG, "Pleos TextToSpeech ready (ON_DEVICE)")
                        pendingText = WARM_UP_TEXT
                        t.speak(WARM_UP_TEXT, true)
                    } catch (e: Throwable) {
                        Log.w(TAG, "Pleos TextToSpeech setup failed, using Android TTS", e)
                    }
                }
                override fun onFailure(type: ErrorType) {
                    Log.w(TAG, "Pleos TextToSpeech init failed: $type, using Android TTS")
                    ready = false
                }
            })
            tts = t
        } catch (e: Throwable) {
            Log.w(TAG, "Pleos TextToSpeech unavailable, falling back to Android TTS", e)
            tts = null
        }
    }

    override fun speak(text: String, onDone: (() -> Unit)?) {
        val t = tts
        if (t == null || !ready) {
            fallback.speak(text, onDone)
            return
        }
        try {
            if (pendingText != null) flushedStops++
            pendingDone = onDone
            pendingText = text
            t.speak(text, true)
        } catch (e: Throwable) {
            Log.w(TAG, "speak failed, using fallback", e)
            pendingDone = null
            pendingText = null
            fallback.speak(text, onDone)
        }
    }

    override fun stop() {
        flushedStops = 0
        pendingDone = null
        pendingText = null
        try { tts?.stop() } catch (_: Throwable) {}
        fallback.stop()
    }

    override fun release() {
        try { tts?.removeEventListener(events); tts?.release() } catch (_: Throwable) {}
        tts = null
        fallback.release()
    }

    private companion object {
        const val TAG = "PleosTTS"
        const val WARM_UP_TEXT = " "
    }
}
