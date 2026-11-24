package com.jarvis.assistant.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.assistant.actions.ActionExecutionStatus
import com.jarvis.assistant.actions.ActionRegistry
import com.jarvis.assistant.actions.PlannedAction
import com.jarvis.assistant.ai.AiBrainRequest
import com.jarvis.assistant.ai.GeminiBackendAiProvider
import com.jarvis.assistant.core.model.AssistantState
import com.jarvis.assistant.core.model.BatteryTelemetry
import com.jarvis.assistant.core.model.ConnectivityTelemetry
import com.jarvis.assistant.core.model.ConversationTurn
import com.jarvis.assistant.core.model.DeviceRuntimeInfo
import com.jarvis.assistant.core.model.TurnRole
import com.jarvis.assistant.memory.JarvisSettingsState
import com.jarvis.assistant.memory.MemoryRepository
import com.jarvis.assistant.permissions.PermissionManager
import com.jarvis.assistant.permissions.PermissionStatusSnapshot
import com.jarvis.assistant.voice.SpeechToTextEngine
import com.jarvis.assistant.voice.TextToSpeechEngine
import com.jarvis.assistant.voice.TtsStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class DashboardUiState(
    val assistantState: AssistantState = AssistantState.STANDBY,
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

class JarvisViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    val permissionManager = PermissionManager(context)
    val memoryRepository = MemoryRepository(context)
    val actionRegistry = ActionRegistry(context)
    val sttEngine = SpeechToTextEngine(context)
    val ttsEngine = TextToSpeechEngine(context)

    val settings: StateFlow<JarvisSettingsState> = memoryRepository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = JarvisSettingsState()
    )

    val ttsStatus: StateFlow<TtsStatus> = ttsEngine.status
    val rmsLevel: StateFlow<Float> = sttEngine.rmsLevel
    val partialSpeech: StateFlow<String> = sttEngine.partialText

    private val _uiState = MutableStateFlow(
        DashboardUiState(permissions = permissionManager.snapshot())
    )
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val pct = if (level >= 0 && scale > 0) (level * 100) / scale else -1
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            _uiState.value = _uiState.value.copy(
                battery = BatteryTelemetry(levelPercent = pct, isCharging = charging)
            )
        }
    }

    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refreshConnectivity()
        override fun onLost(network: Network) = refreshConnectivity()
        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            refreshConnectivity()
        }
    }

    init {
        context.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        cm.registerDefaultNetworkCallback(networkCallback)
        refreshConnectivity()
        refreshPermissions()
    }

    fun refreshPermissions() {
        _uiState.value = _uiState.value.copy(permissions = permissionManager.snapshot())
    }

    private fun refreshConnectivity() {
        val active = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(active)
        val online = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val label = when {
            !online -> "غير متصل"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi متصل"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "بيانات الهاتف"
            else -> "متصل"
        }
        val currentState = _uiState.value.assistantState
        val nextState = when {
            !online && currentState == AssistantState.STANDBY -> AssistantState.OFFLINE
            online && currentState == AssistantState.OFFLINE -> AssistantState.STANDBY
            else -> currentState
        }
        _uiState.value = _uiState.value.copy(
            connectivity = ConnectivityTelemetry(isOnline = online, transportLabel = label),
            assistantState = nextState
        )
    }

    fun onMicButtonPressed() {
        if (ttsEngine.isSpeaking.value) {
            ttsEngine.stop()
        }
        refreshPermissions()
        if (!_uiState.value.permissions.microphoneGranted) {
            _uiState.value = _uiState.value.copy(
                assistantState = AssistantState.ERROR,
                errorDetail = "يا فندم، لازم تفعيل صلاحية الميكروفون الأول عشان أقدر أسمعك."
            )
            return
        }

        if (_uiState.value.assistantState == AssistantState.LISTENING) {
            sttEngine.stopListening()
            _uiState.value = _uiState.value.copy(assistantState = AssistantState.STANDBY)
            return
        }

        sttEngine.startListening(
            preferOffline = settings.value.preferOfflineStt || !_uiState.value.connectivity.isOnline,
            onReady = {
                _uiState.value = _uiState.value.copy(
                    assistantState = AssistantState.LISTENING,
                    errorDetail = null
                )
            },
            onResult = { spokenText ->
                submitUserCommand(spokenText)
            },
            onError = { message ->
                _uiState.value = _uiState.value.copy(
                    assistantState = AssistantState.ERROR,
                    errorDetail = message
                )
            }
        )
    }

    fun stopSpeakingOrListening() {
        sttEngine.stopListening()
        ttsEngine.stop()
        _uiState.value = _uiState.value.copy(
            assistantState = if (_uiState.value.connectivity.isOnline) {
                AssistantState.STANDBY
            } else {
                AssistantState.OFFLINE
            }
        )
    }

    fun submitUserCommand(text: String) {
        val clean = text.trim()
        if (clean.isBlank()) return

        if (ttsEngine.isSpeaking.value) {
            ttsEngine.stop()
        }

        val userTurn = ConversationTurn(
            id = UUID.randomUUID().toString(),
            role = TurnRole.USER,
            text = clean
        )
        _uiState.value = _uiState.value.copy(
            lastCommand = clean,
            conversation = _uiState.value.conversation + userTurn,
            assistantState = AssistantState.THINKING,
            errorDetail = null
        )

        viewModelScope.launch {
            if (!_uiState.value.connectivity.isOnline) {
                handleOfflineCommand(clean)
                return@launch
            }

            val currentSettings = settings.value
            val provider = GeminiBackendAiProvider(currentSettings.backendUrl)
            val result = provider.generateReply(
                AiBrainRequest(
                    userMessage = clean,
                    history = _uiState.value.conversation,
                    userMemories = if (currentSettings.memoryEnabled) currentSettings.memories else emptyMap(),
                    allowlistedActions = actionRegistry.getAllowlist(),
                    modelName = currentSettings.selectedModel
                )
            )

            result.fold(
                onSuccess = { response ->
                    val planned = response.plannedAction
                    if (planned != null) {
                        executePlannedAction(planned, confirmedByUser = false, fallbackReply = response.replyEgyptianArabic)
                    } else {
                        deliverAssistantReply(response.replyEgyptianArabic)
                    }
                },
                onFailure = { err ->
                    val fallbackMsg = err.message ?: "تعذر الاتصال بمحرك الذكاء الاصطناعي حالياً."
                    _uiState.value = _uiState.value.copy(
                        assistantState = AssistantState.ERROR,
                        errorDetail = fallbackMsg,
                        lastResponse = fallbackMsg
                    )
                }
            )
        }
    }

    private fun handleOfflineCommand(clean: String) {
        val lower = clean.lowercase()
        if (lower.contains("بطارية") || lower.contains("شحن") || lower.contains("حالة الجهاز")) {
            executePlannedAction(
                PlannedAction(actionId = "get_device_status", requiresConfirmation = false),
                confirmedByUser = true,
                fallbackReply = ""
            )
        } else {
            val offlineNotice = "الاتصال بالإنترنت مش متاح حالياً يا فندم، لكن أقدر أجاوب على حالة البطارية والجهاز محلياً."
            deliverAssistantReply(offlineNotice, finalState = AssistantState.OFFLINE)
        }
    }

    fun confirmPendingAction(approved: Boolean) {
        val pending = _uiState.value.pendingConfirmation ?: return
        _uiState.value = _uiState.value.copy(pendingConfirmation = null)
        if (!approved) {
            deliverAssistantReply("تم إلغاء التنفيذ بناءً على طلبك يا فندم.")
            return
        }
        executePlannedAction(pending, confirmedByUser = true, fallbackReply = "")
    }

    private fun executePlannedAction(
        planned: PlannedAction,
        confirmedByUser: Boolean,
        fallbackReply: String
    ) {
        _uiState.value = _uiState.value.copy(assistantState = AssistantState.EXECUTING)

        val result = actionRegistry.executeLocalAction(
            planned = planned,
            confirmedByUser = confirmedByUser,
            onSaveMemory = { key, value ->
                if (settings.value.memoryEnabled) {
                    viewModelScope.launch { memoryRepository.upsertMemory(key, value) }
                }
            }
        )

        when (result.status) {
            ActionExecutionStatus.AWAITING_CONFIRMATION -> {
                _uiState.value = _uiState.value.copy(
                    pendingConfirmation = planned,
                    assistantState = AssistantState.STANDBY
                )
                deliverAssistantReply(result.spokenArabicMessage)
            }
            else -> {
                val msg = if (fallbackReply.isNotBlank() && result.status == ActionExecutionStatus.NOT_IMPLEMENTED_IN_PHASE) {
                    "$fallbackReply\n(${result.spokenArabicMessage})"
                } else {
                    result.spokenArabicMessage
                }
                deliverAssistantReply(msg, actionId = result.actionId, actionStatus = result.status.name)
            }
        }
    }

    private fun deliverAssistantReply(
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
        _uiState.value = _uiState.value.copy(
            lastResponse = reply,
            conversation = _uiState.value.conversation + turn,
            assistantState = AssistantState.SPEAKING
        )

        ttsEngine.speak(reply, settings.value.voiceProfile) {
            val target = finalState ?: if (_uiState.value.connectivity.isOnline) {
                AssistantState.STANDBY
            } else {
                AssistantState.OFFLINE
            }
            _uiState.value = _uiState.value.copy(assistantState = target)
        }
    }

    fun testJarvisVoice() {
        ttsEngine.speak("تحت أمرك يا فندم. صوت جارفيس باللهجة المصرية شغال وجاهز.", settings.value.voiceProfile)
    }

    fun testAiConnection() {
        viewModelScope.launch {
            val res = GeminiBackendAiProvider(settings.value.backendUrl).testConnection()
            _uiState.value = _uiState.value.copy(
                connectionTestResult = res.getOrElse { it.message ?: "فشل الاختبار" }
            )
        }
    }

    fun clearConversationHistory() {
        _uiState.value = _uiState.value.copy(
            conversation = emptyList(),
            lastCommand = "",
            lastResponse = "تم مسح سجل المحادثة يا فندم."
        )
    }

    override fun onCleared() {
        super.onCleared()
        runCatching { context.unregisterReceiver(batteryReceiver) }
        runCatching { cm.unregisterNetworkCallback(networkCallback) }
        sttEngine.stopListening()
        ttsEngine.shutdown()
    }
}
