package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.ChatEntity
import com.example.ui.AppDestination
import com.example.ui.VertexChatViewModel
import com.example.ui.chat.ChatScreen
import com.example.ui.settings.AuthSettingsScreen
import com.example.ui.theme.AmoledBlack
import com.example.ui.theme.AmoledBorder
import com.example.ui.theme.AmoledSurfaceCard
import com.example.ui.theme.AmoledSurfaceElevated
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricBlueLight
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SoftViolet
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VertexChatTheme
import com.example.ui.vfs.VfsExplorerScreen
import com.example.ui.vfs.VfsFileDetailModalSheet
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: VertexChatViewModel by viewModels { VertexChatViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VertexChatTheme {
                VertexChatRootApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun VertexChatRootApp(
    viewModel: VertexChatViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val snackbarHostState = remember { SnackbarHostState() }

    val destination by viewModel.currentDestination.collectAsStateWithLifecycle()
    val chats by viewModel.chatsState.collectAsStateWithLifecycle()
    val activeChat by viewModel.activeChatState.collectAsStateWithLifecycle()
    val messages by viewModel.activeMessagesState.collectAsStateWithLifecycle()
    val models by viewModel.modelsState.collectAsStateWithLifecycle()
    val vfsNodes by viewModel.activeVfsNodesState.collectAsStateWithLifecycle()
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    val vaultState by viewModel.vaultState.collectAsStateWithLifecycle()
    val isRefreshingModels by viewModel.isRefreshingModels.collectAsStateWithLifecycle()
    val openedFileState by viewModel.openedFileState.collectAsStateWithLifecycle()
    val pendingExportNode by viewModel.pendingExportNode.collectAsStateWithLifecycle()

    // Android 13+ notification permission launcher for Foreground Worker notifications
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // SAF CreateDocument launcher for exporting VFS files
    val safExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        if (uri != null) {
            viewModel.completeSafExport(context.contentResolver, uri)
        } else {
            viewModel.clearPendingExport()
        }
    }

    LaunchedEffect(pendingExportNode) {
        val node = pendingExportNode
        if (node != null) {
            safExportLauncher.launch(node.name)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.snackbarEvents.collectLatest { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // Back navigation handler for sub-screens
    BackHandler(enabled = destination != AppDestination.CHAT) {
        viewModel.navigateTo(AppDestination.CHAT)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            VertexNavigationDrawerSheet(
                chats = chats,
                activeChatId = activeChat?.id,
                currentDestination = destination,
                vfsFileCount = vfsNodes.count { !it.isDirectory },
                onNewChat = {
                    viewModel.createNewChat()
                    scope.launch { drawerState.close() }
                },
                onSelectChat = { chatId ->
                    viewModel.selectChat(chatId)
                    scope.launch { drawerState.close() }
                },
                onDeleteChat = { chatId ->
                    viewModel.deleteChat(chatId)
                },
                onNavigate = { dest ->
                    viewModel.navigateTo(dest)
                    scope.launch { drawerState.close() }
                }
            )
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AmoledBlack)
        ) {
            AnimatedContent(
                targetState = destination,
                label = "main_screen_transition"
            ) { screen ->
                when (screen) {
                    AppDestination.CHAT -> {
                        ChatScreen(
                            activeChat = activeChat,
                            messages = messages,
                            models = models,
                            vfsNodeCount = vfsNodes.count { !it.isDirectory },
                            settings = settings,
                            vaultState = vaultState,
                            isRefreshingModels = isRefreshingModels,
                            onOpenDrawer = { scope.launch { drawerState.open() } },
                            onSelectModel = viewModel::selectModelForActiveChat,
                            onRefreshModels = viewModel::refreshModelsFromApi,
                            onSendMessage = viewModel::sendMessage,
                            onCancelGeneration = viewModel::cancelActiveGeneration,
                            onOpenVfsExplorer = { viewModel.navigateTo(AppDestination.VFS_EXPLORER) },
                            onOpenVfsFile = viewModel::openVfsFileByPath,
                            onDownloadVfsFile = viewModel::requestSafExportByPath,
                            onOpenAuthSettings = { viewModel.navigateTo(AppDestination.SETTINGS) },
                            onMessageCopied = {
                                scope.launch {
                                    snackbarHostState.showSnackbar(context.getString(R.string.copied_to_clipboard))
                                }
                            }
                        )
                    }

                    AppDestination.VFS_EXPLORER -> {
                        VfsExplorerScreen(
                            chatTitle = activeChat?.title ?: "Konverzace",
                            nodes = vfsNodes,
                            onOpenDrawer = { scope.launch { drawerState.open() } },
                            onBackToChat = { viewModel.navigateTo(AppDestination.CHAT) },
                            onOpenFile = viewModel::openVfsFileByPath,
                            onCreateFile = viewModel::createVfsFileFromUi,
                            onCreateFolder = viewModel::createVfsFolderFromUi,
                            onRenameNode = viewModel::renameVfsNodeFromUi,
                            onDeleteNode = viewModel::deleteVfsNodeFromUi,
                            onDownloadFile = viewModel::requestSafExportByPath,
                            onShareFile = viewModel::shareVfsFile
                        )
                    }

                    AppDestination.SETTINGS -> {
                        AuthSettingsScreen(
                            settings = settings,
                            vaultState = vaultState,
                            onOpenDrawer = { scope.launch { drawerState.open() } },
                            onBackToChat = { viewModel.navigateTo(AppDestination.CHAT) },
                            onSelectAuthMode = viewModel::setAuthMode,
                            onSaveAndEncryptServiceAccount = viewModel::saveAndEncryptServiceAccount,
                            onUnlockVault = viewModel::unlockServiceAccountVault,
                            onLockVault = viewModel::lockServiceAccountVault,
                            onClearVault = viewModel::clearServiceAccountVault,
                            onSaveVertexConfig = viewModel::saveVertexProjectSettings,
                            onSaveCustomGeminiKey = viewModel::saveCustomGeminiKey,
                            onSaveGenerationParams = viewModel::saveGenerationParameters
                        )
                    }
                }
            }

            // VFS File Viewer & Editor Modal Bottom Sheet (accessible from both Chat tool cards & VFS screen)
            openedFileState?.let { fileState ->
                VfsFileDetailModalSheet(
                    fileState = fileState,
                    onDismiss = viewModel::closeOpenedVfsFile,
                    onToggleEdit = viewModel::toggleFileEditMode,
                    onSaveContent = viewModel::saveEditedVfsFile,
                    onSearchQueryChange = viewModel::updateOpenedFileSearchQuery,
                    onDownloadFile = viewModel::requestSafExportByPath,
                    onShareFile = viewModel::shareVfsFile
                )
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 78.dp)
            )
        }
    }
}

@Composable
private fun VertexNavigationDrawerSheet(
    chats: List<ChatEntity>,
    activeChatId: String?,
    currentDestination: AppDestination,
    vfsFileCount: Int,
    onNewChat: () -> Unit,
    onSelectChat: (String) -> Unit,
    onDeleteChat: (String) -> Unit,
    onNavigate: (AppDestination) -> Unit
) {
    ModalDrawerSheet(
        drawerContainerColor = AmoledSurfaceCard,
        drawerContentColor = TextPrimary,
        modifier = Modifier
            .width(310.dp)
            .fillMaxHeight()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            // Brand header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Brush.linearGradient(listOf(ElectricBlue, SoftViolet))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column {
                    Text(
                        text = "Vertex Chat",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = "AMOLED • VFS • OAuth2",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 10.5.sp,
                        color = ElectricBlueLight
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = onNewChat,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricBlue,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("drawer_new_chat_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Nová konverzace", fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "KONVERZACE (${chats.size})",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(items = chats, key = { it.id }) { chat ->
                    val isSelected = chat.id == activeChatId && currentDestination == AppDestination.CHAT
                    Surface(
                        onClick = { onSelectChat(chat.id) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) Color(0xFF172554) else Color.Transparent,
                        border = if (isSelected) BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.5f)) else null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChatBubbleOutline,
                                contentDescription = null,
                                tint = if (isSelected) ElectricBlueLight else TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = chat.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = chat.selectedModelId,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 10.sp,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = { onDeleteChat(chat.id) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Smazat chat",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = AmoledBorder, modifier = Modifier.padding(vertical = 8.dp))

            NavigationDrawerItem(
                label = { Text("Virtuální soubory (VFS)") },
                badge = {
                    if (vfsFileCount > 0) {
                        Text(
                            text = vfsFileCount.toString(),
                            fontFamily = JetBrainsMonoFontFamily,
                            color = ElectricBlueLight
                        )
                    }
                },
                icon = {
                    Icon(
                        imageVector = Icons.Default.FolderSpecial,
                        contentDescription = null
                    )
                },
                selected = currentDestination == AppDestination.VFS_EXPLORER,
                onClick = { onNavigate(AppDestination.VFS_EXPLORER) },
                colors = NavigationDrawerItemDefaults.colors(
                    selectedContainerColor = AmoledSurfaceElevated,
                    unselectedContainerColor = Color.Transparent,
                    selectedTextColor = ElectricBlueLight,
                    unselectedTextColor = TextPrimary,
                    selectedIconColor = ElectricBlueLight,
                    unselectedIconColor = TextSecondary
                ),
                modifier = Modifier.testTag("drawer_nav_vfs")
            )

            NavigationDrawerItem(
                label = { Text("Autentizace & Trezor") },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null
                    )
                },
                selected = currentDestination == AppDestination.SETTINGS,
                onClick = { onNavigate(AppDestination.SETTINGS) },
                colors = NavigationDrawerItemDefaults.colors(
                    selectedContainerColor = AmoledSurfaceElevated,
                    unselectedContainerColor = Color.Transparent,
                    selectedTextColor = ElectricBlueLight,
                    unselectedTextColor = TextPrimary,
                    selectedIconColor = ElectricBlueLight,
                    unselectedIconColor = TextSecondary
                ),
                modifier = Modifier.testTag("drawer_nav_settings")
            )
        }
    }
}
