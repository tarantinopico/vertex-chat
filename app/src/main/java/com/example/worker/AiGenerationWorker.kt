package com.example.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.R
import com.example.VertexChatApplication
import com.example.ai.StreamEvent
import com.example.data.local.MessageEntity
import com.example.data.local.MessageRole
import com.example.data.local.MessageStatus
import com.example.data.local.ToolCallRecord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * WorkManager CoroutineWorker pro spolehlivé provádění dlouhých AI úloh na pozadí.
 * - Běží jako Foreground Service (dataSync na Androidu 14+) s notifikací a tlačítkem Zrušit.
 * - Streamuje myšlenkové pochody (Thought Summary) i odpověď přímo do Room databáze.
 * - Provádí lokální VFS Function Calling ve smyčce a ukládá Tool-Use karty.
 * - Při finishReason == "MAX_TOKENS" automaticky navazuje dalším voláním, dokud model nedokončí.
 */
class AiGenerationWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val container = (appContext.applicationContext as VertexChatApplication).container
    private val dao = container.database.dao()
    private val prefsRepo = container.preferencesRepository
    private val aiClient = container.vertexAiClient
    private val vfsTools = container.vfsAiTools
    private val notificationManager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun doWork(): Result {
        val chatId = inputData.getString(KEY_CHAT_ID) ?: return Result.failure()
        val assistantMessageId = inputData.getString(KEY_ASSISTANT_MESSAGE_ID) ?: return Result.failure()
        val modelId = inputData.getString(KEY_MODEL_ID) ?: "gemini-3.1-pro-preview"

        ensureNotificationChannel()
        val notificationId = assistantMessageId.hashCode() and 0x7FFFFFFF

        try {
            setForeground(createForegroundInfo(notificationId, "Přemýšlím a analyzuji kontext…", 0))
        } catch (_: Exception) {
            // In unit test or background-restricted state, continue execution safely
        }

        val accumulatedText = StringBuilder()
        val accumulatedThoughts = StringBuilder()
        val toolRecords = mutableListOf<ToolCallRecord>()
        var continuationSteps = 0

        return try {
            val settings = prefsRepo.settingsFlow.first()
            val chat = dao.getChatById(chatId)
            val systemInstruction = chat?.systemInstruction.orEmpty()

            // Build initial conversation history from Room (excluding the placeholder assistant message)
            val historyMessages = dao.getMessagesForChatOnce(chatId)
                .filter { it.id != assistantMessageId && it.content.isNotBlank() }

            val contentsList = mutableListOf<JsonElement>()
            for (msg in historyMessages) {
                val roleStr = if (msg.role == MessageRole.USER.name) "user" else "model"
                contentsList.add(
                    buildJsonObject {
                        put("role", roleStr)
                        putJsonArray("parts") {
                            addJsonObject { put("text", msg.content) }
                        }
                    }
                )
            }

            // Inject current VFS file list summary into system instruction for grounded file awareness
            val existingNodes = container.vfsManager.listFiles(chatId, "/", recursive = true).getOrDefault(emptyList())
            val vfsContextSuffix = if (existingNodes.isEmpty()) {
                "\n\n[Aktuální stav VFS chatu: prázdný kořenový adresář '/']"
            } else {
                "\n\n[Aktuální soubory ve VFS chatu:\n" +
                    existingNodes.joinToString("\n") { "- ${it.virtualPath} (${if (it.isDirectory) "složka" else "${it.lineCount} řádků, ${it.sizeBytes} B"})" } +
                    "\n]"
            }
            val fullSystemInstruction = systemInstruction + vfsContextSuffix
            val toolsArray = vfsTools.buildToolsJsonArray()

            var shouldContinueLoop = true
            var toolRoundCount = 0
            val maxToolRounds = 12
            val maxContinuations = if (settings.autoContinueMaxTokens) settings.maxAutoContinueSteps else 0
            var lastPersistTime = 0L

            while (shouldContinueLoop && !isStopped) {
                shouldContinueLoop = false
                var roundFinishReason: String? = null
                val pendingFunctionCalls = mutableListOf<StreamEvent.FunctionCallEvent>()
                val roundModelText = StringBuilder()

                val streamResult = aiClient.streamGenerateContent(
                    settings = settings,
                    modelId = modelId,
                    systemInstruction = fullSystemInstruction,
                    contents = JsonArray(contentsList),
                    tools = toolsArray
                ) { event ->
                    when (event) {
                        is StreamEvent.ThoughtDelta -> {
                            accumulatedThoughts.append(event.text)
                            val now = System.currentTimeMillis()
                            if (now - lastPersistTime > 120L) {
                                lastPersistTime = now
                                persistProgressBlocking(
                                    chatId = chatId,
                                    messageId = assistantMessageId,
                                    modelId = modelId,
                                    content = accumulatedText.toString(),
                                    thoughts = accumulatedThoughts.toString(),
                                    tools = toolRecords,
                                    continuations = continuationSteps,
                                    status = MessageStatus.STREAMING
                                )
                            }
                        }

                        is StreamEvent.TextDelta -> {
                            roundModelText.append(event.text)
                            accumulatedText.append(event.text)
                            val now = System.currentTimeMillis()
                            if (now - lastPersistTime > 100L) {
                                lastPersistTime = now
                                persistProgressBlocking(
                                    chatId = chatId,
                                    messageId = assistantMessageId,
                                    modelId = modelId,
                                    content = accumulatedText.toString(),
                                    thoughts = accumulatedThoughts.toString(),
                                    tools = toolRecords,
                                    continuations = continuationSteps,
                                    status = MessageStatus.STREAMING
                                )
                            }
                        }

                        is StreamEvent.FunctionCallEvent -> {
                            pendingFunctionCalls.add(event)
                        }

                        is StreamEvent.Finish -> {
                            roundFinishReason = event.finishReason
                        }

                        is StreamEvent.Error -> {
                            throw IllegalStateException(event.message)
                        }
                    }
                }

                streamResult.getOrThrow()

                // 1. Handle Function Calls if the model invoked VFS tools
                if (pendingFunctionCalls.isNotEmpty() && toolRoundCount < maxToolRounds && !isStopped) {
                    toolRoundCount++

                    // Append model's turn with functionCall parts
                    contentsList.add(
                        buildJsonObject {
                            put("role", "model")
                            putJsonArray("parts") {
                                if (roundModelText.isNotEmpty()) {
                                    addJsonObject { put("text", roundModelText.toString()) }
                                }
                                pendingFunctionCalls.forEach { fc ->
                                    add(fc.rawPartJson)
                                }
                            }
                        }
                    )

                    val functionResponseParts = mutableListOf<JsonObject>()
                    for (fc in pendingFunctionCalls) {
                        if (isStopped) break
                        val runningRecord = vfsTools.buildRunningRecord(fc.name, fc.args)
                        toolRecords.add(runningRecord)

                        updateForegroundSafe(notificationId, runningRecord.actionTitle, 50)
                        persistProgress(
                            chatId = chatId,
                            messageId = assistantMessageId,
                            modelId = modelId,
                            content = accumulatedText.toString(),
                            thoughts = accumulatedThoughts.toString(),
                            tools = toolRecords,
                            continuations = continuationSteps,
                            status = MessageStatus.STREAMING
                        )

                        val outcome = vfsTools.executeToolCall(chatId, runningRecord, fc.args)
                        val idx = toolRecords.indexOfFirst { it.id == runningRecord.id }
                        if (idx >= 0) {
                            toolRecords[idx] = outcome.toolRecord
                        }
                        functionResponseParts.add(outcome.functionResponseJson)

                        persistProgress(
                            chatId = chatId,
                            messageId = assistantMessageId,
                            modelId = modelId,
                            content = accumulatedText.toString(),
                            thoughts = accumulatedThoughts.toString(),
                            tools = toolRecords,
                            continuations = continuationSteps,
                            status = MessageStatus.STREAMING
                        )
                    }

                    // Append functionResponse turn back to model
                    contentsList.add(
                        buildJsonObject {
                            put("role", "user")
                            put("parts", JsonArray(functionResponseParts))
                        }
                    )

                    updateForegroundSafe(notificationId, "Zpracovávám výsledek VFS operace…", 70)
                    shouldContinueLoop = true
                    continue
                }

                // 2. Handle automatic continuation on MAX_TOKENS
                if (roundFinishReason == "MAX_TOKENS" && continuationSteps < maxContinuations && !isStopped) {
                    continuationSteps++
                    val progressPercent = ((continuationSteps.toFloat() / maxContinuations.coerceAtLeast(1)) * 90f).toInt()
                    updateForegroundSafe(
                        notificationId,
                        "Automaticky pokračuji v dlouhé odpovědi (krok $continuationSteps/$maxContinuations)…",
                        progressPercent
                    )

                    contentsList.add(
                        buildJsonObject {
                            put("role", "model")
                            putJsonArray("parts") {
                                addJsonObject { put("text", roundModelText.toString()) }
                            }
                        }
                    )
                    contentsList.add(
                        buildJsonObject {
                            put("role", "user")
                            putJsonArray("parts") {
                                addJsonObject {
                                    put(
                                        "text",
                                        "Pokračuj v předchozí odpovědi přesně od místa, kde byla přerušena limitem tokenů. Neopakuj již vypsaný úvod."
                                    )
                                }
                            }
                        }
                    )
                    shouldContinueLoop = true
                }
            }

            if (isStopped) {
                persistProgress(
                    chatId = chatId,
                    messageId = assistantMessageId,
                    modelId = modelId,
                    content = accumulatedText.toString().ifBlank { "_Generování bylo zastaveno uživatelem._" },
                    thoughts = accumulatedThoughts.toString(),
                    tools = toolRecords,
                    continuations = continuationSteps,
                    status = MessageStatus.CANCELLED
                )
                return Result.failure()
            }

            val finalContent = accumulatedText.toString().ifBlank {
                if (toolRecords.isNotEmpty()) {
                    "Operace ve Virtuálním souborovém systému byly úspěšně provedeny (${toolRecords.size}×)."
                } else {
                    "Model nevrátil žádný textový výstup."
                }
            }

            persistProgress(
                chatId = chatId,
                messageId = assistantMessageId,
                modelId = modelId,
                content = finalContent,
                thoughts = accumulatedThoughts.toString(),
                tools = toolRecords,
                continuations = continuationSteps,
                status = MessageStatus.COMPLETED,
                finishReason = "STOP"
            )
            dao.touchChat(chatId)

            postCompletionSummaryNotification(
                notificationId = notificationId + 1,
                title = "Odpověď dokončena (${modelId})",
                summary = finalContent.lineSequence().firstOrNull()?.take(120) ?: "Úloha dokončena."
            )

            Result.success(workDataOf(KEY_ASSISTANT_MESSAGE_ID to assistantMessageId))
        } catch (ce: CancellationException) {
            persistProgress(
                chatId = chatId,
                messageId = assistantMessageId,
                modelId = modelId,
                content = accumulatedText.toString().ifBlank { "_Generování bylo zrušeno._" },
                thoughts = accumulatedThoughts.toString(),
                tools = toolRecords,
                continuations = continuationSteps,
                status = MessageStatus.CANCELLED
            )
            throw ce
        } catch (e: Exception) {
            val errorMsg = e.message ?: "Neočekávaná chyba při komunikaci s AI."
            persistProgress(
                chatId = chatId,
                messageId = assistantMessageId,
                modelId = modelId,
                content = accumulatedText.toString(),
                thoughts = accumulatedThoughts.toString(),
                tools = toolRecords,
                continuations = continuationSteps,
                status = MessageStatus.ERROR,
                errorMessage = errorMsg
            )
            postCompletionSummaryNotification(
                notificationId = notificationId + 1,
                title = "Chyba AI úlohy",
                summary = errorMsg.take(140)
            )
            Result.failure(workDataOf(KEY_ERROR_MESSAGE to errorMsg))
        }
    }

    private suspend fun persistProgress(
        chatId: String,
        messageId: String,
        modelId: String,
        content: String,
        thoughts: String,
        tools: List<ToolCallRecord>,
        continuations: Int,
        status: MessageStatus,
        finishReason: String? = null,
        errorMessage: String? = null
    ) {
        val existing = dao.getMessageById(messageId)
        val toolsJson = json.encodeToString(tools)
        val updated = MessageEntity(
            id = messageId,
            chatId = chatId,
            role = MessageRole.MODEL.name,
            content = content,
            thoughtSummary = thoughts,
            toolCallsJson = toolsJson,
            status = status.name,
            modelId = modelId,
            continuationCount = continuations,
            finishReason = finishReason,
            errorMessage = errorMessage,
            timestamp = existing?.timestamp ?: System.currentTimeMillis()
        )
        dao.upsertMessage(updated)
    }

    private fun persistProgressBlocking(
        chatId: String,
        messageId: String,
        modelId: String,
        content: String,
        thoughts: String,
        tools: List<ToolCallRecord>,
        continuations: Int,
        status: MessageStatus
    ) {
        kotlinx.coroutines.runBlocking {
            persistProgress(
                chatId = chatId,
                messageId = messageId,
                modelId = modelId,
                content = content,
                thoughts = thoughts,
                tools = tools,
                continuations = continuations,
                status = status
            )
        }
    }

    private suspend fun updateForegroundSafe(notificationId: Int, statusText: String, progress: Int) {
        try {
            setForeground(createForegroundInfo(notificationId, statusText, progress))
        } catch (_: Exception) {
        }
    }

    private fun createForegroundInfo(
        notificationId: Int,
        statusText: String,
        progressPercent: Int
    ): ForegroundInfo {
        val cancelIntent = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Vertex Chat • Generuji odpověď…")
            .setContentText(statusText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                applicationContext.getString(R.string.action_cancel),
                cancelIntent
            )

        if (progressPercent in 1..99) {
            builder.setProgress(100, progressPercent, false)
        } else {
            builder.setProgress(100, 0, true)
        }

        val notification = builder.build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    private fun postCompletionSummaryNotification(
        notificationId: Int,
        title: String,
        summary: String
    ) {
        runCatching {
            val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(title)
                .setContentText(summary)
                .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
                .setAutoCancel(true)
                .build()
            notificationManager.notify(notificationId, notification)
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                applicationContext.getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = applicationContext.getString(R.string.notif_channel_desc)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "vertex_chat_ai_worker_channel"
        const val KEY_CHAT_ID = "chat_id"
        const val KEY_ASSISTANT_MESSAGE_ID = "assistant_message_id"
        const val KEY_MODEL_ID = "model_id"
        const val KEY_ERROR_MESSAGE = "error_message"

        fun uniqueWorkNameForChat(chatId: String): String = "vertex_ai_generation_$chatId"
    }
}
