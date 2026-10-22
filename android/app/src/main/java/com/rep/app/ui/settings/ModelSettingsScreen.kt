package com.rep.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rep.app.data.ai.AiClient
import com.rep.app.data.ai.AiMessage
import com.rep.app.data.ai.AiRequest
import com.rep.app.data.ai.AiRole
import com.rep.app.data.ai.ProviderConfig
import com.rep.app.data.ai.ProviderKind
import com.rep.app.data.settings.PROVIDER_PRESETS
import com.rep.app.data.settings.SettingsStore
import com.rep.app.ui.common.EmptyState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ModelSettingsViewModel(
    private val settings: SettingsStore,
    private val ai: AiClient,
) : ViewModel() {
    val state = settings.state

    private val _testResult = MutableStateFlow<String?>(null)
    val testResult = _testResult.asStateFlow()

    fun save(config: ProviderConfig) = settings.upsertProvider(config)
    fun delete(id: String) = settings.deleteProvider(id)
    fun setActive(id: String) = settings.setActive(id)
    fun clearTest() { _testResult.value = null }

    fun test(config: ProviderConfig) {
        _testResult.value = "Проверяю ${config.name}…"
        viewModelScope.launch {
            _testResult.value = try {
                val r = ai.complete(
                    AiRequest(
                        system = null,
                        messages = listOf(AiMessage(AiRole.USER, "Ответь одним словом: привет")),
                        maxTokens = 20,
                    ),
                    purpose = "test",
                    scenarioId = null,
                    providerOverride = config,
                )
                "Работает (${r.model}).\nОтвет: ${r.text}\nТокены: ${r.inputTokens} вход / ${r.outputTokens} выход" +
                    if (r.isEstimated) " (оценка)" else ""
            } catch (e: Exception) {
                "Ошибка: ${e.message}"
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSettingsScreen(vm: ModelSettingsViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsState()
    val testResult by vm.testResult.collectAsState()
    var editing by remember { mutableStateOf<ProviderConfig?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Модели") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                val preset = PROVIDER_PRESETS.first()
                editing = ProviderConfig("", preset.name, preset.kind, preset.baseUrl, "", preset.modelHint)
            }) { Icon(Icons.Default.Add, "Добавить") }
        },
    ) { padding ->
        if (state.providers.isEmpty()) {
            EmptyState("Нет ни одной модели. Нажми +, выбери OpenAI, OpenRouter, Anthropic или локальный сервер.", Modifier.padding(padding))
        } else {
            LazyColumn(Modifier.padding(padding)) {
                items(state.providers, key = { it.id }) { p ->
                    ListItem(
                        leadingContent = {
                            RadioButton(selected = p.id == state.activeProviderId, onClick = { vm.setActive(p.id) })
                        },
                        headlineContent = { Text(p.name) },
                        supportingContent = { Text("${p.model}\n${p.baseUrl}") },
                        trailingContent = { TextButton(onClick = { vm.test(p) }) { Text("Тест") } },
                        modifier = Modifier.clickable { editing = p },
                    )
                }
            }
        }
    }

    editing?.let { config ->
        ProviderDialog(
            initial = config,
            onDismiss = { editing = null },
            onSave = { vm.save(it); editing = null },
            onDelete = if (config.id.isNotBlank()) { { vm.delete(config.id); editing = null } } else null,
        )
    }

    testResult?.let { text ->
        AlertDialog(
            onDismissRequest = vm::clearTest,
            title = { Text("Проверка") },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = vm::clearTest) { Text("OK") } },
        )
    }
}

@Composable
private fun ProviderDialog(
    initial: ProviderConfig,
    onDismiss: () -> Unit,
    onSave: (ProviderConfig) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by remember { mutableStateOf(initial.name) }
    var kind by remember { mutableStateOf(initial.kind) }
    var baseUrl by remember { mutableStateOf(initial.baseUrl) }
    var apiKey by remember { mutableStateOf(initial.apiKey) }
    var model by remember { mutableStateOf(initial.model) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id.isBlank()) "Новая модель" else "Модель") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (initial.id.isBlank()) {
                    Text("Шаблон", style = MaterialTheme.typography.labelLarge)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PROVIDER_PRESETS.forEach { p ->
                            FilterChip(
                                selected = baseUrl == p.baseUrl,
                                onClick = { name = p.name; kind = p.kind; baseUrl = p.baseUrl; model = p.modelHint },
                                label = { Text(p.name) },
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = kind == ProviderKind.OPENAI_COMPATIBLE,
                        onClick = { kind = ProviderKind.OPENAI_COMPATIBLE },
                        label = { Text("OpenAI-формат") },
                    )
                    FilterChip(
                        selected = kind == ProviderKind.ANTHROPIC,
                        onClick = { kind = ProviderKind.ANTHROPIC },
                        label = { Text("Anthropic") },
                    )
                }
                OutlinedTextField(name, { name = it }, label = { Text("Название") }, singleLine = true)
                OutlinedTextField(
                    baseUrl, { baseUrl = it }, label = { Text("Адрес API") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                OutlinedTextField(
                    apiKey, { apiKey = it }, label = { Text("API-ключ (для локальных можно пусто)") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                OutlinedTextField(model, { model = it }, label = { Text("Модель") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && baseUrl.isNotBlank() && model.isNotBlank(),
                onClick = {
                    onSave(initial.copy(name = name.trim(), kind = kind, baseUrl = baseUrl.trim(), apiKey = apiKey.trim(), model = model.trim()))
                },
            ) { Text("Сохранить") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Удалить") }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        },
    )
}
