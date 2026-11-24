package com.jarvis.assistant.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalCommandRouterTest {

    private val router = LocalCommandRouter()

    @Test
    fun `arabic battery and connectivity commands route locally`() {
        val battery = router.route("البطارية كام؟")
        assertTrue(battery is LocalRouteOutcome.ExecuteLocalAction)
        assertEquals("get_device_status", (battery as LocalRouteOutcome.ExecuteLocalAction).plannedAction.actionId)

        val net = router.route("هل أنت متصل بالإنترنت؟")
        assertTrue(net is LocalRouteOutcome.ExecuteLocalAction)
        assertEquals("get_device_status", (net as LocalRouteOutcome.ExecuteLocalAction).plannedAction.actionId)
    }

    @Test
    fun `english local commands route locally`() {
        val status = router.route("Check device status and battery")
        assertTrue(status is LocalRouteOutcome.ExecuteLocalAction)
        assertEquals("get_device_status", (status as LocalRouteOutcome.ExecuteLocalAction).plannedAction.actionId)

        val recallLast = router.route("what did i just say?", lastUserPrompt = "افتح أنغامي")
        assertTrue(recallLast is LocalRouteOutcome.DirectArabicReply)
        assertTrue((recallLast as LocalRouteOutcome.DirectArabicReply).replyArabic.contains("افتح أنغامي"))
    }

    @Test
    fun `memory save retrieve delete route locally with expected parameters`() {
        val save = router.route("احفظ المعلومة دي: القهوة = سادة")
        assertTrue(save is LocalRouteOutcome.ExecuteLocalAction)
        val saveAction = (save as LocalRouteOutcome.ExecuteLocalAction).plannedAction
        assertEquals("save_memory_note", saveAction.actionId)
        assertEquals("القهوة", saveAction.parameters["key"])
        assertEquals("سادة", saveAction.parameters["value"])

        val retrieve = router.route("افتكر المعلومة دي القهوة")
        assertTrue(retrieve is LocalRouteOutcome.ExecuteLocalAction)
        assertEquals("retrieve_memory", (retrieve as LocalRouteOutcome.ExecuteLocalAction).plannedAction.actionId)

        val delete = router.route("امسح المعلومة دي القهوة")
        assertTrue(delete is LocalRouteOutcome.ExecuteLocalAction)
        assertEquals("delete_memory_note", (delete as LocalRouteOutcome.ExecuteLocalAction).plannedAction.actionId)
    }

    @Test
    fun `ambiguous command asks for clarification locally`() {
        val res = router.route("اعملها")
        assertTrue(res is LocalRouteOutcome.DirectArabicReply)
    }

    @Test
    fun `general conversation forwards to cloud AI`() {
        val res = router.route("احكيلي معلومة تاريخية سريعة عن القاهرة")
        assertEquals(LocalRouteOutcome.ForwardToCloudAi, res)
    }
}
