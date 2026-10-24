package com.example.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.AiModelEntity
import com.example.data.local.ChatEntity
import com.example.data.local.MessageEntity
import com.example.data.local.MessageRole
import com.example.data.local.MessageStatus
import com.example.data.local.ToolCallRecord
import com.example.data.preferences.AppSettings
import com.example.data.preferences.AuthMode
import com.example.data.security.VaultState
import com.example.ui.components.MarkdownContent
import com.example.ui.components.ThinkingPulseIndicator
import com.example.ui.components.ThoughtSummaryAccordion
import com.example.ui.components.ToolUseCard
import com.example.ui.theme.AmoledBlack
import com.example.ui.theme.AmoledBorder
import com.example.ui.theme.AmoledSurfaceCard
import com.example.ui.theme.AmoledSurfaceElevated
import com.example.ui.theme.AmoledSurfaceHigh
import com.example.ui.theme.CrimsonContainer
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricBlueLight
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SoftViolet
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.UserBubbleBorder
import com.example.ui.theme.UserBubbleContainer
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val toolJsonParser = Json { ignoreUnknownKeys = true }

@Composable
fun ChatScreen(
    activeChat: ChatEntity?,
    messages: List<MessageEntity>,
    models: List<AiModelEntity>,
    vfsNodeCount: Int,
    settings: AppSettings,
    vaultState: VaultState,
    isRefreshingModels: Boolean,
    onOpenDrawer: () -> Unit,
    onSelectModel: (String) -> Unit,
    onRefreshModels: () -> Unit,
    onSendMessage: (String) -> Unit,
    onCancelGeneration: () -> Unit,
    onOpenVfsExplorer: () -> Unit,
    onOpenVfsFile: (String) -> Unit,
    onDownloadVfsFile: (String) -> Unit,
    onOpenAuthSettings: () -> Unit,
    onMessageCopied: () -> Unit
) {
    var promptText by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val isStreamingAny = remember(messages) {
        messages.any { it.status == MessageStatus.STREAMING.name }
    }

    LaunchedEffect(messages.size, messages.lastOrNull()?.content?.length) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem((messages.size - 1).coerceAtLeast(0))
        }
    }

    Scaffold(
        containerColor = AmoledBlack,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            GlassTopChatBar(
                activeChat = activeChat,
                models = models,
                vfsNodeCount = vfsNodeCount,
                settings = settings,
                vaultState = vaultState,
                isRefreshingModels = isRefreshingModels,
                onOpenDrawer = onOpenDrawer,
                onSelectModel = onSelectModel,
                onRefreshModels = onRefreshModels,
                onOpenVfsExplorer = onOpenVfsExplorer,
                onOpenAuthSettings = onOpenAuthSettings
            )
        },
        bottomBar = {
            GlassMessageInputBar(
                value = promptText,
                onValueChange = { promptText = it },
                isStreaming = isStreamingAny,
                onSend = {
                    val toSend = promptText
                    promptText = ""
                    onSendMessage(toSend)
                },
                onCancel = onCancelGeneration
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AmoledBlack)
                .padding(innerPadding)
        ) {
            if (messages.isEmpty()) {
                EmptyChatWorkspaceHero(
                    settings = settings,
                    vaultState = vaultState,
                    onQuickPrompt = { starter ->
                        onSendMessage(starter)
                    },
                    onOpenAuthSettings = onOpenAuthSettings
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("chat_messages_list"),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(items = messages, key = { it.id }) { msg ->
                        MessageBubbleItem(
                            message = msg,
                            onViewFile = onOpenVfsFile,
                            onDownloadFile = onDownloadVfsFile,
                            onCopied = onMessageCopied
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassTopChatBar(
    activeChat: ChatEntity?,
    models: List<AiModelEntity>,
    vfsNodeCount: Int,
    settings: AppSettings,
    vaultState: VaultState,
    isRefreshingModels: Boolean,
    onOpenDrawer: () -> Unit,
    onSelectModel: (String) -> Unit,
    onRefreshModels: () -> Unit,
    onOpenVfsExplorer: () -> Unit,
    onOpenAuthSettings: () -> Unit
) {
    var modelMenuExpanded by remember { mutableStateOf(false) }
    val selectedModelId = activeChat?.selectedModelId ?: settings.defaultModelId
    val selectedModel = models.find { it.modelId == selectedModelId }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xF2080C17), Color(0xD9050505))
                )
            )
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onOpenDrawer,
                modifier = Modifier.testTag("open_drawer_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Otevřít seznam konverzací",
                    tint = TextPrimary
                )
            }

            // Model selector dropdown pill
            Box(modifier = Modifier.weight(1f).padding(horizontal = 6.dp)) {
                Surface(
                    onClick = { modelMenuExpanded = true },
                    shape = RoundedCornerShape(12.dp),
                    color = AmoledSurfaceElevated,
                    border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("model_selector_dropdown")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = selectedModel?.displayName ?: selectedModelId,
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (settings.authMode == AuthMode.VERTEX_SERVICE_ACCOUNT) {
                                    "Vertex AI • ${settings.region}"
                                } else {
                                    "Gemini Direct API"
                                },
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 10.5.sp,
                                color = ElectricBlueLight
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Vybrat AI model",
                            tint = ElectricBlueLight
                        )
                    }
                }

                DropdownMenu(
                    expanded = modelMenuExpanded,
                    onDismissRequest = { modelMenuExpanded = false },
                    modifier = Modifier
                        .background(AmoledSurfaceElevated)
                        .border(1.dp, AmoledBorder, RoundedCornerShape(12.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "DOSTUPNÉ AI MODELY",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                        IconButton(
                            onClick = onRefreshModels,
                            enabled = !isRefreshingModels,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Obnovit modely z Vertex AI",
                                tint = ElectricBlueLight,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    HorizontalDivider(color = AmoledBorder)
                    models.forEach { model ->
                        val isSelected = model.modelId == selectedModelId
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = model.displayName,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) ElectricBlueLight else TextPrimary
                                        )
                                        if (model.supportsThinking) {
                                            Text(
                                                text = "THINKING",
                                                fontFamily = JetBrainsMonoFontFamily,
                                                fontSize = 9.sp,
                                                color = SoftViolet
                                            )
                                        }
                                    }
                                    Text(
                                        text = model.modelId,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                }
                            },
                            onClick = {
                                modelMenuExpanded = false
                                onSelectModel(model.modelId)
                            }
                        )
                    }
                }
            }

            // Auth status button
            IconButton(
                onClick = onOpenAuthSettings,
                modifier = Modifier.testTag("top_auth_status_button")
            ) {
                val isReady = settings.authMode == AuthMode.GEMINI_API_KEY || vaultState is VaultState.Unlocked
                Icon(
                    imageVector = if (isReady) Icons.Default.LockOpen else Icons.Default.Lock,
                    contentDescription = "Stav autentizace a trezoru",
                    tint = if (isReady) EmeraldSuccess else Color(0xFFF59E0B)
                )
            }

            // VFS Explorer button with badge
            IconButton(
                onClick = onOpenVfsExplorer,
                modifier = Modifier.testTag("top_vfs_button")
            ) {
                BadgedBox(
                    badge = {
                        if (vfsNodeCount > 0) {
                            Badge(
                                containerColor = ElectricBlue,
                                contentColor = Color.White
                            ) {
                                Text(text = vfsNodeCount.toString())
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderSpecial,
                        contentDescription = "Otevřít Virtuální souborový systém",
                        tint = ElectricBlueLight
                    )
                }
            }
        }

        HorizontalDivider(
            color = ElectricBlue.copy(alpha = 0.22f),
            thickness = 1.dp
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubbleItem(
    message: MessageEntity,
    onViewFile: (String) -> Unit,
    onDownloadFile: (String) -> Unit,
    onCopied: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val isUser = message.role == MessageRole.USER.name
    val isStreaming = message.status == MessageStatus.STREAMING.name
    val isError = message.status == MessageStatus.ERROR.name

    val toolRecords = remember(message.toolCallsJson) {
        runCatching {
            toolJsonParser.decodeFromString<List<ToolCallRecord>>(message.toolCallsJson)
        }.getOrDefault(emptyList())
    }

    val timeFormatted = remember(message.timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 580.dp)
                .fillMaxWidth(if (isUser) 0.88f else 0.98f)
                .animateContentSize()
                .combinedClickable(
                    onClick = {},
                    onLongClick = {
                        if (message.content.isNotBlank()) {
                            clipboard.setText(AnnotatedString(message.content))
                            onCopied()
                        }
                    }
                )
                .testTag(if (isUser) "user_message_bubble" else "ai_message_bubble"),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    isError -> CrimsonContainer
                    isUser -> UserBubbleContainer
                    else -> AmoledSurfaceCard
                }
            ),
            border = BorderStroke(
                width = 1.dp,
                color = when {
                    isError -> CrimsonError.copy(alpha = 0.7f)
                    isUser -> UserBubbleBorder
                    else -> AmoledBorder
                }
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header row inside bubble
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isUser) ElectricBlueLight else SoftViolet)
                        )
                        Text(
                            text = if (isUser) "Vy" else (message.modelId.ifBlank { "Vertex AI" }),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isUser) ElectricBlueLight else TextSecondary
                        )
                        if (message.continuationCount > 0) {
                            Text(
                                text = "• Auto-Continue (${message.continuationCount}×)",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldSuccess
                            )
                        }
                    }
                    Text(
                        text = timeFormatted,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }

                // 1. Expandable Thought Summary (if present)
                if (!isUser && message.thoughtSummary.isNotBlank()) {
                    ThoughtSummaryAccordion(
                        thoughtSummary = message.thoughtSummary,
                        isStreaming = isStreaming
                    )
                }

                // 2. Animated Thinking indicator while waiting for first token
                if (!isUser && isStreaming && message.content.isBlank() && toolRecords.isEmpty()) {
                    ThinkingPulseIndicator()
                }

                // 3. VFS Tool-Use cards
                if (toolRecords.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        toolRecords.forEach { record ->
                            ToolUseCard(
                                record = record,
                                onViewFile = onViewFile,
                                onDownloadFile = onDownloadFile
                            )
                        }
                    }
                }

                // 4. Main Markdown Message Body
                if (message.content.isNotBlank()) {
                    if (isUser) {
                        Text(
                            text = message.content,
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextPrimary
                        )
                    } else {
                        MarkdownContent(markdown = message.content)
                    }
                }

                // 5. Error banner if failed
                if (!message.errorMessage.isNullOrBlank()) {
                    Text(
                        text = message.errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFFCA5A5)
                    )
                }
            }
        }
    }
}

