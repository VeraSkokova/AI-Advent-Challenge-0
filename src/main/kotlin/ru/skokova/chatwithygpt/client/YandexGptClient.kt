package ru.skokova.chatwithygpt.client

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import ru.skokova.chatwithygpt.config.ApiConfig
import ru.skokova.chatwithygpt.models.CompletionOptions
import ru.skokova.chatwithygpt.models.GptRequest
import ru.skokova.chatwithygpt.models.GptResponse
import ru.skokova.chatwithygpt.models.Message
import ru.skokova.chatwithygpt.utils.Logger

class YandexGptClient(
    private val config: ApiConfig
) {
    private val logger = Logger()
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                isLenient = true
                ignoreUnknownKeys = true
                prettyPrint = false
            })
        }
    }

    private val apiUrl =
        "https://llm.api.cloud.yandex.net/foundationModels/v1/completion"

    suspend fun sendMessage(
        conversationHistory: List<Message>
    ): Result<Pair<String, Int>> = try {
        val messages = listOf(
            Message("system", config.systemPrompt)
        ) + conversationHistory

        val request = GptRequest(
            modelUri = "gpt://${config.folderId}/yandexgpt/${config.modelVersion}",
            completionOptions = CompletionOptions(
                temperature = config.temperature,
                maxTokens = config.maxTokens
            ),
            messages = messages
        )

        val response = client.post(apiUrl) {
            contentType(ContentType.Application.Json)
            header("Authorization", "Api-Key ${config.apiKey}")
            header("x-folder-id", config.folderId)
            setBody(request)
        }

        logger.println("📥 HTTP Status: ${response.status}", Logger.Color.GRAY)

        response.bodyAsText()

        val parsedResponse = response.body<GptResponse>()

        val assistantMessage = parsedResponse.result?.alternatives
            ?.firstOrNull()?.message?.text
            ?: parsedResponse.alternatives?.firstOrNull()?.message?.text
            ?: "No response"

        val tokensUsed = parsedResponse.result?.usage?.totalTokens ?: 0

        Result.success(assistantMessage to tokensUsed)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun close() {
        client.close()
    }
}