#!/data/data/com.termux/files/usr/bin/bash
# NEXUS AI - Phase 2 patch (voice-first orb UI). Run inside ~/Nexus-AI
set -e
P=app/src/main/java/com/nexus/ai
[ -d $P ] || { echo "Pehle: cd ~/Nexus-AI"; exit 1; }
mkdir -p $P/voice

cat > app/src/main/AndroidManifest.xml <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <queries>
        <intent><action android:name="android.speech.RecognitionService" /></intent>
        <intent><action android:name="android.intent.action.TTS_SERVICE" /></intent>
    </queries>
    <application
        android:label="NEXUS AI"
        android:allowBackup="false"
        android:networkSecurityConfig="@xml/network_security_config"
        android:theme="@android:style/Theme.Material.NoActionBar">
        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
EOF

cat > $P/ai/GeminiProvider.kt <<'EOF'
package com.nexus.ai.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class GeminiProvider(
    private val apiKey: String,
    private val model: String = "gemini-flash-latest" // alias; change if your key needs another model
) : AiProvider {

    override suspend fun generate(system: String, history: List<ChatTurn>): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val contents = JSONArray()
                history.forEach {
                    contents.put(JSONObject()
                        .put("role", if (it.fromUser) "user" else "model")
                        .put("parts", JSONArray().put(JSONObject().put("text", it.text))))
                }
                val body = JSONObject()
                    .put("system_instruction", JSONObject()
                        .put("parts", JSONArray().put(JSONObject().put("text", system))))
                    .put("contents", contents)

                val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15000
                    readTimeout = 60000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("x-goog-api-key", apiKey)
                }
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
                val code = conn.responseCode
                if (code !in 200..299) {
                    val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    val msg = runCatching { JSONObject(err).getJSONObject("error").getString("message") }.getOrDefault("")
                    throw IOException("Gemini HTTP $code: ${msg.take(160)}")
                }
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                JSONObject(text).getJSONArray("candidates").getJSONObject(0)
                    .getJSONObject("content").getJSONArray("parts").getJSONObject(0)
                    .getString("text")
            }
        }
}
EOF

cat > $P/voice/VoiceEngine.kt <<'EOF'
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
EOF

cat > $P/ui/ChatViewModel.kt <<'EOF'
package com.nexus.ai.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.ai.ai.AiProvider
import com.nexus.ai.ai.ChatTurn
import com.nexus.ai.ai.GeminiProvider
import com.nexus.ai.personality.PersonalityEngine
import com.nexus.ai.security.SecureStore
import com.nexus.ai.voice.VoiceEngine
import com.nexus.ai.voice.VoicePhase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val messages: List<ChatTurn> = emptyList(),
    val loading: Boolean = false,
    val hasKey: Boolean = false,
    val error: String? = null
)

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val store = SecureStore(app)
    private var provider: AiProvider? = store.apiKey()?.let { GeminiProvider(it) }
    private val _state = MutableStateFlow(UiState(hasKey = provider != null))
    val state: StateFlow<UiState> = _state

    val voice = VoiceEngine(app)
    private val _phase = MutableStateFlow(VoicePhase.IDLE)
    val phase: StateFlow<VoicePhase> = _phase
    val heard = MutableStateFlow("")
    val reply = MutableStateFlow("")
    var continuous = true // after Nexus speaks, listen again automatically

    fun saveKey(key: String) {
        if (key.isBlank()) return
        store.saveApiKey(key)
        provider = GeminiProvider(key.trim())
        _state.update { it.copy(hasKey = true, error = null) }
    }

    fun clearKey() {
        store.clearApiKey(); provider = null
        _state.update { it.copy(hasKey = false) }
    }

    fun setError(msg: String) = _state.update { it.copy(error = msg) }

    fun send(text: String) = ask(text, speak = false)

    /** Tap on the orb: start / finish listening, or interrupt Nexus and talk. */
    fun onOrbTap() {
        when (_phase.value) {
            VoicePhase.IDLE -> listen()
            VoicePhase.LISTENING -> voice.stopListening()
            VoicePhase.THINKING -> {}
            VoicePhase.SPEAKING -> { voice.stopSpeaking(); listen() }
        }
    }

    fun stopAll() {
        _phase.value = VoicePhase.IDLE
        voice.cancelListening()
        voice.stopSpeaking()
    }

    private fun listen() {
        _state.update { it.copy(error = null) }
        _phase.value = VoicePhase.LISTENING
        voice.listen(
            onText = { t -> heard.value = t; reply.value = ""; ask(t, speak = true) },
            onFail = { msg -> _phase.value = VoicePhase.IDLE; setError(msg) }
        )
    }

    private fun ask(text: String, speak: Boolean) {
        val p = provider ?: return
        if (text.isBlank() || _state.value.loading) return
        val history = _state.value.messages + ChatTurn(true, text.trim())
        _state.update { it.copy(messages = history, loading = true, error = null) }
        if (speak) _phase.value = VoicePhase.THINKING
        val system = PersonalityEngine.systemPrompt() +
            if (speak) "\nVoice mode: reply in 1-3 short spoken sentences. No markdown, no lists." else ""
        viewModelScope.launch {
            p.generate(system, history)
                .onSuccess { r ->
                    _state.update { it.copy(messages = history + ChatTurn(false, r), loading = false) }
                    if (speak && _phase.value == VoicePhase.THINKING) {
                        reply.value = r
                        _phase.value = VoicePhase.SPEAKING
                        voice.speak(r) {
                            if (continuous && _phase.value == VoicePhase.SPEAKING) listen()
                            else _phase.value = VoicePhase.IDLE
                        }
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(loading = false, error = e.message ?: "Request failed") }
                    if (speak) _phase.value = VoicePhase.IDLE
                }
        }
    }

    override fun onCleared() { voice.shutdown() }
}
EOF

