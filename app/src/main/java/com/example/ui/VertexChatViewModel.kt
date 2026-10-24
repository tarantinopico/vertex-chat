package com.example.ui

import android.app.Application
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.VertexChatApplication
import com.example.data.local.AiModelEntity
import com.example.data.local.ChatEntity
import com.example.data.local.MessageEntity
import com.example.data.local.MessageRole
import com.example.data.local.MessageStatus
import com.example.data.local.VfsNodeEntity
import com.example.data.preferences.AppSettings
import com.example.data.preferences.AuthMode
import com.example.data.security.VaultState
import com.example.data.vfs.VfsLineItem
import com.example.worker.AiGenerationWorker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

enum class AppDestination {
    CHAT,
    VFS_EXPLORER,
    SETTINGS
}

data class OpenVfsFileState(
    val node: VfsNodeEntity,
    val lines: List<VfsLineItem>,
    val totalLines: Int,
    val rawEditableText: String,
    val isEditing: Boolean = false,
    val searchQuery: String = "",
    val searchMatches: List<VfsLineItem> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
class VertexChatViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as VertexChatApplication
    private val container = app.container
    private val dao = container.database.dao()
    private val prefsRepo = container.preferencesRepository
    private val vault = container.keystoreVault
    private val vfsManager = container.vfsManager
    private val aiClient = container.vertexAiClient
    private val workManager = WorkManager.getInstance(application)

    private val _currentDestination = MutableStateFlow(AppDestination.CHAT)
    val currentDestination: StateFlow<AppDestination> = _currentDestination.asStateFlow()

    private val _activeChatId = MutableStateFlow<String?>(null)
    val activeChatId: StateFlow<String?> = _activeChatId.asStateFlow()

    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbarEvents = _snackbarEvents.asSharedFlow()

    private val _isRefreshingModels = MutableStateFlow(false)
    val isRefreshingModels: StateFlow<Boolean> = _isRefreshingModels.asStateFlow()

    private val _openedFileState = MutableStateFlow<OpenVfsFileState?>(null)
    val openedFileState: StateFlow<OpenVfsFileState?> = _openedFileState.asStateFlow()

    private val _pendingExportNode = MutableStateFlow<VfsNodeEntity?>(null)
    val pendingExportNode: StateFlow<VfsNodeEntity?> = _pendingExportNode.asStateFlow()

    val settingsState: StateFlow<AppSettings> = prefsRepo.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettings()
    )

    val vaultState: StateFlow<VaultState> = vault.vaultState

    val chatsState: StateFlow<List<ChatEntity>> = dao.observeAllChats().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val modelsState: StateFlow<List<AiModelEntity>> = dao.observeModels().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val activeChatState: StateFlow<ChatEntity?> = _activeChatId
        .flatMapLatest { id -> if (id == null) flowOf(null) else dao.observeChatById(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val activeMessagesState: StateFlow<List<MessageEntity>> = _activeChatId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else dao.observeMessagesForChat(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activeVfsNodesState: StateFlow<List<VfsNodeEntity>> = _activeChatId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else vfsManager.observeNodes(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val existingChats = dao.getAllChatsOnce()
            if (existingChats.isEmpty()) {
                val created = createNewChatInternal("Architektura & Kód")
                _activeChatId.value = created.id
            } else {
                _activeChatId.value = existingChats.first().id
            }
        }
    }

    fun navigateTo(destination: AppDestination) {
        _currentDestination.value = destination
    }

    fun selectChat(chatId: String) {
        _activeChatId.value = chatId
        _currentDestination.value = AppDestination.CHAT
    }

    fun createNewChat(title: String = "Nová konverzace") {
        viewModelScope.launch {
            val chat = createNewChatInternal(title)
            _activeChatId.value = chat.id
            _currentDestination.value = AppDestination.CHAT
        }
    }

    private suspend fun createNewChatInternal(title: String): ChatEntity {
        val defaultModel = settingsState.value.defaultModelId
        val chat = ChatEntity(
            id = UUID.randomUUID().toString(),
            title = title,
            selectedModelId = defaultModel
        )
        dao.upsertChat(chat)
        return chat
    }

    fun deleteChat(chatId: String) {
        viewModelScope.launch {
            workManager.cancelUniqueWork(AiGenerationWorker.uniqueWorkNameForChat(chatId))
            vfsManager.deleteChatVfsFolder(chatId)
            dao.deleteChat(chatId)
            val remaining = dao.getAllChatsOnce()
            if (remaining.isEmpty()) {
                val created = createNewChatInternal("Nová konverzace")
                _activeChatId.value = created.id
            } else if (_activeChatId.value == chatId) {
                _activeChatId.value = remaining.first().id
            }
            _snackbarEvents.tryEmit("Konverzace a její VFS byly smazány")
        }
    }

    fun selectModelForActiveChat(modelId: String) {
        viewModelScope.launch {
            val chatId = _activeChatId.value ?: return@launch
            dao.updateChatModel(chatId, modelId)
            prefsRepo.updateDefaultModel(modelId)
        }
    }

    fun refreshModelsFromApi() {
        viewModelScope.launch {
            _isRefreshingModels.value = true
            val result = aiClient.fetchAvailableModels(settingsState.value)
            result.fold(
                onSuccess = { models ->
                    dao.upsertModels(models)
                    _snackbarEvents.tryEmit("Načteno ${models.size} modelů z API")
                },
                onFailure = { err ->
                    _snackbarEvents.tryEmit(err.message ?: "Nepodařilo se načíst modely")
                }
            )
            _isRefreshingModels.value = false
        }
    }

    fun sendMessage(userPrompt: String) {
        val trimmed = userPrompt.trim()
        if (trimmed.isEmpty()) return

        viewModelScope.launch {
            val chatId = _activeChatId.value ?: createNewChatInternal("Nová konverzace").id.also {
                _activeChatId.value = it
            }
            val chat = dao.getChatById(chatId) ?: return@launch
            val existingMessages = dao.getMessagesForChatOnce(chatId)

            // Auto-title chat on first message
            if (existingMessages.isEmpty() && (chat.title == "Nová konverzace" || chat.title == "Architektura & Kód")) {
                val newTitle = trimmed.lineSequence().first().take(42)
                dao.updateChatTitle(chatId, newTitle)
            }

            val now = System.currentTimeMillis()
            val userMsg = MessageEntity(
                id = UUID.randomUUID().toString(),
                chatId = chatId,
                role = MessageRole.USER.name,
                content = trimmed,
                status = MessageStatus.COMPLETED.name,
                modelId = chat.selectedModelId,
                timestamp = now
            )
            dao.upsertMessage(userMsg)

            val assistantMsgId = UUID.randomUUID().toString()
            val placeholderAssistant = MessageEntity(
                id = assistantMsgId,
                chatId = chatId,
                role = MessageRole.MODEL.name,
                content = "",
                thoughtSummary = "",
                status = MessageStatus.STREAMING.name,
                modelId = chat.selectedModelId,
                timestamp = now + 1L
            )
            dao.upsertMessage(placeholderAssistant)
            dao.touchChat(chatId)

            val workRequest = OneTimeWorkRequestBuilder<AiGenerationWorker>()
                .setInputData(
                    workDataOf(
                        AiGenerationWorker.KEY_CHAT_ID to chatId,
                        AiGenerationWorker.KEY_ASSISTANT_MESSAGE_ID to assistantMsgId,
                        AiGenerationWorker.KEY_MODEL_ID to chat.selectedModelId
                    )
                )
                .addTag(chatId)
                .build()

            workManager.enqueueUniqueWork(
                AiGenerationWorker.uniqueWorkNameForChat(chatId),
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }
    }

    fun cancelActiveGeneration() {
        val chatId = _activeChatId.value ?: return
        workManager.cancelUniqueWork(AiGenerationWorker.uniqueWorkNameForChat(chatId))
        viewModelScope.launch {
            val messages = dao.getMessagesForChatOnce(chatId)
            messages.filter { it.status == MessageStatus.STREAMING.name }.forEach { streamingMsg ->
                dao.upsertMessage(
                    streamingMsg.copy(
                        status = MessageStatus.CANCELLED.name,
                        content = streamingMsg.content.ifBlank { "_Generování zastaveno uživatelem._" }
                    )
                )
            }
            _snackbarEvents.tryEmit("Generování bylo zastaveno")
        }
    }

    // --- VFS UI Operations ---

    fun openVfsFileByPath(virtualPath: String) {
        val chatId = _activeChatId.value ?: return
        viewModelScope.launch {
            val windowResult = vfsManager.readFileLines(chatId, virtualPath, startLine = 1, endLine = 2500)
            windowResult.fold(
                onSuccess = { win ->
                    val rawText = win.lines.joinToString("\n") { it.content }
                    _openedFileState.value = OpenVfsFileState(
                        node = win.node,
                        lines = win.lines,
                        totalLines = win.totalLines,
                        rawEditableText = rawText,
                        isEditing = false
                    )
                },
                onFailure = { err ->
                    _snackbarEvents.tryEmit(err.message ?: "Nelze otevřít soubor $virtualPath")
                }
            )
        }
    }

    fun closeOpenedVfsFile() {
        _openedFileState.value = null
    }

    fun toggleFileEditMode(editing: Boolean) {
        val current = _openedFileState.value ?: return
        _openedFileState.value = current.copy(isEditing = editing)
    }

    fun updateOpenedFileSearchQuery(query: String) {
        val current = _openedFileState.value ?: return
        val chatId = _activeChatId.value ?: return
        _openedFileState.value = current.copy(searchQuery = query)
        if (query.isBlank()) {
            _openedFileState.value = current.copy(searchQuery = "", searchMatches = emptyList())
            return
        }
        viewModelScope.launch {
            val matches = vfsManager.searchInFile(chatId, current.node.virtualPath, query)
                .getOrDefault(emptyList())
            _openedFileState.value = _openedFileState.value?.copy(searchMatches = matches)
        }
    }

    fun saveEditedVfsFile(newContent: String) {
        val current = _openedFileState.value ?: return
        val chatId = _activeChatId.value ?: return
        viewModelScope.launch {
            val res = vfsManager.writeFile(
                chatId = chatId,
                virtualPath = current.node.virtualPath,
                content = newContent,
                append = false
            )
            res.fold(
                onSuccess = { updatedNode ->
                    openVfsFileByPath(updatedNode.virtualPath)
                    _snackbarEvents.tryEmit("Soubor ${updatedNode.name} byl uložen (${updatedNode.lineCount} řádků)")
                },
                onFailure = { err ->
                    _snackbarEvents.tryEmit(err.message ?: "Chyba při ukládání souboru")
                }
            )
        }
    }

    fun createVfsFileFromUi(virtualPath: String, initialContent: String) {
        val chatId = _activeChatId.value ?: return
        viewModelScope.launch {
            vfsManager.writeFile(chatId, virtualPath, initialContent, append = false).fold(
                onSuccess = { node ->
                    _snackbarEvents.tryEmit("Vytvořen soubor ${node.virtualPath}")
                    openVfsFileByPath(node.virtualPath)
                },
                onFailure = { err ->
                    _snackbarEvents.tryEmit(err.message ?: "Nelze vytvořit soubor")
                }
            )
        }
    }

    fun createVfsFolderFromUi(virtualPath: String) {
        val chatId = _activeChatId.value ?: return
        viewModelScope.launch {
            vfsManager.createFolder(chatId, virtualPath).fold(
                onSuccess = { node ->
                    _snackbarEvents.tryEmit("Vytvořena složka ${node.virtualPath}")
                },
                onFailure = { err ->
                    _snackbarEvents.tryEmit(err.message ?: "Nelze vytvořit složku")
                }
            )
        }
    }

    fun renameVfsNodeFromUi(virtualPath: String, newName: String) {
        val chatId = _activeChatId.value ?: return
        viewModelScope.launch {
            vfsManager.renameFile(chatId, virtualPath, newName).fold(
                onSuccess = { node ->
                    _snackbarEvents.tryEmit("Přejmenováno na ${node.virtualPath}")
                },
                onFailure = { err ->
                    _snackbarEvents.tryEmit(err.message ?: "Chyba přejmenování")
                }
            )
        }
    }

    fun deleteVfsNodeFromUi(virtualPath: String) {
        val chatId = _activeChatId.value ?: return
        viewModelScope.launch {
            vfsManager.deleteFile(chatId, virtualPath).fold(
                onSuccess = { count ->
                    if (_openedFileState.value?.node?.virtualPath == virtualPath) {
                        _openedFileState.value = null
                    }
                    _snackbarEvents.tryEmit("Smazáno ($count položek)")
                },
                onFailure = { err ->
                    _snackbarEvents.tryEmit(err.message ?: "Chyba při mazání")
                }
            )
        }
    }

    fun requestSafExportByPath(virtualPath: String) {
        val chatId = _activeChatId.value ?: return
        viewModelScope.launch {
            val node = dao.getVfsNodeByPath(chatId, vfsManager.normalizeVirtualPath(virtualPath))
            if (node != null && !node.isDirectory) {
                _pendingExportNode.value = node
            } else {
                _snackbarEvents.tryEmit("Soubor $virtualPath nelze exportovat")
            }
        }
    }

    fun clearPendingExport() {
        _pendingExportNode.value = null
    }

    fun completeSafExport(contentResolver: ContentResolver, targetUri: Uri) {
        val node = _pendingExportNode.value ?: return
        val chatId = _activeChatId.value ?: return
        _pendingExportNode.value = null
        viewModelScope.launch {
            vfsManager.exportToUri(chatId, node.virtualPath, contentResolver, targetUri).fold(
                onSuccess = { bytes ->
                    _snackbarEvents.tryEmit("Exportováno ${node.name} ($bytes B)")
                },
                onFailure = { err ->
                    _snackbarEvents.tryEmit(err.message ?: "Chyba při exportu souboru")
                }
            )
        }
    }

    fun shareVfsFile(virtualPath: String) {
        val chatId = _activeChatId.value ?: return
        viewModelScope.launch {
            vfsManager.getPhysicalFileForSharing(chatId, virtualPath).fold(
                onSuccess = { file ->
                    try {
                        val authority = "${app.packageName}.fileprovider"
                        val uri = FileProvider.getUriForFile(app, authority, file)
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        val chooser = Intent.createChooser(shareIntent, "Sdílet soubor ${file.name}").apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        app.startActivity(chooser)
                    } catch (e: Exception) {
                        _snackbarEvents.tryEmit("Nelze spustit sdílení: ${e.message}")
                    }
                },
                onFailure = { err ->
                    _snackbarEvents.tryEmit(err.message ?: "Soubor nenalezen")
                }
            )
        }
    }

    // --- Authentication & Vault Operations ---

    fun setAuthMode(mode: AuthMode) {
        viewModelScope.launch {
            prefsRepo.updateAuthMode(mode)
            _snackbarEvents.tryEmit(
                if (mode == AuthMode.VERTEX_SERVICE_ACCOUNT) {
                    "Režim přepnut na Vertex AI Service Account (OAuth2)"
                } else {
                    "Režim přepnut na přímý Gemini API klíč"
                }
            )
        }
    }

    fun saveAndEncryptServiceAccount(
        rawJson: String,
        password: String,
        region: String,
        customProjectId: String
    ) {
        viewModelScope.launch {
            val result = vault.saveAndUnlockServiceAccount(rawJson, password)
            result.fold(
                onSuccess = { creds ->
                    val finalProject = customProjectId.ifBlank { creds.projectId }
                    prefsRepo.updateVertexConfig(
                        projectId = finalProject,
                        region = region.ifBlank { "us-central1" },
                        defaultModelId = settingsState.value.defaultModelId
                    )
                    prefsRepo.updateAuthMode(AuthMode.VERTEX_SERVICE_ACCOUNT)
                    _snackbarEvents.tryEmit("Service Account zašifrován v Keystore a odemčen (${creds.clientEmail})")
                    refreshModelsFromApi()
                },
                onFailure = { err ->
                    _snackbarEvents.tryEmit(err.message ?: "Chyba při šifrování Service Accountu")
                }
            )
        }
    }

    fun unlockServiceAccountVault(password: String) {
        viewModelScope.launch {
            vault.unlockVault(password).fold(
                onSuccess = { creds ->
                    if (settingsState.value.projectId.isBlank()) {
                        prefsRepo.updateVertexConfig(
                            projectId = creds.projectId,
                            region = settingsState.value.region,
                            defaultModelId = settingsState.value.defaultModelId
                        )
                    }
                    _snackbarEvents.tryEmit("Trezor úspěšně odemčen (${creds.clientEmail})")
                    refreshModelsFromApi()
                },
                onFailure = { err ->
                    _snackbarEvents.tryEmit(err.message ?: "Nesprávné heslo trezoru")
                }
            )
        }
    }

    fun lockServiceAccountVault() {
        vault.lockVault()
        container.tokenProvider.invalidateCache()
        _snackbarEvents.tryEmit("Trezor Service Accountu byl uzamčen a OAuth2 cache vymazána")
    }

    fun clearServiceAccountVault() {
        vault.clearVault()
        container.tokenProvider.invalidateCache()
        _snackbarEvents.tryEmit("Service Account byl trvale odstraněn z trezoru")
    }

    fun saveVertexProjectSettings(projectId: String, region: String, defaultModelId: String) {
        viewModelScope.launch {
            prefsRepo.updateVertexConfig(projectId, region, defaultModelId)
            _snackbarEvents.tryEmit("Konfigurace Vertex AI uložena")
        }
    }

    fun saveCustomGeminiKey(apiKey: String) {
        viewModelScope.launch {
            prefsRepo.updateCustomGeminiApiKey(apiKey)
            _snackbarEvents.tryEmit("Gemini API klíč aktualizován")
        }
    }

    fun saveGenerationParameters(
        includeThoughts: Boolean,
        thinkingBudget: String,
        temperature: Float,
        autoContinue: Boolean,
        maxContinueSteps: Int
    ) {
        viewModelScope.launch {
            prefsRepo.updateGenerationParams(
                includeThoughts = includeThoughts,
                thinkingBudget = thinkingBudget,
                temperature = temperature,
                autoContinue = autoContinue,
                maxContinueSteps = maxContinueSteps
            )
            _snackbarEvents.tryEmit("Parametry generování uloženy")
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application)
                VertexChatViewModel(app)
            }
        }
    }
}
