package com.rep.app.ui.post

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.rep.app.data.db.AppDatabase
import com.rep.app.data.db.PostWithAuthor
import com.rep.app.data.db.ReplyEntity
import com.rep.app.domain.SimulationEngine
import com.rep.app.ui.common.BusyViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PostViewModel(
    private val postId: Long,
    db: AppDatabase,
    private val engine: SimulationEngine,
) : BusyViewModel() {
    val post = db.postDao().observe(postId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val replies = db.postDao().observeReplies(postId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun generateReplies() = launchBusy { engine.generateReplies(postId, count = 3) }
    fun toggleLike() = viewModelScope.launch { engine.toggleLike(postId) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostScreen(vm: PostViewModel, onBack: () -> Unit) {
    val post by vm.post.collectAsState()
    val replies by vm.replies.collectAsState()
    val busy by vm.busy.collectAsState()
    val error by vm.error.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(error) {
        error?.let { snackbar.showSnackbar(it); vm.clearError() }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Пост") },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                    },
                )
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            post?.let { p ->
                item { PostCard(item = p, onClick = {}, onLike = { vm.toggleLike() }) }
            }
            item {
                Button(
                    onClick = vm::generateReplies,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                ) { Text("Сгенерировать комментарии") }
                HorizontalDivider()
            }
            items(replies, key = { it.id }) { r -> ReplyRow(r) }
        }
    }
}

@Composable
fun PostCard(item: PostWithAuthor, onClick: () -> Unit, onLike: () -> Unit) {
    val p = item.post
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(item.author.name, style = MaterialTheme.typography.titleSmall)
            Text(
                "  @${item.author.handle} · д${p.simulationDay} ${p.simulationHour}:00",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(p.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onLike) {
                    Icon(
                        if (p.userLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        "Лайк",
                        tint = if (p.userLiked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("${p.likeCount}", style = MaterialTheme.typography.labelMedium)
            }
            Text("💬 ${p.replyCount}", style = MaterialTheme.typography.labelMedium)
            Text("🔁 ${p.repostCount}", style = MaterialTheme.typography.labelMedium)
            Text("👁 ${p.viewCount}", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun ReplyRow(r: ReplyEntity) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(
            "${r.authorName}  @${r.authorHandle}",
            style = MaterialTheme.typography.labelLarge,
        )
        Text(r.text, style = MaterialTheme.typography.bodyMedium)
        Text("♥ ${r.likeCount}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider()
}
