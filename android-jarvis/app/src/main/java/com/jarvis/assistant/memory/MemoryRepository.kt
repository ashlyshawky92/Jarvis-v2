package com.jarvis.assistant.memory

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jarvis.assistant.BuildConfig
import com.jarvis.assistant.voice.JarvisVoiceProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.jarvisDataStore: DataStore<Preferences> by preferencesDataStore(name = "jarvis_preferences")

/**
 * Storage abstraction so MemoryRepository can run reliably on Jetpack DataStore in production
 * and in pure JVM unit tests without Android Instrumentation overhead.
 */
interface PreferencesBackend {
    val stateFlow: Flow<JarvisSettingsState>
    suspend fun readRawMemoriesJson(): String?
    suspend fun readRawCorruptedBackup(): String?
    suspend fun isMemoryEnabled(): Boolean
    suspend fun writeMemoriesSafely(jsonPayload: String): Result<Unit>
    suspend fun backupCorruptedAndClearActive(corruptedRaw: String): Result<Unit>
    suspend fun clearMemoriesExplicitly(): Result<Unit>
    suspend fun updateBackendUrl(url: String): Result<Unit>
    suspend fun updateModel(model: String): Result<Unit>
    suspend fun updateVoiceSettings(rate: Float, pitch: Float, languageTag: String, voiceName: String?): Result<Unit>
    suspend fun setPreferOfflineStt(enabled: Boolean): Result<Unit>
    suspend fun setSttLanguageTag(tag: String): Result<Unit>
    suspend fun setMemoryEnabled(enabled: Boolean): Result<Unit>
    suspend fun setShareMemoriesWithAi(enabled: Boolean): Result<Unit>
}

class DataStorePreferencesBackend(private val context: Context) : PreferencesBackend {

    private val json = Json { ignoreUnknownKeys = true }

    private object Keys {
        val BACKEND_URL = stringPreferencesKey("backend_url")
        val SELECTED_MODEL = stringPreferencesKey("selected_model")
        val SPEECH_RATE = floatPreferencesKey("speech_rate")
        val SPEECH_PITCH = floatPreferencesKey("speech_pitch")
        val TTS_LANGUAGE_TAG = stringPreferencesKey("tts_language_tag")
        val TTS_VOICE_NAME = stringPreferencesKey("tts_voice_name")
        val PREFER_OFFLINE_STT = booleanPreferencesKey("prefer_offline_stt")
        val STT_LANGUAGE_TAG = stringPreferencesKey("stt_language_tag")
        val MEMORY_ENABLED = booleanPreferencesKey("memory_enabled")
        val SHARE_MEMORIES_AI = booleanPreferencesKey("share_memories_ai")
        val MAX_HISTORY_AI = intPreferencesKey("max_history_ai")
        val MEMORIES_JSON = stringPreferencesKey("memories_json_v2")
        val CORRUPTED_MEMORIES_BACKUP = stringPreferencesKey("memories_corrupted_backup")
    }

