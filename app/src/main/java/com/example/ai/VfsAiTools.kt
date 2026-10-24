package com.example.ai

import com.example.data.local.ToolCallRecord
import com.example.data.vfs.VirtualFileSystemManager
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.util.UUID

data class VfsToolExecutionOutcome(
    val toolRecord: ToolCallRecord,
    val functionResponseJson: JsonObject
)

/**
 * Definice FunctionDeclarations pro Vertex AI / Gemini a lokální exekutor nad VirtualFileSystemManager.
 */
class VfsAiTools(
    private val vfsManager: VirtualFileSystemManager
) {

    fun buildToolsJsonArray(): JsonArray = buildJsonArray {
        addJsonObject {
            putJsonArray("functionDeclarations") {
                // 1. createFolder
                addJsonObject {
                    put("name", "createFolder")
                    put("description", "Vytvoří novou složku (včetně nadřazených složek) ve Virtuálním souborovém systému (VFS) aktuálního chatu.")
                    putJsonObject("parameters") {
                        put("type", "OBJECT")
                        putJsonObject("properties") {
                            putJsonObject("path") {
                                put("type", "STRING")
                                put("description", "Cesta ke složce začínající '/', např. '/src/main/kotlin'")
                            }
                        }
                        putJsonArray("required") { add("path") }
                    }
                }

                // 2. writeFile
                addJsonObject {
                    put("name", "writeFile")
                    put("description", "Zapíše nebo připojí (append) textový obsah do souboru ve VFS aktuálního chatu. Automaticky vytvoří nadřazené složky.")
                    putJsonObject("parameters") {
                        put("type", "OBJECT")
                        putJsonObject("properties") {
                            putJsonObject("path") {
                                put("type", "STRING")
                                put("description", "Virtuální cesta k souboru, např. '/src/App.kt'")
                            }
                            putJsonObject("content") {
                                put("type", "STRING")
                                put("description", "Kompletní textový obsah k zapsání do souboru")
                            }
                            putJsonObject("append") {
                                put("type", "BOOLEAN")
                                put("description", "Pokud je true, připojí obsah na konec existujícího souboru; výchozí je false (přepsat).")
                            }
                        }
                        putJsonArray("required") {
                            add("path")
                            add("content")
                        }
                    }
                }

                // 3. readFile
                addJsonObject {
                    put("name", "readFile")
                    put("description", "Přečte obsah souboru z VFS po řádcích. U velkých souborů lze specifikovat startLine a endLine.")
                    putJsonObject("parameters") {
                        put("type", "OBJECT")
                        putJsonObject("properties") {
                            putJsonObject("path") {
                                put("type", "STRING")
                                put("description", "Virtuální cesta k souboru, např. '/src/App.kt'")
                            }
                            putJsonObject("startLine") {
                                put("type", "INTEGER")
                                put("description", "Číslo prvního řádku (1-indexované, výchozí 1)")
                            }
                            putJsonObject("endLine") {
                                put("type", "INTEGER")
                                put("description", "Číslo posledního řádku (výchozí 500)")
                            }
                        }
                        putJsonArray("required") { add("path") }
                    }
                }

                // 4. listFiles
                addJsonObject {
                    put("name", "listFiles")
                    put("description", "Vypíše seznam všech souborů a složek ve VFS aktuálního chatu.")
                    putJsonObject("parameters") {
                        put("type", "OBJECT")
                        putJsonObject("properties") {
                            putJsonObject("folderPath") {
                                put("type", "STRING")
                                put("description", "Cesta ke složce, výchozí '/'")
                            }
                            putJsonObject("recursive") {
                                put("type", "BOOLEAN")
                                put("description", "Zda vypsat i podsložky rekurzivně (výchozí true)")
                            }
                        }
                    }
                }

                // 5. deleteFile
                addJsonObject {
                    put("name", "deleteFile")
                    put("description", "Smaže soubor nebo složku (včetně obsahu) z VFS aktuálního chatu.")
                    putJsonObject("parameters") {
                        put("type", "OBJECT")
                        putJsonObject("properties") {
                            putJsonObject("path") {
                                put("type", "STRING")
                                put("description", "Virtuální cesta k souboru nebo složce ke smazání")
                            }
                        }
                        putJsonArray("required") { add("path") }
                    }
                }

                // 6. moveFile
                addJsonObject {
                    put("name", "moveFile")
                    put("description", "Přesune soubor nebo složku ze zdrojové cesty na cílovou cestu ve VFS.")
                    putJsonObject("parameters") {
                        put("type", "OBJECT")
                        putJsonObject("properties") {
                            putJsonObject("sourcePath") {
                                put("type", "STRING")
                                put("description", "Původní virtuální cesta")
                            }
                            putJsonObject("destinationPath") {
                                put("type", "STRING")
                                put("description", "Nová cílová virtuální cesta")
                            }
                        }
                        putJsonArray("required") {
                            add("sourcePath")
                            add("destinationPath")
                        }
                    }
                }

                // 7. renameFile
                addJsonObject {
                    put("name", "renameFile")
                    put("description", "Přejmenuje soubor nebo složku ve VFS.")
                    putJsonObject("parameters") {
                        put("type", "OBJECT")
                        putJsonObject("properties") {
                            putJsonObject("path") {
                                put("type", "STRING")
                                put("description", "Virtuální cesta k položce")
                            }
                            putJsonObject("newName") {
                                put("type", "STRING")
                                put("description", "Nový název souboru nebo složky (bez lomítek)")
                            }
                        }
                        putJsonArray("required") {
                            add("path")
                            add("newName")
                        }
                    }
                }
            }
        }
    }

    fun buildRunningRecord(toolName: String, args: JsonObject): ToolCallRecord {
        val path = args["path"]?.jsonPrimitive?.content
            ?: args["sourcePath"]?.jsonPrimitive?.content
            ?: args["folderPath"]?.jsonPrimitive?.content
            ?: "/"
        val title = when (toolName) {
            "createFolder" -> "Vytvářím složku $path…"
            "writeFile" -> {
                val append = args["append"]?.jsonPrimitive?.booleanOrNull == true
                if (append) "Připojuji obsah do $path…" else "Vytvářím soubor $path…"
            }
            "readFile" -> "Čtu soubor $path…"
            "listFiles" -> "Procházím strom VFS ($path)…"
            "deleteFile" -> "Mažu položku $path…"
            "moveFile" -> "Přesouvám $path…"
            "renameFile" -> "Přejmenovávám $path…"
            else -> "Spouštím nástroj $toolName…"
        }
        return ToolCallRecord(
            id = UUID.randomUUID().toString(),
            toolName = toolName,
            targetPath = path,
            actionTitle = title,
            status = "RUNNING"
        )
    }

    suspend fun executeToolCall(
        chatId: String,
        runningRecord: ToolCallRecord,
        args: JsonObject
    ): VfsToolExecutionOutcome {
        val toolName = runningRecord.toolName
        return when (toolName) {
            "createFolder" -> {
                val path = args["path"]?.jsonPrimitive?.content ?: "/"
                val result = vfsManager.createFolder(chatId, path)
                result.fold(
                    onSuccess = { node ->
                        VfsToolExecutionOutcome(
                            toolRecord = runningRecord.copy(
                                targetPath = node.virtualPath,
                                actionTitle = "Složka ${node.virtualPath} vytvořena",
                                status = "SUCCESS",
                                details = "Adresář připraven ve VFS"
                            ),
                            functionResponseJson = buildResponse(toolName) {
                                put("success", true)
                                put("path", node.virtualPath)
                                put("message", "Folder created at ${node.virtualPath}")
                            }
                        )
                    },
                    onFailure = { err -> errorOutcome(runningRecord, err.message ?: "Chyba createFolder") }
                )
            }

            "writeFile" -> {
                val path = args["path"]?.jsonPrimitive?.content ?: "/untitled.txt"
                val content = args["content"]?.jsonPrimitive?.content ?: ""
                val append = args["append"]?.jsonPrimitive?.booleanOrNull ?: false
                val result = vfsManager.writeFile(chatId, path, content, append)
                result.fold(
                    onSuccess = { node ->
                        VfsToolExecutionOutcome(
                            toolRecord = runningRecord.copy(
                                targetPath = node.virtualPath,
                                actionTitle = if (append) "Doplněn soubor ${node.name}" else "Uložen soubor ${node.name}",
                                status = "SUCCESS",
                                details = "${node.lineCount} řádků • ${formatBytes(node.sizeBytes)}",
                                sizeBytes = node.sizeBytes
                            ),
                            functionResponseJson = buildResponse(toolName) {
                                put("success", true)
                                put("path", node.virtualPath)
                                put("sizeBytes", node.sizeBytes)
                                put("lineCount", node.lineCount)
                                put("mimeType", node.mimeType)
                            }
                        )
                    },
                    onFailure = { err -> errorOutcome(runningRecord, err.message ?: "Chyba writeFile") }
                )
            }

            "readFile" -> {
                val path = args["path"]?.jsonPrimitive?.content ?: "/"
                val startLine = args["startLine"]?.jsonPrimitive?.intOrNull ?: 1
                val endLine = args["endLine"]?.jsonPrimitive?.intOrNull ?: 500
                val result = vfsManager.readFileLines(chatId, path, startLine, endLine)
                result.fold(
                    onSuccess = { win ->
                        val text = win.lines.joinToString("\n") { "${it.lineNumber}: ${it.content}" }
                        VfsToolExecutionOutcome(
                            toolRecord = runningRecord.copy(
                                targetPath = win.node.virtualPath,
                                actionTitle = "Přečten soubor ${win.node.name}",
                                status = "SUCCESS",
                                details = "Řádky $startLine–${(startLine + win.lines.size - 1).coerceAtLeast(startLine)} z ${win.totalLines}",
                                sizeBytes = win.node.sizeBytes
                            ),
                            functionResponseJson = buildResponse(toolName) {
                                put("success", true)
                                put("path", win.node.virtualPath)
                                put("totalLines", win.totalLines)
                                put("truncated", win.truncated)
                                put("content", text)
                            }
                        )
                    },
                    onFailure = { err -> errorOutcome(runningRecord, err.message ?: "Chyba readFile") }
                )
            }

            "listFiles" -> {
                val folderPath = args["folderPath"]?.jsonPrimitive?.content ?: "/"
                val recursive = args["recursive"]?.jsonPrimitive?.booleanOrNull ?: true
                val result = vfsManager.listFiles(chatId, folderPath, recursive)
                result.fold(
                    onSuccess = { nodes ->
                        VfsToolExecutionOutcome(
                            toolRecord = runningRecord.copy(
                                targetPath = folderPath,
                                actionTitle = "Výpis VFS ($folderPath)",
                                status = "SUCCESS",
                                details = "Nalezeno ${nodes.size} položek"
                            ),
                            functionResponseJson = buildResponse(toolName) {
                                put("success", true)
                                put("count", nodes.size)
                                putJsonArray("files") {
                                    nodes.forEach { n ->
                                        addJsonObject {
                                            put("path", n.virtualPath)
                                            put("isDirectory", n.isDirectory)
                                            put("sizeBytes", n.sizeBytes)
                                            put("lineCount", n.lineCount)
                                        }
                                    }
                                }
                            }
                        )
                    },
                    onFailure = { err -> errorOutcome(runningRecord, err.message ?: "Chyba listFiles") }
                )
            }

            "deleteFile" -> {
                val path = args["path"]?.jsonPrimitive?.content ?: "/"
                val result = vfsManager.deleteFile(chatId, path)
                result.fold(
                    onSuccess = { deletedCount ->
                        VfsToolExecutionOutcome(
                            toolRecord = runningRecord.copy(
                                targetPath = path,
                                actionTitle = "Smazáno: $path",
                                status = "SUCCESS",
                                details = "Odstraněno $deletedCount položek z VFS"
                            ),
                            functionResponseJson = buildResponse(toolName) {
                                put("success", true)
                                put("deletedCount", deletedCount)
                                put("path", path)
                            }
                        )
                    },
                    onFailure = { err -> errorOutcome(runningRecord, err.message ?: "Chyba deleteFile") }
                )
            }

            "moveFile" -> {
                val src = args["sourcePath"]?.jsonPrimitive?.content ?: "/"
                val dst = args["destinationPath"]?.jsonPrimitive?.content ?: "/"
                val result = vfsManager.moveFile(chatId, src, dst)
                result.fold(
                    onSuccess = { node ->
                        VfsToolExecutionOutcome(
                            toolRecord = runningRecord.copy(
                                targetPath = node.virtualPath,
                                actionTitle = "Přesunuto na ${node.virtualPath}",
                                status = "SUCCESS",
                                details = "Původní cesta: $src",
                                sizeBytes = node.sizeBytes
                            ),
                            functionResponseJson = buildResponse(toolName) {
                                put("success", true)
                                put("sourcePath", src)
                                put("destinationPath", node.virtualPath)
                            }
                        )
                    },
                    onFailure = { err -> errorOutcome(runningRecord, err.message ?: "Chyba moveFile") }
                )
            }

            "renameFile" -> {
                val path = args["path"]?.jsonPrimitive?.content ?: "/"
                val newName = args["newName"]?.jsonPrimitive?.content ?: ""
                val result = vfsManager.renameFile(chatId, path, newName)
                result.fold(
                    onSuccess = { node ->
                        VfsToolExecutionOutcome(
                            toolRecord = runningRecord.copy(
                                targetPath = node.virtualPath,
                                actionTitle = "Přejmenováno na ${node.name}",
                                status = "SUCCESS",
                                details = "Nová cesta: ${node.virtualPath}",
                                sizeBytes = node.sizeBytes
                            ),
                            functionResponseJson = buildResponse(toolName) {
                                put("success", true)
                                put("oldPath", path)
                                put("newPath", node.virtualPath)
                            }
                        )
                    },
                    onFailure = { err -> errorOutcome(runningRecord, err.message ?: "Chyba renameFile") }
                )
            }

            else -> errorOutcome(runningRecord, "Neznámý VFS nástroj: $toolName")
        }
    }

    private fun errorOutcome(runningRecord: ToolCallRecord, errorMessage: String): VfsToolExecutionOutcome {
        return VfsToolExecutionOutcome(
            toolRecord = runningRecord.copy(
                actionTitle = "Chyba nástroje ${runningRecord.toolName}",
                status = "ERROR",
                details = errorMessage
            ),
            functionResponseJson = buildResponse(runningRecord.toolName) {
                put("success", false)
                put("error", errorMessage)
            }
        )
    }

    private fun buildResponse(toolName: String, builder: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): JsonObject {
        return buildJsonObject {
            putJsonObject("functionResponse") {
                put("name", toolName)
                putJsonObject("response", builder)
            }
        }
    }

    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes < 1024L) return "$bytes B"
            val kb = bytes / 1024.0
            if (kb < 1024.0) return String.format(java.util.Locale.US, "%.1f KB", kb)
            val mb = kb / 1024.0
            return String.format(java.util.Locale.US, "%.2f MB", mb)
        }
    }
}
