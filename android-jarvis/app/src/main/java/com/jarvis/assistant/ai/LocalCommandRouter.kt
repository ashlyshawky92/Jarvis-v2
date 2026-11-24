package com.jarvis.assistant.ai

import com.jarvis.assistant.actions.PlannedAction

sealed interface LocalRouteOutcome {
    data class ExecuteLocalAction(
        val plannedAction: PlannedAction,
        val preReplyArabic: String = ""
    ) : LocalRouteOutcome

    data class DirectArabicReply(
        val replyArabic: String
    ) : LocalRouteOutcome

    data object ForwardToCloudAi : LocalRouteOutcome
}

/**
 * Deterministic local command router that handles supported device status, memory,
 * and unimplemented Phase 3-5 commands locally without unnecessary Gemini calls.
 */
class LocalCommandRouter {

    fun route(rawInput: String, lastUserPrompt: String? = null): LocalRouteOutcome {
        val text = rawInput.trim()
        if (text.isBlank()) {
            return LocalRouteOutcome.DirectArabicReply("ممكن تعيد طلبك تاني يا فندم؟")
        }

        val lower = text.lowercase()

        // 1. Device Status (Battery / Internet / Device info) in Arabic and English
        val deviceStatusKeywords = listOf(
            "حالة البطارية", "البطارية كام", "شحن البطارية", "البطارية",
            "حالة الجهاز", "هل أنت متصل بالإنترنت", "متصل بالنت", "حالة النت",
            "battery", "device status", "internet status", "network status", "am i online"
        )
        if (deviceStatusKeywords.any { lower.contains(it) }) {
            return LocalRouteOutcome.ExecuteLocalAction(
                PlannedAction(actionId = "get_device_status", requiresConfirmation = false)
            )
        }

        // 2. Recall last user utterance ("افتكر أنا قلتلك إيه؟")
        if (
            lower.contains("أنا قلتلك إيه") ||
            lower.contains("انا قلتلك ايه") ||
            lower.contains("what did i say") ||
            lower.contains("what did i just say")
        ) {
            val reply = if (!lastUserPrompt.isNullOrBlank()) {
                "آخر حاجة قولتهالي يا فندم كانت: \"$lastUserPrompt\"."
            } else {
                "لسه مفيش كلام سابق في الجلسة دي يا فندم."
            }
            return LocalRouteOutcome.DirectArabicReply(reply)
        }

        // 3. Save memory ("احفظ المعلومة دي: ..." / "save memory ...")
        val savePrefixes = listOf("احفظ المعلومة دي", "احفظ إن", "احفظ ان", "save memory", "remember that")
        for (prefix in savePrefixes) {
            if (lower.startsWith(prefix)) {
                val remainder = text.substring(prefix.length).trim().trimStart(':', '،', '-', ' ')
                if (remainder.isBlank()) {
                    return LocalRouteOutcome.DirectArabicReply(
                        "تحب أحفظ إيه بالضبط يا فندم؟ مثلاً قول: احفظ المعلومة دي: رقم المكتب 12."
                    )
                }
                val (key, value) = splitKeyValue(remainder)
                return LocalRouteOutcome.ExecuteLocalAction(
                    PlannedAction(
                        actionId = "save_memory_note",
                        parameters = mapOf("key" to key, "value" to value),
                        requiresConfirmation = true
                    )
                )
            }
        }

        // 4. Retrieve memory ("افتكر المعلومة دي ..." / "recall memory ...")
        val retrievePrefixes = listOf("افتكر المعلومة دي", "هات المعلومة", "retrieve memory", "recall memory")
        for (prefix in retrievePrefixes) {
            if (lower.startsWith(prefix)) {
                val key = text.substring(prefix.length).trim().trimStart(':', '،', '-', ' ')
                if (key.isBlank()) {
                    return LocalRouteOutcome.DirectArabicReply("عايزني أفتكر أنهي معلومة يا فندم؟ قول اسمها.")
                }
                return LocalRouteOutcome.ExecuteLocalAction(
                    PlannedAction(
                        actionId = "retrieve_memory",
                        parameters = mapOf("key" to key),
                        requiresConfirmation = false
                    )
                )
            }
        }

        // 5. Delete memory ("امسح المعلومة دي ..." / "delete memory ...")
        val deletePrefixes = listOf("امسح المعلومة دي", "احذف المعلومة", "delete memory", "forget memory")
        for (prefix in deletePrefixes) {
            if (lower.startsWith(prefix)) {
                val key = text.substring(prefix.length).trim().trimStart(':', '،', '-', ' ')
                if (key.isBlank()) {
                    return LocalRouteOutcome.DirectArabicReply("أنهي معلومة تحب أمسحها يا فندم؟")
                }
                return LocalRouteOutcome.ExecuteLocalAction(
                    PlannedAction(
                        actionId = "delete_memory_note",
                        parameters = mapOf("key" to key),
                        requiresConfirmation = true
                    )
                )
            }
        }

        // 6. Ambiguous short commands
        val ambiguous = setOf("اعملها", "نفذ", "افتحه", "امسحه", "احفظها", "do it", "open it")
        if (lower in ambiguous) {
            return LocalRouteOutcome.DirectArabicReply("الأمر مش واضح يا فندم، تقصد إيه بالضبط؟")
        }

        return LocalRouteOutcome.ForwardToCloudAi
    }

    private fun splitKeyValue(raw: String): Pair<String, String> {
        val separators = listOf("=", ":", " هو ", " هي ", " أن ", " ان ")
        for (sep in separators) {
            val idx = raw.indexOf(sep, ignoreCase = true)
            if (idx > 0) {
                val left = raw.substring(0, idx).trim()
                val right = raw.substring(idx + sep.length).trim()
                if (left.isNotBlank() && right.isNotBlank()) {
                    return left to right
                }
            }
        }
        val words = raw.split(Regex("\\s+"), limit = 3)
        val fallbackKey = words.take(2).joinToString(" ")
        return fallbackKey to raw
    }
}
