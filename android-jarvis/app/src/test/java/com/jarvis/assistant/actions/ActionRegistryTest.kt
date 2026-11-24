package com.jarvis.assistant.actions

import com.jarvis.assistant.memory.FakePreferencesBackend
import com.jarvis.assistant.memory.MemoryRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private class FakeDeviceStatusReader : DeviceStatusReader {
    override fun readStatusMessage(): ActionResult {
        return ActionResult(
            actionId = "get_device_status",
            status = ActionExecutionStatus.SUCCESS,
            spokenArabicMessage = "البطارية 85% والاتصال واي فاي.",
            verificationNote = "FakeDeviceStatusReader"
        )
    }
}

class ActionRegistryTest {

    @Test
    fun `valid get_device_status executes without confirmation`() = runTest {
        val registry = ActionRegistry(FakeDeviceStatusReader(), MemoryRepository(FakePreferencesBackend()))
        val res = registry.executeAction(
            PlannedAction(actionId = "get_device_status", requiresConfirmation = false),
            confirmedByUser = false
        )
        assertEquals(ActionExecutionStatus.SUCCESS, res.status)
    }

    @Test
    fun `unknown action ID is rejected`() = runTest {
        val registry = ActionRegistry(FakeDeviceStatusReader(), MemoryRepository(FakePreferencesBackend()))
        val res = registry.executeAction(
            PlannedAction(actionId = "run_shell_command"),
            confirmedByUser = true
        )
        assertEquals(ActionExecutionStatus.INVALID_PARAMETERS, res.status)
    }

    @Test
    fun `missing parameters are rejected`() = runTest {
        val registry = ActionRegistry(FakeDeviceStatusReader(), MemoryRepository(FakePreferencesBackend()))
        val res = registry.executeAction(
            PlannedAction(actionId = "save_memory_note", parameters = mapOf("key" to "onlyKey")),
            confirmedByUser = true
        )
        assertEquals(ActionExecutionStatus.INVALID_PARAMETERS, res.status)
    }

    @Test
    fun `confirmation policy cannot be bypassed by AI flag`() = runTest {
        val registry = ActionRegistry(FakeDeviceStatusReader(), MemoryRepository(FakePreferencesBackend()))
        val res = registry.executeAction(
            PlannedAction(
                actionId = "save_memory_note",
                parameters = mapOf("key" to "name", "value" to "Ashly"),
                requiresConfirmation = false
            ),
            confirmedByUser = false
        )
        assertEquals(ActionExecutionStatus.AWAITING_CONFIRMATION, res.status)
    }

    @Test
    fun `future unimplemented action returns NOT_IMPLEMENTED_IN_PHASE`() = runTest {
        val registry = ActionRegistry(FakeDeviceStatusReader(), MemoryRepository(FakePreferencesBackend()))
        val res = registry.executeAction(
            PlannedAction(actionId = "initiate_phone_call", parameters = mapOf("contactName" to "Ahmed")),
            confirmedByUser = true
        )
        assertEquals(ActionExecutionStatus.NOT_IMPLEMENTED_IN_PHASE, res.status)
    }

    @Test
    fun `save memory failure returns FAILED and duplicate token is ignored`() = runTest {
        val backend = FakePreferencesBackend().apply { failWrites = true }
        val registry = ActionRegistry(FakeDeviceStatusReader(), MemoryRepository(backend))
        val action = PlannedAction(
            actionId = "save_memory_note",
            parameters = mapOf("key" to "city", "value" to "Cairo"),
            confirmationToken = "tok-1"
        )

        val failedRes = registry.executeAction(action, confirmedByUser = true)
        assertEquals(ActionExecutionStatus.FAILED, failedRes.status)

        backend.failWrites = false
        val okRes = registry.executeAction(action, confirmedByUser = true)
        assertEquals(ActionExecutionStatus.SUCCESS, okRes.status)

        val dupRes = registry.executeAction(action, confirmedByUser = true)
        assertEquals(ActionExecutionStatus.DUPLICATE_IGNORED, dupRes.status)
    }
}
