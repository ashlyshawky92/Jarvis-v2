package com.jarvis.assistant.memory

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class FakePreferencesBackend : PreferencesBackend {
    var rawJson: String? = null
    var corruptedBackup: String? = null
    var memoryEnabled: Boolean = true
    var failWrites: Boolean = false

    private val _flow = MutableStateFlow(JarvisSettingsState())
    override val stateFlow: Flow<JarvisSettingsState> = _flow

    override suspend fun readRawMemoriesJson(): String? = rawJson
    override suspend fun readRawCorruptedBackup(): String? = corruptedBackup
    override suspend fun isMemoryEnabled(): Boolean = memoryEnabled

    override suspend fun writeMemoriesSafely(jsonPayload: String): Result<Unit> {
        if (failWrites) {
            return Result.failure(IllegalStateException("Simulated DataStore I/O error"))
        }
        rawJson = jsonPayload
        return Result.success(Unit)
    }

    override suspend fun backupCorruptedAndClearActive(corruptedRaw: String): Result<Unit> {
        corruptedBackup = corruptedRaw
        rawJson = "[]"
        return Result.success(Unit)
    }

    override suspend fun clearMemoriesExplicitly(): Result<Unit> {
        rawJson = null
        corruptedBackup = null
        return Result.success(Unit)
    }

    override suspend fun updateBackendUrl(url: String): Result<Unit> = Result.success(Unit)
    override suspend fun updateModel(model: String): Result<Unit> = Result.success(Unit)
    override suspend fun updateVoiceSettings(rate: Float, pitch: Float, languageTag: String, voiceName: String?): Result<Unit> = Result.success(Unit)
    override suspend fun setPreferOfflineStt(enabled: Boolean): Result<Unit> = Result.success(Unit)
    override suspend fun setSttLanguageTag(tag: String): Result<Unit> = Result.success(Unit)
    override suspend fun setMemoryEnabled(enabled: Boolean): Result<Unit> {
        memoryEnabled = enabled
        return Result.success(Unit)
    }
    override suspend fun setShareMemoriesWithAi(enabled: Boolean): Result<Unit> = Result.success(Unit)
}

class MemoryRepositoryTest {

    @Test
    fun `save and retrieve memory succeeds and normalizes key`() = runTest {
        val backend = FakePreferencesBackend()
        var now = 1000L
        val repo = MemoryRepository(backend) { now }

        val saveRes = repo.saveOrUpdateMemory("  Coffee   Type ", "Espresso", MemoryCategory.USER_PREFERENCE)
        assertTrue(saveRes.isSuccess)
        assertEquals("coffee_type", saveRes.getOrThrow().normalizedKey)

        val retrieved = repo.retrieveMemory("coffee type").getOrThrow()
        assertNotNull(retrieved)
        assertEquals("Espresso", retrieved?.value)
        assertEquals(1000L, retrieved?.createdAtMillis)
    }

    @Test
    fun `duplicate key updates existing entry and preserves createdAtMillis`() = runTest {
        val backend = FakePreferencesBackend()
        var now = 1000L
        val repo = MemoryRepository(backend) { now }

        repo.saveOrUpdateMemory("Home City", "Cairo")
        now = 2500L
        val updated = repo.saveOrUpdateMemory("home   city", "Alexandria").getOrThrow()

        assertEquals(1000L, updated.createdAtMillis)
        assertEquals(2500L, updated.updatedAtMillis)
        assertEquals("Alexandria", updated.value)
        assertEquals(1, repo.searchMemories("").getOrThrow().size)
    }

    @Test
    fun `failed DataStore write propagates failure instead of false success`() = runTest {
        val backend = FakePreferencesBackend().apply { failWrites = true }
        val repo = MemoryRepository(backend)

        val res = repo.saveOrUpdateMemory("key", "value")
        assertTrue(res.isFailure)
        assertNull(backend.rawJson)
    }

    @Test
    fun `disabled memory blocks both writes and reads`() = runTest {
        val backend = FakePreferencesBackend().apply { memoryEnabled = false }
        val repo = MemoryRepository(backend)

        assertTrue(repo.saveOrUpdateMemory("k", "v").isFailure)
        assertTrue(repo.retrieveMemory("k").isFailure)
        assertTrue(repo.getRelevantMemoriesForPrompt("k").isEmpty())
    }

    @Test
    fun `invalid empty key or value is rejected`() = runTest {
        val repo = MemoryRepository(FakePreferencesBackend())
        assertTrue(repo.saveOrUpdateMemory("   ", "value").isFailure)
        assertTrue(repo.saveOrUpdateMemory("key", "   ").isFailure)
    }

    @Test
    fun `delete removes target entry without affecting others`() = runTest {
        val repo = MemoryRepository(FakePreferencesBackend())
        repo.saveOrUpdateMemory("k1", "v1")
        repo.saveOrUpdateMemory("k2", "v2")

        assertTrue(repo.deleteMemory("k1").getOrThrow())
        assertNull(repo.retrieveMemory("k1").getOrThrow())
        assertEquals("v2", repo.retrieveMemory("k2").getOrThrow()?.value)
    }

    @Test
    fun `clearAll requires explicit confirmation`() = runTest {
        val repo = MemoryRepository(FakePreferencesBackend())
        repo.saveOrUpdateMemory("k1", "v1")

        assertTrue(repo.clearAllMemories(explicitUserConfirmed = false).isFailure)
        assertEquals(1, repo.searchMemories("").getOrThrow().size)

        assertTrue(repo.clearAllMemories(explicitUserConfirmed = true).isSuccess)
        assertTrue(repo.searchMemories("").getOrThrow().isEmpty())
    }

    @Test
    fun `corrupted JSON is not silently overwritten and can be backed up explicitly`() = runTest {
        val backend = FakePreferencesBackend().apply { rawJson = "{corrupted_json!!" }
        val repo = MemoryRepository(backend)

        val writeAttempt = repo.saveOrUpdateMemory("new", "val")
        assertTrue(writeAttempt.isFailure)
        assertEquals("{corrupted_json!!", backend.rawJson)

        repo.recoverCorruptedStoreWithBackup()
        assertEquals("{corrupted_json!!", backend.corruptedBackup)
        assertTrue(repo.saveOrUpdateMemory("new", "val").isSuccess)
        assertFalse(repo.searchMemories("").getOrThrow().isEmpty())
    }
}
