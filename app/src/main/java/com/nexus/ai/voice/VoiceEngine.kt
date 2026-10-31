package com.nexus.ai.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Locale

enum class VoicePhase { IDLE, LISTENING, THINKING, SPEAKING }

/** Android SpeechRecognizer (STT) + TextToSpeech (TTS). Mic level drives the orb. */
class VoiceEngine(private val ctx: Context) {
    val level = MutableStateFlow(0f)
    var speechRate = 1.0f
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    init { tts = TextToSpeech(ctx) { ttsReady = it == TextToSpeech.SUCCESS } }

    fun listen(lang: String = "hi-IN", onText: (String) -> Unit, onFail: (String) -> Unit) {
        main.post {
            stopSpeaking()
            if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
                onFail("Speech recognition available nahi hai (Google app chahiye)")
                return@post
            }
            recognizer?.destroy()
            val r = SpeechRecognizer.createSpeechRecognizer(ctx)
            r.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(p: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rms: Float) { level.value = ((rms + 2f) / 12f).coerceIn(0f, 1f) }
                override fun onBufferReceived(b: ByteArray?) {}
                override fun onEndOfSpeech() { level.value = 0f }
                override fun onPartialResults(b: Bundle?) {}
                override fun onEvent(t: Int, b: Bundle?) {}
                override fun onError(e: Int) {
                    level.value = 0f
                    onFail(when (e) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                            "Kuch sunai nahi diya. Orb ko phir tap karo"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Mic permission chahiye"
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Internet check karo"
                        else -> "Speech error ($e)"
                    })
                }
                override fun onResults(b: Bundle?) {
                    level.value = 0f
                    val t = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (t.isNullOrBlank()) onFail("Samajh nahi aaya, phir bolo") else onText(t)
                }
            })
            recognizer = r
            r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang))
        }
    }

    fun stopListening() { main.post { recognizer?.stopListening() } }
    fun cancelListening() { main.post { recognizer?.cancel(); level.value = 0f } }

    fun speak(text: String, finished: () -> Unit) {
        val clean = text.replace(Regex("[\\p{So}\\p{Cs}*_#`~]"), "").trim()
        val engine = tts
        if (!ttsReady || engine == null || clean.isEmpty()) { finished(); return }
        val deva = clean.any { it in '\u0900'..'\u097F' }
        engine.language = if (deva) Locale("hi", "IN") else Locale("en", "IN")
        engine.setSpeechRate(speechRate)
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) { finished() }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) { finished() }
        })
        engine.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "nexus")
    }

    fun stopSpeaking() { tts?.stop() }

    fun shutdown() {
        recognizer?.destroy()
        tts?.shutdown()
    }
}
