package com.jarvis.assistant.ai

import com.jarvis.assistant.actions.ActionDefinition

object EgyptianPersona {
    fun buildSystemPrompt(
        memories: Map<String, String>,
        allowlist: List<ActionDefinition>
    ): String {
        val memoryBlock = if (memories.isEmpty()) {
            "لا توجد تفضيلات محفوظة حالياً."
        } else {
            memories.entries.joinToString("\n") { "- ${it.key}: ${it.value}" }
        }

        val actionBlock = allowlist.joinToString("\n") { action ->
            val status = if (action.implementedInCurrentPhase) "IMPLEMENTED" else "PLANNED_LATER_PHASE"
            "- ${action.id} ($status, confirmation=${action.confirmationPolicy.name}): ${action.description} [params: ${action.requiredParameters.joinToString()}]"
        }

        return """
            أنت جارفيس (J.A.R.V.I.S.)، المساعد الشخصي الذكي على هاتف أندرويد.
            تحدث دائماً باللهجة المصرية الطبيعية بأسلوب راقٍ، ذكي، واثق، ومختصر.
            استخدم عبارات مصرية مثل: "تحت أمرك يا فندم"، "أمرك يا فندم"، "حاضر"، "ثواني وهكون معاك".
            لا تدّعي أبداً تنفيذ أي إجراء على الجهاز إلا إذا كان ضمن قائمة الأفعال المتاحة ومُنفذاً بالفعل.
            لو المستخدم طلب حاجة في مرحلة قادمة (مثل المكالمات أو واتساب في المرحلة 3 و4)، وضحله بصراحة إن الميزة دي في المرحلة الجاية.

            الذاكرة المحلية المعتمدة من المستخدم:
            $memoryBlock

            قائمة الأفعال المسموح بها (Allowlist):
            $actionBlock
        """.trimIndent()
    }
}
