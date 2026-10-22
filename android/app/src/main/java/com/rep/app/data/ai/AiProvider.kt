package com.rep.app.data.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.addJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

interface AiProvider {
    suspend fun complete(request: AiRequest): AiResult
}

private val JSON_MEDIA = "application/json".toMediaType()
private val json = Json { ignoreUnknownKeys = true }

private suspend fun OkHttpClient.postJson(
    url: String,
    headers: Map<String, String>,
    body: JsonObject,
): JsonObject = withContext(Dispatchers.IO) {
    val builder = Request.Builder().url(url).post(body.toString().toRequestBody(JSON_MEDIA))
    headers.forEach { (k, v) -> builder.header(k, v) }
    try {
        newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw AiException("HTTP ${response.code}: ${text.take(500)}")
            }
            json.parseToJsonElement(text).jsonObject
        }
    } catch (e: AiException) {
        throw e
    } catch (e: Exception) {
        throw AiException(e.message ?: e.javaClass.simpleName, e)
    }
}

class OpenAiCompatibleProvider(
    private val http: OkHttpClient,
    private val config: ProviderConfig,
) : AiProvider {
    override suspend fun complete(request: AiRequest): AiResult {
        val body = buildJsonObject {
            put("model", config.model)
            put("max_tokens", request.maxTokens)
            put("temperature", request.temperature)
            putJsonArray("messages") {
                request.system?.takeIf { it.isNotBlank() }?.let { system ->
                    addJsonObject { put("role", "system"); put("content", system) }
                }
                request.messages.forEach { m ->
                    addJsonObject {
                        put("role", if (m.role == AiRole.USER) "user" else "assistant")
                        put("content", m.content)
                    }
                }
            }
        }
        val headers = buildMap<String, String> {
            if (config.apiKey.isNotBlank()) put("Authorization", "Bearer ${config.apiKey}")
        }
        val response = http.postJson(config.baseUrl.trimEnd('/') + "/chat/completions", headers, body)

        val text = response["choices"]?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
            ?: throw AiException("Пустой ответ модели: ${response.toString().take(300)}")
        val usage = response["usage"]?.jsonObject
        val input = usage?.get("prompt_tokens")?.jsonPrimitive?.intOrNull
        val output = usage?.get("completion_tokens")?.jsonPrimitive?.intOrNull
        return AiResult(
            text = text.trim(),
            model = response["model"]?.jsonPrimitive?.contentOrNull ?: config.model,
            inputTokens = input ?: estimateTokens(request.system.orEmpty() + request.messages.joinToString { it.content }),
            outputTokens = output ?: estimateTokens(text),
            isEstimated = input == null || output == null,
        )
    }
}

class AnthropicProvider(
    private val http: OkHttpClient,
    private val config: ProviderConfig,
) : AiProvider {
    override suspend fun complete(request: AiRequest): AiResult {
        val body = buildJsonObject {
            put("model", config.model)
            put("max_tokens", request.maxTokens)
            put("temperature", request.temperature.coerceIn(0.0, 1.0))
            request.system?.takeIf { it.isNotBlank() }?.let { put("system", it) }
            putJsonArray("messages") {
                request.messages.forEach { m ->
                    addJsonObject {
                        put("role", if (m.role == AiRole.USER) "user" else "assistant")
                        put("content", m.content)
                    }
                }
            }
        }
        val headers = mapOf(
            "x-api-key" to config.apiKey,
            "anthropic-version" to "2023-06-01",
        )
        val response = http.postJson(config.baseUrl.trimEnd('/') + "/v1/messages", headers, body)

        val text = response["content"]?.jsonArray
            ?.mapNotNull { block ->
                val obj = block.jsonObject
                if (obj["type"]?.jsonPrimitive?.contentOrNull == "text") obj["text"]?.jsonPrimitive?.contentOrNull else null
            }
            ?.joinToString("")
            ?: throw AiException("Пустой ответ модели: ${response.toString().take(300)}")
        val usage = response["usage"]?.jsonObject
        val input = usage?.get("input_tokens")?.jsonPrimitive?.intOrNull
        val output = usage?.get("output_tokens")?.jsonPrimitive?.intOrNull
        return AiResult(
            text = text.trim(),
            model = response["model"]?.jsonPrimitive?.contentOrNull ?: config.model,
            inputTokens = input ?: estimateTokens(request.system.orEmpty() + request.messages.joinToString { it.content }),
            outputTokens = output ?: estimateTokens(text),
            isEstimated = input == null || output == null,
        )
    }
}

fun createProvider(http: OkHttpClient, config: ProviderConfig): AiProvider = when (config.kind) {
    ProviderKind.OPENAI_COMPATIBLE -> OpenAiCompatibleProvider(http, config)
    ProviderKind.ANTHROPIC -> AnthropicProvider(http, config)
}
