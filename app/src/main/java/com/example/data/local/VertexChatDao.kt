package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface VertexChatDao {

    // --- Chats ---
    @Query("SELECT * FROM chats ORDER BY updatedAt DESC")
    fun observeAllChats(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats ORDER BY updatedAt DESC")
    suspend fun getAllChatsOnce(): List<ChatEntity>

    @Query("SELECT * FROM chats WHERE id = :chatId LIMIT 1")
    suspend fun getChatById(chatId: String): ChatEntity?

    @Query("SELECT * FROM chats WHERE id = :chatId LIMIT 1")
    fun observeChatById(chatId: String): Flow<ChatEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChat(chat: ChatEntity)

    @Query("UPDATE chats SET title = :title, updatedAt = :updatedAt WHERE id = :chatId")
    suspend fun updateChatTitle(chatId: String, title: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE chats SET selectedModelId = :modelId, updatedAt = :updatedAt WHERE id = :chatId")
    suspend fun updateChatModel(chatId: String, modelId: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE chats SET updatedAt = :updatedAt WHERE id = :chatId")
    suspend fun touchChat(chatId: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM chats WHERE id = :chatId")
    suspend fun deleteChat(chatId: String)

    // --- Messages ---
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    fun observeMessagesForChat(chatId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    suspend fun getMessagesForChatOnce(chatId: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE id = :messageId LIMIT 1")
    suspend fun getMessageById(messageId: String): MessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessage(message: MessageEntity)

    @Update
    suspend fun updateMessage(message: MessageEntity)

    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun deleteMessage(messageId: String)

    // --- VFS Nodes ---
    @Query("SELECT * FROM vfs_nodes WHERE chatId = :chatId ORDER BY isDirectory DESC, virtualPath ASC")
    fun observeVfsNodes(chatId: String): Flow<List<VfsNodeEntity>>

    @Query("SELECT * FROM vfs_nodes WHERE chatId = :chatId ORDER BY isDirectory DESC, virtualPath ASC")
    suspend fun getVfsNodesOnce(chatId: String): List<VfsNodeEntity>

    @Query("SELECT * FROM vfs_nodes WHERE chatId = :chatId AND virtualPath = :virtualPath LIMIT 1")
    suspend fun getVfsNodeByPath(chatId: String, virtualPath: String): VfsNodeEntity?

    @Query("SELECT * FROM vfs_nodes WHERE chatId = :chatId AND (virtualPath = :pathPrefix OR virtualPath LIKE :pathPrefixWithSlash || '%')")
    suspend fun getVfsSubtree(chatId: String, pathPrefix: String, pathPrefixWithSlash: String): List<VfsNodeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVfsNode(node: VfsNodeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVfsNodes(nodes: List<VfsNodeEntity>)

    @Query("DELETE FROM vfs_nodes WHERE id = :nodeId")
    suspend fun deleteVfsNodeById(nodeId: String)

    @Query("DELETE FROM vfs_nodes WHERE chatId = :chatId AND (virtualPath = :virtualPath OR virtualPath LIKE :prefixWithSlash || '%')")
    suspend fun deleteVfsSubtree(chatId: String, virtualPath: String, prefixWithSlash: String)

    // --- AI Models ---
    @Query("SELECT * FROM ai_models ORDER BY isPinned DESC, displayName ASC")
    fun observeModels(): Flow<List<AiModelEntity>>

    @Query("SELECT * FROM ai_models ORDER BY isPinned DESC, displayName ASC")
    suspend fun getModelsOnce(): List<AiModelEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertModels(models: List<AiModelEntity>)
}
