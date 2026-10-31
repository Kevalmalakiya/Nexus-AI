package com.nexus.ai.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.ai.ai.AiProvider
import com.nexus.ai.ai.ChatTurn
import com.nexus.ai.ai.GeminiProvider
import com.nexus.ai.personality.PersonalityEngine
import com.nexus.ai.security.SecureStore
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

    fun send(text: String) {
        val p = provider ?: return
        if (text.isBlank() || _state.value.loading) return
        val history = _state.value.messages + ChatTurn(true, text.trim())
        _state.update { it.copy(messages = history, loading = true, error = null) }
        viewModelScope.launch {
            p.generate(PersonalityEngine.systemPrompt(), history)
                .onSuccess { r -> _state.update { it.copy(messages = history + ChatTurn(false, r), loading = false) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.message ?: "Request failed") } }
        }
    }
}
