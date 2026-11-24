package com.jarvis.assistant.actions

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import com.jarvis.assistant.core.model.DeviceRuntimeInfo
import com.jarvis.assistant.memory.MemoryCategory
import com.jarvis.assistant.memory.MemoryRepository

interface DeviceStatusReader {
    fun readStatusMessage(): ActionResult
}

class AndroidDeviceStatusReader(private val context: Context) : DeviceStatusReader {
    override fun readStatusMessage(): ActionResult {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val pct = if (level >= 0 && scale > 0) (level * 100) / scale else -1
        val chargingText = if (
            status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        ) " (بيشحن)" else ""

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(activeNetwork)
        val online = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val netLabel = when {
            !online -> "غير متصل"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "واي فاي متصل"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "بيانات الهاتف متصلة"
            else -> "متصل"
        }

        val info = DeviceRuntimeInfo.detect()
        val magicText = info.magicOsVersion?.let { "، MagicOS $it" } ?: ""
        val batteryText = if (pct >= 0) "$pct%$chargingText" else "غير متاح"

        return ActionResult(
            actionId = "get_device_status",
            status = ActionExecutionStatus.SUCCESS,
            spokenArabicMessage = "البطارية حالياً $batteryText، والاتصال $netLabel، والجهاز شغال على أندرويد ${info.androidVersion}$magicText.",
            verificationNote = "Verified via BatteryManager, ConnectivityManager, and Build/SystemProperties."
        )
    }
}

