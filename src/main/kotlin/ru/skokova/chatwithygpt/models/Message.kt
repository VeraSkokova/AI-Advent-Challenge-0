package ru.skokova.chatwithygpt.models

import kotlinx.serialization.Serializable

@Serializable
data class Message(
    val role: String,      // "user", "assistant", "system"
    val text: String
)

@Serializable
data class CompletionOptions(
    val stream: Boolean = false,
    val temperature: Double = 0.6,
    val maxTokens: Int = 1000
)

@Serializable
data class GptRequest(
    val modelUri: String,
    val completionOptions: CompletionOptions,
    val messages: List<Message>,
    val jsonObject: Boolean = false
)
