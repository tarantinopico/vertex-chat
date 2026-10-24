package com.example.ui.vfs

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ai.VfsAiTools
import com.example.data.local.VfsNodeEntity
import com.example.ui.OpenVfsFileState
import com.example.ui.theme.AmoledBlack
import com.example.ui.theme.AmoledBorder
import com.example.ui.theme.AmoledSurfaceCard
import com.example.ui.theme.AmoledSurfaceElevated
import com.example.ui.theme.AmoledSurfaceLowest
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricBlueLight
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SoftViolet
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun VfsExplorerScreen(
    chatTitle: String,
    nodes: List<VfsNodeEntity>,
    onOpenDrawer: () -> Unit,
    onBackToChat: () -> Unit,
    onOpenFile: (String) -> Unit,
    onCreateFile: (String, String) -> Unit,
    onCreateFolder: (String) -> Unit,
    onRenameNode: (String, String) -> Unit,
    onDeleteNode: (String) -> Unit,
    onDownloadFile: (String) -> Unit,
    onShareFile: (String) -> Unit
) {
    val expandedFolders = remember { mutableStateMapOf<String, Boolean>() }
    var filterQuery by rememberSaveable { mutableStateOf("") }

    var showCreateFileDialog by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var renameTargetNode by remember { mutableStateOf<VfsNodeEntity?>(null) }

    val visibleTreeNodes = remember(nodes, expandedFolders.toMap(), filterQuery) {
        if (filterQuery.isNotBlank()) {
            nodes.filter { it.virtualPath.contains(filterQuery, ignoreCase = true) }
        } else {
            nodes.filter { node ->
                isNodeVisibleInTree(node, expandedFolders)
            }
        }
    }

    Scaffold(
        containerColor = AmoledBlack,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xF2080C17))
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onOpenDrawer) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Otevřít menu",
                                tint = TextPrimary
                            )
                        }
                        Column {
                            Text(
                                text = "Virtuální souborový systém",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = "Chat: $chatTitle • ${nodes.count { !it.isDirectory }} souborů",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 11.sp,
                                color = ElectricBlueLight
                            )
                        }
                    }

                    TextButton(
                        onClick = onBackToChat,
                        modifier = Modifier.testTag("vfs_back_to_chat_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = ElectricBlueLight,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Do chatu", color = ElectricBlueLight)
                    }
                }
                HorizontalDivider(color = ElectricBlue.copy(alpha = 0.22f))
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AmoledBlack)
                .padding(innerPadding)
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Search & Action Toolbar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = filterQuery,
                    onValueChange = { filterQuery = it },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("vfs_search_input"),
                    placeholder = {
                        Text(
                            text = "Hledat cestu ve VFS…",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = AmoledSurfaceElevated,
                        unfocusedContainerColor = AmoledSurfaceCard,
                        focusedBorderColor = ElectricBlue,
                        unfocusedBorderColor = AmoledBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                FilledTonalButton(
                    onClick = { showCreateFolderDialog = true },
                    modifier = Modifier.testTag("vfs_new_folder_btn"),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = AmoledSurfaceElevated,
                        contentColor = ElectricBlueLight
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CreateNewFolder,
                        contentDescription = "Nová složka",
                        modifier = Modifier.size(18.dp)
                    )
                }

                Button(
                    onClick = { showCreateFileDialog = true },
                    modifier = Modifier.testTag("vfs_new_file_btn"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricBlue,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Nový soubor",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Soubor")
                }
            }

            if (visibleTreeNodes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = ElectricBlueLight,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = stringResource(R.string.vfs_empty_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = stringResource(R.string.vfs_empty_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("vfs_tree_list"),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(items = visibleTreeNodes, key = { it.id }) { node ->
                        val depth = (node.virtualPath.count { it == '/' } - 1).coerceAtLeast(0)
                        val isExpanded = expandedFolders[node.virtualPath] ?: true

                        VfsTreeNodeCard(
                            node = node,
                            depth = depth,
                            isExpanded = isExpanded,
                            onClick = {
                                if (node.isDirectory) {
                                    expandedFolders[node.virtualPath] = !isExpanded
                                } else {
                                    onOpenFile(node.virtualPath)
                                }
                            },
                            onRename = { renameTargetNode = node },
                            onDelete = { onDeleteNode(node.virtualPath) },
                            onDownload = { onDownloadFile(node.virtualPath) },
                            onShare = { onShareFile(node.virtualPath) }
                        )
                    }
                }
            }
        }
    }

    if (showCreateFolderDialog) {
        CreateFolderDialog(
            onDismiss = { showCreateFolderDialog = false },
            onConfirm = { folderPath ->
                showCreateFolderDialog = false
                onCreateFolder(folderPath)
            }
        )
    }

    if (showCreateFileDialog) {
        CreateFileDialog(
            onDismiss = { showCreateFileDialog = false },
            onConfirm = { filePath, content ->
                showCreateFileDialog = false
                onCreateFile(filePath, content)
            }
        )
    }

    renameTargetNode?.let { target ->
        RenameNodeDialog(
            currentName = target.name,
            virtualPath = target.virtualPath,
            onDismiss = { renameTargetNode = null },
            onConfirm = { newName ->
                renameTargetNode = null
                onRenameNode(target.virtualPath, newName)
            }
        )
    }
}