    override val stateFlow: Flow<JarvisSettingsState> = context.jarvisDataStore.data.map { prefs ->
        val raw = prefs[Keys.MEMORIES_JSON].orEmpty()
        val corruptBackup = prefs[Keys.CORRUPTED_MEMORIES_BACKUP]
        val parsed = MemoryJsonCodec.decodeEntries(json, raw)

        JarvisSettingsState(
            backendUrl = prefs[Keys.BACKEND_URL] ?: BuildConfig.JARVIS_BACKEND_URL,
            selectedModel = prefs[Keys.SELECTED_MODEL] ?: BuildConfig.DEFAULT_GEMINI_MODEL,
            voiceProfile = JarvisVoiceProfile(
                profileName = "JARVIS",
                languageTag = prefs[Keys.TTS_LANGUAGE_TAG] ?: "ar-EG",
                selectedVoiceName = prefs[Keys.TTS_VOICE_NAME],
                speechRate = prefs[Keys.SPEECH_RATE] ?: 1.0f,
                pitch = prefs[Keys.SPEECH_PITCH] ?: 0.92f
            ),
            preferOfflineStt = prefs[Keys.PREFER_OFFLINE_STT] ?: false,
            sttLanguageTag = prefs[Keys.STT_LANGUAGE_TAG] ?: "ar-EG",
            memoryEnabled = prefs[Keys.MEMORY_ENABLED] ?: true,
            memories = parsed.getOrElse { emptyList() },
            memoryIntegrity = MemoryIntegrityStatus(
                hasCorruptedBackup = !corruptBackup.isNullOrBlank() || parsed.isFailure,
                corruptionMessage = when {
                    parsed.isFailure -> "تعذر قراءة ملف الذاكرة المحفوظ؛ البيانات الأصلية محفوظة دون مسح تلقائي."
                    !corruptBackup.isNullOrBlank() -> "تم عزل نسخة ذاكرة تالفة احتياطياً لحماية البيانات."
                    else -> null
                }
            ),
            maxHistoryTurnsForAi = prefs[Keys.MAX_HISTORY_AI] ?: 10,
            shareMemoriesWithAi = prefs[Keys.SHARE_MEMORIES_AI] ?: true
        )
    }

    override suspend fun readRawMemoriesJson(): String? =
        context.jarvisDataStore.data.first()[Keys.MEMORIES_JSON]

    override suspend fun readRawCorruptedBackup(): String? =
        context.jarvisDataStore.data.first()[Keys.CORRUPTED_MEMORIES_BACKUP]

    override suspend fun isMemoryEnabled(): Boolean =
        context.jarvisDataStore.data.first()[Keys.MEMORY_ENABLED] ?: true

    override suspend fun writeMemoriesSafely(jsonPayload: String): Result<Unit> = runCatching {
        context.jarvisDataStore.edit { it[Keys.MEMORIES_JSON] = jsonPayload }
        Unit
    }

    override suspend fun backupCorruptedAndClearActive(corruptedRaw: String): Result<Unit> = runCatching {
        context.jarvisDataStore.edit {
            it[Keys.CORRUPTED_MEMORIES_BACKUP] = corruptedRaw
            it[Keys.MEMORIES_JSON] = "[]"
        }
        Unit
    }

    override suspend fun clearMemoriesExplicitly(): Result<Unit> = runCatching {
        context.jarvisDataStore.edit {
            it.remove(Keys.MEMORIES_JSON)
            it.remove(Keys.CORRUPTED_MEMORIES_BACKUP)
        }
        Unit
    }

    override suspend fun updateBackendUrl(url: String): Result<Unit> = runCatching {
        context.jarvisDataStore.edit { it[Keys.BACKEND_URL] = url.trim() }
        Unit
    }

    override suspend fun updateModel(model: String): Result<Unit> = runCatching {
        context.jarvisDataStore.edit { it[Keys.SELECTED_MODEL] = model.trim() }
        Unit
    }

    override suspend fun updateVoiceSettings(
        rate: Float,
        pitch: Float,
        languageTag: String,
        voiceName: String?
    ): Result<Unit> = runCatching {
        context.jarvisDataStore.edit {
            it[Keys.SPEECH_RATE] = rate.coerceIn(0.6f, 1.6f)
            it[Keys.SPEECH_PITCH] = pitch.coerceIn(0.7f, 1.3f)
            it[Keys.TTS_LANGUAGE_TAG] = languageTag.ifBlank { "ar-EG" }
            if (voiceName.isNullOrBlank()) {
                it.remove(Keys.TTS_VOICE_NAME)
            } else {
                it[Keys.TTS_VOICE_NAME] = voiceName
            }
        }
        Unit
    }

    override suspend fun setPreferOfflineStt(enabled: Boolean): Result<Unit> = runCatching {
        context.jarvisDataStore.edit { it[Keys.PREFER_OFFLINE_STT] = enabled }
        Unit
    }

