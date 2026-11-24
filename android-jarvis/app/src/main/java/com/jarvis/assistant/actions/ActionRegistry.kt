package com.jarvis.assistant.actions

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import com.jarvis.assistant.core.model.DeviceRuntimeInfo

class ActionRegistry(private val context: Context) {

    private val allowlist: Map<String, ActionDefinition> = listOf(
        ActionDefinition(
            id = "get_device_status",
            name = "Get Device Status",
            description = "Read battery level, charging state, network status, and runtime Android/MagicOS info.",
            requiredParameters = emptyList(),
            requiredPermissions = emptyList(),
            confirmationPolicy = ConfirmationPolicy.NONE,
            implementedInCurrentPhase = true
        ),
        ActionDefinition(
            id = "save_memory_note",
            name = "Save User Preference",
            description = "Save an explicit user-approved preference or personal note locally.",
            requiredParameters = listOf("key", "value"),
            requiredPermissions = emptyList(),
            confirmationPolicy = ConfirmationPolicy.REQUIRED,
            implementedInCurrentPhase = true
        ),
        ActionDefinition(
            id = "open_application",
            name = "Open Application",
            description = "Launch an installed Android application by name (Phase 3).",
            requiredParameters = listOf("appName"),
            requiredPermissions = emptyList(),
            confirmationPolicy = ConfirmationPolicy.NONE,
            implementedInCurrentPhase = false
        ),
        ActionDefinition(
            id = "initiate_phone_call",
            name = "Initiate Phone Call",
            description = "Resolve contact and start a phone call (Phase 3).",
            requiredParameters = listOf("contactName"),
            requiredPermissions = listOf("android.permission.READ_CONTACTS", "android.permission.CALL_PHONE"),
            confirmationPolicy = ConfirmationPolicy.REQUIRED,
            implementedInCurrentPhase = false
        ),
        ActionDefinition(
            id = "read_notifications",
            name = "Read Message Notifications",
            description = "Read recent accessible messaging notifications (Phase 3/4).",
            requiredParameters = emptyList(),
            requiredPermissions = listOf("BIND_NOTIFICATION_LISTENER_SERVICE"),
            confirmationPolicy = ConfirmationPolicy.NONE,
            implementedInCurrentPhase = false
        ),
        ActionDefinition(
            id = "schedule_reminder",
            name = "Schedule Reminder",
            description = "Schedule an Android notification reminder via AlarmManager/WorkManager (Phase 5).",
            requiredParameters = listOf("title", "timeText"),
            requiredPermissions = listOf("android.permission.POST_NOTIFICATIONS"),
            confirmationPolicy = ConfirmationPolicy.NONE,
            implementedInCurrentPhase = false
        )
    ).associateBy { it.id }

    fun getAllowlist(): List<ActionDefinition> = allowlist.values.toList()

    fun findAction(actionId: String): ActionDefinition? = allowlist[actionId]

    fun executeLocalAction(
        planned: PlannedAction,
        confirmedByUser: Boolean,
        onSaveMemory: (key: String, value: String) -> Unit
    ): ActionResult {
        val definition = allowlist[planned.actionId]
            ?: return ActionResult(
                actionId = planned.actionId,
                status = ActionExecutionStatus.INVALID_PARAMETERS,
                spokenArabicMessage = "يا فندم، الأمر ده مش موجود ضمن الصلاحيات المسموح بيها في النظام.",
                verificationNote = "Rejected: actionId '${planned.actionId}' is not in the explicit allowlist."
            )

        if (!definition.implementedInCurrentPhase) {
            return ActionResult(
                actionId = definition.id,
                status = ActionExecutionStatus.NOT_IMPLEMENTED_IN_PHASE,
                spokenArabicMessage = "الأمر '${definition.name}' متخطط في المرحلة الجاية، ولسه متفعلش على الجهاز في المرحلة الأولى.",
                verificationNote = "Blocked honestly: action '${definition.id}' is scheduled for a later phase."
            )
        }

        val missingParams = definition.requiredParameters.filter { planned.parameters[it].isNullOrBlank() }
        if (missingParams.isNotEmpty()) {
            return ActionResult(
                actionId = definition.id,
                status = ActionExecutionStatus.INVALID_PARAMETERS,
                spokenArabicMessage = "محتاج توضيح بسيط يا فندم عشان أنفذ الأمر ده.",
                verificationNote = "Missing parameters: ${missingParams.joinToString(", ")}"
            )
        }

        if (definition.confirmationPolicy == ConfirmationPolicy.REQUIRED && !confirmedByUser) {
            return ActionResult(
                actionId = definition.id,
                status = ActionExecutionStatus.AWAITING_CONFIRMATION,
                spokenArabicMessage = "تأكد عليا يا فندم قبل ما أنفذ الأمر ده؟",
                verificationNote = "Confirmation required by ActionRegistry policy."
            )
        }

        return when (definition.id) {
            "get_device_status" -> executeDeviceStatus()
            "save_memory_note" -> {
                val key = planned.parameters.getValue("key").trim()
                val value = planned.parameters.getValue("value").trim()
                onSaveMemory(key, value)
                ActionResult(
                    actionId = definition.id,
                    status = ActionExecutionStatus.SUCCESS,
                    spokenArabicMessage = "تمام يا فندم، حفظت المعلومة دي عندك في الذاكرة المحلية.",
                    verificationNote = "Saved local DataStore memory entry '$key'."
                )
            }
            else -> ActionResult(
                actionId = definition.id,
                status = ActionExecutionStatus.FAILED,
                spokenArabicMessage = "حصل خطأ غير متوقع أثناء تنفيذ الأمر.",
                verificationNote = "Unhandled implemented action."
            )
        }
    }

    private fun executeDeviceStatus(): ActionResult {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val pct = if (level >= 0 && scale > 0) (level * 100) / scale else -1

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(activeNetwork)
        val online = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val netLabel = when {
            !online -> "غير متصل"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "واي فاي"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "بيانات الهاتف"
            else -> "متصل"
        }

        val info = DeviceRuntimeInfo.detect()
        val magicText = info.magicOsVersion?.let { "، MagicOS $it" } ?: ""
        val batteryText = if (pct >= 0) "$pct%" else "غير متاح"

        return ActionResult(
            actionId = "get_device_status",
            status = ActionExecutionStatus.SUCCESS,
            spokenArabicMessage = "البطارية حالياً $batteryText، والاتصال $netLabel، والجهاز شغال على أندرويد ${info.androidVersion}$magicText.",
            verificationNote = "Verified via BatteryManager, ConnectivityManager, and Build/SystemProperties."
        )
    }
}
