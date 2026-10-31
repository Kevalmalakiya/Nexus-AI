package com.nexus.ai.ai

data class ChatTurn(val fromUser: Boolean, val text: String)

/** Provider abstraction: add OpenAI/Claude/etc. by implementing this. */
interface AiProvider {
    suspend fun generate(system: String, history: List<ChatTurn>): Result<String>
}
