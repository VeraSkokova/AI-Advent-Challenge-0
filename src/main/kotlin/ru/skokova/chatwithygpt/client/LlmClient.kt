package ru.skokova.chatwithygpt.client

import ru.skokova.chatwithygpt.data.Persona
import ru.skokova.chatwithygpt.models.GenerationResult
import ru.skokova.chatwithygpt.models.Message
import ru.skokova.chatwithygpt.models.ModelConfig

interface LlmClient {
    suspend fun sendMessage(
        messages: List<Message>,
        persona: Persona,
        model: ModelConfig
    ): Result<GenerationResult>

    suspend fun close()
}
