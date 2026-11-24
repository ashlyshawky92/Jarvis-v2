package com.jarvis.assistant.ui

import com.jarvis.assistant.actions.ActionExecutionStatus
import com.jarvis.assistant.actions.ActionRegistry
import com.jarvis.assistant.actions.PlannedAction
import com.jarvis.assistant.ai.AiBrainRequest
import com.jarvis.assistant.ai.AiProvider
import com.jarvis.assistant.ai.LocalCommandRouter
import com.jarvis.assistant.ai.LocalRouteOutcome
import com.jarvis.assistant.core.model.AssistantState
import com.jarvis.assistant.core.model.BatteryTelemetry
import com.jarvis.assistant.core.model.ConnectivityTelemetry
import com.jarvis.assistant.core.model.ConversationTurn
import com.jarvis.assistant.core.model.DeviceRuntimeInfo
import com.jarvis.assistant.core.model.TurnRole
import com.jarvis.assistant.database.ConversationHistoryStore
import com.jarvis.assistant.memory.JarvisSettingsState
import com.jarvis.assistant.memory.MemoryRepository
import com.jarvis.assistant.permissions.PermissionStatusSnapshot
import com.jarvis.assistant.voice.JarvisVoiceProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class DashboardUiState(
    val assistantState: AssistantState = AssistantState.INITIALIZING,
    val lastCommand: String = "",
    val lastResponse: String = "أهلاً بك يا فندم. أنا جارفيس، جاهز لمساعدتك.",
    val errorDetail: String? = null,
    val conversation: List<ConversationTurn> = emptyList(),
    val pendingConfirmation: PlannedAction? = null,
    val battery: BatteryTelemetry = BatteryTelemetry(),
    val connectivity: ConnectivityTelemetry = ConnectivityTelemetry(),
    val permissions: PermissionStatusSnapshot = PermissionStatusSnapshot(false, false, false),
    val deviceInfo: DeviceRuntimeInfo = DeviceRuntimeInfo.detect(),
    val connectionTestResult: String? = null
)

/**
 * Pure Kotlin state & orchestration coordinator separating ViewModel logic from Android framework
 * singletons so state transitions, cancellation, offline routing, and error recovery are fully
 * unit-testable on JVM.
 */