cat > $P/ui/NexusOrb.kt <<'EOF'
package com.nexus.ai.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.nexus.ai.voice.VoicePhase
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Animated orb. Listening: real mic level. Speaking: simulated pulse. Thinking: spinning ring. */
@Composable
fun NexusOrb(phase: VoicePhase, level: Float, modifier: Modifier = Modifier) {
    val inf = rememberInfiniteTransition(label = "orb")
    val t by inf.animateFloat(
        0f, (2 * PI).toFloat(),
        infiniteRepeatable(tween(4000, easing = LinearEasing)), label = "t"
    )
    val pulse by inf.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "p"
    )
    val lv by animateFloatAsState(level, spring(stiffness = Spring.StiffnessMedium), label = "lv")

    val (midT, edgeT) = when (phase) {
        VoicePhase.LISTENING -> Color(0xFF8FE9FF) to Color(0xFF3D7BFF)
        VoicePhase.THINKING -> Color(0xFFFFC2F0) to Color(0xFFB04DFF)
        VoicePhase.SPEAKING -> Color(0xFFD7C8FF) to Color(0xFF7B5CFF)
        VoicePhase.IDLE -> Color(0xFFC9BFFF) to Color(0xFF5B4BD6)
    }
    val mid by animateColorAsState(midT, tween(500), label = "mid")
    val edge by animateColorAsState(edgeT, tween(500), label = "edge")

    val amp = when (phase) {
        VoicePhase.LISTENING -> 0.04f + lv * 0.22f
        VoicePhase.SPEAKING -> 0.05f + 0.10f * pulse * (0.6f + 0.4f * sin(t * 5f))
        VoicePhase.THINKING -> 0.03f
        VoicePhase.IDLE -> 0.02f + 0.01f * pulse
    }

    Canvas(modifier) {
        val c = center
        val base = size.minDimension * 0.26f * (1f + if (phase == VoicePhase.LISTENING) lv * 0.15f else 0f)

        drawCircle(
            brush = Brush.radialGradient(listOf(edge.copy(alpha = 0.40f), Color.Transparent), center = c, radius = base * 2.3f),
            radius = base * 2.3f, center = c
        )

        val path = Path()
        val n = 90
        for (i in 0..n) {
            val a = i / n.toFloat() * 2f * PI.toFloat()
            val r = base * (1f + amp * sin(a * 3f + t * 2f) + amp * 0.6f * sin(a * 5f - t * 3f))
            val x = c.x + r * cos(a)
            val y = c.y + r * sin(a)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        drawPath(
            path,
            Brush.radialGradient(
                listOf(Color.White, mid, edge),
                center = Offset(c.x - base * 0.3f, c.y - base * 0.3f),
                radius = base * 1.6f
            )
        )

        if (phase == VoicePhase.THINKING) {
            val ring = base * 1.35f
            drawArc(
                color = mid, startAngle = t * 57.3f * 2f, sweepAngle = 100f, useCenter = false,
                topLeft = Offset(c.x - ring, c.y - ring), size = Size(ring * 2, ring * 2),
                style = Stroke(width = 8f, cap = StrokeCap.Round)
            )
        }
    }
}
EOF

