package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.ChatEntity
import com.example.data.local.VertexChatDatabase
import com.example.data.security.KeystoreServiceAccountVault
import com.example.data.vfs.VirtualFileSystemManager
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var context: Context
    private lateinit var db: VertexChatDatabase
    private lateinit var vfsManager: VirtualFileSystemManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, VertexChatDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        vfsManager = VirtualFileSystemManager(context, db.dao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `app name matches Vertex Chat`() {
        val appName = context.getString(R.string.app_name)
        assertEquals("Vertex Chat", appName)
    }

    @Test
    fun `vfs manager supports streamed write, append, windowed read, rename, move and delete`() = runTest {
        val chatId = "test-chat-vfs-1"
        db.dao().upsertChat(
            ChatEntity(
                id = chatId,
                title = "VFS Test Chat",
                selectedModelId = "gemini-3.1-pro-preview"
            )
        )

        // 1. Create folder
        val folderRes = vfsManager.createFolder(chatId, "/src/core")
        assertTrue(folderRes.isSuccess)
        assertEquals("/src/core", folderRes.getOrThrow().virtualPath)

        // 2. Write file & append lines
        val initialLines = (1..50).joinToString("\n") { "line_$it" }
        val writeRes = vfsManager.writeFile(chatId, "/src/core/Engine.kt", "$initialLines\n", append = false)
        assertTrue(writeRes.isSuccess)
        assertEquals(50, writeRes.getOrThrow().lineCount)

        val appendRes = vfsManager.writeFile(chatId, "/src/core/Engine.kt", "line_51\nline_52\n", append = true)
        assertTrue(appendRes.isSuccess)
        assertEquals(52, appendRes.getOrThrow().lineCount)

        // 3. Read windowed lines
        val windowRes = vfsManager.readFileLines(chatId, "/src/core/Engine.kt", startLine = 10, endLine = 12)
        assertTrue(windowRes.isSuccess)
        val window = windowRes.getOrThrow()
        assertEquals(3, window.lines.size)
        assertEquals("line_10", window.lines.first().content)
        assertEquals(52, window.totalLines)
        assertTrue(window.truncated)

        // 4. Rename & move
        val renameRes = vfsManager.renameFile(chatId, "/src/core/Engine.kt", "CoreEngine.kt")
        assertTrue(renameRes.isSuccess)
        assertEquals("/src/core/CoreEngine.kt", renameRes.getOrThrow().virtualPath)

        val moveRes = vfsManager.moveFile(chatId, "/src/core/CoreEngine.kt", "/app/CoreEngine.kt")
        assertTrue(moveRes.isSuccess)
        assertEquals("/app/CoreEngine.kt", moveRes.getOrThrow().virtualPath)

        // 5. Reject directory traversal
        val traversalRes = vfsManager.writeFile(chatId, "/src/../../escape.txt", "bad", append = false)
        assertTrue(traversalRes.isFailure)

        // 6. Delete subtree
        val deleteRes = vfsManager.deleteFile(chatId, "/app")
        assertTrue(deleteRes.isSuccess)
    }

    @Test
    fun `keystore vault encrypts and unlocks service account json with password`() = runTest {
        val vault = KeystoreServiceAccountVault(context)
        val sampleJson = """
            {
              "type": "service_account",
              "project_id": "vertex-demo-project",
              "private_key_id": "key123",
              "private_key": "-----BEGIN PRIVATE KEY-----\nMIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQC3\n-----END PRIVATE KEY-----\n",
              "client_email": "vertex-bot@vertex-demo-project.iam.gserviceaccount.com",
              "client_id": "123456789"
            }
        """.trimIndent()

        val saveRes = vault.saveAndUnlockServiceAccount(sampleJson, "StrongPass#2026")
        assertTrue(saveRes.isSuccess)
        assertEquals("vertex-demo-project", saveRes.getOrThrow().projectId)

        vault.lockVault()
        val wrongPassRes = vault.unlockVault("WrongPass")
        assertTrue(wrongPassRes.isFailure)

        val unlockRes = vault.unlockVault("StrongPass#2026")
        assertTrue(unlockRes.isSuccess)
        assertEquals("vertex-bot@vertex-demo-project.iam.gserviceaccount.com", unlockRes.getOrThrow().clientEmail)
    }
}
