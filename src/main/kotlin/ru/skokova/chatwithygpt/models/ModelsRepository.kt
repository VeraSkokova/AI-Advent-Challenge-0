package ru.skokova.chatwithygpt.models

enum class ClientType { YANDEX_NATIVE, OPENAI_COMPATIBLE }

data class ModelConfig(
    val id: String,
    val name: String,
    val uriTemplate: String, // "gpt://%s/yandexgpt/latest"
    val inputPrice: Double,
    val outputPrice: Double,
    val clientType: ClientType,
    val supportsJsonMode: Boolean = false
)

object ModelsRepository {
    val YandexLite = ModelConfig(
        id = "yandexgpt-lite",
        name = "YandexGPT Lite 5",
        uriTemplate = "gpt://%s/yandexgpt-lite/latest",
        inputPrice = 0.20,
        outputPrice = 0.20,
        clientType = ClientType.YANDEX_NATIVE,
        supportsJsonMode = true
    )

    val YandexPro = ModelConfig(
        id = "yandexgpt",
        name = "YandexGPT Pro 5.1",
        uriTemplate = "gpt://%s/yandexgpt/latest",
        inputPrice = 0.40,
        outputPrice = 0.40,
        clientType = ClientType.YANDEX_NATIVE,
        supportsJsonMode = true
    )

    val Qwen = ModelConfig(
        id = "qwen3-235b",
        name = "Qwen3 235B",
        uriTemplate = "gpt://%s/qwen3-235b-a22b-fp8/latest",
        inputPrice = 0.50,
        outputPrice = 1.00,
        clientType = ClientType.OPENAI_COMPATIBLE
    )

    val ALL = listOf(YandexLite, YandexPro, Qwen)
}
