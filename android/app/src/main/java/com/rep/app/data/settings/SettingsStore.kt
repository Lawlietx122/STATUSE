package com.rep.app.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.rep.app.data.ai.ProviderConfig
import com.rep.app.data.ai.ProviderKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/** Готовые адреса, чтобы не вбивать руками. */
data class ProviderPreset(val name: String, val kind: ProviderKind, val baseUrl: String, val modelHint: String)

val PROVIDER_PRESETS = listOf(
    ProviderPreset("OpenAI", ProviderKind.OPENAI_COMPATIBLE, "https://api.openai.com/v1", "gpt-4o-mini"),
    ProviderPreset("OpenRouter", ProviderKind.OPENAI_COMPATIBLE, "https://openrouter.ai/api/v1", "provider/model"),
    ProviderPreset("Anthropic", ProviderKind.ANTHROPIC, "https://api.anthropic.com", "claude-haiku-5-5"),
    ProviderPreset("Ollama (ПК в сети)", ProviderKind.OPENAI_COMPATIBLE, "http://192.168.0.10:11434/v1", "llama3.1"),
    ProviderPreset("LM Studio (ПК в сети)", ProviderKind.OPENAI_COMPATIBLE, "http://192.168.0.10:1234/v1", "local-model"),
)

data class SettingsState(
    val providers: List<ProviderConfig> = emptyList(),
    val activeProviderId: String? = null,
)

/** Провайдеры и ключи лежат в EncryptedSharedPreferences. */
class SettingsStore(context: Context) {
    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_settings",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
    private val json = Json { ignoreUnknownKeys = true }

    private val _state = MutableStateFlow(load())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    fun activeProvider(): ProviderConfig? {
        val s = _state.value
        return s.providers.firstOrNull { it.id == s.activeProviderId } ?: s.providers.firstOrNull()
    }

    fun upsertProvider(config: ProviderConfig) {
        val withId = if (config.id.isBlank()) config.copy(id = UUID.randomUUID().toString()) else config
        val list = _state.value.providers.filterNot { it.id == withId.id } + withId
        save(_state.value.copy(
            providers = list,
            activeProviderId = _state.value.activeProviderId ?: withId.id,
        ))
    }

    fun deleteProvider(id: String) {
        val list = _state.value.providers.filterNot { it.id == id }
        val active = _state.value.activeProviderId.takeIf { it != id } ?: list.firstOrNull()?.id
        save(SettingsState(list, active))
    }

    fun setActive(id: String) = save(_state.value.copy(activeProviderId = id))

    private fun load(): SettingsState {
        val providers = prefs.getString(KEY_PROVIDERS, null)
            ?.let { runCatching { json.decodeFromString<List<ProviderConfig>>(it) }.getOrNull() }
            .orEmpty()
        return SettingsState(providers, prefs.getString(KEY_ACTIVE, null))
    }

    private fun save(state: SettingsState) {
        prefs.edit()
            .putString(KEY_PROVIDERS, json.encodeToString(state.providers))
            .putString(KEY_ACTIVE, state.activeProviderId)
            .apply()
        _state.value = state
    }

    private companion object {
        const val KEY_PROVIDERS = "providers"
        const val KEY_ACTIVE = "active_provider"
    }
}
