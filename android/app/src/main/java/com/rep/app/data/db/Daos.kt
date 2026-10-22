package com.rep.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class PostWithAuthor(
    @Embedded val post: PostEntity,
    @Relation(parentColumn = "authorId", entityColumn = "id")
    val author: CharacterEntity,
)

data class ChatWithCharacter(
    @Embedded val chat: ChatEntity,
    @Relation(parentColumn = "characterId", entityColumn = "id")
    val character: CharacterEntity,
)

data class UsageTotals(
    val requests: Int,
    val inputTokens: Long,
    val outputTokens: Long,
)

data class ModelUsage(
    val providerId: String,
    val model: String,
    val requests: Int,
    val inputTokens: Long,
    val outputTokens: Long,
)

@Dao
interface ScenarioDao {
    @Query("SELECT * FROM scenarios ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ScenarioEntity>>

    @Query("SELECT * FROM scenarios WHERE id = :id")
    fun observe(id: Long): Flow<ScenarioEntity?>

    @Query("SELECT * FROM scenarios WHERE id = :id")
    suspend fun get(id: Long): ScenarioEntity?

    @Insert
    suspend fun insert(scenario: ScenarioEntity): Long

    @Update
    suspend fun update(scenario: ScenarioEntity)

    @Delete
    suspend fun delete(scenario: ScenarioEntity)
}

@Dao
interface CharacterDao {
    @Query("SELECT * FROM characters WHERE scenarioId = :scenarioId ORDER BY isPlayer DESC, name")
    fun observeForScenario(scenarioId: Long): Flow<List<CharacterEntity>>

    @Query("SELECT * FROM characters WHERE scenarioId = :scenarioId")
    suspend fun getForScenario(scenarioId: Long): List<CharacterEntity>

    @Query("SELECT * FROM characters WHERE id = :id")
    suspend fun get(id: Long): CharacterEntity?

    @Insert
    suspend fun insert(character: CharacterEntity): Long

    @Update
    suspend fun update(character: CharacterEntity)

    @Delete
    suspend fun delete(character: CharacterEntity)
}

@Dao
interface PostDao {
    @androidx.room.Transaction
    @Query("SELECT * FROM posts WHERE scenarioId = :scenarioId ORDER BY createdAt DESC")
    fun observeFeed(scenarioId: Long): Flow<List<PostWithAuthor>>

    @androidx.room.Transaction
    @Query("SELECT * FROM posts WHERE id = :id")
    fun observe(id: Long): Flow<PostWithAuthor?>

    @Query("SELECT * FROM posts WHERE id = :id")
    suspend fun get(id: Long): PostEntity?

    @Query("SELECT * FROM posts WHERE scenarioId = :scenarioId ORDER BY createdAt DESC LIMIT :limit")
    suspend fun recent(scenarioId: Long, limit: Int): List<PostEntity>

    @Insert
    suspend fun insert(post: PostEntity): Long

    @Update
    suspend fun update(post: PostEntity)

    @Query("SELECT * FROM replies WHERE postId = :postId ORDER BY createdAt")
    fun observeReplies(postId: Long): Flow<List<ReplyEntity>>

    @Query("SELECT * FROM replies WHERE postId = :postId ORDER BY createdAt")
    suspend fun getReplies(postId: Long): List<ReplyEntity>

    @Insert
    suspend fun insertReply(reply: ReplyEntity): Long

    @Query("UPDATE posts SET replyCount = replyCount + :delta WHERE id = :postId")
    suspend fun addReplyCount(postId: Long, delta: Int)
}

@Dao
interface ChatDao {
    @androidx.room.Transaction
    @Query("SELECT * FROM chats WHERE scenarioId = :scenarioId ORDER BY lastMessageAt DESC")
    fun observeChats(scenarioId: Long): Flow<List<ChatWithCharacter>>

    @androidx.room.Transaction
    @Query("SELECT * FROM chats WHERE id = :id")
    fun observeChat(id: Long): Flow<ChatWithCharacter?>

    @Query("SELECT * FROM chats WHERE id = :id")
    suspend fun get(id: Long): ChatEntity?

    @Query("SELECT * FROM chats WHERE scenarioId = :scenarioId AND characterId = :characterId")
    suspend fun find(scenarioId: Long, characterId: Long): ChatEntity?

    @Insert
    suspend fun insert(chat: ChatEntity): Long

    @Update
    suspend fun update(chat: ChatEntity)

    @Query("SELECT * FROM chat_messages WHERE chatId = :chatId ORDER BY createdAt")
    fun observeMessages(chatId: Long): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE chatId = :chatId AND isSummarized = 0 ORDER BY createdAt")
    suspend fun unsummarizedMessages(chatId: Long): List<ChatMessageEntity>

    @Insert
    suspend fun insertMessage(message: ChatMessageEntity): Long

    @Query("UPDATE chat_messages SET isSummarized = 1 WHERE id IN (:ids)")
    suspend fun markSummarized(ids: List<Long>)
}

@Dao
interface TokenUsageDao {
    @Insert
    suspend fun insert(usage: TokenUsageEntity)

    @Query(
        "SELECT COUNT(*) AS requests, COALESCE(SUM(inputTokens), 0) AS inputTokens, " +
            "COALESCE(SUM(outputTokens), 0) AS outputTokens FROM token_usage WHERE createdAt >= :since"
    )
    fun observeTotals(since: Long): Flow<UsageTotals>

    @Query(
        "SELECT providerId, model, COUNT(*) AS requests, SUM(inputTokens) AS inputTokens, " +
            "SUM(outputTokens) AS outputTokens FROM token_usage WHERE createdAt >= :since " +
            "GROUP BY providerId, model ORDER BY (SUM(inputTokens) + SUM(outputTokens)) DESC"
    )
    fun observeByModel(since: Long): Flow<List<ModelUsage>>

    @Query("SELECT * FROM token_usage ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<TokenUsageEntity>>

    @Query("DELETE FROM token_usage")
    suspend fun clear()
}
