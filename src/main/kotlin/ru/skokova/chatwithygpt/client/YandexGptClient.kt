package ru.skokova.chatwithygpt.client

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import ru.skokova.chatwithygpt.config.ApiConfig
import ru.skokova.chatwithygpt.data.Persona
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
        messagesHistory: List<Message>,
        persona: Persona,
    ): Result<Pair<String, Int>> = try {

        // 1. Разделяем историю на "старую" и "текущее сообщение"
        val lastUserMessage = messagesHistory.lastOrNull { it.role == "user" }
        val historyContext = messagesHistory.dropLast(1)

        // 2. Применяем форматтер из Персоны к последнему сообщению
        // Если форматтера нет (дефолтный), текст останется прежним
        val processedUserText = lastUserMessage?.text?.let {
            persona.userMessageFormatter(it)
        } ?: ""

        // 3. Собираем итоговый список
        val messagesToSend = mutableListOf<Message>()
        messagesToSend.add(Message("system", persona.systemPrompt))
        messagesToSend.addAll(historyContext)
        messagesToSend.add(Message("user", processedUserText)) // Отправляем модифицированный текст

        val request = GptRequest(
            modelUri = "gpt://${config.folderId}/yandexgpt/${config.modelVersion}",
            completionOptions = CompletionOptions(
                temperature = persona.temperature,
                maxTokens = 1000
            ),
            messages = messagesToSend,
            jsonObject = true
        )

        val response = client.post(apiUrl) {
            contentType(ContentType.Application.Json)
            header("Authorization", "Api-Key ${config.apiKey}")
            header("x-folder-id", config.folderId)
            setBody(request)
        }

        // ... (парсинг ответа оставляем тем же, он у тебя хороший)
        logger.println("📥 HTTP Status: ${response.status}", Logger.Color.GRAY)
        val parsedResponse = response.body<GptResponse>()

        val assistantMessage = parsedResponse.result?.alternatives?.firstOrNull()?.message?.text
            ?: parsedResponse.alternatives?.firstOrNull()?.message?.text
            ?: "{}" // Возвращаем пустой JSON при ошибке, чтобы парсер не упал

        val tokensUsed = parsedResponse.result?.usage?.totalTokens?.toInt() ?: 0 // Приводим к Int явно

        Result.success(assistantMessage to tokensUsed)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun close() {
        client.close()
    }
}