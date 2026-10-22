package com.rep.app.ui.tokens

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
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rep.app.data.db.TokenUsageDao
import com.rep.app.data.db.UsageTotals
import com.rep.app.ui.common.formatTokens
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class Period(val title: String) { TODAY("Сегодня"), WEEK("7 дней"), MONTH("30 дней"), ALL("Всё время") }

private fun Period.since(): Long {
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
    return when (this) {
        Period.TODAY -> cal.timeInMillis
        Period.WEEK -> cal.apply { add(Calendar.DAY_OF_YEAR, -6) }.timeInMillis
        Period.MONTH -> cal.apply { add(Calendar.DAY_OF_YEAR, -29) }.timeInMillis
        Period.ALL -> 0L
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class TokenStatsViewModel(private val dao: TokenUsageDao) : ViewModel() {
    val period = MutableStateFlow(Period.TODAY)

    val totals = period.flatMapLatest { dao.observeTotals(it.since()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UsageTotals(0, 0, 0))
    val byModel = period.flatMapLatest { dao.observeByModel(it.since()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val recent = dao.observeRecent(50)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun clear() = viewModelScope.launch { dao.clear() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TokenStatsScreen(vm: TokenStatsViewModel, onBack: () -> Unit) {
    val period by vm.period.collectAsState()
    val totals by vm.totals.collectAsState()
    val byModel by vm.byModel.collectAsState()
    val recent by vm.recent.collectAsState()
    val timeFormat = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Токены") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                },
                actions = {
                    IconButton(onClick = { vm.clear() }) { Icon(Icons.Default.DeleteSweep, "Очистить журнал") }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(12.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Period.entries.forEach { p ->
                        FilterChip(selected = p == period, onClick = { vm.period.value = p }, label = { Text(p.title) })
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Stat("Запросы", totals.requests.toString())
                        Stat("Вход", formatTokens(totals.inputTokens))
                        Stat("Выход", formatTokens(totals.outputTokens))
                        Stat("Всего", formatTokens(totals.inputTokens + totals.outputTokens))
                    }
                }
            }
            if (byModel.isNotEmpty()) {
                item { Text("По моделям", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 8.dp)) }
                items(byModel, key = { it.providerId + "/" + it.model }) { m ->
                    ListItem(
                        headlineContent = { Text(m.model) },
                        supportingContent = { Text("${m.providerId} · ${m.requests} запр.") },
                        trailingContent = {
                            Text("${formatTokens(m.inputTokens)} / ${formatTokens(m.outputTokens)}")
                        },
                    )
                }
            }
            item { Text("Последние запросы", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 8.dp)) }
            items(recent, key = { it.id }) { u ->
                ListItem(
                    headlineContent = { Text("${u.purpose} · ${u.model}") },
                    supportingContent = {
                        Text("${timeFormat.format(Date(u.createdAt))} · ${u.durationMs} мс" + if (u.isEstimated) " · оценка" else "")
                    },
                    trailingContent = { Text("${u.inputTokens} / ${u.outputTokens}") },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
