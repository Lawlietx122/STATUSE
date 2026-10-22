package com.rep.app.ui.scenarios

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rep.app.data.db.AppDatabase
import com.rep.app.data.db.CharacterEntity
import com.rep.app.data.db.ScenarioEntity
import com.rep.app.ui.common.EmptyState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ScenariosViewModel(private val db: AppDatabase) : ViewModel() {
    val scenarios = db.scenarioDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun create(title: String, description: String, world: String, playerName: String, playerHandle: String) {
        viewModelScope.launch {
            val scenarioId = db.scenarioDao().insert(
                ScenarioEntity(title = title, description = description, world = world)
            )
            val playerId = db.characterDao().insert(
                CharacterEntity(
                    scenarioId = scenarioId,
                    handle = playerHandle.removePrefix("@"),
                    name = playerName,
                    bio = "",
                    personality = "",
                    goal = "",
                    isPlayer = true,
                )
            )
            db.scenarioDao().get(scenarioId)?.let {
                db.scenarioDao().update(it.copy(playerCharacterId = playerId))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScenariosScreen(
    vm: ScenariosViewModel,
    onOpen: (Long) -> Unit,
    onModels: () -> Unit,
    onPrompts: () -> Unit,
    onTokens: () -> Unit,
) {
    val scenarios by vm.scenarios.collectAsState()
    var showCreate by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Сценарии") },
                actions = {
                    IconButton(onClick = onTokens) { Icon(Icons.Default.BarChart, "Токены") }
                    IconButton(onClick = onPrompts) { Icon(Icons.Default.EditNote, "Промпты") }
                    IconButton(onClick = onModels) { Icon(Icons.Default.SmartToy, "Модели") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreate = true }) { Icon(Icons.Default.Add, "Новый сценарий") }
        },
    ) { padding ->
        if (scenarios.isEmpty()) {
            EmptyState("Сценариев пока нет. Нажми +, чтобы создать мир.", Modifier.padding(padding))
        } else {
            LazyColumn(
                Modifier.padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(scenarios, key = { it.id }) { s ->
                    Card(Modifier.fillMaxWidth().clickable { onOpen(s.id) }) {
                        Column(Modifier.padding(16.dp)) {
                            Text(s.title, style = MaterialTheme.typography.titleMedium)
                            if (s.description.isNotBlank()) Text(s.description, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "День ${s.currentDay}, ${s.currentHour}:00",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        CreateScenarioDialog(
            onDismiss = { showCreate = false },
            onCreate = { t, d, w, n, h -> vm.create(t, d, w, n, h); showCreate = false },
        )
    }
}

@Composable
private fun CreateScenarioDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String, String, String, String) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var world by remember { mutableStateOf("") }
    var playerName by remember { mutableStateOf("") }
    var playerHandle by remember { mutableStateOf("") }
    val valid = title.isNotBlank() && playerName.isNotBlank() && playerHandle.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новый сценарий") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Название") }, singleLine = true)
                OutlinedTextField(description, { description = it }, label = { Text("Короткое описание") })
                OutlinedTextField(world, { world = it }, label = { Text("Мир (для промптов)") }, minLines = 3)
                OutlinedTextField(playerName, { playerName = it }, label = { Text("Твоё имя") }, singleLine = true)
                OutlinedTextField(playerHandle, { playerHandle = it }, label = { Text("Твой @ник") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onCreate(title.trim(), description.trim(), world.trim(), playerName.trim(), playerHandle.trim())
            }) { Text("Создать") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
