package com.jarvis.assistant.memory

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jarvis.assistant.BuildConfig
import com.jarvis.assistant.voice.JarvisVoiceProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.jarvisDataStore: DataStore<Preferences> by preferencesDataStore(name = "jarvis_preferences")

data class JarvisSettingsState(
    val backendUrl: String = BuildConfig.JARVIS_BACKEND_URL,
    val selectedModel: String = BuildConfig.DEFAULT_GEMINI_MODEL,
    val voiceProfile: JarvisVoiceProfile = JarvisVoiceProfile(),
    val preferOfflineStt: Boolean = false,
    val memoryEnabled: Boolean = true,
    val memories: Map<String, String> = emptyMap(),
    val wakeWordUiPlaceholderEnabled: Boolean = false
)

class MemoryRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    private object Keys {
        val BACKEND_URL = stringPreferencesKey("backend_url")
        val SELECTED_MODEL = stringPreferencesKey("selected_model")
        val SPEECH_RATE = floatPreferencesKey("speech_rate")
        val SPEECH_PITCH = floatPreferencesKey("speech_pitch")
        val PREFER_OFFLINE_STT = booleanPreferencesKey("prefer_offline_stt")
        val MEMORY_ENABLED = booleanPreferencesKey("memory_enabled")
        val MEMORIES_JSON = stringPreferencesKey("memories_json")
    }

    val settingsFlow: Flow<JarvisSettingsState> = context.jarvisDataStore.data.map { prefs ->
        val rawMemories = prefs[Keys.MEMORIES_JSON].orEmpty()
        val parsedMemories = if (rawMemories.isNotBlank()) {
            runCatching { json.decodeFromString<Map<String, String>>(rawMemories) }.getOrDefault(emptyMap())
        } else {
            emptyMap()
        }

        JarvisSettingsState(
            backendUrl = prefs[Keys.BACKEND_URL] ?: BuildConfig.JARVIS_BACKEND_URL,
            selectedModel = prefs[Keys.SELECTED_MODEL] ?: BuildConfig.DEFAULT_GEMINI_MODEL,
            voiceProfile = JarvisVoiceProfile(
                profileName = "JARVIS",
                speechRate = prefs[Keys.SPEECH_RATE] ?: 1.0f,
                pitch = prefs[Keys.SPEECH_PITCH] ?: 0.92f
            ),
            preferOfflineStt = prefs[Keys.PREFER_OFFLINE_STT] ?: false,
            memoryEnabled = prefs[Keys.MEMORY_ENABLED] ?: true,
            memories = parsedMemories
        )
    }

    suspend fun updateBackendUrl(url: String) {
        context.jarvisDataStore.edit { it[Keys.BACKEND_URL] = url.trim() }
    }

    suspend fun updateModel(model: String) {
        context.jarvisDataStore.edit { it[Keys.SELECTED_MODEL] = model }
    }

    suspend fun updateVoiceSettings(rate: Float, pitch: Float) {
        context.jarvisDataStore.edit {
            it[Keys.SPEECH_RATE] = rate.coerceIn(0.6f, 1.6f)
            it[Keys.SPEECH_PITCH] = pitch.coerceIn(0.7f, 1.3f)
        }
    }

    suspend fun setPreferOfflineStt(enabled: Boolean) {
        context.jarvisDataStore.edit { it[Keys.PREFER_OFFLINE_STT] = enabled }
    }

    suspend fun setMemoryEnabled(enabled: Boolean) {
        context.jarvisDataStore.edit { it[Keys.MEMORY_ENABLED] = enabled }
    }

    suspend fun upsertMemory(key: String, value: String) {
        context.jarvisDataStore.edit { prefs ->
            val current = prefs[Keys.MEMORIES_JSON]
                ?.let { runCatching { json.decodeFromString<Map<String, String>>(it) }.getOrDefault(emptyMap()) }
                ?: emptyMap()
            val updated = current.toMutableMap().apply { put(key.trim(), value.trim()) }
            prefs[Keys.MEMORIES_JSON] = json.encodeToString(updated)
        }
    }

    suspend fun deleteMemory(key: String) {
        context.jarvisDataStore.edit { prefs ->
            val current = prefs[Keys.MEMORIES_JSON]
                ?.let { runCatching { json.decodeFromString<Map<String, String>>(it) }.getOrDefault(emptyMap()) }
                ?: emptyMap()
            val updated = current.toMutableMap().apply { remove(key) }
            prefs[Keys.MEMORIES_JSON] = json.encodeToString(updated)
        }
    }

    suspend fun clearAllMemories() {
        context.jarvisDataStore.edit { it.remove(Keys.MEMORIES_JSON) }
    }
}
