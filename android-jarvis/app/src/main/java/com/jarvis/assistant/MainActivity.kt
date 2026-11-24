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
                        onBack = { showSettings = false },
                        onSaveBackendUrl = { url -> scope.launch { viewModel.memoryRepository.updateBackendUrl(url) } },
                        onTestConnection = { viewModel.testAiConnection() },
                        onUpdateVoice = { rate, pitch -> scope.launch { viewModel.memoryRepository.updateVoiceSettings(rate, pitch) } },
                        onTestVoice = { viewModel.testJarvisVoice() },
                        onToggleOfflineStt = { enabled -> scope.launch { viewModel.memoryRepository.setPreferOfflineStt(enabled) } },
                        onToggleMemory = { enabled -> scope.launch { viewModel.memoryRepository.setMemoryEnabled(enabled) } },
                        onDeleteMemory = { key -> scope.launch { viewModel.memoryRepository.deleteMemory(key) } },
                        onClearMemories = { scope.launch { viewModel.memoryRepository.clearAllMemories() } },
                        onClearHistory = { viewModel.clearConversationHistory() },
                        onOpenAppSettings = { startActivity(viewModel.permissionManager.createAppSettingsIntent()) },
                        onOpenBatterySettings = { startActivity(viewModel.permissionManager.createBatteryOptimizationSettingsIntent()) }
                    )
                } else {
                    DashboardScreen(
                        uiState = uiState,
                        rmsLevel = rmsLevel,
                        partialSpeech = partialSpeech,
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
                        onOpenSettings = { showSettings = true },
                        onRequestPermissions = {
                            permissionLauncher.launch(viewModel.permissionManager.phase1RequiredPermissions())
                        }
                    )
                }
            }
        }
    }
}
