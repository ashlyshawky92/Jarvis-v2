package com.jarvis.assistant.memory

import com.jarvis.assistant.BuildConfig
import com.jarvis.assistant.voice.JarvisVoiceProfile
import kotlinx.serialization.Serializable

enum class MemoryCategory(val arabicLabel: String) {
    USER_PREFERENCE("تفضيلات المستخدم"),
    PERSONAL_NOTE("ملاحظة شخصية"),
    IMPORTANT_FACT("معلومة مهمة"),
    FREQUENT_SETTING("إعداد متكرر"),
    ASSISTANT_CONFIG("إعداد المساعد")
}

@Serializable
data class MemoryEntry(
    val normalizedKey: String,
    val displayKey: String,
    val value: String,
    val category: MemoryCategory = MemoryCategory.PERSONAL_NOTE,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
)

data class MemoryIntegrityStatus(
    val hasCorruptedBackup: Boolean = false,
    val corruptionMessage: String? = null
)

data class JarvisSettingsState(
    val backendUrl: String = BuildConfig.JARVIS_BACKEND_URL,
    val selectedModel: String = BuildConfig.DEFAULT_GEMINI_MODEL,
    val voiceProfile: JarvisVoiceProfile = JarvisVoiceProfile(),
    val preferOfflineStt: Boolean = false,
    val sttLanguageTag: String = "ar-EG",
    val memoryEnabled: Boolean = true,
    val memories: List<MemoryEntry> = emptyList(),
    val memoryIntegrity: MemoryIntegrityStatus = MemoryIntegrityStatus(),
    val maxHistoryTurnsForAi: Int = 10,
    val shareMemoriesWithAi: Boolean = true
)
