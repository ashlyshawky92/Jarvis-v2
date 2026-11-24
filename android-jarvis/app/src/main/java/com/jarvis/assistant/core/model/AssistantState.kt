package com.jarvis.assistant.core.model

enum class AssistantState(val arabicLabel: String, val englishCode: String) {
    STANDBY("في الانتظار", "STANDBY"),
    LISTENING("بسمعك يا فندم...", "LISTENING"),
    THINKING("بفكر...", "THINKING"),
    EXECUTING("بنفذ الأمر...", "EXECUTING"),
    SPEAKING("بتكلم...", "SPEAKING"),
    OFFLINE("غير متصل بالإنترنت", "OFFLINE"),
    ERROR("حصلت مشكلة", "ERROR")
}

data class ConversationTurn(
    val id: String,
    val role: TurnRole,
    val text: String,
    val timestampMillis: Long = System.currentTimeMillis(),
    val actionId: String? = null,
    val actionStatus: String? = null
)

enum class TurnRole {
    USER,
    ASSISTANT,
    SYSTEM
}

data class BatteryTelemetry(
    val levelPercent: Int = -1,
    val isCharging: Boolean = false
)

data class ConnectivityTelemetry(
    val isOnline: Boolean = false,
    val transportLabel: String = "غير معروف"
)
