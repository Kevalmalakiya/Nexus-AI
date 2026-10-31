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
import android.speech.tts.Voice
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

enum class VoicePhase { IDLE, LISTENING, THINKING, SPEAKING }

/** STT + sentence-queued TTS. Mic level drives the orb. */
class VoiceEngine(private val ctx: Context) {
    val level = MutableStateFlow(0f)
    var speechRate = 1.05f
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    @Volatile private var ttsReady = false

    private val pending = AtomicInteger(0)
    @Volatile private var sealed = false
    @Volatile private var doneCb: (() -> Unit)? = null
    @Volatile private var langSet = false
    @Volatile private var session = 0
    private var uid = 0

    init {
        tts = TextToSpeech(ctx) { st ->
            ttsReady = st == TextToSpeech.SUCCESS
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) { finishOne(id) }
                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) { finishOne(id) }
            })
        }
    }

    private fun fire() {
        val cb = doneCb
        doneCb = null
        cb?.invoke()
    }

    private fun finishOne(id: String?) {
        if (id == null || !id.startsWith("s${session}_")) return
        if (pending.decrementAndGet() <= 0 && sealed) fire()
    }

    // ---------- speaking (queue sentences as they stream in) ----------
    fun beginSpeech(onDone: () -> Unit) {
        stopSpeaking()
        session++
        pending.set(0)
        sealed = false
        langSet = false
        doneCb = onDone
    }

    fun enqueue(text: String) {
        val clean = text.replace(Regex("[\\p{So}\\p{Cs}*_#`~]"), "").trim()
        val engine = tts
        if (!ttsReady || engine == null || clean.isEmpty()) return
        if (!langSet) { langSet = true; applyVoice(engine, clean) }
        pending.incrementAndGet()
        val r = engine.speak(clean, TextToSpeech.QUEUE_ADD, null, "s${session}_${uid++}")
        if (r != TextToSpeech.SUCCESS) pending.decrementAndGet()
    }

    fun endSpeech() {
        sealed = true
        if (pending.get() <= 0) fire()
    }

    fun stopSpeaking() {
        doneCb = null
        sealed = true
        pending.set(0)
        tts?.stop()
    }

    private fun applyVoice(engine: TextToSpeech, sample: String) {
        val deva = sample.any { it in '\u0900'..'\u097F' }
        val loc = if (deva) Locale("hi", "IN") else Locale("en", "IN")
        engine.language = loc
        engine.setSpeechRate(speechRate)
        engine.setPitch(1.0f)
        runCatching {
            val best = engine.voices
                ?.filter { it.locale.language == loc.language && it.features?.contains("notInstalled") != true }
                ?.sortedWith(compareByDescending<Voice> { it.quality }
                    .thenByDescending { it.locale.country == loc.country })
                ?.firstOrNull()
            if (best != null) engine.voice = best
        }
    }

    // ---------- listening ----------
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

    fun shutdown() {
        recognizer?.destroy()
        tts?.shutdown()
    }
}