private fun isNodeVisibleInTree(
    node: VfsNodeEntity,
    expandedFolders: Map<String, Boolean>
): Boolean {
    var parent = node.parentPath
    while (parent != "/" && parent.isNotBlank()) {
        val isExpanded = expandedFolders[parent] ?: true
        if (!isExpanded) return false
        val lastSlash = parent.lastIndexOf('/')
        parent = if (lastSlash <= 0) "/" else parent.substring(0, lastSlash)
    }
    return true
}

@Composable
private fun VfsTreeNodeCard(
    node: VfsNodeEntity,
    depth: Int,
    isExpanded: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onDownload: () -> Unit,
    onShare: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (depth * 14).coerceAtMost(70).dp)
            .animateContentSize()
            .clickable(onClick = onClick)
            .testTag("vfs_node_${node.virtualPath}"),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (node.isDirectory) Color(0xFF0B1324) else AmoledSurfaceCard
        ),
        border = BorderStroke(
            1.dp,
            if (node.isDirectory) ElectricBlue.copy(alpha = 0.35f) else AmoledBorder
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = when {
                        node.isDirectory && isExpanded -> Icons.Default.FolderOpen
                        node.isDirectory -> Icons.Default.Folder
                        node.virtualPath.endsWith(".kt") || node.virtualPath.endsWith(".py") ||
                            node.virtualPath.endsWith(".json") -> Icons.Default.Code
                        else -> Icons.Default.Description
                    },
                    contentDescription = null,
                    tint = if (node.isDirectory) ElectricBlueLight else SoftViolet,
                    modifier = Modifier.size(22.dp)
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = node.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (node.isDirectory) {
                            node.virtualPath
                        } else {
                            "${node.virtualPath} • ${node.lineCount} řádků • ${VfsAiTools.formatBytes(node.sizeBytes)}"
                        },
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 11.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!node.isDirectory) {
                    IconButton(onClick = onDownload, modifier = Modifier.size(34.dp)) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Stáhnout soubor",
                            tint = ElectricBlueLight,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onShare, modifier = Modifier.size(34.dp)) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Sdílet soubor",
                            tint = TextSecondary,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
                IconButton(onClick = onRename, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.Default.DriveFileRenameOutline,
                        contentDescription = "Přejmenovat",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Smazat",
                        tint = CrimsonError.copy(alpha = 0.85f),
                        modifier = Modifier.size(18.dp)
                    )
                }
                if (node.isDirectory) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = ElectricBlueLight
                    )
                }
            }
        }
    }
}

