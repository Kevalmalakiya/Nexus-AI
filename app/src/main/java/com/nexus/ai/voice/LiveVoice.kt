package com.nexus.ai.voice

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

enum class VoicePhase { IDLE, LISTENING, THINKING, SPEAKING }

/** Gemini Live API: mic PCM 16k in, model PCM 24k out, plus tool calls, over one WebSocket. */
class LiveVoice(private val ctx: Context) {
    val level = MutableStateFlow(0f)
    var onPhase: (VoicePhase) -> Unit = {}
    var onUserText: (String) -> Unit = {}
    var onModelText: (String) -> Unit = {}
    var onError: (String) -> Unit = {}
    var onToolCall: (String, JSONObject) -> String = { _, _ -> "Not supported" }

    @Volatile private var running = false
    @Volatile private var phase = VoicePhase.IDLE
    @Volatile private var muteUntil = 0L
    @Volatile private var endAt = 0L
    @Volatile private var discarding = false
    @Volatile private var turnOpen = false
    @Volatile private var headset = false
    private var audioStarted = false
    private var ws: WebSocket? = null
    private var recorder: AudioRecord? = null
    private var track: AudioTrack? = null
    private val playQ = LinkedBlockingQueue<ByteArray>()
    private val END = ByteArray(0)
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private fun setPhase(p: VoicePhase) {
        if (phase != p) { phase = p; onPhase(p) }
    }

