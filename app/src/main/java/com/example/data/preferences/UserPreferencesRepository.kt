package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "vertex_chat_settings")

enum class AuthMode {
    VERTEX_SERVICE_ACCOUNT,
    GEMINI_API_KEY
}

data class AppSettings(
    val authMode: AuthMode = AuthMode.VERTEX_SERVICE_ACCOUNT,
    val projectId: String = "",
    val region: String = "us-central1",
    val defaultModelId: String = "gemini-3.1-pro-preview",
    val includeThoughts: Boolean = true,
    val thinkingBudget: String = "HIGH", // LOW, MEDIUM, HIGH
    val temperature: Float = 0.4f,
    val autoContinueMaxTokens: Boolean = true,
    val maxAutoContinueSteps: Int = 5,
    val customGeminiApiKey: String = ""
)

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val AUTH_MODE = stringPreferencesKey("auth_mode")
        val PROJECT_ID = stringPreferencesKey("project_id")
        val REGION = stringPreferencesKey("region")
        val DEFAULT_MODEL_ID = stringPreferencesKey("default_model_id")
        val INCLUDE_THOUGHTS = booleanPreferencesKey("include_thoughts")
        val THINKING_BUDGET = stringPreferencesKey("thinking_budget")
        val TEMPERATURE = floatPreferencesKey("temperature")
        val AUTO_CONTINUE = booleanPreferencesKey("auto_continue_max_tokens")
        val MAX_CONTINUE_STEPS = intPreferencesKey("max_continue_steps")
        val CUSTOM_GEMINI_KEY = stringPreferencesKey("custom_gemini_api_key")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        val modeName = prefs[Keys.AUTH_MODE] ?: AuthMode.VERTEX_SERVICE_ACCOUNT.name
        val mode = runCatching { AuthMode.valueOf(modeName) }.getOrDefault(AuthMode.VERTEX_SERVICE_ACCOUNT)
        AppSettings(
            authMode = mode,
            projectId = prefs[Keys.PROJECT_ID] ?: "",
            region = prefs[Keys.REGION] ?: "us-central1",
            defaultModelId = prefs[Keys.DEFAULT_MODEL_ID] ?: "gemini-3.1-pro-preview",
            includeThoughts = prefs[Keys.INCLUDE_THOUGHTS] ?: true,
            thinkingBudget = prefs[Keys.THINKING_BUDGET] ?: "HIGH",
            temperature = prefs[Keys.TEMPERATURE] ?: 0.4f,
            autoContinueMaxTokens = prefs[Keys.AUTO_CONTINUE] ?: true,
            maxAutoContinueSteps = prefs[Keys.MAX_CONTINUE_STEPS] ?: 5,
            customGeminiApiKey = prefs[Keys.CUSTOM_GEMINI_KEY] ?: ""
        )
    }

    suspend fun updateAuthMode(mode: AuthMode) {
        context.dataStore.edit { it[Keys.AUTH_MODE] = mode.name }
    }

    suspend fun updateVertexConfig(projectId: String, region: String, defaultModelId: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.PROJECT_ID] = projectId.trim()
            prefs[Keys.REGION] = region.trim().ifEmpty { "us-central1" }
            if (defaultModelId.isNotBlank()) {
                prefs[Keys.DEFAULT_MODEL_ID] = defaultModelId.trim()
            }
        }
    }

    suspend fun updateDefaultModel(modelId: String) {
        context.dataStore.edit { it[Keys.DEFAULT_MODEL_ID] = modelId }
    }

    suspend fun updateGenerationParams(
        includeThoughts: Boolean,
        thinkingBudget: String,
        temperature: Float,
        autoContinue: Boolean,
        maxContinueSteps: Int
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.INCLUDE_THOUGHTS] = includeThoughts
            prefs[Keys.THINKING_BUDGET] = thinkingBudget
            prefs[Keys.TEMPERATURE] = temperature.coerceIn(0f, 2f)
            prefs[Keys.AUTO_CONTINUE] = autoContinue
            prefs[Keys.MAX_CONTINUE_STEPS] = maxContinueSteps.coerceIn(1, 15)
        }
    }

    suspend fun updateCustomGeminiApiKey(apiKey: String) {
        context.dataStore.edit { it[Keys.CUSTOM_GEMINI_KEY] = apiKey.trim() }
    }
}
