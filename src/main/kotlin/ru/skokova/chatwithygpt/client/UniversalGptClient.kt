package ru.skokova.chatwithygpt.client

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.HttpResponse
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.*
import ru.skokova.chatwithygpt.config.ApiConfig
import ru.skokova.chatwithygpt.data.Persona
import ru.skokova.chatwithygpt.models.*
import kotlin.system.measureTimeMillis

class UniversalGptClient(
    private val config: ApiConfig
) : LlmClient {

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                ignoreUnknownKeys = true
                encodeDefaults = true
            })
        }
        install(Logging) {
            level = LogLevel.NONE
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 120_000 // 2 минуты для Qwen
            connectTimeoutMillis = 30_000
            socketTimeoutMillis = 120_000
        }
    }

    override suspend fun sendMessage(
        messages: List<Message>,
        persona: Persona,
        model: ModelConfig
    ): Result<GenerationResult> = runCatching {

        val folderId = config.folderId
        val modelUri = model.uriTemplate.format(folderId)

        val systemMessage = Message("system", persona.systemPrompt)
        val fullHistory = listOf(systemMessage) + messages

        val startTime = System.currentTimeMillis()
        var inputTokens = 0
        var outputTokens = 0
        var responseText = ""

        if (model.clientType == ClientType.YANDEX_NATIVE) {
            // === YANDEX NATIVE LOGIC ===
            val requestBody = buildJsonObject {
                put("modelUri", modelUri)
                putJsonObject("completionOptions") {
                    put("stream", false)
                    put("temperature", persona.temperature)
                    put("maxTokens", "1000") // Строка!
                }
                if (model.supportsJsonMode) {
                    put("jsonObject", true)
                }
                putJsonArray("messages") {
                    fullHistory.forEach { msg ->
                        addJsonObject {
                            put("role", msg.role)
                            put("text", msg.text)
                        }
                    }
                }
            }

            val response: HttpResponse = client.post("https://llm.api.cloud.yandex.net/foundationModels/v1/completion") {
                header("Authorization", "Api-Key ${config.apiKey}")
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }

            val json = response.body<JsonObject>()

            // Обработка ошибки
            if (response.status != HttpStatusCode.OK) {
                throw Exception("API Error: ${response.status} $json")
            }

            val result = json["result"]?.jsonObject
            responseText = result?.get("alternatives")?.jsonArray?.get(0)?.jsonObject
                ?.get("message")?.jsonObject?.get("text")?.jsonPrimitive?.content ?: ""

            val usage = result?.get("usage")?.jsonObject
            inputTokens = usage?.get("inputTextTokens")?.jsonPrimitive?.content?.toInt() ?: 0
            outputTokens = usage?.get("completionTokens")?.jsonPrimitive?.content?.toInt() ?: 0

        } else {
            // === OPENAI COMPATIBLE (QWEN) LOGIC ===
            val requestBody = buildJsonObject {
                put("model", modelUri)
                put("temperature", persona.temperature)
                put("max_tokens", 1000) // Число!
                putJsonArray("messages") {
                    fullHistory.forEach { msg ->
                        addJsonObject {
                            put("role", msg.role)
                            put("content", msg.text) // content, не text
                        }
                    }
                }
            }

            val response: HttpResponse = client.post("https://llm.api.cloud.yandex.net/v1/chat/completions") {
                header("Authorization", "Api-Key ${config.apiKey}")
                header("OpenAI-Project", folderId) // <-- КРИТИЧНО для Qwen
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }

            val json = response.body<JsonObject>()

            if (response.status != HttpStatusCode.OK) {
                throw Exception("API Error: ${response.status} $json")
            }

            responseText = json["choices"]?.jsonArray?.get(0)?.jsonObject
                ?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content ?: ""

            val usage = json["usage"]?.jsonObject
            inputTokens = usage?.get("prompt_tokens")?.jsonPrimitive?.int ?: 0
            outputTokens = usage?.get("completion_tokens")?.jsonPrimitive?.int ?: 0
        }

        val duration = System.currentTimeMillis() - startTime

        // Расчет стоимости
        val cost = (inputTokens * model.inputPrice / 1000.0) +
                (outputTokens * model.outputPrice / 1000.0)

        GenerationResult(
            text = responseText,
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            costRub = cost,
            durationMs = duration,
            modelName = model.name
        )
    }

    override suspend fun close() {
        client.close()
    }
}
