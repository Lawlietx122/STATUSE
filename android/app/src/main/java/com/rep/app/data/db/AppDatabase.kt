package com.rep.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ScenarioEntity::class,
        CharacterEntity::class,
        PostEntity::class,
        ReplyEntity::class,
        ChatEntity::class,
        ChatMessageEntity::class,
        TokenUsageEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scenarioDao(): ScenarioDao
    abstract fun characterDao(): CharacterDao
    abstract fun postDao(): PostDao
    abstract fun chatDao(): ChatDao
    abstract fun tokenUsageDao(): TokenUsageDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "rep.db")
                // Пока схема меняется часто: при смене версии база пересоздаётся.
                .fallbackToDestructiveMigration()
                .build()
    }
}
