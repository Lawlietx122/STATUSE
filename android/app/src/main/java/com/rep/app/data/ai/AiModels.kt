package com.rep.app.data.ai

import kotlinx.serialization.Serializable

enum class AiRole { USER, ASSISTANT }

data class AiMessage(val role: AiRole, val content: String)

data class AiRequest(
    val system: String?,
    val messages: List<AiMessage>,
    val maxTokens: Int = 300,
    val temperature: Double = 0.9,
)

data class AiResult(
    val text: String,
    val model: String,
    val inputTokens: Int,
    val outputTokens: Int,
    /** true, если провайдер не прислал usage и токены оценены по длине текста. */
    val isEstimated: Boolean,
)

class AiException(message: String, cause: Throwable? = null) : Exception(message, cause)

@Serializable
enum class ProviderKind {
    /** Любой сервер с API в формате OpenAI /chat/completions: OpenAI, OpenRouter, Ollama, LM Studio и т.д. */
    OPENAI_COMPATIBLE,
    ANTHROPIC,
}

@Serializable
data class ProviderConfig(
    val id: String,
    val name: String,
    val kind: ProviderKind,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
)

/** Грубая оценка на случай, если сервер не вернул usage (часто у локальных моделей). */
fun estimateTokens(text: String): Int = (text.length / 4).coerceAtLeast(1)