/**
 * Modal Sheet pro virtualizovaný detail souboru (LazyColumn s čísly řádků), vyhledávání v obsahu a ruční úpravu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VfsFileDetailModalSheet(
    fileState: OpenVfsFileState,
    onDismiss: () -> Unit,
    onToggleEdit: (Boolean) -> Unit,
    onSaveContent: (String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onDownloadFile: (String) -> Unit,
    onShareFile: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var editedText by remember(fileState.node.virtualPath, fileState.rawEditableText) {
        mutableStateOf(fileState.rawEditableText)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AmoledSurfaceCard,
        contentColor = TextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.90f)
                .padding(horizontal = 14.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = fileState.node.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = "${fileState.node.virtualPath} • ${fileState.totalLines} řádků • ${VfsAiTools.formatBytes(fileState.node.sizeBytes)}",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 11.sp,
                        color = ElectricBlueLight
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = { onDownloadFile(fileState.node.virtualPath) }) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Stáhnout přes SAF",
                            tint = ElectricBlueLight
                        )
                    }
                    IconButton(onClick = { onShareFile(fileState.node.virtualPath) }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Sdílet přes FileProvider",
                            tint = TextPrimary
                        )
                    }
                    IconButton(onClick = { onToggleEdit(!fileState.isEditing) }) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Přepnout režim úprav",
                            tint = if (fileState.isEditing) EmeraldSuccess else TextPrimary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Zavřít",
                            tint = TextSecondary
                        )
                    }
                }
            }

            HorizontalDivider(color = AmoledBorder)

            if (fileState.isEditing) {
                // Manual editor mode
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Režim ruční úpravy souboru",
                        style = MaterialTheme.typography.labelLarge,
                        color = EmeraldSuccess
                    )
                    Button(
                        onClick = { onSaveContent(editedText) },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess),
                        modifier = Modifier.testTag("vfs_save_edited_file_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Uložit do VFS", color = AmoledBlack, fontWeight = FontWeight.Bold)
                    }
                }

                OutlinedTextField(
                    value = editedText,
                    onValueChange = { editedText = it },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 12.dp)
                        .testTag("vfs_file_editor_input"),
                    textStyle = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = AmoledSurfaceLowest,
                        unfocusedContainerColor = AmoledSurfaceLowest,
                        focusedBorderColor = ElectricBlue,
                        unfocusedBorderColor = AmoledBorder
                    )
                )
            } else {
                // Virtualized Line-by-Line Reader + Search
                OutlinedTextField(
                    value = fileState.searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            text = "Hledat řádek v souboru…",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )

                val displayLines = if (fileState.searchQuery.isNotBlank()) {
                    fileState.searchMatches
                } else {
                    fileState.lines
                }

                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 12.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = AmoledSurfaceLowest,
                    border = BorderStroke(1.dp, AmoledBorder)
                ) {
                    val horizontalScroll = rememberScrollState()
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .horizontalScroll(horizontalScroll)
                            .testTag("vfs_virtualized_lines_list"),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(items = displayLines, key = { it.lineNumber }) { lineItem ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 1.5.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = lineItem.lineNumber.toString().padStart(4, ' '),
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 11.5.sp,
                                    color = TextSecondary.copy(alpha = 0.65f),
                                    modifier = Modifier
                                        .width(44.dp)
                                        .padding(end = 8.dp)
                                )
                                Text(
                                    text = lineItem.content,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 12.5.sp,
                                    color = Color(0xFFE2E8F0)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateFolderDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var folderPath by remember { mutableStateOf("/src/") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AmoledSurfaceElevated,
        title = { Text("Nová složka ve VFS", color = TextPrimary) },
        text = {
            OutlinedTextField(
                value = folderPath,
                onValueChange = { folderPath = it },
                label = { Text("Cesta složky (např. /src/main)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { if (folderPath.isNotBlank()) onConfirm(folderPath) },
                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
            ) {
                Text("Vytvořit")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Zrušit")
            }
        }
    )
}

@Composable
private fun CreateFileDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var filePath by remember { mutableStateOf("/src/Main.kt") }
    var content by remember { mutableStateOf("package com.example\n\nfun main() {\n    println(\"Hello from Vertex Chat VFS!\")\n}\n") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AmoledSurfaceElevated,
        title = { Text("Nový soubor ve VFS", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = filePath,
                    onValueChange = { filePath = it },
                    label = { Text("Virtuální cesta (např. /src/Main.kt)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Výchozí obsah souboru") },
                    maxLines = 8,
                    textStyle = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (filePath.isNotBlank()) onConfirm(filePath, content) },
                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
            ) {
                Text("Uložit soubor")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Zrušit")
            }
        }
    )
}

@Composable
private fun RenameNodeDialog(
    currentName: String,
    virtualPath: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var newName by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AmoledSurfaceElevated,
        title = { Text("Přejmenovat položku", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = virtualPath,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 11.sp,
                    color = ElectricBlueLight
                )
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Nový název") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (newName.isNotBlank()) onConfirm(newName) },
                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
            ) {
                Text("Přejmenovat")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Zrušit")
            }
        }
    )
}
