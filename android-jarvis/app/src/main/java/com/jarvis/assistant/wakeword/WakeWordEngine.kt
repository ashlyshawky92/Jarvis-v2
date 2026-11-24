package com.jarvis.assistant.wakeword

import kotlinx.coroutines.flow.StateFlow

enum class WakeWordState {
    UNAVAILABLE_NOT_INTEGRATED,
    STOPPED,
    STARTING,
    LISTENING_FOR_WAKE_WORD,
    TRIGGERED,
    ERROR
}

data class WakeWordRequirements(
    val requiredRuntimePermission: String = "android.permission.RECORD_AUDIO",
    val requiredForegroundServiceType: String = "microphone",
    val requiresUserBatteryExemptionOnHonorMagicOs: Boolean = true,
    val wakePhrase: String = "JARVIS",
    val isRealEngineIntegrated: Boolean = false
)

/**
 * Clean preparation contract for Phase 2 always-on local wake word ("JARVIS").
 * Strictly separated from SpeechToTextEngine so wake-word listening never silently streams
 * audio to cloud STT. No fake wake-word detector is implemented in Phase 1.1.
 */
interface WakeWordEngine {
    val state: StateFlow<WakeWordState>
    val requirements: WakeWordRequirements

    suspend fun startWakeWordDetection(onDetected: () -> Unit): Result<Unit>
    suspend fun stopWakeWordDetection()
    fun release()
}
