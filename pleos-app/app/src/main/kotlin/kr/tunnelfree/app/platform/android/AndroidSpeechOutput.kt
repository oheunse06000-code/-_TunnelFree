package kr.tunnelfree.app.platform.android

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kr.tunnelfree.app.platform.SpeechOutput
import java.util.Locale

class AndroidSpeechOutput(context: Context) : SpeechOutput {

    private var ready = false
    private var pending: Pair<String, (() -> Unit)?>? = null
    private val callbacks = HashMap<String, () -> Unit>()
    private var counter = 0

    private lateinit var tts: TextToSpeech

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ready = true
                tts.language = Locale.KOREAN
                tts.setSpeechRate(0.9f)
                pending?.let { (text, onDone) -> speak(text, onDone) }
                pending = null
            }
        }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                utteranceId?.let { callbacks.remove(it)?.invoke() }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                utteranceId?.let { callbacks.remove(it) }
            }
        })
    }

    override fun speak(text: String, onDone: (() -> Unit)?) {
        if (!ready) {
            pending = text to onDone
            return
        }
        val id = "tf-${counter++}"
        if (onDone != null) callbacks[id] = onDone
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), id)
    }

    override fun stop() {
        callbacks.clear()
        if (ready) tts.stop()
    }

    override fun release() {
        stop()
        tts.shutdown()
    }
}
