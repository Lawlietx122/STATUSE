package com.rep.app.domain

import com.rep.app.data.ai.AiClient
import com.rep.app.data.ai.AiMessage
import com.rep.app.data.ai.AiRequest
import com.rep.app.data.ai.AiRole
import com.rep.app.data.db.AppDatabase
import com.rep.app.data.db.CharacterEntity
import com.rep.app.data.db.ChatEntity
import com.rep.app.data.db.ChatMessageEntity
import com.rep.app.data.db.PostEntity
import com.rep.app.data.db.ReplyEntity
import com.rep.app.data.db.ScenarioEntity
import com.rep.app.data.prompts.PromptKind
import com.rep.app.data.prompts.PromptStore
import com.rep.app.data.prompts.renderTemplate
import kotlin.random.Random

/**
 * Вся игровая логика: генерация постов, комментариев, ответов в чате и шаг времени.
 * Сюда же потом добавляются уровни, навыки, энергия и т.п.
 */
class SimulationEngine(
    private val db: AppDatabase,
    private val ai: AiClient,
    private val prompts: PromptStore,
) {
    private val scenarios = db.scenarioDao()
    private val characters = db.characterDao()
    private val posts = db.postDao()
    private val chats = db.chatDao()

    suspend fun createPlayerPost(scenarioId: Long, text: String): Long {
        val scenario = requireScenario(scenarioId)
        val player = playerOf(scenario) ?: error("В сценарии нет персонажа игрока")
        return posts.insert(
            PostEntity(
                scenarioId = scenarioId,
                authorId = player.id,
                text = text.trim(),
                simulationDay = scenario.currentDay,
                simulationHour = scenario.currentHour,
            )
        )
    }

    suspend fun generatePost(scenarioId: Long, characterId: Long): Long {
        val scenario = requireScenario(scenarioId)
        val character = characters.get(characterId) ?: error("Персонаж не найден")
        val recent = posts.recent(scenarioId, 5).joinToString("\n") { "- ${it.text}" }.ifBlank { "(пока пусто)" }
        val vars = commonVars(scenario) + characterVars(character) + mapOf("recent_posts" to recent)
        val t = prompts.get(PromptKind.POST)
        val result = ai.complete(
            AiRequest(
                system = renderTemplate(t.system, vars),
                messages = listOf(AiMessage(AiRole.USER, renderTemplate(t.user, vars))),
                maxTokens = 200,
            ),
            purpose = "post",
            scenarioId = scenarioId,
        )
        return posts.insert(
            PostEntity(
                scenarioId = scenarioId,
                authorId = character.id,
                text = result.text.trim().trim('"'),
                isAiGenerated = true,
                likeCount = Random.nextInt(0, 50),
                viewCount = Random.nextInt(10, 300),
                simulationDay = scenario.currentDay,
                simulationHour = scenario.currentHour,
            )
        )
    }

    /** Аналог POST /post/{id}/generate-replies у Status, только синхронно и без поллинга. */
    suspend fun generateReplies(postId: Long, count: Int = 3) {
        val post = posts.get(postId) ?: error("Пост не найден")
        val scenario = requireScenario(post.scenarioId)
        val author = characters.get(post.authorId) ?: error("Автор не найден")
        val existing = posts.getReplies(postId)
            .joinToString("\n") { "${it.authorHandle}: ${it.text}" }
            .ifBlank { "(комментариев нет)" }
        val vars = commonVars(scenario) + mapOf(
            "post_author" to author.name,
            "post_author_handle" to author.handle,
            "post_text" to post.text,
            "existing_replies" to existing,
            "count" to count.toString(),
        )
        val t = prompts.get(PromptKind.REPLIES)
        val result = ai.complete(
            AiRequest(
                system = renderTemplate(t.system, vars),
                messages = listOf(AiMessage(AiRole.USER, renderTemplate(t.user, vars))),
                maxTokens = 80 * count + 50,
            ),
            purpose = "reply",
            scenarioId = scenario.id,
        )
        val parsed = parseReplies(result.text).ifEmpty { listOf(randomHandle() to result.text.trim()) }
        val known = characters.getForScenario(scenario.id).associateBy { it.handle.lowercase() }
        parsed.forEach { (handle, text) ->
            val character = known[handle.lowercase()]
            posts.insertReply(
                ReplyEntity(
                    postId = postId,
                    authorId = character?.id,
                    authorHandle = character?.handle ?: handle,
                    authorName = character?.name ?: handle,
                    text = text,
                    likeCount = Random.nextInt(0, 30),
                    isAiGenerated = true,
                )
            )
        }
        posts.addReplyCount(postId, parsed.size)
    }

    suspend fun toggleLike(postId: Long) {
        val post = posts.get(postId) ?: return
        posts.update(
            post.copy(
                userLiked = !post.userLiked,
                likeCount = post.likeCount + if (post.userLiked) -1 else 1,
            )
        )
    }

    suspend fun openChat(scenarioId: Long, characterId: Long): Long =
        chats.find(scenarioId, characterId)?.id
            ?: chats.insert(ChatEntity(scenarioId = scenarioId, characterId = characterId))

    suspend fun sendChatMessage(chatId: Long, text: String) {
        val chat = chats.get(chatId) ?: error("Чат не найден")
        chats.insertMessage(ChatMessageEntity(chatId = chatId, fromPlayer = true, content = text.trim()))
        replyInChat(chat)
    }

    /** Ответ персонажа на последние сообщения. Можно вызвать и без нового сообщения игрока. */
    suspend fun replyInChat(chat: ChatEntity) {
        val scenario = requireScenario(chat.scenarioId)
        val character = characters.get(chat.characterId) ?: error("Персонаж не найден")
        val player = playerOf(scenario)
        val history = chats.unsummarizedMessages(chat.id)
        val vars = commonVars(scenario) + characterVars(character) + mapOf(
            "player_name" to (player?.name ?: "игрок"),
            "summary" to chat.summary.ifBlank { "(ничего)" },
        )
        val t = prompts.get(PromptKind.CHAT)
        val messages = history.map { AiMessage(if (it.fromPlayer) AiRole.USER else AiRole.ASSISTANT, it.content) }
            .let { if (t.user.isNotBlank()) it + AiMessage(AiRole.USER, renderTemplate(t.user, vars)) else it }
            .let { if (it.firstOrNull()?.role != AiRole.USER) listOf(AiMessage(AiRole.USER, "(начало переписки)")) + it else it }
            .let(::mergeSameRole)
        val result = ai.complete(
            AiRequest(system = renderTemplate(t.system, vars), messages = messages, maxTokens = 300),
            purpose = "chat",
            scenarioId = scenario.id,
        )
        chats.insertMessage(ChatMessageEntity(chatId = chat.id, fromPlayer = false, content = result.text))
        chats.update(chat.copy(lastMessageAt = System.currentTimeMillis()))

        if (history.size + 1 > SUMMARIZE_AFTER) summarize(chat.id)
    }

    /** Сжимает всё, кроме последних KEEP_RECENT сообщений, в chat.summary. */
    suspend fun summarize(chatId: Long) {
        val chat = chats.get(chatId) ?: return
        val messages = chats.unsummarizedMessages(chatId)
        val toSummarize = messages.dropLast(KEEP_RECENT)
        if (toSummarize.isEmpty()) return
        val scenario = requireScenario(chat.scenarioId)
        val character = characters.get(chat.characterId) ?: return
        val playerName = playerOf(scenario)?.name ?: "игрок"
        val dialog = toSummarize.joinToString("\n") {
            "${if (it.fromPlayer) playerName else character.name}: ${it.content}"
        }
        val vars = commonVars(scenario) + characterVars(character) + mapOf(
            "player_name" to playerName,
            "summary" to chat.summary.ifBlank { "(ничего)" },
            "dialog" to dialog,
        )
        val t = prompts.get(PromptKind.SUMMARY)
        val result = ai.complete(
            AiRequest(
                system = renderTemplate(t.system, vars),
                messages = listOf(AiMessage(AiRole.USER, renderTemplate(t.user, vars))),
                maxTokens = 400,
                temperature = 0.3,
            ),
            purpose = "summary",
            scenarioId = scenario.id,
        )
        chats.update(chat.copy(summary = result.text))
        chats.markSummarized(toSummarize.map { it.id })
    }

    /**
     * Шаг времени: +1 час, немного «живости» существующим постам
     * и новые посты от случайных ИИ-персонажей.
     */
    suspend fun step(scenarioId: Long, newPosts: Int = 1) {
        val scenario = requireScenario(scenarioId)
        val nextHour = (scenario.currentHour + 1) % 24
        val nextDay = scenario.currentDay + if (nextHour == 0) 1 else 0
        scenarios.update(scenario.copy(currentDay = nextDay, currentHour = nextHour))

        posts.recent(scenarioId, 20).forEach { p ->
            posts.update(
                p.copy(
                    likeCount = p.likeCount + Random.nextInt(0, 15),
                    viewCount = p.viewCount + Random.nextInt(5, 60),
                    repostCount = p.repostCount + Random.nextInt(0, 3),
                )
            )
        }

        val npcs = characters.getForScenario(scenarioId).filterNot { it.isPlayer }
        npcs.shuffled().take(newPosts).forEach { generatePost(scenarioId, it.id) }
    }

    private suspend fun requireScenario(id: Long): ScenarioEntity =
        scenarios.get(id) ?: error("Сценарий не найден")

    private suspend fun playerOf(scenario: ScenarioEntity): CharacterEntity? =
        scenario.playerCharacterId?.let { characters.get(it) }
            ?: characters.getForScenario(scenario.id).firstOrNull { it.isPlayer }

    private fun commonVars(s: ScenarioEntity) = mapOf(
        "world" to s.world,
        "scenario" to s.title,
        "day" to s.currentDay.toString(),
        "hour" to s.currentHour.toString(),
    )

    private fun characterVars(c: CharacterEntity) = mapOf(
        "name" to c.name,
        "handle" to c.handle,
        "bio" to c.bio,
        "personality" to c.personality,
        "goal" to c.goal,
    )

    private fun randomHandle() = "user" + Random.nextInt(1000, 99999)

    companion object {
        const val SUMMARIZE_AFTER = 30
        const val KEEP_RECENT = 10

        private val REPLY_LINE = Regex("""(?U)^\s*(?:\d+[.)]\s*)?[-*]?\s*@?([\w.]{2,30})\s*[:：]\s*(.+)$""")

        /** Разбирает ответ модели в пары (handle, текст) по строкам `handle: текст`. */
        fun parseReplies(text: String): List<Pair<String, String>> =
            text.lines().mapNotNull { line ->
                REPLY_LINE.find(line)?.let { it.groupValues[1] to it.groupValues[2].trim().trim('"') }
            }.filter { it.second.isNotBlank() }

        /** Anthropic требует чередования ролей, поэтому подряд идущие сообщения одной роли склеиваются. */
        fun mergeSameRole(messages: List<AiMessage>): List<AiMessage> =
            messages.fold(mutableListOf<AiMessage>()) { acc, m ->
                val last = acc.lastOrNull()
                if (last != null && last.role == m.role) acc[acc.lastIndex] = last.copy(content = last.content + "\n" + m.content)
                else acc.add(m)
                acc
            }
    }
}
