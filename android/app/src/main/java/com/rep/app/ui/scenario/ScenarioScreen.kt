package com.rep.app.ui.scenario

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rep.app.data.db.CharacterEntity
import com.rep.app.data.db.ChatWithCharacter
import com.rep.app.ui.common.EmptyState
import com.rep.app.ui.post.PostCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScenarioScreen(
    vm: ScenarioViewModel,
    onBack: () -> Unit,
    onOpenPost: (Long) -> Unit,
    onOpenChat: (Long) -> Unit,
    onTokens: () -> Unit,
) {
    val scenario by vm.scenario.collectAsState()
    val busy by vm.busy.collectAsState()
    val error by vm.error.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<CharacterEntity?>(null) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(error) {
        error?.let { snackbar.showSnackbar(it); vm.clearError() }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(scenario?.title.orEmpty())
                            scenario?.let {
                                Text(
                                    "День ${it.currentDay}, ${it.currentHour}:00",
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                    },
                    actions = {
                        IconButton(onClick = onTokens) { Icon(Icons.Default.BarChart, "Токены") }
                        IconButton(onClick = vm::step, enabled = !busy) {
                            Icon(Icons.Default.FastForward, "Шаг времени")
                        }
                    },
                )
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                TabRow(selectedTabIndex = tab) {
                    listOf("Лента", "Чаты", "Персонажи").forEachIndexed { i, title ->
                        Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title) })
                    }
                }
            }
        },
        floatingActionButton = {
            if (tab == 2) {
                FloatingActionButton(onClick = {
                    editing = CharacterEntity(
                        scenarioId = 0, handle = "", name = "", bio = "", personality = "", goal = "",
                    )
                }) { Icon(Icons.Default.Add, "Новый персонаж") }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                0 -> FeedTab(vm, busy, onOpenPost)
                1 -> ChatsTab(vm, onOpenChat)
                else -> CharactersTab(vm, busy, onEdit = { editing = it }, onOpenChat = { id ->
                    vm.openChat(id, onOpenChat)
                })
            }
        }
    }

    editing?.let { character ->
        CharacterDialog(
            initial = character,
            onDismiss = { editing = null },
            onSave = { vm.saveCharacter(it); editing = null },
            onDelete = if (character.id != 0L && !character.isPlayer) {
                { vm.deleteCharacter(character); editing = null }
            } else null,
        )
    }
}

@Composable
private fun FeedTab(vm: ScenarioViewModel, busy: Boolean, onOpenPost: (Long) -> Unit) {
    val feed by vm.feed.collectAsState()
    var draft by rememberSaveable { mutableStateOf("") }

    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text("Что нового?") },
            maxLines = 4,
        )
        IconButton(
            enabled = draft.isNotBlank() && !busy,
            onClick = { vm.publish(draft); draft = "" },
        ) { Icon(Icons.AutoMirrored.Filled.Send, "Опубликовать") }
    }
    HorizontalDivider()
    if (feed.isEmpty()) {
        EmptyState("Лента пуста. Напиши пост или нажми ⏩, чтобы персонажи что-нибудь запостили.")
    } else {
        LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
            items(feed, key = { it.post.id }) { item ->
                PostCard(
                    item = item,
                    onClick = { onOpenPost(item.post.id) },
                    onLike = { vm.toggleLike(item.post.id) },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun ChatsTab(vm: ScenarioViewModel, onOpenChat: (Long) -> Unit) {
    val chats by vm.chats.collectAsState()
    if (chats.isEmpty()) {
        EmptyState("Чатов нет. Открой вкладку «Персонажи» и нажми «Написать».")
        return
    }
    LazyColumn {
        items(chats, key = { it.chat.id }) { c: ChatWithCharacter ->
            ListItem(
                headlineContent = { Text(c.character.name) },
                supportingContent = { Text("@${c.character.handle}") },
                modifier = Modifier.clickable { onOpenChat(c.chat.id) },
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun CharactersTab(
    vm: ScenarioViewModel,
    busy: Boolean,
    onEdit: (CharacterEntity) -> Unit,
    onOpenChat: (Long) -> Unit,
) {
    val characters by vm.characters.collectAsState()
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(characters, key = { it.id }) { c ->
            Card(Modifier.fillMaxWidth().clickable { onEdit(c) }) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        c.name + if (c.isPlayer) " (ты)" else "",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text("@${c.handle}", style = MaterialTheme.typography.labelMedium)
                    if (c.bio.isNotBlank()) Text(c.bio, style = MaterialTheme.typography.bodyMedium)
                    if (!c.isPlayer) {
                        Row(Modifier.padding(top = 8.dp)) {
                            OutlinedButton(onClick = { onOpenChat(c.id) }) { Text("Написать") }
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(enabled = !busy, onClick = { vm.generatePost(c.id) }) { Text("Пост от него") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CharacterDialog(
    initial: CharacterEntity,
    onDismiss: () -> Unit,
    onSave: (CharacterEntity) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by remember { mutableStateOf(initial.name) }
    var handle by remember { mutableStateOf(initial.handle) }
    var bio by remember { mutableStateOf(initial.bio) }
    var personality by remember { mutableStateOf(initial.personality) }
    var goal by remember { mutableStateOf(initial.goal) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "Новый персонаж" else "Персонаж") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { OutlinedTextField(name, { name = it }, label = { Text("Имя") }, singleLine = true) }
                item { OutlinedTextField(handle, { handle = it }, label = { Text("@ник") }, singleLine = true) }
                item { OutlinedTextField(bio, { bio = it }, label = { Text("Био (видно в профиле)") }) }
                item { OutlinedTextField(personality, { personality = it }, label = { Text("Характер (для ИИ)") }, minLines = 3) }
                item { OutlinedTextField(goal, { goal = it }, label = { Text("Цель") }) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && handle.isNotBlank(),
                onClick = {
                    onSave(
                        initial.copy(
                            name = name.trim(),
                            handle = handle.trim().removePrefix("@"),
                            bio = bio.trim(),
                            personality = personality.trim(),
                            goal = goal.trim(),
                        )
                    )
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
