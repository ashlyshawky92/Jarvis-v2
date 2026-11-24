package com.jarvis.assistant.ai

import com.jarvis.assistant.actions.ActionDefinition
import com.jarvis.assistant.actions.PlannedAction
import com.jarvis.assistant.core.model.ConversationTurn

data class AiBrainRequest(
    val userMessage: String,
    val history: List<ConversationTurn>,
    val userMemories: Map<String, String>,
    val allowlistedActions: List<ActionDefinition>,
    val modelName: String
)

data class AiBrainResponse(
    val replyEgyptianArabic: String,
    val plannedAction: PlannedAction? = null,
    val providerName: String,
    val modelUsed: String,
    val latencyMs: Long
)

interface AiProvider {
    val providerId: String
    val displayName: String

    suspend fun generateReply(request: AiBrainRequest): Result<AiBrainResponse>
    suspend fun testConnection(): Result<String>
}
