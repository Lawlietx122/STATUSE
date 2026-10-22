package com.rep.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "scenarios")
data class ScenarioEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String,
    /** Общее описание мира, подставляется в промпты как {world}. */
    val world: String,
    val currentDay: Int = 1,
    val currentHour: Int = 0,
    /** id персонажа, за которого играет пользователь. */
    val playerCharacterId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "characters",
    foreignKeys = [ForeignKey(
        entity = ScenarioEntity::class,
        parentColumns = ["id"],
        childColumns = ["scenarioId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("scenarioId")],
)
data class CharacterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scenarioId: Long,
    val handle: String,
    val name: String,
    val bio: String,
    val personality: String,
    val goal: String,
    val isPlayer: Boolean = false,
    val followerCount: Int = 0,
    val level: Int = 1,
    val experience: Int = 0,
    val auraScore: Float = 1f,
    val humorScore: Float = 1f,
    val goalScore: Float = 0f,
)

@Entity(
    tableName = "posts",
    foreignKeys = [
        ForeignKey(entity = ScenarioEntity::class, parentColumns = ["id"], childColumns = ["scenarioId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = CharacterEntity::class, parentColumns = ["id"], childColumns = ["authorId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("scenarioId"), Index("authorId")],
)
data class PostEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scenarioId: Long,
    val authorId: Long,
    val text: String,
    val likeCount: Int = 0,
    val replyCount: Int = 0,
    val repostCount: Int = 0,
    val viewCount: Int = 0,
    val userLiked: Boolean = false,
    val isAiGenerated: Boolean = false,
    val simulationDay: Int,
    val simulationHour: Int,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Комментарий. Автор либо персонаж (authorId), либо безымянный NPC (только handle/name). */
@Entity(
    tableName = "replies",
    foreignKeys = [ForeignKey(entity = PostEntity::class, parentColumns = ["id"], childColumns = ["postId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("postId")],
)
data class ReplyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val postId: Long,
    val authorId: Long? = null,
    val authorHandle: String,
    val authorName: String,
    val text: String,
    val likeCount: Int = 0,
    val isAiGenerated: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "chats",
    foreignKeys = [
        ForeignKey(entity = ScenarioEntity::class, parentColumns = ["id"], childColumns = ["scenarioId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = CharacterEntity::class, parentColumns = ["id"], childColumns = ["characterId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("scenarioId"), Index(value = ["scenarioId", "characterId"], unique = true)],
)
data class ChatEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scenarioId: Long,
    val characterId: Long,
    /** Сжатое содержание старой части диалога (аналог trigger-all-summaries у Status). */
    val summary: String = "",
    val lastMessageAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "chat_messages",
    foreignKeys = [ForeignKey(entity = ChatEntity::class, parentColumns = ["id"], childColumns = ["chatId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("chatId")],
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chatId: Long,
    val fromPlayer: Boolean,
    val content: String,
    val isSummarized: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Одна запись на каждый запрос к модели: основа счётчика токенов. */
@Entity(tableName = "token_usage", indices = [Index("createdAt"), Index("scenarioId")])
data class TokenUsageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val providerId: String,
    val model: String,
    /** Для чего был запрос: post / reply / chat / summary / test. */
    val purpose: String,
    val scenarioId: Long?,
    val inputTokens: Int,
    val outputTokens: Int,
    /** true, если провайдер не вернул usage и число посчитано приблизительно. */
    val isEstimated: Boolean,
    val durationMs: Long,
)
