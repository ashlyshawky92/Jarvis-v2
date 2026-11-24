package com.jarvis.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.jarvis.assistant.ui.JarvisViewModel
import com.jarvis.assistant.ui.screens.DashboardScreen
import com.jarvis.assistant.ui.screens.SettingsScreen
import com.jarvis.assistant.ui.theme.JarvisTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: JarvisViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            JarvisTheme {
                val uiState by viewModel.uiState.collectAsState()
                val settings by viewModel.settings.collectAsState()
                val ttsStatus by viewModel.ttsStatus.collectAsState()
                val rmsLevel by viewModel.rmsLevel.collectAsState()
                val partialSpeech by viewModel.partialSpeech.collectAsState()
                val scope = rememberCoroutineScope()

                var showSettings by remember { mutableStateOf(false) }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) {
                    viewModel.refreshPermissions()
                }

                if (showSettings) {
                    SettingsScreen(
                        uiState = uiState,
                        settings = settings,
                        ttsStatus = ttsStatus,
                        sttCapabilities = viewModel.sttEngine.getCapabilities(),
                        onBack = { showSettings = false },
                        onSaveBackendUrl = { url -> viewModel.saveBackendUrl(url) },
                        onTestConnection = { viewModel.testAiConnection() },
                        onUpdateVoice = { rate, pitch, langTag, voiceName ->
                            scope.launch {
                                viewModel.memoryRepository.updateVoiceSettings(rate, pitch, langTag, voiceName)
                            }
                        },
                        onTestVoice = { viewModel.testJarvisVoice() },
                        onToggleOfflineStt = { enabled ->
                            scope.launch { viewModel.memoryRepository.setPreferOfflineStt(enabled) }
                        },
                        onUpdateSttLanguage = { tag ->
                            scope.launch { viewModel.memoryRepository.setSttLanguageTag(tag) }
                        },
                        onToggleMemory = { enabled ->
                            scope.launch { viewModel.memoryRepository.setMemoryEnabled(enabled) }
                        },
                        onToggleShareMemoriesWithAi = { enabled ->
                            scope.launch { viewModel.memoryRepository.setShareMemoriesWithAi(enabled) }
                        },
                        onSaveManualMemory = { k, v, cat -> viewModel.saveManualMemory(k, v, cat) },
                        onDeleteMemory = { key -> viewModel.deleteMemory(key) },
                        onClearAllMemoriesConfirmed = { viewModel.clearAllMemoriesConfirmed() },
                        onRecoverCorruptedMemory = { viewModel.recoverCorruptedMemory() },
                        onClearHistory = { viewModel.clearConversationHistory() },
                        onOpenAppSettings = { startActivity(viewModel.permissionManager.createAppSettingsIntent()) },
                        onOpenBatterySettings = { startActivity(viewModel.permissionManager.createBatteryOptimizationSettingsIntent()) }
                    )
                } else {
                    DashboardScreen(
                        uiState = uiState,
                        rmsLevel = rmsLevel,
                        partialSpeech = partialSpeech,
                        rationales = viewModel.permissionManager.getPhase1Rationales(),
                        onMicClick = {
                            if (!uiState.permissions.microphoneGranted) {
                                permissionLauncher.launch(viewModel.permissionManager.phase1RequiredPermissions())
                            } else {
                                viewModel.onMicButtonPressed()
                            }
                        },
                        onStopClick = { viewModel.stopSpeakingOrListening() },
                        onSendText = { viewModel.submitUserCommand(it) },
                        onConfirmAction = { viewModel.confirmPendingAction(it) },
                        onDeleteTurn = { viewModel.deleteConversationTurn(it) },
                        onClearHistory = { viewModel.clearConversationHistory() },
                        onOpenSettings = { showSettings = true },
                        onRequestPermissions = {
                            permissionLauncher.launch(viewModel.permissionManager.phase1RequiredPermissions())
                        }
                    )
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        viewModel.sttEngine.stopListening()
    }
}
