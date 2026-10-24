package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ChatEntity::class,
        MessageEntity::class,
        VfsNodeEntity::class,
        AiModelEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class VertexChatDatabase : RoomDatabase() {

    abstract fun dao(): VertexChatDao

    companion object {
        @Volatile
        private var INSTANCE: VertexChatDatabase? = null

        val DEFAULT_MODELS = listOf(
            AiModelEntity(
                modelId = "gemini-3.1-pro-preview",
                displayName = "Gemini 3.1 Pro Preview",
                description = "Nejvyšší přesnost pro architekturu kódu, hloubkové uvažování a VFS nástroje",
                supportsThinking = true,
                supportsTools = true,
                maxOutputTokens = 65536,
                isPinned = true
            ),
            AiModelEntity(
                modelId = "gemini-3.5-flash",
                displayName = "Gemini 3.5 Flash",
                description = "Rychlý multimodální model s nízkou latencí a podporou Thinking & Tools",
                supportsThinking = true,
                supportsTools = true,
                maxOutputTokens = 65536,
                isPinned = true
            ),
            AiModelEntity(
                modelId = "gemini-2.5-pro",
                displayName = "Gemini 2.5 Pro (Vertex AI)",
                description = "Produkční Vertex AI model pro komplexní analýzu a rozsáhlé generování",
                supportsThinking = true,
                supportsTools = true,
                maxOutputTokens = 65536,
                isPinned = true
            ),
            AiModelEntity(
                modelId = "gemini-2.5-flash",
                displayName = "Gemini 2.5 Flash (Vertex AI)",
                description = "Vyvážený produkční Vertex AI model s vysokou rychlostí streamování",
                supportsThinking = true,
                supportsTools = true,
                maxOutputTokens = 65536,
                isPinned = true
            ),
            AiModelEntity(
                modelId = "gemini-3.1-flash-lite-preview",
                displayName = "Gemini 3.1 Flash Lite",
                description = "Ultrarychlý model pro okamžité odpovědi a lehké úlohy",
                supportsThinking = false,
                supportsTools = true,
                maxOutputTokens = 32768,
                isPinned = false
            )
        )

        fun getInstance(context: Context): VertexChatDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    VertexChatDatabase::class.java,
                    "vertex_chat.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
