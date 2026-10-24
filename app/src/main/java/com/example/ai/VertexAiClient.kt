package com.example.ai

import com.example.BuildConfig
import com.example.data.local.AiModelEntity
import com.example.data.local.VertexChatDatabase
import com.example.data.preferences.AppSettings
import com.example.data.preferences.AuthMode
import com.example.data.security.KeystoreServiceAccountVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

/**
 * Síťový klient pro Google Cloud Vertex AI (přes OAuth2 Service Account) i přímé Gemini REST API.
 * Podporuje:
 * 1) Streamované generování přes SSE (:streamGenerateContent?alt=sse)
 * 2) ThinkingConfig s includeThoughts = true
 * 3) Function Calling pro VFS nástroje
 * 4) Načítání seznamu dostupných modelů (ListModels API)
 */
class VertexAiClient(
    private val okHttpClient: OkHttpClient,
    private val vault: KeystoreServiceAccountVault,
    private val tokenProvider: VertexOAuthTokenProvider
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Načte seznam modelů z Vertex AI (nebo Gemini API) a spojí je s doporučenými modely.
     */
    suspend fun fetchAvailableModels(settings: AppSettings): Result<List<AiModelEntity>> =
        withContext(Dispatchers.IO) {
            runCatching {
                when (settings.authMode) {
                    AuthMode.VERTEX_SERVICE_ACCOUNT -> fetchVertexModels(settings)
                    AuthMode.GEMINI_API_KEY -> fetchGeminiApiModels(settings)
                }
            }
        }

    private suspend fun fetchVertexModels(settings: AppSettings): List<AiModelEntity> {
        val creds = vault.getUnlockedCredentialsOrNull()
            ?: error("Trezor Service Accountu je uzamčen. Odemkněte jej v nastavení.")
        val token = tokenProvider.getValidAccessToken(creds).getOrThrow()
        val region = settings.region.ifBlank { "us-central1" }

        val url = "https://$region-aiplatform.googleapis.com/v1beta1/publishers/google/models?pageSize=50"
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .build()

        val discovered = mutableListOf<AiModelEntity>()
        runCatching {
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string().orEmpty()
                    val root = json.parseToJsonElement(bodyStr).jsonObject
                    val publisherModels = root["publisherModels"]?.jsonArray ?: JsonArray(emptyList())
                    for (elem in publisherModels) {
                        val obj = elem.jsonObject
                        val rawName = obj["name"]?.jsonPrimitive?.content ?: continue
                        val modelId = rawName.substringAfterLast('/')
                        if (!isSupportedGeminiModel(modelId)) continue
                        val displayName = obj["displayName"]?.jsonPrimitive?.content
                            ?: formatModelTitle(modelId)
                        discovered.add(
                            AiModelEntity(
                                modelId = modelId,
                                displayName = displayName,
                                description = "Vertex AI Publisher Model ($region)",
                                supportsThinking = modelId.contains("2.5") || modelId.contains("3."),
                                supportsTools = true,
                                isPinned = modelId.contains("pro") || modelId.contains("flash")
                            )
                        )
                    }
                }
            }
        }

        return mergeWithDefaults(discovered)
    }

    private fun fetchGeminiApiModels(settings: AppSettings): List<AiModelEntity> {
        val apiKey = resolveGeminiApiKey(settings)
        if (apiKey.isBlank()) {
            return VertexChatDatabase.DEFAULT_MODELS
        }
        val url = "https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey"
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/json")
            .build()

        val discovered = mutableListOf<AiModelEntity>()
        runCatching {
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string().orEmpty()
                    val root = json.parseToJsonElement(bodyStr).jsonObject
                    val models = root["models"]?.jsonArray ?: JsonArray(emptyList())
                    for (elem in models) {
                        val obj = elem.jsonObject
                        val rawName = obj["name"]?.jsonPrimitive?.content ?: continue
                        val modelId = rawName.removePrefix("models/")
                        if (!isSupportedGeminiModel(modelId)) continue
                        val displayName = obj["displayName"]?.jsonPrimitive?.content
                            ?: formatModelTitle(modelId)
                        val desc = obj["description"]?.jsonPrimitive?.content
                            ?: "Gemini API model"
                        discovered.add(
                            AiModelEntity(
                                modelId = modelId,
                                displayName = displayName,
                                description = desc,
                                supportsThinking = modelId.contains("2.5") || modelId.contains("3."),
                                supportsTools = true,
                                isPinned = false
                            )
                        )
                    }
                }
            }
        }

        return mergeWithDefaults(discovered)
    }

    /**
     * Spustí streamované generování (:streamGenerateContent?alt=sse) a volá [onEvent] pro každý SSE chunk.
     */
    suspend fun streamGenerateContent(
        settings: AppSettings,
        modelId: String,
        systemInstruction: String,
        contents: JsonArray,
        tools: JsonArray?,
        onEvent: (StreamEvent) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val payloadJson = buildRequestPayload(
                settings = settings,
                modelId = modelId,
                systemInstruction = systemInstruction,
                contents = contents,
                tools = tools
            )
            val requestBody = payloadJson.toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = when (settings.authMode) {
                AuthMode.VERTEX_SERVICE_ACCOUNT -> {
                    val creds = vault.getUnlockedCredentialsOrNull()
                        ?: error("Trezor Service Accountu je uzamčen. Otevřete sekci Autentizace a trezor a odemkněte jej heslem, nebo přepněte na Gemini API klíč.")
                    val token = tokenProvider.getValidAccessToken(creds).getOrThrow()
                    val projectId = settings.projectId.ifBlank { creds.projectId }
                    val region = settings.region.ifBlank { "us-central1" }
                    val endpoint = if (region.equals("global", ignoreCase = true)) {
                        "https://aiplatform.googleapis.com/v1/projects/$projectId/locations/global/publishers/google/models/$modelId:streamGenerateContent?alt=sse"
                    } else {
                        "https://$region-aiplatform.googleapis.com/v1/projects/$projectId/locations/$region/publishers/google/models/$modelId:streamGenerateContent?alt=sse"
                    }
                    Request.Builder()
                        .url(endpoint)
                        .post(requestBody)
                        .header("Authorization", "Bearer $token")
                        .header("Accept", "text/event-stream")
                        .build()
                }

                AuthMode.GEMINI_API_KEY -> {
                    val apiKey = resolveGeminiApiKey(settings)
                    require(apiKey.isNotBlank()) {
                        "Chybí Gemini API klíč. Nastavte GEMINI_API_KEY v panelu Secrets v AI Studiu, zadejte klíč v nastavení, nebo přepněte na Vertex AI Service Account."
                    }
                    val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$modelId:streamGenerateContent?alt=sse&key=$apiKey"
                    Request.Builder()
                        .url(endpoint)
                        .post(requestBody)
                        .header("Accept", "text/event-stream")
                        .build()
                }
            }

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string().orEmpty()
                    val cleanErr = extractApiErrorMessage(errBody, response.code)
                    error(cleanErr)
                }

                val body = response.body ?: error("Prázdná odpověď ze serveru.")
                BufferedReader(InputStreamReader(body.byteStream(), StandardCharsets.UTF_8), 32 * 1024).use { reader ->
                    VertexSseParser.parseSseStream(reader, onEvent)
                }
            }
        }
    }

    internal fun buildRequestPayload(
        settings: AppSettings,
        modelId: String,
        systemInstruction: String,
        contents: JsonArray,
        tools: JsonArray?
    ): JsonObject = buildJsonObject {
        put("contents", contents)

        if (systemInstruction.isNotBlank()) {
            putJsonObject("systemInstruction") {
                put("role", "system")
                putJsonArray("parts") {
                    addJsonObject {
                        put("text", systemInstruction)
                    }
                }
            }
        }

        if (tools != null && tools.isNotEmpty()) {
            put("tools", tools)
        }

        putJsonObject("generationConfig") {
            put("temperature", settings.temperature)
            put("maxOutputTokens", 65536)

            val supportsThinking = modelId.contains("2.5") || modelId.contains("3.")
            if (settings.includeThoughts && supportsThinking && !modelId.contains("lite")) {
                putJsonObject("thinkingConfig") {
                    put("includeThoughts", true)
                }
            }
        }
    }

    private fun resolveGeminiApiKey(settings: AppSettings): String {
        if (settings.customGeminiApiKey.isNotBlank()) {
            return settings.customGeminiApiKey.trim()
        }
        val buildConfigKey = BuildConfig.GEMINI_API_KEY
        return if (buildConfigKey.isNotBlank() && buildConfigKey != "MY_GEMINI_API_KEY") {
            buildConfigKey
        } else {
            ""
        }
    }

    private fun extractApiErrorMessage(rawBody: String, httpCode: Int): String {
        return try {
            val obj = json.parseToJsonElement(rawBody).jsonObject
            val message = obj["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
            if (!message.isNullOrBlank()) {
                "API chyba ($httpCode): $message"
            } else {
                "HTTP chyba $httpCode: ${rawBody.take(240)}"
            }
        } catch (_: Exception) {
            "HTTP chyba $httpCode: ${rawBody.take(240)}"
        }
    }

    private fun isSupportedGeminiModel(modelId: String): Boolean {
        val lower = modelId.lowercase()
        if (!lower.startsWith("gemini-")) return false
        // Exclude legacy deprecated 1.0/1.5/2.0 models and non-chat embedding models
        if (lower.contains("1.0") || lower.contains("1.5") || lower.contains("2.0") || lower.contains("embedding")) {
            return false
        }
        return true
    }

    private fun formatModelTitle(modelId: String): String {
        return modelId.split('-')
            .joinToString(" ") { part ->
                part.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
    }

    private fun mergeWithDefaults(discovered: List<AiModelEntity>): List<AiModelEntity> {
        val byId = LinkedHashMap<String, AiModelEntity>()
        VertexChatDatabase.DEFAULT_MODELS.forEach { byId[it.modelId] = it }
        discovered.forEach { model ->
            if (!byId.containsKey(model.modelId)) {
                byId[model.modelId] = model
            }
        }
        return byId.values.toList()
    }
}
