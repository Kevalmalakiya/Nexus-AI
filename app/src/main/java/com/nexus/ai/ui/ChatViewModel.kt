package com.nexus.ai.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.nexus.ai.personality.PersonalityEngine
import com.nexus.ai.security.SecureStore
import com.nexus.ai.voice.LiveVoice
import com.nexus.ai.voice.VoicePhase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class UiState(val hasKey: Boolean = false, val error: String? = null)

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    companion object { const val DEFAULT_MODEL = "gemini-3.8-live" }

    private val store = SecureStore(app)
    private var key: String? = store.apiKey()
    val model = MutableStateFlow(store.model())
    val userName = MutableStateFlow(store.userName().ifBlank { "Dost" })
    private val _state = MutableStateFlow(UiState(hasKey = key != null))
    val state: StateFlow<UiState> = _state

    val voice = LiveVoice(app)
    private val _phase = MutableStateFlow(VoicePhase.IDLE)
    val phase: StateFlow<VoicePhase> = _phase
    val heard = MutableStateFlow("")
    val reply = MutableStateFlow("")
    private var lastSpeaker = 0

    init {
        voice.onPhase = { _phase.value = it }
        voice.onError = { msg -> setError(msg) }
        voice.onUserText = { t ->
            if (lastSpeaker != 1) { heard.value = ""; reply.value = "" }
            lastSpeaker = 1
            heard.update { it + t }
        }
        voice.onModelText = { t ->
            if (lastSpeaker != 2) reply.value = ""
            lastSpeaker = 2
            reply.update { it + t }
        }
    }

    fun saveKey(k: String) {
        if (k.isBlank()) return
        store.saveApiKey(k); key = k.trim()
        _state.update { it.copy(hasKey = true, error = null) }
    }

    fun clearKey() {
        voice.stop(); store.clearApiKey(); key = null
        _state.update { it.copy(hasKey = false) }
    }

    fun saveModel(m: String) { store.saveModel(m); model.value = m.trim() }
    fun saveName(n: String) { store.saveName(n); userName.value = n.trim().ifBlank { "Dost" } }
    fun setError(msg: String) = _state.update { it.copy(error = msg) }

    private fun system() = PersonalityEngine.systemPrompt() +
        "\nThe user's name is ${userName.value}." +
        "\nYou are on a live voice call. Speak naturally and briefly (1-3 sentences). " +
        "Reply in the language the user speaks: Hindi, Hinglish or English. Never read out emojis or markdown."

    /** Idle: start the live session. Speaking: interrupt Nexus. */
    fun onOrbTap() {
        when (_phase.value) {
            VoicePhase.IDLE -> {
                val k = key ?: return setError("Pehle Settings me API key daalo")
                _state.update { it.copy(error = null) }
                heard.value = ""; reply.value = ""; lastSpeaker = 0
                _phase.value = VoicePhase.THINKING // connecting
                voice.start(k, model.value.ifBlank { DEFAULT_MODEL }, system(), "Aoede")
            }
            VoicePhase.SPEAKING -> voice.interrupt()
            else -> {}
        }
    }

    fun stopAll() { voice.stop() }

    override fun onCleared() { voice.stop() }
}
