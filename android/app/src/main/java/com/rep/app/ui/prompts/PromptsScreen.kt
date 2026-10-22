package com.rep.app.ui.prompts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rep.app.data.prompts.PromptKind
import com.rep.app.data.prompts.PromptStore
import com.rep.app.data.prompts.PromptTemplate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptsScreen(store: PromptStore, onBack: () -> Unit) {
    val templates by store.templates.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Промпты") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(PromptKind.entries.toList(), key = { it.name }) { kind ->
                PromptEditor(
                    kind = kind,
                    template = templates.getValue(kind),
                    onSave = { store.save(kind, it) },
                    onReset = { store.reset(kind) },
                )
            }
        }
    }
}

@Composable
private fun PromptEditor(
    kind: PromptKind,
    template: PromptTemplate,
    onSave: (PromptTemplate) -> Unit,
    onReset: () -> Unit,
) {
    var system by remember(template) { mutableStateOf(template.system) }
    var user by remember(template) { mutableStateOf(template.user) }
    val changed = system != template.system || user != template.user

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(kind.title, style = MaterialTheme.typography.titleMedium)
            Text(
                "Переменные: " + kind.variables.joinToString(" ") { "{$it}" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(system, { system = it }, label = { Text("System") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(user, { user = it }, label = { Text("User") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            Row {
                TextButton(enabled = changed, onClick = { onSave(PromptTemplate(system, user)) }) { Text("Сохранить") }
                TextButton(onClick = onReset) { Text("Сбросить") }
            }
        }
    }
}