class ActionRegistry(
    private val deviceStatusReader: DeviceStatusReader,
    private val memoryRepository: MemoryRepository
) {
    constructor(context: Context, memoryRepository: MemoryRepository) : this(
        deviceStatusReader = AndroidDeviceStatusReader(context.applicationContext),
        memoryRepository = memoryRepository
    )

    private val completedTokens = mutableSetOf<String>()

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
            name = "Save User Memory",
            description = "Save or update an explicit user-approved preference or personal note locally.",
            requiredParameters = listOf("key", "value"),
            requiredPermissions = emptyList(),
            confirmationPolicy = ConfirmationPolicy.REQUIRED,
            implementedInCurrentPhase = true
        ),
        ActionDefinition(
            id = "retrieve_memory",
            name = "Retrieve Local Memory",
            description = "Look up a saved preference or personal note from local memory.",
            requiredParameters = listOf("key"),
            requiredPermissions = emptyList(),
            confirmationPolicy = ConfirmationPolicy.NONE,
            implementedInCurrentPhase = true
        ),
        ActionDefinition(
            id = "delete_memory_note",
            name = "Delete Local Memory",
            description = "Delete a specific entry from local memory after explicit user confirmation.",
            requiredParameters = listOf("key"),
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

    suspend fun executeAction(
        planned: PlannedAction,
        confirmedByUser: Boolean
    ): ActionResult {
        if (planned.confirmationToken in completedTokens) {
            return ActionResult(
                actionId = planned.actionId,
                status = ActionExecutionStatus.DUPLICATE_IGNORED,
                spokenArabicMessage = "الأمر ده اتنفذ بالفعل يا فندم.",
                verificationNote = "Ignored duplicate execution for token ${planned.confirmationToken}."
            )
        }

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
                spokenArabicMessage = "الأمر '${definition.name}' متخطط في المرحلة الجاية، ولسه متفعلش على الجهاز في المرحلة الحالية.",
                verificationNote = "Blocked honestly: action '${definition.id}' is not implemented in Phase 1.1."
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

        // Security rule: ActionRegistry always enforces its own ConfirmationPolicy;
        // Gemini cannot bypass confirmation by passing requiresConfirmation = false.
        val mustConfirm = definition.confirmationPolicy == ConfirmationPolicy.REQUIRED
        if (mustConfirm && !confirmedByUser) {
            return ActionResult(
                actionId = definition.id,
                status = ActionExecutionStatus.AWAITING_CONFIRMATION,
                spokenArabicMessage = "تأكد عليا يا فندم قبل ما أنفذ الأمر ده؟",
                verificationNote = "Confirmation enforced by ActionRegistry policy."
            )
        }

        val result = when (definition.id) {
            "get_device_status" -> deviceStatusReader.readStatusMessage()
            "save_memory_note" -> {
                val key = planned.parameters.getValue("key").trim()
                val value = planned.parameters.getValue("value").trim()
                val category = planned.parameters["category"]
                    ?.let { runCatching { MemoryCategory.valueOf(it) }.getOrNull() }
                    ?: MemoryCategory.PERSONAL_NOTE

                val saveResult = memoryRepository.saveOrUpdateMemory(key, value, category)
                saveResult.fold(
                    onSuccess = { saved ->
                        ActionResult(
                            actionId = definition.id,
                            status = ActionExecutionStatus.SUCCESS,
                            spokenArabicMessage = "تمام يا فندم، حفظت '${saved.displayKey}' في الذاكرة المحلية بنجاح.",
                            verificationNote = "Awaited DataStore write for '${saved.normalizedKey}'."
                        )
                    },
                    onFailure = { err ->
                        ActionResult(
                            actionId = definition.id,
                            status = ActionExecutionStatus.FAILED,
                            spokenArabicMessage = err.message ?: "فشل حفظ المعلومة في الذاكرة المحلية.",
                            verificationNote = "Memory write failed: ${err.message}"
                        )
                    }
                )
            }
            "retrieve_memory" -> {
                val key = planned.parameters.getValue("key").trim()
                val readResult = memoryRepository.retrieveMemory(key)
                readResult.fold(
                    onSuccess = { entry ->
                        if (entry != null) {
                            ActionResult(
                                actionId = definition.id,
                                status = ActionExecutionStatus.SUCCESS,
                                spokenArabicMessage = "لقيت في الذاكرة يا فندم: ${entry.displayKey} = ${entry.value}.",
                                verificationNote = "Retrieved '${entry.normalizedKey}' from MemoryRepository."
                            )
                        } else {
                            ActionResult(
                                actionId = definition.id,
                                status = ActionExecutionStatus.SUCCESS,
                                spokenArabicMessage = "ملقيتش معلومة محفوظة باسم '$key' يا فندم.",
                                verificationNote = "Memory lookup returned no match for '$key'."
                            )
                        }
                    },
                    onFailure = { err ->
                        ActionResult(
                            actionId = definition.id,
                            status = ActionExecutionStatus.FAILED,
                            spokenArabicMessage = err.message ?: "تعذر قراءة الذاكرة المحلية.",
                            verificationNote = "Memory read failed: ${err.message}"
                        )
                    }
                )
            }
            "delete_memory_note" -> {
                val key = planned.parameters.getValue("key").trim()
                val deleteResult = memoryRepository.deleteMemory(key)
                deleteResult.fold(
                    onSuccess = { deleted ->
                        ActionResult(
                            actionId = definition.id,
                            status = ActionExecutionStatus.SUCCESS,
                            spokenArabicMessage = if (deleted) {
                                "تم حذف '$key' من الذاكرة المحلية يا فندم."
                            } else {
                                "ملقيتش '$key' في الذاكرة أصلاً يا فندم."
                            },
                            verificationNote = "Delete result for '$key': deleted=$deleted"
                        )
                    },
                    onFailure = { err ->
                        ActionResult(
                            actionId = definition.id,
                            status = ActionExecutionStatus.FAILED,
                            spokenArabicMessage = err.message ?: "فشل حذف المعلومة من الذاكرة المحلية.",
                            verificationNote = "Memory delete failed: ${err.message}"
                        )
                    }
                )
            }
            else -> ActionResult(
                actionId = definition.id,
                status = ActionExecutionStatus.FAILED,
                spokenArabicMessage = "حصل خطأ غير متوقع أثناء تنفيذ الأمر.",
                verificationNote = "Unhandled action."
            )
        }

        if (result.status == ActionExecutionStatus.SUCCESS) {
            completedTokens.add(planned.confirmationToken)
        }
        return result
    }
}
