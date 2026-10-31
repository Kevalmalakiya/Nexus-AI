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
    private var key: String? = store.apiKey()
    val model = MutableStateFlow(store.model())
    val userName = MutableStateFlow(store.userName().ifBlank { "Dost" })
    private var provider: AiProvider? = key?.let { GeminiProvider(it, model.value) }
    private val _state = MutableStateFlow(UiState(hasKey = provider != null))
    val state: StateFlow<UiState> = _state

    val voice = VoiceEngine(app)
    private val _phase = MutableStateFlow(VoicePhase.IDLE)
    val phase: StateFlow<VoicePhase> = _phase
    val heard = MutableStateFlow("")
    val reply = MutableStateFlow("")
    val continuous = MutableStateFlow(true)
    @Volatile private var genId = 0

    private fun rebuild() { provider = key?.let { GeminiProvider(it, model.value) } }

    fun saveKey(k: String) {
        if (k.isBlank()) return
        store.saveApiKey(k); key = k.trim(); rebuild()
        _state.update { it.copy(hasKey = true, error = null) }
    }

    fun clearKey() {
        store.clearApiKey(); key = null; provider = null
        _state.update { it.copy(hasKey = false) }
    }

    fun saveModel(m: String) { store.saveModel(m); model.value = m.trim(); rebuild() }
    fun saveName(n: String) { store.saveName(n); userName.value = n.trim().ifBlank { "Dost" } }
    fun setError(msg: String) = _state.update { it.copy(error = msg) }

    private fun baseSystem() = PersonalityEngine.systemPrompt() + "\nThe user's name is ${userName.value}."

    // ---------- text chat ----------
    fun send(text: String) {
        val p = provider ?: return
        if (text.isBlank() || _state.value.loading) return
        val history = _state.value.messages + ChatTurn(true, text.trim())
        _state.update { it.copy(messages = history, loading = true, error = null) }
        viewModelScope.launch {
            p.generate(baseSystem(), history)
                .onSuccess { r -> _state.update { it.copy(messages = history + ChatTurn(false, r), loading = false) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.message ?: "Request failed") } }
        }
    }

    // ---------- voice ----------
    fun onOrbTap() {
        when (_phase.value) {
            VoicePhase.IDLE -> listen()
            VoicePhase.LISTENING -> voice.stopListening()
            VoicePhase.THINKING -> {}
            VoicePhase.SPEAKING -> {
                genId++
                voice.stopSpeaking()
                _state.update { it.copy(loading = false) }
                listen()
            }
        }
    }

    fun stopAll() {
        genId++
        _phase.value = VoicePhase.IDLE
        voice.cancelListening()
        voice.stopSpeaking()
        _state.update { it.copy(loading = false) }
    }

    private fun listen() {
        _state.update { it.copy(error = null) }
        _phase.value = VoicePhase.LISTENING
        voice.listen(
            onText = { t -> heard.value = t; reply.value = ""; askVoice(t) },
            onFail = { msg -> _phase.value = VoicePhase.IDLE; setError(msg) }
        )
    }

    private fun say(gen: Int, s: String) {
        if (gen != genId) return
        if (_phase.value == VoicePhase.THINKING) _phase.value = VoicePhase.SPEAKING
        reply.value = (reply.value + " " + s).trim()
        voice.enqueue(s)
    }

    private fun drain(buf: StringBuilder, flush: Boolean): List<String> {
        val out = mutableListOf<String>()
        while (true) {
            val i = buf.indexOfFirst { it == '.' || it == '!' || it == '?' || it == '।' || it == '\n' }
            if (i < 0) break
            val s = buf.substring(0, i + 1).trim()
            buf.delete(0, i + 1)
            if (s.length > 1) out.add(s)
        }
        if (flush && buf.isNotBlank()) { out.add(buf.toString().trim()); buf.clear() }
        return out
    }

    private fun askVoice(text: String) {
        val p = provider ?: return
        if (text.isBlank()) return
        val gen = ++genId
        val history = _state.value.messages + ChatTurn(true, text.trim())
        _state.update { it.copy(messages = history, loading = true, error = null) }
        _phase.value = VoicePhase.THINKING
        val system = baseSystem() + PersonalityEngine.VOICE_RULES
        voice.beginSpeech {
            if (gen == genId) {
                if (continuous.value) listen() else _phase.value = VoicePhase.IDLE
            }
        }
        val buf = StringBuilder()
        val full = StringBuilder()
        viewModelScope.launch {
            val r = p.stream(system, history) { chunk ->
                if (gen == genId) {
                    full.append(chunk)
                    buf.append(chunk)
                    for (s in drain(buf, false)) say(gen, s)
                }
            }
            if (gen != genId) return@launch
            r.onSuccess {
                for (s in drain(buf, true)) say(gen, s)
                _state.update { st -> st.copy(messages = history + ChatTurn(false, full.toString()), loading = false) }
                voice.endSpeech()
            }.onFailure { e ->
                _state.update { st -> st.copy(loading = false, error = e.message ?: "Request failed") }
                _phase.value = VoicePhase.IDLE
                voice.stopSpeaking()
            }
        }
    }

    override fun onCleared() { voice.shutdown() }
}
