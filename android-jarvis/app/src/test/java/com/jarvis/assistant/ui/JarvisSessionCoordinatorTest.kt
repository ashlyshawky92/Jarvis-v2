package com.jarvis.assistant.ui

import com.jarvis.assistant.actions.ActionExecutionStatus
import com.jarvis.assistant.actions.ActionRegistry
import com.jarvis.assistant.actions.ActionResult
import com.jarvis.assistant.actions.DeviceStatusReader
import com.jarvis.assistant.actions.PlannedAction
import com.jarvis.assistant.ai.AiBrainRequest
import com.jarvis.assistant.ai.AiBrainResponse
import com.jarvis.assistant.ai.AiProvider
import com.jarvis.assistant.core.model.AssistantState
import com.jarvis.assistant.core.model.ConnectivityTelemetry
import com.jarvis.assistant.core.model.ConversationTurn
import com.jarvis.assistant.database.ConversationHistoryStore
import com.jarvis.assistant.memory.FakePreferencesBackend
import com.jarvis.assistant.memory.JarvisSettingsState
import com.jarvis.assistant.memory.MemoryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class InMemoryHistoryStore : ConversationHistoryStore {
    private val list = mutableListOf<ConversationTurn>()
    override suspend fun appendTurn(turn: ConversationTurn): Result<Unit> {
        list.add(turn)
        return Result.success(Unit)
    }
    override suspend fun loadRecentTurns(limit: Int): Result<List<ConversationTurn>> = Result.success(list.takeLast(limit))
    override suspend fun deleteTurn(turnId: String): Result<Unit> {
        list.removeAll { it.id == turnId }
        return Result.success(Unit)
    }
    override suspend fun clearHistory(): Result<Unit> {
        list.clear()
        return Result.success(Unit)
    }
}

