package com.rep.app.data.ai

import com.rep.app.data.db.TokenUsageDao
import com.rep.app.data.db.TokenUsageEntity
import com.rep.app.data.settings.SettingsStore
import okhttp3.OkHttpClient

/**
 * Единая точка вызова модели: берёт активного провайдера из настроек
 * и записывает расход токенов в журнал после каждого запроса.
 */
class AiClient(
    private val http: OkHttpClient,
    private val settings: SettingsStore,
    private val usageDao: TokenUsageDao,
) {
    suspend fun complete(
        request: AiRequest,
        purpose: String,
        scenarioId: Long?,
        providerOverride: ProviderConfig? = null,
    ): AiResult {
        val config = providerOverride ?: settings.activeProvider()
            ?: throw AiException("Не выбрана модель. Добавь провайдера в настройках.")
        val started = System.currentTimeMillis()
        val result = createProvider(http, config).complete(request)
        usageDao.insert(
            TokenUsageEntity(
                providerId = config.name,
                model = result.model,
                purpose = purpose,
                scenarioId = scenarioId,
                inputTokens = result.inputTokens,
                outputTokens = result.outputTokens,
                isEstimated = result.isEstimated,
                durationMs = System.currentTimeMillis() - started,
            )
        )
        return result
    }
}
