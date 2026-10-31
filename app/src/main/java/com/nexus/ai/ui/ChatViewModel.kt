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