private class StubDeviceReader : DeviceStatusReader {
    override fun readStatusMessage(): ActionResult = ActionResult(
        actionId = "get_device_status",
        status = ActionExecutionStatus.SUCCESS,
        spokenArabicMessage = "البطارية 90%",
        verificationNote = "stub"
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
class JarvisSessionCoordinatorTest {

    @Test
    fun `offline mode blocks cloud AI and updates state to OFFLINE`() = runTest {
        val backend = FakePreferencesBackend()
        val memoryRepo = MemoryRepository(backend)
        val coordinator = JarvisSessionCoordinator(
            scope = this,
            memoryRepository = memoryRepo,
            historyStore = InMemoryHistoryStore(),
            actionRegistry = ActionRegistry(StubDeviceReader(), memoryRepo),
            aiProviderFactory = {
                object : AiProvider {
                    override val providerId = "stub"
                    override val displayName = "stub"
                    override suspend fun generateReply(request: AiBrainRequest) =
                        Result.failure<AiBrainResponse>(AssertionError("Should not call AI offline"))
                    override suspend fun testConnection() = Result.success("ok")
                }
            },
            speakText = { _, _, done -> done(Result.success(Unit)) },
            stopSpeech = {},
            getSettings = { JarvisSettingsState() }
        )

        coordinator.updateTelemetry(connectivity = ConnectivityTelemetry(isOnline = false, transportLabel = "غير متصل"))
        coordinator.submitUserCommand("كلمني عن الفضاء")
        advanceUntilIdle()

        assertEquals(AssistantState.OFFLINE, coordinator.uiState.value.assistantState)
    }

    @Test
    fun `AI failure sets ERROR state and surfaces error detail`() = runTest {
        val memoryRepo = MemoryRepository(FakePreferencesBackend())
        val coordinator = JarvisSessionCoordinator(
            scope = this,
            memoryRepository = memoryRepo,
            historyStore = InMemoryHistoryStore(),
            actionRegistry = ActionRegistry(StubDeviceReader(), memoryRepo),
            aiProviderFactory = {
                object : AiProvider {
                    override val providerId = "fail"
                    override val displayName = "fail"
                    override suspend fun generateReply(request: AiBrainRequest) =
                        Result.failure<AiBrainResponse>(IllegalStateException("خطأ شبكة مؤقت"))
                    override suspend fun testConnection() = Result.failure<String>(IllegalStateException("fail"))
                }
            },
            speakText = { _, _, done -> done(Result.success(Unit)) },
            stopSpeech = {},
            getSettings = { JarvisSettingsState() }
        )

        coordinator.updateTelemetry(connectivity = ConnectivityTelemetry(isOnline = true, transportLabel = "Wi-Fi"))
        coordinator.submitUserCommand("اشرحلي الفيزياء")
        advanceUntilIdle()

        assertEquals(AssistantState.ERROR, coordinator.uiState.value.assistantState)
        assertEquals("خطأ شبكة مؤقت", coordinator.uiState.value.errorDetail)
    }

    @Test
    fun `cancellation resets assistant back to STANDBY`() = runTest {
        val memoryRepo = MemoryRepository(FakePreferencesBackend())
        val coordinator = JarvisSessionCoordinator(
            scope = this,
            memoryRepository = memoryRepo,
            historyStore = InMemoryHistoryStore(),
            actionRegistry = ActionRegistry(StubDeviceReader(), memoryRepo),
            aiProviderFactory = {
                object : AiProvider {
                    override val providerId = "slow"
                    override val displayName = "slow"
                    override suspend fun generateReply(request: AiBrainRequest): Result<AiBrainResponse> {
                        delay(5000)
                        return Result.success(AiBrainResponse("رد متأخر", null, "slow", "m", 1))
                    }
                    override suspend fun testConnection() = Result.success("ok")
                }
            },
            speakText = { _, _, done -> done(Result.success(Unit)) },
            stopSpeech = {},
            getSettings = { JarvisSettingsState() }
        )

        coordinator.updateTelemetry(connectivity = ConnectivityTelemetry(isOnline = true, transportLabel = "Wi-Fi"))
        coordinator.submitUserCommand("سؤال طويل")
        coordinator.cancelActiveOperations()
        advanceUntilIdle()

        assertEquals(AssistantState.STANDBY, coordinator.uiState.value.assistantState)
    }

    @Test
    fun `memory save failure propagates error without claiming success`() = runTest {
        val backend = FakePreferencesBackend().apply { failWrites = true }
        val memoryRepo = MemoryRepository(backend)
        val coordinator = JarvisSessionCoordinator(
            scope = this,
            memoryRepository = memoryRepo,
            historyStore = InMemoryHistoryStore(),
            actionRegistry = ActionRegistry(StubDeviceReader(), memoryRepo),
            aiProviderFactory = {
                object : AiProvider {
                    override val providerId = "stub"
                    override val displayName = "stub"
                    override suspend fun generateReply(request: AiBrainRequest) =
                        Result.success(
                            AiBrainResponse(
                                replyEgyptianArabic = "تحت أمرك",
                                plannedAction = PlannedAction(
                                    actionId = "save_memory_note",
                                    parameters = mapOf("key" to "color", "value" to "cyan")
                                ),
                                providerName = "stub",
                                modelUsed = "m",
                                latencyMs = 1
                            )
                        )
                    override suspend fun testConnection() = Result.success("ok")
                }
            },
            speakText = { _, _, done -> done(Result.success(Unit)) },
            stopSpeech = {},
            getSettings = { JarvisSettingsState() }
        )

        coordinator.updateTelemetry(connectivity = ConnectivityTelemetry(isOnline = true, transportLabel = "Wi-Fi"))
        coordinator.submitUserCommand("احفظ المعلومة دي: اللون = أزرق")
        advanceUntilIdle()

        assertEquals(AssistantState.AWAITING_CONFIRMATION, coordinator.uiState.value.assistantState)
        coordinator.confirmPendingAction(approved = true)
        advanceUntilIdle()

        assertNotNull(coordinator.uiState.value.errorDetail)
        assertTrue(coordinator.uiState.value.lastResponse.contains("Simulated DataStore I/O error"))
    }

    @Test
    fun `TTS error does not leave UI stuck in SPEAKING`() = runTest {
        val memoryRepo = MemoryRepository(FakePreferencesBackend())
        val coordinator = JarvisSessionCoordinator(
            scope = this,
            memoryRepository = memoryRepo,
            historyStore = InMemoryHistoryStore(),
            actionRegistry = ActionRegistry(StubDeviceReader(), memoryRepo),
            aiProviderFactory = {
                object : AiProvider {
                    override val providerId = "stub"
                    override val displayName = "stub"
                    override suspend fun generateReply(request: AiBrainRequest) =
                        Result.success(AiBrainResponse("أهلاً يا فندم", null, "stub", "m", 1))
                    override suspend fun testConnection() = Result.success("ok")
                }
            },
            speakText = { _, _, done -> done(Result.failure(IllegalStateException("TTS hardware error"))) },
            stopSpeech = {},
            getSettings = { JarvisSettingsState() }
        )

        coordinator.updateTelemetry(connectivity = ConnectivityTelemetry(isOnline = true, transportLabel = "Wi-Fi"))
        coordinator.submitUserCommand("أهلاً")
        advanceUntilIdle()

        assertEquals(AssistantState.STANDBY, coordinator.uiState.value.assistantState)
        assertEquals("TTS hardware error", coordinator.uiState.value.errorDetail)
    }
}
