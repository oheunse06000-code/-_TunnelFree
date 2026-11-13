package kr.tunnelfree.app.platform.pleos

import android.content.Context
import android.util.Log
import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import kr.tunnelfree.app.platform.SpeechInput
import java.util.Locale

class PleosSpeechInput(
    context: Context,
    private val fallback: SpeechInput,
) : SpeechInput {

    private var stt: SpeechToText? = null
    private var ready = false
    private var usingPleos = false
    private var pleosListening = false

    private var onResult: ((String) -> Unit)? = null
    private var onEnd: (() -> Unit)? = null
    private var onError: ((String) -> Unit)? = null
    private var onReady: (() -> Unit)? = null
    private var delivered = false

    override val isListening: Boolean
        get() = if (usingPleos) pleosListening else fallback.isListening

    private fun notifyReady() {
        val ready = onReady
        onReady = null
        ready?.invoke()
    }

    private val results = object : ResultListener {
        override fun onReady() { notifyReady() }
        override fun onStartedRecognition() { pleosListening = true; delivered = false; notifyReady() }
        override fun onUpdated(text: String, isFinal: Boolean) {
            if (isFinal && !delivered && text.isNotBlank()) {
                delivered = true
                onResult?.invoke(text)
            }
        }
        override fun onUpdatedEpdData(start: Long, end: Long) {}
        override fun onUpdatedRms(rms: Double) {}
        override fun onEndedRecognition() { pleosListening = false; onEnd?.invoke() }
        override fun onError(type: ErrorType) {
            pleosListening = false
            onError?.invoke("음성 인식 오류 ($type)")
            onEnd?.invoke()
        }
    }

    init {
        try {
            val s = SpeechToText(context.applicationContext, Mode.ON_DEVICE)
            s.addListener(results)
            s.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    ready = true
                    try { s.setLocale(Locale.KOREA) } catch (_: Throwable) {}
                    Log.i(TAG, "Pleos SpeechToText ready (ON_DEVICE)")
                }
                override fun onFailure(type: ErrorType) {
                    Log.w(TAG, "Pleos SpeechToText init failed: $type, using Android SpeechRecognizer")
                    ready = false
                }
            })
            stt = s
        } catch (e: Throwable) {
            Log.w(TAG, "Pleos SpeechToText unavailable, falling back", e)
            stt = null
        }
    }

    override fun start(onResult: (String) -> Unit, onEnd: () -> Unit, onError: (String) -> Unit, onReady: () -> Unit) {
        val s = stt
        if (s == null || !ready) {
            usingPleos = false
            fallback.start(onResult, onEnd, onError, onReady)
            return
        }
        this.onResult = onResult
        this.onEnd = onEnd
        this.onError = onError
        this.onReady = onReady
        try {
            usingPleos = true
            pleosListening = true
            delivered = false
            s.request()
        } catch (e: Throwable) {
            Log.w(TAG, "request failed, using fallback", e)
            pleosListening = false
            usingPleos = false
            this.onReady = null
            fallback.start(onResult, onEnd, onError, onReady)
        }
    }

    override fun stop() {
        onReady = null
        if (usingPleos) {
            try { stt?.stop() } catch (_: Throwable) {}
            pleosListening = false
        }
        fallback.stop()
    }

    override fun release() {
        try { stt?.removeListener(results); stt?.release() } catch (_: Throwable) {}
        stt = null
        fallback.release()
    }

    private companion object { const val TAG = "PleosSTT" }
}