@Composable
private fun GlassMessageInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    isStreaming: Boolean,
    onSend: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xEB070709))
            .navigationBarsPadding()
            .imePadding()
    ) {
        HorizontalDivider(color = AmoledBorder, thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_message_input"),
                placeholder = {
                    Text(
                        text = stringResource(R.string.message_placeholder),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                },
                maxLines = 6,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = AmoledSurfaceElevated,
                    unfocusedContainerColor = AmoledSurfaceCard,
                    focusedBorderColor = ElectricBlue,
                    unfocusedBorderColor = AmoledBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = ElectricBlueLight
                )
            )

            if (isStreaming) {
                IconButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("cancel_generation_button"),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = CrimsonError,
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.StopCircle,
                        contentDescription = stringResource(R.string.cancel_generation)
                    )
                }
            } else {
                IconButton(
                    onClick = onSend,
                    enabled = value.isNotBlank(),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("send_message_button"),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = ElectricBlue,
                        contentColor = Color.White,
                        disabledContainerColor = AmoledSurfaceHigh,
                        disabledContentColor = TextSecondary
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.send_message)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyChatWorkspaceHero(
    settings: AppSettings,
    vaultState: VaultState,
    onQuickPrompt: (String) -> Unit,
    onOpenAuthSettings: () -> Unit
) {
    val starterPrompts = listOf(
        "Vytvoř ve VFS kompletní Kotlin modul '/src/network/ApiClient.kt' s retry logikou a unit testem v '/test/ApiClientTest.kt'.",
        "Navrhni produkční architekturu pro Android aplikaci a ulož technickou specifikaci do '/docs/ARCHITEKTURA.md'.",
        "Vypiš aktuální obsah VFS a vytvoř skript '/scripts/deploy.sh' pro automatizaci buildu."
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(ElectricBlue, SoftViolet)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Terminal,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(34.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Vertex Chat Studio",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Osobní AI architekt s izolovaným Virtuálním souborovým systémem (VFS), Thinking režimem a během na pozadí.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Auth status notice if Vertex Service Account is locked
        val needsUnlock = settings.authMode == AuthMode.VERTEX_SERVICE_ACCOUNT && vaultState !is VaultState.Unlocked
        AnimatedVisibility(visible = needsUnlock) {
            Surface(
                onClick = onOpenAuthSettings,
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1F1606),
                border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.6f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .testTag("unlock_vault_banner")
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = Color(0xFFF59E0B)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Trezor Service Accountu vyžaduje odemčení",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary
                        )
                        Text(
                            text = "Klepněte zde pro vložení/odemčení Service Account JSONu nebo přepnutí na Gemini API klíč.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            starterPrompts.forEachIndexed { idx, prompt ->
                AssistChip(
                    onClick = { onQuickPrompt(prompt) },
                    label = {
                        Text(
                            text = prompt,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (idx == 0) Icons.Default.Code else Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = ElectricBlueLight,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = AmoledSurfaceCard
                    ),
                    border = AssistChipDefaults.assistChipBorder(
                        enabled = true,
                        borderColor = AmoledBorder
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("starter_prompt_$idx")
                )
            }
        }
    }
}
