package com.rep.app.ui.scenario

import androidx.lifecycle.viewModelScope
import com.rep.app.data.db.AppDatabase
import com.rep.app.data.db.CharacterEntity
import com.rep.app.domain.SimulationEngine
import com.rep.app.ui.common.BusyViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ScenarioViewModel(
    private val scenarioId: Long,
    private val db: AppDatabase,
    private val engine: SimulationEngine,
) : BusyViewModel() {
    val scenario = db.scenarioDao().observe(scenarioId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val feed = db.postDao().observeFeed(scenarioId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val chats = db.chatDao().observeChats(scenarioId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val characters = db.characterDao().observeForScenario(scenarioId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun publish(text: String) = launchBusy { engine.createPlayerPost(scenarioId, text) }

    fun toggleLike(postId: Long) = viewModelScope.launch { engine.toggleLike(postId) }

    fun step() = launchBusy { engine.step(scenarioId) }

    fun generatePost(characterId: Long) = launchBusy { engine.generatePost(scenarioId, characterId) }

    fun openChat(characterId: Long, onOpened: (Long) -> Unit) = viewModelScope.launch {
        onOpened(engine.openChat(scenarioId, characterId))
    }

    fun saveCharacter(character: CharacterEntity) = viewModelScope.launch {
        if (character.id == 0L) db.characterDao().insert(character.copy(scenarioId = scenarioId))
        else db.characterDao().update(character)
    }

    fun deleteCharacter(character: CharacterEntity) = viewModelScope.launch {
        db.characterDao().delete(character)
    }
}
