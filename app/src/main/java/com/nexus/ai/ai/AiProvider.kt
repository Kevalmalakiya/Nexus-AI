package com.nexus.ai.ai

data class ChatTurn(val fromUser: Boolean, val text: String)

/** Provider abstraction: add OpenAI/Claude/etc. by implementing this. */
interface AiProvider {
    suspend fun generate(system: String, history: List<ChatTurn>): Result<String>

    /** Streams text chunks as they arrive. Default: one chunk from generate(). */
    suspend fun stream(system: String, history: List<ChatTurn>, onChunk: (String) -> Unit): Result<String> =
        generate(system, history).onSuccess { onChunk(it) }
}
