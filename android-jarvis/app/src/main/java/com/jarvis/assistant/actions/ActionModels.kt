package com.jarvis.assistant.actions

import kotlinx.serialization.Serializable

enum class ConfirmationPolicy {
    NONE,
    REQUIRED
}

enum class ActionExecutionStatus {
    SUCCESS,
    AWAITING_CONFIRMATION,
    NOT_IMPLEMENTED_IN_PHASE,
    PERMISSION_MISSING,
    INVALID_PARAMETERS,
    FAILED
}

data class ActionDefinition(
    val id: String,
    val name: String,
    val description: String,
    val requiredParameters: List<String>,
    val requiredPermissions: List<String>,
    val confirmationPolicy: ConfirmationPolicy,
    val implementedInCurrentPhase: Boolean
)

@Serializable
data class PlannedAction(
    val actionId: String,
    val parameters: Map<String, String> = emptyMap(),
    val requiresConfirmation: Boolean = true
)

data class ActionResult(
    val actionId: String,
    val status: ActionExecutionStatus,
    val spokenArabicMessage: String,
    val verificationNote: String
)