    override suspend fun setSttLanguageTag(tag: String): Result<Unit> = runCatching {
        context.jarvisDataStore.edit { it[Keys.STT_LANGUAGE_TAG] = tag.ifBlank { "ar-EG" } }
        Unit
    }

    override suspend fun setMemoryEnabled(enabled: Boolean): Result<Unit> = runCatching {
        context.jarvisDataStore.edit { it[Keys.MEMORY_ENABLED] = enabled }
        Unit
    }

    override suspend fun setShareMemoriesWithAi(enabled: Boolean): Result<Unit> = runCatching {
        context.jarvisDataStore.edit { it[Keys.SHARE_MEMORIES_AI] = enabled }
        Unit
    }
}

internal object MemoryJsonCodec {
    fun decodeEntries(json: Json, raw: String): Result<List<MemoryEntry>> {
        if (raw.isBlank()) return Result.success(emptyList())
        return runCatching {
            json.decodeFromString<List<MemoryEntry>>(raw)
        }
    }
}

class MemoryRepository(
    private val backend: PreferencesBackend,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    constructor(context: Context) : this(DataStorePreferencesBackend(context.applicationContext))

    private val json = Json { ignoreUnknownKeys = true }

    val settingsFlow: Flow<JarvisSettingsState> = backend.stateFlow

    fun normalizeKey(rawKey: String): String {
        return rawKey.trim()
            .lowercase()
            .replace(Regex("\\s+"), "_")
    }

    suspend fun saveOrUpdateMemory(
        rawKey: String,
        rawValue: String,
        category: MemoryCategory = MemoryCategory.PERSONAL_NOTE
    ): Result<MemoryEntry> {
        if (!backend.isMemoryEnabled()) {
            return Result.failure(IllegalStateException("الذاكرة المحلية معطلة حالياً من الإعدادات."))
        }

        val cleanDisplayKey = rawKey.trim()
        val cleanValue = rawValue.trim()
        val normalized = normalizeKey(cleanDisplayKey)

        if (normalized.isBlank() || cleanValue.isBlank()) {
            return Result.failure(IllegalArgumentException("مفتاح المعلومة والقيمة مينفعش يكونوا فاضيين."))
        }
        if (cleanDisplayKey.length > 120 || cleanValue.length > 1000) {
            return Result.failure(IllegalArgumentException("حجم النص أكبر من الحد المسموح للذاكرة المحلية."))
        }

        val rawJson = backend.readRawMemoriesJson().orEmpty()
        val existingList = MemoryJsonCodec.decodeEntries(json, rawJson).getOrElse { err ->
            // Preserve the corrupted payload separately rather than silently overwriting it.
            return Result.failure(
                IllegalStateException(
                    "ملف الذاكرة الحالي تالف؛ رفضت الكتابة فوقه لحماية بياناتك (${err.message})."
                )
            )
        }

        val now = clock()
        val existing = existingList.find { it.normalizedKey == normalized }
        val entry = if (existing != null) {
            existing.copy(
                displayKey = cleanDisplayKey,
                value = cleanValue,
                category = category,
                updatedAtMillis = now
            )
        } else {
            MemoryEntry(
                normalizedKey = normalized,
                displayKey = cleanDisplayKey,
                value = cleanValue,
                category = category,
                createdAtMillis = now,
                updatedAtMillis = now
            )
        }

        val updatedList = existingList.filterNot { it.normalizedKey == normalized } + entry
        val encoded = json.encodeToString(updatedList)
        return backend.writeMemoriesSafely(encoded).map { entry }
    }

    suspend fun retrieveMemory(rawKey: String): Result<MemoryEntry?> {
        if (!backend.isMemoryEnabled()) {
            return Result.failure(IllegalStateException("الذاكرة المحلية معطلة حالياً."))
        }
        val normalized = normalizeKey(rawKey)
        if (normalized.isBlank()) {
            return Result.failure(IllegalArgumentException("حدد اسم المعلومة المطلوب البحث عنها."))
        }
        val entries = loadAllActiveEntries().getOrElse { return Result.failure(it) }
        val exact = entries.find { it.normalizedKey == normalized }
            ?: entries.find { it.displayKey.contains(rawKey.trim(), ignoreCase = true) }
        return Result.success(exact)
    }

    suspend fun searchMemories(query: String): Result<List<MemoryEntry>> {
        if (!backend.isMemoryEnabled()) {
            return Result.success(emptyList())
        }
        val clean = query.trim()
        val entries = loadAllActiveEntries().getOrElse { return Result.failure(it) }
        if (clean.isBlank()) return Result.success(entries.sortedByDescending { it.updatedAtMillis })

        val tokens = clean.lowercase().split(Regex("\\s+")).filter { it.length >= 2 }
        val matches = entries.filter { entry ->
            val haystack = "${entry.displayKey} ${entry.value} ${entry.category.arabicLabel}".lowercase()
            haystack.contains(clean.lowercase()) || tokens.any { haystack.contains(it) }
        }.sortedByDescending { it.updatedAtMillis }
        return Result.success(matches)
    }

    /**
     * Only send relevant local memories (up to maxCount) to Gemini, never dumping the entire
     * personal store automatically on every turn.
     */
    suspend fun getRelevantMemoriesForPrompt(query: String, maxCount: Int = 5): Map<String, String> {
        if (!backend.isMemoryEnabled()) return emptyMap()
        val searched = searchMemories(query).getOrDefault(emptyList())
        return searched.take(maxCount).associate { it.displayKey to it.value }
    }

    suspend fun deleteMemory(rawKey: String): Result<Boolean> {
        if (!backend.isMemoryEnabled()) {
            return Result.failure(IllegalStateException("الذاكرة المحلية معطلة حالياً."))
        }
        val normalized = normalizeKey(rawKey)
        if (normalized.isBlank()) {
            return Result.failure(IllegalArgumentException("اسم المعلومة غير صالح."))
        }
        val entries = loadAllActiveEntries().getOrElse { return Result.failure(it) }
        val filtered = entries.filterNot { it.normalizedKey == normalized || it.displayKey.equals(rawKey.trim(), ignoreCase = true) }
        if (filtered.size == entries.size) {
            return Result.success(false)
        }
        val encoded = json.encodeToString(filtered)
        return backend.writeMemoriesSafely(encoded).map { true }
    }

    suspend fun clearAllMemories(explicitUserConfirmed: Boolean): Result<Unit> {
        if (!explicitUserConfirmed) {
            return Result.failure(IllegalStateException("لازم تأكيد صريح من المستخدم قبل مسح كل الذاكرة."))
        }
        return backend.clearMemoriesExplicitly()
    }

    suspend fun recoverCorruptedStoreWithBackup(): Result<Unit> {
        val raw = backend.readRawMemoriesJson().orEmpty()
        return backend.backupCorruptedAndClearActive(raw)
    }

    private suspend fun loadAllActiveEntries(): Result<List<MemoryEntry>> {
        val raw = backend.readRawMemoriesJson().orEmpty()
        return MemoryJsonCodec.decodeEntries(json, raw)
    }

    suspend fun updateBackendUrl(url: String): Result<Unit> = backend.updateBackendUrl(url)
    suspend fun updateModel(model: String): Result<Unit> = backend.updateModel(model)
    suspend fun updateVoiceSettings(rate: Float, pitch: Float, languageTag: String, voiceName: String?): Result<Unit> =
        backend.updateVoiceSettings(rate, pitch, languageTag, voiceName)
    suspend fun setPreferOfflineStt(enabled: Boolean): Result<Unit> = backend.setPreferOfflineStt(enabled)
    suspend fun setSttLanguageTag(tag: String): Result<Unit> = backend.setSttLanguageTag(tag)
    suspend fun setMemoryEnabled(enabled: Boolean): Result<Unit> = backend.setMemoryEnabled(enabled)
    suspend fun setShareMemoriesWithAi(enabled: Boolean): Result<Unit> = backend.setShareMemoriesWithAi(enabled)
}
