package kr.tunnelfree.app.platform.android

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kr.tunnelfree.app.platform.SpeechInput

class AndroidSpeechInput(private val context: Context) : SpeechInput {

    private var recognizer: SpeechRecognizer? = null
    override var isListening: Boolean = false
        private set

    override fun start(onResult: (String) -> Unit, onEnd: () -> Unit, onError: (String) -> Unit, onReady: () -> Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("이 기기에서 음성 인식을 사용할 수 없습니다.")
            onEnd()
            return
        }
        stop()
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { isListening = true; onReady() }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { isListening = false }
            override fun onError(error: Int) {
                isListening = false
                val message = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> ""
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "마이크 권한이 필요합니다."
                    else -> "음성 인식 오류 ($error)"
                }
                if (message.isNotEmpty()) onError(message)
                onEnd()
            }
            override fun onResults(results: Bundle?) {
                isListening = false
                val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val best = list?.firstOrNull().orEmpty()
                if (best.isNotEmpty()) onResult(best)
                onEnd()
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        isListening = true
        r.startListening(intent)
    }

    override fun stop() {
        isListening = false
        recognizer?.let {
            it.cancel()
            it.destroy()
        }
        recognizer = null
    }

    override fun release() = stop()
}
