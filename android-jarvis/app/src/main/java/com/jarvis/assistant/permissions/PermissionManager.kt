package com.jarvis.assistant.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

data class PermissionStatusSnapshot(
    val microphoneGranted: Boolean,
    val notificationsGranted: Boolean,
    val ignoringBatteryOptimizations: Boolean
)

data class PermissionRationale(
    val permission: String,
    val titleArabic: String,
    val explanationArabic: String
)

class PermissionManager(private val context: Context) {

    fun snapshot(): PermissionStatusSnapshot {
        val mic = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val notif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val batteryExempt = pm.isIgnoringBatteryOptimizations(context.packageName)

        return PermissionStatusSnapshot(
            microphoneGranted = mic,
            notificationsGranted = notif,
            ignoringBatteryOptimizations = batteryExempt
        )
    }

    fun getPhase1Rationales(): List<PermissionRationale> {
        val list = mutableListOf(
            PermissionRationale(
                permission = Manifest.permission.RECORD_AUDIO,
                titleArabic = "صلاحية الميكروفون",
                explanationArabic = "مطلوبة فقط عند الضغط على زر التحدث لسماع أوامرك الصوتية باللهجة المصرية."
            )
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(
                PermissionRationale(
                    permission = Manifest.permission.POST_NOTIFICATIONS,
                    titleArabic = "صلاحية الإشعارات",
                    explanationArabic = "مطلوبة لإظهار تنبيهات المساعد والتذكيرات المستقبلية عند تفعيلها."
                )
            )
        }
        return list
    }

    fun phase1RequiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            arrayOf(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun createAppSettingsIntent(): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun createBatteryOptimizationSettingsIntent(): Intent {
        return Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