    fun start(apiKey: String, model: String, system: String, voiceName: String, tools: JSONArray) {
        stop()
        running = true
        val url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=$apiKey"
        ws = client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(setupJson(model, system, voiceName, tools))
            }
            override fun onMessage(webSocket: WebSocket, text: String) = handle(text)
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) = handle(bytes.utf8())
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                fail("Session band ($code): ${reason.take(200)}")
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val http = response?.let { " HTTP ${it.code}" } ?: ""
                fail("Connection fail$http: ${t.message ?: ""}".take(220))
            }
        })
    }

    private fun setupJson(model: String, system: String, voiceName: String, tools: JSONArray): String =
        JSONObject().put("setup", JSONObject()
            .put("model", "models/$model")
            .put("generationConfig", JSONObject()
                .put("responseModalities", JSONArray().put("AUDIO"))
                .put("speechConfig", JSONObject().put("voiceConfig",
                    JSONObject().put("prebuiltVoiceConfig", JSONObject().put("voiceName", voiceName)))))
            .put("systemInstruction", JSONObject()
                .put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("tools", tools)
            .put("inputAudioTranscription", JSONObject())
            .put("outputAudioTranscription", JSONObject())
        ).toString()

    private fun handleToolCall(tc: JSONObject) {
        val calls = tc.optJSONArray("functionCalls") ?: return
        val responses = JSONArray()
        for (i in 0 until calls.length()) {
            val c = calls.getJSONObject(i)
            val name = c.optString("name")
            val result = runCatching { onToolCall(name, c.optJSONObject("args") ?: JSONObject()) }
                .getOrElse { "Failed" }
            responses.put(JSONObject()
                .put("id", c.optString("id"))
                .put("name", name)
                .put("response", JSONObject().put("result", result)))
        }
        ws?.send(JSONObject().put("toolResponse",
            JSONObject().put("functionResponses", responses)).toString())
    }

    private fun handle(text: String) {
        val j = runCatching { JSONObject(text) }.getOrNull() ?: return
        if (j.has("setupComplete")) { startAudio(); return }
        if (j.has("goAway")) { fail("Session time limit khatam. Orb tap karke dobara shuru karo"); return }
        j.optJSONObject("toolCall")?.let { handleToolCall(it); return }
        val sc = j.optJSONObject("serverContent") ?: return

        sc.optJSONObject("inputTranscription")?.optString("text", "")
            ?.takeIf { it.isNotEmpty() }?.let(onUserText)
        sc.optJSONObject("outputTranscription")?.optString("text", "")
            ?.takeIf { it.isNotEmpty() }?.let(onModelText)

        val parts = sc.optJSONObject("modelTurn")?.optJSONArray("parts")
        if (parts != null && !discarding) {
            for (i in 0 until parts.length()) {
                val data = parts.optJSONObject(i)?.optJSONObject("inlineData")?.optString("data", "") ?: ""
                if (data.isEmpty()) continue
                turnOpen = true
                if (phase != VoicePhase.SPEAKING) setPhase(VoicePhase.SPEAKING)
                playQ.put(Base64.decode(data, Base64.DEFAULT))
            }
        }
        if (sc.optBoolean("interrupted")) {
            discarding = false; turnOpen = false
            clearPlayback()
            setPhase(VoicePhase.LISTENING)
        }
        if (sc.optBoolean("turnComplete")) {
            discarding = false
            playQ.put(END)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startAudio() {
        if (audioStarted || !running) return
        audioStarted = true
        val rate = 16000
        val minIn = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, rate,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minIn, 4096) * 2)
        if (rec.state != AudioRecord.STATE_INITIALIZED) { fail("Mic start nahi hua"); return }
        runCatching { if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(rec.audioSessionId)?.enabled = true }
        runCatching { if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(rec.audioSessionId)?.enabled = true }
        recorder = rec

        val outMin = AudioTrack.getMinBufferSize(24000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val tr = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(24000)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(maxOf(outMin, 4096) * 3)
            .setTransferMode(AudioTrack.MODE_STREAM).build()
        tr.play()
        track = tr
        Thread { micLoop(rec) }.start()
        Thread { playLoop(tr) }.start()
        setPhase(VoicePhase.LISTENING)
    }

    private fun headsetNow(): Boolean = runCatching {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                it.type == 26 // BLE headset
        }
    }.getOrDefault(false)

    private fun micLoop(rec: AudioRecord) {
        try {
            rec.startRecording()
            val buf = ShortArray(1024)
            var lastLoud = 0L
            var heard = false
            var lastHs = 0L
            while (running) {
                val n = rec.read(buf, 0, buf.size)
                if (n <= 0) continue
                val now = System.currentTimeMillis()
                if (now - lastHs > 1500) { headset = headsetNow(); lastHs = now }
                // No headphones: don't feed the speaker's own voice back into the mic.
                if (!headset && (phase == VoicePhase.SPEAKING || now < muteUntil)) { heard = false; continue }
                var sum = 0.0
                for (i in 0 until n) { val v = buf[i].toDouble(); sum += v * v }
                val rms = Math.sqrt(sum / n) / 32768.0
                if (phase != VoicePhase.SPEAKING) level.value = (rms * 9).toFloat().coerceIn(0f, 1f)
                if (rms > 0.02) {
                    lastLoud = now; heard = true
                    if (phase == VoicePhase.THINKING) setPhase(VoicePhase.LISTENING)
                } else if (heard && phase == VoicePhase.LISTENING && now - lastLoud > 800) {
                    heard = false
                    setPhase(VoicePhase.THINKING)
                }
                val bytes = ByteArray(n * 2)
                for (i in 0 until n) {
                    bytes[2 * i] = (buf[i].toInt() and 0xFF).toByte()
                    bytes[2 * i + 1] = (buf[i].toInt() shr 8).toByte()
                }
                val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                ws?.send("""{"realtimeInput":{"audio":{"data":"$b64","mimeType":"audio/pcm;rate=16000"}}}""")
            }
        } catch (_: Exception) {}
    }

    private fun playLoop(tr: AudioTrack) {
        try {
            while (running) {
                val item = playQ.poll(200, TimeUnit.MILLISECONDS) ?: continue
                if (item === END) {
                    while (running && playQ.isEmpty() && System.currentTimeMillis() < endAt) Thread.sleep(40)
                    if (running && playQ.isEmpty()) {
                        muteUntil = System.currentTimeMillis() + 500
                        level.value = 0f
                        turnOpen = false
                        setPhase(VoicePhase.LISTENING)
                    }
                    continue
                }
                val n = item.size / 2
                var sum = 0.0
                for (i in 0 until n) {
                    val v = ((item[2 * i + 1].toInt() shl 8) or (item[2 * i].toInt() and 0xFF)).toShort().toDouble()
                    sum += v * v
                }
                level.value = (Math.sqrt(sum / maxOf(n, 1)) / 32768.0 * 6).toFloat().coerceIn(0f, 1f)
                tr.write(item, 0, item.size)
                endAt = maxOf(endAt, System.currentTimeMillis()) + n * 1000L / 24000
            }
        } catch (_: Exception) {}
    }

    private fun clearPlayback() {
        playQ.clear()
        endAt = 0
        runCatching { track?.pause(); track?.flush(); track?.play() }
        level.value = 0f
    }

    /** User tapped the orb while Nexus is speaking. */
    fun interrupt() {
        if (turnOpen) discarding = true
        clearPlayback()
        muteUntil = 0
        setPhase(VoicePhase.LISTENING)
    }

    fun stop() {
        running = false
        runCatching { ws?.close(1000, "bye") }
        ws = null
        runCatching { recorder?.stop(); recorder?.release() }
        recorder = null
        runCatching { track?.stop(); track?.release() }
        track = null
        audioStarted = false
        playQ.clear()
        level.value = 0f
        endAt = 0; discarding = false; turnOpen = false; muteUntil = 0
        setPhase(VoicePhase.IDLE)
    }

    private fun fail(msg: String) {
        if (!running) return
        stop()
        onError(msg)
    }
}