cat > $P/ui/VoiceScreen.kt <<'EOF'
package com.nexus.ai.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.nexus.ai.voice.VoicePhase

@Composable
fun VoiceScreen(vm: ChatViewModel, onKeyboard: () -> Unit) {
    val phase by vm.phase.collectAsState()
    val level by vm.voice.level.collectAsState()
    val heard by vm.heard.collectAsState()
    val reply by vm.reply.collectAsState()
    val s by vm.state.collectAsState()
    val ctx = LocalContext.current

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) vm.onOrbTap() else vm.setError("Voice ke liye mic permission chahiye")
    }
    fun tap() {
        val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) vm.onOrbTap() else launcher.launch(Manifest.permission.RECORD_AUDIO)
    }

    val status = when (phase) {
        VoicePhase.IDLE -> "Orb ko tap karke bolo"
        VoicePhase.LISTENING -> "Sun rahi hoon…"
        VoicePhase.THINKING -> "Soch rahi hoon…"
        VoicePhase.SPEAKING -> "Bol rahi hoon… (tap karke beech me bol sakte ho)"
    }

    Column(
        Modifier.fillMaxSize().background(Color(0xFF05050D))
            .statusBarsPadding().navigationBarsPadding().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("NEXUS", style = MaterialTheme.typography.headlineSmall, color = Color(0xFFB9A8FF))
        Spacer(Modifier.height(4.dp))
        Text(status, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)

        Spacer(Modifier.weight(1f))
        NexusOrb(
            phase, level,
            Modifier.fillMaxWidth().aspectRatio(1f).clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null
            ) { tap() }
        )
        Spacer(Modifier.weight(1f))

        if (heard.isNotBlank()) Text("Tum: $heard", color = Color.White.copy(alpha = 0.6f), textAlign = TextAlign.Center, maxLines = 3)
        if (reply.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(reply, color = Color.White, textAlign = TextAlign.Center, maxLines = 5)
        }
        s.error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        }

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onKeyboard) { Text("Type") }
            TextButton(onClick = { vm.stopAll() }) { Text("Stop") }
        }
    }
}
EOF

cat > $P/MainActivity.kt <<'EOF'
package com.nexus.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexus.ai.ui.ChatScreen
import com.nexus.ai.ui.ChatViewModel
import com.nexus.ai.ui.VoiceScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF8E7CFF),
                    background = Color(0xFF0B0B17),
                    surface = Color(0xFF15152A)
                )
            ) {
                val vm: ChatViewModel = viewModel()
                val s by vm.state.collectAsState()
                var textMode by remember { mutableStateOf(false) }
                if (!s.hasKey || textMode) {
                    Box(Modifier.fillMaxSize()) {
                        ChatScreen(vm)
                        if (s.hasKey) {
                            TextButton(
                                onClick = { textMode = false },
                                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding()
                            ) { Text("Voice") }
                        }
                    }
                } else {
                    VoiceScreen(vm, onKeyboard = { textMode = true })
                }
            }
        }
    }
}
EOF

cat >> CHANGELOG.md <<'EOF'

## v0.2.0 (unreleased)
- Voice-first UI: animated orb (mic-level reactive), Hindi/English STT + TTS, tap to interrupt.
- Model alias gemini-flash-latest; API error message shown.
EOF

echo "Phase 2 patch applied."
rm -- "$0"

