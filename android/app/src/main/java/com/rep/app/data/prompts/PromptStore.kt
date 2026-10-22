package com.rep.app.data.prompts

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Виды запросов к модели. Для каждого есть пара шаблонов (system + user),
 * которые редактируются на экране «Промпты». Переменные пишутся как {name}.
 */
enum class PromptKind(val title: String, val variables: List<String>) {
    POST(
        "Новый пост персонажа",
        PromptVars.COMMON + PromptVars.CHARACTER + listOf("recent_posts"),
    ),
    REPLIES(
        "Комментарии NPC к посту",
        PromptVars.COMMON + listOf("post_author", "post_author_handle", "post_text", "existing_replies", "count"),
    ),
    CHAT(
        "Ответ персонажа в личке",
        PromptVars.COMMON + PromptVars.CHARACTER + listOf("player_name", "summary"),
    ),
    SUMMARY(
        "Сжатие старой переписки",
        PromptVars.COMMON + PromptVars.CHARACTER + listOf("player_name", "summary", "dialog"),
    ),
}

/**
 * Отдельный object, а не top-level val: иначе конструктор PromptKind запускал бы
 * инициализацию класса файла, где DEFAULTS ссылается на ещё не созданные PromptKind.POST и т.д.
 * (получались null-ключи и падение при старте).
 */
private object PromptVars {
    val COMMON = listOf("world", "scenario", "day", "hour")
    val CHARACTER = listOf("name", "handle", "bio", "personality", "goal")
}

data class PromptTemplate(val system: String, val user: String)

/**
 * Заглушки, чтобы приложение работало сразу. Их нужно заменить своими промптами.
 * Важно для REPLIES: парсер ждёт по одному комментарию на строку в виде `handle: текст`.
 */
private val DEFAULTS = mapOf(
    PromptKind.POST to PromptTemplate(
        system = "TODO: свой промпт.\nМир: {world}\nТы {name} (@{handle}). {personality}\nЦель: {goal}",
        user = "Последние посты в ленте:\n{recent_posts}\n\nНапиши один новый пост. Только текст поста.",
    ),
    PromptKind.REPLIES to PromptTemplate(
        system = "TODO: свой промпт.\nМир: {world}\nТы пишешь комментарии от лица разных пользователей соцсети.",
        user = "Пост от {post_author} (@{post_author_handle}):\n{post_text}\n\nУже есть:\n{existing_replies}\n\n" +
            "Напиши {count} комментариев. Каждый с новой строки в формате handle: текст",
    ),
    PromptKind.CHAT to PromptTemplate(
        system = "TODO: свой промпт.\nМир: {world}\nТы {name} (@{handle}). {personality}\nЦель: {goal}\n" +
            "Ты переписываешься с {player_name}. Что было раньше: {summary}",
        user = "",
    ),
    PromptKind.SUMMARY to PromptTemplate(
        system = "Ты сжимаешь переписку в короткое содержание, сохраняя факты и отношения.",
        user = "Прошлое содержание:\n{summary}\n\nНовая часть диалога {name} и {player_name}:\n{dialog}\n\nНапиши обновлённое содержание.",
    ),
)

class PromptStore(context: Context) {
    private val prefs = context.getSharedPreferences("prompts", Context.MODE_PRIVATE)

    private val _templates = MutableStateFlow(PromptKind.entries.associateWith { load(it) })
    val templates: StateFlow<Map<PromptKind, PromptTemplate>> = _templates.asStateFlow()

    fun get(kind: PromptKind): PromptTemplate = _templates.value.getValue(kind)

    fun save(kind: PromptKind, template: PromptTemplate) {
        prefs.edit()
            .putString("${kind.name}_system", template.system)
            .putString("${kind.name}_user", template.user)
            .apply()
        _templates.value = _templates.value + (kind to template)
    }

    fun reset(kind: PromptKind) = save(kind, DEFAULTS.getValue(kind))

    private fun load(kind: PromptKind): PromptTemplate {
        val def = DEFAULTS.getValue(kind)
        return PromptTemplate(
            system = prefs.getString("${kind.name}_system", null) ?: def.system,
            user = prefs.getString("${kind.name}_user", null) ?: def.user,
        )
    }
}

/** Подставляет {name} из vars. Неизвестные переменные остаются как есть, чтобы опечатку было видно. */
fun renderTemplate(template: String, vars: Map<String, String>): String =
    Regex("\\{([a-z_]+)\\}").replace(template) { m -> vars[m.groupValues[1]] ?: m.value }
