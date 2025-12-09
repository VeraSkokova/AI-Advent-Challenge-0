package ru.skokova.chatwithygpt.models

data class GenerationResult(
    val text: String,
    val inputTokens: Int,
    val outputTokens: Int,
    val costRub: Double,
    val durationMs: Long,
    val modelName: String
)