class JarvisSessionCoordinator(
    private val scope: CoroutineScope,
    private val memoryRepository: MemoryRepository,
    private val historyStore: ConversationHistoryStore,
    private val actionRegistry: ActionRegistry,
    private val localRouter: LocalCommandRouter = LocalCommandRouter(),
    private val aiProviderFactory: (JarvisSettingsState) -> AiProvider,
    private val speakText: (text: String, profile: JarvisVoiceProfile, onFinished: (Result<Unit>) -> Unit) -> Unit,
    private val stopSpeech: () -> Unit,
    private val getSettings: () -> JarvisSettingsState
) {
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private var activeCommandJob: Job? = null

    fun loadInitialHistory() {
        scope.launch {
            val loaded = historyStore.loadRecentTurns(limit = 50).getOrDefault(emptyList())
            val lastAssistant = loaded.lastOrNull { it.role == TurnRole.ASSISTANT }?.text
            val lastUser = loaded.lastOrNull { it.role == TurnRole.USER }?.text
            _uiState.value = _uiState.value.copy(
                conversation = loaded,
                lastCommand = lastUser.orEmpty(),
                lastResponse = lastAssistant ?: _uiState.value.lastResponse,
                assistantState = if (_uiState.value.connectivity.isOnline) {
                    AssistantState.STANDBY
                } else {
                    AssistantState.OFFLINE
                }
            )
        }
    }

    fun updateTelemetry(
        battery: BatteryTelemetry? = null,
        connectivity: ConnectivityTelemetry? = null,
        permissions: PermissionStatusSnapshot? = null
    ) {
        val current = _uiState.value
        val nextConn = connectivity ?: current.connectivity
        val nextState = when {
            !nextConn.isOnline && current.assistantState == AssistantState.STANDBY -> AssistantState.OFFLINE
            nextConn.isOnline && current.assistantState == AssistantState.OFFLINE -> AssistantState.STANDBY
            else -> current.assistantState
        }
        _uiState.value = current.copy(
            battery = battery ?: current.battery,
            connectivity = nextConn,
            permissions = permissions ?: current.permissions,
            assistantState = nextState
        )
    }

    fun setSpeechState(state: AssistantState, errorDetail: String? = null) {
        _uiState.value = _uiState.value.copy(
            assistantState = state,
            errorDetail = errorDetail
        )
    }

    fun setConnectionTestResult(message: String) {
        _uiState.value = _uiState.value.copy(connectionTestResult = message)
    }

    fun cancelActiveOperations() {
        activeCommandJob?.cancel()
        activeCommandJob = null
        stopSpeech()
        _uiState.value = _uiState.value.copy(
            pendingConfirmation = null,
            assistantState = baselineState()
        )
    }

    fun submitUserCommand(text: String): Job? {
        val clean = text.trim()
        if (clean.isBlank()) return null

        // Cancel any in-flight AI request so outdated responses never overwrite newer turns.
        activeCommandJob?.cancel()
        stopSpeech()

        val previousUserPrompt = _uiState.value.conversation.lastOrNull { it.role == TurnRole.USER }?.text
        val userTurn = ConversationTurn(
            id = UUID.randomUUID().toString(),
            role = TurnRole.USER,
            text = clean
        )

        _uiState.value = _uiState.value.copy(
            lastCommand = clean,
            conversation = _uiState.value.conversation + userTurn,
            pendingConfirmation = null,
            assistantState = AssistantState.THINKING,
            errorDetail = null
        )

        val job = scope.launch {
            historyStore.appendTurn(userTurn)
            try {
                when (val route = localRouter.route(clean, previousUserPrompt)) {
                    is LocalRouteOutcome.ExecuteLocalAction -> {
                        executePlannedAction(
                            planned = route.plannedAction,
                            confirmedByUser = false,
                            fallbackReply = route.preReplyArabic
                        )
                    }
                    is LocalRouteOutcome.DirectArabicReply -> {
                        deliverAssistantReply(route.replyArabic)
                    }
                    LocalRouteOutcome.ForwardToCloudAi -> {
                        if (!_uiState.value.connectivity.isOnline) {
                            deliverAssistantReply(
                                "الاتصال بالإنترنت مش متاح حالياً يا فندم، لكن أقدر أنفذ أوامر حالة البطارية والجهاز والذاكرة المحلية بدون إنترنت.",
                                finalState = AssistantState.OFFLINE
                            )
                            return@launch
                        }

                        val currentSettings = getSettings()
                        val relevantMemories = if (currentSettings.memoryEnabled && currentSettings.shareMemoriesWithAi) {
                            memoryRepository.getRelevantMemoriesForPrompt(clean, maxCount = 5)
                        } else {
                            emptyMap()
                        }

                        val provider = aiProviderFactory(currentSettings)
                        val result = provider.generateReply(
                            AiBrainRequest(
                                userMessage = clean,
                                history = _uiState.value.conversation,
                                userMemories = relevantMemories,
                                allowlistedActions = actionRegistry.getAllowlist(),
                                modelName = currentSettings.selectedModel
                            )
                        )

                        result.fold(
                            onSuccess = { response ->
                                val planned = response.plannedAction
                                if (planned != null) {
                                    executePlannedAction(
                                        planned = planned,
                                        confirmedByUser = false,
                                        fallbackReply = response.replyEgyptianArabic
                                    )
                                } else {
                                    deliverAssistantReply(response.replyEgyptianArabic)
                                }
                            },
                            onFailure = { err ->
                                val msg = err.message ?: "تعذر الاتصال بمحرك الذكاء الاصطناعي حالياً."
                                _uiState.value = _uiState.value.copy(
                                    assistantState = AssistantState.ERROR,
                                    errorDetail = msg,
                                    lastResponse = msg
                                )
                            }
                        )
                    }
                }
            } catch (c: CancellationException) {
                _uiState.value = _uiState.value.copy(assistantState = baselineState())
                throw c
            }
        }
        activeCommandJob = job
        return job
    }

    fun confirmPendingAction(approved: Boolean): Job? {
        val pending = _uiState.value.pendingConfirmation ?: return null
        _uiState.value = _uiState.value.copy(pendingConfirmation = null)
        if (!approved) {
            return scope.launch {
                deliverAssistantReply("تم إلغاء التنفيذ بناءً على طلبك يا فندم.")
            }
        }
        return scope.launch {
            executePlannedAction(planned = pending, confirmedByUser = true, fallbackReply = "")
        }
    }

    private suspend fun executePlannedAction(
        planned: PlannedAction,
        confirmedByUser: Boolean,
        fallbackReply: String
    ) {
        val result = actionRegistry.executeAction(
            planned = planned,
            confirmedByUser = confirmedByUser
        )

        when (result.status) {
            ActionExecutionStatus.AWAITING_CONFIRMATION -> {
                _uiState.value = _uiState.value.copy(
                    pendingConfirmation = planned,
                    assistantState = AssistantState.AWAITING_CONFIRMATION
                )
                deliverAssistantReply(
                    reply = result.spokenArabicMessage,
                    finalState = AssistantState.AWAITING_CONFIRMATION
                )
            }
            ActionExecutionStatus.FAILED,
            ActionExecutionStatus.INVALID_PARAMETERS -> {
                _uiState.value = _uiState.value.copy(errorDetail = result.verificationNote)
                deliverAssistantReply(
                    reply = result.spokenArabicMessage,
                    actionId = result.actionId,
                    actionStatus = result.status.name
                )
            }
            else -> {
                val msg = if (
                    fallbackReply.isNotBlank() &&
                    result.status == ActionExecutionStatus.NOT_IMPLEMENTED_IN_PHASE
                ) {
                    "$fallbackReply\n(${result.spokenArabicMessage})"
                } else {
                    result.spokenArabicMessage
                }
                deliverAssistantReply(
                    reply = msg,
                    actionId = result.actionId,
                    actionStatus = result.status.name
                )
            }
        }
    }

    private suspend fun deliverAssistantReply(
        reply: String,
        actionId: String? = null,
        actionStatus: String? = null,
        finalState: AssistantState? = null
    ) {
        val turn = ConversationTurn(
            id = UUID.randomUUID().toString(),
            role = TurnRole.ASSISTANT,
            text = reply,
            actionId = actionId,
            actionStatus = actionStatus
        )
        historyStore.appendTurn(turn)

        _uiState.value = _uiState.value.copy(
            lastResponse = reply,
            conversation = _uiState.value.conversation + turn,
            assistantState = AssistantState.SPEAKING
        )

        val targetAfterSpeech = finalState ?: baselineState()
        speakText(reply, getSettings().voiceProfile) { ttsOutcome ->
            ttsOutcome.onFailure { err ->
                // Recover state cleanly instead of leaving UI stuck in SPEAKING.
                _uiState.value = _uiState.value.copy(
                    assistantState = if (targetAfterSpeech == AssistantState.AWAITING_CONFIRMATION) {
                        AssistantState.AWAITING_CONFIRMATION
                    } else {
                        baselineState()
                    },
                    errorDetail = err.message
                )
            }.onSuccess {
                _uiState.value = _uiState.value.copy(
                    assistantState = targetAfterSpeech
                )
            }
        }
    }

    fun deleteConversationTurn(turnId: String) {
        scope.launch {
            historyStore.deleteTurn(turnId)
            _uiState.value = _uiState.value.copy(
                conversation = _uiState.value.conversation.filterNot { it.id == turnId }
            )
        }
    }

    fun clearConversationHistory() {
        scope.launch {
            historyStore.clearHistory()
            _uiState.value = _uiState.value.copy(
                conversation = emptyList(),
                lastCommand = "",
                lastResponse = "تم مسح سجل المحادثة يا فندم.",
                errorDetail = null
            )
        }
    }

    private fun baselineState(): AssistantState {
        return if (_uiState.value.connectivity.isOnline) {
            AssistantState.STANDBY
        } else {
            AssistantState.OFFLINE
        }
    }
}
