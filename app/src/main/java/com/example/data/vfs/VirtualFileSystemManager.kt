package com.example.data.vfs

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import com.example.data.local.VertexChatDao
import com.example.data.local.VfsNodeEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.UUID

data class VfsLineItem(
    val lineNumber: Int,
    val content: String
)

data class VfsReadWindowResult(
    val node: VfsNodeEntity,
    val lines: List<VfsLineItem>,
    val totalLines: Int,
    val truncated: Boolean
)

/**
 * Izolovaný Virtuální souborový systém (VFS) pro každý chat.
 * Metadata jsou uložena v Room databázi (VfsNodeEntity) a fyzický obsah v filesDir/vfs/{chatId}.
 * Veškerý zápis a čtení velkých souborů probíhá streamovaně přes FileOutputStream / BufferedReader.
 */
class VirtualFileSystemManager(
    private val context: Context,
    private val dao: VertexChatDao
) {
    private val vfsRootDir: File
        get() = File(context.filesDir, "vfs").apply { if (!exists()) mkdirs() }

    fun getChatRootDir(chatId: String): File {
        val safeChatId = chatId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return File(vfsRootDir, safeChatId).apply { if (!exists()) mkdirs() }
    }

    fun observeNodes(chatId: String): Flow<List<VfsNodeEntity>> = dao.observeVfsNodes(chatId)

    /**
     * Normalizuje virtuální cestu: vždy začíná '/', nikdy nekončí '/' (kromě kořene "/"),
     * brání directory traversalu ("..").
     */
    fun normalizeVirtualPath(rawPath: String): String {
        val cleaned = rawPath.trim().replace('\\', '/')
        val segments = cleaned.split('/').filter { it.isNotBlank() && it != "." }
        require(segments.none { it == ".." }) {
            "Nepovolená cesta obsahující '..': $rawPath"
        }
        return if (segments.isEmpty()) "/" else "/" + segments.joinToString("/")
    }

    private fun parentPathOf(normalizedPath: String): String {
        if (normalizedPath == "/") return "/"
        val lastSlash = normalizedPath.lastIndexOf('/')
        return if (lastSlash <= 0) "/" else normalizedPath.substring(0, lastSlash)
    }

    private fun nameOf(normalizedPath: String): String {
        if (normalizedPath == "/") return "/"
        return normalizedPath.substringAfterLast('/')
    }

    private fun resolvePhysicalFile(chatId: String, normalizedPath: String): File {
        val chatRoot = getChatRootDir(chatId).canonicalFile
        val rel = normalizedPath.removePrefix("/")
        val target = if (rel.isEmpty()) chatRoot else File(chatRoot, rel).canonicalFile
        require(target.path.startsWith(chatRoot.path)) {
            "Bezpečnostní chyba: Pokus o přístup mimo VFS pískoviště chatu."
        }
        return target
    }

    /**
     * Vytvoří složku (včetně případných chybějících nadřazených složek).
     */
    suspend fun createFolder(chatId: String, virtualPath: String): Result<VfsNodeEntity> =
        withContext(Dispatchers.IO) {
            runCatching {
                val normalized = normalizeVirtualPath(virtualPath)
                require(normalized != "/") { "Kořenová složka '/' již existuje." }

                ensureParentFoldersInternal(chatId, normalized)
                val existing = dao.getVfsNodeByPath(chatId, normalized)
                if (existing != null) {
                    require(existing.isDirectory) { "Na cestě $normalized již existuje soubor." }
                    return@runCatching existing
                }

                val dir = resolvePhysicalFile(chatId, normalized)
                if (!dir.exists() && !dir.mkdirs()) {
                    error("Nepodařilo se vytvořit fyzický adresář pro $normalized")
                }

                val now = System.currentTimeMillis()
                val entity = VfsNodeEntity(
                    id = UUID.randomUUID().toString(),
                    chatId = chatId,
                    name = nameOf(normalized),
                    virtualPath = normalized,
                    parentPath = parentPathOf(normalized),
                    physicalRelativePath = normalized.removePrefix("/"),
                    isDirectory = true,
                    sizeBytes = 0L,
                    lineCount = 0,
                    mimeType = "inode/directory",
                    createdAt = now,
                    updatedAt = now
                )
                dao.upsertVfsNode(entity)
                entity
            }
        }

    private suspend fun ensureParentFoldersInternal(chatId: String, normalizedTarget: String) {
        val segments = normalizedTarget.removePrefix("/").split('/').filter { it.isNotBlank() }
        if (segments.size <= 1) return

        var currentPath = ""
        for (i in 0 until segments.size - 1) {
            currentPath += "/${segments[i]}"
            val existing = dao.getVfsNodeByPath(chatId, currentPath)
            val dir = resolvePhysicalFile(chatId, currentPath)
            if (!dir.exists()) dir.mkdirs()
            if (existing == null) {
                val now = System.currentTimeMillis()
                dao.upsertVfsNode(
                    VfsNodeEntity(
                        id = UUID.randomUUID().toString(),
                        chatId = chatId,
                        name = segments[i],
                        virtualPath = currentPath,
                        parentPath = parentPathOf(currentPath),
                        physicalRelativePath = currentPath.removePrefix("/"),
                        isDirectory = true,
                        sizeBytes = 0L,
                        lineCount = 0,
                        mimeType = "inode/directory",
                        createdAt = now,
                        updatedAt = now
                    )
                )
            }
        }
    }

    /**
     * Streamovaný zápis nebo připojení (append) obsahu do souboru ve VFS přes FileOutputStream.
     * Nikdy nedrží celý soubor zbytečně v paměti při počítání řádků.
     */
    suspend fun writeFile(
        chatId: String,
        virtualPath: String,
        content: String,
        append: Boolean = false
    ): Result<VfsNodeEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val normalized = normalizeVirtualPath(virtualPath)
            require(normalized != "/") { "Nelze zapsat soubor do kořenové cesty '/'." }

            ensureParentFoldersInternal(chatId, normalized)
            val existing = dao.getVfsNodeByPath(chatId, normalized)
            if (existing != null && existing.isDirectory) {
                error("Cesta $normalized je složka, nikoliv soubor.")
            }

            val physicalFile = resolvePhysicalFile(chatId, normalized)
            physicalFile.parentFile?.let { if (!it.exists()) it.mkdirs() }

            FileOutputStream(physicalFile, append).use { fos ->
                BufferedWriter(OutputStreamWriter(fos, StandardCharsets.UTF_8), 32 * 1024).use { writer ->
                    val chunkSize = 16 * 1024
                    var offset = 0
                    while (offset < content.length) {
                        val end = (offset + chunkSize).coerceAtMost(content.length)
                        writer.write(content, offset, end - offset)
                        offset = end
                    }
                    writer.flush()
                }
            }

            val totalLines = countLinesStreamed(physicalFile)
            val now = System.currentTimeMillis()
            val node = VfsNodeEntity(
                id = existing?.id ?: UUID.randomUUID().toString(),
                chatId = chatId,
                name = nameOf(normalized),
                virtualPath = normalized,
                parentPath = parentPathOf(normalized),
                physicalRelativePath = normalized.removePrefix("/"),
                isDirectory = false,
                sizeBytes = physicalFile.length(),
                lineCount = totalLines,
                mimeType = detectMimeType(normalized),
                createdAt = existing?.createdAt ?: now,
                updatedAt = now
            )
            dao.upsertVfsNode(node)
            node
        }
    }

    /**
     * Streamované čtení souboru po řádcích pomocí BufferedReader.
     * Umožňuje číst výřez řádků (1-indexed startLine..endLine) bez načtení celého souboru do paměti.
     */
    suspend fun readFileLines(
        chatId: String,
        virtualPath: String,
        startLine: Int = 1,
        endLine: Int = 2000
    ): Result<VfsReadWindowResult> = withContext(Dispatchers.IO) {
        runCatching {
            val normalized = normalizeVirtualPath(virtualPath)
            val node = dao.getVfsNodeByPath(chatId, normalized)
                ?: error("Soubor $normalized ve VFS neexistuje.")
            require(!node.isDirectory) { "$normalized je složka, nikoliv soubor." }

            val physicalFile = resolvePhysicalFile(chatId, normalized)
            require(physicalFile.exists()) { "Fyzický soubor pro $normalized nebyl nalezen." }

            val safeStart = startLine.coerceAtLeast(1)
            val safeEnd = endLine.coerceAtLeast(safeStart)
            val lines = ArrayList<VfsLineItem>()
            var currentLineNo = 0
            var truncated = false

            FileInputStream(physicalFile).use { fis ->
                BufferedReader(InputStreamReader(fis, StandardCharsets.UTF_8), 32 * 1024).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        currentLineNo++
                        if (currentLineNo in safeStart..safeEnd) {
                            lines.add(VfsLineItem(lineNumber = currentLineNo, content = line ?: ""))
                        } else if (currentLineNo > safeEnd) {
                            truncated = true
                        }
                    }
                }
            }

            VfsReadWindowResult(
                node = node.copy(lineCount = currentLineNo, sizeBytes = physicalFile.length()),
                lines = lines,
                totalLines = currentLineNo,
                truncated = truncated
            )
        }
    }

    /**
     * Přečte text souboru pro AI nástroj nebo editor s bezpečnostním limitem řádků.
     */
    suspend fun readFileText(
        chatId: String,
        virtualPath: String,
        startLine: Int = 1,
        endLine: Int = 1500
    ): Result<String> = readFileLines(chatId, virtualPath, startLine, endLine).map { result ->
        buildString {
            result.lines.forEachIndexed { idx, item ->
                if (idx > 0) append('\n')
                append(item.content)
            }
        }
    }

    /**
     * Streamované vyhledávání výrazu v souboru po řádcích.
     */
    suspend fun searchInFile(
        chatId: String,
        virtualPath: String,
        query: String,
        maxMatches: Int = 200
    ): Result<List<VfsLineItem>> = withContext(Dispatchers.IO) {
        runCatching {
            if (query.isBlank()) return@runCatching emptyList()
            val normalized = normalizeVirtualPath(virtualPath)
            val physicalFile = resolvePhysicalFile(chatId, normalized)
            require(physicalFile.exists() && physicalFile.isFile) { "Soubor $normalized neexistuje." }

            val matches = ArrayList<VfsLineItem>()
            FileInputStream(physicalFile).use { fis ->
                BufferedReader(InputStreamReader(fis, StandardCharsets.UTF_8)).use { reader ->
                    var lineNo = 0
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        lineNo++
                        val text = line ?: continue
                        if (text.contains(query, ignoreCase = true)) {
                            matches.add(VfsLineItem(lineNo, text))
                            if (matches.size >= maxMatches) break
                        }
                    }
                }
            }
            matches
        }
    }

    /**
     * Vypíše soubory a složky v daném adresáři (nebo rekurzivně v celém VFS chatu).
     */
    suspend fun listFiles(
        chatId: String,
        folderPath: String = "/",
        recursive: Boolean = true
    ): Result<List<VfsNodeEntity>> = withContext(Dispatchers.IO) {
        runCatching {
            val normalized = normalizeVirtualPath(folderPath)
            val allNodes = dao.getVfsNodesOnce(chatId)
            if (normalized == "/") {
                if (recursive) allNodes else allNodes.filter { it.parentPath == "/" }
            } else {
                val prefix = "$normalized/"
                allNodes.filter { node ->
                    if (recursive) {
                        node.virtualPath.startsWith(prefix)
                    } else {
                        node.parentPath == normalized
                    }
                }
            }
        }
    }

    /**
     * Smaže soubor nebo celou složku včetně podstromu.
     */
    suspend fun deleteFile(chatId: String, virtualPath: String): Result<Int> =
        withContext(Dispatchers.IO) {
            runCatching {
                val normalized = normalizeVirtualPath(virtualPath)
                require(normalized != "/") { "Nelze smazat kořenovou složku '/'." }

                val subtree = dao.getVfsSubtree(chatId, normalized, "$normalized/")
                require(subtree.isNotEmpty()) { "Položka $normalized ve VFS neexistuje." }

                val physicalTarget = resolvePhysicalFile(chatId, normalized)
                if (physicalTarget.exists()) {
                    physicalTarget.deleteRecursively()
                }
                dao.deleteVfsSubtree(chatId, normalized, "$normalized/")
                subtree.size
            }
        }

    /**
     * Přesune soubor nebo složku na novou cestu v rámci VFS chatu.
     */
    suspend fun moveFile(
        chatId: String,
        sourcePath: String,
        destinationPath: String
    ): Result<VfsNodeEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val srcNorm = normalizeVirtualPath(sourcePath)
            val dstNorm = normalizeVirtualPath(destinationPath)
            require(srcNorm != "/" && dstNorm != "/") { "Nelze přesouvat kořenovou složku." }
            require(srcNorm != dstNorm) { "Zdrojová a cílová cesta jsou shodné." }
            require(!dstNorm.startsWith("$srcNorm/")) { "Nelze přesunout složku do sebe samé." }

            val rootNode = dao.getVfsNodeByPath(chatId, srcNorm)
                ?: error("Zdrojová položka $srcNorm neexistuje.")
            if (dao.getVfsNodeByPath(chatId, dstNorm) != null) {
                error("Na cílové cestě $dstNorm již položka existuje.")
            }

            ensureParentFoldersInternal(chatId, dstNorm)
            val srcFile = resolvePhysicalFile(chatId, srcNorm)
            val dstFile = resolvePhysicalFile(chatId, dstNorm)
            dstFile.parentFile?.let { if (!it.exists()) it.mkdirs() }

            if (srcFile.exists()) {
                val renamed = srcFile.renameTo(dstFile)
                if (!renamed) {
                    srcFile.copyRecursively(dstFile, overwrite = true)
                    srcFile.deleteRecursively()
                }
            }

            val subtree = dao.getVfsSubtree(chatId, srcNorm, "$srcNorm/")
            val now = System.currentTimeMillis()
            val updatedNodes = subtree.map { item ->
                val newVirtualPath = if (item.virtualPath == srcNorm) {
                    dstNorm
                } else {
                    dstNorm + item.virtualPath.removePrefix(srcNorm)
                }
                item.copy(
                    name = nameOf(newVirtualPath),
                    virtualPath = newVirtualPath,
                    parentPath = parentPathOf(newVirtualPath),
                    physicalRelativePath = newVirtualPath.removePrefix("/"),
                    updatedAt = now
                )
            }
            dao.deleteVfsSubtree(chatId, srcNorm, "$srcNorm/")
            dao.upsertVfsNodes(updatedNodes)
            updatedNodes.first { it.virtualPath == dstNorm }
        }
    }

    /**
     * Přejmenuje soubor nebo složku ve stejné nadřazené složce.
     */
    suspend fun renameFile(
        chatId: String,
        virtualPath: String,
        newName: String
    ): Result<VfsNodeEntity> {
        val cleanName = newName.trim().replace("/", "")
        if (cleanName.isBlank()) {
            return Result.failure(IllegalArgumentException("Nový název nesmí být prázdný."))
        }
        val srcNorm = normalizeVirtualPath(virtualPath)
        val parent = parentPathOf(srcNorm)
        val dstNorm = if (parent == "/") "/$cleanName" else "$parent/$cleanName"
        return moveFile(chatId, srcNorm, dstNorm)
    }

    /**
     * Exportuje soubor přes Storage Access Framework (SAF) do uživatelem zvoleného Uri.
     */
    suspend fun exportToUri(
        chatId: String,
        virtualPath: String,
        contentResolver: ContentResolver,
        targetUri: Uri
    ): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val normalized = normalizeVirtualPath(virtualPath)
            val physicalFile = resolvePhysicalFile(chatId, normalized)
            require(physicalFile.exists() && physicalFile.isFile) { "Soubor $normalized neexistuje." }

            var bytesCopied = 0L
            FileInputStream(physicalFile).use { input ->
                contentResolver.openOutputStream(targetUri)?.use { output ->
                    bytesCopied = input.copyTo(output, bufferSize = 32 * 1024)
                    output.flush()
                } ?: error("Nelze otevřít výstupní proud pro export.")
            }
            bytesCopied
        }
    }

    /**
     * Vrátí fyzický soubor připravený pro sdílení přes FileProvider.
     */
    suspend fun getPhysicalFileForSharing(chatId: String, virtualPath: String): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val normalized = normalizeVirtualPath(virtualPath)
                val file = resolvePhysicalFile(chatId, normalized)
                require(file.exists() && file.isFile) { "Soubor $normalized neexistuje." }
                file
            }
        }

    suspend fun deleteChatVfsFolder(chatId: String) = withContext(Dispatchers.IO) {
        runCatching {
            getChatRootDir(chatId).deleteRecursively()
        }
    }

    private fun countLinesStreamed(file: File): Int {
        if (!file.exists() || !file.isFile || file.length() == 0L) return 0
        var count = 0
        FileInputStream(file).use { fis ->
            BufferedReader(InputStreamReader(fis, StandardCharsets.UTF_8), 32 * 1024).use { reader ->
                while (reader.readLine() != null) {
                    count++
                }
            }
        }
        return count
    }

    private fun detectMimeType(virtualPath: String): String {
        val ext = virtualPath.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "kt", "kts" -> "text/x-kotlin"
            "java" -> "text/x-java"
            "py" -> "text/x-python"
            "js", "mjs" -> "application/javascript"
            "ts", "tsx" -> "application/typescript"
            "json" -> "application/json"
            "xml" -> "application/xml"
            "html", "htm" -> "text/html"
            "css" -> "text/css"
            "md", "markdown" -> "text/markdown"
            "sh", "bash" -> "application/x-sh"
            "yml", "yaml" -> "application/x-yaml"
            "sql" -> "application/sql"
            "csv" -> "text/csv"
            else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "text/plain"
        }
    }
}
