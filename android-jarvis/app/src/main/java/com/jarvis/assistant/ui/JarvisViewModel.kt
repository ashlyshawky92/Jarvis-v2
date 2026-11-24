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
import com.jarvis.assistant.actions.ActionRegistry
import com.jarvis.assistant.ai.BackendUrlValidator
import com.jarvis.assistant.ai.GeminiBackendAiProvider
import com.jarvis.assistant.core.model.AssistantState
import com.jarvis.assistant.core.model.BatteryTelemetry
import com.jarvis.assistant.core.model.ConnectivityTelemetry
import com.jarvis.assistant.database.SqliteConversationHistoryStore
import com.jarvis.assistant.memory.JarvisSettingsState
import com.jarvis.assistant.memory.MemoryCategory
import com.jarvis.assistant.memory.MemoryRepository
import com.jarvis.assistant.permissions.PermissionManager
import com.jarvis.assistant.voice.SpeechToTextEngine
import com.jarvis.assistant.voice.SttEngineState
import com.jarvis.assistant.voice.TextToSpeechEngine
import com.jarvis.assistant.voice.TtsStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class JarvisViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    val permissionManager = PermissionManager(appContext)
    val memoryRepository = MemoryRepository(appContext)
    private val historyStore = SqliteConversationHistoryStore(appContext)
    val actionRegistry = ActionRegistry(appContext, memoryRepository)
    val sttEngine = SpeechToTextEngine(appContext)
    val ttsEngine = TextToSpeechEngine(appContext)

    val settings: StateFlow<JarvisSettingsState> = memoryRepository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = JarvisSettingsState()
    )

    val ttsStatus: StateFlow<TtsStatus> = ttsEngine.status
    val sttState: StateFlow<SttEngineState> = sttEngine.state
    val rmsLevel: StateFlow<Float> = sttEngine.rmsLevel
    val partialSpeech: StateFlow<String> = sttEngine.partialText

    val coordinator = JarvisSessionCoordinator(
        scope = viewModelScope,
        memoryRepository = memoryRepository,
        historyStore = historyStore,
        actionRegistry = actionRegistry,
        aiProviderFactory = { s ->
            GeminiBackendAiProvider(
                backendBaseUrl = s.backendUrl,
                maxHistoryTurns = s.maxHistoryTurnsForAi
            )
        },
        speakText = { text, profile, onFinished -> ttsEngine.speak(text, profile, onFinished) },
        stopSpeech = { ttsEngine.stop() },
        getSettings = { settings.value }
    )

    val uiState: StateFlow<DashboardUiState> = coordinator.uiState

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val pct = if (level >= 0 && scale > 0) (level * 100) / scale else -1
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            coordinator.updateTelemetry(battery = BatteryTelemetry(levelPercent = pct, isCharging = charging))
        }
    }

    private val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refreshConnectivity()
        override fun onLost(network: Network) = refreshConnectivity()
        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            refreshConnectivity()
        }
    }

    init {
        appContext.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        cm.registerDefaultNetworkCallback(networkCallback)
        refreshConnectivity()
        refreshPermissions()
        coordinator.loadInitialHistory()
    }

    fun refreshPermissions() {
        coordinator.updateTelemetry(permissions = permissionManager.snapshot())
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
        coordinator.updateTelemetry(connectivity = ConnectivityTelemetry(isOnline = online, transportLabel = label))
    }

    fun onMicButtonPressed() {
        ttsEngine.stop()
        refreshPermissions()
        if (!uiState.value.permissions.microphoneGranted) {
            coordinator.setSpeechState(
                state = AssistantState.ERROR,
                errorDetail = "يا فندم، لازم تفعيل صلاحية الميكروفون الأول عشان أقدر أسمعك."
            )
            return
        }

        if (uiState.value.assistantState == AssistantState.LISTENING) {
            sttEngine.stopListening()
            coordinator.setSpeechState(
                if (uiState.value.connectivity.isOnline) AssistantState.STANDBY else AssistantState.OFFLINE
            )
            return
        }

        coordinator.setSpeechState(AssistantState.INITIALIZING)
        val currentSettings = settings.value
        sttEngine.startListening(
            languageTag = currentSettings.sttLanguageTag,
            preferOffline = currentSettings.preferOfflineStt || !uiState.value.connectivity.isOnline,
            isNetworkAvailable = uiState.value.connectivity.isOnline,
            onReady = {
                coordinator.setSpeechState(AssistantState.LISTENING)
            },
            onResult = { spokenText, _ ->
                coordinator.submitUserCommand(spokenText)
            },
            onError = { message ->
                coordinator.setSpeechState(AssistantState.ERROR, errorDetail = message)
            }
        )
    }

    fun stopSpeakingOrListening() {
        sttEngine.stopListening()
        coordinator.cancelActiveOperations()
    }

    fun submitUserCommand(text: String) {
        coordinator.submitUserCommand(text)
    }

    fun confirmPendingAction(approved: Boolean) {
        coordinator.confirmPendingAction(approved)
    }

    fun saveBackendUrl(rawUrl: String) {
        viewModelScope.launch {
            val validation = BackendUrlValidator.validate(rawUrl)
            validation.fold(
                onSuccess = { valid ->
                    memoryRepository.updateBackendUrl(valid)
                    coordinator.setConnectionTestResult("تم حفظ رابط الخادم بنجاح.")
                },
                onFailure = { err ->
                    coordinator.setConnectionTestResult(err.message ?: "رابط غير صالح.")
                }
            )
        }
    }

    fun testAiConnection() {
        viewModelScope.launch {
            val res = GeminiBackendAiProvider(settings.value.backendUrl).testConnection()
            coordinator.setConnectionTestResult(res.getOrElse { it.message ?: "فشل اختبار الاتصال" })
        }
    }

    fun testJarvisVoice() {
        ttsEngine.speak(
            "تحت أمرك يا فندم. صوت جارفيس باللهجة المصرية شغال وجاهز.",
            settings.value.voiceProfile
        ) { outcome ->
            outcome.onFailure { err ->
                coordinator.setSpeechState(AssistantState.ERROR, err.message)
            }
        }
    }

    fun saveManualMemory(key: String, value: String, category: MemoryCategory) {
        viewModelScope.launch {
            val res = memoryRepository.saveOrUpdateMemory(key, value, category)
            res.onFailure { err ->
                coordinator.setSpeechState(AssistantState.ERROR, err.message)
            }
        }
    }

    fun deleteMemory(key: String) {
        viewModelScope.launch { memoryRepository.deleteMemory(key) }
    }

    fun clearAllMemoriesConfirmed() {
        viewModelScope.launch { memoryRepository.clearAllMemories(explicitUserConfirmed = true) }
    }

    fun recoverCorruptedMemory() {
        viewModelScope.launch { memoryRepository.recoverCorruptedStoreWithBackup() }
    }

    fun deleteConversationTurn(turnId: String) = coordinator.deleteConversationTurn(turnId)
    fun clearConversationHistory() = coordinator.clearConversationHistory()

    override fun onCleared() {
        super.onCleared()
        runCatching { appContext.unregisterReceiver(batteryReceiver) }
        runCatching { cm.unregisterNetworkCallback(networkCallback) }
        sttEngine.release()
        ttsEngine.shutdown()
    }
}
