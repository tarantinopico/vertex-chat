package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey val id: String,
    val title: String,
    val selectedModelId: String,
    val systemInstruction: String = DEFAULT_SYSTEM_INSTRUCTION,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val DEFAULT_SYSTEM_INSTRUCTION =
            "Jsi expertní AI architekt a programátor v aplikaci Vertex Chat. " +
                "Máš přímý přístup k izolovanému Virtuálnímu souborovému systému (VFS) této konverzace pomocí nástrojů " +
                "(createFolder, writeFile, readFile, listFiles, deleteFile, moveFile, renameFile). " +
                "Když tě uživatel požádá o vytvoření nebo úpravu kódu, projektu či dokumentu, aktivně používej VFS nástroje " +
                "pro uložení souborů a zároveň přehledně vysvětli postup v Markdownu."
    }
}

enum class MessageRole {
    USER,
    MODEL,
    SYSTEM
}

enum class MessageStatus {
    STREAMING,
    COMPLETED,
    CANCELLED,
    ERROR
}

@Serializable
data class ToolCallRecord(
    val id: String,
    val toolName: String,
    val targetPath: String,
    val actionTitle: String,
    val status: String, // "RUNNING", "SUCCESS", "ERROR"
    val details: String = "",
    val sizeBytes: Long = 0L,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatEntity::class,
            parentColumns = ["id"],
            childColumns = ["chatId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("chatId"), Index("timestamp")]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val chatId: String,
    val role: String, // MessageRole.name
    val content: String,
    val thoughtSummary: String = "",
    val toolCallsJson: String = "[]",
    val status: String = MessageStatus.COMPLETED.name,
    val modelId: String = "",
    val continuationCount: Int = 0,
    val finishReason: String? = null,
    val errorMessage: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "vfs_nodes",
    foreignKeys = [
        ForeignKey(
            entity = ChatEntity::class,
            parentColumns = ["id"],
            childColumns = ["chatId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["chatId", "virtualPath"], unique = true),
        Index(value = ["chatId", "parentPath"])
    ]
)
data class VfsNodeEntity(
    @PrimaryKey val id: String,
    val chatId: String,
    val name: String,
    val virtualPath: String,          // Normalized e.g. "/src/Main.kt"
    val parentPath: String,           // Normalized e.g. "/src" or "/"
    val physicalRelativePath: String, // Path inside filesDir/vfs/{chatId}/...
    val isDirectory: Boolean,
    val sizeBytes: Long = 0L,
    val lineCount: Int = 0,
    val mimeType: String = "text/plain",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "ai_models")
data class AiModelEntity(
    @PrimaryKey val modelId: String,
    val displayName: String,
    val description: String,
    val supportsThinking: Boolean = true,
    val supportsTools: Boolean = true,
    val maxOutputTokens: Int = 65536,
    val isPinned: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)
